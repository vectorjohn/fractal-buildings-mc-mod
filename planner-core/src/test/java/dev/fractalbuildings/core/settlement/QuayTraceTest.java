package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * QUAYLAZY 3a: the lazy trace gives every tile what the one global computation gives it. No terrain: the field is a seeded hash, the
 * grade is a deliberately order-dependent fake (each part's levels mix in the levels of every earlier part with a column within
 * {@link QuayTrace#MEET_REACH} blocks, in order).
 */
class QuayTraceTest {
	static final int I0 = -15, J0 = -15, I1 = 14, J1 = 14, TILE_CELLS = 8, STEP_CELLS = 6, M = 12;

	static long mix(long v) {
		v ^= v >>> 33; v *= 0xff51afd7ed558ccdL; v ^= v >>> 33; v *= 0xc4ceb9fe1a85ec53L; v ^= v >>> 33;
		return v;
	}

	static long h(long seed, long a, long b) { return mix(seed * 1_000_003L + mix(a * 7919L + mix(b + 17))); }

	/** The field of a seed: smooth noise at two scales (long chains, saddles), or raw random nodes (checkerboard-heavy). */
	static Quays.Near field(long seed) {
		int kind = (int) (seed % 3);
		double thr = 0.35 + 0.3 * ((h(seed, 1, 1) >>> 11) / (double) (1L << 53));
		return (x, z) -> {
			int i = Math.floorDiv(x, Quays.STEP), j = Math.floorDiv(z, Quays.STEP);
			if (kind == 1) return ((h(seed, i, j) >>> 11) / (double) (1L << 53)) < 0.30 + 0.2 * ((h(seed, 2, 2) >>> 11) / (double) (1L << 53));
			int s = kind == 0 ? 4 : 3;
			int ci = Math.floorDiv(i, s), cj = Math.floorDiv(j, s);
			double fx = (i - ci * s) / (double) s, fz = (j - cj * s) / (double) s;
			double v00 = u(seed, ci, cj), v10 = u(seed, ci + 1, cj), v01 = u(seed, ci, cj + 1), v11 = u(seed, ci + 1, cj + 1);
			double v = (v00 * (1 - fx) + v10 * fx) * (1 - fz) + (v01 * (1 - fx) + v11 * fx) * fz;
			return 0.8 * v + 0.2 * u(seed + 99, i, j) < thr;
		};
	}

	static double u(long seed, long a, long b) { return (h(seed, a, b) >>> 11) / (double) (1L << 53); }

	/** The fake bank: pointwise ok (cuts chains into runs), order-dependent grade. */
	record Fake(long seed, Quays.Near near) implements QuayTrace.Bank {
		public boolean near(int x, int z) { return near.at(x, z); }

		public boolean ok(int[] c) { return h(seed, Math.floorDiv(c[0], 8), Math.floorDiv(c[1], 8)) % 13 != 0; }

		public void prefetch(List<int[]> boxes) {}

		public List<QuayWhole> grade(List<List<int[]>> runs) {
			List<QuayWhole> out = new ArrayList<>();
			List<Map<Long, Integer>> index = new ArrayList<>();
			List<List<int[]>> canonical = Districts.canonicalQuays(runs);
			for (int r = 0; r < canonical.size(); r++) {
				List<int[]> run = canonical.get(r);
				List<List<int[]>> parts = run.size() % 3 == 0 ? List.of(run.subList(0, run.size() / 2), run.subList(run.size() / 2, run.size())) : List.of(run);
				for (int k = 0; k < parts.size(); k++) {
					List<int[]> cols = new ArrayList<>(parts.get(k));
					int[] y = new int[cols.size()];
					Map<Long, Integer> mine = new HashMap<>();
					for (int i = 0; i < y.length; i++) {
						int[] c = cols.get(i);
						long acc = h(seed, c[0], c[1]);
						for (Map<Long, Integer> q : index) {
							for (int dz = -QuayTrace.MEET_REACH; dz <= QuayTrace.MEET_REACH; dz++) {
								for (int dx = -QuayTrace.MEET_REACH; dx <= QuayTrace.MEET_REACH; dx++) {
									Integer yq = q.get(QuayTrace.key(c[0] + dx, c[1] + dz));
									if (yq != null) acc = mix(acc * 31 + yq);
								}
							}
						}
						y[i] = (int) (acc & 0xffff);
						mine.put(QuayTrace.key(c[0], c[1]), y[i]);
					}
					index.add(mine);
					out.add(new QuayWhole(cols, y, new boolean[y.length], QuayTrace.orderKey(canonical, r, k)));
				}
			}
			return out;
		}
	}

	static QuayTrace.Area window() { return new QuayTrace.Area(new TreeSet<>(), I0, J0, I1, J1); }

	/** The obvious global computation: contour of the whole window, every run, every run graded together. */
	static List<QuayWhole> reference(Fake bank) {
		List<List<int[]>> runs = new ArrayList<>();
		for (List<Pt> chain : Quays.contour(bank.near(), I0 * 8, J0 * 8, I1 * 8, J1 * 8)) runs.addAll(QuayTrace.runsOf(chain, bank::ok));
		return bank.grade(runs); // already in canonical order: the reference does not sort
	}

	static List<QuayWhole> filter(List<QuayWhole> all, int[] box, int m) {
		List<QuayWhole> out = new ArrayList<>();
		for (QuayWhole q : all) {
			for (int[] c : q.cols()) {
				if (c[0] >= box[0] - m && c[0] <= box[2] + m && c[1] >= box[1] - m && c[1] <= box[3] + m) { out.add(q); break; }
			}
		}
		return out;
	}

	static List<int[]> tiles() {
		List<int[]> out = new ArrayList<>();
		for (int tj = J0; tj <= J1 - TILE_CELLS + 1; tj += STEP_CELLS) {
			for (int ti = I0; ti <= I1 - TILE_CELLS + 1; ti += STEP_CELLS) out.add(new int[] {ti * 8, tj * 8, (ti + TILE_CELLS) * 8 - 1, (tj + TILE_CELLS) * 8 - 1});
		}
		return out;
	}

	static String digest(List<QuayWhole> l) {
		StringBuilder sb = new StringBuilder();
		for (QuayWhole q : l) {
			sb.append(Arrays.toString(q.order())).append(Arrays.toString(q.y())).append(Arrays.toString(q.steep()));
			for (int[] c : q.cols()) sb.append(c[0]).append(',').append(c[1]).append(';');
			sb.append('\n');
		}
		return sb.toString();
	}

	static void assertSame(List<QuayWhole> want, List<QuayWhole> got, String what) {
		assertEquals(want.size(), got.size(), what + ": part count");
		assertEquals(digest(want), digest(got), what);
	}

	@Test
	void aTileGetsTheGlobalAnswer() {
		int multi = 0, nonEmpty = 0, parts = 0;
		for (long seed = 0; seed < 100; seed++) {
			Fake bank = new Fake(seed, field(seed));
			List<QuayWhole> ref = reference(bank);
			for (int[] box : tiles()) {
				QuayTrace t = new QuayTrace(window()); // a fresh trace per tile: the worst case for independence
				List<QuayWhole> got = t.near(bank, box[0], box[1], box[2], box[3], M);
				assertSame(filter(ref, box, M), got, "seed " + seed + " tile " + Arrays.toString(box));
				multi = Math.max(multi, t.maxClusterRuns());
				if (!got.isEmpty()) nonEmpty++;
				parts += got.size();
			}
		}
		assertTrue(nonEmpty > 500, "the fixtures must put quays in tiles: " + nonEmpty);
		assertTrue(multi >= 2, "no cluster of several runs came up, so the closure is untested: " + multi);
		System.out.println("QuayTraceTest: " + nonEmpty + " non-empty tile answers, " + parts + " parts, largest cluster " + multi + " runs");
	}


	/**
	 * QUAYFAST step 4: a bank whose ok varies inside a cell (4 x 4 column blocks, half of them not ok), with the tightest legal screen
	 * ({@code maybeOk == ok}) or none; counts the nodes read. A screen that tests less than a cell's every column drops a cell holding an ok
	 * column and the answer differs.
	 */
	record Blocky(Fake f, boolean screen, java.util.concurrent.atomic.AtomicInteger reads) implements QuayTrace.Bank {
		boolean pass(int x, int z) { return h(f.seed() + 7, Math.floorDiv(x, 4), Math.floorDiv(z, 4)) % 2 == 0; }

		public boolean near(int x, int z) { reads.incrementAndGet(); return f.near().at(x, z); }

		public boolean ok(int[] c) { return pass(c[0], c[1]); }

		public boolean maybeOk(int x, int z) { return !screen || pass(x, z); }

		public void prefetch(List<int[]> boxes) {}

		public List<QuayWhole> grade(List<List<int[]>> runs) { return f.grade(runs); }
	}

	@Test
	void aScreenedScanGivesTheGlobalAnswerAndReadsFewerNodes() {
		long readsOn = 0, readsOff = 0;
		for (long seed = 0; seed < 60; seed++) {
			Fake fake = new Fake(seed, field(seed));
			Blocky off = new Blocky(fake, false, new java.util.concurrent.atomic.AtomicInteger());
			List<List<int[]>> runs = new ArrayList<>();
			for (List<Pt> chain : Quays.contour(fake.near(), I0 * 8, J0 * 8, I1 * 8, J1 * 8)) runs.addAll(QuayTrace.runsOf(chain, off::ok));
			List<QuayWhole> ref = fake.grade(runs);
			Blocky on = new Blocky(fake, true, new java.util.concurrent.atomic.AtomicInteger());
			for (int[] box : tiles()) {
				on.reads().set(0); off.reads().set(0);
				List<QuayWhole> a = new QuayTrace(window()).near(on, box[0], box[1], box[2], box[3], M);
				List<QuayWhole> b = new QuayTrace(window()).near(off, box[0], box[1], box[2], box[3], M);
				assertSame(filter(ref, box, M), a, "screened, seed " + seed + " tile " + Arrays.toString(box));
				assertSame(filter(ref, box, M), b, "unscreened, seed " + seed + " tile " + Arrays.toString(box));
				readsOn += on.reads().get(); readsOff += off.reads().get();
			}
		}
		assertTrue(readsOn < readsOff, "the screen must skip cells: " + readsOn + " vs " + readsOff);
		System.out.println("QuayTraceTest: screened scan reads " + readsOn + " nodes, unscreened " + readsOff);
	}
	@Test
	void theVisitOrderDoesNotMatter() {
		for (long seed = 0; seed < 30; seed++) {
			Fake bank = new Fake(seed, field(seed));
			List<QuayWhole> ref = reference(bank);
			List<int[]> tiles = tiles();
			for (int shuffle = 0; shuffle < 3; shuffle++) {
				List<int[]> order = new ArrayList<>(tiles);
				Collections.shuffle(order, new Random(seed * 31 + shuffle));
				QuayTrace t = new QuayTrace(window());
				for (int[] box : order) assertSame(filter(ref, box, M), t.near(bank, box[0], box[1], box[2], box[3], M), "seed " + seed + " shuffle " + shuffle);
			}
		}
	}

	@Test
	void allEqualsTheReference() {
		for (long seed = 0; seed < 100; seed++) {
			Fake bank = new Fake(seed, field(seed));
			assertSame(reference(bank), new QuayTrace(window()).all(bank), "seed " + seed);
		}
	}

	@Test
	void allAfterTilesAndTilesAfterAllAgree() {
		for (long seed = 0; seed < 20; seed++) {
			Fake bank = new Fake(seed, field(seed));
			List<QuayWhole> ref = reference(bank);
			QuayTrace t = new QuayTrace(window());
			for (int[] box : tiles()) t.near(bank, box[0], box[1], box[2], box[3], M);
			assertSame(ref, t.all(bank), "all after tiles, seed " + seed);
			QuayTrace u = new QuayTrace(window());
			u.all(bank);
			for (int[] box : tiles()) assertSame(filter(ref, box, M), u.near(bank, box[0], box[1], box[2], box[3], M), "tile after all, seed " + seed);
			assertSame(ref, u.all(bank), "all again, seed " + seed); // QUAYFAST: a second whole trace on a traced memo
		}
	}

	/** QUAYFAST: every column a quay, so every closed ring is one run that starts where the ring does (the walk's whole-ring fallback). */
	record AllQuay(Fake fake) implements QuayTrace.Bank {
		public boolean near(int x, int z) { return fake.near(x, z); }

		public boolean ok(int[] c) { return true; }

		public void prefetch(List<int[]> boxes) {}

		public List<QuayWhole> grade(List<List<int[]>> runs) { return fake.grade(runs); }
	}

	@Test
	void anAllQuayRingGivesTheGlobalAnswer() {
		int rings = 0;
		for (long seed = 0; seed < 40; seed++) {
			Fake fake = new Fake(seed, field(seed));
			AllQuay bank = new AllQuay(fake);
			List<List<int[]>> runs = new ArrayList<>();
			for (List<Pt> chain : Quays.contour(fake.near(), I0 * 8, J0 * 8, I1 * 8, J1 * 8)) {
				List<List<int[]>> rs = QuayTrace.runsOf(chain, bank::ok);
				if (!rs.isEmpty() && chain.get(0).equals(chain.get(chain.size() - 1))) rings++;
				runs.addAll(rs);
			}
			List<QuayWhole> ref = fake.grade(runs);
			for (int[] box : tiles()) assertSame(filter(ref, box, M), new QuayTrace(window()).near(bank, box[0], box[1], box[2], box[3], M), "seed " + seed + " tile " + Arrays.toString(box));
			assertSame(ref, new QuayTrace(window()).all(bank), "seed " + seed + " whole");
		}
		assertTrue(rings > 20, "the fields must hold all-quay rings of a whole run's length: " + rings);
	}

	/** QUAYFAST: clusters are of runs, not chains, so two runs of one chain far apart are graded apart; some field must show it, or the refinement is untested. */
	@Test
	void runClustersAreFinerThanChainClusters() {
		int split = 0;
		for (long seed = 0; seed < 100; seed++) {
			QuayTrace t = new QuayTrace(window());
			t.all(new Fake(seed, field(seed)));
			if (t.splitsAChain()) split++;
		}
		assertTrue(split > 10, "fields where one chain's runs fall in different clusters: " + split);
	}

	@Test
	void noTwoPartsShareAnOrderKey() {
		for (long seed = 0; seed < 100; seed++) {
			TreeSet<String> seen = new TreeSet<>();
			for (QuayWhole q : reference(new Fake(seed, field(seed)))) assertTrue(seen.add(Arrays.toString(q.order())), "tie in the canonical order, seed " + seed);
		}
	}

	/** QUAYLAZY 3b: one trace serves several worldgen threads at once; every thread's every tile still gets the global answer. */
	@Test
	void severalThreadsShareOneTrace() throws Exception {
		int threads = 8;
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		try {
			for (long seed = 0; seed < 40; seed++) {
				Fake bank = new Fake(seed, field(seed));
				List<QuayWhole> ref = reference(bank);
				List<int[]> tiles = tiles();
				QuayTrace t = new QuayTrace(window());
				java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
				List<java.util.concurrent.Future<String>> results = new ArrayList<>();
				for (int k = 0; k < threads; k++) {
					long shuffle = seed * 131 + k;
					results.add(pool.submit(() -> {
						List<int[]> order = new ArrayList<>(tiles);
						Collections.shuffle(order, new Random(shuffle));
						go.await();
						for (int[] box : order) {
							String want = digest(filter(ref, box, M)), got = digest(t.near(bank, box[0], box[1], box[2], box[3], M));
							if (!want.equals(got)) return "tile " + Arrays.toString(box) + " differs";
						}
						return "";
					}));
				}
				go.countDown();
				for (var f : results) assertEquals("", f.get(), "seed " + seed);
				assertSame(ref, t.all(bank), "all after the threads, seed " + seed);
			}
		} finally {
			pool.shutdownNow();
		}
	}

	/** Two runs through the same smallest column, from the same first column, of one size: one order whatever the input order, and distinct keys. */
	@Test
	void tiedRunsGetOneOrderAndDistinctKeys() {
		List<int[]> a = new ArrayList<>(), b = new ArrayList<>();
		for (int i = 0; i < 6; i++) { a.add(new int[] {i, 0}); b.add(new int[] {i < 3 ? i : 2, i < 3 ? 0 : i - 2}); }
		List<List<int[]>> ab = Districts.canonicalQuays(List.of(a, b)), ba = Districts.canonicalQuays(List.of(b, a));
		assertEquals(QuayTrace.smallest(ab.get(0)), QuayTrace.smallest(ab.get(1)), "the fixture ties on the smallest column");
		assertEquals(ab.get(0).size(), ab.get(1).size(), "and on size");
		StringBuilder x = new StringBuilder(), y = new StringBuilder();
		for (int r = 0; r < 2; r++) {
			for (int[] c : ab.get(r)) x.append(c[0]).append(',').append(c[1]).append(' ');
			for (int[] c : ba.get(r)) y.append(c[0]).append(',').append(c[1]).append(' ');
			x.append('|');
			y.append('|');
		}
		assertEquals(x.toString(), y.toString(), "the input order does not matter");
		assertFalse(Arrays.equals(QuayTrace.orderKey(ab, 0, 0), QuayTrace.orderKey(ab, 1, 0)), "tied runs get distinct order keys");
		assertTrue(Arrays.compare(QuayTrace.orderKey(ab, 0, 1), QuayTrace.orderKey(ab, 1, 0)) < 0, "every part of the first comes before the second");
	}

	/** A grade that asks the trace for its own cluster again fails at once instead of waiting on itself. */
	@Test
	void aGradeThatCallsBackIntoTheTraceThrows() {
		long seed = 3;
		Fake fake = new Fake(seed, field(seed));
		List<QuayWhole> ref = reference(fake);
		int[] box = null;
		for (int[] b : tiles()) if (!filter(ref, b, M).isEmpty()) { box = b; break; }
		assertNotNull(box, "a tile with quays");
		int[] tile = box;
		QuayTrace t = new QuayTrace(window());
		QuayTrace.Bank reentrant = new QuayTrace.Bank() {
			public boolean near(int x, int z) { return fake.near(x, z); }

			public boolean ok(int[] c) { return fake.ok(c); }

			public void prefetch(List<int[]> boxes) {}

			public List<QuayWhole> grade(List<List<int[]>> runs) {
				t.near(this, tile[0], tile[1], tile[2], tile[3], M);
				return fake.grade(runs);
			}
		};
		assertTimeoutPreemptively(java.time.Duration.ofSeconds(20), () -> {
			IllegalStateException e = assertThrows(IllegalStateException.class, () -> t.near(reentrant, tile[0], tile[1], tile[2], tile[3], M));
			assertTrue(e.getMessage().contains("must not call back"), e.getMessage());
		});
	}

	/**
	 * A bank that fans out on a pool whose helpers start after random delays (so pieces finish in any order), forks a bank per worker thread
	 * and fails if one bank is ever used by two threads (a Districts bank's memos are one thread's).
	 */
	static final class Parallel implements QuayTrace.Bank {
		final Fake fake;
		final dev.fractalbuildings.core.cache.Fanout fanout;
		final java.util.concurrent.atomic.AtomicReference<Thread> user = new java.util.concurrent.atomic.AtomicReference<>();
		final java.util.concurrent.atomic.AtomicInteger forks;
		final java.util.concurrent.atomic.AtomicReference<String> failure;

		Parallel(Fake fake, dev.fractalbuildings.core.cache.Fanout fanout, java.util.concurrent.atomic.AtomicInteger forks, java.util.concurrent.atomic.AtomicReference<String> failure) {
			this.fake = fake;
			this.fanout = fanout;
			this.forks = forks;
			this.failure = failure;
		}

		private void use() {
			Thread me = Thread.currentThread();
			Thread was = user.compareAndSet(null, me) ? me : user.get();
			if (was != me) failure.compareAndSet(null, "a bank used by " + was.getName() + " and " + me.getName());
		}

		public boolean near(int x, int z) { use(); return fake.near(x, z); }

		public boolean ok(int[] c) { use(); return fake.ok(c); }

		public void prefetch(List<int[]> boxes) { use(); }

		public List<QuayWhole> grade(List<List<int[]>> runs) { use(); return fake.grade(runs); }

		@Override public QuayTrace.Bank fork() {
			forks.incrementAndGet();
			return new Parallel(fake, fanout, forks, failure);
		}

		@Override public dev.fractalbuildings.core.cache.Fanout fanout() { return fanout; }
	}

	/**
	 * QUAYLAZY step 4: the walks and cluster gradings of one request run on several threads, each on its own bank, started after random delays
	 * so which piece ends first changes from run to run; every tile, and the whole trace, still give the global answer byte for byte.
	 */
	@Test
	void parallelWalksAndGradesGiveTheGlobalAnswerInAnyCompletionOrder() throws Exception {
		java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(6, 6, 1, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
		Random delays = new Random(7);
		java.util.concurrent.Executor slow = r -> pool.execute(() -> {
			try { Thread.sleep(delays.nextInt(4)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
			r.run();
		});
		java.util.concurrent.atomic.AtomicInteger forks = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicReference<String> failure = new java.util.concurrent.atomic.AtomicReference<>();
		try {
			int checked = 0;
			for (long seed = 0; seed < 30; seed++) {
				Fake fake = new Fake(seed, field(seed));
				List<QuayWhole> ref = reference(fake);
				QuayTrace t = new QuayTrace(window());
				List<int[]> order = new ArrayList<>(tiles());
				Collections.shuffle(order, new Random(seed));
				for (int[] box : order) {
					Parallel bank = new Parallel(fake, dev.fractalbuildings.core.cache.Fanout.over(() -> slow, 5), forks, failure);
					assertSame(filter(ref, box, M), t.near(bank, box[0], box[1], box[2], box[3], M), "seed " + seed + " tile " + Arrays.toString(box));
					checked++;
				}
				assertNull(failure.get());
				assertSame(ref, new QuayTrace(window()).all(new Parallel(fake, dev.fractalbuildings.core.cache.Fanout.over(() -> slow, 5), forks, failure)), "seed " + seed + " whole");
			}
			assertNull(failure.get(), failure.get());
			assertTrue(forks.get() > 0, "some piece ran on a helper's own bank (else the test covers nothing parallel)");
			System.out.println("QuayTraceTest parallel: " + checked + " tile requests, " + forks.get() + " forked banks");
		} finally {
			pool.shutdownNow();
		}
	}
}
