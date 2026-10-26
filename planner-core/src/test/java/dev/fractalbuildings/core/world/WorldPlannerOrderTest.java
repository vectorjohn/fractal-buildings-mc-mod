package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.DistrictJson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Determinism of the world planner (ADR 0003 D6, DT-style): plan, buildings and ground of 30 tiles of {@code gate-metropolis-mixed}
 * are the same bytes in forward, reverse and shuffled order, on 8 threads, with every cache at capacity 1 to 3 and an evictor thread
 * emptying them all the while, and with prefetch running on a pool. The mutant probes at the bottom show the harness fails when a
 * result depends on call order or on an unsynchronised memo.
 */
class WorldPlannerOrderTest {
	static final int TILES = 30;
	static final GateWorld GATE = GateWorld.load("gate-metropolis-mixed");

	/** Everything the planner says about one tile, as text. */
	static String digest(WorldPlanner w, int ix, int iz) {
		StringBuilder sb = new StringBuilder();
		sb.append(DistrictJson.write(w.plan(ix, iz).plan()));
		List<Lot> lots = new ArrayList<>();
		List<PlacedBuilding> bs = new ArrayList<>();
		for (BuildingPiece p : w.buildings(ix, iz)) {
			lots.add(p.lot());
			bs.add(p.building());
			sb.append(p.ref()).append(p.brief()).append(p.box());
		}
		List<Box> ground = new ArrayList<>();
		for (GroundPiece g : w.ground(ix, iz)) {
			ground.addAll(g.boxes());
			sb.append(g.ref()).append(g.chunkX()).append(',').append(g.chunkZ()).append(g.box());
		}
		sb.append(IrJson.write(new SceneDoc(GeneratorV1.GEN_VERSION, GATE.seed(), null, lots, bs, ground, List.of())));
		return sb.toString();
	}

	/** The first {@link #TILES} planned tiles in id order, as {ix, iz}. */
	static List<int[]> tiles() {
		WorldPlanner w = GATE.planner(WorldPlanner.Caches.DEFAULT, null);
		List<int[]> out = new ArrayList<>();
		for (TileInfo t : WorldPlannerParityTest.planned(w, GATE).values()) {
			if (t.tier() != null && out.size() < TILES) out.add(new int[] {t.ix(), t.iz()});
		}
		// spread: every k-th tile so the sample covers the settlement, not a corner
		List<int[]> all = new ArrayList<>();
		for (TileInfo t : WorldPlannerParityTest.planned(w, GATE).values()) all.add(new int[] {t.ix(), t.iz()});
		List<int[]> pick = new ArrayList<>();
		for (int i = 0; i < TILES; i++) pick.add(all.get(i * all.size() / TILES));
		return pick;
	}

	static final List<int[]> TILE_LIST = tiles();
	static final Map<String, String> BASELINE = baseline();

	static String id(int[] t) { return t[0] + "," + t[1]; }

	static Map<String, String> baseline() {
		WorldPlanner w = GATE.planner(WorldPlanner.Caches.DEFAULT, null);
		Map<String, String> out = new TreeMap<>();
		for (int[] t : TILE_LIST) out.put(id(t), digest(w, t[0], t[1]));
		return out;
	}

	static void assertSame(Map<String, String> got, String what) {
		assertEquals(BASELINE.keySet(), got.keySet(), what);
		for (var e : BASELINE.entrySet()) assertTrue(e.getValue().equals(got.get(e.getKey())), what + ": tile " + e.getKey() + " differs");
	}

	static Map<String, String> run(WorldPlanner w, List<int[]> order) {
		Map<String, String> out = new TreeMap<>();
		for (int[] t : order) out.put(id(t), digest(w, t[0], t[1]));
		return out;
	}

	@Test
	void forwardReverseAndShuffledOrders() {
		assertTrue(TILE_LIST.size() == TILES);
		List<int[]> rev = new ArrayList<>(TILE_LIST);
		Collections.reverse(rev);
		assertSame(run(GATE.planner(WorldPlanner.Caches.DEFAULT, null), rev), "reverse");
		for (long seed : new long[] {1, 2}) {
			List<int[]> sh = new ArrayList<>(TILE_LIST);
			Collections.shuffle(sh, new Random(seed));
			assertSame(run(GATE.planner(WorldPlanner.Caches.DEFAULT, null), sh), "shuffled " + seed);
		}
	}

	@Test
	void tinyCachesGiveTheSameBytes() {
		for (int cap : new int[] {1, 2, 3}) {
			List<int[]> sh = new ArrayList<>(TILE_LIST);
			Collections.shuffle(sh, new Random(cap));
			// each tile twice in a row, so a capacity-1 cache hits and misses
			List<int[]> twice = new ArrayList<>();
			for (int[] t : sh) { twice.add(t); twice.add(t); }
			assertSame(run(GATE.planner(WorldPlanner.Caches.all(cap), null), twice), "capacity " + cap);
		}
	}

