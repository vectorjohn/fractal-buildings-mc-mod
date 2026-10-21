package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** IRR1 (the DL half of 5.13): diagonal avenues get cut lanes with frontage lots, and big leftover triangles become greens. */
class AvenueDistrictsTest {
	static boolean hasCutLane(Districts.Detail d) { return d.plan().streets().stream().anyMatch(s -> s.id().startsWith("av")); }

	@Test
	void diagonalAvenuesGetCutLanesWithLotsAndEveryRuleHolds() {
		int tiles = 0, withLane = 0, lanes = 0, lotsInTiles = 0, greens = 0;
		TreeMap<String, Integer> bad = new TreeMap<>();
		for (int i : OldTownDistrictsTest.bigCases(40)) {
			DistrictsTest.Planned p = DistrictsTest.planned(i);
			for (Districts.Detail d : p.all()) {
				tiles++;
				for (Violation v : d.plan().violations()) bad.merge(v.invariant() + " (case " + i + ")", 1, Integer::sum);
				if (d.notes().stream().anyMatch(n -> n.startsWith("avenue green"))) {
					greens++;
					for (Plaza z : d.plan().plazas()) if (z.kind().equals("green") && d.notes().stream().anyMatch(n -> n.startsWith("avenue green") && n.endsWith(" " + z.x() + "," + z.z()))) assertTrue(z.w() >= 16 && z.w() <= 20 && z.w() == z.d());
				}
				if (!hasCutLane(d)) continue;
				withLane++;
				lotsInTiles += d.plan().lots().size();
				for (Street s : d.plan().streets()) {
					if (!s.id().startsWith("av")) continue;
					lanes++;
					assertEquals(RoadKind.LANE, s.kind());
					assertEquals(StreetRules.LANE_WIDTH, s.width());
					Pt a = s.points().get(0), b = s.points().get(s.points().size() - 1);
					assertTrue(a.x() == b.x() || a.z() == b.z(), "cut lanes run along an axis: " + s.id());
					assertTrue(Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) >= Districts.AVE_LANE_MIN - 1);
				}
			}
		}
		System.out.println(tiles + " tiles, " + withLane + " with cut lanes (" + lanes + " lanes), " + lotsInTiles + " lots there, " + greens + " tiles with an avenue green; bad " + bad);
		assertEquals(java.util.Map.of(), bad);
		assertTrue(lanes >= 10, "avenues get cut lanes: " + lanes);
		assertTrue(lotsInTiles > 20 * withLane, "and the tiles are packed with lots");
	}

	@Test
	void tilesWithCutLanesAreTheSameInAnyOrder() {
		int checked = 0;
		for (int i : OldTownDistrictsTest.bigCases(10)) {
			DistrictsTest.Planned p = DistrictsTest.planned(i);
			TreeMap<String, String> expected = new TreeMap<>();
			for (Districts.Detail d : p.all()) if (hasCutLane(d)) expected.put(d.plan().district().id(), DistrictJson.write(d.plan()));
			if (expected.isEmpty()) continue;
			List<DistrictSummary> order = new ArrayList<>(p.skeleton().districts());
			java.util.Collections.shuffle(order, new java.util.Random(i + 5));
			DistrictsTest.Planned fresh = DistrictsTest.planned(i);
			TreeMap<String, String> actual = new TreeMap<>();
			for (DistrictSummary s : order) Districts.plan(fresh.src(), s.ix(), s.iz(), fresh.exact()).ifPresent(det -> { if (hasCutLane(det)) actual.put(det.plan().district().id(), DistrictJson.write(det.plan())); });
			assertEquals(expected, actual, "case " + i);
			checked += expected.size();
		}
		assertTrue(checked > 0);
	}

	@Test
	void diagonalRunsFindOnlyDiagonalSteps() {
		List<int[]> cols = new ArrayList<>();
		for (int k = 0; k < 10; k++) cols.add(new int[] {k, 0});
		for (int k = 1; k <= 25; k++) cols.add(new int[] {9 + k, k});
		for (int k = 1; k <= 8; k++) cols.add(new int[] {34, 25 + k});
		List<int[]> runs = Districts.diagonalRuns(cols);
		assertEquals(1, runs.size());
		assertEquals(9, runs.get(0)[0]);
		assertEquals(34, runs.get(0)[1]);
	}
}
