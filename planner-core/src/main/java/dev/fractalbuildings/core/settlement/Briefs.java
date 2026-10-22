package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.v1.SpecPolicyV1;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.Types;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.style.StyleField;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fills the briefs of a district plan (Phase 5, 5.4 wiring; docs/plans/phase5.md "DB"): per lot the style from the
 * {@link StyleField}, the storey range from the {@link ClassTable} row of the lot's density and quarter, the
 * {@code streets} mask from which sides touch a street or plaza, {@code frontY} from the graded street in front, and
 * the status. A lot whose type cannot be built on it (too small for its orientation, too steep for the type's
 * {@code maxGrade}) is re-picked from the class table with the lot's real relief; if nothing fits it stays empty, so
 * the spec policy never throws on a filled plan. House and workshop lots with more than {@link #STEEP_RELIEF}
 * of relief become {@code empty:steep}. Pure function of the plan, the style field and the terrain.
 */
public final class Briefs {
	private Briefs() {}

	/** Relief above which an optional fill lot (house, workshop) is left empty. */
	public static final int STEEP_RELIEF = 8;

	/**
	 * @param quarter kind of the district's quarter (the plan holds only its index)
	 * @param exact exact terrain, for the relief of each lot
	 */
	public static DistrictPlan fill(DistrictPlan plan, QuarterKind quarter, StyleField styles, TerrainProbe exact) {
		return fill(plan, (x, z, density) -> quarter, styles, exact);
	}

	/** The quarter kind at a column and density (the settlement's {@link QuarterField}). */
	@FunctionalInterface
	public interface QuarterAt {
		QuarterKind at(int x, int z, int density);
	}

	/** As above, with each lot taking the quarter kind at its own centre instead of its district's. */
	public static DistrictPlan fill(DistrictPlan plan, QuarterAt quarters, StyleField styles, TerrainProbe exact) {
		List<Rect> plazas = new ArrayList<>();
		for (Plaza p : plan.plazas()) plazas.add(new Rect(p.x(), p.z(), p.w(), p.d()));
		List<List<Rect>> streetRects = new ArrayList<>();
		for (Street s : plan.streets()) streetRects.add(SettlementChecks.footprint(s));
		List<LotBrief> out = new ArrayList<>();
		for (LotBrief l : plan.lots()) out.add(fillLot(plan, l, quarters.at(l.x() + l.w() / 2, l.z() + l.d() / 2, l.density()), styles, exact, streetRects, plazas));
		return new DistrictPlan(plan.genVersion(), plan.seed(), plan.district(), plan.streets(), plan.plazas(), out, plan.scene(), plan.violations());
	}

	private static LotBrief fillLot(DistrictPlan plan, LotBrief l, QuarterKind quarter, StyleField styles, TerrainProbe exact,
			List<List<Rect>> streetRects, List<Rect> plazas) {
		String style = styles.pick(l.x() + l.w() / 2, l.z() + l.d() / 2);
		int mask = streetMask(l, streetRects, plazas);
		int frontY = frontY(plan, l, streetRects);
		if (!l.builds()) return new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), l.type(), style, l.minStoreys(), l.maxStoreys(), mask, l.airRights(), frontY, l.density(), l.status(), l.walk());
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int z = l.z(); z < l.z() + l.d(); z++)
			for (int x = l.x(); x < l.x() + l.w(); x++) {
				int y = exact.floorY(x, z);
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
			}
		int relief = hi - lo;
		String type = l.type();
		if (!buildable(type, l, relief)) {
			if (dev.fractalbuildings.core.settlement.tiers.WallRing.isRingLotId(l.id())) return empty(l, style, mask, frontY, "empty:no-fit"); // a wall lot is never swapped for another type
			boolean alongX = l.front() == Facing.NORTH || l.front() == Facing.SOUTH;
			Optional<ClassTable.Pick> p = ClassTable.pick(plan.seed(), l.x(), l.z(), l.density(), quarter, relief, alongX ? l.w() : l.d(), alongX ? l.d() : l.w());
			if (p.isPresent() && buildable(p.get().type(), l, relief)) type = p.get().type();
			else return empty(l, style, mask, frontY, relief > STEEP_RELIEF ? "empty:steep" : "empty:no-fit");
		}
		if ((type.equals("house") || type.equals("workshop")) && relief > STEEP_RELIEF) return empty(l, style, mask, frontY, "empty:steep");
		int[] range = storeys(type, l.density(), quarter);
		if (type.equals("highrise")) range = ClassTable.lotStoreys(plan.seed(), l.x(), l.z(), l.density(), range[0], range[1]);
		return new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), type, style, range[0], range[1], mask, l.airRights(), frontY, l.density(), LotBrief.BUILD, l.walk());
	}

	private static LotBrief empty(LotBrief l, String style, int mask, int frontY, String status) {
		return new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), l.type(), style, l.minStoreys(), l.maxStoreys(), mask, l.airRights(), frontY, l.density(), status, l.walk());
	}

	/** The checks of {@code SpecPolicyV1.spec} that can throw: lot size along the front, and relief for gentle-ground types. */
	static boolean buildable(String type, LotBrief l, int relief) {
		if (!Types.ids().contains(type)) return false;
		TypeLimits lim = Types.get(type).limits();
		boolean alongX = l.front() == Facing.NORTH || l.front() == Facing.SOUTH;
		int lw = alongX ? l.w() : l.d(), ld = alongX ? l.d() : l.w();
		int w = lw - 2 * lim.side(), d = ld - lim.front() - lim.back();
		return w >= lim.minWidth() && d >= lim.minDepth() && SpecPolicyV1.reliefOk(lim, relief, l.w(), l.d()) && ClassTable.reliefAllows(type, relief);
	}

	/** Storey range for a type: its class-table entry in this row, within the type's own limits. */
	static int[] storeys(String type, int density, QuarterKind quarter) {
		TypeLimits lim = Types.get(type).limits();
		int min = lim.minStoreys(), max = lim.maxStoreys();
		boolean found = false;
		for (ClassTable.Entry e : ClassTable.entries(density, quarter)) {
			if (!e.type().equals(type)) continue;
			int a = Math.max(e.min(), lim.minStoreys()), b = Math.min(e.max(), lim.maxStoreys());
			if (a <= b) { min = a; max = b; }
			found = true;
			break;
		}
		if (!found) {
			// The quarter at the lot's centre drops the type (a high-rise in an old town or on a waterfront), but the lot was drawn under the
			// quarter at its sample point beside it: take the row's range, within the quarter's storey cap when that leaves one.
			for (ClassTable.Entry e : ClassTable.entries(density, QuarterKind.RESIDENTIAL)) {
				if (!e.type().equals(type)) continue;
				int cap = quarter == QuarterKind.OLD_TOWN ? ClassTable.OLD_TOWN_CAP : quarter == QuarterKind.WATERFRONT ? ClassTable.WATERFRONT_CAP : Integer.MAX_VALUE;
				int a = Math.max(e.min(), lim.minStoreys()), b = Math.min(e.max(), lim.maxStoreys());
				if (a <= b) { min = a; max = b; }
				if (cap >= min) max = Math.min(max, cap);
				break;
			}
		}
		return new int[] {min, max};
	}

	/** Mask of the lot's sides (local frame: 1 front, 2 east of the front, 4 back, 8 west) with at least 3 street or plaza columns next to them. */
	static int streetMask(LotBrief l, List<List<Rect>> streetRects, List<Rect> plazas) {
		int mask = 0;
		for (Facing side : Facing.values()) {
			int need = Math.min(3, side.dx != 0 ? l.d() : l.w());
			int hit = 0;
			for (int k = 0; k < (side.dx != 0 ? l.d() : l.w()); k++) {
				int[] c = outside(l, side, k);
				if (covered(c[0], c[1], streetRects, plazas)) hit++;
			}
			if (hit >= need) mask |= 1 << Math.floorMod(side.ordinal() - l.front().ordinal(), 4);
		}
		return mask | 1;
	}

	/** The k-th column just outside a side of the lot, along that side. */
	private static int[] outside(LotBrief l, Facing side, int k) {
		return switch (side) {
			case NORTH -> new int[] {l.x() + k, l.z() - 1};
			case SOUTH -> new int[] {l.x() + k, l.z() + l.d()};
			case WEST -> new int[] {l.x() - 1, l.z() + k};
			case EAST -> new int[] {l.x() + l.w(), l.z() + k};
		};
	}

	private static boolean covered(int x, int z, List<List<Rect>> streetRects, List<Rect> plazas) {
		for (List<Rect> rs : streetRects) for (Rect r : rs) if (r.contains(x, z)) return true;
		for (Rect r : plazas) if (r.contains(x, z)) return true;
		return false;
	}

	/** Street surface y just outside the middle of the front edge (street centre-line y nearest to it; plaza y on a plaza), or {@link Brief#NO_FRONT_Y}. */
	static int frontY(DistrictPlan plan, LotBrief l, List<List<Rect>> streetRects) {
		int k = ((l.front().dx != 0 ? l.d() : l.w()) - 1) / 2;
		int[] c = outside(l, l.front(), k);
		int best = Brief.NO_FRONT_Y, bestDist = Integer.MAX_VALUE;
		for (int i = 0; i < plan.streets().size(); i++) {
			boolean in = false;
			for (Rect r : streetRects.get(i)) if (r.contains(c[0], c[1])) { in = true; break; }
			if (!in) continue;
			int[] hit = nearest(plan.streets().get(i), c[0], c[1]);
			if (hit[0] < bestDist) { bestDist = hit[0]; best = hit[1]; }
		}
		if (best != Brief.NO_FRONT_Y) return best;
		for (Plaza p : plan.plazas()) if (new Rect(p.x(), p.z(), p.w(), p.d()).contains(c[0], c[1])) return p.y();
		return Brief.NO_FRONT_Y;
	}

	/** {chebyshev distance, interpolated y} of the street's centre-line column nearest to (px, pz). */
	static int[] nearest(Street s, int px, int pz) {
		int[] ys = s.y();
		List<Pt> ps = s.points();
		int bestD = Integer.MAX_VALUE, bestY = ys[0];
		for (int i = 0; i + 1 < ps.size(); i++) {
			Pt a = ps.get(i), b = ps.get(i + 1);
			int n = Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z()));
			int ya = ys[i], yb = ys[i + 1];
			for (int t = 0; t <= n; t++) {
				int cx = n == 0 ? a.x() : a.x() + (int) Math.floorDiv(2L * (b.x() - a.x()) * t + n, 2L * n);
				int cz = n == 0 ? a.z() : a.z() + (int) Math.floorDiv(2L * (b.z() - a.z()) * t + n, 2L * n);
				int dist = Math.max(Math.abs(cx - px), Math.abs(cz - pz));
				if (dist < bestD) {
					bestD = dist;
					bestY = n == 0 ? ya : ya + (int) Math.floorDiv((long) (yb - ya) * t, n);
				}
			}
		}
		return new int[] {bestD, bestY};
	}
}
