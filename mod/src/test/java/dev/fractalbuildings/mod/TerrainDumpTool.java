package dev.fractalbuildings.mod;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPOutputStream;
import dev.fractalbuildings.mod.probe.LiveTerrainProbe;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Samples vanilla terrain and biomes without a server and writes {@code fb-region-dump/1}
 * (layers empty). Test sources only. Run via {@link TerrainDumpToolTest#dump()}:
 * {@code ./gradlew :mod:test --tests '*TerrainDumpToolTest.dump' -Dfb.dump=seed:x0:z0:step:nx:nz:name[,...]}
 * (names are written to {@code fixtures/terrain/<name>.json.gz}, or under {@code -Dfb.dump.out}).
 * Surface and floor come from {@code getBaseColumn} (the OCEAN_FLOOR heightmap reads minY in the
 * plain bootstrap, see NOTES); biome is taken at the surface quart after a tie-priming lookup.
 */
public final class TerrainDumpTool {
	private TerrainDumpTool() {}

	public static final String MC = "26.3";
	static final LevelHeightAccessor H = LiveTerrainProbe.H;
	/** Fixed non-tie target that resets the RTree's thread-local "last result" before each lookup. */
	private static final Climate.TargetPoint PRIME = Climate.target(0.0F, 0.0F, 0.5F, 0.0F, 0.0F, 0.0F);

	private static HolderLookup.Provider reg;
	private static Holder<NoiseGeneratorSettings> settings;
	private static NoiseBasedChunkGenerator gen;

	public static synchronized void boot() {
		if (gen != null) return;
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		reg = VanillaRegistries.createWorldLookup();
		dev.fractalbuildings.mod.testing.BiomeTagBinder.bind(reg);
		settings = reg.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
		var src = MultiNoiseBiomeSource.createFromPreset(
			reg.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		gen = new NoiseBasedChunkGenerator(src, settings);
	}

	/** One sampled lattice window, row-major. */
	public record Grid(long seed, int x0, int z0, int step, int nx, int nz, List<String> palette, int[] biome, int[] surface, int[] water) {}

	/** {surface, floor}: the live probe's definition ({@link LiveTerrainProbe#column}), so dumps and the game cannot drift. */
	static int[] surfaceAndFloor(RandomState rs, int x, int z) {
		return LiveTerrainProbe.column(gen, rs, x, z);
	}

	public static NoiseBasedChunkGenerator generator() { boot(); return gen; }

	public static RandomState randomState(long seed) {
		boot();
		return RandomState.create(reg.lookupOrThrow(Registries.NOISE), seed, settings.value());
	}

	/** A random state for modified settings (tests that bend the density router). */
	public static RandomState randomState(long seed, NoiseGeneratorSettings s) {
		boot();
		return RandomState.create(reg.lookupOrThrow(Registries.NOISE), seed, s);
	}

	static String name(Holder<Biome> h) {
		return h.unwrapKey().map(k -> k.identifier().getNamespace().equals("minecraft") ? k.identifier().getPath() : k.identifier().toString()).orElse("?");
	}

	public static Grid sample(long seed, int x0, int z0, int step, int nx, int nz, int threads) throws Exception {
		boot();
		RandomState rs = RandomState.create(reg.lookupOrThrow(Registries.NOISE), seed, settings.value());
		int[] surface = new int[nx * nz], water = new int[nx * nz];
		String[] names = new String[nx * nz];
		ExecutorService ex = Executors.newFixedThreadPool(threads);
		List<Future<?>> fs = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			final int tt = t;
			fs.add(ex.submit(() -> {
				Climate.Sampler sampler = rs.createClimateSampler(SamplerContext.builder().enableCaches().build());
				var src = (MultiNoiseBiomeSource) gen.getBiomeSource();
				for (int jj = 0; jj < nz; jj++) {
					int j = tt % 2 == 0 ? jj : nz - 1 - jj; // order must not matter
					if (j % threads != tt) continue;
					for (int i = 0; i < nx; i++) {
						int x = x0 + i * step, z = z0 + j * step;
						int[] sf = surfaceAndFloor(rs, x, z);
						src.getNoiseBiome(PRIME);
						Holder<Biome> b = src.getNoiseBiome(sampler.sample(QuartPos.fromBlock(x), QuartPos.fromBlock(sf[0]), QuartPos.fromBlock(z)));
						int k = j * nx + i;
						surface[k] = sf[0];
						water[k] = sf[0] - sf[1];
						names[k] = name(b);
					}
				}
				return null;
			}));
		}
		try {
			for (Future<?> f : fs) f.get();
		} finally {
			ex.shutdown();
		}
		Map<String, Integer> idx = new LinkedHashMap<>(); // palette in first-seen row-major order
		int[] biome = new int[nx * nz];
		for (int k = 0; k < names.length; k++) biome[k] = idx.computeIfAbsent(names[k], n -> idx.size());
		return new Grid(seed, x0, z0, step, nx, nz, new ArrayList<>(idx.keySet()), biome, surface, water);
	}

	/** Same key order and compact layout as the S4 spike's dumps. */
	public static String toJson(Grid g) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"format\":\"fb-region-dump/1\",\"seed\":").append(g.seed()).append(",\"mc\":\"").append(MC).append("\",\"grid\":{\"x0\":")
			.append(g.x0()).append(",\"z0\":").append(g.z0()).append(",\"step\":").append(g.step()).append(",\"nx\":").append(g.nx())
			.append(",\"nz\":").append(g.nz()).append("},\"palette\":[");
		for (int i = 0; i < g.palette().size(); i++) sb.append(i > 0 ? "," : "").append('"').append(g.palette().get(i)).append('"');
		sb.append("],\"biome\":");
		ints(sb, g.biome());
		sb.append(",\"surface\":");
		ints(sb, g.surface());
		sb.append(",\"water\":");
		ints(sb, g.water());
		return sb.append(",\"layers\":[]}").toString();
	}

	private static void ints(StringBuilder sb, int[] a) {
		sb.append('[');
		for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]);
		sb.append(']');
	}

	/** Gzip bytes are stable: the JDK writes a zero mtime. */
	public static void writeGz(Path file, String json) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		try (OutputStream o = new GZIPOutputStream(Files.newOutputStream(file))) {
			o.write(json.getBytes(StandardCharsets.UTF_8));
		}
	}
}
