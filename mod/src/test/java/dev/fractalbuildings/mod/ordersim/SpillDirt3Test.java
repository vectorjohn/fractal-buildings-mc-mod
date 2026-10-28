package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.SpillKinds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * SPILLDIRT3: the order test's {@code light_gray_concrete->dirt [unknown]} (shuffle2, gate-temperate-city 1287,65,-9504), reproduced in the sim. A dark
 * oak of a neighbouring chunk that decorates after our write sets the dirt under its base cell; where that cell is our street surface the log is blocked
 * and the dirt replaces the lower of two road blocks. Writer of the dirt: the vanilla feature. The cell is accepted as spill (not as a generator fault),
 * only under a road surface; the same cell with no tree evidence anywhere else stays unknown.
 */
class SpillDirt3Test {
	static SimSites gate;
	static WorldPlanner planner;

	@BeforeAll
	static void boot() {
		SimBoot.boot();
		gate = SimSites.load("gate-temperate-city");
		planner = gate.planner();
		CONCRETE = named("light_gray_concrete");
		DIRT = Blocks.DIRT.defaultBlockState();
		AIR = Blocks.AIR.defaultBlockState();
	}

	static BlockState CONCRETE, DIRT, AIR;

	static BlockState named(String n) {
		for (net.minecraft.world.level.block.Block b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) if (SpillKinds.name(b.defaultBlockState()).equals(n)) return b.defaultBlockState();
		throw new AssertionError(n);
	}

	@Test
	void classifierAcceptsTheBaseOnlyUnderARoadSurface() {
		assertEquals("spill", SpillKinds.solidKind(CONCRETE, DIRT, CONCRETE, AIR, true));
		assertEquals("unknown", SpillKinds.solidKind(CONCRETE, DIRT, CONCRETE, AIR, false), "not a road cell");
		assertEquals("unknown", SpillKinds.solidKind(CONCRETE, DIRT, AIR, AIR, true), "open to the air: a plain stray block");
		assertEquals("unknown", SpillKinds.solidKind(CONCRETE, Blocks.STONE_BRICKS.defaultBlockState(), CONCRETE, AIR, true), "only the dirt family");
	}

	/** A dark oak on a two-block-thick street (found on the plan), in 14 orders: the dirt under the street's lower block is a spill in every order. */
	@Test
	void aDarkOakOnATwoBlockStreetLeavesOnlyAcceptedDirt() {
		int[][] spots = {{1205, -9601}};
		List<String> failures = new ArrayList<>();
		int spillCells = 0;
		for (int[] sp : spots) {
			int x = sp[0], z = sp[1];
			SimFeatures.Call c = SimFeatures.scripted("dark_oak", x, gate.probes().exact().floorY(x, z), z, gate.seed(), gate.probes().exact(), gate.probes().biome());
			assertNotNull(c, "the dark oak places on the bare terrain at " + x + "," + z);
			Scenario s = new Scenario("sd3", planner, gate.seed(), gate.probes().exact(), gate.probes().biome(), Math.floorDiv(x, 16) - 1, Math.floorDiv(z, 16) - 1, 3, List.of(c), false);
			List<SimOrders.Order> orders = new ArrayList<>(SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed));
			for (int k = 3; k < 12; k++) orders.add(SimOrders.shuffle(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed, k));
			OrderSim.Result res = OrderSim.runAll(s, orders, true);
			for (SimRun r : res.runs) {
				if (r.solidUnknown + r.airUnknown > 0) failures.add(r.order + " " + r.badCells);
				spillCells += r.histogram.getOrDefault("light_gray_concrete->dirt [spill]", 0);
			}
			assertTrue(res.runs.stream().allMatch(r -> r.contractViolations <= 0), "our own writes obey their contract");
		}
		assertEquals(List.of(), failures);
		// the control: the scenario still exercises the case (re-find the spot with the doubles scan in SPILLDIRT3's log if the street geometry moved)
		assertTrue(spillCells > 0, "the dark oak leaves dirt under the street in some order; otherwise this test no longer tests anything");
	}
}
