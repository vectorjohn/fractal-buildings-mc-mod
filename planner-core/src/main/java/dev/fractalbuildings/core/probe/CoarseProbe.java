package dev.fractalbuildings.core.probe;

/**
 * Terrain and biome on a coarse lattice (every {@link #stride()} blocks, aligned to multiples of the
 * stride), what a cell skeleton may read (ADR 0003 D4, W2: the skeleton must stay correct when its heights
 * are off by up to 20). Asking for an off-lattice column throws {@link IllegalArgumentException}, so a
 * skeleton cannot quietly read exact heights. Pure, like {@link TerrainProbe}.
 */
public interface CoarseProbe {
	int stride();

	/** Floor height (heightmap convention) at a lattice column. */
	int floorY(int x, int z);

	/** Water depth above the floor at a lattice column; 0 on land. */
	int waterDepth(int x, int z);

	String biome(int x, int z);

	/** Water at a lattice column: depth of at least 2, or a water biome. */
	default boolean water(int x, int z) { return waterDepth(x, z) >= 2 || BiomeIds.isWater(biome(x, z)); }

	/** A coarse view of exact probes. */
	static CoarseProbe of(TerrainProbe terrain, BiomeProbe biome, int stride) {
		if (stride <= 0) throw new IllegalArgumentException("stride must be positive: " + stride);
		return new CoarseProbe() {
			private void lattice(int x, int z) {
				if (Math.floorMod(x, stride) != 0 || Math.floorMod(z, stride) != 0) {
					throw new IllegalArgumentException("column (" + x + ", " + z + ") is off the coarse lattice of stride " + stride);
				}
			}

			@Override public int stride() { return stride; }
			@Override public int floorY(int x, int z) { lattice(x, z); return terrain.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { lattice(x, z); return terrain.waterDepth(x, z); }
			@Override public String biome(int x, int z) { lattice(x, z); return biome.biome(x, z); }
		};
	}
}
