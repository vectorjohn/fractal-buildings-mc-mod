package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.mod.TerrainDumpTool;
import dev.fractalbuildings.mod.probe.LiveProbes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * B4's timing note: what a cold first touch of a settlement costs on live vanilla terrain, and what warming leaves for the lane. Not part
 * of the normal run: {@code -Dfb.timing=1 :mod:test --tests '*LiveWarmTiming*' -i} under the heavy lock (record the load).
 */
class LiveWarmTiming {
	static final long SEED = 987654321L;

	private static void line(String s, Object... a) { System.out.println("TIMING " + String.format(Locale.ROOT, s, a)); }

	private static double ms(long t0) { return (System.nanoTime() - t0) / 1e6; }

	private static WorldPlanner planner(NoiseBasedChunkGenerator gen, RandomState rs, ThreadPoolExecutor pool) {
		LiveProbes lp = new LiveProbes(gen, rs);
		return new WorldPlanner(SEED, WorldConfig.defaults(), new WorldProbes(lp.region(), lp.coarse(32), lp.exact(), lp.biome()), pool);
	}

	@Test
	@EnabledIfSystemProperty(named = "fb.timing", matches = ".+")
	void timing() throws Exception {
		NoiseBasedChunkGenerator gen = TerrainDumpTool.generator();
		RandomState rs = TerrainDumpTool.randomState(SEED);
		line("load before: %s; cpus %d; pool size %d", Files.readString(Path.of("/proc/loadavg")).trim(), Runtime.getRuntime().availableProcessors(), Workers.defaultSize());

		// find a settlement with a cheap planner (its caches are not reused)
		Optional<SettlementInfo> found = Optional.empty();
		for (Tier t : new Tier[] {Tier.CITY, Tier.TOWN, Tier.VILLAGE}) {
			found = planner(gen, rs, null).nearest(0, 0, t, 12);
			if (found.isPresent()) break;
		}
		SettlementInfo info = found.orElseThrow(() -> new IllegalStateException("no settlement near the origin"));
		int sx = info.site().centre().x(), sz = info.site().centre().z();
		line("settlement %s tier %s at %d,%d radius %d", info.site().id(), info.site().tier(), sx, sz, info.site().radius());

		// cold, on the calling thread: what the lane pays for the first touch
		WorldPlanner cold = planner(gen, rs, null);
		long t0 = System.nanoTime();
		cold.warm(sx, sz, 2 * cold.config().cellSize());
		double warmMs = ms(t0);
		DistrictGrid.Tile tile = cold.grid().tileAt(sx, sz);
		t0 = System.nanoTime();
		boolean planned = cold.tile(tile.ix(), tile.iz()).isPresent();
		double tileMs = ms(t0);
		t0 = System.nanoTime();
		int n = cold.buildings(tile.ix(), tile.iz()).size();
		cold.ground(tile.ix(), tile.iz());
		double distMs = ms(t0);
		line("COLD first touch: region + skeletons %.0f ms, tile decision %.0f ms (planned %s), district plan + %d buildings + ground %.0f ms; tile %dx%d blocks",
				warmMs, tileMs, planned, n, distMs, tile.maxX() - tile.minX(), tile.maxZ() - tile.minZ());
		// a neighbour next door, skeleton already warm
		int nix = tile.ix() + 1, niz = tile.iz();
		t0 = System.nanoTime();
		int nb = cold.tile(nix, niz).isPresent() ? cold.buildings(nix, niz).size() : -1;
		line("COLD neighbour tile (skeleton warm): %.0f ms (%d buildings)", ms(t0), nb);

		// warmed: the warm and the prefetch on workers, then the lane's ask
		ThreadPoolExecutor pool = Workers.create(Workers.defaultSize());
		try {
			WorldPlanner w = planner(gen, rs, pool);
			t0 = System.nanoTime();
			pool.execute(() -> w.warm(sx, sz, 2 * w.config().cellSize()));
			Workers.awaitIdle(pool, 600_000);
			double w1 = ms(t0);
			t0 = System.nanoTime();
			w.prefetch(tile.ix(), tile.iz());
			Workers.awaitIdle(pool, 600_000);
			double w2 = ms(t0);
			t0 = System.nanoTime();
			w.buildings(tile.ix(), tile.iz());
			w.ground(tile.ix(), tile.iz());
			double lane = ms(t0);
			t0 = System.nanoTime();
			if (w.tile(nix, niz).isPresent()) w.buildings(nix, niz);
			double laneNb = ms(t0);
			line("WARMED: worker time for region + skeletons %.0f ms, prefetch of the tile + 8 neighbour plans %.0f ms wall (%d workers); lane then pays %.2f ms for the tile, %.0f ms for a neighbour's buildings (plan done ahead)",
					w1, w2, pool.getCorePoolSize(), lane, laneNb);
			double tileSize = tile.maxX() - tile.minX();
			double need = warmMs + tileMs + distMs;
			for (double v : new double[] {4.3, 5.6, 30, 60}) {
				line("speed %.1f b/s: warm radius 2 cells (2048 blocks) = %.0f s lead; tile crossing %.1f s; cold first touch %.1f s, warmed prefetch %.1f s", v, 2048 / v, tileSize / v, need / 1000, (w1 + w2) / 1000);
			}
		} finally {
			Workers.shutdown(pool);
		}
		line("load after: %s", Files.readString(Path.of("/proc/loadavg")).trim());
	}
}
