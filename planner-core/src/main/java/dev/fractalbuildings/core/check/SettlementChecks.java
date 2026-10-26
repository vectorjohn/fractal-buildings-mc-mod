package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.gen.v1.SpecPolicyV1;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.Types;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CellGeometry;
import dev.fractalbuildings.core.settlement.DistrictInfo;
import dev.fractalbuildings.core.settlement.CellSkeleton;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.Gate;
import dev.fractalbuildings.core.settlement.GateKind;
import dev.fractalbuildings.core.settlement.Road;
import dev.fractalbuildings.core.settlement.RoadKind;
import dev.fractalbuildings.core.settlement.Landmark;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.settlement.Plaza;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.Rect;
import dev.fractalbuildings.core.settlement.Street;
import java.util.ArrayList;
import java.util.List;

/**
 * Settlement invariants (docs/plans/phase5.md "Settlement invariants"), kept apart from {@link Invariants} so
 * parallel steps do not conflict. Each rule is a method returning violations (empty = holds) with a stable id; steps
 * add their rules here. Violation {@code building} is null; the message names the lots.
 */
public final class SettlementChecks {
	private SettlementChecks() {}

	public static final String LOT_IN_TILE = "lot-in-tile";
	public static final String LOTS_DISJOINT = "lots-disjoint";
	public static final String SKELETON_IN_CELL = "skeleton-in-cell";
	public static final String CELL_GATE_AGREE = "cell-gate-agree";
	public static final String ROAD_GRADE = "road-grade";

	/** Every rule that needs only a district plan and the landmarks inside it. */
	public static List<Violation> district(DistrictPlan d, List<Landmark> landmarks) {
		List<Violation> out = new ArrayList<>(lotInTile(d));
		out.addAll(lotsDisjoint(d, landmarks));
		return out;
	}

	/** {@code lot-in-tile}: every lot lies inside its district tile. */
	public static List<Violation> lotInTile(DistrictPlan d) {
		List<Violation> out = new ArrayList<>();
		Rect tile = d.district().rect();
		for (LotBrief l : d.lots()) {
			if (!tile.containsRect(l.x(), l.z(), l.w(), l.d())) {
				out.add(v(LOT_IN_TILE, "lot " + l.id() + " lies outside tile " + d.district().id(), l.x(), l.z(), l.w(), l.d()));
			}
		}
		return out;
	}

	/** {@code lots-disjoint}: lots overlap no other lot, street, plaza or landmark. */
	public static List<Violation> lotsDisjoint(DistrictPlan d, List<Landmark> landmarks) {
		List<Violation> out = new ArrayList<>();
		List<LotBrief> lots = d.lots();
		for (int i = 0; i < lots.size(); i++) {
			LotBrief a = lots.get(i);
			for (int j = i + 1; j < lots.size(); j++) {
				LotBrief b = lots.get(j);
				if (Rect.overlap(a.x(), a.z(), a.w(), a.d(), b.x(), b.z(), b.w(), b.d())) {
					out.add(v(LOTS_DISJOINT, "lots " + a.id() + " and " + b.id() + " overlap", a.x(), a.z(), a.w(), a.d()));
				}
			}
			for (Street s : d.streets()) {
				for (Rect r : footprint(s)) {
					if (Rect.overlap(a.x(), a.z(), a.w(), a.d(), r.x(), r.z(), r.w(), r.d()) && !inGatePassage(a, r)) {
						out.add(v(LOTS_DISJOINT, "lot " + a.id() + " overlaps street " + s.id(), a.x(), a.z(), a.w(), a.d()));
						break;
					}
				}
			}
			for (Plaza p : d.plazas()) {
				if (Rect.overlap(a.x(), a.z(), a.w(), a.d(), p.x(), p.z(), p.w(), p.d())) {
					out.add(v(LOTS_DISJOINT, "lot " + a.id() + " overlaps plaza " + p.id(), a.x(), a.z(), a.w(), a.d()));
				}
			}
			for (Landmark m : landmarks) {
				if (Rect.overlap(a.x(), a.z(), a.w(), a.d(), m.x(), m.z(), m.w(), m.d())) {
					out.add(v(LOTS_DISJOINT, "lot " + a.id() + " overlaps landmark " + m.id(), a.x(), a.z(), a.w(), a.d()));
				}
			}
		}
		return out;
	}

	/** A street may cross a ring gate lot (a {@code wall} lot exactly 23 long) within its passage: the 5 columns each side of the lot's middle, through its depth. */
	static boolean inGatePassage(LotBrief l, Rect street) {
		if (!dev.fractalbuildings.core.settlement.tiers.WallRing.isGateShape(l.type(), l.front(), l.w(), l.d())) return false;
		boolean alongX = l.front() == Facing.NORTH || l.front() == Facing.SOUTH;
		int gate = alongX ? l.x() + l.w() / 2 : l.z() + l.d() / 2, b = dev.fractalbuildings.core.settlement.tiers.WallRing.BAND;
		int lo = Math.max(alongX ? street.x() : street.z(), gate - b), hi = Math.min(alongX ? street.x() + street.w() : street.z() + street.d(), gate + b + 1);
		int sLo = alongX ? street.x() : street.z(), sHi = alongX ? street.x() + street.w() : street.z() + street.d();
		return sLo >= gate - b && sHi <= gate + b + 1 && lo < hi;
	}