	/** {@code threads} threads each walk every tile in their own shuffled order; an optional evictor empties the caches meanwhile. */
	static void threaded(WorldPlanner w, int threads, boolean evictor, String what) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads + 1);
		AtomicBoolean done = new AtomicBoolean();
		Future<?> ev = evictor ? pool.submit(() -> {
			while (!done.get()) {
				w.evictAll();
				try { Thread.sleep(2); } catch (InterruptedException e) { return; }
			}
		}) : null;
		List<Future<Map<String, String>>> fs = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			List<int[]> order = new ArrayList<>(TILE_LIST);
			Collections.shuffle(order, new Random(100 + i));
			fs.add(pool.submit(() -> run(w, order)));
		}
		try {
			for (Future<Map<String, String>> f : fs) assertSame(f.get(5, TimeUnit.MINUTES), what);
		} finally {
			done.set(true);
			if (ev != null) ev.get(10, TimeUnit.SECONDS);
			pool.shutdownNow();
		}
	}

	@Test
	void eightThreads() throws Exception {
		threaded(GATE.planner(WorldPlanner.Caches.DEFAULT, null), 8, false, "8 threads");
	}

	@Test
	void eightThreadsTinyCachesAndAnEvictor() throws Exception {
		threaded(GATE.planner(WorldPlanner.Caches.all(1), null), 8, true, "8 threads, capacity 1, evictor");
	}

	@Test
	void prefetchOnAPoolChangesNothing() throws Exception {
		ExecutorService workers = Executors.newFixedThreadPool(4);
		try {
			WorldPlanner w = GATE.planner(new WorldPlanner.Caches(4096, 64, 64, 8192, 4, 4, 4).strict(), workers);
			List<int[]> sh = new ArrayList<>(TILE_LIST);
			Collections.shuffle(sh, new Random(7));
			Map<String, String> out = new TreeMap<>();
			for (int[] t : sh) {
				w.prefetch(t[0], t[1]);
				out.put(id(t), digest(w, t[0], t[1]));
			}
			assertSame(out, "prefetch");
		} finally {
			workers.shutdownNow();
		}
	}

	@Test
	void lookupObserverClassifiesWithoutChangingAnswers() throws Exception {
		ExecutorService workers = Executors.newFixedThreadPool(2);
		try {
			WorldPlanner w = GATE.planner(WorldPlanner.Caches.DEFAULT, workers);
			int[] counts = new int[3];
			w.observeBuildings(l -> { synchronized (counts) { counts[l.ordinal()]++; } });
			int[] t = TILE_LIST.get(0);
			String first = digest(w, t[0], t[1]); // computed inline by this thread
			assertEquals(first, BASELINE.get(id(t)));
			int inline;
			synchronized (counts) { inline = counts[WorldPlanner.Lookup.INLINE.ordinal()]; }
			org.junit.jupiter.api.Assertions.assertTrue(inline >= 1, "a cold lookup is inline");
			int doneBefore;
			synchronized (counts) { doneBefore = counts[WorldPlanner.Lookup.DONE.ordinal()]; }
			w.buildings(t[0], t[1]);
			synchronized (counts) { org.junit.jupiter.api.Assertions.assertEquals(doneBefore + 1, counts[WorldPlanner.Lookup.DONE.ordinal()], "a repeat is served from the finished result"); }
		} finally {
			workers.shutdownNow();
		}
	}

	// ---- mutants: the harness must notice these

	/** The biome answer depends on how many questions came before (order dependence). */
	static WorldProbes counting(WorldProbes p) {
		AtomicInteger calls = new AtomicInteger();
		BiomeProbe b = (x, z) -> calls.incrementAndGet() % 97 == 0 ? "minecraft:desert" : p.biome().biome(x, z);
		return new WorldProbes(p.region(), p.coarse(), p.exact(), b, p.window());
	}

	/** A probe that parks its question in a shared scratch field before answering (a non-thread-safe probe). */
	static WorldProbes sharedScratch(WorldProbes p) {
		TerrainProbe t = new TerrainProbe() {
			int scratchX;
			@Override public int surfaceY(int x, int z) { return p.exact().surfaceY(x, z); }
			@Override public int waterDepth(int x, int z) { return p.exact().waterDepth(x, z); }
			@Override public int floorY(int x, int z) {
				scratchX = x;
				Thread.yield();
				return p.exact().floorY(scratchX, z);
			}
		};
		return new WorldProbes(p.region(), p.coarse(), t, p.biome(), p.window());
	}

	private static boolean differs(WorldProbes probes, boolean threads) {
		try {
			WorldPlanner w = WorldPlanner.forSite(GATE.seed(), WorldConfig.defaults(), probes, null, WorldPlanner.Caches.DEFAULT, GATE.site());
			if (!threads) {
				List<int[]> rev = new ArrayList<>(TILE_LIST);
				Collections.reverse(rev);
				Map<String, String> got = run(w, rev);
				return !BASELINE.equals(got);
			}
			ExecutorService pool = Executors.newFixedThreadPool(8);
			try {
				List<Future<Map<String, String>>> fs = new ArrayList<>();
				for (int i = 0; i < 8; i++) {
					List<int[]> order = new ArrayList<>(TILE_LIST);
					Collections.shuffle(order, new Random(i));
					fs.add(pool.submit(() -> run(w, order)));
				}
				for (var f : fs) if (!BASELINE.equals(f.get(5, TimeUnit.MINUTES))) return true;
				return false;
			} finally {
				pool.shutdownNow();
			}
		} catch (Exception | Error e) {
			return true; // a corrupted memo that throws is detected too
		}
	}

	@Test
	void theHarnessCatchesAnOrderDependentProbe() {
		assertTrue(differs(counting(GATE.probes()), false));
	}

	@Test
	void theHarnessCatchesAProbeThatIsNotThreadSafe() {
		boolean caught = false;
		for (int attempt = 0; attempt < 3 && !caught; attempt++) caught = differs(sharedScratch(GATE.probes()), true);
		assertTrue(caught);
	}
}
