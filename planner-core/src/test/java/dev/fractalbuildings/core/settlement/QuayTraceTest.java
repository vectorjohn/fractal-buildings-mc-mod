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
			for (List<int[]> run : Districts.canonicalQuays(runs)) {
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
					out.add(new QuayWhole(cols, y, new boolean[y.length], QuayTrace.orderKey(run, k)));
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
				multi = Math.max(multi, t.maxClusterChains());
				if (!got.isEmpty()) nonEmpty++;
				parts += got.size();
			}
		}
		assertTrue(nonEmpty > 500, "the fixtures must put quays in tiles: " + nonEmpty);
		assertTrue(multi >= 2, "no cluster of several chains came up, so the closure is untested: " + multi);
		System.out.println("QuayTraceTest: " + nonEmpty + " non-empty tile answers, " + parts + " parts, largest cluster " + multi + " chains");
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
		}
	}

	@Test
	void noTwoPartsShareAnOrderKey() {
		for (long seed = 0; seed < 100; seed++) {
			TreeSet<String> seen = new TreeSet<>();
			for (QuayWhole q : reference(new Fake(seed, field(seed)))) assertTrue(seen.add(Arrays.toString(q.order())), "tie in the canonical order, seed " + seed);
		}
	}
}
