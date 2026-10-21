package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.metrics.Circulation;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The whole-house room program: every house of two or more rooms has a kitchen (on the ground floor) and
 * a living room; a one-room hut stays a single bedroom ("hall house"). Measurement prints with
 * {@code -Dfb.houseprogram=N} (houses per style).
 */
class HouseProgramTest {
	static long count(BuildingIr b, String use) { return b.rooms().stream().filter(r -> r.use().equals(use)).count(); }

	static long groundCount(BuildingIr b, String use) { return b.rooms().stream().filter(r -> r.storey() == 0 && r.use().equals(use)).count(); }

	/** Rooms people live in: not corridors, stairs, halls, landings. */
	static long habitable(BuildingIr b) {
		return b.rooms().stream().filter(r -> r.use().equals("bedroom") || r.use().equals("living") || r.use().equals("kitchen")).count();
	}

	/** {houses, houses with 2+ habitable rooms, of those without a kitchen, houses with 3+ rooms, of those without kitchen, with 2+ kitchens, with no living room, kitchens not on the ground floor, bedrooms, rooms, aspect>2 rooms, habitable rooms, deep rooms, houses with 2+ living on a floor}. */
	static long[] stats(String style, int n) {
		long[] s = new long[15];
		for (BuildingIr b : CirculationTest.houses(style, n)) {
			s[0]++;
			long hab = habitable(b), k = count(b, "kitchen");
			if (hab >= 2) { s[1]++; if (k == 0) s[2]++; }
			long gh = groundCount(b, "living") + groundCount(b, "kitchen") + groundCount(b, "bedroom");
			if (gh >= 2 && count(b, "living") == 0) s[6]++;
			if (gh >= 2) s[14]++;
			if (hab >= 2 && k == 0 && Integer.getInteger("fb.houseprogram.diag", 0) > 0) System.out.println("NOKITCHEN " + b.rooms().stream().map(r -> r.storey() + r.use() + (int) r.parts().get(0).sx() + "x" + r.parts().get(0).sz()).toList());
			if (b.rooms().size() >= 3) { s[3]++; if (k == 0) s[4]++; }
			if (k >= 2) s[5]++;
			s[7] += k - groundCount(b, "kitchen");
			s[8] += count(b, "bedroom");
			s[9] += b.rooms().size();
			for (Room r : b.rooms()) if (RoomShapeTest.sleepOrLive(r.use())) { s[11]++; if (RoomShapeTest.aspect(r) > 2.0) s[10]++; }
			s[12] += Circulation.measure(b).deep();
			if (groundCount(b, "living") >= 2) s[13]++;
		}
		return s;
	}

	@Test
	void report() {
		int n = Integer.getInteger("fb.houseprogram", 0);
		if (n == 0) return;
		for (String style : List.of("plain", "medieval", "plains", "desert", "snowy")) {
			long[] r = stats(style, n);
			System.out.printf("PROGRAM %s houses=%d noKitchen(2+hab)=%.1f%% noKitchen(3+rooms)=%.1f%% noLiving(2+ground rooms)=%.1f%% twoKitchens=%.1f%% twoLiving=%.1f%% bedrooms/house=%.2f aspect>2=%.1f%% deep=%.2f%%%n",
					style, r[0], 100.0 * r[2] / r[1], 100.0 * r[4] / r[3], 100.0 * r[6] / r[14], 100.0 * r[5] / r[0], 100.0 * r[13] / r[0], (double) r[8] / r[0], 100.0 * r[10] / r[11], 100.0 * r[12] / r[9]);
		}
	}

	@Test
	void everyHouseWithTwoRoomsHasOneKitchenAndALivingRoom() {
		for (String style : List.of("plain", "medieval", "desert")) {
			int kitchens = 0, houses = 0;
			for (BuildingIr b : CirculationTest.houses(style, 300)) {
				if (habitable(b) < 2) continue;
				houses++;
				long k = count(b, "kitchen");
				assertTrue(k >= 1, style + ": house " + b.rooms().stream().map(r -> r.storey() + r.use()).toList() + " has no kitchen");
				assertEquals(k, groundCount(b, "kitchen"), style + ": a kitchen above the ground floor");
				assertTrue(k <= 2, style + ": " + k + " kitchens");
				if (k == 2) kitchens++;
				if (groundCount(b, "living") + groundCount(b, "kitchen") + groundCount(b, "bedroom") >= 2)
					assertTrue(count(b, "living") >= 1, style + ": no living room in " + b.rooms().stream().map(r -> r.storey() + r.use()).toList());
			}
			assertTrue(houses > 100, style + ": only " + houses + " multi-room houses");
			assertTrue(kitchens * 10 < houses, style + ": a second kitchen in " + kitchens + " of " + houses + " houses should be rare");
		}
	}
}
