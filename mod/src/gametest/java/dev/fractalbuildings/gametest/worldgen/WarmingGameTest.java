package dev.fractalbuildings.gametest.worldgen;

import static dev.fractalbuildings.gametest.worldgen.StartSupport.check;

import dev.fractalbuildings.core.probe.SyntheticBiomes;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.mod.worldgen.LaneStats;
import dev.fractalbuildings.mod.worldgen.RegionWarmer;
import dev.fractalbuildings.mod.worldgen.Workers;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Server game tests of prefetch, warming and the lane counters (plan B4). Warming only fills caches, so a world planned with it must be
 * block for block the world planned without it: same ledger digest, same world hash. Same synthetic flat-plains town as
 * {@link WorldgenGameTest} (seed 6).
 */
// gametest-order: 4
public class WarmingGameTest {
	private static WorldPlanner planner(ThreadPoolExecutor pool) {
		WorldProbes pr = WorldProbes.of(SyntheticTerrain.flat(70), SyntheticBiomes.constant("minecraft:plains"));
		WorldPlanner w = new WorldPlanner(6, WorldConfig.defaults().withIntercityRoads(false), pr, pool);
		LaneStats.attach(w);
		return w;
	}

	/** What placing the tile's building and ground starts made: the ledger digest and the world hash. */
	private record Result(String digest, long world, int cells) {}

	private static Result placeTile(ServerLevel level, WorldPlanner w) {
		WorldgenGameTest.Target t = WorldgenGameTest.flatTarget(w);
		WorldPlanners.overrideForTests(w);
		WorldPlanners.FAILURES.set(0);
		try {
			StartSupport.clear(level, StartSupport.start(level, t.structure(), t.chunk()));
			StartSupport.clear(level, StartSupport.start(level, "district_ground", t.chunk()));
			StructureStart b = StartSupport.start(level, t.structure(), t.chunk());
			StructureStart g = StartSupport.start(level, "district_ground", t.chunk());
			List<ChunkPos> chunks = new ArrayList<>(StartSupport.chunks(b));
			for (ChunkPos c : StartSupport.chunks(g)) if (!chunks.contains(c)) chunks.add(c);
			chunks.sort((x, y) -> x.z() != y.z() ? Integer.compare(x.z(), y.z()) : Integer.compare(x.x(), y.x()));
			var bg = new ArrayList<StructureStart>(List.of(b, g));
			StartSupport.Placed placed = place(level, bg, chunks);
			Result r = new Result(placed.digest(), StartSupport.worldHash(level, b), placed.cells().size());
			StartSupport.clear(level, b);
			StartSupport.clear(level, g);
			check(WorldPlanners.FAILURES.get() == 0, "the structures swallowed exceptions");
			return r;
		} finally {
			WorldPlanners.overrideForTests(null);
		}
	}

	private static StartSupport.Placed place(ServerLevel level, List<StructureStart> starts, List<ChunkPos> order) {
		java.util.TreeMap<Long, Integer> merged = new java.util.TreeMap<>();
		int outside = 0, dups = 0;
		for (StructureStart s : starts) {
			// each start alone over the whole order; the ledger of both is merged (a cell claimed by both would be a duplicate)
			StartSupport.Placed p = StartSupport.place(level, s, order, 1);
			outside += p.outsideChunk();
			dups += p.duplicates();
			for (var e : p.cells().entrySet()) if (merged.put(e.getKey(), e.getValue()) != null) dups++;
		}
		return new StartSupport.Placed(merged, outside, dups);
	}

