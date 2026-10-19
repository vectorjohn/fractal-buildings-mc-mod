package dev.fractalbuildings.core.gen.v1.roof;

import java.util.function.IntUnaryOperator;

/**
 * Roof heightfields (pure integer maths): {@code h[x][z]} is how many blocks the roof surface of a
 * column stands above the eave layer (0 at the eaves). A pitch is {@code rise} blocks up per
 * {@code run} blocks in; {@code distance} is measured in blocks from the nearest open cell (an edge
 * column has distance 1), so the profile of the straight-skeleton-like hip roof is the chessboard
 * distance (docs/research/pcg/straight-skeleton-roofs.md).
 */
public final class RoofField {
	private RoofField() {}

	/** No open cell anywhere. */
	public static final int INF = 1 << 20;

	public record Pitch(int rise, int run) {
		public Pitch {
			if (rise < 0 || run <= 0) throw new IllegalArgumentException("pitch " + rise + "/" + run);
		}

		/** Height after {@code d} blocks in from the eave. */
		public int h(int d) { return d * rise / run; }

		/** Largest height difference between neighbouring columns of a profile. */
		public int maxStep() { return (rise + run - 1) / run; }
	}

	/** Mansard profile: two steep steps (2 per block), then a flat top one higher. */
	public static int mansard(int d) { return Math.min(d, 2) * 2 + (d > 2 ? 1 : 0); }

	/** Gable over an sx x sz rectangle: only the two edges parallel to the ridge are eaves. */
	public static int[][] gable(int sx, int sz, boolean ridgeX, Pitch p) {
		int[][] h = new int[sx][sz];
		int across = ridgeX ? sz : sx;
		for (int x = 0; x < sx; x++)
			for (int z = 0; z < sz; z++) {
				int i = ridgeX ? z : x;
				h[x][z] = p.h(Math.min(i, across - 1 - i));
			}
		return h;
	}

	/** Hip over an sx x sz rectangle: every edge is an eave (a pyramid when square). */
	public static int[][] hip(int sx, int sz, Pitch p) {
		int[][] h = new int[sx][sz];
		for (int x = 0; x < sx; x++)
			for (int z = 0; z < sz; z++) h[x][z] = p.h(Math.min(Math.min(x, sx - 1 - x), Math.min(z, sz - 1 - z)));
		return h;
	}

	/**
	 * Chessboard distance of every region cell to the nearest open cell; {@code open} may be larger than
	 * the region and is indexed like it (cells outside the array are open). Non-region cells get 0.
	 */
	public static int[][] distance(boolean[][] region, boolean[][] open) {
		int w = region.length, d = region[0].length;
		int[][] dist = new int[w][d];
		for (int x = 0; x < w; x++)
			for (int z = 0; z < d; z++) {
				if (!region[x][z]) continue;
				int best = Math.min(Math.min(x + 1, w - x), Math.min(z + 1, d - z)); // the array border is open
				for (int ox = 0; ox < w; ox++)
					for (int oz = 0; oz < d; oz++) {
						if (!open[ox][oz]) continue;
						int e = Math.max(Math.abs(ox - x), Math.abs(oz - z));
						if (e < best) best = e;
					}
				dist[x][z] = best;
			}
		return dist;
	}

	/** Heights {@code profile(distance - 1)} over the region, -1 elsewhere. */
	public static int[][] fromDistance(int[][] dist, boolean[][] region, IntUnaryOperator profile) {
		int[][] h = new int[dist.length][dist[0].length];
		for (int x = 0; x < h.length; x++)
			for (int z = 0; z < h[0].length; z++) h[x][z] = region[x][z] ? profile.applyAsInt(dist[x][z] - 1) : -1;
		return h;
	}
}
