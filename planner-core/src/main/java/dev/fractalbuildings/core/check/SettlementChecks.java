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
					if (Rect.overlap(a.x(), a.z(), a.w(), a.d(), r.x(), r.z(), r.w(), r.d())) {
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
			boolean root = s.kind() == RoadKind.ARTERIAL || s.kind() == RoadKind.MAIN || s.kind() == RoadKind.PATH;
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

	private static Violation v(String id, String msg, int x, int z, int w, int d) {
		return new Violation(id, null, msg, List.of(new Box3(x, 0, z, w, 1, d)));
	}
}