	/**
	 * The columns a street covers, as rectangles: for each point, a {@code width} square centred on it (columns
	 * {@code p - width/2 .. p - width/2 + width - 1}), merged per axis-aligned segment; diagonal segments contribute
	 * one square per step.
	 */
	public static List<Rect> footprint(Street s) {
		List<Rect> out = new ArrayList<>();
		int w = s.width(), h = w / 2;
		List<Pt> ps = s.points();
		for (int i = 1; i < ps.size(); i++) {
			Pt a = ps.get(i - 1), b = ps.get(i);
			if (a.x() == b.x() || a.z() == b.z()) {
				int x0 = Math.min(a.x(), b.x()) - h, z0 = Math.min(a.z(), b.z()) - h;
				out.add(new Rect(x0, z0, Math.abs(b.x() - a.x()) + w, Math.abs(b.z() - a.z()) + w));
			} else {
				int n = Math.abs(b.x() - a.x()), sx = Integer.signum(b.x() - a.x()), sz = Integer.signum(b.z() - a.z());
				for (int k = 0; k <= n; k++) out.add(new Rect(a.x() + sx * k - h, a.z() + sz * k - h, w, w));
			}
		}
		return out;
	}

	/**
	 * {@code skeleton-in-cell}: every road point of a cell skeleton is a lattice column or segment end the cell owns (nearest
	 * region site), or one of its own gate columns, which sit on the border and may belong to the neighbour.
	 */
	public static List<Violation> skeletonInCell(List<CellSkeleton> cells, RegionField field) {
		List<Violation> out = new ArrayList<>();
		for (CellSkeleton s : cells) {
			for (Road r : s.roads()) {
				for (Pt p : r.points()) {
					boolean gate = false;
					for (Gate g : s.gates()) if (g.x() == p.x() && g.z() == p.z()) gate = true;
					if (!gate && !CellGeometry.owns(field, s.cell(), p.x(), p.z())) {
						out.add(v(SKELETON_IN_CELL, "road " + r.id() + " of cell " + s.cell().cx() + "," + s.cell().cz() + " leaves the cell at " + p.x() + "," + p.z(), p.x(), p.z(), 1, 1));
						break;
					}
				}
			}
		}
		return out;
	}

	/**
	 * {@code cell-gate-agree}: every cell gate of a skeleton is also a gate of the neighbour it names, at the same column, and
	 * every main road that ends outside its cell ends on a cell gate.
	 */
	public static List<Violation> cellGateAgree(List<CellSkeleton> cells) {
		List<Violation> out = new ArrayList<>();
		for (CellSkeleton s : cells) {
			for (Gate g : s.gates()) {
				if (g.kind() != GateKind.CELL) continue;
				Cell other = g.cells().get(0).equals(s.cell()) ? g.cells().get(1) : g.cells().get(0);
				boolean found = false, known = false;
				for (CellSkeleton o : cells) {
					if (!o.cell().equals(other)) continue;
					known = true;
					for (Gate og : o.gates()) if (og.kind() == GateKind.CELL && og.x() == g.x() && og.z() == g.z() && og.cells().equals(g.cells())) found = true;
				}
				if (known && !found) out.add(v(CELL_GATE_AGREE, "gate at " + g.x() + "," + g.z() + " of cell " + s.cell().cx() + "," + s.cell().cz() + " is missing in " + other.cx() + "," + other.cz(), g.x(), g.z(), 1, 1));
			}
			for (Road r : s.roads()) {
				if (r.kind() != RoadKind.MAIN && r.kind() != RoadKind.PATH) continue;
				Pt end = r.points().get(r.points().size() - 1);
				boolean ends = false;
				for (Gate g : s.gates()) if (g.x() == end.x() && g.z() == end.z()) ends = true;
				if (!ends) out.add(v(CELL_GATE_AGREE, "road " + r.id() + " ends at " + end.x() + "," + end.z() + " on no gate", end.x(), end.z(), 1, 1));
			}
		}
		return out;
	}

	/**
	 * {@code road-grade}: along a street's graded centre line (one height per column) adjacent columns differ by at most 1
	 * and columns 3 apart by at most 1. Reports the first failing column of the street.
	 */
	public static List<Violation> roadGrade(String streetId, int[] x, int[] z, int[] y) {
		List<Violation> out = new ArrayList<>();
		for (int i = 1; i < y.length; i++) {
			if (Math.abs(y[i] - y[i - 1]) > 1 || (i >= 3 && Math.abs(y[i] - y[i - 3]) > 1)) {
				out.add(v(ROAD_GRADE, "street " + streetId + " is too steep at column " + i + " (y " + y[i - 1] + " to " + y[i] + ")", x[i], z[i], 1, 1));
				break;
			}
		}
		return out;
	}


	// ---- district detail (plan step DL)

	public static final String LOT_FRONTAGE = "lot-frontage";
	public static final String LOT_DRY = "lot-dry";
	public static final String LOT_GRADE = "lot-grade";
	public static final String ROADS_CONNECTED = "roads-connected";
	/** Whole-city rule (ROADS2): every street or plaza column of the city reaches the hub through street and plaza columns; no detached road. */
	public static final String ROADS_ATTACHED = "roads-attached";
	public static final String TILE_BORDER_AGREE = "tile-border-agree";
	/** Blocks of the plan's own coverage raster round the tile (streets reach this far over the border). */
	public static final int COVER_PAD = 16;