	@GameTest(maxTicks = TestArea.TICKS)
	public void warmedAndColdWorldsPlaceTheSameBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldPlanner area = planner(null);
		TestArea.run(helper, WorldgenGameTest.area(level, area, WorldgenGameTest.flatTarget(area)), () -> {
			Result cold = placeTile(level, planner(null));
			check(cold.cells() > 2000, "cells claimed " + cold.cells());

			ThreadPoolExecutor pool = Workers.create(4);
			try {
				// (1) warmed and settled: region, skeletons, tile plans and buildings are all done ahead
				WorldPlanner w = planner(pool);
				WorldgenGameTest.Target t = WorldgenGameTest.flatTarget(planner(null));
				int cx = (t.tile().minX() + t.tile().maxX()) / 2, cz = (t.tile().minZ() + t.tile().maxZ()) / 2;
				w.warm(cx, cz, 2 * w.config().cellSize());
				w.warmQuays(cx, cz, 2 * w.config().cellSize());
				w.prefetch(t.tile().ix(), t.tile().iz());
				check(Workers.awaitIdle(pool, 120_000), "the workers did not finish the prefetch");
				LaneStats.enable(true);
				LaneStats.reset();
				w.observeBuildings(LaneStats::lookup);
				Result warm = placeTile(level, w);
				check(warm.equals(cold), "warmed world differs from the cold one: " + warm + " against " + cold);
				System.out.println("LANE after settled warm: " + LaneStats.report().replace('\n', '|'));
				check(LaneStats.done() > 0 && LaneStats.inline() == 0, "after prefetch every building lookup should be served ahead: done " + LaneStats.done() + " inline " + LaneStats.inline());

				// (2) a race: prefetch queued, the lane asks at once (it joins a worker or computes inline); still the same blocks
				WorldPlanner r = planner(pool);
				r.prefetch(t.tile().ix(), t.tile().iz());
				Result raced = placeTile(level, r);
				check(raced.equals(cold), "a world planned while the prefetch ran differs from the cold one");
				check(Workers.awaitIdle(pool, 120_000), "the workers did not finish");

				// (3) a world warmed through the warmer, as the server tick does it, then evicted: the next ask recomputes the same
				WorldPlanner e = planner(pool);
				check(RegionWarmer.warmPlayer(e, UUID.randomUUID(), cx, cz, pool), "the warm was refused");
				check(Workers.awaitIdle(pool, 120_000), "the workers did not finish the warm");
				e.evictAll();
				check(placeTile(level, e).equals(cold), "a warmed then evicted world differs from the cold one");
			} finally {
				LaneStats.enable(false);
				Workers.shutdown(pool);
			}
		});
	}

	@GameTest
	public void warmerFillsTheCachesAheadOfThePlayer(GameTestHelper helper) throws Exception {
		ThreadPoolExecutor pool = Workers.create(4);
		try {
			WorldPlanner probe = planner(null);
			WorldgenGameTest.Target t = WorldgenGameTest.flatTarget(probe);
			int cx = (t.tile().minX() + t.tile().maxX()) / 2, cz = (t.tile().minZ() + t.tile().maxZ()) / 2;
			WorldPlanner w = planner(pool);
			java.util.concurrent.atomic.AtomicInteger inline = new java.util.concurrent.atomic.AtomicInteger(), done = new java.util.concurrent.atomic.AtomicInteger();
			w.observeBuildings(l -> (l == WorldPlanner.Lookup.INLINE ? inline : done).incrementAndGet());
			UUID player = UUID.randomUUID();
			// first call: the player stands still and the warm covers the tile; the second, a second later, moves 40 blocks east
			check(RegionWarmer.warmPlayer(w, player, cx - 40, cz, pool), "first warm refused");
			check(Workers.awaitIdle(pool, 120_000), "workers busy");
			int before = inline.get();
			w.buildings(t.tile().ix(), t.tile().iz());
			check(inline.get() == before && done.get() > 0, "the tile's buildings should have been done ahead (inline " + (inline.get() - before) + ")");
		} finally {
			Workers.shutdown(pool);
		}
		helper.succeed();
	}


	/** A player standing still queues the neighbour plans of the tile once; later warms of the same tile queue nothing more (only their own warm task). */
	@GameTest
	public void warmerQueuesNeighbourPlansOnlyWhenTheTileChanges(GameTestHelper helper) throws Exception {
		ThreadPoolExecutor pool = Workers.create(4);
		try {
			WorldPlanner probe = planner(null);
			WorldgenGameTest.Target t = WorldgenGameTest.flatTarget(probe);
			int cx = (t.tile().minX() + t.tile().maxX()) / 2, cz = (t.tile().minZ() + t.tile().maxZ()) / 2;
			WorldPlanner w = planner(pool);
			java.util.concurrent.atomic.AtomicInteger tasks = new java.util.concurrent.atomic.AtomicInteger();
			java.util.concurrent.Executor counting = r -> { tasks.incrementAndGet(); pool.execute(r); };
			UUID player = UUID.randomUUID();
			int calls = 0;
			for (int i = 0; i < 4; i++) { // the first calls queue the neighbours of the player's tile (own point, then the point ahead; here the same tile)
				check(RegionWarmer.warmPlayer(w, player, cx, cz, counting), "warm refused");
				calls++;
				check(Workers.awaitIdle(pool, 120_000), "workers busy");
			}
			check(LaneStats.neighboursRunning() == 0, "a neighbour task is still counted after the pool went idle");
			int settled = tasks.get();
			check(settled <= calls + 2, "standing still queued " + (settled - calls) + " neighbour tasks (at most 2: own point and ahead)");
			for (int i = 0; i < 5; i++) {
				check(RegionWarmer.warmPlayer(w, player, cx, cz, counting), "warm refused");
				check(Workers.awaitIdle(pool, 120_000), "workers busy");
			}
			check(tasks.get() == settled + 5, "five warms of an unchanged tile queued " + (tasks.get() - settled - 5) + " extra tasks");
		} finally {
			Workers.shutdown(pool);
		}
		helper.succeed();
	}
	@GameTest
	public void shuttingTheWorkersDownLeavesNoThreads(GameTestHelper helper) throws Exception {
		// only this pool's own threads: other game tests run their pools in the same JVM at the same time (flake 00d70622, which counted
		// every worker thread alive), so the threads are recorded as the pool's factory makes them
		ThreadPoolExecutor pool = Workers.create(4);
		java.util.concurrent.ThreadFactory made = pool.getThreadFactory();
		List<Thread> mine = java.util.Collections.synchronizedList(new ArrayList<>());
		pool.setThreadFactory(r -> { Thread th = made.newThread(r); mine.add(th); return th; });
		java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(8);
		for (int i = 0; i < 8; i++) pool.execute(l::countDown);
		check(l.await(10, java.util.concurrent.TimeUnit.SECONDS), "tasks did not run");
		List<Thread> threads;
		synchronized (mine) { threads = new ArrayList<>(mine); }
		check(!threads.isEmpty(), "the pool should have started threads");
		check(threads.stream().allMatch(th -> th.getName().startsWith(Workers.PREFIX) && th.isDaemon()), "workers are daemons named " + Workers.PREFIX);
		Workers.shutdown(pool);
		// a terminated pool's last workers may still be leaving run(); a leaked one never ends, so a short join tells them apart
		for (Thread th : threads) th.join(2_000);
		long left = threads.stream().filter(Thread::isAlive).count();
		check(left == 0, "worker threads left: " + left);
		helper.succeed();
	}
}
