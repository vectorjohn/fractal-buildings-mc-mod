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
			List.of(e("shop", 40, 2, 4), e("house", 25, 2, 3), e("block", 15, 2, 4), e("workshop", 10, 2, 2)),
			List.of(e("block", 45, 3, 5), e("shop", 25, 3, 4), e("highrise", 15, 20, 30), e("hall", 0, 1, 3)),
			// highrise storeys depend on density: see storeys()
			List.of(e("highrise", 60, 20, 60), e("block", 35, 4, 5), e("shop", 5, 4, 4)));

	/** Rows for a density, before quarter overrides (for tests and tools). */
	static List<Entry> row(int density) {
		int i = 0;
		while (i < BANDS.length && density >= BANDS[i]) i++;
		return ROWS.get(i);
	}

	/** Storey cap of a waterfront quarter. */
	public static final int WATERFRONT_CAP = 4;

	/** Entries after quarter overrides: weights and storey ranges used for the draw. */
	static List<Entry> entries(int density, QuarterKind quarter) {
		List<Entry> out = new ArrayList<>();
		for (Entry en : row(density)) {
			int w = en.weight(), min = en.min(), max = en.max();
			String type = en.type();
			if (type.equals("highrise") && density >= BANDS[BANDS.length - 1]) {
				int base = 20 + (density - 85) * 40 / 15; // 20..60
				min = Math.max(20, base - base / 5);
				max = Math.min(60, base + base / 5);
			}
			if (quarter == QuarterKind.WORKSHOP && type.equals("workshop")) w *= 2;
			if (quarter == QuarterKind.WATERFRONT) {
				if (type.equals("block")) continue; // the block's weight goes to shop
				if (type.equals("shop")) w += blockWeight(density);
				max = Math.min(max, WATERFRONT_CAP);
				if (max < min) continue;
			}
			out.add(new Entry(type, w, min, max));
		}
		return out;
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
		if (quarter == QuarterKind.PARK) return Optional.empty();
		List<Entry> left = new ArrayList<>();
		for (Entry en : entries(density, quarter)) {
			if (en.weight() <= 0) continue;
			TypeLimits lim = Types.get(en.type()).limits();
			boolean fits = (lotW >= lim.minLotWidth() && lotD >= lim.minLotDepth()) || (lotD >= lim.minLotWidth() && lotW >= lim.minLotDepth());
			if (!fits || !SpecPolicyV1.reliefOk(lim, relief, lotW, lotD)) continue;
			int min = Math.max(en.min(), lim.minStoreys()), max = Math.min(en.max(), lim.maxStoreys());
			if (max < min) continue;
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