	/** The rules that need only the plan: the basic ones plus {@code lot-frontage} and {@code roads-connected}. */
	public static List<Violation> detail(DistrictPlan d, List<Landmark> landmarks) {
		List<Violation> out = new ArrayList<>(district(d, landmarks));
		out.addAll(lotFrontage(d));
		out.addAll(roadsConnected(d));
		return out;
	}

	/** Street and plaza coverage of a plan over its tile plus {@link #COVER_PAD} blocks; streets also carry their index (+1) for component tests. */
	public static final class Cover {
		public final int x0, z0, w, d;
		final boolean[] street, plaza;
		final short[] label;

		Cover(DistrictPlan p) {
			Rect t = p.district().rect();
			x0 = t.x() - COVER_PAD;
			z0 = t.z() - COVER_PAD;
			w = t.w() + 2 * COVER_PAD;
			d = t.d() + 2 * COVER_PAD;
			street = new boolean[w * d];
			plaza = new boolean[w * d];
			label = new short[w * d];
			for (int i = 0; i < p.streets().size(); i++) {
				for (Rect r : footprint(p.streets().get(i))) {
					for (int z = Math.max(r.z(), z0); z < Math.min(r.z() + r.d(), z0 + d); z++) {
						for (int x = Math.max(r.x(), x0); x < Math.min(r.x() + r.w(), x0 + w); x++) {
							street[(z - z0) * w + (x - x0)] = true;
							if (label[(z - z0) * w + (x - x0)] == 0) label[(z - z0) * w + (x - x0)] = (short) (i + 1);
						}
					}
				}
			}
			for (Plaza pz : p.plazas()) {
				for (int z = Math.max(pz.z(), z0); z < Math.min(pz.z() + pz.d(), z0 + d); z++) {
					for (int x = Math.max(pz.x(), x0); x < Math.min(pz.x() + pz.w(), x0 + w); x++) plaza[(z - z0) * w + (x - x0)] = true;
				}
			}
		}

		boolean in(int x, int z) { return x >= x0 && z >= z0 && x < x0 + w && z < z0 + d; }

		/** A street covers the column. */
		public boolean street(int x, int z) { return in(x, z) && street[(z - z0) * w + (x - x0)]; }

		/** A street or plaza covers the column. */
		public boolean open(int x, int z) { return in(x, z) && (street[(z - z0) * w + (x - x0)] || plaza[(z - z0) * w + (x - x0)]); }
	}

	/**
	 * {@code lot-frontage}: the columns just outside a lot's front edge are street or plaza along at least 3 of them (all, for a
	 * front shorter than 3).
	 */
	public static List<Violation> lotFrontage(DistrictPlan d) {
		List<Violation> out = new ArrayList<>();
		Cover c = new Cover(d);
		for (LotBrief l : d.lots()) {
			if (dev.fractalbuildings.core.settlement.tiers.WallRing.isRingLotId(l.id())) continue; // the wall faces the field
			int n = l.front().dx != 0 ? l.d() : l.w(), hit = 0;
			for (int k = 0; k < n; k++) {
				int x = switch (l.front()) { case EAST -> l.x() + l.w(); case WEST -> l.x() - 1; default -> l.x() + k; };
				int z = switch (l.front()) { case SOUTH -> l.z() + l.d(); case NORTH -> l.z() - 1; default -> l.z() + k; };
				if (c.open(x, z)) hit++;
			}
			if (hit < Math.min(3, n)) out.add(v(LOT_FRONTAGE, "lot " + l.id() + " has street or plaza along only " + hit + " columns of its " + l.front().json() + " front", l.x(), l.z(), l.w(), l.d()));
		}
		return out;
	}

	/** {@code lot-dry}: no column of a lot has water above its floor. */
	public static List<Violation> lotDry(DistrictPlan d, TerrainProbe exact) {
		List<Violation> out = new ArrayList<>();
		for (LotBrief l : d.lots()) {
			outer:
			for (int z = l.z(); z < l.z() + l.d(); z++) {
				for (int x = l.x(); x < l.x() + l.w(); x++) {
					if (exact.waterDepth(x, z) > 0) {
						out.add(v(LOT_DRY, "lot " + l.id() + " has water at " + x + "," + z, l.x(), l.z(), l.w(), l.d()));
						break outer;
					}
				}
			}
		}
		return out;
	}

