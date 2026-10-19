package dev.fractalbuildings.core.gen.v1.types.block;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.mass.MassingOps;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.style.MassingStyle;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * Massing of a city block (step 4.6 T-block): a perimeter ring of row units, each a ground mass of
 * 2-5 storeys that stands on its street, joined to its neighbours by shared party walls (one wall
 * strip, no door), around an open courtyard.
 *
 * <p>Layout in the local frame (north = z 0): the north and south rows span the whole width and own
 * the four corners; the west and east columns run between them. A column's width is at least two
 * less than the corner units it hangs from, so it shares a wall with the corner unit and never meets
 * the next unit of the row. Units are 6-12 wide along their street and 8-10 deep; neighbours differ in
 * depth by 0-1 and in height by the draw. Sometimes one joint of the north or south row is a 3-4 wide
 * alley instead of a party wall, which makes the courtyard reachable from the street. Each unit
 * more than one storey high has its own core, away from its street side.
 */
public final class BlockMassing {
	private BlockMassing() {}

	public static final int MIN_W = 6, MAX_W = 12, THICK_MIN = 8, THICK_MAX = 10;
	/** Share of the lot (envelope plus its 1-block margin) the courtyard must have at least. */
	public static final double MIN_COURT = 0.22;

	/** One unit before it becomes a mass: footprint and storeys. */
	record Unit(int x, int z, int sx, int sz, int storeys) {}

	public static Massing build(BuildContext c, int maxStoreys) {
		for (int attempt = 0; attempt < 24; attempt++) {
			Massing m = attempt(c, maxStoreys, attempt);
			if (m != null) return m;
		}
		throw new IllegalStateException("no city-block massing for " + c.width() + "x" + c.depth());
	}

	/** The side of the envelope a unit stands on: its street. Rows own the corners. */
	public static Facing street(Mass m, int width, int depth) {
		if (m.z() == 0) return Facing.NORTH;
		if (m.maxZ() == depth) return Facing.SOUTH;
		if (m.x() == 0) return Facing.WEST;
		return Facing.EAST;
	}

	private static Massing attempt(BuildContext c, int maxStoreys, int attempt) {
		int W = c.width(), D = c.depth();
		Draws r = c.draws("mass/block", attempt);
		int thickMax = Math.max(THICK_MIN, THICK_MAX - attempt / 3);
		int tN = r.range(THICK_MIN, thickMax), tS = r.range(THICK_MIN, thickMax);
		int tW = r.range(THICK_MIN, thickMax), tE = r.range(THICK_MIN, thickMax);
		int alleyRow = r.chance(1, 2) ? r.below(2) : -1; // 0 north, 1 south
		int gap = r.range(3, 4);
		List<int[]> north = row(W, tW + 2, tE + 2, alleyRow == 0 ? gap : 0, r);
		List<int[]> south = row(W, tW + 2, tE + 2, alleyRow == 1 ? gap : 0, r);
		if (north == null || south == null) return null;
		List<Unit> units = new ArrayList<>();
		int[] nDepth = new int[north.size()], sDepth = new int[south.size()];
		for (int i = 0; i < north.size(); i++) nDepth[i] = tN + r.range(0, 1);
		for (int i = 0; i < south.size(); i++) sDepth[i] = tS + r.range(0, 1);
		// storeys: drawn later, footprints first (order: north row, south row, west column, east column)
		List<int[]> fp = new ArrayList<>();
		for (int i = 0; i < north.size(); i++) fp.add(new int[] {north.get(i)[0], 0, north.get(i)[1], nDepth[i]});
		for (int i = 0; i < south.size(); i++) fp.add(new int[] {south.get(i)[0], D - sDepth[i], south.get(i)[1], sDepth[i]});
		int nw = nDepth[0], ne = nDepth[north.size() - 1], sw = sDepth[0], se = sDepth[south.size() - 1];
		if (!column(fp, 0, tW, nw - 1, D - sw + 1, r) || !column(fp, W - tE, tE, ne - 1, D - se + 1, r)) return null;
		// courtyard share
		boolean[][] cov = new boolean[W][D];
		for (int[] f : fp)
			for (int x = f[0]; x < f[0] + f[2]; x++) for (int z = f[1]; z < f[1] + f[3]; z++) cov[x][z] = true;
		if (courtyard(cov, W, D) < MIN_COURT * (W + 10) * (D + 10)) return null;
		// heights
		int cap = maxStoreys;
		while (cap > 1 && StoreyGrid.of(cap, c.style().massing().ground() + c.style().massing().groundExtra(), c.style().massing().typical()).y(cap) + 2 > c.spec().maxHeight()) cap--;
		maxStoreys = cap;
		int base = Math.max(1, Math.min(maxStoreys, c.spec().storeys()));
		int hi = Math.min(maxStoreys, base + 1), lo = Math.max(1, hi - 2);
		int[] st = new int[fp.size()];
		for (int i = 0; i < st.length; i++) st[i] = lo + r.below(hi - lo + 1);
		if (st.length >= 8 && hi - lo >= 2) {
			boolean[] seen = new boolean[hi + 1];
			for (int s : st) seen[s] = true;
			int missing = 0;
			for (int s = lo; s <= hi; s++) if (!seen[s]) missing++;
			for (int s = lo, k = 1; s <= hi && missing > 0; s++) {
				if (!seen[s]) { st[k] = s; k += 3; missing--; }
			}
		}
		for (int i = 0; i < st.length; i++) units.add(new Unit(fp.get(i)[0], fp.get(i)[1], fp.get(i)[2], fp.get(i)[3], st[i]));
		return assemble(c, units, r, W, D);
	}

