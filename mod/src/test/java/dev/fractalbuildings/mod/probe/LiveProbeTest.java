package dev.fractalbuildings.mod.probe;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.mod.TerrainDumpTool;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Live probes against the recorded dumps (decision 1: the game uses the dumps' exact definition) and
 * the chunk fill methods against each other. A Minecraft bump that changes the definition fails here.
 */
class LiveProbeTest {
	static final Path TERRAIN = Path.of("../fixtures/terrain");

	record Window(String file, long seed) {}

	static final Window TEMPERATE = new Window("gate-temperate-city.json.gz", 987654321L);
	static final Window COAST = new Window("gate-coast-city.json.gz", 12345L);
	static final Window SNOW = new Window("gate-snow-city.json.gz", 987654321L);

	/** Region and coarse-lattice style reads (single columns) equal the recorded dump on every 4th lattice column, biome included. */
	@Test
	void sparseProbesMatchDumps() throws Exception {
		for (Window w : new Window[] {TEMPERATE, COAST, SNOW}) {
			RecordedProbe dump = RecordedProbe.read(TERRAIN.resolve(w.file()));
			var gen = TerrainDumpTool.generator();
			var rs = TerrainDumpTool.randomState(w.seed());
			var live = new LiveProbes(gen, rs);
			var region = live.region();
			int checked = 0, wet = 0;
			for (int j = 0; j < dump.nz(); j += 4) {
				for (int i = 0; i < dump.nx(); i += 4) {
					int x = dump.x0() + i * dump.step(), z = dump.z0() + j * dump.step();
					String at = w.file() + " (" + x + ", " + z + ")";
					assertEquals(dump.floorY(x, z), region.floorY(x, z), "floor " + at);
					assertEquals(dump.waterDepth(x, z), region.waterDepth(x, z), "water " + at);
					assertEquals(dump.biome(x, z), region.biome(x, z), "biome " + at);
					assertEquals(dump.biome(x, z), live.biome().biome(x, z), "biome (district probe) " + at);
					checked++;
					if (dump.waterDepth(x, z) > 0) wet++;
				}
			}
			assertTrue(checked > 400, "checked " + checked);
			System.out.println("PARITY " + w.file() + ": " + checked + " columns equal, " + wet + " with water");
		}
	}

	/** The chunk-memo path (district reads) equals the dump on the lattice columns of chunks around the sites, for every fill method. */
	@Test
	void chunkMemoMatchesDumps() throws Exception {
		var gen = TerrainDumpTool.generator();
		for (Window w : new Window[] {TEMPERATE, COAST}) {
			RecordedProbe dump = RecordedProbe.read(TERRAIN.resolve(w.file()));
			var rs = TerrainDumpTool.randomState(w.seed());
			int cx0 = (dump.x0() + dump.step() * dump.nx() / 2) >> 4, cz0 = (dump.z0() + dump.step() * dump.nz() / 2) >> 4;
			for (ChunkFill fill : new ChunkFill[] {ChunkFill.COLUMNS, ChunkFill.BATCHED, ChunkFill.WINDOWED}) {
				var probe = new LiveTerrainProbe(gen, rs, fill, 64);
				int n = 0;
				for (int cz = cz0; cz < cz0 + 3; cz++) {
					for (int cx = cx0; cx < cx0 + 3; cx++) {
						for (int dz = 0; dz < 16; dz++) {
							for (int dx = 0; dx < 16; dx++) {
								int x = cx * 16 + dx, z = cz * 16 + dz;
								if (Math.floorMod(x - dump.x0(), dump.step()) != 0 || Math.floorMod(z - dump.z0(), dump.step()) != 0) continue;
								assertEquals(dump.floorY(x, z), probe.floorY(x, z), w.file() + " floor (" + x + ", " + z + ")");
								assertEquals(dump.waterDepth(x, z), probe.waterDepth(x, z), w.file() + " water (" + x + ", " + z + ")");
								n++;
							}
						}
					}
				}
				assertTrue(n >= 30, "lattice columns compared: " + n);
			}
		}
	}

	/** All three fill methods give the same 512 values as 256 getBaseColumn calls, on land, coast, snow and ocean chunks. */
	@Test
	void fillMethodsAgree() throws Exception {
		var gen = TerrainDumpTool.generator();
		int[][] spots = {{987654321, 1280, -9600}, {987654321, 4288, 448}, {987654321, 6720, -6784}, {12345, -7360, -3712}, {12345, -4000, -2000}, {1, 8960, -3648}};
		int wetChunks = 0;
		for (int[] s : spots) {
			var rs = TerrainDumpTool.randomState(s[0]);
			for (int k = 0; k < 4; k++) {
				int cx = (s[1] >> 4) + k * 7, cz = (s[2] >> 4) - k * 5;
				int[] a = ChunkFill.COLUMNS.fill(gen, rs, cx, cz);
				int[] b = ChunkFill.BATCHED.fill(gen, rs, cx, cz);
				int[] c = ChunkFill.WINDOWED.fill(gen, rs, cx, cz);
				String at = "seed " + s[0] + " chunk " + cx + "," + cz;
				assertArrayEquals(a, b, "batched " + at);
				assertArrayEquals(a, c, "windowed " + at);
				if (Arrays.stream(a, 256, 512).anyMatch(v -> v > 0)) wetChunks++;
			}
		}
		assertTrue(wetChunks > 0, "no chunk with water in the sample, the fluid path is untested");
	}

	/** Windowed and batched fills equal 256 getBaseColumn calls on 72 chunks spread over 3 seeds and +-9000 blocks (mountains, oceans, rivers). */
	@Test
	void windowedAgreesOnManyChunks() {
		var gen = TerrainDumpTool.generator();
		long[] seeds = {1L, 12345L, 987654321L};
		int wet = 0, fallbacks0 = (int) ChunkFill.Batched.FALLBACKS.get();
		for (long seed : seeds) {
			var rs = TerrainDumpTool.randomState(seed);
			long h = seed * 0x9E3779B97F4A7C15L;
			for (int k = 0; k < 24; k++) {
				h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L + k;
				int cx = (int) ((h >>> 8) % 1125) - 562, cz = (int) ((h >>> 32) % 1125) - 562;
				int[] a = ChunkFill.COLUMNS.fill(gen, rs, cx, cz);
				assertArrayEquals(a, ChunkFill.WINDOWED.fill(gen, rs, cx, cz), "windowed seed " + seed + " chunk " + cx + "," + cz);
				if (k % 3 == 0) assertArrayEquals(a, ChunkFill.BATCHED.fill(gen, rs, cx, cz), "batched seed " + seed + " chunk " + cx + "," + cz);
				if (Arrays.stream(a, 256, 512).anyMatch(v -> v > 0)) wet++;
			}
		}
		System.out.println("MANY: 72 chunks equal, " + wet + " with water, " + (ChunkFill.Batched.FALLBACKS.get() - fallbacks0) + " windowed fallbacks, " + ChunkFill.Batched.ABOVE_HITS.get() + " of them by the solid-above check");
	}


	/** Reads in a scrambled order, one column per chunk (what a district does), equal getBaseColumn; district biome (memo surface) equals sparse biome (single-column surface). */
	@Test
	void lazyBlockReadsMatchColumns() {
		var gen = TerrainDumpTool.generator();
		for (long seed : new long[] {1L, 12345L, 987654321L}) {
			var rs = TerrainDumpTool.randomState(seed);
			var live = new LiveProbes(gen, rs);
			long h = seed * 0x9E3779B97F4A7C15L;
			int wet = 0;
			for (int k = 0; k < 400; k++) {
				h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L + k;
				int cx = (int) ((h >>> 8) % 400) - 200, cz = (int) ((h >>> 32) % 400) - 200;
				int x = cx * 16 + (int) ((h >>> 3) & 15), z = cz * 16 + (int) ((h >>> 52) & 15);
				int[] ref = LiveTerrainProbe.column(gen, rs, x, z);
				String at = "seed " + seed + " (" + x + ", " + z + ")";
				assertEquals(ref[1], live.exact().floorY(x, z), "floor " + at);
				assertEquals(ref[0] - ref[1], live.exact().waterDepth(x, z), "water " + at);
				assertEquals(ref[0], live.exact().surfaceY(x, z), "surface " + at);
				if (k % 8 == 0) assertEquals(live.region().biome(x, z), live.biome().biome(x, z), "biome " + at);
				if (ref[0] > ref[1]) wet++;
			}
			assertTrue(live.exactLive().memo().size() >= 200, "chunks filled lazily");
		}
	}

	/** The sparse memo hands out copies: writing to a result does not change later reads. */
	@Test
	void sparseColumnsReturnCopies() {
		var gen = TerrainDumpTool.generator();
		var sparse = new SparseColumns(gen, TerrainDumpTool.randomState(987654321L), 16);
		int[] a = sparse.get(1280, -9600);
		int f = a[1];
		a[0] = 12345;
		a[1] = 54321;
		assertEquals(f, sparse.get(1280, -9600)[1]);
		assertEquals(f, sparse.floor(1280, -9600));
		assertNotEquals(12345, sparse.surface(1280, -9600));
	}

	/**
	 * The solid-above check: a slab 16 thick at y 224 to 239 (the sample step; thinner features are the documented blind spot), far above the surface (more than 32 blocks above the sampled
	 * chunk_surface_level, air at the window top), must send the fill to the full scan, and the result must equal getBaseColumn
	 * on the same bent terrain. Without the check the windowed fill would report the real ground (control below).
	 */
	@Test
	void overhangAboveWindowFallsBack() {
		var base = TerrainDumpTool.generator();
		var s = base.generatorSettings().value();
		var r = s.noiseRouter();
		var slab = net.minecraft.world.level.levelgen.densityfunction.DensityFunctions.rangeChoice(
			net.minecraft.world.level.levelgen.densityfunction.DensityFunctions.yClampedGradient(-64, 320, -64f, 320f), 224f, 240f,
			net.minecraft.world.level.levelgen.densityfunction.DensityFunctions.constant(1f), net.minecraft.world.level.levelgen.densityfunction.DensityFunctions.constant(-1f));
		var router = new net.minecraft.world.level.levelgen.NoiseRouter(r.temperature(), r.vegetation(), r.continents(), r.erosion(), r.depth(), r.ridges(), r.chunkSurfaceLevel(),
			net.minecraft.world.level.levelgen.densityfunction.DensityFunctions.max(r.finalDensity(), slab));
		var bent = new net.minecraft.world.level.levelgen.NoiseGeneratorSettings(s.noiseSettings(), s.defaultBlock(), s.defaultFluid(), router, s.materialRule(), s.spawnTarget(), s.seaLevel(), s.disableMobGeneration(), s.aquifers(), s.useLegacyRandomSource(), s.debugFunctions());
		var gen = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(base.getBiomeSource(), net.minecraft.core.Holder.direct(bent));
		var rs = TerrainDumpTool.randomState(987654321L, bent);
		long hits0 = ChunkFill.Batched.ABOVE_HITS.get();
		for (int[] c : new int[][] {{80, -600}, {268, 28}, {420, -424}}) {
			int[] ref = ChunkFill.COLUMNS.fill(gen, rs, c[0], c[1]);
			assertEquals(240, ref[0], "the slab is the surface of the bent terrain");
			assertArrayEquals(ref, ChunkFill.WINDOWED.fill(gen, rs, c[0], c[1]), "windowed chunk " + c[0] + "," + c[1]);
		}
		assertTrue(ChunkFill.Batched.ABOVE_HITS.get() - hits0 >= 3, "the check, not luck, caught the slab");
	}

	/** Memo eviction and read order change speed only. */
	@Test
	void memoIsPure() throws Exception {
		var gen = TerrainDumpTool.generator();
		var rs = TerrainDumpTool.randomState(987654321L);
		var big = new LiveTerrainProbe(gen, rs, ChunkFill.BATCHED, 64);
		var tiny = new LiveTerrainProbe(gen, rs, ChunkFill.BATCHED, 2);
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < 6; i++) {
				int x = 1280 + (pass == 0 ? i : 5 - i) * 17, z = -9600 + (pass == 0 ? 5 - i : i) * 23;
				assertEquals(big.floorY(x, z), tiny.floorY(x, z));
				assertEquals(big.waterDepth(x, z), tiny.waterDepth(x, z));
			}
		}
		assertTrue(tiny.memo().fills() > big.memo().fills(), "tiny memo must have refilled");
	}
}
