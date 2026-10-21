package dev.fractalbuildings.core.gen;

import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.Objects;

/**
 * One building to generate on one lot. {@code type} is a building type id (generator v1; v0 builds
 * houses whatever it says). {@code style} is a style id, or {@code "auto"} (v1: chosen from the biome
 * at the lot centre). {@code brief} is optional (null: none).
 */
public record GenRequest(long worldSeed, Lot lot, String type, String style, TerrainProbe terrain, BiomeProbe biome, Brief brief) {
	public static final String DEFAULT_TYPE = "house";

	public GenRequest {
		Objects.requireNonNull(lot);
		Objects.requireNonNull(type);
		Objects.requireNonNull(style);
		Objects.requireNonNull(terrain);
		Objects.requireNonNull(biome);
	}

	/** A request without a brief (everything before Phase 5). */
	public GenRequest(long worldSeed, Lot lot, String type, String style, TerrainProbe terrain, BiomeProbe biome) {
		this(worldSeed, lot, type, style, terrain, biome, null);
	}

	/** A house request (the Phase 3 signature). */
	public GenRequest(long worldSeed, Lot lot, String style, TerrainProbe terrain, BiomeProbe biome) {
		this(worldSeed, lot, DEFAULT_TYPE, style, terrain, biome);
	}
}