	/**
	 * Units along a street of length {@code len}: {x, size} per unit, first and last sized at least
	 * {@code minFirst} / {@code minLast}, neighbours sharing one wall column, except across an alley of
	 * {@code gap} blocks (0 = none) at a random joint. Null if no count of units fits.
	 */
	static List<int[]> row(int len, int minFirst, int minLast, int gap, Draws r) {
		int first = r.range(minFirst, MAX_W), last = r.range(minLast, MAX_W);
		List<Integer> ns = new ArrayList<>();
		for (int n = 2; n <= 40; n++) {
			int joints = n - 1;
			int total = len + joints - (gap > 0 ? gap + 1 : 0);
			int lo = first + last + MIN_W * (n - 2), hi = first + last + MAX_W * (n - 2);
			if (total >= lo && total <= hi) ns.add(n);
		}
		if (ns.isEmpty()) return null;
		int n = ns.get(r.below(ns.size()));
		int total = len + (n - 1) - (gap > 0 ? gap + 1 : 0);
		int[] w = new int[n];
		w[0] = first;
		w[n - 1] = last;
		int extra = total - first - last - MIN_W * (n - 2);
		for (int i = 1; i < n - 1; i++) w[i] = MIN_W;
		while (extra > 0) {
			int i = 1 + r.below(n - 2 > 0 ? n - 2 : 1);
			if (n > 2 && w[i] < MAX_W) { w[i]++; extra--; }
		}
		int alley = gap > 0 ? r.below(n - 1) : -1;
		List<int[]> out = new ArrayList<>();
		int x = 0;
		for (int i = 0; i < n; i++) {
			out.add(new int[] {x, w[i]});
			x += w[i] - 1;
			if (i == alley) x += gap + 1;
		}
		return out;
	}

	/** Adds the units of a side column of {@code width} blocks at x between z0 (shared with the corner above) and z1. */
	private static boolean column(List<int[]> fp, int x, int width, int z0, int z1, Draws r) {
		int len = z1 - z0;
		List<Integer> ks = new ArrayList<>();
		for (int k = 1; k <= 20; k++) if (len + k - 1 >= MIN_W * k && len + k - 1 <= MAX_W * k) ks.add(k);
		if (ks.isEmpty()) return false;
		int k = ks.get(r.below(ks.size()));
		int total = len + k - 1;
		int[] w = new int[k];
		java.util.Arrays.fill(w, MIN_W);
		int extra = total - MIN_W * k;
		while (extra > 0) {
			int i = r.below(k);
			if (w[i] < MAX_W) { w[i]++; extra--; }
		}
		int z = z0;
		for (int i = 0; i < k; i++) {
			fp.add(new int[] {x, z, width, w[i]});
			z += w[i] - 1;
		}
		return true;
	}

