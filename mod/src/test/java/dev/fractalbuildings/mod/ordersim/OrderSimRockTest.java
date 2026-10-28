package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.Mutations;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ORDERSIM S4a: the pinned forest rock case. Vanilla's {@code forest_rock} ({@code BlockBlobFeature}) sets blocks with no replace check, so a
 * rock of a neighbour chunk overwrites a claimed solid street cell (light gray concrete became mossy cobblestone) when the street's chunk ran
 * first. It occurs in old growth pine and spruce taiga, where the taiga style builds, so it is a real order dependence in the game. SC1 and SC5
 * leave {@code forest_rock} out of their pool for that reason ({@link OrderSimTest}).
 *
 * <p>This test finds the case itself: it runs SC1's 4 x 4 window with the full pool, takes the unknown cell, reads the call that wrote it from
 * the report, and replays that one call alone. Today the fast order set fails C3 on it (mossy cobblestone over a street cell).
 *
 * <p>TODO(ROCKVETO): when the production veto lands (a veto on forest_rock like {@code LakeVeto}, applied by {@code SimFeatures.place} too),
 * flip {@link #FIXED} to true: the same scenario must then pass every check in every order.
 */
class OrderSimRockTest {
	/** ROCKVETO flips this to true when the veto is in production and in the sim. */
	static final boolean FIXED = true;

	static Scenario full, single;
	static SimFeatures.Call rock;

	@BeforeAll
	static void build() {
		SimBoot.boot();
		SimSites gate = SimSites.load("gate-temperate-city");
		WorldPlanner planner = gate.planner();
		ScenarioFinder.Window w = new ScenarioFinder(planner, gate.site(), gate.probes().exact()).densestWindow(4);
		full = Scenario.city("rock-full", gate, planner, w.cx(), w.cz(), w.n(), 3, false, List.of(), false);
		// the rock calls of the full pool that can reach the inner window: find the one the forward and reverse runs name as the last writer
		var orders = OrderSimTest.fast(full);
		Mutations.mode = Mutations.ROCKS; // ROCKVETO: with the veto on no rock lands, so find the case with it off
		try {
			for (int i = 0; i < 2 && rock == null; i++) {
				SimRun r = OrderSim.run(full, orders.get(i), false);
				for (String cell : r.badCells) {
					int at = cell.indexOf("feature:forest_rock@");
					if (at < 0) continue;
					String label = cell.substring(at).split("[ ;]")[0];
					for (SimFeatures.Call c : full.calls) if (c.label().equals(label)) rock = c;
				}
			}
		} finally {
			Mutations.mode = null; // the replay below runs with the veto on
		}
		if (rock != null) single = new Scenario("rock", planner, gate.seed(), gate.probes().exact(), gate.probes().biome(), w.cx(), w.cz(), w.n(), List.of(rock), true);
	}

	@Test
	void aNeighbourForestRockOverAStreetCell() {
		assertNotNull(rock, "the full pool puts a forest rock over a claimed street cell in forward or reverse (if this fails the pool or the planner changed: find a new case)");
		OrderSim.Result res = OrderSim.runAll(single, OrderSimTest.fast(single), false);
		SimReport.write(res);
		System.out.println("[ordersim] rock " + rock.label() + ": " + res.failed());
		if (FIXED) {
			assertEquals(List.of(), res.failed(), "ROCKVETO: the rock no longer lands on a claimed cell in any order");
			return;
		}
		assertTrue(res.failed().stream().anyMatch(f -> f.contains("C3") && f.contains("unknown solid")), "today a forest rock overwrites a claimed solid cell: " + res.failed());
		boolean mossy = res.runs.stream().anyMatch(r -> r.badCells.stream().anyMatch(c -> c.contains("has mossy_cobblestone") && c.contains("feature:forest_rock")));
		assertTrue(mossy, "and it is the rock that did it");
	}
}
