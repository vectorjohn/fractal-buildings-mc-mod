package dev.fractalbuildings.core.terrain;

import dev.fractalbuildings.core.probe.TerrainProbe;

/**
 * Water on a lattice of {@code stride} blocks. A local stand-in for P0's {@code CoarseProbe}
 * (which will implement or adapt to it): TA is coded in parallel with P0. Must be pure.
 */
public interface WaterSamples {
	int stride();

	/** True when the sample column is water (depth >= 2 or a water biome). */
	boolean water(int x, int z);

	/** Water where the probe's water depth is at least 2. */
	static WaterSamples of(TerrainProbe probe, int stride) {
		if (stride <= 0) throw new IllegalArgumentException("stride " + stride);
		return new WaterSamples() {
			@Override public int stride() { return stride; }
			@Override public boolean water(int x, int z) { return probe.waterDepth(x, z) >= 2; }
		};
	}
}
