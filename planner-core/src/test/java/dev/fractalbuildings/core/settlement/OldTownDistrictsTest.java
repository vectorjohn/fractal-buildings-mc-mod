package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** IRR1: old-town tiles are cut into different-size blocks (StreetLayout.SUBDIVIDE) and still obey every district rule. */
class OldTownDistrictsTest {
	static boolean subdivided(Districts.Detail d) { return d.notes().stream().anyMatch(n -> n.startsWith("old-town subdivision")); }

	/** Cities and metropolises of the synthetic sites (the towns have no old town). */
	static List<Integer> bigCases(int n) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; out.size() < n; i++) if (i % 3 != 0) out.add(i);
		return out;
	}

	@Test
	void oldTownTilesAreSubdividedAndKeepEveryRule() {
		int tiles = 0, sub = 0, lots = 0, streets = 0, squares = 0, sdLanes = 0;
		TreeMap<String, Integer> bad = new TreeMap<>();
		for (int i : bigCases(40)) {
			DistrictsTest.Planned p = DistrictsTest.shared(i);
			for (Districts.Detail d : DistrictsTest.details(i)) {
				tiles++;
				if (!subdivided(d)) continue;
				sub++;
				lots += d.plan().lots().size();
				for (Violation v : d.plan().violations()) bad.merge(v.invariant() + " (case " + i + ")", 1, Integer::sum);
				for (Street s : d.plan().streets()) {
					if (!s.id().startsWith("sd") || s.id().startsWith("sdk")) continue;
					streets++;
					if (s.kind() == RoadKind.LANE) sdLanes++;
					assertTrue(s.kind() == RoadKind.STREET || s.kind() == RoadKind.LANE);
					assertEquals(s.kind() == RoadKind.STREET ? Subdivision.FIRST : Subdivision.LANE, s.width());
				}
				for (Plaza z : d.plan().plazas()) if (z.id().startsWith("p") && d.notes().stream().anyMatch(n -> n.startsWith("old-town small") && n.contains(" " + z.x() + "," + z.z()))) {
					squares++;
					assertTrue(z.w() >= 16 && z.w() <= 24 && z.w() == z.d());
				}
			}
		}
		System.out.println(tiles + " tiles, " + sub + " subdivided, " + lots + " lots, " + streets + " subdivision streets (" + sdLanes + " lanes), " + squares + " small squares; bad " + bad);
		assertEquals(java.util.Map.of(), bad);
		assertTrue(sub >= 8, "old-town tiles are subdivided: " + sub);
		assertTrue(lots >= 8 * 8, "and packed with lots: " + lots);
		assertTrue(streets > sub, "several streets per tile");
		assertTrue(sdLanes > 0, "deeper levels are lanes");
		assertTrue(squares > 0, "some get a small square");
	}

	@Test
	void subdividedTilesAreTheSameInAnyOrderAndFromFreshSources() {
		int checked = 0;
		for (int i : bigCases(12)) {
			DistrictsTest.Planned p = DistrictsTest.planned(i);
			TreeMap<String, String> expected = new TreeMap<>();
			for (Districts.Detail d : p.all()) if (subdivided(d)) expected.put(d.plan().district().id(), DistrictJson.write(d.plan()));
			if (expected.isEmpty()) continue;
			List<DistrictSummary> order = new ArrayList<>(p.skeleton().districts());
			java.util.Collections.shuffle(order, new java.util.Random(i));
			DistrictsTest.Planned fresh = DistrictsTest.planned(i);
			TreeMap<String, String> actual = new TreeMap<>();
			for (DistrictSummary s : order) Districts.plan(fresh.src(), s.ix(), s.iz(), fresh.exact()).ifPresent(det -> { if (subdivided(det)) actual.put(det.plan().district().id(), DistrictJson.write(det.plan())); });
			assertEquals(expected, actual, "case " + i);
			checked += expected.size();
		}
		assertTrue(checked > 0, "some case has an old-town tile");
	}

	@Test
	void layoutByQuarter() {
		DefaultProgram pr = new DefaultProgram();
		for (Tier t : new Tier[] {Tier.CITY, Tier.METROPOLIS}) {
			assertEquals(StreetLayout.SUBDIVIDE, pr.streetLayout(t, QuarterKind.OLD_TOWN, 90), "old town, even dense");
			assertEquals(StreetLayout.SUBDIVIDE, pr.streetLayout(t, QuarterKind.MARKET, 40));
			assertEquals(StreetLayout.GRID, pr.streetLayout(t, QuarterKind.RESIDENTIAL, 60));
			assertEquals(StreetLayout.GRID, pr.streetLayout(t, QuarterKind.MARKET, 60));
			assertEquals(StreetLayout.FRONTAGE, pr.streetLayout(t, QuarterKind.RESIDENTIAL, 40));
		}
		for (Tier t : new Tier[] {Tier.HAMLET, Tier.VILLAGE, Tier.TOWN}) assertEquals(StreetLayout.FRONTAGE, pr.streetLayout(t, QuarterKind.MARKET, 40));
	}
}
