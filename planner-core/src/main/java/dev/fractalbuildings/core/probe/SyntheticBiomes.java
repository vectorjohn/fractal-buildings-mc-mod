package dev.fractalbuildings.core.probe;

/** Synthetic biome probes for tests and the CLI. */
public final class SyntheticBiomes {
	private SyntheticBiomes() {}

	public static BiomeProbe constant(String id) { return (x, z) -> id; }

	/** {@code west} for x below the line, {@code east} for x at or beyond it. */
	public static BiomeProbe halfPlaneX(int x, String west, String east) {
		return (px, pz) -> px < x ? west : east;
	}
}
