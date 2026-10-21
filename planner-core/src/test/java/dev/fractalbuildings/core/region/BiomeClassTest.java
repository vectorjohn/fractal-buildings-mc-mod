package dev.fractalbuildings.core.region;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BiomeClassTest {
	@Test
	void waterPeaksAndLandAreClassified() {
		for (String w : new String[] {"minecraft:ocean", "minecraft:deep_cold_ocean", "minecraft:river", "minecraft:frozen_river", "mushroom_fields"}) {
			assertTrue(BiomeClass.isWater(w), w);
			assertEquals(0, BiomeClass.multiplier(w));
			assertEquals("water", BiomeClass.group(w));
		}
		assertTrue(BiomeClass.isPeak("minecraft:jagged_peaks"));
		assertEquals(0, BiomeClass.multiplier("minecraft:stony_peaks"));
		assertFalse(BiomeClass.isWater("minecraft:beach"));
	}

	@Test
	void tableValuesAndNamespacesAndDefault() {
		assertEquals(1.0, BiomeClass.multiplier("minecraft:plains"));
		assertEquals(1.0, BiomeClass.multiplier("plains"));
		assertEquals(1.0, BiomeClass.multiplier("othermod:plains"));
		assertEquals(0.7, BiomeClass.multiplier("minecraft:taiga"));
		assertEquals(0.25, BiomeClass.multiplier("minecraft:swamp"));
		assertEquals(0.4, BiomeClass.multiplier("minecraft:lush_caves"), "unlisted biomes use the default");
		assertEquals("desert", BiomeClass.group("minecraft:desert"));
		assertEquals("snow", BiomeClass.group("minecraft:snowy_taiga"));
		assertEquals("temperate", BiomeClass.group("minecraft:lush_caves"));
	}
}