	/** {@code lot-grade}: every built lot's type exists, fits the lot's size along its front and accepts its relief (so the spec policy never throws). */
	public static List<Violation> lotGrade(DistrictPlan d, TerrainProbe exact) {
		List<Violation> out = new ArrayList<>();
		for (LotBrief l : d.lots()) {
			if (!l.builds()) continue;
			if (!Types.ids().contains(l.type())) { out.add(v(LOT_GRADE, "lot " + l.id() + " has unknown type " + l.type(), l.x(), l.z(), l.w(), l.d())); continue; }
			TypeLimits lim = Types.get(l.type()).limits();
			boolean alongX = l.front() == Facing.NORTH || l.front() == Facing.SOUTH;
			int lw = alongX ? l.w() : l.d(), ld = alongX ? l.d() : l.w();
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int z = l.z(); z < l.z() + l.d(); z++) for (int x = l.x(); x < l.x() + l.w(); x++) { int y = exact.floorY(x, z); lo = Math.min(lo, y); hi = Math.max(hi, y); }
			if (lw < lim.minLotWidth() || ld < lim.minLotDepth()) out.add(v(LOT_GRADE, "lot " + l.id() + " (" + lw + "x" + ld + ") is too small for a " + l.type(), l.x(), l.z(), l.w(), l.d()));
			else if (!SpecPolicyV1.reliefOk(lim, hi - lo, l.w(), l.d())) out.add(v(LOT_GRADE, "lot " + l.id() + " has relief " + (hi - lo) + ", too steep for a " + l.type(), l.x(), l.z(), l.w(), l.d()));
			else if (!SpecPolicyV1.railRoomOk(l.type(), lim, hi - lo, lw, ld)) out.add(v(LOT_GRADE, "lot " + l.id() + " (" + lw + "x" + ld + ", relief " + (hi - lo) + ") has no room to set a " + l.type() + " back for a railed flight", l.x(), l.z(), l.w(), l.d()));
		}
		return out;
	}

	/**
	 * {@code roads-connected} (district part): every group of streets whose footprints overlap or touch reaches a skeleton road (arterial,
	 * main road or path) or the tile border (a street point on or beyond the tile's outermost columns), where a neighbour's street
	 * continues. The whole-city rule (every street reaches the hub) is D0's.
	 */
	public static List<Violation> roadsConnected(DistrictPlan d) {
		List<Violation> out = new ArrayList<>();
		int n = d.streets().size();
		if (n == 0) return out;
		Cover c = new Cover(d);
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) parent[i] = i;
		for (int i = 0; i < n; i++) {
			for (Rect r : footprint(d.streets().get(i))) {
				for (int z = Math.max(r.z(), c.z0); z < Math.min(r.z() + r.d(), c.z0 + c.d); z++) {
					for (int x = Math.max(r.x(), c.x0); x < Math.min(r.x() + r.w(), c.x0 + c.w); x++) {
						int l = c.label[(z - c.z0) * c.w + (x - c.x0)] - 1;
						if (l != i) union(parent, l, i);
					}
				}
			}
		}
		for (int z = 0; z < c.d; z++) {
			for (int x = 0; x < c.w; x++) {
				int a = c.label[z * c.w + x] - 1;
				if (a < 0) continue;
				if (x + 1 < c.w && c.label[z * c.w + x + 1] != 0) union(parent, a, c.label[z * c.w + x + 1] - 1);
				if (z + 1 < c.d && c.label[(z + 1) * c.w + x] != 0) union(parent, a, c.label[(z + 1) * c.w + x] - 1);
			}
		}
		Rect t = d.district().rect();
		boolean[] good = new boolean[n];
		for (int i = 0; i < n; i++) {
			Street s = d.streets().get(i);
			boolean root = s.kind() == RoadKind.ARTERIAL || s.kind() == RoadKind.MAIN || s.kind() == RoadKind.PATH || s.kind() == RoadKind.QUAY;
			for (Pt p : s.points()) if (p.x() <= t.x() || p.x() >= t.x() + t.w() - 1 || p.z() <= t.z() || p.z() >= t.z() + t.d() - 1) root = true;
			if (root) good[find(parent, i)] = true;
		}
		java.util.TreeMap<Integer, List<String>> bad = new java.util.TreeMap<>();
		for (int i = 0; i < n; i++) if (!good[find(parent, i)]) bad.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(d.streets().get(i).id());
		for (List<String> ids : bad.values()) {
			Pt p = d.streets().get(d.streets().stream().map(Street::id).toList().indexOf(ids.get(0))).points().get(0);
			out.add(v(ROADS_CONNECTED, "streets " + ids + " reach no skeleton road and not the tile border", p.x(), p.z(), 1, 1));
		}
		return out;
	}

	private static int find(int[] p, int a) {
		while (p[a] != a) { p[a] = p[p[a]]; a = p[a]; }
		return a;
	}

	private static void union(int[] p, int a, int b) {
		a = find(p, a);
		b = find(p, b);
		if (a != b) p[Math.max(a, b)] = Math.min(a, b);
	}

	/**
	 * {@code tile-border-agree}: for two plans of neighbouring tiles, the street and plaza coverage of the columns within 3 of their
	 * shared border is the same in both. Returns nothing for tiles that do not share a border.
	 */
	public static List<Violation> tileBorderAgree(DistrictPlan a, DistrictPlan b) {
		Rect ra = a.district().rect(), rb = b.district().rect();
		DistrictPlan west = a, east = b;
		boolean vertical;
		if (ra.x() + ra.w() == rb.x()) vertical = true;
		else if (rb.x() + rb.w() == ra.x()) { west = b; east = a; vertical = true; }
		else if (ra.z() + ra.d() == rb.z()) vertical = false;
		else if (rb.z() + rb.d() == ra.z()) { west = b; east = a; vertical = false; }
		else return List.of();
		Rect rw = west.district().rect(), re = east.district().rect();
		int line = vertical ? rw.x() + rw.w() : rw.z() + rw.d();
		int lo = vertical ? Math.max(rw.z(), re.z()) : Math.max(rw.x(), re.x()), hi = vertical ? Math.min(rw.z() + rw.d(), re.z() + re.d()) : Math.min(rw.x() + rw.w(), re.x() + re.w());
		if (lo >= hi) return List.of();
		Cover cw = new Cover(west), ce = new Cover(east);
		List<Violation> out = new ArrayList<>();
		int bad = 0, first = Integer.MIN_VALUE;
		for (int t = lo; t < hi; t++) {
			for (int off = -3; off < 3; off++) {
				int x = vertical ? line + off : t, z = vertical ? t : line + off;
				if (cw.open(x, z) != ce.open(x, z)) { if (bad++ == 0) first = t; }
			}
		}
		if (bad > 0) {
			int x = vertical ? line - 3 : first, z = vertical ? first : line - 3;
			out.add(v(TILE_BORDER_AGREE, bad + " columns beside the border of " + west.district().id() + " and " + east.district().id() + " differ (first at " + (vertical ? "z " : "x ") + first + ")", x, z, vertical ? 6 : 1, vertical ? 1 : 6));
		}
		return out;
	}

	/** Longest distance from the hub to the nearest street or plaza column that the whole-city connectivity check accepts as "on the hub". */
	public static final int HUB_REACH = 24;

	/** At most this many unreachable pieces are listed individually. */
	static final int MAX_LISTED = 8;

	/**
	 * Result of {@link #cityRoads}: the violations, and the number of stray pieces (street fragments that reach neither the hub
	 * nor anything that matters: no skeleton road and no building lot on them).
	 */
	public record CityRoads(List<Violation> violations, int strays, int hubPiece, int total) {}

	/** {@link #cityRoads} violations only. */
	public static List<Violation> cityRoadsConnected(java.util.Collection<DistrictPlan> plans, Pt hub) {
		return cityRoads(plans, hub).violations();
	}

	/**
	 * {@code roads-connected} (city part, D0). The street and plaza columns of every planned district, taken together, split into
	 * 4-connected pieces (bridges are street points, so they join their banks); the piece nearest the hub (within
	 * {@link #HUB_REACH} blocks) is the hub's. Every other piece that holds the front
	 * of a lot that builds is one violation (its size, box and first lot): that building cannot be reached along streets. A hub
	 * with no street near it is one too. A piece without a lot front (a port, a grid line or an outlying arterial whose far end is
	 * dead) is a stray fragment, harmless to reachability, and only counted.
	 */
	public static CityRoads cityRoads(java.util.Collection<DistrictPlan> plans, Pt hub) {
		List<Violation> out = new ArrayList<>();
		if (plans.isEmpty()) return new CityRoads(out, 0, 0, 0);
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (DistrictPlan p : plans) {
			Rect t = p.district().rect();
			x0 = Math.min(x0, t.x() - COVER_PAD);
			z0 = Math.min(z0, t.z() - COVER_PAD);
			x1 = Math.max(x1, t.x() + t.w() + COVER_PAD);
			z1 = Math.max(z1, t.z() + t.d() + COVER_PAD);
		}
		int w = x1 - x0, d = z1 - z0;
		byte[] kind = new byte[w * d]; // 0 none, 1 street or plaza, 2 skeleton road
		boolean[] seen = new boolean[w * d];
		for (DistrictPlan p : plans) {
			for (Street s : p.streets()) {
				boolean sk = s.kind() == RoadKind.ARTERIAL || s.kind() == RoadKind.MAIN || s.kind() == RoadKind.PATH;
				for (Rect r : footprint(s)) paint(kind, r, x0, z0, w, d, sk ? 2 : 1);
			}
			for (Plaza pz : p.plazas()) paint(kind, new Rect(pz.x(), pz.z(), pz.w(), pz.d()), x0, z0, w, d, 1);
		}
		java.util.Map<Integer, String> frontOf = new java.util.TreeMap<>();
		for (DistrictPlan p : plans) {
			for (LotBrief l : p.lots()) {
				if (!l.builds()) continue;
				int n = l.front().dx != 0 ? l.d() : l.w();
				for (int k = 0; k < n; k++) {
					int x = (switch (l.front()) { case EAST -> l.x() + l.w(); case WEST -> l.x() - 1; default -> l.x() + k; }) - x0;
					int z = (switch (l.front()) { case SOUTH -> l.z() + l.d(); case NORTH -> l.z() - 1; default -> l.z() + k; }) - z0;
					if (x >= 0 && z >= 0 && x < w && z < d && kind[z * w + x] != 0) frontOf.putIfAbsent(z * w + x, l.id());
				}
			}
		}
		int start = -1, best = Integer.MAX_VALUE;
		if (hub != null) {
			for (int dz = -HUB_REACH; dz <= HUB_REACH; dz++) {
				for (int dx = -HUB_REACH; dx <= HUB_REACH; dx++) {
					int x = hub.x() + dx - x0, z = hub.z() + dz - z0;
					if (x < 0 || z < 0 || x >= w || z >= d || kind[z * w + x] == 0) continue;
					int dist = dx * dx + dz * dz;
					if (dist < best) { best = dist; start = z * w + x; }
				}
			}
			if (start < 0) out.add(v(ROADS_CONNECTED, "no street or plaza within " + HUB_REACH + " blocks of the hub", hub.x(), hub.z(), 1, 1));
		}
		Piece pc = new Piece(kind, seen, w, d, frontOf);
		int hubSize = 0, total = 0;
		if (start >= 0) { pc.flood(start); hubSize = pc.size; }
		int listed = 0, pieces = 0, strays = 0;
		List<Violation> strayList = new ArrayList<>();
		for (int i = 0; i < kind.length; i++) {
			if (kind[i] == 0 || seen[i]) continue;
			pc.flood(i);
			if (start < 0) continue;
			if (pc.lot == null) {
				strays++;
				if (strayList.size() < MAX_LISTED) strayList.add(v(ROADS_ATTACHED, "a detached piece of " + pc.size + " street columns (x " + (x0 + pc.minX) + ".." + (x0 + pc.maxX) + ", z " + (z0 + pc.minZ) + ".." + (z0 + pc.maxZ) + (pc.skeleton ? ", with a skeleton road" : "") + ") does not reach the hub", x0 + i % w, z0 + i / w, 1, 1));
				continue;
			}
			pieces++;
			if (listed++ < MAX_LISTED) {
				out.add(v(ROADS_CONNECTED, "a piece of " + pc.size + " street columns (x " + (x0 + pc.minX) + ".." + (x0 + pc.maxX) + ", z " + (z0 + pc.minZ) + ".." + (z0 + pc.maxZ) + ") with the front of lot " + pc.lot + (pc.skeleton ? " and a skeleton road" : "") + " does not reach the hub",
						x0 + i % w, z0 + i / w, 1, 1));
			}
		}
		if (start >= 0 && pieces > MAX_LISTED) out.add(v(ROADS_CONNECTED, (pieces - MAX_LISTED) + " more pieces do not reach the hub", hub.x(), hub.z(), 1, 1));
		if (start >= 0) {
			out.addAll(strayList);
			if (strays > strayList.size()) out.add(v(ROADS_ATTACHED, (strays - strayList.size()) + " more detached pieces do not reach the hub", hub.x(), hub.z(), 1, 1));
		}
		for (byte b : kind) if (b != 0) total++;
		return new CityRoads(out, strays, hubSize, total);
	}

	private static void paint(byte[] kind, Rect r, int x0, int z0, int w, int d, int k) {
		for (int z = Math.max(r.z(), z0); z < Math.min(r.z() + r.d(), z0 + d); z++)
			for (int x = Math.max(r.x(), x0); x < Math.min(r.x() + r.w(), x0 + w); x++) {
				int i = (z - z0) * w + x - x0;
				if (kind[i] < k) kind[i] = (byte) k;
			}
	}

	/** A 4-connected piece being flooded: its size, box, whether it holds a skeleton road, and the first lot front on it. */
	private static final class Piece {
		final byte[] kind;
		final boolean[] seen;
		final int w, d;
		final java.util.Map<Integer, String> frontOf;
		int[] stack = new int[1024];
		int size, minX, minZ, maxX, maxZ;
		boolean skeleton;
		String lot;

		Piece(byte[] kind, boolean[] seen, int w, int d, java.util.Map<Integer, String> frontOf) {
			this.kind = kind;
			this.seen = seen;
			this.w = w;
			this.d = d;
			this.frontOf = frontOf;
		}

		void flood(int from) {
			size = 0;
			minX = minZ = Integer.MAX_VALUE;
			maxX = maxZ = Integer.MIN_VALUE;
			skeleton = false;
			lot = null;
			int sp = 0;
			stack[sp++] = from;
			seen[from] = true;
			while (sp > 0) {
				int c = stack[--sp];
				size++;
				int x = c % w, z = c / w;
				minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
				if (kind[c] == 2) skeleton = true;
				if (lot == null) lot = frontOf.get(c);
				if (sp + 4 > stack.length) stack = java.util.Arrays.copyOf(stack, stack.length * 2);
				if (x > 0 && kind[c - 1] != 0 && !seen[c - 1]) { seen[c - 1] = true; stack[sp++] = c - 1; }
				if (x + 1 < w && kind[c + 1] != 0 && !seen[c + 1]) { seen[c + 1] = true; stack[sp++] = c + 1; }
				if (z > 0 && kind[c - w] != 0 && !seen[c - w]) { seen[c - w] = true; stack[sp++] = c - w; }
				if (z + 1 < d && kind[c + w] != 0 && !seen[c + w]) { seen[c + w] = true; stack[sp++] = c + w; }
			}
		}
	}

	/**
	 * {@code tile-border-agree} over a whole settlement: every pair of tiles that share a border (by tile index, east and south
	 * neighbours) agrees along it.
	 */
	public static List<Violation> cityTileBorders(java.util.Collection<DistrictPlan> plans) {
		java.util.Map<Long, DistrictPlan> by = new java.util.TreeMap<>();
		for (DistrictPlan p : plans) by.put(key(p.district().ix(), p.district().iz()), p);
		List<DistrictPlan> sorted = new ArrayList<>(plans);
		sorted.sort(java.util.Comparator.comparing((DistrictPlan p) -> p.district().id()));
		List<Violation> out = new ArrayList<>();
		for (DistrictPlan p : sorted) {
			DistrictPlan e = by.get(key(p.district().ix() + 1, p.district().iz()));
			if (e != null) out.addAll(tileBorderAgree(p, e));
			DistrictPlan s = by.get(key(p.district().ix(), p.district().iz() + 1));
			if (s != null) out.addAll(tileBorderAgree(p, s));
		}
		return out;
	}

	private static long key(int ix, int iz) { return ((long) ix << 32) ^ (iz & 0xffffffffL); }

	// ---- street junction heights (JUNCTION)

	/** Whole-city rule: where two different streets share ground (one ends on the other, or they cross) their surfaces differ by at most {@link #JUNCTION_STEP}. */
	public static final String JUNCTION_HEIGHT = "junction-height";
	public static final int JUNCTION_STEP = 1;

	/**
	 * One pair of streets whose footprints overlap, with the first cell more than {@link #JUNCTION_STEP} apart.
	 *
	 * @param a the street with the smaller id, b the other
	 * @param cells overlapping footprint cells, bad of them more than {@link #JUNCTION_STEP} apart
	 * @param maxDiff the largest difference
	 */
	public record Junction(String a, RoadKind ka, String b, RoadKind kb, int cells, int bad, int maxDiff, int x, int z, int ya, int yb) {}

	/** Surface level of each footprint cell of a street ({@code (z << 32) + x} to y): the first centre-line column to claim a cell gives its height, as the grader does. */
	static java.util.Map<Long, Integer> streetCells(Street s) { return streetCells(s, null, null); }

	/**
	 * {@link #streetCells(Street)}, and in {@code ends} the cells that the street's first or last {@code width / 2 + 1} columns claim (its end
	 * faces), leaving out an end that lies on the edge of the window a tile keeps its streets in ({@code tile}, widened by
	 * {@code Districts.MARGIN}): there the street goes on in the neighbouring tile.
	 */
	static java.util.Map<Long, Integer> streetCells(Street s, java.util.Set<Long> ends, DistrictInfo tile) {
		List<Pt> ps = s.points();
		List<long[]> cols = new ArrayList<>();
		int[] ys = s.y();
		int w = s.width(), h = w / 2;
		java.util.Map<Long, Integer> cells = new java.util.TreeMap<>();
		for (int p = 0; p < ps.size(); p++) {
			int n = 0, dx = 0, dz = 0;
			if (p > 0) {
				dx = ps.get(p).x() - ps.get(p - 1).x();
				dz = ps.get(p).z() - ps.get(p - 1).z();
				n = Math.max(Math.abs(dx), Math.abs(dz));
			}
			for (int k = p == 0 ? 0 : 1; k <= n; k++) {
				int cx, cz, y;
				if (p == 0) { cx = ps.get(0).x(); cz = ps.get(0).z(); y = ys[0]; }
				else {
					cx = ps.get(p - 1).x() + (int) Math.floorDiv(2L * dx * k + n, 2L * n);
					cz = ps.get(p - 1).z() + (int) Math.floorDiv(2L * dz * k + n, 2L * n);
					y = ys[p - 1] + (int) Math.floorDiv((long) (ys[p] - ys[p - 1]) * k, n);
				}
				cols.add(new long[] {cx, cz, y});
			}
		}
		int n = cols.size(), e = h + 1;
		boolean headEnd = ends != null && !onWindowEdge(cols.get(0), tile), tailEnd = ends != null && !onWindowEdge(cols.get(n - 1), tile);
		for (int i = 0; i < n; i++) {
			long[] c = cols.get(i);
			boolean end = (headEnd && i < e) || (tailEnd && i >= n - e);
			for (int oz = 0; oz < w; oz++) {
				for (int ox = 0; ox < w; ox++) {
					long k = ((c[1] - h + oz) << 32) | ((c[0] - h + ox) & 0xffffffffL);
					if (cells.putIfAbsent(k, (int) c[2]) == null && end) ends.add(k);
				}
			}
		}
		return cells;
	}

	/** The column lies on the edge of the tile's street window (tile plus {@code Districts.MARGIN} = 8), where district detail clips its streets. */
	private static boolean onWindowEdge(long[] c, DistrictInfo t) {
		if (t == null) return false;
		int m = 8;
		return c[0] <= t.x() - m || c[0] >= t.x() + t.w() - 1 + m || c[1] <= t.z() - m || c[1] >= t.z() + t.d() - 1 + m;
	}

	/**
	 * Every pair of streets in the plans whose footprints overlap, or touch where one of them ends (its end face against the other's side or end),
	 * in id order. Each cell is judged in the plan of the tile that builds
	 * it: a tile's ground is clipped to the tile ({@code Pieces.ground}), so the part of a street piece that lies in a neighbour's
	 * ground (the margin a tile keeps its streets in) is built by the neighbour, from the neighbour's plan, and is never compared.
	 */
	public static List<Junction> junctions(java.util.Collection<DistrictPlan> plans) {
		List<DistrictPlan> sorted = new ArrayList<>(plans);
		sorted.sort(java.util.Comparator.comparing((DistrictPlan p) -> p.district().id()));
		java.util.TreeMap<String, Junction> by = new java.util.TreeMap<>();
		for (DistrictPlan p : sorted) {
			java.util.Map<Long, List<Object[]>> at = new java.util.TreeMap<>(); // cell of this tile -> {street id, kind, y, end face}
			var t = p.district();
			for (Street s : p.streets()) {
				java.util.Set<Long> ends = new java.util.TreeSet<>();
				java.util.Map<Long, Integer> mine = streetCells(s, ends, t);
				for (var e : mine.entrySet()) {
					int x = (int) (long) e.getKey(), z = (int) (e.getKey() >> 32);
					if (x < t.x() || z < t.z() || x >= t.x() + t.w() || z >= t.z() + t.d()) continue;
					List<Object[]> here = at.computeIfAbsent(e.getKey(), k -> new ArrayList<>(2));
					int y = e.getValue();
					boolean dup = false;
					for (Object[] o : here) {
						String oid = (String) o[0];
						// a cell two pieces of one whole street cover is built by the first ({@code DistrictScenes.ground})
						if (oid.equals(s.id()) || wholeStreet(oid).equals(wholeStreet(s.id()))) { dup = true; break; }
					}
					if (dup) continue;
					for (Object[] o : here) meet(by, (String) o[0], (RoadKind) o[1], (Integer) o[2], s.id(), s.kind(), y, x, z);
					here.add(new Object[] {s.id(), s.kind(), y, ends.contains(e.getKey())});
				}
			}
			// a street that ends against another street's side (footprints touching across one cell edge, not overlapping) meets it there too
			for (var e : at.entrySet()) {
				int x = (int) (long) e.getKey(), z = (int) (e.getKey() >> 32);
				for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
					List<Object[]> nb = at.get(((long) (z + d[1]) << 32) | ((x + d[0]) & 0xffffffffL));
					if (nb == null) continue;
					for (Object[] a : e.getValue()) {
						for (Object[] b : nb) {
							if (!((Boolean) a[3] || (Boolean) b[3])) continue;
							String ia = (String) a[0], ib = (String) b[0];
							if (wholeStreet(ia).equals(wholeStreet(ib)) || covers(e.getValue(), ib) || covers(nb, ia)) continue; // overlapping: compared above
							meet(by, ia, (RoadKind) a[1], (Integer) a[2], ib, (RoadKind) b[1], (Integer) b[2], x, z);
						}
					}
				}
			}
		}
		return new ArrayList<>(by.values());
	}

	private static boolean covers(List<Object[]> cell, String id) {
		for (Object[] o : cell) if (wholeStreet((String) o[0]).equals(wholeStreet(id))) return true;
		return false;
	}

	/** Records one compared cell of the pair of streets. */
	private static void meet(java.util.TreeMap<String, Junction> by, String oid, RoadKind ok, int oy, String id, RoadKind k, int y, int x, int z) {
		boolean first = oid.compareTo(id) < 0;
		String ida = first ? oid : id, idb = first ? id : oid;
		RoadKind ka = first ? ok : k, kb = first ? k : ok;
		int ya = first ? oy : y, yb = first ? y : oy;
		String key = ida + "|" + idb;
		Junction j = by.get(key);
		int d = Math.abs(ya - yb);
		boolean isBad = d > JUNCTION_STEP;
		if (j == null) by.put(key, new Junction(ida, ka, idb, kb, 1, isBad ? 1 : 0, d, x, z, ya, yb));
		else if (!isBad) by.put(key, new Junction(ida, ka, idb, kb, j.cells() + 1, j.bad(), j.maxDiff(), j.x(), j.z(), j.ya(), j.yb()));
		else if (j.bad() == 0) by.put(key, new Junction(ida, ka, idb, kb, j.cells() + 1, 1, d, x, z, ya, yb));
		else by.put(key, new Junction(ida, ka, idb, kb, j.cells() + 1, j.bad() + 1, Math.max(d, j.maxDiff()), j.x(), j.z(), j.ya(), j.yb()));
	}

	/** The whole street a street piece belongs to: its id less a {@code .N} piece suffix (district detail names the pieces of a street cut by a square {@code r}, {@code r.1}, ...). */
	public static String wholeStreet(String id) {
		int dot = id.lastIndexOf('.');
		if (dot < 0 || dot == id.length() - 1) return id;
		for (int i = dot + 1; i < id.length(); i++) if (!Character.isDigit(id.charAt(i))) return id;
		return id.substring(0, dot);
	}

	/** {@code junction-height}: one violation per pair of streets with a cell more than {@link #JUNCTION_STEP} apart. */
	public static List<Violation> cityJunctionHeights(java.util.Collection<DistrictPlan> plans) {
		List<Violation> out = new ArrayList<>();
		for (Junction j : junctions(plans)) {
			if (j.bad() == 0) continue;
			out.add(new Violation(JUNCTION_HEIGHT, null, "streets " + j.a() + " (" + j.ka().json + ") and " + j.b() + " (" + j.kb().json + ") meet at " + j.x() + "," + j.z() + " with surfaces " + j.ya() + " and " + j.yb()
					+ " (" + j.bad() + " of " + j.cells() + " shared cells more than " + JUNCTION_STEP + " apart, up to " + j.maxDiff() + ")", List.of(new Box3(j.x(), Math.min(j.ya(), j.yb()), j.z(), 1, Math.abs(j.ya() - j.yb()) + 1, 1))));
		}
		return out;
	}


	private static Violation v(String id, String msg, int x, int z, int w, int d) {
		return new Violation(id, null, msg, List.of(new Box3(x, 0, z, w, 1, d)));
	}
}
