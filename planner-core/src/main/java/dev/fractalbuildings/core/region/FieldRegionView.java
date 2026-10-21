package dev.fractalbuildings.core.region;

import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The join rule (ADR 0003 R2, ROADMAP 5.8) over a {@link RegionPlanner}: big settlements claim the cells
 * their drawn radius reaches, so a city is no longer clamped to its own cell.
 *
 * <p>One round, no cascade. A <b>claimant</b> is a cell whose decision is settled and whose drawn radius
 * exceeds {@code claimFrac} x cell size (large tiers only; the others are never decided here). It claims
 * every cell whose site lies within its drawn radius of its centre. It <b>survives</b> unless some other
 * claimant whose claim set overlaps its own has a higher priority ({@code hash(seed, "region/priority",
 * cx, cz)} unsigned, ties to the lower (cx, cz)); dropped claimants still block, which is what bounds the
 * read. A cell claimed by a survivor is a member (survivors never overlap); a surviving owner takes its
 * drawn radius; every other cell decides alone with the grow clamp.
 *
 * <p>Pure function of (seed, cell, probe): the caches here only memoise, so results never depend on
 * query order or threads (D6). Build the view after configuring the planner; it assumes the planner's
 * fields no longer change. With a {@code window}, cells whose probe reach leaves it are treated as
 * non-claimants (a recorded lattice cannot answer more), so results near the window edge differ from
 * the unbounded world.
 */
public final class FieldRegionView {
	/** A claimant needs a drawn radius above this fraction of the cell size. */
	public double claimFrac = 0.4;

	private final RegionPlanner planner;
	private final RegionMap.Window window;
	private final RegionField field;
	private final int S, R, slack;

	private final ConcurrentHashMap<CellKey, CellDecision> decisions = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<CellKey, Claim> claims = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<CellKey, Boolean> survives = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<CellKey, Resolved> resolved = new ConcurrentHashMap<>();

	private record Claim(CellDecision d, long priority, int radius, List<CellKey> cells) {}

	private static final Claim NONE = new Claim(null, 0, 0, List.of());

	/** What a cell ends up as: its decision (the owner's with the drawn radius), who claimed it, its site. */
	public record Resolved(CellDecision decision, CellKey claimedBy, SettlementSite site) {
		public boolean member() {
			return claimedBy != null;
		}
	}

	public FieldRegionView(RegionPlanner planner) {
		this(planner, null);
	}

	public FieldRegionView(RegionPlanner planner, RegionMap.Window window) {
		this.planner = planner;
		this.window = window;
		this.field = planner.field;
		this.S = field.cellSize;
		this.R = planner.maxRadius;
		this.slack = (int) (0.3 * S) + RegionProbe.STRIDE;
	}

	/**
	 * Bound on how far from a cell's site any probe column is read to resolve it: claimants within R of the
	 * cell, rivals within 2R more, each decided over its probe reach.
	 */
	public int readRadius() {
		return 3 * R + slack + (int) (0.3 * S) + Math.min(R, (int) (planner.clampFrac * S)) + 2 * RegionProbe.STRIDE;
	}

	private boolean available(CellKey k) {
		if (window == null) return true;
		int reach = planner.probeReach(k.cx(), k.cz()), sx = field.siteX(k.cx(), k.cz()), sz = field.siteZ(k.cx(), k.cz());
		return sx - reach >= window.x0() && sx + reach <= window.x1() && sz - reach >= window.z0() && sz + reach <= window.z1();
	}

	public CellDecision decision(CellKey k) {
		CellDecision d = decisions.get(k);
		if (d == null) {
			d = planner.decide(k.cx(), k.cz());
			decisions.putIfAbsent(k, d);
		}
		return d;
	}

	private static boolean before(CellKey a, long pa, CellKey b, long pb) {
		int c = Long.compareUnsigned(pa, pb);
		if (c != 0) return c > 0;
		return a.cx() != b.cx() ? a.cx() < b.cx() : a.cz() < b.cz();
	}

	private long priority(CellKey k) {
		return Hash64.hash(field.seed, "region/priority", k.cx(), k.cz());
	}

	private Claim claim(CellKey k) {
		Claim c = claims.get(k);
		if (c != null) return c;
		c = computeClaim(k);
		Claim prev = claims.putIfAbsent(k, c);
		return prev != null ? prev : c;
	}

