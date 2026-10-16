package dev.fractalbuildings.core.gen;

import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.Objects;

public record GenRequest(long worldSeed, Lot lot, String style, TerrainProbe terrain, BiomeProbe biome) {
	public GenRequest {
		Objects.requireNonNull(lot);
		Objects.requireNonNull(style);
		Objects.requireNonNull(terrain);
		Objects.requireNonNull(biome);
	}
}
