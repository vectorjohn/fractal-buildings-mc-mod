package dev.fractalbuildings.core.probe;

/**
 * Biome at a world column (at the surface), as a namespaced id such as {@code "minecraft:plains"}.
 * Pure, like {@link TerrainProbe}.
 */
@FunctionalInterface
public interface BiomeProbe {
	String biome(int x, int z);
}
