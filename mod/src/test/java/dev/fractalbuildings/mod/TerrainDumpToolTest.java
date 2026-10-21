package dev.fractalbuildings.mod;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class TerrainDumpToolTest {
	static final Path CROP = Path.of("../fixtures/terrain/s4-12345-crop32.json");

	/** {@code -Dfb.dump=seed:x0:z0:step:nx:nz:name[,...]} writes fixtures/terrain/name.json.gz. */
	@Test
	@EnabledIfSystemProperty(named = "fb.dump", matches = ".+")
	void dump() throws Exception {
		Path out = Path.of(System.getProperty("fb.dump.out", "../fixtures/terrain"));
		int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
		for (String one : System.getProperty("fb.dump").split(",")) {
			String[] p = one.trim().split(":");
			assertEquals(7, p.length, "spec is seed:x0:z0:step:nx:nz:name, got " + one);
			long t0 = System.nanoTime();
			var g = TerrainDumpTool.sample(Long.parseLong(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
				Integer.parseInt(p[4]), Integer.parseInt(p[5]), threads);
			Path f = out.resolve(p[6] + ".json.gz");
			TerrainDumpTool.writeGz(f, TerrainDumpTool.toJson(g));
			System.out.printf("DUMP %s: %dx%d step %d, %d palette, %.1f s on %d threads, %d bytes%n", f, g.nx(), g.nz(), g.step(),
				g.palette().size(), (System.nanoTime() - t0) / 1e9, threads, Files.size(f));
		}
	}

	@Test
	void sameBytesOnOneAndFourThreads() throws Exception {
		var a = TerrainDumpTool.sample(12345, -1408, -4160, 64, 16, 16, 1);
		var b = TerrainDumpTool.sample(12345, -1408, -4160, 64, 16, 16, 4);
		assertEquals(TerrainDumpTool.toJson(a), TerrainDumpTool.toJson(b));
		Path p1 = Files.createTempFile("fbdump", ".json.gz"), p2 = Files.createTempFile("fbdump", ".json.gz");
		TerrainDumpTool.writeGz(p1, TerrainDumpTool.toJson(a));
		TerrainDumpTool.writeGz(p2, TerrainDumpTool.toJson(b));
		assertArrayEquals(Files.readAllBytes(p1), Files.readAllBytes(p2));
	}

	/** The S4 crop (seed 12345, 32x32 at step 64) is reproduced: surface, water and biome names. */
	@Test
	void reproducesS4Crop() throws Exception {
		var g = TerrainDumpTool.sample(12345, -1408, -4160, 64, 32, 32, 4);
		var crop = dev.fractalbuildings.core.probe.RecordedProbe.parse(Files.readString(CROP));
		int diffSurf = 0, diffWater = 0, diffBiome = 0;
		for (int j = 0; j < 32; j++) {
			for (int i = 0; i < 32; i++) {
				int x = g.x0() + i * 64, z = g.z0() + j * 64;
				int k = j * 32 + i;
				if (crop.surfaceY(x, z) != g.surface()[k]) diffSurf++;
				if (crop.waterDepth(x, z) != g.water()[k]) diffWater++;
				if (!crop.biome(x, z).equals("minecraft:" + g.palette().get(g.biome()[k]))) diffBiome++;
			}
		}
		assertEquals(0, diffSurf, "surface differs");
		assertEquals(0, diffWater, "water differs");
		assertEquals(0, diffBiome, "biome differs");
		int k = 0 * 32 + (576 + 1408) / 64; // (576, -4160): river, surface 63, water 20 (NOTES)
		assertEquals(63, g.surface()[k]);
		assertEquals(20, g.water()[k]);
		assertEquals("river", g.palette().get(g.biome()[k]));
	}

	@Test
	void differentSeedsDiffer() throws Exception {
		var a = TerrainDumpTool.sample(1, 0, 0, 64, 8, 8, 2);
		var b = TerrainDumpTool.sample(2, 0, 0, 64, 8, 8, 2);
		assertFalse(Arrays.equals(a.surface(), b.surface()));
	}
}
