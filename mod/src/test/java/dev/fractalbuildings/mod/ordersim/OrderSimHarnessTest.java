package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.ordersim.SimOrders.Order;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ORDERSIM S3: the harness on SC1 (gate-temperate-city, the densest 2 x 2 window). Smoke: every check passes in the fast order set, in a few
 * seconds. Determinism: the same order twice gives the same result. Controls: a world change after the run, and a planted order dependence (a
 * test-only step that reads a neighbour chunk), are caught, so the comparator can fail.
 */
class OrderSimHarnessTest {
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

	static Scenario sc1(int k) {
		return Scenario.city("sc1", gate, planner, window.cx(), window.cz(), window.n(), k, false, List.of(), false);
	}

	static List<Order> fast(Scenario s) { return SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed); }

	@Test
	void smokeFastOrdersPass() {
		Scenario s = sc1(3);
		s.plan(); // the reference raster is built once per scenario: time the orders, not it
		long t0 = System.nanoTime(), c0 = java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
		OrderSim.Result res = OrderSim.runAll(s, fast(s), true);
		long ms = (System.nanoTime() - t0) / 1_000_000;
		long cpuMs = (java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - c0) / 1_000_000; // this thread CPU time: independent of host load (wall clock flaked at 10115 ms on a busy perf worker)
		SimReport.write(res);
		System.out.println("[ordersim] SC1 smoke: " + res.runs.size() + " orders in " + ms + " ms (cpu " + cpuMs + " ms), loadavg " + SimReport.load());
		assertEquals(List.of(), res.failed());
		assertEquals(6, res.runs.size());
		assertTrue(res.runs.get(0).features.placed() > 20, "the hashed features really place: " + res.runs.get(0).features);
		assertTrue(res.runs.get(0).ledgerCells > 1000, "the window claims cells");
		assertTrue(res.runs.get(0).contractViolations == 0 && res.runs.get(1).contractViolations == 0, "C5 ran in forward and reverse");
		assertTrue(cpuMs < 30_000, "a few seconds of CPU (3 s on a quiet host): " + cpuMs + " cpu ms, " + ms + " wall ms");
	}

	@Test
	void sameOrderTwiceGivesTheSameResult() {
		Scenario s = sc1(3);
		Order forward = fast(s).get(0);
		SimRun a = OrderSim.run(s, forward, false), b = OrderSim.run(s, forward, false);
		assertEquals(a.ledgerDigest, b.ledgerDigest);
		assertArrayEquals(a.have, b.have, "the claimed cells are the same");
		assertEquals(a.histogram, b.histogram);
		assertEquals(a.features.toString(), b.features.toString());
		// and a fresh scenario (a fresh plan) on a fresh planner, too
		Scenario s2 = Scenario.city("sc1", gate, gate.planner(), window.cx(), window.cz(), window.n(), 3, false, List.of(), false);
		assertArrayEquals(a.have, OrderSim.run(s2, forward, false).have);
	}

	@Test
	void aWorldChangeInAClaimedAirCellIsCaughtByC3() {
		Scenario s = sc1(0);
		Order forward = fast(s).get(0);
		SimRun clean = OrderSim.run(s, forward, false);
		assertEquals(List.of(), clean.failed(false), "the control: no features, nothing to find");
		long[] cell = firstClaimedAir(s);
		SimRun bad = OrderSim.run(s, forward, false, w -> w.chunk(PieceLedger.x(cell[0]) >> 4, PieceLedger.z(cell[0]) >> 4)
			.setBlockState(new BlockPos(PieceLedger.x(cell[0]), PieceLedger.y(cell[0]), PieceLedger.z(cell[0])), Blocks.STONE.defaultBlockState(), 0));
		assertEquals(1, bad.airUnknown, "stone in claimed air is an unknown spill");
		assertTrue(bad.failed(false).stream().anyMatch(f -> f.startsWith("C3")), bad.failed(false).toString());
		assertFalse(bad.badCells.isEmpty());
		System.out.println("[ordersim] planted: " + bad.badCells.get(0));
	}

	/** An air cell the pieces claim, above the terrain (so only a planted block can be there). */
	static long[] firstClaimedAir(Scenario s) {
		Scenario.Plan p = s.plan();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (long k : p.innerKeys) {
			if (Block.BLOCK_STATE_REGISTRY.byId(p.ref.get(k)) != air) continue;
			if (PieceLedger.y(k) >= s.terrain.floorY(PieceLedger.x(k), PieceLedger.z(k)) + 2) return new long[] {k};
		}
		throw new AssertionError("no claimed air cell");
	}

	/**
	 * A planted order dependence: when chunk C runs, it reads a claimed solid cell of its east neighbour (a piece wrote it there if the
	 * neighbour ran first) and, if it finds one, puts stone in a claimed air cell of its own. Forward order runs C first; reverse runs it last.
	 */
	@Test
	void aPlantedNeighbourReadIsCaught() {
		Scenario base = sc1(0);
		Scenario.Plan p = base.plan();
		int cx = base.cx, cz = base.cz; // west chunk of the inner window; its east neighbour is cx + 1
		long[] airCell = null, solidCell = null;
		for (long k : p.innerKeys) {
			int x = PieceLedger.x(k), z = PieceLedger.z(k);
			boolean air = Block.BLOCK_STATE_REGISTRY.byId(p.ref.get(k)).isAir();
			if (air && airCell == null && x >> 4 == cx && z >> 4 == cz && p.ref.containsKey(PieceLedger.pack(x, PieceLedger.y(k) - 1, z))) airCell = new long[] {k};
			if (!air && solidCell == null && x >> 4 == cx + 1 && z >> 4 == cz && PieceLedger.y(k) >= base.terrain.floorY(x, z)) solidCell = new long[] {k};
		}
		assertNotNull(airCell);
		assertNotNull(solidCell);
		final long a = airCell[0], sol = solidCell[0];
		Scenario.Extra planted = (world, ccx, ccz) -> {
			if (ccx != cx || ccz != cz) return;
			SimLevel lv = new SimLevel(world, ccx, ccz, "feature:planted@" + ccx + "," + ccz, 1L);
			BlockState east = lv.proxy().getBlockState(new BlockPos(PieceLedger.x(sol), PieceLedger.y(sol), PieceLedger.z(sol)));
			if (!east.isAir() && !east.is(Blocks.STONE)) lv.proxy().setBlock(new BlockPos(PieceLedger.x(a), PieceLedger.y(a), PieceLedger.z(a)), Blocks.STONE.defaultBlockState(), 2 | 16);
		};
		Scenario s = base.withExtras("planted", true, planted);
		OrderSim.Result res = OrderSim.runAll(s, fast(s), false);
		SimReport.write(res);
		List<String> failed = res.failed();
		assertFalse(failed.isEmpty(), "the planted dependence is found");
		assertTrue(failed.stream().anyMatch(f -> f.contains("C4")), "as a difference between orders: " + failed);
		assertTrue(failed.stream().anyMatch(f -> f.contains("C3")), "and as an unknown block in a claimed cell: " + failed);
		assertEquals(0, res.runs.get(0).crossDiff + 1, "forward is the baseline");
		SimRun reverse = res.runs.get(1);
		assertTrue(reverse.crossDiff >= 1, "reverse runs the west chunk after its east neighbour: " + SimReport.line(reverse));
		assertTrue(reverse.diffCells.get(0).contains("feature:planted"), "the report names the writer: " + reverse.diffCells.get(0));
	}
}
