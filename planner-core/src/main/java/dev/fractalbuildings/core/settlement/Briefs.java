package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.v1.SpecPolicyV1;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.Types;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.rand.Hash64;
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

	/** SHOPLOW (DS-45): a street whose surface is more than this from the lot's front-edge ground runs on an embankment or in a cutting along it; the lot stays empty ({@code empty:bank}). */
	public static final int BANK_LIMIT = 4;

	/** SHOPLOW: a shop or workshop door sits within 2 of the street, so the lot's highest ground may stand at most this above the street (S2 digs in by at most 4). */
	public static final int SHOP_FRONT_RISE = 6;

	/** SHOPLOW S2: a house's door sits at most 5 above the street (DS-45, a landing above 3); the dug-in slab stands 5 below the highest ground of the site, so the lot may rise at most 9 above the street until the bank buildings (S8) take the steeper ones. */
	public static final int HOUSE_FRONT_RISE = 9;

	/** SHOPLOW S2: the ground in a house lot's front rows, where its door stands, may rise at most this far above the street (a rise of 5 is the most DS-45 gives a house door). */
	public static final int HOUSE_FRONT_ROW = 4;

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
		markSquareLots(plan, out, exact);
		return new DistrictPlan(plan.genVersion(), plan.seed(), plan.district(), plan.streets(), plan.plazas(), out, plan.scene(), plan.violations());
	}

	/** Kinds of plaza that carry a market hall: a town's market and a city's square (a green is a village or hamlet's, and has none). */
	static boolean hallPlaza(Plaza p) { return p.kind().equals("market") || p.kind().equals("square"); }

	/** Percent of eligible squares whose lowest-hash lot carries the market hall (architect, SHOPLOW A6). */
	public static final int SQUARE_HALL_PCT = 60;
	/** Widest relief (blocks) of a market-hall lot. */
	public static final int SQUARE_RELIEF = 1;
	/** Narrowest front (blocks) of a market-hall lot: an envelope of {@code ShopType.MARKET_MIN_WIDTH} (9) plus the shop's side margins. */
	public static final int SQUARE_MIN_FRONT = dev.fractalbuildings.core.gen.v1.types.shop.ShopType.MARKET_MIN_WIDTH + 2 * 1;

	/**
	 * 7.17 S4: in each market or square plaza, the eligible lot with the lowest hash (a shop, level, at least {@link #SQUARE_MIN_FRONT} along a
	 * front that faces the plaza across the street) is marked {@code square} on {@link #SQUARE_HALL_PCT} percent of the plazas, so a square has one market hall
	 * at most. A pure function of the plan seed and the plaza and lot positions.
	 */
	static void markSquareLots(DistrictPlan plan, List<LotBrief> lots, TerrainProbe exact) {
		for (Plaza p : plan.plazas()) {
			if (!hallPlaza(p) || Hash64.below(Hash64.hash(plan.seed(), "square-hall", p.x(), p.z()), 100) >= SQUARE_HALL_PCT) continue;
			Rect r = new Rect(p.x(), p.z(), p.w(), p.d());
			boolean taken = false; // a lot marked for a neighbouring plaza that faces this one too counts: one hall per square
			for (LotBrief l : lots) taken |= l.square() && facesPlaza(l, r);
			if (taken) continue;
			int best = -1;
			long bestHash = 0;
			for (int i = 0; i < lots.size(); i++) {
				LotBrief l = lots.get(i);
				if (!eligible(l, r, exact)) continue;
				long h = Hash64.hash(plan.seed(), "square-lot", l.x(), l.z());
				if (best < 0 || Long.compareUnsigned(h, bestHash) < 0) { best = i; bestHash = h; }
			}
			if (best < 0) continue;
			LotBrief l = lots.get(best);
			lots.set(best, new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), l.type(), l.style(), l.minStoreys(), l.maxStoreys(), l.streets(), l.airRights(), l.frontY(), l.density(), l.status(), l.walk(), true));
		}
	}

	/** The lot may carry the plaza's hall: a shop that builds, a front of at least {@link #SQUARE_MIN_FRONT} that faces the plaza, a footprint with relief of at most {@link #SQUARE_RELIEF}. */
	static boolean eligible(LotBrief l, Rect plaza, TerrainProbe exact) {
		if (!l.builds() || !l.type().equals("shop") || (l.front().dx != 0 ? l.d() : l.w()) < SQUARE_MIN_FRONT || !facesPlaza(l, plaza)) return false;
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int z = l.z(); z < l.z() + l.d(); z++)
			for (int x = l.x(); x < l.x() + l.w(); x++) {
				int y = exact.floorY(x, z);
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
			}
		return hi - lo <= SQUARE_RELIEF;
	}

	/** Most blocks between a lot's front edge and the plaza it faces: the street along the square (3 to 5 wide) and its margins. */
	public static final int SQUARE_REACH = 6;

	/** The lot faces the rectangle: straight out from the middle of its front edge, within {@link #SQUARE_REACH} blocks, lies a column of it. */
	static boolean facesPlaza(LotBrief l, Rect r) {
		int k = ((l.front().dx != 0 ? l.d() : l.w()) - 1) / 2;
		int[] c = outside(l, l.front(), k);
		for (int g = 0; g < SQUARE_REACH; g++) if (r.contains(c[0] + g * l.front().dx, c[1] + g * l.front().dz)) return true;
		return false;
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
		int[] fc = outside(l, l.front(), ((l.front().dx != 0 ? l.d() : l.w()) - 1) / 2);
		int fr = exact.floorY(fc[0], fc[1]); // the raw ground of the street column in front of the lot (the same column frontY looks out from)
		boolean frontKnown = frontY != Brief.NO_FRONT_Y;
		int street = frontKnown ? frontY : fr;
		java.util.Set<String> excluded = java.util.Set.of();
		if (frontKnown && Math.abs(frontY - fr) > BANK_LIMIT && frontType(type)) return empty(l, style, mask, frontY, "empty:bank");
		if (shopRiseTooHigh(type, hi, street) || houseFrontRowTooHigh(type, l, exact, street)) excluded = java.util.Set.of(type);
		if (excluded.contains(type) || !buildable(type, l, relief)) {
			if (dev.fractalbuildings.core.settlement.tiers.WallRing.isRingLotId(l.id())) return empty(l, style, mask, frontY, "empty:no-fit"); // a wall lot is never swapped for another type
			boolean alongX = l.front() == Facing.NORTH || l.front() == Facing.SOUTH;
			Optional<ClassTable.Pick> p = ClassTable.pick(plan.seed(), l.x(), l.z(), l.density(), quarter, relief, alongX ? l.w() : l.d(), alongX ? l.d() : l.w(), excluded);
			if (p.isPresent() && buildable(p.get().type(), l, relief) && !shopRiseTooHigh(p.get().type(), hi, street) && !houseFrontRowTooHigh(p.get().type(), l, exact, street)) type = p.get().type();
			else return empty(l, style, mask, frontY, relief > STEEP_RELIEF ? "empty:steep" : "empty:no-fit");
			if (frontKnown && Math.abs(frontY - fr) > BANK_LIMIT && frontType(type)) return empty(l, style, mask, frontY, "empty:bank");
		}
		if ((type.equals("house") || type.equals("workshop")) && relief > STEEP_RELIEF) return empty(l, style, mask, frontY, "empty:steep");
		int[] range = storeys(type, l.density(), quarter);
		if (type.equals("highrise")) range = ClassTable.lotStoreys(plan.seed(), l.x(), l.z(), l.density(), range[0], range[1]);
		return new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), type, style, range[0], range[1], mask, l.airRights(), frontY, l.density(), LotBrief.BUILD, l.walk());
	}

	/** The street-front types DS-45 limits the door rise of. */
	static boolean frontType(String type) { return type.equals("house") || type.equals("workshop") || type.equals("shop"); }

	/** A shop or workshop lot whose highest ground stands more than {@link #SHOP_FRONT_RISE} above the street (or front ground) cannot hold its door within 2 of the street; a house lot more than {@link #HOUSE_FRONT_RISE} cannot hold it within 5. */
	static boolean shopRiseTooHigh(String type, int hi, int street) { return (type.equals("shop") || type.equals("workshop")) && hi - street > SHOP_FRONT_RISE || type.equals("house") && hi - street > HOUSE_FRONT_RISE; }

	/** A house lot whose front rows (two deep, the middle three columns) stand more than {@link #HOUSE_FRONT_ROW} above the street. */
	static boolean houseFrontRowTooHigh(String type, LotBrief l, dev.fractalbuildings.core.probe.TerrainProbe exact, int street) {
		if (!type.equals("house")) return false;
		int k0 = ((l.front().dx != 0 ? l.d() : l.w()) - 1) / 2, hi = Integer.MIN_VALUE;
		for (int row = 0; row < 2; row++)
			for (int k = Math.max(0, k0 - 1); k <= Math.min((l.front().dx != 0 ? l.d() : l.w()) - 1, k0 + 1); k++) {
				int x, z;
				switch (l.front()) {
					case NORTH -> { x = l.x() + k; z = l.z() + row; }
					case SOUTH -> { x = l.x() + k; z = l.z() + l.d() - 1 - row; }
					case WEST -> { x = l.x() + row; z = l.z() + k; }
					default -> { x = l.x() + l.w() - 1 - row; z = l.z() + k; }
				}
				hi = Math.max(hi, exact.floorY(x, z));
			}
		return hi - street > HOUSE_FRONT_ROW;
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
		return w >= lim.minWidth() && d >= lim.minDepth() && SpecPolicyV1.reliefOk(lim, relief, l.w(), l.d()) && ClassTable.reliefAllows(type, relief)
				&& SpecPolicyV1.railRoomOk(type, lim, relief, lw, ld);
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

	/**
	 * The ground a lot's building generator sees: {@code exact}, except in the row of columns just outside the lot's front edge, where it reads the street's own surface
	 * (its first free y, from the street's centre line as {@link #frontY} reads it, but column by column along the front: a street on a slope steps by a block every few
	 * cells, so the street at a door near the end of a wide front is not the street at the middle) or the plaza's level, where a street or plaza covers the column.
	 * SHOPLOW S2: a building that digs in to the street needs the street level where its door meets it. Depends on the plan alone, so any order of lots gives the same bytes.
	 */
	public static TerrainProbe withStreetFront(DistrictPlan plan, LotBrief l, TerrainProbe exact) {
		if (plan.streets().isEmpty() && plan.plazas().isEmpty()) return exact;
		List<List<Rect>> streetRects = new ArrayList<>();
		for (Street st : plan.streets()) streetRects.add(SettlementChecks.footprint(st));
		int len = l.front().dx != 0 ? l.d() : l.w();
		int[] ys = new int[len];
		boolean any = false;
		for (int k = 0; k < len; k++) {
			ys[k] = frontYAt(plan, l, streetRects, k);
			any |= ys[k] != Brief.NO_FRONT_Y;
		}
		if (!any) return exact;
		return new TerrainProbe() {
			@Override public int floorY(int x, int z) {
				int k = switch (l.front()) {
					case NORTH -> z == l.z() - 1 ? x - l.x() : -1;
					case SOUTH -> z == l.z() + l.d() ? x - l.x() : -1;
					case WEST -> x == l.x() - 1 ? z - l.z() : -1;
					case EAST -> x == l.x() + l.w() ? z - l.z() : -1;
				};
				return k >= 0 && k < len && ys[k] != Brief.NO_FRONT_Y ? ys[k] : exact.floorY(x, z);
			}

			@Override public int waterDepth(int x, int z) { return exact.waterDepth(x, z); }
		};
	}

	/** Street surface y just outside the middle of the front edge (street centre-line y nearest to it; plaza y on a plaza), or {@link Brief#NO_FRONT_Y}. */
	static int frontY(DistrictPlan plan, LotBrief l, List<List<Rect>> streetRects) {
		return frontYAt(plan, l, streetRects, ((l.front().dx != 0 ? l.d() : l.w()) - 1) / 2);
	}

	/** The same at the k-th column along the front edge. */
	static int frontYAt(DistrictPlan plan, LotBrief l, List<List<Rect>> streetRects, int k) {
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
