package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.cache.Fanout;
import dev.fractalbuildings.core.probe.ParallelReads;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.DistrictJson;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * PARREADS: a planner whose region resolutions, road links and junction fields fan out on a pool, and whose quay trace prefetches, gives the
 * same bytes as the serial planner: on the river town of world 12345 (a waterfront town with intercity roads) at the recorded fixtures, run
 * from the calling thread and from inside the pool's own thread, with the pool free and with it saturated.
 */
class ParallelReadsPlannerTest {
	static final long SEED = 12345L;
	/** The river town (CLAUDE.md city fixture) and the cell size reach warm uses in the game (two cells). */
	static final int X = -900, Z = -3500, REACH = 2048;

	static WorldProbes base() { return JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, "w12345-river-s8", "world-12345-s64")); }

	/** Parallel reads over {@code pool}: the fanout, and a prefetch that reads every column of the boxes' 4-lattice on the pool. */
	static final class PoolReads implements ParallelReads {
		final Fanout fanout;
		final TerrainProbe exact;
		final AtomicLong boxes = new AtomicLong();

		PoolReads(ExecutorService pool, int helpers, TerrainProbe exact) {
			this.fanout = Fanout.over(() -> pool, helpers);
			this.exact = exact;
		}

		@Override public Fanout fanout() { return fanout; }

		@Override public void prefetch(List<int[]> bs) {
			boxes.addAndGet(bs.size());
			fanout.each(bs.size(), i -> {
				int[] b = bs.get(i);
				for (int z = b[1]; z <= b[3]; z += 4) for (int x = b[0]; x <= b[2]; x += 4) exact.waterDepth(x, z);
			});
		}
	}

	static WorldProbes with(WorldProbes p, ParallelReads r) {
		return new WorldProbes(p.region(), p.coarse(), p.exact(), p.biome(), p.window(), p.roadExact(), r);
	}

	/** Warm, quays, then the plans of the tiles round the town and the road columns of chunks round it, as text. */
	static String digest(WorldPlanner w) { return digest(w, false); }

	/** {@code together}: one warm with the quays (as the mod's warmer), else warm then warmQuays. */
	static String digest(WorldPlanner w, boolean together) {
		if (together) w.warm(X, Z, REACH, true);
		else {
			w.warm(X, Z, REACH);
			w.warmQuays(X, Z, REACH);
		}
		StringBuilder sb = new StringBuilder();
		DistrictGrid.Tile t = w.grid().tileAt(X, Z);
		for (int dz = -3; dz <= 3; dz++) {
			for (int dx = -3; dx <= 3; dx++) if (w.tile(t.ix() + dx, t.iz() + dz).isPresent()) sb.append(DistrictJson.write(w.plan(t.ix() + dx, t.iz() + dz).plan()));
		}
		int cx = X >> 4, cz = Z >> 4;
		for (int d = -96; d <= 96; d += 6) sb.append(w.roadColumns(cx + d, cz)).append(w.roadColumns(cx, cz + d));
		int s = w.config().cellSize();
		for (int j = -2; j <= 2; j++) for (int i = -2; i <= 2; i++) sb.append(w.roads().edgesNearCell(new CellKey(Math.floorDiv(X, s) + i, Math.floorDiv(Z, s) + j)));
		return sb.toString();
	}

	static String serial() { return digest(new WorldPlanner(SEED, WorldConfig.defaults(), base(), null)); }

	static final String SERIAL = serial();

	@Test
	void theSerialRunPlansTheWaterfrontTown() {
		assertTrue(SERIAL.contains("\"quay"), "the river town has quays (else this test would not cover the quay prefetch)");
	}

	@Test
	void aPoolGivesTheSameBytes() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(6);
		try {
			WorldProbes p = base();
			PoolReads r = new PoolReads(pool, 6, p.exact());
			assertEquals(SERIAL, digest(new WorldPlanner(SEED, WorldConfig.defaults(), with(p, r), null)));
			assertTrue(r.boxes.get() > 0, "the quay trace prefetched");
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void warmWithQuaysOnAPoolGivesTheSameBytes() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(6);
		try {
			WorldProbes p = base();
			assertEquals(SERIAL, digest(new WorldPlanner(SEED, WorldConfig.defaults(), with(p, new PoolReads(pool, 6, p.exact())), null), true));
			assertEquals(SERIAL, digest(new WorldPlanner(SEED, WorldConfig.defaults(), p, null), true), "serially too");
		} finally {
			pool.shutdownNow();
		}
	}

	/** Called from the pool's only thread (a worker warming ahead of a player): no deadlock (the helpers it asks for queue behind it), same bytes. */
	@Test
	void fromInsideItsOwnOneThreadPool() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(1);
		try {
			WorldProbes p = base();
			WorldPlanner w = new WorldPlanner(SEED, WorldConfig.defaults(), with(p, new PoolReads(pool, 3, p.exact())), null);
			Future<String> f = pool.submit(() -> digest(w));
			try {
				assertEquals(SERIAL, f.get(5, TimeUnit.MINUTES));
			} catch (java.util.concurrent.TimeoutException e) {
				throw new AssertionError("no answer in 5 min (deadlock?)\n" + WorldPlannerOrderTest.threadDump(), e);
			}
		} finally {
			pool.shutdownNow();
		}
	}

	/** Two planners' callers share one pool and run at once (players warming two places): same bytes for both. */
	@Test
	void twoCallersShareThePool() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(3), callers = Executors.newFixedThreadPool(2);
		try {
			WorldProbes p = base();
			Future<String> a = callers.submit(() -> digest(new WorldPlanner(SEED, WorldConfig.defaults(), with(p, new PoolReads(pool, 3, p.exact())), null)));
			Future<String> b = callers.submit(() -> digest(new WorldPlanner(SEED, WorldConfig.defaults(), with(p, new PoolReads(pool, 3, p.exact())), null)));
			assertEquals(SERIAL, a.get(10, TimeUnit.MINUTES));
			assertEquals(SERIAL, b.get(10, TimeUnit.MINUTES));
		} finally {
			pool.shutdownNow();
			callers.shutdownNow();
		}
	}
}
