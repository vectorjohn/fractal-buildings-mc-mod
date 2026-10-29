package dev.fractalbuildings.mod;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.mod.compat.BiomeAliases;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;

/** The unit-test terrain harness binds biome tags like a server, so tag lookups work and BiomeAliases memoises. */
class BiomeTagHarnessTest {

	@Test
	void tagLookupDoesNotThrow() {
		TerrainDumpTool.boot();
		var gen = TerrainDumpTool.generator();
		Holder<Biome> h = gen.getBiomeSource().possibleBiomes().stream().filter(b -> b.is(Biomes.DESERT)).findFirst().orElseThrow();
		assertDoesNotThrow(() -> h.is(BiomeTags.IS_OVERWORLD));
		assertTrue(h.is(BiomeTags.IS_OVERWORLD));
		assertFalse(h.is(BiomeTags.IS_OCEAN));
		assertDoesNotThrow(() -> h.is(TagKey.create(Registries.BIOME, Identifier.parse("fractalbuildings:no_settlements"))));
	}

	@Test
	void aliasIsMemoised() {
		TerrainDumpTool.boot();
		Holder<Biome> h = TerrainDumpTool.generator().getBiomeSource().possibleBiomes().stream().filter(b -> b.is(Biomes.PLAINS)).findFirst().orElseThrow();
		BiomeAliases.clear();
		assertEquals("minecraft:plains", BiomeAliases.alias(h));
		// second read is served from the memo (no tag reads)
		assertEquals("minecraft:plains", BiomeAliases.alias(h));
	}
}
