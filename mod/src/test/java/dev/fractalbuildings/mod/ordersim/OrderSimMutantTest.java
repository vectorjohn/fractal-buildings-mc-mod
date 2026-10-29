package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.mod.ordersim.RegressionScenarios.LakeKind;
import dev.fractalbuildings.mod.ordersim.SimOrders.Order;
import dev.fractalbuildings.mod.worldgen.Mutations;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * ORDERSIM S4b: every mutant switch of {@link Mutations} must make the sim fail the check that is meant to catch it, on its scenario in forward
 * and reverse. The same scenario without the mutant passes (the control), so a failure is the mutant's. Mutants run one after another because
 * {@code Mutations.mode} is global.
 *
 * <p>Tagged {@code heavy} (30 to 42 s): it tests the checks, not the product. The regression and scenario classes stay in the default run and
 * show the sim passes; this one runs with {@code -PfbTests=heavy} ({@code scripts/test-all.sh --planner-heavy}) and whenever the checks change.
 */
@Tag("heavy")
class OrderSimMutantTest {
	static final RegressionScenarios R = RegressionScenarios.get();

	@AfterEach
	void reset() { Mutations.mode = null; }

	static List<Order> twoOrders(Scenario s) { return SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed).subList(0, 2); }

	private static Scenario sc1;
	private static final java.util.Map<String, Boolean> CONTROLLED = new java.util.HashMap<>();

	/** SC1 once: its plan (the reference raster) is the same for every mutant. */
	static synchronized Scenario sc1() {
		if (sc1 != null) return sc1;
		var w = R.finder.densestWindow(2);
		return sc1 = Scenario.city("sc1", R.gate, R.planner, w.cx(), w.cz(), w.n(), 3, false, List.of(), false);
	}

	/** Runs the mutant in forward and reverse; returns the failed checks (each prefixed with the order). */
	static List<String> failures(String mutant, Scenario s) {
		Mutations.mode = mutant;
		try {
			OrderSim.Result res = OrderSim.runAll(s, twoOrders(s), false);
			List<String> f = res.failed();
			System.out.println("[ordersim-s4b] mutant " + mutant + " on " + s.id + ": " + f.size() + " failure(s), first: " + (f.isEmpty() ? "-" : f.get(0)));
			return f;
		} finally {
			Mutations.mode = null;
		}
	}

	private static List<String> caught(String mutant, Scenario s, String check, Predicate<String> detail, String what) {
		if (CONTROLLED.put(s.id, true) == null) assertEquals(List.of(), failures(null, s), "the control passes on " + s.id); // once per scenario
		List<String> f = failures(mutant, s);
		assertTrue(f.stream().anyMatch(l -> l.contains(check) && detail.test(l)), mutant + " must fail " + check + " (" + what + ") on " + s.id + ", got " + f);
		return f;
	}

	private static Predicate<String> has(String s) { return l -> l.contains(s); }

	// ---------------------------------------------------------------- the two regressions

	/** ORDERFAIL: with no veto the lake of SC2a writes into the dug-in wall in the order where the wall is already built. */
	@Test
	void lakesMutantFailsC3OnSc2a() {
		Scenario s = R.lakeScenario(R.lake(LakeKind.DUG_IN_WALL), RegressionScenarios.LAKE_INTO_WALL);
		List<String> f = caught(Mutations.LAKES, s, "C3", has("unknown"), "lava, cave air or barrier stone where our wall is");
		assertTrue(f.stream().anyMatch(l -> l.startsWith("reverse")), "found in reverse, where the lake runs after the wall: " + f);
	}

	/** SC2b without the veto: the lake reaches the deep foundation under the building, so the veto must look at it (the deep foundation is not in the box). */
	@Test
	void lakesMutantFailsC3OnSc2b() {
		Scenario s = R.lakeScenario(R.lake(LakeKind.UNDER_BOX));
		caught(Mutations.LAKES, s, "C3", has("unknown"), "lava, cave air or barrier stone in the foundation");
	}

	/** SPILLDIRT third hit: without the planned-air restore the trunk's dirt stays in the street's air. */
	@Test
	void trunkBaseMutantFailsC3OnSc3b() {
		Scenario s = R.trunkScenario(R.trunkInStreet(), "sc3-STREET");
		// ROADLEAFORDER: FoliageClear.own puts back the ground under a trunk of the chunk's own tree in planned street air too; StreetAirFix is measured alone
		dev.fractalbuildings.mod.worldgen.FoliageClear.OWN = false;
		try {
			caught(Mutations.TRUNK_BASE, s, "C3", has("unknown air"), "dirt left in planned street air");
		} finally {
			dev.fractalbuildings.mod.worldgen.FoliageClear.OWN = true;
		}
	}

	/**
	 * The pre-2026-10-23 SpillClear clears logs and the cells under them. In the sim it is the write contract (C5, "cleared a log") that names it,
	 * at the write; the plan expected C3.
	 */
	@Test
	void spillLogsMutantFailsC5OnSc3() {
		caught(Mutations.SPILL_LOGS, R.trunkScenario(R.trunkInStreet(), "sc3-STREET"), "C5", has("cleared a log"), "SpillClear.run clearing a trunk");
		caught(Mutations.SPILL_LOGS, R.trunkScenario(R.trunk(ScenarioFinder.TrunkKind.ON_SURFACE)), "C5", has("cleared a log"), "SpillClear.run clearing a trunk");
	}

	// ---------------------------------------------------------------- the generic mutants, on SC1

	@Test
	void randomMutantChangesTheDigest() {
		caught(Mutations.RANDOM, sc1(), "C2", has("digest differs"), "the written blocks depend on the random");
	}

	@Test
	void counterMutantBreaksTheLedgerAgainstTheRaster() {
		caught(Mutations.COUNTER, sc1(), "C2", has("ledger against raster"), "every 7th block is stone bricks");
	}

	@Test
	void noChunkClipMutantWritesOutsideItsChunk() {
		caught(Mutations.NO_CHUNK_CLIP, sc1(), "C5", has("outside its chunk"), "a TilePiece write outside its chunk");
	}

	@Test
	void skipAirMutantLeavesSolidInClaimedAir() {
		caught(Mutations.SKIP_AIR, sc1(), "C3", has("unknown air"), "air never written");
	}

	@Test
	void worldStoneMutantIsFoundByTheWorldCheck() {
		caught(Mutations.WORLD_STONE, sc1(), "C3", has("unknown solid"), "stone in the world, correct state in the ledger");
	}

	/** In the sim the real structure class is asked its step, so the mutant is found by C1's step check (the plan expected C3). */
	@Test
	void earlyStepMutantMovesTheStructureStep() {
		caught(Mutations.EARLY_STEP, sc1(), "C1", has("SURFACE_STRUCTURES"), "our structures run before the vegetation");
	}

	@Test
	void flag2MutantMissesFlag16() {
		caught(Mutations.FLAG2, sc1(), "C5", has("UPDATE_KNOWN_SHAPE"), "writes with flag 2 alone");
	}

	@Test
	void twiceMutantClaimsCellsTwice() {
		caught(Mutations.TWICE, sc1(), "C2", has("duplicate"), "each ground cell claimed twice");
	}
}
