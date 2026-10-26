package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.gen.v1.SpecPolicyV1;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.Types;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Density and quarter to building type and storey range (docs/plans/phase5.md "ClassTable"; D3 tunes the
 * numbers). One hashed draw per lot picks a type by weight; a type the lot cannot take (too small, too steep for
 * its {@code maxGrade}, storey range empty after the quarter's cap) is skipped and the next weight drawn.
 * Pure: the same arguments always give the same pick.
 */
public final class ClassTable {
	private ClassTable() {}

	/** A chosen type with the storey range to hand to the generator in the brief. */
	public record Pick(String type, int minStoreys, int maxStoreys) {}

	/** One row entry: weight 0 means landmarks only. */
	record Entry(String type, int weight, int min, int max) {}

	private static Entry e(String type, int weight, int min, int max) { return new Entry(type, weight, min, max); }

	/** Band upper bounds (exclusive) of the density rows; the last row is open. */
	static final int[] BANDS = {25, 50, 70, 85};

	static final List<List<Entry>> ROWS = List.of(
			List.of(e("house", 70, 1, 2), e("workshop", 20, 1, 2), e("tower", 5, 4, 6), e("shop", 5, 2, 2)),
			List.of(e("house", 50, 1, 3), e("shop", 25, 2, 3), e("workshop", 20, 1, 2), e("tower", 5, 4, 8)),
			List.of(e("shop", 30, 2, 4), e("house", 20, 2, 3), e("block", 40, 3, 5), e("workshop", 10, 2, 2)),
			List.of(e("block", 45, 3, 5), e("highrise", 35, 10, 19), e("shop", 20, 3, 4), e("hall", 0, 1, 3)),
			// highrise storeys depend on density: see highriseStoreys(); 10 to 19 is the mid-rise
			List.of(e("highrise", 62, 20, 60), e("block", 28, 4, 5), e("shop", 10, 4, 4)));

	/** Rows for a density, before quarter overrides (for tests and tools). */
	static List<Entry> row(int density) {
		int i = 0;
		while (i < BANDS.length && density >= BANDS[i]) i++;
		return ROWS.get(i);
	}

	/** A high-rise wants level ground: more relief across its lot than this and the lot takes another type (a 28-block cliff gave an entrance with no way down). */
	public static final int HIGHRISE_MAX_RELIEF = 12;

	/** Does the class table allow the type on a lot with this relief (on top of the type's own grade limit)? */
	public static boolean reliefAllows(String type, int relief) { return !type.equals("highrise") || relief <= HIGHRISE_MAX_RELIEF; }

	/** Storey cap of a waterfront quarter. */
	public static final int WATERFRONT_CAP = 4;
	/** Storey cap of an old town (no blocks or high-rises inside the walls). */
	public static final int OLD_TOWN_CAP = 5;

	/** Entries after quarter overrides: weights and storey ranges used for the draw. */
	static List<Entry> entries(int density, QuarterKind quarter) {
		List<Entry> out = new ArrayList<>();
		for (Entry en : row(density)) {
			int w = en.weight(), min = en.min(), max = en.max();
			String type = en.type();
			if (type.equals("highrise")) {
				int[] s = highriseStoreys(density);
				min = s[0];
				max = s[1];
			}
			if (quarter == QuarterKind.WORKSHOP && type.equals("workshop")) w *= 2;
			if (quarter == QuarterKind.WATERFRONT || quarter == QuarterKind.OLD_TOWN) {
				if (type.equals("block") || (quarter == QuarterKind.OLD_TOWN && type.equals("highrise"))) continue; // the weight goes to shop
				if (type.equals("shop")) w += blockWeight(density) + (quarter == QuarterKind.OLD_TOWN ? highriseWeight(density) : 0);
				max = Math.min(max, quarter == QuarterKind.OLD_TOWN ? OLD_TOWN_CAP : WATERFRONT_CAP);
				if (max < min) continue;
			}
			out.add(new Entry(type, w, min, max));
		}
		return out;
	}

	/** Storeys of a high-rise at a density: a mid-rise of 10 to 19 from density 70 (the gap between 5-storey blocks and the 20-storey towers), 20 at 85, rising to 60 at 100; at most 12 % spread. */
	public static int[] highriseStoreys(int density) {
		if (density < BANDS[BANDS.length - 1]) {
			int d = Math.max(70, density);
			return new int[] {10 + (d - 70) * 5 / 15, 14 + (d - 70) * 5 / 14};
		}
		int base = 20 + (density - 85) * 40 / 15; // 20..60
		return new int[] {Math.max(20, base - base * 12 / 100), Math.min(60, base + base * 12 / 100)};
	}

	/**
	 * A lot's own tower height inside a tower density (85 and up): the density gives the tallest (20 to 60 storeys), and a hashed draw skewed
	 * low puts most lots near 20 to 35 storeys and a few near the tallest, so the core is peaked rather than a flat plateau. Returns the entry range
	 * narrowed to [h - 12 %, h] (not below 20). Other ranges (mid-rise) are returned as given.
	 */
	public static int[] lotStoreys(long seed, int lotX, int lotZ, int density, int min, int max) {
		if (density < BANDS[BANDS.length - 1] || max <= 20) return new int[] {min, max};
		double u = new Draws(Hash64.hash(seed, "tower-height", lotX, lotZ)).below(10000) / 10000.0;
		int tallest = highriseStoreys(density)[1];
		int h = 20 + (int) Math.round((Math.min(tallest, max) - 20) * StrictMath.pow(u, 2.5));
		return new int[] {Math.max(20, h - h * 12 / 100), h};
	}

	private static int highriseWeight(int density) {
		for (Entry en : row(density)) if (en.type().equals("highrise")) return en.weight();
		return 0;
	}

	private static int blockWeight(int density) {
		for (Entry en : row(density)) if (en.type().equals("block")) return en.weight();
		return 0;
	}

	/**
	 * Picks a type and storey range for a lot, or none (park quarters build nothing; a lot nothing fits stays empty).
	 *
	 * @param relief highest minus lowest ground column in the lot
	 * @param lotW width of the lot in blocks (either orientation may be used by the front chosen later)
	 */
	public static Optional<Pick> pick(long seed, int lotX, int lotZ, int density, QuarterKind quarter, int relief, int lotW, int lotD) {
		return pick(seed, lotX, lotZ, density, quarter, relief, lotW, lotD, java.util.Set.of());
	}

	/** As above, without the types in {@code excluded} (a type the street frontage could not take). */
	public static Optional<Pick> pick(long seed, int lotX, int lotZ, int density, QuarterKind quarter, int relief, int lotW, int lotD, java.util.Set<String> excluded) {
		if (quarter == QuarterKind.PARK) return Optional.empty();
		List<Entry> left = new ArrayList<>();
		for (Entry en : entries(density, quarter)) {
			if (en.weight() <= 0 || excluded.contains(en.type())) continue;
			TypeLimits lim = Types.get(en.type()).limits();
			boolean fits = (lotW >= lim.minLotWidth() && lotD >= lim.minLotDepth()) || (lotD >= lim.minLotWidth() && lotW >= lim.minLotDepth());
			boolean room = SpecPolicyV1.railRoomOk(en.type(), lim, relief, lotW, lotD) || SpecPolicyV1.railRoomOk(en.type(), lim, relief, lotD, lotW);
			if (!fits || !room || !SpecPolicyV1.reliefOk(lim, relief, lotW, lotD) || !reliefAllows(en.type(), relief)) continue;
			int min = Math.max(en.min(), lim.minStoreys()), max = Math.min(en.max(), lim.maxStoreys());
			if (max < min) continue;
			if (en.type().equals("highrise")) {
				int[] r = lotStoreys(seed, lotX, lotZ, density, min, max);
				min = r[0];
				max = r[1];
			}
			left.add(new Entry(en.type(), en.weight(), min, max));
		}
		if (left.isEmpty()) return Optional.empty();
		// drawing among the entries that fit equals drawing among all and re-drawing on a refusal
		int total = 0;
		for (Entry en : left) total += en.weight();
		int t = new Draws(Hash64.hash(seed, "class-table", lotX, lotZ)).below(total);
		for (Entry en : left) {
			t -= en.weight();
			if (t < 0) return Optional.of(new Pick(en.type(), en.min(), en.max()));
		}
		throw new IllegalStateException("unreachable");
	}
}
