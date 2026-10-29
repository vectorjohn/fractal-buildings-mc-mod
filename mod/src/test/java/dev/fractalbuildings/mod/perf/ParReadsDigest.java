package dev.fractalbuildings.mod.perf;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.mod.TerrainDumpTool;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * PARREADS evidence, not part of any run ({@code -Dfb.parreadsDigest=1}): on live vanilla terrain, a cold planner warms each gate window
 * (fixtures/terrain/gate-sites.tsv) and the perf metropolis, quays included, then prints a SHA-256 of the plans of the tiles round the centre
 * and the road columns round it. Run at a commit before PARREADS and after (the probes come from {@link #probes}, which differs) and compare.
 */
class ParReadsDigest {
	/** How the commit under test builds the game's probes for a world (the game's own code path). */
	static BiFunction<NoiseBasedChunkGenerator, RandomState, WorldProbes> probes = ParReadsDigestProbes::game;

	static String digest(long seed, int x, int z) throws Exception {
		NoiseBasedChunkGenerator gen = TerrainDumpTool.generator();
		RandomState rs = TerrainDumpTool.randomState(seed);
		WorldPlanner p = new WorldPlanner(seed, WorldConfig.defaults(), probes.apply(gen, rs), null);
		long t0 = System.nanoTime();
		p.warm(x, z, 2 * p.config().cellSize());
		p.warmQuays(x, z, 2 * p.config().cellSize());
		double ms = (System.nanoTime() - t0) / 1e6;
		StringBuilder sb = new StringBuilder();
		DistrictGrid.Tile t = p.grid().tileAt(x, z);
		int tiles = 0;
		for (int dz = -4; dz <= 4; dz += 2) {
			for (int dx = -4; dx <= 4; dx += 2) {
				if (p.tile(t.ix() + dx, t.iz() + dz).isEmpty()) continue;
				sb.append(DistrictJson.write(p.plan(t.ix() + dx, t.iz() + dz).plan()));
				tiles++;
			}
		}
		for (int c = -48; c <= 48; c += 6) sb.append(p.roadColumns((x >> 4) + c, z >> 4)).append(p.roadColumns(x >> 4, (z >> 4) + c));
		String h = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
		return String.format("%s tiles %d bytes %d warm+quays %.0f ms", h.substring(0, 16), tiles, sb.length(), ms);
	}

	@Test
	@EnabledIfSystemProperty(named = "fb.parreadsDigest", matches = ".+")
	void gateWindowsAndTheMetropolis() throws Exception {
		Path tsv = Files.isDirectory(Path.of("../fixtures/terrain")) ? Path.of("../fixtures/terrain/gate-sites.tsv") : Path.of("fixtures/terrain/gate-sites.tsv");
		List<String> out = new ArrayList<>();
		for (String line : Files.readAllLines(tsv).stream().skip(1).toList()) {
			String[] f = line.split("\t");
			out.add("DIGEST " + f[0] + " " + digest(Long.parseLong(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3])));
			System.out.println(out.get(out.size() - 1));
		}
		out.add("DIGEST perf-metropolis " + digest(PerfPlannerTest.SEED, 2432, -5696));
		System.out.println(out.get(out.size() - 1));
		Files.createDirectories(Path.of("build/parreads"));
		Files.write(Path.of("build/parreads/digests.txt"), out);
	}
}
