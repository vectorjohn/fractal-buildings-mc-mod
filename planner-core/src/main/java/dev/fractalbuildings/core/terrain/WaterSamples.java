package dev.fractalbuildings.core.terrain;

import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;

/**
 * Water on a lattice of {@code stride} blocks, what {@link WaterField} reads. {@link #of(CoarseProbe)} adapts the
 * skeleton's coarse probe (depth of at least 2, or a water biome); the {@link TerrainProbe} form serves exact
 * terrain in tests. Must be pure.
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

	/** The coarse probe's own water rule, on its lattice. */
	static WaterSamples of(CoarseProbe probe) {
		return new WaterSamples() {
			@Override public int stride() { return probe.stride(); }
			@Override public boolean water(int x, int z) { return probe.water(x, z); }
		};
	}
}
