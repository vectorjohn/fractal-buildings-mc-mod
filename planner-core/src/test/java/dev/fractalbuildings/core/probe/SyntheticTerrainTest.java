package dev.fractalbuildings.core.probe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SyntheticTerrainTest {
	@Test
	void flatAndPlane() {
		assertEquals(64, SyntheticTerrain.flat(64).floorY(-5, 900));
		var pl = SyntheticTerrain.plane(64, 1, 0, 4);
		assertEquals(64, pl.floorY(0, 7));
		assertEquals(65, pl.floorY(4, 0));
		assertEquals(64, pl.floorY(3, 0));
		assertEquals(63, pl.floorY(-1, 0));
		assertEquals(60, pl.floorY(-16, 0));
		assertEquals(72, SyntheticTerrain.plane(64, 1, 2, 1).floorY(2, 3));
	}

	@Test
	void hillIsCone() {
		var h = SyntheticTerrain.hill(0, 0, 20, 24, 64);
		assertEquals(84, h.floorY(0, 0));
		assertEquals(74, h.floorY(12, 0));
		assertEquals(74, h.floorY(0, -12));
		assertEquals(64, h.floorY(24, 0));
		assertEquals(64, h.floorY(100, 100));
		assertEquals(h.floorY(5, 3), h.floorY(-5, -3));
	}

	@Test
	void terraces() {
		var t = SyntheticTerrain.terraces(60, 2, 8);
		assertEquals(60, t.floorY(0, 5));
		assertEquals(60, t.floorY(7, 5));
		assertEquals(62, t.floorY(8, 5));
		assertEquals(58, t.floorY(-1, 5));
	}

	@Test
	void noiseBoundedAndSeeded() {
		var a = SyntheticTerrain.noise(1, 64, 6, 32);
		var a2 = SyntheticTerrain.noise(1, 64, 6, 32);
		var b = SyntheticTerrain.noise(2, 64, 6, 32);
		boolean differs = false, varies = false;
		int first = a.floorY(0, 0);
		for (int x = -200; x < 200; x += 7) {
			for (int z = -200; z < 200; z += 5) {
				int y = a.floorY(x, z);
				assertTrue(y >= 58 && y <= 70, "y=" + y);
				assertEquals(y, a2.floorY(x, z));
				if (y != b.floorY(x, z)) differs = true;
				if (y != first) varies = true;
			}
		}
		assertTrue(differs);
		assertTrue(varies);
	}

	@Test
	void waterWrapper() {
		var w = SyntheticTerrain.water(SyntheticTerrain.plane(60, 1, 0, 1), 63);
		assertEquals(3, w.waterDepth(0, 0));
		assertEquals(63, w.surfaceY(0, 0));
		assertEquals(0, w.waterDepth(5, 0));
		assertEquals(65, w.surfaceY(5, 0));
		assertEquals(60, w.floorY(0, 0));
	}

	@Test
	void biomes() {
		assertEquals("minecraft:desert", SyntheticBiomes.constant("minecraft:desert").biome(3, 4));
		var b = SyntheticBiomes.halfPlaneX(10, "w", "e");
		assertEquals("w", b.biome(9, 0));
		assertEquals("e", b.biome(10, 0));
	}
}
