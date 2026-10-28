package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * TREEINORDER: {@code BuildingAirClear} must take a log out of the air a building claims whatever order the chunks generate in.
 *
 * <p>Before the fix a log in claimed air went only when its connected logs touched a natural leaf, or a leaf cut in the same call. A leaf that a
 * neighbour chunk's call had already cut looks like plain air, so the log stayed or went by chunk order. The scripted case: chunk C (west of
 * the inner window) puts a natural leaf in a claimed air cell of its own after its pieces (a crown of its trees; its pass cuts it), and its east
 * neighbour puts a log with no leaf of its own in the claimed air cell beside it (a fallen log, a trunk whose crown went). Forward runs C
 * first: the log arrives after the leaf was cut and stayed. Reverse runs the neighbour first: C's piece wrote air over the log. Now every
 * order ends with air in both cells.
 */
class TreeInOrderSimTest {
	static SimSites gate;
	static WorldPlanner planner;
	static ScenarioFinder.Window window;

	@BeforeAll
	static void build() {
		SimBoot.boot();
		gate = SimSites.load("gate-temperate-city");
		planner = gate.planner();
		window = new ScenarioFinder(planner, gate.site(), gate.probes().exact()).densestWindow(2);
	}

	/** Two claimed air cells side by side in x (log west, leaf east), both in chunk (cx, cz), above the terrain. */
	static long[] pair(Scenario s, int cx, int cz) {
		Scenario.Plan p = s.plan();
		for (long k : p.innerKeys) {
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			if (x >> 4 != cx || z >> 4 != cz || (x & 15) == 15) continue;
			if (y < s.terrain.floorY(x, z) + 2) continue;
			long east = PieceLedger.pack(x + 1, y, z);
			if (!isAir(p, k) || !p.ref.containsKey(east) || !isAir(p, east)) continue;
			return new long[] {k, east};
		}
		throw new AssertionError("no pair of claimed air cells in chunk " + cx + "," + cz);
	}

	static boolean isAir(Scenario.Plan p, long k) { return Block.BLOCK_STATE_REGISTRY.byId(p.ref.get(k)).isAir(); }

	static BlockPos pos(long k) { return new BlockPos(PieceLedger.x(k), PieceLedger.y(k), PieceLedger.z(k)); }

	@Test
	void aLogBesideALeafCutByAnotherChunkGoesInEveryOrder() {
		Scenario base = Scenario.city("treeinorder", gate, planner, window.cx(), window.cz(), window.n(), 0, false, List.of(), false);
		int cx = base.cx, cz = base.cz;
		long[] cells = pair(base, cx, cz);
		final long log = cells[0], leaf = cells[1];
		BlockState natural = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1);
		Scenario.Extra crown = (world, ccx, ccz) -> {
			if (ccx != cx || ccz != cz) return;
			new SimLevel(world, ccx, ccz, "feature:leaf@" + ccx + "," + ccz, 1L).proxy().setBlock(pos(leaf), natural, 2 | 16);
		};
		Scenario.Extra fallen = (world, ccx, ccz) -> {
			if (ccx != cx + 1 || ccz != cz) return;
			new SimLevel(world, ccx, ccz, "feature:log@" + ccx + "," + ccz, 2L).proxy().setBlock(pos(log), Blocks.OAK_LOG.defaultBlockState(), 2 | 16);
		};
		Scenario s = base.withExtras("treeinorder", true, crown, fallen);
		List<String> logsLeft = new ArrayList<>();
		for (SimOrders.Order o : SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed)) {
			SimRun r = OrderSim.run(s, o, false);
			assertEquals(List.of(), r.errors, o.name());
			BlockState atLog = r.world.get(PieceLedger.x(log), PieceLedger.y(log), PieceLedger.z(log));
			BlockState atLeaf = r.world.get(PieceLedger.x(leaf), PieceLedger.y(leaf), PieceLedger.z(leaf));
			assertFalse(atLeaf.is(BlockTags.LEAVES), o.name() + ": the planted natural leaf in claimed air is cut");
			if (atLog.is(BlockTags.LOGS)) logsLeft.add(o.name());
		}
		System.out.println("[ordersim] treeinorder: log at " + pos(log) + ", leaf at " + pos(leaf) + "; orders that left the log: " + logsLeft);
		assertEquals(List.of(), logsLeft, "a log in claimed air goes in every order");
	}
}
