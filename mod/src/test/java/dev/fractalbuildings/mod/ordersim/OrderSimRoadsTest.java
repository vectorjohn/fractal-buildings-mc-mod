package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.ordersim.SimOrders.Order;
import dev.fractalbuildings.mod.roads.RoadFeature;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ORDERSIM S5 (SC4 roads): the real road step ({@code RoadFeature.placeIn}) beside districts and hashed vanilla features, in many orders. */
class OrderSimRoadsTest {
	static Scenario sc4;
	static RoadWorld.Window win;
	static List<SimFeatures.Call> crowns;

	@BeforeAll
	static void build() {
		SimBoot.boot();
		long t0 = System.nanoTime();
		SimSites site = RoadWorld.site();
		WorldPlanner p = RoadWorld.planner(site);
		win = RoadWorld.densestWindow(p, 4);
		crowns = RoadWorld.crowns(p, site, win, 4);
		sc4 = Scenario.city("sc4", site, p, win.cx(), win.cz(), win.n(), 3, false, crowns, false);
		sc4.plan();
		System.out.println("[ordersim] sc4 window " + win + ", crowns " + crowns.size() + ", planner + plan " + (System.nanoTime() - t0) / 1_000_000 + " ms");
	}

	static List<Order> fast(Scenario s) { return SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed); }

	@Test
	void sc4RoadsBesideDistricts() {
		long t0 = System.nanoTime();
		OrderSim.Result res = OrderSim.runAll(sc4, fast(sc4), true);
		SimReport.write(res);
		System.out.println("[ordersim] sc4: " + res.runs.size() + " orders in " + (System.nanoTime() - t0) / 1_000_000 + " ms; road cells " + res.runs.get(0).roadCells + " (solid " + res.runs.get(0).roadSolid
			+ "), road digest " + res.runs.get(0).roadDigest.substring(0, 12) + ", histogram " + res.runs.get(0).histogram + ", fired " + res.runs.get(0).branchWrites);
		assertEquals(List.of(), res.failed());
		assertTrue(res.runs.get(0).roadSolid > 20, "the road really draws in the window: " + res.runs.get(0).roadSolid);
	}

	// ---------------------------------------------------------------- liveness and controls

	@Test
	void roadPassesFire() {
		OrderSim.Result res = OrderSim.runAll(sc4, fast(sc4).subList(0, 2), false);
		assertEquals(List.of(), res.failed());
		int leaves = 0, draws = 0;
		for (SimRun r : res.runs) {
			leaves += r.branchWrites.getOrDefault("SpillClear.roadLeaves/leaf", 0);
			draws += r.branchWrites.getOrDefault("RoadFeature.put/other", 0) + r.branchWrites.getOrDefault("RoadFeature.put/air", 0);
		}
		assertTrue(leaves > 0, "roadLeaves removed a leaf: " + res.runs.get(0).branchWrites);
		assertTrue(draws > 0, "the road draw wrote blocks");
		assertTrue(res.runs.get(0).branchWrites.getOrDefault("RoadFeature.draw/leaf", 0) > 0, "the road clear took a leaf out of the headroom: " + res.runs.get(0).branchWrites);
	}

	/** Plants {@code block} over the ground block of every 4th dry road column (outside district columns) of the chunk, before its road step. */
	static Scenario.Extra plant(Scenario s, Block block, java.util.List<int[]> planted) {
		return (world, cx, cz) -> {
			boolean[] district = s.planner.districtColumns(cx, cz);
			SimLevel lv = new SimLevel(world, cx, cz, "feature:plant@" + cx + "," + cz, 1L);
			for (var col : s.planner.roadColumns(cx, cz)) {
				int x = col.x(), z = col.z();
				if (Math.floorDiv(x, 16) != cx || Math.floorDiv(z, 16) != cz || (x * 7 + z * 3) % 4 != 0) continue;
				if (district != null && district[(z & 15) * 16 + (x & 15)]) continue;
				if (s.terrain.surfaceY(x, z) != s.terrain.floorY(x, z)) continue;
				lv.proxy().setBlock(new net.minecraft.core.BlockPos(x, s.terrain.floorY(x, z) - 1, z), block.defaultBlockState(), 2 | 16);
				synchronized (planted) { planted.add(new int[] {x, z}); }
			}
		};
	}

	static int lowest(SimRun r, int x, int z) {
		int best = Integer.MAX_VALUE;
		for (var e : r.roadStates.entrySet()) {
			long k = e.getKey();
			if (PieceLedger.x(k) == x && PieceLedger.z(k) == z) best = Math.min(best, PieceLedger.y(k));
		}
		return best;
	}

	/** The ground scan passes a leaf or a log lying on the ground block, so the road is where it is on stone-topped ground one block higher. */
	@Test
	void groundScanPassesALeafAndALog() {
		Order forward = fast(sc4).get(0);
		java.util.List<int[]> stoneCols = new java.util.ArrayList<>(), leafCols = new java.util.ArrayList<>(), logCols = new java.util.ArrayList<>();
		SimRun stone = OrderSim.run(sc4.withExtras("sc4-stone", false, plant(sc4, Blocks.STONE, stoneCols)), forward, false);
		SimRun leaf = OrderSim.run(sc4.withExtras("sc4-leaf", false, plant(sc4, Blocks.OAK_LEAVES, leafCols)), forward, false);
		SimRun log = OrderSim.run(sc4.withExtras("sc4-log", false, plant(sc4, Blocks.OAK_LOG, logCols)), forward, false);
		assertEquals(List.of(), leaf.failed(false));
		assertEquals(List.of(), log.failed(false));
		int lower = 0, same = 0, checked = 0;
		for (int[] c : stoneCols) {
			if (c[0] < sc4.cx * 16 || c[0] >= (sc4.cx + sc4.n) * 16 || c[1] < sc4.cz * 16 || c[1] >= (sc4.cz + sc4.n) * 16) continue;
			int s = lowest(stone, c[0], c[1]), l = lowest(leaf, c[0], c[1]), g = lowest(log, c[0], c[1]);
			if (s == Integer.MAX_VALUE || l == Integer.MAX_VALUE || g == Integer.MAX_VALUE) continue;
			checked++;
			assertTrue(l <= s && g <= s, "a leaf or a log never raises the lowest road cell: stone " + s + " leaf " + l + " log " + g + " at " + c[0] + "," + c[1]);
			if (l == s - 1 && g == s - 1) lower++; else same++;
		}
		System.out.println("[ordersim] sc4 scan: planted columns checked " + checked + ", lowest road cell one lower than on stone with both leaf and log " + lower + ", otherwise " + same);
		assertTrue(lower >= 3, "the scan passed the planted leaf and log in at least 3 columns: " + lower + " of " + checked);
	}

	@Test
	void mutantGroundFromMotionBlockingIsCaught() {
		RoadFeature.MUTANT_GROUND = true;
		try {
			List<String> failed = OrderSim.runAll(sc4, fast(sc4), false).failed();
			System.out.println("[ordersim] sc4 MUTANT_GROUND: " + failed.size() + " failures, e.g. " + (failed.isEmpty() ? "-" : failed.get(0)));
			assertFalse(failed.isEmpty(), "reading the ground from MOTION_BLOCKING must fail a check");
		} finally {
			RoadFeature.MUTANT_GROUND = false;
		}
	}
}