	/** Cells not under any unit that have a unit on every side somewhere along their row and column. */
	public static int courtyard(boolean[][] cov, int W, int D) {
		int n = 0;
		for (int x = 0; x < W; x++)
			for (int z = 0; z < D; z++) {
				if (cov[x][z]) continue;
				boolean l = false, rr = false, u = false, d = false;
				for (int i = 0; i < x; i++) l |= cov[i][z];
				for (int i = x + 1; i < W; i++) rr |= cov[i][z];
				for (int j = 0; j < z; j++) u |= cov[x][j];
				for (int j = z + 1; j < D; j++) d |= cov[x][j];
				if (l && rr && u && d) n++;
			}
		return n;
	}

	private static Massing assemble(BuildContext c, List<Unit> units, Draws r, int W, int D) {
		MassingStyle st = c.style().massing();
		int count = 1;
		for (Unit u : units) count = Math.max(count, u.storeys());
		StoreyGrid g = MassingOps.grid(st, count, c.draws("mass/block-ground"));
		List<Mass> ms = new ArrayList<>();
		for (int i = 0; i < units.size(); i++) {
			Unit u = units.get(i);
			ms.add(Mass.ground(i == 0 ? "main0" : "unit" + i, i == 0 ? "main" : "unit", u.x(), u.z(), u.sx(), u.sz(), u.storeys()));
		}
		List<Core> cores = new ArrayList<>();
		for (int i = 0; i < ms.size(); i++) {
			Mass m = ms.get(i);
			if (m.to() < 2) continue;
			Core k = core(g, m, street(m, W, D), r);
			if (k == null) {
				// no room for a stair: this unit stays one storey
				ms.set(i, Mass.ground(m.id(), m.kind(), m.x(), m.z(), m.sx(), m.sz(), 1));
				continue;
			}
			cores.add(k);
		}
		int top = 1;
		for (Mass m : ms) top = Math.max(top, m.to());
		if (top != g.count()) g = MassingOps.grid(st, top, c.draws("mass/block-ground"));
		for (int i = 0; i < cores.size(); i++) { // keep core ranges consistent with the (possibly shorter) grid
			Core k = cores.get(i);
			if (k.to() > g.count()) return null;
		}
		List<String> entrances = new ArrayList<>();
		for (Mass m : ms) entrances.add(m.id());
		List<Massing.Party> parties = new ArrayList<>();
		for (int i = 0; i < ms.size(); i++)
			for (int j = i + 1; j < ms.size(); j++) if (Massing.strip(ms.get(i), ms.get(j)) != null) parties.add(new Massing.Party(ms.get(i).id(), ms.get(j).id()));
		Massing out = new Massing(g, ms, cores, entrances, parties);
		return MassingCheck.check(out, W, D, c.spec().maxHeight()).isEmpty() ? out : null;
	}

	/** A core for the unit on storeys [0, to), in the half away from its street if it can. */
	static Core core(StoreyGrid g, Mass m, Facing street, Draws r) {
		int to = m.to();
		int len = Core.length(g, 0, to);
		int ix0 = m.x() + 1, ix1 = m.maxX() - 1, iz0 = m.z() + 1, iz1 = m.maxZ() - 1;
		List<Core> back = new ArrayList<>(), front = new ArrayList<>();
		// long side along x: two-row bands at the north and south inside rows
		if (ix1 - ix0 >= len && iz1 - iz0 >= 3) {
			int[] xs = {ix0, ix1 - len, ix0 + (ix1 - ix0 - len) / 2};
			for (int x : xs) {
				(street == Facing.NORTH ? front : back).add(new Core(x, iz0, len, 2, 0, to));
				(street == Facing.SOUTH ? front : back).add(new Core(x, iz1 - 2, len, 2, 0, to));
			}
		}
		// long side along z: two-column bands at the west and east inside columns
		if (iz1 - iz0 >= len && ix1 - ix0 >= 3) {
			int[] zs = {iz0, iz1 - len, iz0 + (iz1 - iz0 - len) / 2};
			for (int z : zs) {
				(street == Facing.WEST ? front : back).add(new Core(ix0, z, 2, len, 0, to));
				(street == Facing.EAST ? front : back).add(new Core(ix1 - 2, z, 2, len, 0, to));
			}
		}
		List<Core> pick = back.isEmpty() ? front : back;
		return pick.isEmpty() ? null : pick.get(r.below(pick.size()));
	}

}
