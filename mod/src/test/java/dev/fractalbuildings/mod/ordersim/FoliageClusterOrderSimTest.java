package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * FOLIAGECLUSTER: {@code FoliageClear} must treat a floating tree the same whatever order the chunks' passes run in, also outside the cells our
 * pieces claim (the sim's C4 only compares claimed cells).
 *
 * <p>Scripted: chunk C (south of B) plants, in B, a floating natural leaf and a log beside it (nothing under them). The leaf is 6 blocks from chunk
 * A's edge (inside A's removal margin), the log 7 (outside it). A and B both run a foliage pass afterwards. B first: both go (B's pass covers both).
 * A first: A's pass cuts the leaf only, and B's pass then sees a log with no leaf: "a beam of a building", kept. Before the fix the log's fate
 * depended on the order; now a floating log goes whatever its neighbours were.
 */
class FoliageClusterOrderSimTest {
	static SimSites gate;
	static WorldPlanner planner;
	static ScenarioFinder.Window window;

	@BeforeAll
	static void build() {
		SimBoot.boot();
		gate = SimSites.load("gate-temperate-city");
		planner = gate.planner();
		window = new ScenarioFinder(planner, gate.site(), gate.probes().exact()).densestWindow(3);
	}

	@Test
	void aFloatingLogBesideALeafAnotherPassCutGoesInEveryOrder() {
		Scenario base = Scenario.city("foliagecluster", gate, planner, window.cx(), window.cz(), window.n(), 0, false, List.of(), false);
		final int ax = base.cx, az = base.cz, bx = ax + 1, cx = ax, cz = az + 1;
		final int leafX = bx * 16 + 5, logX = bx * 16 + 6, z = bz(az);
		final int y = plantY(base, leafX, logX, z); // ROADLEAFORDER: just over our claims (FoliageClear.own judges a chunk's trees only near them)
		final BlockPos leaf = new BlockPos(leafX, y, z), log = new BlockPos(logX, y, z);
		BlockState natural = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1);
		Scenario.Extra plant = (world, ccx, ccz) -> {
			if (ccx != cx || ccz != cz) return;
			var l = new SimLevel(world, ccx, ccz, "feature:tree@" + ccx + "," + ccz, 1L).proxy();
			dev.fractalbuildings.mod.worldgen.OwnWrites.enterFeature(); // ROADLEAFORDER: a tree of chunk C (FoliageClear.own judges C's own writes)
			dev.fractalbuildings.mod.worldgen.OwnWrites.enterTree();
			try {
				l.setBlock(leaf, natural, 2 | 16);
				l.setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 2 | 16);
			} finally {
				dev.fractalbuildings.mod.worldgen.OwnWrites.exitTree();
				dev.fractalbuildings.mod.worldgen.OwnWrites.exitFeature();
			}
		};
		Scenario s = base.withExtras("foliagecluster", true, plant);
		assertFalse(s.plan().ref.containsKey(PieceLedger.pack(leafX, y, z)) || s.plan().ref.containsKey(PieceLedger.pack(logX, y, z)), "the planted cells are unclaimed");
		List<String> logsLeft = new ArrayList<>(), leavesLeft = new ArrayList<>();
		for (int[][] o : new int[][][] {{{cx, cz}, {ax, az}, {bx, az}}, {{cx, cz}, {bx, az}, {ax, az}}}) {
			String name = (o[1][0] == ax ? "A-then-B" : "B-then-A");
			SimOrders.Order order = new SimOrders.Order(name, List.of(o));
			SimRun r = OrderSim.run(s, order, false);
			assertEquals(List.of(), r.errors, name);
			if (r.world.get(logX, y, z).is(BlockTags.LOGS)) logsLeft.add(name);
			if (r.world.get(leafX, y, z).is(BlockTags.LEAVES)) leavesLeft.add(name);
		}
		System.out.println("[ordersim] foliagecluster: log " + log + " leaf " + leaf + "; orders that left the log: " + logsLeft + ", the leaf: " + leavesLeft);
		assertEquals(List.of(), leavesLeft, "the floating leaf goes in every order");
		assertEquals(List.of(), logsLeft, "the floating log goes in every order");
	}

	private static int bz(int az) { return az * 16 + 3; }

	/** The lowest y over the terrain where both planted cells are unclaimed but a piece claims a cell next to them. */
	private static int plantY(Scenario base, int leafX, int logX, int z) {
		Scenario.Plan p = base.plan();
		for (int y = base.terrain.floorY(logX, z) + 2; y < base.terrain.floorY(logX, z) + 60; y++) {
			if (p.ref.containsKey(PieceLedger.pack(leafX, y, z)) || p.ref.containsKey(PieceLedger.pack(logX, y, z))) continue;
			if (!planner.claimRegion(new dev.fractalbuildings.core.ir.Box3(leafX - 1, y - 1, z - 1, 4, 3, 3)).isEmpty() && base.terrain.floorY(logX, z) + 6 < y) return y;
		}
		throw new IllegalStateException("no plant height at " + leafX + "," + z);
	}
}