	private Claim computeClaim(CellKey k) {
		int drawn = Tiers.radius(field.seed, k.cx(), k.cz(), Tiers.tier(field.seed, k.cx(), k.cz()), R);
		if (drawn <= claimFrac * S || !available(k)) return NONE;
		CellDecision d = decision(k);
		if (!d.settled()) return NONE;
		List<CellKey> cells = new ArrayList<>();
		long r2 = (long) drawn * drawn;
		for (int cz = Math.floorDiv(d.centreZ() - drawn, S); cz <= Math.floorDiv(d.centreZ() + drawn, S); cz++) {
			for (int cx = Math.floorDiv(d.centreX() - drawn, S); cx <= Math.floorDiv(d.centreX() + drawn, S); cx++) {
				long dx = (long) field.siteX(cx, cz) - d.centreX(), dz = (long) field.siteZ(cx, cz) - d.centreZ();
				if (dx * dx + dz * dz <= r2) cells.add(new CellKey(cx, cz));
			}
		}
		// the owner always owns its own cell, even when the centre sits further than the radius from the site
		CellKey self = new CellKey(k.cx(), k.cz());
		if (!cells.contains(self)) cells.add(self);
		Collections.sort(cells);
		return new Claim(d, priority(k), drawn, List.copyOf(cells));
	}

	/** Claimants whose centre may lie within {@code dist} of (px, pz), found by site and a slack, in (cz, cx) order. */
	private List<CellKey> claimantsNear(int px, int pz, int dist) {
		int reach = dist + slack;
		List<CellKey> out = new ArrayList<>();
		for (int cz = Math.floorDiv(pz - reach, S); cz <= Math.floorDiv(pz + reach, S); cz++) {
			for (int cx = Math.floorDiv(px - reach, S); cx <= Math.floorDiv(px + reach, S); cx++) {
				long dx = (long) field.siteX(cx, cz) - px, dz = (long) field.siteZ(cx, cz) - pz;
				if (dx * dx + dz * dz > (long) reach * reach) continue;
				CellKey k = new CellKey(cx, cz);
				if (claim(k) != NONE) out.add(k);
			}
		}
		return out;
	}

	private static boolean overlap(List<CellKey> a, List<CellKey> b) {
		int i = 0, j = 0;
		while (i < a.size() && j < b.size()) {
			int c = a.get(i).compareTo(b.get(j));
			if (c == 0) return true;
			if (c < 0) i++;
			else j++;
		}
		return false;
	}

	private boolean survives(CellKey a) {
		Boolean s = survives.get(a);
		if (s != null) return s;
		Claim ca = claim(a);
		boolean ok = true;
		for (CellKey b : claimantsNear(ca.d().centreX(), ca.d().centreZ(), ca.radius() + R)) {
			if (b.equals(a)) continue;
			Claim cb = claim(b);
			long dx = (long) cb.d().centreX() - ca.d().centreX(), dz = (long) cb.d().centreZ() - ca.d().centreZ();
			long rr = (long) ca.radius() + cb.radius();
			if (dx * dx + dz * dz > rr * rr) continue;
			if (before(b, cb.priority(), a, ca.priority()) && overlap(ca.cells(), cb.cells())) {
				ok = false;
				break;
			}
		}
		survives.putIfAbsent(a, ok);
		return ok;
	}

	/** The cell's final result; the same value whatever was asked before. */
	public Resolved cell(int cx, int cz) {
		CellKey k = new CellKey(cx, cz);
		Resolved r = resolved.get(k);
		if (r != null) return r;
		r = compute(k);
		Resolved prev = resolved.putIfAbsent(k, r);
		return prev != null ? prev : r;
	}

	private Resolved compute(CellKey k) {
		CellKey winner = null;
		for (CellKey a : claimantsNear(field.siteX(k.cx(), k.cz()), field.siteZ(k.cx(), k.cz()), R)) {
			if (Collections.binarySearch(claim(a).cells(), k) < 0 || !survives(a)) continue;
			if (winner != null) throw new IllegalStateException("cell " + k + " claimed by two survivors " + winner + " and " + a);
			winner = a;
		}
		if (winner == null) return new Resolved(decision(k), null, null);
		Claim c = claim(winner);
		SettlementSite site = new SettlementSite(winner, c.d().centreX(), c.d().centreZ(), c.radius(), c.cells());
		if (winner.equals(k)) return new Resolved(c.d().withRadius(c.radius()), null, site);
		return new Resolved(decision(k), winner, site);
	}

	/** The settlement this cell belongs to, as owner or member; null when it decides alone. */
	public SettlementSite site(int cx, int cz) {
		return cell(cx, cz).site();
	}

	/** Resolved cells in (cz, cx) order for every cell whose probe reach lies inside the window (as {@link RegionPlanner#decideWindow}). */
	public List<Resolved> resolveWindow(int x0, int z0, int x1, int z1) {
		List<Resolved> out = new ArrayList<>();
		for (CellDecision d : planner.decideWindow(x0, z0, x1, z1)) out.add(cell(d.cx(), d.cz()));
		return out;
	}
}
