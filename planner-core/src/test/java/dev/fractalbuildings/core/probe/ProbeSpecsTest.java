package dev.fractalbuildings.core.probe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProbeSpecsTest {
	@Test
	void forms() {
		assertEquals(64, ProbeSpecs.parse("flat:64").terrain().floorY(3, 3));
		assertEquals(65, ProbeSpecs.parse("plane:64:1:0:4").terrain().floorY(4, 0));
		assertEquals(84, ProbeSpecs.parse("hill:64:20:24").terrain().floorY(0, 0));
		var n = ProbeSpecs.parse("noise:5:64:6:32");
		assertEquals(SyntheticTerrain.noise(5, 64, 6, 32).floorY(9, 9), n.terrain().floorY(9, 9));
		assertEquals("minecraft:plains", n.biome().biome(0, 0));
		var r = ProbeSpecs.parse("recorded:../fixtures/terrain/s4-12345-crop32.json");
		assertEquals(69, r.terrain().surfaceY(-832, -3840));
		assertEquals("minecraft:sunflower_plains", r.biome().biome(-832, -3840));
	}

	@Test
	void badSpecs() {
		for (String s : new String[] {"", "bogus:1", "flat", "flat:x", "flat:1:2", "plane:1:2:3:0", "hill:1:2:0", "noise:1:2:3", "recorded:", "recorded:/nope/x.json"}) {
			var e = assertThrows(IllegalArgumentException.class, () -> ProbeSpecs.parse(s), s);
			assertTrue(e.getMessage().startsWith("bad probe spec"), e.getMessage());
		}
	}
}
