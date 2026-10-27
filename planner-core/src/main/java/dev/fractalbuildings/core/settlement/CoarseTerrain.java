package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.probe.BiomeIds;
import dev.fractalbuildings.core.probe.CoarseProbe;

/**
 * The skeleton's pointwise terrain tests over a {@link CoarseProbe}: every query is snapped to the lattice
 * first, so the answer depends only on the nearest lattice column and no exact height is ever read.
 */
public final class CoarseTerrain {
	/** Floors at or above this are peaks (kept out of the urban mask). */
	public static final int PEAK_Y = 140;
	/** Largest height difference to a 4-neighbour over 32 blocks that still counts as buildable (3 in 4: houses and shops terrace and step up a slope; it was 1 in 2, which left real hills patchy, TR 5.3). */
	public static final int STEEP_PER_32 = 24;
	/** The road router charges extra for columns steeper than this (24 per 32, 3 in 4: the comment said 1 in 2, the value was raised with {@link #STEEP_PER_32}). */
	public static final int ROAD_STEEP_PER_32 = 24;

	public final CoarseProbe probe;
	public final int step;

	public CoarseTerrain(CoarseProbe probe) {
		this.probe = probe;
		this.step = probe.stride();
	}

	/** The lattice coordinate nearest to v. */
	public int snap(int v) { return Math.floorDiv(v + step / 2, step) * step; }

	public int floorY(int x, int z) { return probe.floorY(snap(x), snap(z)); }

	public int surfaceY(int x, int z) { int sx = snap(x), sz = snap(z); return probe.floorY(sx, sz) + probe.waterDepth(sx, sz); }

	public boolean water(int x, int z) { return probe.water(snap(x), snap(z)); }

	/** The column and its eight lattice neighbours are all dry. */
	public boolean dryAround(int x, int z) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) if (water(x + dx * step, z + dz * step)) return false;
		}
		return true;
	}

	public boolean peak(int x, int z) {
		int sx = snap(x), sz = snap(z);
		return probe.floorY(sx, sz) >= PEAK_Y || BiomeIds.isPeak(probe.biome(sx, sz));
	}

	/** Some 4-neighbour's surface differs from this column's by more than 3 in 4 (over one lattice step). */
	public boolean steep(int x, int z) { return steep(x, z, STEEP_PER_32); }

	/** Steeper than {@link #ROAD_STEEP_PER_32} (3 in 4, equal to the buildable limit today, so it adds nothing over {@link #steep}). */
	public boolean roadSteep(int x, int z) { return steep(x, z, ROAD_STEEP_PER_32); }

	private boolean steep(int x, int z, int per32) {
		int sx = snap(x), sz = snap(z), h = surfaceY(sx, sz);
		int limit = per32 * step / 32;
		return Math.abs(surfaceY(sx + step, sz) - h) > limit || Math.abs(surfaceY(sx - step, sz) - h) > limit
				|| Math.abs(surfaceY(sx, sz + step) - h) > limit || Math.abs(surfaceY(sx, sz - step) - h) > limit;
	}

	/** Dry, not a peak, not steep: a column the urban mask may take (inside the disk is checked elsewhere). */
	public boolean buildable(int x, int z) { return !water(x, z) && !peak(x, z) && !steep(x, z); }

	/** Any lattice column within {@code dist} blocks of (x, z) is water. */
	public boolean waterWithin(int x, int z, int dist) {
		int x0 = snap(x - dist), x1 = snap(x + dist), z0 = snap(z - dist), z1 = snap(z + dist);
		long d2 = (long) dist * dist;
		for (int nz = z0; nz <= z1; nz += step) {
			for (int nx = x0; nx <= x1; nx += step) {
				long dx = nx - x, dz = nz - z;
				if (dx * dx + dz * dz <= d2 && probe.water(nx, nz)) return true;
			}
		}
		return false;
	}
}
