package dev.fractalbuildings.core.region;

import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;

/**
 * The coarse terrain view the region planner reads. It is queried only at columns that are multiples
 * of {@link #STRIDE}, so a recorded lattice at that stride replays every decision exactly.
 *
 * <p>Stand-in for the P0 {@code CoarseProbe} (same method names); swap it for that interface when P0
 * is merged (a rename and an import).
 */
public interface RegionProbe {
	int STRIDE = 64;

	int floorY(int x, int z);

	int waterDepth(int x, int z);

	/** Biome id at the surface, namespaced ({@code minecraft:plains}) or bare. */
	String biome(int x, int z);

	/** Water: depth of at least 2 or a water biome. */
	default boolean water(int x, int z) {
		return waterDepth(x, z) >= 2 || BiomeClass.isWater(biome(x, z));
	}

	static RegionProbe of(TerrainProbe t, BiomeProbe b) {
		return new RegionProbe() {
			@Override public int floorY(int x, int z) { return t.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { return t.waterDepth(x, z); }
			@Override public String biome(int x, int z) { return b.biome(x, z); }
		};
	}
}
