package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.metrics.Circulation;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIRC2: house rooms lean square and bedrooms come before duplicate room types. Measurement prints with
 * {@code -Dfb.roomshape=N} (houses per style).
 */
class RoomShapeTest {
	static double aspect(Room r) {
		Box3 b = r.parts().get(0);
		return (double) Math.max(b.sx(), b.sz()) / Math.min(b.sx(), b.sz());
	}

	static boolean sleepOrLive(String use) { return use.equals("bedroom") || use.equals("living") || use.equals("kitchen"); }

	/** {houses, bedrooms, houses with >=2 bedrooms, houses with 0 bedrooms, rooms, living/bedroom/kitchen rooms, those with aspect > 2, deep rooms, sum aspect x1000, max aspect x1000, duplicated non-bedroom types (a floor with 2+ living or kitchens)}. */
	static long[] stats(String style, int n) {
		long[] s = new long[13];
		for (BuildingIr b : CirculationTest.houses(style, n)) {
			s[0]++;
			int bed = 0;
			java.util.Map<String, Integer> groundCount = new java.util.TreeMap<>();
			for (Room r : b.rooms()) {
				if (r.use().equals("bedroom")) bed++;
				if (r.storey() == 0 && (r.use().equals("living") || r.use().equals("kitchen"))) groundCount.merge(r.use(), 1, Integer::sum);
				s[4]++;
				if (sleepOrLive(r.use())) {
					s[5]++;
					double a = aspect(r);
					if (a > 2.0 && Integer.getInteger("fb.roomshape.diag", 0) > 1) System.out.println("LONG " + r.use() + " " + r.parts().get(0).sx() + "x" + r.parts().get(0).sz() + " of " + b.rooms().size());
					if (a > 2.0) s[6]++;
					s[8] += Math.round(a * 1000);
					s[9] = Math.max(s[9], Math.round(a * 1000));
				}
			}
			if (bed == 0 && Integer.getInteger("fb.roomshape.diag", 0) > 0 && s[3] < 25) System.out.println("NOBED " + b.rooms().stream().map(r -> r.storey() + r.use() + (int) r.parts().get(0).sx() + "x" + r.parts().get(0).sz()).toList());
			s[1] += bed;
			if (b.rooms().size() >= 3) { s[11]++; s[12] += bed; }
			if (bed >= 2) s[2]++;
			if (bed == 0) s[3]++;
			s[7] += Circulation.measure(b).deep();
			if (groundCount.values().stream().anyMatch(v -> v >= 2)) s[10]++;
		}
		return s;
	}

	@Test
	void report() {
		int n = Integer.getInteger("fb.roomshape", 0);
		if (n == 0) return;
		for (String style : List.of("plain", "medieval", "plains", "desert", "snowy")) {
			long[] r = stats(style, n);
			System.out.printf("SHAPE %s houses=%d bedrooms/house=%.2f twoPlusBed=%.1f%% noBed=%.1f%% rooms/house=%.2f meanAspect=%.2f maxAspect=%.2f aspect>2=%.1f%% deep=%.2f%% dupLiveKitchen=%.1f%% bed/house(3+rooms)=%.2f of %d%n",
					style, r[0], (double) r[1] / r[0], 100.0 * r[2] / r[0], 100.0 * r[3] / r[0], (double) r[4] / r[0], r[8] / 1000.0 / r[5], r[9] / 1000.0,
					100.0 * r[6] / r[5], 100.0 * r[7] / r[4], 100.0 * r[10] / r[0], (double) r[12] / r[11], r[11]);
		}
	}

	@Test
	void housesLeanSquareAndSleepInSeveralBedrooms() {
		for (String style : List.of("plain", "medieval", "desert")) {
			long[] r = stats(style, 300);
			assertTrue(100.0 * r[6] / r[5] < 8.0, style + ": rooms with aspect > 2: " + r[6] + " of " + r[5]);
			assertTrue((double) r[1] / r[0] >= 1.3, style + ": bedrooms per house " + (double) r[1] / r[0]);
		}
	}
}
