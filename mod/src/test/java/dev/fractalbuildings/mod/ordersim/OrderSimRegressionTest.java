package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.mod.ordersim.RegressionScenarios.LakeKind;
import dev.fractalbuildings.mod.ordersim.ScenarioFinder.TrunkKind;
import dev.fractalbuildings.mod.ordersim.ScenarioFinder.TrunkSpot;
import dev.fractalbuildings.mod.ordersim.SimOrders.Order;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ORDERSIM S4b: the two real failures of 2026-10-27/28 as scenarios, on gate-temperate-city. SC2 is ORDERFAIL (a vanilla lava lake over dug-in
 * walls), SC3 is SPILLDIRT (dark oak trunk-base dirt on a street). On the current code every check passes in the fast orders, and the passes that
 * fix them really fire (liveness). {@link OrderSimMutantTest} shows the checks fail without the fixes.
 */
class OrderSimRegressionTest {
	static final RegressionScenarios R = RegressionScenarios.get();

	static List<Order> fast(Scenario s) { return SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed); }

	static OrderSim.Result run(Scenario s) {
		long t0 = System.nanoTime();
		OrderSim.Result res = OrderSim.runAll(s, fast(s), true);
		SimReport.write(res);
		System.out.println("[ordersim-s4b] " + s.id + ": " + res.runs.size() + " orders in " + (System.nanoTime() - t0) / 1_000_000 + " ms (plan " + res.planMs + " ms), features "
			+ res.runs.get(0).features + ", loadavg " + SimReport.load());
		for (SimRun r : res.runs) System.out.println("[ordersim-s4b]   " + r.order + " " + r.features + " hist " + r.histogram + (r.branchWrites.isEmpty() ? "" : " branch " + r.branchWrites));
		return res;
	}

	private static int lake(SimRun r, int what) { return r.features.byKey.get("lake_lava")[what]; }

	/** SC2a: the lake's grid meets a dug-in wall: no lake in any order, the veto is counted, and the wall is whole. */
	@Test
	void sc2aLakeBesideADugInWallIsVetoed() {
		OrderSim.Result res = run(R.lakeScenario(R.lake(LakeKind.DUG_IN_WALL), RegressionScenarios.LAKE_INTO_WALL));
		assertEquals(List.of(), res.failed());
		for (SimRun r : res.runs) {
			assertEquals(0, lake(r, 1), r.order + ": the lake is not placed");
			assertEquals(1, lake(r, 2), r.order + ": the veto is counted");
		}
	}

	/** SC2b: only the deep foundation is met (the box is not): the veto must look at the rastered box. */
	@Test
	void sc2bLakeUnderTheBoxIsVetoedByTheDeepFoundation() {
		OrderSim.Result res = run(R.lakeScenario(R.lake(LakeKind.UNDER_BOX)));
		assertEquals(List.of(), res.failed());
		for (SimRun r : res.runs) {
			assertEquals(0, lake(r, 1), r.order + ": the lake is not placed");
			assertEquals(1, lake(r, 2), r.order + ": the veto is counted");
		}
	}

	/** SC2c, the control: nothing of ours is near, so the lake is placed in every order (the veto is not a blanket). */
	@Test
	void sc2cLakeFarBelowIsPlacedInEveryOrder() {
		OrderSim.Result res = run(R.lakeScenario(R.lake(LakeKind.BELOW)));
		assertEquals(List.of(), res.failed());
		for (SimRun r : res.runs) {
			assertEquals(1, lake(r, 1), r.order + ": the lake is placed");
			assertEquals(0, lake(r, 2), r.order + ": not vetoed");
		}
	}

	private static int branch(OrderSim.Result res, String key) {
		return res.runs.stream().mapToInt(r -> r.branchWrites.getOrDefault(key, 0)).sum();
	}

	private static boolean placedIn(OrderSim.Result res, int orders) {
		return res.runs.stream().filter(r -> r.features.placed() > 0).count() >= orders;
	}

	/** SC3 (a) trunk on a street surface at the natural level, grown by the street chunk's neighbour: the street is whole. */
	@Test
	void sc3aTrunkOnTheSurfacePasses() {
		OrderSim.Result res = run(R.trunkScenario(R.trunk(TrunkKind.ON_SURFACE)));
		assertEquals(List.of(), res.failed());
		assertTrue(placedIn(res, 6), "the tree grows in every order");
		assertTrue(branch(res, "StreetAirFix.run") > 0 || branch(res, "SpillClear.run") > 0, "a pass wrote near the trunk: " + res.runs.get(0).branchWrites);
	}

	/** SC3 buried: the natural-level base is under the raised street, so the log is blocked or covered. */
	@Test
	void sc3BuriedBasePasses() {
		OrderSim.Result res = run(R.trunkScenario(R.trunk(TrunkKind.BURIED)));
		assertEquals(List.of(), res.failed());
		assertTrue(res.runs.stream().anyMatch(r -> r.features.placed() > 0), "the tree grows in some order");
	}

	/** SC3 (c) the base at the top of a street cut's headroom: dirt under the hillside's grass is accepted spill; restoreBase fires. */
	@Test
	void sc3cTrunkInACutHeadroomPasses() {
		OrderSim.Result res = run(R.trunkScenario(R.trunk(TrunkKind.CUT_HEADROOM)));
		assertEquals(List.of(), res.failed());
		assertTrue(placedIn(res, 6), "the tree grows in every order");
		assertTrue(branch(res, "StreetAirFix.restoreBase") > 0, "restoreBase fired: " + res.runs.get(0).branchWrites);
	}

	/**
	 * SC3 (b), the SPILLDIRT third hit: the whole 2 x 2 base is on the street across a chunk line, in planned air. {@code StreetAirFix} removes
	 * the trunk and {@code restoreBase} writes air over the dirt it stood on, in every order (reverse runs the street's chunk first and the tree's
	 * pass last).
	 */
	@Test
	void sc3bTrunkOverStreetAirHasItsDirtRemoved() {
		TrunkSpot t = R.trunkInStreet();
		assertNotNull(t, "a street with a 2 x 2 base across a chunk line, cut 2 or more below the ground");
		OrderSim.Result res = run(R.trunkScenario(t, "sc3-STREET"));
		assertEquals(List.of(), res.failed());
		for (SimRun r : res.runs) assertEquals(1, r.features.placed(), r.order + ": the tree grows");
		SimRun forward = res.runs.get(0), reverse = res.runs.get(1);
		assertTrue(forward.branchWrites.getOrDefault("StreetAirFix.restoreBase", 0) + reverse.branchWrites.getOrDefault("StreetAirFix.restoreBase", 0) > 0,
			"restoreBase wrote over dirt: " + forward.branchWrites + " " + reverse.branchWrites);
	}
}
