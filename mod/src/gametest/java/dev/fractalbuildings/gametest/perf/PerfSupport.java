package dev.fractalbuildings.gametest.perf;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.worldgen.LaneStats;
import dev.fractalbuildings.mod.worldgen.Workers;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Shared measuring helpers of the perf world half. Two harnesses record the same rows through it: {@link PerfServerHarness} (a dedicated
 * server, no renderer; what {@code scripts/perf.sh} runs) and {@link PerfClientGameTest} (the old in-client run, {@code perf.sh --client},
 * kept for comparison).
 */
final class PerfSupport extends PerfCore {

	/** {@code /locate} rows (mod world only), on the server thread. */
	void locateRows(MinecraftServer server) {
		long t0 = System.nanoTime();
		String loc = command(server, "locate structure #fractalbuildings:settlement", 30000, 30000);
		put("locate.fresh.ms", (System.nanoTime() - t0) / 1e6);
		put("locate.fresh.text", loc);
		t0 = System.nanoTime();
		loc = command(server, "locate structure #fractalbuildings:settlement", 0, 0);
		put("locate.spawn.ms", (System.nanoTime() - t0) / 1e6);
		put("locate.spawn.text", loc);
		// vanilla villages go through the footprint veto (B7): every candidate asks the region layer, cold, on this thread
		t0 = System.nanoTime();
		loc = command(server, "locate structure #minecraft:village", -30000, -30000);
		put("locate.village.fresh.ms", (System.nanoTime() - t0) / 1e6);
		put("locate.village.fresh.text", loc);
		t0 = System.nanoTime();
		command(server, "locate structure #minecraft:village", -30000, -30000);
		put("locate.village.repeat.ms", (System.nanoTime() - t0) / 1e6);
	}

	/**
	 * The lane counters after the flight (mod world only). {@code flight.startsInProgress} is how many starts were still planning when they were read:
	 * a cold warm of a metropolis region takes longer than the flight, so counters read at the landing can all be 0 (PERFSRV r4).
	 */
	void flightLaneRows() {
		put("flight.startsInProgress", LaneStats.startsInProgress());
		put("flight.lane", LaneStats.report().replace('\n', ' '));
		put("flight.lookups.done", LaneStats.done());
		put("flight.lookups.joined", LaneStats.inflight());
		put("flight.lookups.inline", LaneStats.inline());
		put("flight.start.decisions", LaneStats.decisions()); put("flight.start.stubs", LaneStats.stubs()); put("flight.start.rejected", LaneStats.rejected()); put("flight.starts", LaneStats.starts());
	}

	/** After the flight: cold block in the metropolis, the warm, then the block next to it; region growth, heap, load. {@code p} is "mod." or "base.". */
	void blockRows(MinecraftServer server, String p, boolean mod) {
		ServerLevel level = server.overworld();
		long bytes0 = regionBytes(server);
		phase.accept("cold");
		LaneStats.reset();
		long t1 = System.nanoTime();
		double[] cold = generate(level, METRO_X, METRO_Z, 4, 0);
		put(p + "cold.block.wall.ms", (System.nanoTime() - t1) / 1e6);
		stats(p + "cold.chunk", cold);
		phase.accept(mod ? "warmprep" : "warm");
		if (mod) {
			put("cold.lane", LaneStats.report().replace('\n', ' '));
			put("cold.start.mean.ms", LaneStats.startMeanMs()); put("cold.start.max.ms", LaneStats.startMaxMs()); put("cold.postProcess.mean.ms", LaneStats.postMeanMs());
			WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
			DistrictGrid.Tile t = planner.grid().tileAt(METRO_X + 9 * 16, METRO_Z);
			planner.warm(METRO_X + 9 * 16, METRO_Z, 2048);
			planner.warmQuays(METRO_X + 9 * 16, METRO_Z, 2048);
			planner.prefetch(t.ix(), t.iz());
			Workers.awaitIdle(Workers.pool(), 120_000);
		}
		if (mod) phase.accept("warm");
		LaneStats.reset();
		long t2 = System.nanoTime();
		double[] warm = generate(level, METRO_X, METRO_Z, 4, 9);
		put(p + "warm.block.wall.ms", (System.nanoTime() - t2) / 1e6);
		stats(p + "warm.chunk", warm);
		if (mod) { put("warm.lane", LaneStats.report().replace('\n', ' ')); put("warm.postProcess.mean.ms", LaneStats.postMeanMs()); put("warm.start.mean.ms", LaneStats.startMeanMs()); }
		phase.accept("tail");
		long bytes1 = regionBytes(server);
		put(p + "region.growth.bytes", bytes1 - bytes0);
		put(p + "heap.mb", usedMb());
		put(p + "load.end", load());
	}

	/** The no-settlement control world of the OLD in-client run (perf.sh --client): frequency 0. Not vanilla; the server run's base is tools/vanilla-server (PERFBASE). */
	static void baseConfig() {
		WorldPlanners.setConfigSource(() -> new WorldConfig(WorldConfig.DEFAULT_CELL_SIZE, 0.0, WorldConfig.defaults().maxRadius(), java.util.Set.of(), WorldConfig.defaults().genVersion()));
	}

	/** Derived rows of a complete run (both variants recorded). */
	void derive() {
		double m = (double) out.get("mod.warm.chunk.mean.ms"), b = (double) out.get("base.warm.chunk.mean.ms");
		put("warm.chunk.ratio", b > 0 ? m / b : 0);
		double mc = (double) out.get("mod.cold.chunk.mean.ms"), bc = (double) out.get("base.cold.chunk.mean.ms");
		put("cold.chunk.ratio", bc > 0 ? mc / bc : 0);
		double rg = (double) out.get("mod.region.growth.bytes"), rb = (double) out.get("base.region.growth.bytes");
		put("region.growth.ratio", rb > 0 ? rg / rb - 1 : 0);
		put("heap.growth.mb", (double) out.get("mod.heap.mb") - (double) out.get("base.heap.mb"));
	}
}
