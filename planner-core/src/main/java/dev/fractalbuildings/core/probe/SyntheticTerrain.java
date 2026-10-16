package dev.fractalbuildings.core.probe;

import dev.fractalbuildings.core.rand.Hash64;

/**
 * Synthetic terrain for tests and the CLI. Integer arithmetic only, so results are identical on
 * every JVM. All shapes are dry land; wrap with {@link #water} to add a sea.
 */
public final class SyntheticTerrain {
	private SyntheticTerrain() {}

	public static TerrainProbe flat(int y) { return (x, z) -> y; }

	/** y0 + floorDiv(x*riseX + z*riseZ, run). */
	public static TerrainProbe plane(int y0, int riseX, int riseZ, int run) {
		if (run <= 0) throw new IllegalArgumentException("run must be positive: " + run);
		return (x, z) -> y0 + (int) Math.floorDiv((long) x * riseX + (long) z * riseZ, (long) run);
	}

	/** Integer cone: baseY + peak at the centre, falling linearly to baseY at radius (floor), then flat. */
	public static TerrainProbe hill(int cx, int cz, int peak, int radius, int baseY) {
		if (radius <= 0) throw new IllegalArgumentException("radius must be positive: " + radius);
		long r2 = (long) radius * radius;
		return (x, z) -> {
			long dx = x - cx, dz = z - cz;
			long d2 = dx * dx + dz * dz;
			if (d2 >= r2) return baseY;
			long d = isqrt(d2);
			return baseY + (int) ((long) peak * (radius - d) / radius);
		};
	}

	/** Steps of height {@code step}, each {@code width} columns wide along x: y0 + step * floorDiv(x, width). */
	public static TerrainProbe terraces(int y0, int step, int width) {
		if (width <= 0) throw new IllegalArgumentException("width must be positive: " + width);
		return (x, z) -> y0 + step * Math.floorDiv(x, width);
	}

	/**
	 * Hashed value noise: lattice of spacing {@code wavelength}, a value in [-amplitude, amplitude]
	 * per lattice point (Hash64 of seed and lattice coords), integer bilinear interpolation.
	 * Result is within baseY +- amplitude.
	 */
	public static TerrainProbe noise(long seed, int baseY, int amplitude, int wavelength) {
		if (wavelength <= 0) throw new IllegalArgumentException("wavelength must be positive: " + wavelength);
		if (amplitude < 0) throw new IllegalArgumentException("amplitude must be >= 0: " + amplitude);
		final long w = wavelength;
		return (x, z) -> {
			int ix = Math.floorDiv(x, wavelength), iz = Math.floorDiv(z, wavelength);
			long fx = Math.floorMod(x, wavelength), fz = Math.floorMod(z, wavelength);
			long v00 = lattice(seed, ix, iz, amplitude), v10 = lattice(seed, ix + 1, iz, amplitude);
			long v01 = lattice(seed, ix, iz + 1, amplitude), v11 = lattice(seed, ix + 1, iz + 1, amplitude);
			long sum = v00 * (w - fx) * (w - fz) + v10 * fx * (w - fz) + v01 * (w - fx) * fz + v11 * fx * fz;
			return baseY + (int) Math.floorDiv(sum, w * w);
		};
	}

	private static long lattice(long seed, int ix, int iz, int amplitude) {
		return Hash64.range(Hash64.hash(seed, "synthetic-noise", ix, iz), -amplitude, amplitude);
	}

	/** Wraps a probe: columns whose floor is below seaLevel get water up to seaLevel. */
	public static TerrainProbe water(TerrainProbe base, int seaLevel) {
		return new TerrainProbe() {
			@Override public int floorY(int x, int z) { return base.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { return Math.max(0, seaLevel - base.floorY(x, z)); }
		};
	}

	static long isqrt(long n) {
		long r = (long) StrictMath.sqrt((double) n);
		while (r * r > n) r--;
		while ((r + 1) * (r + 1) <= n) r++;
		return r;
	}
}
