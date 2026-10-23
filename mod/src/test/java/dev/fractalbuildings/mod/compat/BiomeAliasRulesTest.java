package dev.fractalbuildings.mod.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.fractalbuildings.core.region.BiomeClass;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** B7: foreign biomes stand in for vanilla ones by convention tag; {@code #fractalbuildings:no_settlements} bans. A fake tag lookup, no Minecraft. */
class BiomeAliasRulesTest {
	private static String alias(String id, String... tags) {
		Set<String> t = Set.of(tags);
		return BiomeAliasRules.alias(id, t::contains);
	}

	@Test
	void vanillaBiomesAreThemselves() {
		assertEquals("minecraft:plains", alias("minecraft:plains"));
		assertEquals("minecraft:desert", alias("minecraft:desert", "c:is_desert"));
		assertEquals("minecraft:cherry_grove", alias("minecraft:cherry_grove", "c:is_snowy"), "a vanilla id is never remapped");
	}

	@Test
	void foreignBiomesMapByConventionTag() {
		assertEquals("minecraft:desert", alias("terralith:desert_canyon", "c:is_desert"));
		assertEquals("minecraft:jungle", alias("terralith:amethyst_rainforest", "c:is_jungle"));
		assertEquals("minecraft:savanna", alias("terralith:savanna_slopes", "c:is_savanna"));
		assertEquals("minecraft:badlands", alias("x:mesa", "c:is_badlands"));
		assertEquals("minecraft:snowy_plains", alias("x:tundra", "c:is_snowy"));
		assertEquals("minecraft:taiga", alias("x:pines", "c:is_taiga"));
		assertEquals("minecraft:ocean", alias("x:warm_sea", "c:is_ocean"));
		assertEquals("minecraft:river", alias("x:stream", "c:is_river"));
		assertEquals("minecraft:windswept_hills", alias("x:cliffs", "c:is_mountain"));
	}

	@Test
	void waterAndMountainsBeatClimate() {
		assertEquals("minecraft:river", alias("x:frozen_stream", "c:is_snowy", "c:is_river"));
		assertEquals("minecraft:windswept_hills", alias("x:snow_peaks", "c:is_snowy", "c:is_mountain"));
	}

	@Test
	void anUntaggedForeignBiomeIsPlains() {
		assertEquals("minecraft:plains", alias("x:whatever"));
	}

	@Test
	void noSettlementsBansAnyBiomeAndItsStandInHasMultiplierZero() {
		for (String id : new String[] {"minecraft:plains", "terralith:something", "minecraft:desert"}) {
			String a = alias(id, BiomeAliasRules.NO_SETTLEMENTS, "c:is_desert");
			assertEquals(BiomeAliasRules.BANNED_STAND_IN, a, id);
			assertEquals(0.0, BiomeClass.multiplier(a), "the stand-in is a class that never hosts a settlement");
		}
		// and the unbanned fallback does host them
		assertEquals(true, BiomeClass.multiplier(BiomeAliasRules.FALLBACK) > 0);
	}

}
