package dev.fractalbuildings.core.region;

import dev.fractalbuildings.core.rand.Hash64;

/**
 * 2D hashed Voronoi: one site per grid cell, jittered, on integer block coordinates. A pure function of
 * (seed, x, z); no state, any chunk can ask at any time. All distance comparisons are exact integer
 * arithmetic; the only floating point is {@code StrictMath.sqrt} in {@link #edgeDistance}.
 */
public final class RegionField {
	public static final double DEFAULT_JITTER = 0.7;

	public final long seed;
	public final int cellSize;
	/** The site sits within +- jitter/2 of the cell centre, as a fraction of the cell size. */
	public final double jitter;

	public RegionField(long seed, int cellSize, double jitter) {
		if (cellSize < 64 || jitter < 0 || jitter >= 1) throw new IllegalArgumentException("cellSize " + cellSize + ", jitter " + jitter);
		this.seed = seed;
		this.cellSize = cellSize;
		this.jitter = jitter;
	}

	public RegionField(long seed, int cellSize) {
		this(seed, cellSize, DEFAULT_JITTER);
	}

	private int site(int c, long hash) {
		double u = Tiers.unit(hash);
		return Math.addExact(Math.multiplyExact(c, cellSize), cellSize / 2 + (int) Math.floor((u - 0.5) * jitter * cellSize));
	}

	public int siteX(int cx, int cz) {
		return site(cx, Hash64.hash(seed, "region/site-x", cx, cz));
	}

	public int siteZ(int cx, int cz) {
		return site(cz, Hash64.hash(seed, "region/site-z", cx, cz));
	}

	private static long d2(long dx, long dz) {
		return dx * dx + dz * dz;
	}

	/** The cell whose site is nearest (ties go to the lower cz, then the lower cx). */
	public record Hit(int cx, int cz, long d1sq, long d2sq) {}

	public Hit query(int x, int z) {
		return query(x, z, 1);
	}

	/** reach = how many grid cells around the containing one to search (1 = 3x3; tests compare with 3). */
	public Hit query(int x, int z, int reach) {
		int gx = Math.floorDiv(x, cellSize), gz = Math.floorDiv(z, cellSize);
		long b1 = Long.MAX_VALUE, b2 = Long.MAX_VALUE;
		int bx = 0, bz = 0;
		for (int dz = -reach; dz <= reach; dz++) {
			for (int dx = -reach; dx <= reach; dx++) {
				int cx = gx + dx, cz = gz + dz;
				long d = d2((long) siteX(cx, cz) - x, (long) siteZ(cx, cz) - z);
				if (d < b1) {
					b2 = b1;
					b1 = d;
					bx = cx;
					bz = cz;
				} else if (d < b2) {
					b2 = d;
				}
			}
		}
		return new Hit(bx, bz, b1, b2);
	}

	/** Exact distance from (x, z) to the border of its Voronoi cell (the minimum over the bisectors). */
	public double edgeDistance(int x, int z) {
		Hit h = query(x, z);
		long sx = siteX(h.cx, h.cz), sz = siteZ(h.cx, h.cz);
		long d1 = d2(x - sx, z - sz);
		double best = Double.MAX_VALUE;
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				if (dx == 0 && dz == 0) continue;
				int cx = h.cx + dx, cz = h.cz + dz;
				long ox = siteX(cx, cz), oz = siteZ(cx, cz);
				long len2 = d2(ox - sx, oz - sz);
				long diff = d2(x - ox, z - oz) - d1;
				best = Math.min(best, diff / (2 * StrictMath.sqrt((double) len2)));
			}
		}
		return best;
	}
}
