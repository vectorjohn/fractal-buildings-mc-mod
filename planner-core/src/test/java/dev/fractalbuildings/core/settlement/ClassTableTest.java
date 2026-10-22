package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.Types;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ClassTableTest {
	/** Share of each type over 10,000 lots, in percent x 100 (big flat lots, so nothing is refused). */
	private static TreeMap<String, Integer> shares(int density, QuarterKind q) {
		TreeMap<String, Integer> n = new TreeMap<>();
		for (int i = 0; i < 10_000; i++) {
			Optional<ClassTable.Pick> p = ClassTable.pick(99, i * 7, i * 13 - 500, density, q, 0, 80, 80);
			p.ifPresent(x -> n.merge(x.type(), 1, Integer::sum));
		}
		return n;
	}

	private static void assertTable(int density, QuarterKind q) {
		var entries = ClassTable.entries(density, q);
		int total = entries.stream().mapToInt(ClassTable.Entry::weight).sum();
		TreeMap<String, Integer> got = shares(density, q);
		for (var e : entries) {
			double want = 100.0 * e.weight() / total, have = got.getOrDefault(e.type(), 0) / 100.0;
			assertEquals(want, have, 2.0, "density " + density + " " + q + " " + e.type());
		}
		assertTrue(entries.stream().filter(e -> e.weight() == 0).allMatch(e -> !got.containsKey(e.type())), "weight 0 never drawn");
	}

	@Test
	void distributionMatchesTheTableWithin2Percent() {
		for (int d : new int[] {10, 40, 60, 75, 90, 100}) assertTable(d, QuarterKind.RESIDENTIAL);
	}

	@Test
	void quarterOverrides() {
		assertTable(30, QuarterKind.WORKSHOP);
		assertTable(60, QuarterKind.WATERFRONT);
		assertTrue(!shares(60, QuarterKind.WATERFRONT).containsKey("block"), "waterfront swaps block for shop");
		for (int i = 0; i < 200; i++) {
			var p = ClassTable.pick(1, i, i, 95, QuarterKind.WATERFRONT, 0, 80, 80).orElseThrow();
			assertTrue(p.maxStoreys() <= ClassTable.WATERFRONT_CAP, p.toString());
			assertTrue(!p.type().equals("highrise"));
		}
		assertEquals(Optional.empty(), ClassTable.pick(1, 0, 0, 50, QuarterKind.PARK, 0, 80, 80));
		// workshop weight doubles in a workshop quarter
		int plain = shares(30, QuarterKind.RESIDENTIAL).getOrDefault("workshop", 0), work = shares(30, QuarterKind.WORKSHOP).getOrDefault("workshop", 0);
		assertTrue(work > plain * 1.5, plain + " vs " + work);
	}

	@Test
	void storeysStayInsideTypeLimitsAndHighriseGrowsWithDensity() {
		for (int d = 0; d <= 100; d += 5) {
			for (int i = 0; i < 300; i++) {
				var p = ClassTable.pick(5, i * 31, i * 17, d, QuarterKind.DOWNTOWN, 0, 80, 80).orElseThrow();
				var lim = Types.get(p.type()).limits();
				assertTrue(p.minStoreys() >= lim.minStoreys() && p.maxStoreys() <= lim.maxStoreys() && p.minStoreys() <= p.maxStoreys(), d + " " + p);
			}
		}
		var low = ClassTable.entries(86, QuarterKind.DOWNTOWN).get(0);
		var high = ClassTable.entries(100, QuarterKind.DOWNTOWN).get(0);
		assertEquals("highrise", high.type());
		assertTrue(high.min() > low.min() && high.max() >= 48 && low.min() >= 20, low + " " + high);
	}

	/** D3: mean top storeys per density band rise monotonically; no high-rise below density 70; the mid-rise fills the gap between 5 and 20 storeys. */
	@Test
	void skylineRisesWithDensityAndTheMidRiseFillsTheGap() {
		double prev = 0;
		for (int d : new int[] {10, 35, 60, 75, 80, 85, 90, 95, 100}) {
			long sum = 0;
			int n = 4000;
			for (int i = 0; i < n; i++) sum += ClassTable.pick(11, i * 3, i * 5, d, QuarterKind.RESIDENTIAL, 0, 100, 44).orElseThrow().maxStoreys();
			double mean = sum / (double) n;
			assertTrue(mean > prev, "mean storeys at density " + d + " is " + mean + ", not above " + prev);
			prev = mean;
		}
		for (int d = 0; d < 70; d++) {
			for (int i = 0; i < 200; i++) assertTrue(!ClassTable.pick(2, i, d, d, QuarterKind.DOWNTOWN, 0, 100, 44).orElseThrow().type().equals("highrise"), "high-rise below 70 at " + d);
		}
		int mid = 0;
		for (int d = 70; d < 100; d++) {
			int[] s = ClassTable.highriseStoreys(d);
			assertTrue(s[0] <= s[1] && s[0] >= 10 && (d < 85 ? s[1] <= 19 : s[0] >= 20), d + " " + s[0] + ".." + s[1]);
			if (d < 85) mid++;
			assertTrue(d == 70 || s[0] >= ClassTable.highriseStoreys(d - 1)[0], "the lowest top never falls with density at " + d);
		}
		assertEquals(15, mid);
	}

	/** P5 skyline: a tower lot is peaked (many near 20, few near the density's tallest) and high-rises skip steep ground. */
	@Test
	void towerHeightsArePeakedAndHighrisesAvoidSteepLots() {
		assertTrue(ClassTable.reliefAllows("highrise", ClassTable.HIGHRISE_MAX_RELIEF) && !ClassTable.reliefAllows("highrise", 13) && ClassTable.reliefAllows("shop", 40));
		int n = 3000, low = 0, high = 0, top = 0;
		for (int i = 0; i < n; i++) {
			var p = ClassTable.pick(21, i * 7, i * 11, 100, QuarterKind.DOWNTOWN, 0, 60, 60).orElseThrow();
			if (!p.type().equals("highrise")) continue;
			assertTrue(p.minStoreys() >= 20 && p.minStoreys() <= p.maxStoreys() && p.maxStoreys() <= 60, p.toString());
			if (p.maxStoreys() < 35) low++;
			if (p.maxStoreys() >= 50) top++;
			high++;
		}
		assertTrue(high > 1000 && low * 100 >= high * 55 && top * 100 <= high * 15 && top > 0, low + " below 35 and " + top + " from 50 of " + high + " towers at density 100");
		for (int i = 0; i < 200; i++) assertTrue(ClassTable.pick(5, i, i * 3, 95, QuarterKind.DOWNTOWN, 13, 60, 60).map(q -> !q.type().equals("highrise")).orElse(true));
	}

	@Test
	void oldTownHasNeitherBlocksNorHighrises() {
		for (int d = 0; d <= 100; d += 5) {
			for (int i = 0; i < 300; i++) {
				var p = ClassTable.pick(4, i * 7, i * 3 + d, d, QuarterKind.OLD_TOWN, 0, 100, 44).orElseThrow();
				assertTrue(!p.type().equals("block") && !p.type().equals("highrise") && p.maxStoreys() <= ClassTable.OLD_TOWN_CAP, d + " " + p);
			}
		}
	}

	@Test
	void smallOrSteepLotsSkipTypesTheyCannotTake() {
		// 7x7 only fits a house; a 30x30 lot is too small for a block (52) or highrise (20 fits!)
		for (int i = 0; i < 100; i++) {
			var p = ClassTable.pick(3, i, -i, 20, QuarterKind.RESIDENTIAL, 0, 8, 8).orElseThrow();
			assertEquals("house", p.type());
		}
		assertEquals(Optional.empty(), ClassTable.pick(3, 0, 0, 20, QuarterKind.RESIDENTIAL, 0, 5, 5));
		for (int i = 0; i < 100; i++) assertTrue(!ClassTable.pick(3, i, i, 60, QuarterKind.RESIDENTIAL, 0, 30, 30).orElseThrow().type().equals("block"));
		// a block needs gentle ground: a 52x52 lot with 30 relief refuses it, flat accepts it
		boolean flat = false;
		for (int i = 0; i < 200; i++) {
			assertTrue(!ClassTable.pick(3, i, i, 60, QuarterKind.RESIDENTIAL, 30, 60, 60).orElseThrow().type().equals("block"));
			flat |= ClassTable.pick(3, i, i, 60, QuarterKind.RESIDENTIAL, 0, 60, 60).orElseThrow().type().equals("block");
		}
		assertTrue(flat);
	}

	@Test
	void pureAndHashedPerLot() {
		assertEquals(ClassTable.pick(7, 10, 20, 55, QuarterKind.MARKET, 1, 40, 40), ClassTable.pick(7, 10, 20, 55, QuarterKind.MARKET, 1, 40, 40));
		assertTrue(shares(40, QuarterKind.RESIDENTIAL).size() > 2, "the draw varies with the lot position");
	}
}
