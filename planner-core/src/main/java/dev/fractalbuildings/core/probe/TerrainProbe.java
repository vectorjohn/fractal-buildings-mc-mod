package dev.fractalbuildings.core.probe;

/**
 * Terrain heights at world columns. Implementations: synthetic (tests), recorded dump (CLI,
 * viewer), live Minecraft (mod). Must be pure: the same column always gives the same answer, in
 * any order and on any thread.
 *
 * <p>Heights use Minecraft's heightmap convention: the y of the first free block above the column
 * (one above the top block). {@link #floorY} is above solid ground (like OCEAN_FLOOR_WG, water
 * ignored); {@link #surfaceY} is above ground or water (like WORLD_SURFACE_WG).
 */
@FunctionalInterface
public interface TerrainProbe {
	int floorY(int x, int z);

	/** Water depth above the floor; 0 on dry land. */
	default int waterDepth(int x, int z) { return 0; }

	default int surfaceY(int x, int z) { return floorY(x, z) + waterDepth(x, z); }
}
