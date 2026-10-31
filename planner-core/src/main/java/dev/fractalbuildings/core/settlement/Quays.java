package dev.fractalbuildings.core.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Promenade lines along the water (TR, 5.3): the contour of a binary field ("water within {@value #OFFSET} blocks") by marching squares
 * on the 8-block street lattice. Every cell's segments depend only on its four corner values, so any tile that asks for a window
 * gets the same lines inside it and neighbouring tiles agree on their shared border without talking. Contour points are edge
 * midpoints, so every segment runs along an axis or at 45 degrees (a street polyline's rule). The caller clips the chains to its
 * tile and drops the columns that are wet, too high above the water, or outside the waterfront quarter.
 */
final class Quays {
	private Quays() {}

	/** Distance from the water of the contour, blocks (the contour lands within half a lattice step of it). */
	static final int OFFSET = 8;
	/** Promenade width and the shortest run worth a street, blocks. */
	static final int WIDTH = 4, MIN_LEN = 40;
	/** A quay runs only where the bank is at most this far above the water surface (a cliff gets none). */
	static final int MAX_RISE = 4;
	/** Lattice spacing of the field. */
	static final int STEP = StreetRules.NODE;

	interface Near {
		/** Water within {@link #OFFSET} blocks of the lattice node (x, z). */
		boolean at(int x, int z);
	}

	private static long key(Pt p) { return ((long) p.z() << 32) ^ (p.x() & 0xffffffffL); }

	/**
	 * Chains of contour points over the cells that touch the window {@code [minX, maxX] x [minZ, maxZ]} (lattice-aligned cells; a
	 * chain may leave the window). Closed rings repeat their first point at the end. Deterministic order: chains by the key of
	 * their first point.
	 */
	static List<List<Pt>> contour(Near near, int minX, int minZ, int maxX, int maxZ) {
		int i0 = Math.floorDiv(minX, STEP), i1 = Math.floorDiv(maxX, STEP), j0 = Math.floorDiv(minZ, STEP), j1 = Math.floorDiv(maxZ, STEP);
		List<int[]> cells = new ArrayList<>();
		for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) cells.add(new int[] {i, j});
		return contourCells(near, cells);
	}

	/** The segments of cell (i, j): zero, one or (saddle) two pairs of edge-midpoint points. */
	private static List<Pt[]> segments(Near near, int i, int j) {
		int x = i * STEP, z = j * STEP, h = STEP / 2;
		boolean a = near.at(x, z), b = near.at(x + STEP, z), c = near.at(x + STEP, z + STEP), d = near.at(x, z + STEP); // nw, ne, se, sw
		Pt n = new Pt(x + h, z), e = new Pt(x + STEP, z + h), s = new Pt(x + h, z + STEP), w = new Pt(x, z + h);
		int code = (a ? 1 : 0) | (b ? 2 : 0) | (c ? 4 : 0) | (d ? 8 : 0);
		List<Pt[]> out = new ArrayList<>(2);
		switch (code) {
			case 1, 14 -> out.add(new Pt[] {w, n});
			case 2, 13 -> out.add(new Pt[] {n, e});
			case 3, 12 -> out.add(new Pt[] {w, e});
			case 4, 11 -> out.add(new Pt[] {e, s});
			case 6, 9 -> out.add(new Pt[] {n, s});
			case 7, 8 -> out.add(new Pt[] {w, s});
			case 5 -> { out.add(new Pt[] {w, n}); out.add(new Pt[] {e, s}); } // saddle: the two near corners stay apart
			case 10 -> { out.add(new Pt[] {n, e}); out.add(new Pt[] {w, s}); }
			default -> { }
		}
		return out;
	}

	/** Cell keys {@code {i, j}} in scan order: j outer, i inner. */
	private static final java.util.Comparator<int[]> SCAN = (p, q) -> p[1] != q[1] ? Integer.compare(p[1], q[1]) : Integer.compare(p[0], q[0]);

	/** One point (the first end) per segment of the cells {@code [ci0, ci1] x [cj0, cj1]} (cell indices), in scan order. */
	static List<Pt> seeds(Near near, int ci0, int cj0, int ci1, int cj1) {
		List<Pt> out = new ArrayList<>();
		for (int j = cj0; j <= cj1; j++) for (int i = ci0; i <= ci1; i++) for (Pt[] sg : segments(near, i, j)) out.add(sg[0]);
		return out;
	}

	/**
	 * One seed per set of segments joined inside the cells {@code [ci0, ci1] x [cj0, cj1]} (QUAYLAZY step 4): the first of {@link #seeds} in
	 * each group, in scan order. Seeds of one group are on one chain; two groups may still be one chain that joins outside the box.
	 */
	static List<Pt> distinctSeeds(Near near, int ci0, int cj0, int ci1, int cj1) {
		java.util.TreeMap<Long, Long> parent = new java.util.TreeMap<>(); // union-find over point keys, lookup only
		List<Pt> firsts = new ArrayList<>();
		for (int j = cj0; j <= cj1; j++) {
			for (int i = ci0; i <= ci1; i++) {
				for (Pt[] sg : segments(near, i, j)) {
					firsts.add(sg[0]);
					parent.putIfAbsent(key(sg[0]), key(sg[0]));
					parent.putIfAbsent(key(sg[1]), key(sg[1]));
					long a = find(parent, key(sg[0])), b = find(parent, key(sg[1]));
					if (a != b) parent.put(Math.max(a, b), Math.min(a, b));
				}
			}
		}
		List<Pt> out = new ArrayList<>();
		java.util.TreeSet<Long> done = new java.util.TreeSet<>();
		for (Pt p : firsts) if (done.add(find(parent, key(p)))) out.add(p);
		return out;
	}

	private static long find(java.util.TreeMap<Long, Long> parent, long k) {
		long r = k;
		while (parent.get(r) != r) r = parent.get(r);
		return r;
	}

	/**
	 * The chain of {@link #contour} over the cell window {@code [i0, i1] x [j0, j1]} (cell indices) that holds {@code seed}, found
	 * by walking from the seed in both directions and reading only the cells on the way. It is the contour's own list (same first
	 * point, same direction): the visited cells go through the contour's code in scan order. A chain ends at the window edge.
	 */
	static List<Pt> chainThrough(Near near, int i0, int j0, int i1, int j1, Pt seed) {
		java.util.TreeMap<Long, List<Pt[]>> cellSegs = new java.util.TreeMap<>(); // lookup only
		java.util.TreeSet<Long> seen = new java.util.TreeSet<>(); // lookup only
		List<int[]> cells = new ArrayList<>();
		java.util.ArrayDeque<Pt> todo = new java.util.ArrayDeque<>();
		todo.add(seed);
		seen.add(key(seed));
		while (!todo.isEmpty()) {
			Pt p = todo.poll();
			// the (at most two) cells on either side of the edge that p is the midpoint of
			int px = p.x(), pz = p.z();
			int[][] around = px % STEP == 0 ? new int[][] {{px / STEP, Math.floorDiv(pz, STEP)}, {px / STEP - 1, Math.floorDiv(pz, STEP)}}
				: new int[][] {{Math.floorDiv(px, STEP), pz / STEP}, {Math.floorDiv(px, STEP), pz / STEP - 1}};
			for (int[] c : around) {
				if (c[0] < i0 || c[0] > i1 || c[1] < j0 || c[1] > j1) continue;
				long ck = ((long) c[1] << 32) ^ (c[0] & 0xffffffffL);
				List<Pt[]> segs = cellSegs.get(ck);
				if (segs == null) {
					segs = segments(near, c[0], c[1]);
					cellSegs.put(ck, segs);
					cells.add(c);
				}
				for (Pt[] sg : segs) {
					Pt other = sg[0].equals(p) ? sg[1] : sg[1].equals(p) ? sg[0] : null;
					if (other != null && seen.add(key(other))) todo.add(other);
				}
			}
		}
		cells.sort(SCAN);
		for (List<Pt> chain : contourCells(near, cells)) if (chain.contains(seed)) return chain;
		throw new IllegalStateException("seed " + seed + " is on no contour chain");
	}

	private static List<List<Pt>> contourCells(Near near, List<int[]> cells) {
		TreeMap<Long, List<Pt>> adj = new TreeMap<>();
		TreeMap<Long, Pt> pts = new TreeMap<>();
		for (int[] cell : cells) {
			for (Pt[] sg : segments(near, cell[0], cell[1])) link(adj, pts, sg[0], sg[1]);
		}
		List<List<Pt>> out = new ArrayList<>();
		TreeMap<Long, Boolean> used = new TreeMap<>();
		for (int pass = 0; pass < 2; pass++) { // open chains first (from their ends), then rings
			for (var en : adj.entrySet()) {
				if (used.containsKey(en.getKey())) continue;
				if (pass == 0 && en.getValue().size() != 1) continue;
				Pt start = pts.get(en.getKey());
				List<Pt> chain = new ArrayList<>();
				chain.add(start);
				used.put(en.getKey(), true);
				Pt prev = null, cur = start;
				while (true) {
					Pt next = null;
					boolean closed = false;
					for (Pt q : adj.get(key(cur))) {
						if (q.equals(prev)) continue;
						if (q.equals(start) && chain.size() > 2) { closed = true; break; }
						if (!used.containsKey(key(q))) { next = q; break; }
					}
					if (closed) { chain.add(start); break; }
					if (next == null) break;
					chain.add(next);
					used.put(key(next), true);
					prev = cur;
					cur = next;
				}
				if (chain.size() >= 2) out.add(chain);
			}
		}
		return out;
	}

	private static void link(TreeMap<Long, List<Pt>> adj, TreeMap<Long, Pt> pts, Pt p, Pt q) {
		for (Pt r : new Pt[] {p, q}) {
			pts.putIfAbsent(key(r), r);
			adj.computeIfAbsent(key(r), k -> new ArrayList<>());
		}
		adj.get(key(p)).add(q);
		adj.get(key(q)).add(p);
	}
}
