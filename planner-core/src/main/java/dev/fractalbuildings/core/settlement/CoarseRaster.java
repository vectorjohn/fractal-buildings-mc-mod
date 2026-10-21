package dev.fractalbuildings.core.settlement;

import java.util.List;

/**
 * The skeleton's coarse screen over the plan area: a lattice of {@code step}-spaced columns from
 * ({@code x0}, {@code z0}), row-major with z outer. {@code mask}: 0 wild, 1 urban, 2 water, 3 steep,
 * 4 open (park, green). {@code density} 0..100. {@code quarter} indexes the plan's quarters (-1 none).
 * {@code biome} indexes {@code biomes}.
 */
public record CoarseRaster(int x0, int z0, int step, int nx, int nz, int[] floorY, int[] water, int[] biome, List<String> biomes,
		int[] mask, int[] density, int[] quarter) {
	public static final int WILD = 0, URBAN = 1, WATER = 2, STEEP = 3, OPEN = 4;

	public CoarseRaster {
		if (step <= 0 || nx <= 0 || nz <= 0) throw new IllegalArgumentException("coarse raster " + nx + "x" + nz + " step " + step);
		int n = nx * nz;
		for (int[] a : new int[][] {floorY, water, biome, mask, density, quarter}) {
			if (a.length != n) throw new IllegalArgumentException("coarse array length " + a.length + " != nx*nz " + n);
		}
		floorY = floorY.clone();
		water = water.clone();
		biome = biome.clone();
		mask = mask.clone();
		density = density.clone();
		quarter = quarter.clone();
		biomes = List.copyOf(biomes);
		for (int b : biome) if (b < 0 || b >= biomes.size()) throw new IllegalArgumentException("coarse biome index " + b);
		for (int m : mask) if (m < WILD || m > OPEN) throw new IllegalArgumentException("coarse mask value " + m);
		for (int v : density) if (v < 0 || v > 100) throw new IllegalArgumentException("coarse density " + v);
		for (int v : water) if (v < 0) throw new IllegalArgumentException("coarse water " + v);
	}

	public int index(int i, int j) { return j * nx + i; }

	// arrays are exposed as copies so the record stays immutable
	@Override public int[] floorY() { return floorY.clone(); }
	@Override public int[] water() { return water.clone(); }
	@Override public int[] biome() { return biome.clone(); }
	@Override public int[] mask() { return mask.clone(); }
	@Override public int[] density() { return density.clone(); }
	@Override public int[] quarter() { return quarter.clone(); }

	@Override public boolean equals(Object o) {
		return o instanceof CoarseRaster c && c.x0 == x0 && c.z0 == z0 && c.step == step && c.nx == nx && c.nz == nz
				&& java.util.Arrays.equals(c.floorY, floorY) && java.util.Arrays.equals(c.water, water) && java.util.Arrays.equals(c.biome, biome)
				&& c.biomes.equals(biomes) && java.util.Arrays.equals(c.mask, mask) && java.util.Arrays.equals(c.density, density)
				&& java.util.Arrays.equals(c.quarter, quarter);
	}

	@Override public int hashCode() { return java.util.Objects.hash(x0, z0, step, nx, nz, java.util.Arrays.hashCode(floorY), biomes); }
}
