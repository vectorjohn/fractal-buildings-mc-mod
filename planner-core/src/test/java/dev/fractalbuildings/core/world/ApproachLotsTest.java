package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.road.RoadNet.Col;
import dev.fractalbuildings.core.settlement.Cell;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 6.15 ROADJOIN step 4: the district keeps lots off the approaches of its settlement (the approach columns are the district's, D5, so a lot
 * over one makes the approach vanish from the street to the lot's far edge). Corpus: every settlement of the playtest world's fixtures
 * (k4_4_0 had a 15-block gap, k4_5_0 8) and the five world-12345 cells of {@link WorldPlannerRoadsTest}.
 */
class ApproachLotsTest {
	record Result(String world, int lotsOnApproach, int gaps, int approaches) {}

	static Result check(String name, WorldPlanner w, List<int[]> cells) {
		int onLots = 0, gaps = 0, n = 0;
		TreeSet<String> done = new TreeSet<>();
		for (int[] c : cells) {
			var so = w.roads().settlement(new CellKey(c[0], c[1]));
			var site = w.siteOf(new Cell(c[0], c[1])).orElse(null);
			if (so.isEmpty() || site == null || !done.add(site.id())) continue;
			for (WorldPlanner.Approach a : w.drawnApproaches(site, so.get())) {
				n++;
				TreeSet<Long> tiles = new TreeSet<>();
				for (Col k : a.columns()) { var t = w.grid().tileOf(k.x(), k.z()); tiles.add(JoinWorld.key(t.ix(), t.iz())); }
				for (long tk : tiles) {
					int ix = (int) (tk & 0xffffffffL), iz = (int) (tk >> 32);
					if (w.tile(ix, iz).isEmpty()) continue;
					for (var lot : w.plan(ix, iz).plan().lots()) {
						for (Col k : a.columns()) {
							if (k.x() >= lot.x() && k.x() < lot.x() + lot.w() && k.z() >= lot.z() && k.z() < lot.z() + lot.d()) {
								onLots++;
								System.out.println(name + " lot " + lot.id() + " on approach " + a.road().id() + " at " + k.x() + "," + k.z());
								break;
							}
						}
					}
				}
			}
			for (var j : JoinWorld.joins(w, c[0], c[1])) {
				if (j.exitTilePlanned() && j.gap() != 0 && j.gap() != Integer.MAX_VALUE) { gaps++; System.out.println(name + " GAP " + j); }
			}
		}
		System.out.println(name + ": approaches " + n + ", lots on an approach " + onLots + ", gaps " + gaps);
		return new Result(name, onLots, gaps, n);
	}

	@Test
	void playtestWorld() {
		List<int[]> cells = new ArrayList<>();
		for (int cz = -1; cz <= 5; cz++) for (int cx = -1; cx <= 4; cx++) cells.add(new int[] {cx, cz});
		Result r = check("pt", JoinWorld.playtest(), cells);
		assertTrue(r.approaches() >= 10, "corpus has approaches");
		assertEquals(0, r.lotsOnApproach(), "no lot on an approach");
		assertEquals(0, r.gaps(), "no gap between an approach and its street (k4_4_0 15, k4_5_0 8 before)");
	}

	@Test
	void world12345() {
		WorldPlanner w = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		Result r = check("w12345", w, java.util.Arrays.asList(WorldPlannerRoadsTest.CELLS));
		assertEquals(0, r.lotsOnApproach(), "no lot on an approach");
		assertEquals(0, r.gaps(), "no gap between an approach and its street");
	}
}
