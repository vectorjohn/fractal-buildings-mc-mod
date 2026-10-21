package dev.fractalbuildings.core.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoarseProbeTest {
	@Test
	void latticeOnlyAndWaterRule() {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:64+river:96:40:5+biomes:split:300:plains:desert");
		CoarseProbe c = CoarseProbe.of(p.terrain(), p.biome(), 32);
		assertEquals(32, c.stride());
		assertEquals(59, c.floorY(96, -64));
		assertThrows(IllegalArgumentException.class, () -> c.floorY(97, 0));
		assertThrows(IllegalArgumentException.class, () -> c.biome(0, 5));
		assertThrows(IllegalArgumentException.class, () -> CoarseProbe.of(p.terrain(), p.biome(), 0));
		assertTrue(c.water(128, 0), "inside the river");
		assertFalse(c.water(32, 0));
		assertEquals("minecraft:desert", c.biome(320, 0));
	}

	@Test
	void waterNeedsDepthTwoOrAWaterBiome() {
		CoarseProbe shallow = CoarseProbe.of(SyntheticTerrain.water(SyntheticTerrain.flat(63), 64), SyntheticBiomes.constant("minecraft:plains"), 32);
		assertEquals(1, shallow.waterDepth(0, 0));
		assertFalse(shallow.water(0, 0));
		CoarseProbe deep = CoarseProbe.of(SyntheticTerrain.water(SyntheticTerrain.flat(62), 64), SyntheticBiomes.constant("minecraft:plains"), 32);
		assertTrue(deep.water(0, 0));
		assertTrue(CoarseProbe.of(SyntheticTerrain.flat(70), SyntheticBiomes.constant("minecraft:deep_ocean"), 32).water(0, 0));
	}

	@Test
	void biomeIds() {
		assertTrue(BiomeIds.isWater("minecraft:river") && BiomeIds.isWater("minecraft:deep_ocean") && BiomeIds.isWater("mushroom_fields"));
		assertFalse(BiomeIds.isWater("minecraft:plains"));
		assertTrue(BiomeIds.isPeak("minecraft:jagged_peaks") && !BiomeIds.isPeak("minecraft:meadow"));
		assertEquals("plains", BiomeIds.path("minecraft:plains"));
		assertEquals("plains", BiomeIds.path("plains"));
	}

	@Test
	void countingProbeCountsEachKindAndDoesNotChangeAnswers() {
		ProbeSpecs.Probes p = ProbeSpecs.parse("hill:60:20:50");
		CountingProbe cp = new CountingProbe(p.terrain(), p.biome());
		CoarseProbe coarse = cp.coarse(CoarseProbe.of(cp, cp, 16));
		assertEquals(p.terrain().floorY(3, 4), cp.floorY(3, 4));
		cp.waterDepth(1, 1);
		cp.surfaceY(1, 1);
		cp.biome(1, 1);
		assertEquals(3, cp.exactCalls());
		assertEquals(1, cp.biomeCalls());
		coarse.floorY(16, 16);
		coarse.water(0, 0);
		assertEquals(2, cp.coarseCalls());
		cp.reset();
		assertEquals(0, cp.exactCalls() + cp.biomeCalls() + cp.coarseCalls());
	}

	@Test
	void riverAndSplitSpecs() {
		ProbeSpecs.Probes p = ProbeSpecs.parse("plane:64:1:0:4+river:40:8:3");
		assertEquals(64 + 10 - 3, p.terrain().floorY(40, 0));
		assertEquals(3, p.terrain().waterDepth(40, 0));
		assertEquals(0, p.terrain().waterDepth(48, 0));
		assertEquals(ProbeSpecs.RIVER, p.biome().biome(47, 9));
		assertEquals(ProbeSpecs.PLAINS, p.biome().biome(48, 9));
		ProbeSpecs.Probes q = ProbeSpecs.parse("flat:64+biomes:split:10:desert:jungle");
		assertEquals("minecraft:desert", q.biome().biome(9, 0));
		assertEquals("minecraft:jungle", q.biome().biome(10, 0));
		for (String bad : new String[] {"flat:64+river:1:2", "flat:64+river:1:0:2", "flat:64+biomes:split:1:a", "flat:64+bogus:1", "flat:64+"}) {
			assertThrows(IllegalArgumentException.class, () -> ProbeSpecs.parse(bad), bad);
		}
	}
}
