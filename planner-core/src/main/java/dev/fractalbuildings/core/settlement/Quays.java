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
		TreeMap<Long, List<Pt>> adj = new TreeMap<>();
		TreeMap<Long, Pt> pts = new TreeMap<>();
		for (int j = j0; j <= j1; j++) {
			for (int i = i0; i <= i1; i++) {
				int x = i * STEP, z = j * STEP, h = STEP / 2;
				boolean a = near.at(x, z), b = near.at(x + STEP, z), c = near.at(x + STEP, z + STEP), d = near.at(x, z + STEP); // nw, ne, se, sw
				Pt n = new Pt(x + h, z), e = new Pt(x + STEP, z + h), s = new Pt(x + h, z + STEP), w = new Pt(x, z + h);
				int code = (a ? 1 : 0) | (b ? 2 : 0) | (c ? 4 : 0) | (d ? 8 : 0);
				switch (code) {
					case 1, 14 -> link(adj, pts, w, n);
					case 2, 13 -> link(adj, pts, n, e);
					case 3, 12 -> link(adj, pts, w, e);
					case 4, 11 -> link(adj, pts, e, s);
					case 6, 9 -> link(adj, pts, n, s);
					case 7, 8 -> link(adj, pts, w, s);
					case 5 -> { link(adj, pts, w, n); link(adj, pts, e, s); } // saddle: the two near corners stay apart
					case 10 -> { link(adj, pts, n, e); link(adj, pts, w, s); }
					default -> { }
				}
			}
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
