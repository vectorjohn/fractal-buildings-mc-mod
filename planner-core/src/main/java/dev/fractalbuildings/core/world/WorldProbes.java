package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionProbe;
import java.util.Objects;

/**
 * The terrain a {@link WorldPlanner} reads. Every probe must be a pure function of the column and safe to call from any thread at
 * once (the live ones in the mod are; recorded and synthetic ones are). {@code region} is read on the 64-block lattice only,
 * {@code coarse} on its own stride, {@code exact} and {@code biome} anywhere.
 *
 * @param roadExact the exact terrain the road graph reads at its stations (the same values as {@code exact}; a live world gives single-column reads, since stations are sparse); null means {@code exact}
 * @param window the area a recorded dump covers, or null for an unbounded world; cells whose probe reach leaves it are not decided
 */
public record WorldProbes(RegionProbe region, CoarseProbe coarse, TerrainProbe exact, BiomeProbe biome, RegionMap.Window window, TerrainProbe roadExact) {
	public WorldProbes {
		Objects.requireNonNull(region);
		Objects.requireNonNull(coarse);
		Objects.requireNonNull(exact);
		Objects.requireNonNull(biome);
		if (roadExact == null) roadExact = exact;
	}

	public WorldProbes(RegionProbe region, CoarseProbe coarse, TerrainProbe exact, BiomeProbe biome, RegionMap.Window window) {
		this(region, coarse, exact, biome, window, null);
	}

	/** An unbounded world. */
	public WorldProbes(RegionProbe region, CoarseProbe coarse, TerrainProbe exact, BiomeProbe biome) {
		this(region, coarse, exact, biome, null);
	}

	/** All four views from one exact terrain and biome probe, the coarse one on a 32-block lattice (as the CLI does). */
	public static WorldProbes of(TerrainProbe terrain, BiomeProbe biome) {
		return new WorldProbes(RegionProbe.of(terrain, biome), CoarseProbe.of(terrain, biome, 32), terrain, biome, null);
	}
}
