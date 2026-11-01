package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.probe.ParallelReads;
import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.SettlementSite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * CASCADE step 4 (A3): the link rule and the trim fan their independent pieces out on the reads fanout, and the answers (every gate, link state and
 * trimmed tile plan of the first settlements of pt1573 and w12345) equal the serial run's: on a pool with random start delays, in a shuffled asking
 * order, called from the test thread and from inside a pool thread, with the pool free and saturated, and by several threads at once on one planner.
 * The fan-out must also have run (a pool planner is asked to fan out more than once).
 */
@Timeout(1800)
class CascadeFanoutTest {
	record World(String label, long seed, Supplier<WorldProbes> probes) {}

	static final List<World> WORLDS = List.of(
		new World("pt1573", JoinWorld.PT1573_SEED, () -> JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, "pt1573-metro-s8", "pt1573-world-s64"))),
		new World("w12345", WorldPlannerRegionTest.SEED, () -> WorldPlannerRegionTest.WORLD));

	static final int SITES = 3;

	static WorldPlanner planner(World w, ParallelReads reads) {
		WorldProbes p = w.probes().get();
		if (reads != null) p = ParallelReadsPlannerTest.with(p, reads);
		return new WorldPlanner(w.seed(), WorldConfig.defaults(), p, null, WorldPlanner.Caches.DEFAULT);
	}

	/** Counts how often a fanout call really had several pieces (so the test covers the fan-out, not only the serial fallback). */
	static final class CountingReads implements ParallelReads {
		final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		final ParallelReadsPlannerTest.PoolReads inner;
		final dev.fractalbuildings.core.cache.Fanout counted;

		CountingReads(ExecutorService pool, int helpers, WorldProbes p) {
			inner = new ParallelReadsPlannerTest.PoolReads(pool, helpers, p.exact());
			counted = new dev.fractalbuildings.core.cache.Fanout() {
				@Override public void each(int n, java.util.function.IntConsumer piece) { if (n > 1) calls.incrementAndGet(); inner.fanout().each(n, piece); }

				@Override public int threads() { return inner.fanout().threads(); }
			};
		}

		@Override public dev.fractalbuildings.core.cache.Fanout fanout() { return counted; }

		@Override public void prefetch(List<int[]> boxes) { inner.prefetch(boxes); }
	}

	/** The questions, one per key, as closures over a planner: gate answers, link states and the trimmed plans round the first settlements. */
	static TreeMap<String, java.util.function.Function<WorldPlanner, String>> questions(World world) {
		WorldPlanner ref = planner(world, null);
		TreeMap<String, java.util.function.Function<WorldPlanner, String>> q = new TreeMap<>();
		int n = 0;
		for (var e : LinkShortCutTest.sites(ref).entrySet()) {
			if (n >= SITES) break;
			SettlementSite site = e.getValue();
			List<int[]> gates;
			try { gates = LinkShortCutTest.gates(ref, site); } catch (RuntimeException ex) { continue; }
			if (gates.isEmpty()) continue;
			// settlements whose approaches are drawn (some gate served), so the fan-out of the approaches has something to do
			boolean anyServed = false;
			for (int[] g : gates) anyServed |= LinkShortCutTest.attempt(() -> ref.exitServedAt(site, g[0], g[1])).equals("true");
			if (!anyServed) continue;
			n++;
			for (int[] g : gates) q.put(site.id() + " gate " + g[0] + "," + g[1], w -> LinkShortCutTest.attempt(() -> w.exitServedAt(w.siteOf(site.cells().get(0)).orElseThrow(), g[0], g[1])));
			var s = ref.roads().settlement(new dev.fractalbuildings.core.region.CellKey(site.claimant().cx(), site.claimant().cz()));
			if (s.isPresent()) for (RoadNet.EdgeKey k : ref.roads().edgesOf(s.get())) q.put(site.id() + " link " + k, w -> LinkShortCutTest.attempt(() -> w.linkState(k)));
			DistrictGrid.Tile t = ref.grid().tileAt(site.centre().x(), site.centre().z());
			for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) {
				int ix = t.ix() + dx, iz = t.iz() + dz;
				q.put(site.id() + " tile " + ix + "," + iz, w -> LinkShortCutTest.attempt(() -> w.tile(ix, iz).isPresent() ? DistrictJson.write(w.plan(ix, iz).plan()) : "none"));
			}
		}
		assertTrue(n > 0, world.label() + ": settlements with gates");
		return q;
	}

	static TreeMap<String, String> answers(TreeMap<String, java.util.function.Function<WorldPlanner, String>> q, WorldPlanner w, List<String> order) {
		TreeMap<String, String> out = new TreeMap<>();
		for (String k : order) out.put(k, q.get(k).apply(w));
		return out;
	}

	static void same(String what, TreeMap<String, String> want, TreeMap<String, String> got) {
		assertEquals(want.keySet(), got.keySet(), what);
		for (var e : want.entrySet()) assertEquals(e.getValue(), got.get(e.getKey()), what + ": " + e.getKey());
	}

	@Test
	void aPoolGivesTheSameAnswersInAnyOrder() throws Exception {
		int fanouts = 0;
		for (World world : WORLDS) {
			var q = questions(world);
			List<String> keys = new ArrayList<>(q.keySet());
			TreeMap<String, String> want = answers(q, planner(world, null), keys);
			for (int round = 0; round < 3; round++) {
				List<String> order = new ArrayList<>(keys);
				if (round > 0) Collections.shuffle(order, new Random(97L * round));
				QuayLazyTest.SlowPool pool = new QuayLazyTest.SlowPool(6, 31L * round + world.label().hashCode());
				try {
					CountingReads reads = new CountingReads(pool, 5, world.probes().get());
					WorldPlanner w = planner(world, reads);
					Future<TreeMap<String, String>> f = round == 2 ? pool.submit(() -> answers(q, w, order)) : null; // from inside a pool thread
					TreeMap<String, String> got = f != null ? f.get(25, TimeUnit.MINUTES) : answers(q, w, order);
					same(world.label() + " round " + round, want, got);
					fanouts += reads.calls.get();
				} finally {
					pool.shutdownNow();
				}
			}
		}
		assertTrue(fanouts > 0, "the planner fanned out");
	}

	/** Every thread of the pool is held by a task that waits: the caller runs every piece itself and still ends, with the same answers. */
	@Test
	void aSaturatedPoolGivesTheSameAnswers() throws Exception {
		World world = WORLDS.get(0);
		var q = questions(world);
		List<String> keys = new ArrayList<>(q.keySet());
		TreeMap<String, String> want = answers(q, planner(world, null), keys);
		ExecutorService pool = Executors.newFixedThreadPool(4);
		CountDownLatch release = new CountDownLatch(1);
		try {
			for (int i = 0; i < 4; i++) pool.execute(() -> { try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
			WorldPlanner w = planner(world, new CountingReads(pool, 4, world.probes().get()));
			same("saturated", want, answers(q, w, keys));
		} finally {
			release.countDown();
			pool.shutdownNow();
		}
	}

	/** Eight threads start together on one planner, each asking a different tile's trimmed plan (and so the same cascade): all equal the serial plans. */
	@Test
	void concurrentNeighboursShareOneCascade() throws Exception {
		World world = WORLDS.get(0);
		var q = questions(world);
		List<String> tiles = new ArrayList<>();
		for (String k : q.keySet()) if (k.contains(" tile ")) tiles.add(k);
		String first = tiles.get(0).substring(0, tiles.get(0).indexOf(" tile "));
		tiles.removeIf(k -> !k.startsWith(first + " "));
		TreeMap<String, String> want = answers(q, planner(world, null), tiles);
		ExecutorService callers = Executors.newFixedThreadPool(8);
		try {
			for (int round = 0; round < 3; round++) {
				QuayLazyTest.SlowPool pool = new QuayLazyTest.SlowPool(6, 5L + round);
				try {
					WorldPlanner w = planner(world, new CountingReads(pool, 5, world.probes().get()));
					CountDownLatch go = new CountDownLatch(1);
					List<Future<String>> fs = new ArrayList<>();
					for (int i = 0; i < 8; i++) {
						String key = tiles.get((i * 5 + round) % tiles.size());
						fs.add(callers.submit(() -> { go.await(); return key + "=" + q.get(key).apply(w); }));
					}
					go.countDown();
					for (Future<String> f : fs) {
						String r = f.get(25, TimeUnit.MINUTES);
						String key = r.substring(0, r.indexOf('='));
						assertEquals(want.get(key), r.substring(key.length() + 1), "round " + round + " " + key);
					}
				} finally {
					pool.shutdownNow();
				}
			}
		} finally {
			callers.shutdownNow();
		}
	}
}
