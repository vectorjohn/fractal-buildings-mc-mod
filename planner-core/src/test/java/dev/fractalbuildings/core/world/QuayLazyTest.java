package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.settlement.Districts;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * QUAYLAZY 3b: the lazy quay trace gives every tile exactly the quays the reference whole trace gives it (the verbatim pre-QUAYLAZY
 * {@code settlementQuaysReference}), over the waterfront settlements of the committed recorded worlds ({@link QuaySites}): with a fresh source
 * per tile (nothing shared with any other tile's planning), in any visit order, on several threads at once, and after a whole warm. The
 * tile plans are byte-identical. A read-count guard fails if the lazy path ever scans the whole waterfront again.
 */
class QuayLazyTest {
	/**
	 * A tile's quays are the runs with a footprint cell within this many blocks of it ({@code Districts.Build.QUAY_REACH}: MIN_LEN 40 +
	 * MARGIN 8 + 2 * STEP 8). Pinned here: the comparison below filters the reference's whole list itself, so a lazy reach that is short
	 * (or long) by even a block fails it, given runs in the band just inside the reach (asserted).
	 */
	static final int REACH = 64;
	static final int WIDTH = 4, H = WIDTH / 2, STEP = 8;

	/** Per site: the reference source's whole list and per-tile lists, and each tile's lazy list from a fresh source (computed once per JVM). */
	record Data(QuaySites site, QuaySites.Open open, List<DistrictGrid.TileId> tiles, List<String> refAll, TreeMap<String, List<String>> refTile, TreeMap<String, List<String>> freshTile) {}

	private static final TreeMap<String, Data> DATA = new TreeMap<>();

	static String id(DistrictGrid.TileId t) { return t.ix() + "," + t.iz(); }

	static synchronized Data data(QuaySites s) {
		Data d = DATA.get(s.label());
		if (d != null) return d;
		QuaySites.Open o = s.open();
		List<DistrictGrid.TileId> tiles = o.waterfront();
		assertFalse(tiles.isEmpty(), s.label() + " has waterfront tiles");
		SkeletonSource.Direct ref = o.reference();
		List<String> all = Districts.allQuayRuns(ref, o.exact());
		TreeMap<String, List<String>> refTile = new TreeMap<>(), fresh = new TreeMap<>();
		for (DistrictGrid.TileId t : tiles) {
			refTile.put(id(t), Districts.quayRuns(ref, t.ix(), t.iz(), o.exact()));
			fresh.put(id(t), Districts.quayRuns(o.fresh(), t.ix(), t.iz(), o.exact())); // a fresh lazy source per tile: the worst case for independence
		}
		d = new Data(s, o, tiles, all, refTile, fresh);
		DATA.put(s.label(), d);
		return d;
	}

	static List<QuaySites> sites(boolean heavy) {
		List<QuaySites> out = new ArrayList<>();
		for (QuaySites s : QuaySites.all()) if (s.heavy() == heavy) out.add(s);
		return out;
	}

	/** The columns {x, z} of a run line ({@code x,z:y x,z:y ...}). */
	static List<int[]> cols(String run) {
		List<int[]> out = new ArrayList<>();
		for (String c : run.split(" ")) {
			String[] xz = c.substring(0, c.indexOf(':')).split(",");
			out.add(new int[] {Integer.parseInt(xz[0]), Integer.parseInt(xz[1])});
		}
		return out;
	}

	/** The Chebyshev distance from the tile to the nearest footprint cell of the run (0 when one is inside). */
	static int reach(String run, DistrictGrid.Tile t) {
		int best = Integer.MAX_VALUE;
		for (int[] c : cols(run)) {
			int fx0 = c[0] - H, fx1 = c[0] - H + WIDTH - 1, fz0 = c[1] - H, fz1 = c[1] - H + WIDTH - 1;
			int dx = Math.max(0, Math.max(t.minX() - fx1, fx0 - t.maxX())), dz = Math.max(0, Math.max(t.minZ() - fz1, fz0 - t.maxZ()));
			best = Math.min(best, Math.max(dx, dz));
		}
		return best;
	}

	static List<String> within(List<String> all, DistrictGrid.Tile t) {
		List<String> out = new ArrayList<>();
		for (String r : all) if (reach(r, t) <= REACH) out.add(r);
		return out;
	}

	// ---- lazy against reference, a fresh source per tile

	static void everyTileSeesTheReferenceQuays(boolean heavy) {
		int quayTiles = 0, band = 0, parts = 0;
		for (QuaySites s : sites(heavy)) {
			Data d = data(s);
			assertFalse(d.refAll().isEmpty(), s.label() + " has quays, or the fixture covers nothing");
			// the order keys are total and sorted, and no tie came up (so the column-sequence tiebreak changes no fixture's output)
			List<long[]> keys = Districts.allQuayOrder(d.open().fresh(), d.open().exact());
			assertEquals(d.refAll().size(), keys.size(), s.label() + ": part count");
			for (int i = 0; i < keys.size(); i++) {
				assertEquals(0, keys.get(i)[3], s.label() + ": a tie in the canonical run order at part " + i + " " + Arrays.toString(keys.get(i)));
				if (i > 0) assertTrue(Arrays.compare(keys.get(i - 1), keys.get(i)) < 0, s.label() + ": order keys not strictly increasing at " + i);
			}
			for (DistrictGrid.TileId t : d.tiles()) {
				List<String> lazy = d.freshTile().get(id(t));
				DistrictGrid.Tile tile = d.open().source().grid().tile(t.ix(), t.iz());
				assertEquals(d.refTile().get(id(t)), lazy, s.label() + " tile " + id(t) + ": lazy (fresh source) against the reference source");
				assertEquals(within(d.refAll(), tile), lazy, s.label() + " tile " + id(t) + ": lazy against the reference's whole list within " + REACH);
				if (!lazy.isEmpty()) quayTiles++;
				parts += lazy.size();
				for (String r : lazy) if (reach(r, tile) > REACH - 2 * STEP) band++;
			}
			System.out.println("QuayLazyTest " + s.label() + ": " + d.tiles().size() + " waterfront tiles, " + d.refAll().size() + " quay parts");
		}
		assertTrue(quayTiles > 0, "some tile sees a quay");
		assertTrue(band > 0, "no run lies in the last " + 2 * STEP + " blocks of the reach, so the reach is not pinned");
		System.out.println("QuayLazyTest: " + quayTiles + " tiles with quays, " + parts + " tile parts, " + band + " in the reach band");
	}

	@Test
	void everyWaterfrontTileSeesTheReferenceQuays() { everyTileSeesTheReferenceQuays(false); }

	@Tag("heavy")
	@Test
	void everyMetropolisWaterfrontTileSeesTheReferenceQuays() { everyTileSeesTheReferenceQuays(true); }

	// ---- visit order, threads, warming

	@Test
	void theVisitOrderDoesNotMatter() {
		for (QuaySites s : sites(false)) {
			Data d = data(s);
			for (int shuffle = 0; shuffle < 3; shuffle++) {
				List<DistrictGrid.TileId> order = new ArrayList<>(d.tiles());
				Collections.shuffle(order, new Random(31L * shuffle + s.label().hashCode()));
				SkeletonSource.Direct shared = d.open().fresh();
				for (DistrictGrid.TileId t : order) {
					assertEquals(d.refTile().get(id(t)), Districts.quayRuns(shared, t.ix(), t.iz(), d.open().exact()), s.label() + " shuffle " + shuffle + " tile " + id(t));
				}
			}
		}
	}

	/** Several threads plan tiles on one shared lazy source at once (worldgen threads in the mod): every answer is the reference's. */
	@Test
	void severalThreadsShareOneLazySource() throws Exception {
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			for (QuaySites s : sites(false)) {
				Data d = data(s);
				for (int round = 0; round < 3; round++) {
					SkeletonSource.Direct shared = d.open().fresh();
					CountDownLatch go = new CountDownLatch(1);
					List<Future<String>> results = new ArrayList<>();
					for (int k = 0; k < threads; k++) {
						long shuffle = 977L * round + k;
						results.add(pool.submit(() -> {
							List<DistrictGrid.TileId> order = new ArrayList<>(d.tiles());
							Collections.shuffle(order, new Random(shuffle));
							go.await();
							for (DistrictGrid.TileId t : order) {
								if (!d.refTile().get(id(t)).equals(Districts.quayRuns(shared, t.ix(), t.iz(), d.open().exact()))) return "tile " + id(t) + " differs";
							}
							return "";
						}));
					}
					go.countDown();
					for (Future<String> f : results) assertEquals("", f.get(), s.label() + " round " + round);
				}
			}
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void warmingFirstChangesNothing() {
		for (QuaySites s : sites(false)) {
			Data d = data(s);
			SkeletonSource.Direct warmed = d.open().fresh();
			Districts.warmQuays(warmed, d.open().exact());
			for (DistrictGrid.TileId t : d.tiles()) assertEquals(d.refTile().get(id(t)), Districts.quayRuns(warmed, t.ix(), t.iz(), d.open().exact()), s.label() + " tile " + id(t) + " after warmQuays");
			assertEquals(d.refAll(), Districts.allQuayRuns(warmed, d.open().exact()), s.label() + ": the whole lazy list after the tiles");
			SkeletonSource.Direct tilesFirst = d.open().fresh();
			for (DistrictGrid.TileId t : d.tiles()) Districts.quayRuns(tilesFirst, t.ix(), t.iz(), d.open().exact());
			assertEquals(d.refAll(), Districts.allQuayRuns(tilesFirst, d.open().exact()), s.label() + ": the whole lazy list after every tile");
		}
	}

	// ---- whole tile plans

	static void planEqualsReference(boolean heavy) {
		for (QuaySites s : sites(heavy)) {
			Data d = data(s);
			SkeletonSource.Direct ref = d.open().reference();
			List<DistrictGrid.TileId> order = new ArrayList<>(d.tiles());
			Collections.shuffle(order, new Random(s.label().hashCode()));
			SkeletonSource.Direct shared = d.open().fresh(); // heavy: one lazy source in a shuffled order (the per-tile fresh case is covered above)
			for (DistrictGrid.TileId t : order) {
				String want = DistrictJson.write(Districts.plan(ref, t.ix(), t.iz(), d.open().exact()).orElseThrow().plan());
				SkeletonSource.Direct lazy = heavy ? shared : d.open().fresh();
				assertEquals(want, DistrictJson.write(Districts.plan(lazy, t.ix(), t.iz(), d.open().exact()).orElseThrow().plan()), s.label() + " tile " + id(t) + ": the tile plan, lazy against reference");
			}
		}
	}

	@Test
	void theLazyPlanEqualsTheReferencePlan() { planEqualsReference(false); }

	@Tag("heavy")
	@Test
	void theLazyMetropolisPlanEqualsTheReferencePlan() { planEqualsReference(true); }

	// ---- tile-border-agree where a quay crosses between two waterfront tiles

	static void adjacentTilesAgree(boolean heavy, String needLabel, int needPairs) {
		for (QuaySites s : sites(heavy)) {
			Data d = data(s);
			TreeSet<String> have = new TreeSet<>();
			for (DistrictGrid.TileId t : d.tiles()) have.add(id(t));
			int crossed = 0;
			for (DistrictGrid.TileId a : d.tiles()) {
				for (int[] dir : new int[][] {{1, 0}, {0, 1}}) {
					DistrictGrid.TileId b = new DistrictGrid.TileId(a.ix() + dir[0], a.iz() + dir[1]);
					if (!have.contains(id(b))) continue;
					DistrictGrid.Tile ta = d.open().source().grid().tile(a.ix(), a.iz()), tb = d.open().source().grid().tile(b.ix(), b.iz());
					int m = Districts.MARGIN;
					// the border between them, MARGIN either side: a run with a column there is within reach of both tiles
					int[] box = dir[0] == 1 ? new int[] {tb.minX() - m, Math.max(ta.minZ(), tb.minZ()) - m, tb.minX() + m, Math.min(ta.maxZ(), tb.maxZ()) + m}
						: new int[] {Math.max(ta.minX(), tb.minX()) - m, tb.minZ() - m, Math.min(ta.maxX(), tb.maxX()) + m, tb.minZ() + m};
					TreeSet<String> na = across(d.freshTile().get(id(a)), box), nb = across(d.freshTile().get(id(b)), box);
					assertEquals(String.join("\n", na), String.join("\n", nb), s.label() + ": quays across the border of " + id(a) + " and " + id(b));
					if (!na.isEmpty()) crossed++;
				}
			}
			System.out.println("QuayLazyTest " + s.label() + ": " + crossed + " adjacent waterfront tile pairs with a quay across their border");
			if (s.label().equals(needLabel)) assertTrue(crossed >= needPairs, s.label() + ": at least " + needPairs + " tile pairs a quay crosses, found " + crossed);
		}
	}

	static TreeSet<String> across(List<String> runs, int[] box) {
		TreeSet<String> out = new TreeSet<>();
		for (String r : runs) {
			for (int[] c : cols(r)) if (c[0] >= box[0] && c[0] <= box[2] && c[1] >= box[1] && c[1] <= box[3]) { out.add(r); break; }
		}
		return out;
	}

	@Test
	void adjacentTilesAgreeOnTheQuaysAcrossTheirBorder() { adjacentTilesAgree(false, "", 0); }

	@Tag("heavy")
	@Test
	void adjacentMetropolisTilesAgreeOnTheQuaysAcrossTheirBorder() { adjacentTilesAgree(true, "pt1573-s4_m2", 3); }

	// ---- the cost guard: no area scan

	/** An exact probe that records every 16 x 16 chunk read. */
	static final class Counting implements TerrainProbe {
		final TerrainProbe in;
		final Set<Long> chunks = ConcurrentHashMap.newKeySet();

		Counting(TerrainProbe in) { this.in = in; }

		private void at(int x, int z) { chunks.add(((long) (z >> 4) << 32) ^ ((x >> 4) & 0xffffffffL)); }

		@Override public int floorY(int x, int z) { at(x, z); return in.floorY(x, z); }

		@Override public int waterDepth(int x, int z) { at(x, z); return in.waterDepth(x, z); }

		@Override public int surfaceY(int x, int z) { at(x, z); return in.surfaceY(x, z); }
	}

	/** The chunks one tile's quays read on a fresh source (the junction field, built first, is not counted). */
	static int chunks(SkeletonSource.Direct src, QuaySites.Open o, DistrictGrid.TileId t) {
		Counting c = new Counting(o.exact());
		src.junctions(c);
		c.chunks.clear();
		Districts.quayRuns(src, t.ix(), t.iz(), c);
		return c.chunks.size();
	}

	/**
	 * The deterministic cost guard (plan: "fails if anyone brings back an area scan"): on metropolis s4_m2, the median sampled waterfront
	 * tile's lazy quays, each from a fresh source, read at most a quarter of the chunks the reference whole trace reads (the plan's probe
	 * saw about 8 %). Recorded terrain: chunk counts, not times.
	 */
	@Test
	void aTileReadsAFractionOfTheWholeTrace() {
		QuaySites.Open o = QuaySites.of("pt1573-s4_m2").open();
		List<DistrictGrid.TileId> tiles = o.waterfront();
		int whole = chunks(o.reference(), o, tiles.get(tiles.size() / 2));
		List<Integer> lazy = new ArrayList<>();
		int n = 9;
		for (int k = 0; k < n; k++) lazy.add(chunks(o.fresh(), o, tiles.get((int) ((long) k * (tiles.size() - 1) / (n - 1)))));
		Collections.sort(lazy);
		int median = lazy.get(n / 2);
		System.out.println("QuayLazyTest cost: whole trace " + whole + " chunks; lazy tiles " + lazy + " (median " + median + ", " + (100 * median / whole) + " %)");
		assertTrue(whole > 5000, "the reference reads the whole waterfront: " + whole);
		assertTrue(median * 4 <= whole, "a tile's lazy quays read " + median + " chunks, more than a quarter of the whole trace's " + whole);
	}

	/** QUAYLAZY step 5: warmQuaysTile (what the mod's warm and the perf row call) is the tile's own lazy request: the quays that tile then lists equal the reference's. */
	@Test
	void warmQuaysTileChangesNothing() {
		QuaySites.Open o = QuaySites.of("pt1573-s4_m2").open();
		List<DistrictGrid.TileId> tiles = o.waterfront();
		for (int k : new int[] {0, tiles.size() / 2, tiles.size() - 1}) {
			DistrictGrid.TileId t = tiles.get(k);
			SkeletonSource.Direct src = o.fresh();
			Counting c = new Counting(o.exact());
			src.junctions(c);
			c.chunks.clear();
			Districts.warmQuaysTile(src, t.ix(), t.iz(), c);
			assertTrue(c.chunks.size() > 0, "the warm of a waterfront tile reads terrain");
			c.chunks.clear();
			List<String> lazy = Districts.quayRuns(src, t.ix(), t.iz(), c);
			assertEquals(Districts.quayRuns(o.reference(), t.ix(), t.iz(), o.exact()), lazy, "tile " + id(t));
		}
	}

	// ---- QUAYLAZY step 4: parallel walks and cluster grading on the reads pool

	/** A pool whose tasks start after a random delay, so helpers finish pieces in any order. */
	static final class SlowPool extends java.util.concurrent.ThreadPoolExecutor {
		final Random delays;

		SlowPool(int threads, long seed) {
			super(threads, threads, 1, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
			delays = new Random(seed);
		}

		@Override public void execute(Runnable r) {
			int ms;
			synchronized (delays) { ms = delays.nextInt(5); }
			super.execute(() -> {
				try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
				r.run();
			});
		}
	}

	/** Every tile's quays, then the whole list, on a source whose reads fan out on {@code pool}, against the reference. */
	static void sameOnPool(QuaySites s, ExecutorService pool, int helpers, List<DistrictGrid.TileId> order, String what) {
		Data d = data(s);
		ParallelReadsPlannerTest.PoolReads reads = new ParallelReadsPlannerTest.PoolReads(pool, helpers, d.open().exact());
		SkeletonSource.Direct src = d.open().fresh().withReads(reads);
		for (DistrictGrid.TileId t : order) assertEquals(d.refTile().get(id(t)), Districts.quayRuns(src, t.ix(), t.iz(), d.open().exact()), s.label() + " " + what + " tile " + id(t));
		assertEquals(d.refAll(), Districts.allQuayRuns(src, d.open().exact()), s.label() + " " + what + ": the whole list");
		assertTrue(reads.boxes.get() > 0, "the trace prefetched through the pool");
	}

	/** The lazy path on a pool equals the serial run, called from the test thread, from inside a pool thread, with the pool free and saturated (as PARREADS did). */
	@Test
	void aPoolGivesTheSameQuays() throws Exception {
		for (QuaySites s : sites(false)) {
			Data d = data(s);
			List<DistrictGrid.TileId> order = new ArrayList<>(d.tiles());
			Collections.shuffle(order, new Random(s.label().hashCode()));
			ExecutorService pool = Executors.newFixedThreadPool(6);
			try {
				sameOnPool(s, pool, 6, order, "free pool");
			} finally {
				pool.shutdownNow();
			}
			ExecutorService one = Executors.newFixedThreadPool(1);
			try { // from the pool's only thread: the helpers it asks for queue behind it, the caller does the pieces
				assertTimeoutPreemptively(java.time.Duration.ofMinutes(5), () -> one.submit(() -> sameOnPool(s, one, 3, order, "inside the pool")).get());
			} finally {
				one.shutdownNow();
			}
			ExecutorService busy = Executors.newFixedThreadPool(2);
			CountDownLatch release = new CountDownLatch(1);
			try { // every pool thread is held by someone else: helpers never start, the caller does all
				for (int k = 0; k < 2; k++) busy.submit(() -> { release.await(); return null; });
				assertTimeoutPreemptively(java.time.Duration.ofMinutes(5), () -> sameOnPool(s, busy, 4, order, "saturated pool"));
			} finally {
				release.countDown();
				busy.shutdownNow();
			}
		}
	}

	/** Pieces that finish in random order, on several threads planning tiles of one shared lazy source at once: still the reference, in each of five rounds. */
	@Test
	void concurrentTilesShareOneTraceOnAPoolWithRandomCompletionOrder() throws Exception {
		int threads = 8, rounds = 5;
		ExecutorService callers = Executors.newFixedThreadPool(threads);
		try {
			for (QuaySites s : sites(false)) {
				Data d = data(s);
				for (int round = 0; round < rounds; round++) {
					SlowPool pool = new SlowPool(6, 41L * round + s.label().hashCode());
					try {
						SkeletonSource.Direct shared = d.open().fresh().withReads(new ParallelReadsPlannerTest.PoolReads(pool, 5, d.open().exact()));
						CountDownLatch go = new CountDownLatch(1);
						List<Future<String>> results = new ArrayList<>();
						for (int k = 0; k < threads; k++) {
							long shuffle = 613L * round + k;
							results.add(callers.submit(() -> {
								List<DistrictGrid.TileId> order = new ArrayList<>(d.tiles());
								Collections.shuffle(order, new Random(shuffle));
								go.await();
								for (DistrictGrid.TileId t : order) {
									if (!d.refTile().get(id(t)).equals(Districts.quayRuns(shared, t.ix(), t.iz(), d.open().exact()))) return "tile " + id(t) + " differs";
								}
								return "";
							}));
						}
						go.countDown();
						for (Future<String> f : results) assertEquals("", f.get(), s.label() + " round " + round);
						assertEquals(d.refAll(), Districts.allQuayRuns(shared, d.open().exact()), s.label() + " round " + round + ": the whole list");
					} finally {
						pool.shutdownNow();
					}
				}
			}
		} finally {
			callers.shutdownNow();
		}
	}
}
