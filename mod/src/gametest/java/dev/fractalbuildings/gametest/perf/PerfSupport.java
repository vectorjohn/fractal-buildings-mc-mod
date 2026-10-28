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
final class PerfSupport {
	static final String SEED = "987654321";
	static final int CITY_X = 1280, CITY_Z = -9600, METRO_X = 2432, METRO_Z = -5696;
	static final double ELYTRA_PER_TICK = 33.0 / 20.0;
	static final int FLIGHT_BLOCKS = Integer.parseInt(System.getenv().getOrDefault("FB_PERF_FLIGHT", "2500"));

	final Map<String, Object> out = new LinkedHashMap<>();

	void put(String k, double v) {
		out.put(k, Math.round(v * 100.0) / 100.0);
		FractalBuildingsMod.LOGGER.info("[PERF] {} = {}", k, String.format(Locale.ROOT, "%.2f", v));
	}

	void put(String k, String v) {
		out.put(k, v);
		FractalBuildingsMod.LOGGER.info("[PERF] {} = {}", k, v);
	}

	static String load() {
		try { return Files.readString(Path.of("/proc/loadavg")).split(" ")[0]; } catch (IOException e) { return "?"; }
	}

	static String command(MinecraftServer server, String command, double x, double z) {
		StringBuilder o = new StringBuilder();
		CommandSource sink = new CommandSource() {
			@Override public void sendSystemMessage(Component m) { o.append(m.getString()).append('\n'); }
			@Override public boolean acceptsSuccess() { return true; }
			@Override public boolean acceptsFailure() { return true; }
			@Override public boolean shouldInformAdmins() { return false; }
		};
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(sink).withPosition(new net.minecraft.world.phys.Vec3(x, 64, z)), command);
		return o.toString().strip();
	}

	static long usedMb() {
		for (int i = 0; i < 3; i++) System.gc();
		Runtime r = Runtime.getRuntime();
		return (r.totalMemory() - r.freeMemory()) >> 20;
	}

	static long regionBytes(MinecraftServer server) {
		server.saveEverything(false, true, false);
		Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		try (var s = Files.walk(dir)) {
			return s.filter(p -> p.toString().endsWith(".mca")).mapToLong(p -> p.toFile().length()).sum();
		} catch (IOException e) {
			return -1;
		}
	}

	/** Generates a (2r+1)^2 block of chunks around a block position on the server thread; returns the per-chunk wall times (ms). */
	static double[] generate(ServerLevel level, int bx, int bz, int r, int shiftChunksX) {
		int ccx = (bx >> 4) + shiftChunksX, ccz = bz >> 4;
		List<Double> t = new ArrayList<>();
		for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
			long t0 = System.nanoTime();
			final int fi = t.size();
			Thread wd = new Thread(() -> { // COLDCHUNK diagnostic: dump busy worker stacks while one chunk takes > 4 s
				try {
					Thread.sleep(4000);
					for (int k = 0; k < 60; k++) {
						StringBuilder sb = new StringBuilder("[COLDCHUNK-STACK] chunk#" + fi + " t+" + (4 + k * 5) + "s\n");
						for (var e : Thread.getAllStackTraces().entrySet()) {
							String n = e.getKey().getName();
							StackTraceElement[] st = e.getValue();
							if (!(n.contains("Worker") || n.contains("Server thread") || n.contains("fb-"))) continue;
							if (st.length == 0 || (!n.contains("Server thread") && st[0].getMethodName().startsWith("park"))) continue;
							sb.append("  ").append(n).append('\n');
							for (int i = 0; i < Math.min(st.length, 25); i++) sb.append("    ").append(st[i]).append('\n');
						}
						System.out.println(sb);
						Thread.sleep(5000);
					}
				} catch (InterruptedException ignored) { }
			}, "coldchunk-watchdog");
			wd.setDaemon(true);
			wd.start();
			level.getChunk(ccx + dx, ccz + dz, ChunkStatus.FULL, true);
			wd.interrupt();
			t.add((System.nanoTime() - t0) / 1e6);
			if (t.get(fi) > 1000) System.out.println("[COLDCHUNK] chunk#" + fi + " (" + (ccx + dx) + "," + (ccz + dz) + ") took " + t.get(fi) + " ms");
		}
		return t.stream().mapToDouble(Double::doubleValue).toArray();
	}

	void stats(String k, double[] ms) {
		double[] s = ms.clone();
		Arrays.sort(s);
		put(k + ".mean.ms", Arrays.stream(s).average().orElse(0));
		put(k + ".median.ms", s[s.length / 2]);
		put(k + ".p95.ms", s[Math.min(s.length - 1, (int) Math.ceil(s.length * 0.95) - 1)]);
		put(k + ".max.ms", s[s.length - 1]);
	}

	/** The flight rows from the per-tick durations: stats, where the worst tick fell, how many ran over 50 ms. */
	void flightStats(String p, double[] tm) {
		if (tm.length > 0) stats(p + "flight.tick", tm);
		int worst = 0;
		for (int i = 0; i < tm.length; i++) if (tm[i] > tm[worst]) worst = i;
		put(p + "flight.tick.worstAt.blocksFlown", worst * ELYTRA_PER_TICK);
		double over = 0;
		for (double x : tm) if (x > 50) over++;
		put(p + "flight.ticksOver50ms", over);
	}

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
		LaneStats.reset();
		long t1 = System.nanoTime();
		double[] cold = generate(level, METRO_X, METRO_Z, 4, 0);
		put(p + "cold.block.wall.ms", (System.nanoTime() - t1) / 1e6);
		stats(p + "cold.chunk", cold);
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
		LaneStats.reset();
		long t2 = System.nanoTime();
		double[] warm = generate(level, METRO_X, METRO_Z, 4, 9);
		put(p + "warm.block.wall.ms", (System.nanoTime() - t2) / 1e6);
		stats(p + "warm.chunk", warm);
		if (mod) { put("warm.lane", LaneStats.report().replace('\n', ' ')); put("warm.postProcess.mean.ms", LaneStats.postMeanMs()); put("warm.start.mean.ms", LaneStats.startMeanMs()); }
		long bytes1 = regionBytes(server);
		put(p + "region.growth.bytes", bytes1 - bytes0);
		put(p + "heap.mb", usedMb());
		put(p + "load.end", load());
	}

	/** The no-settlement control world: the same terrain with the region layer's frequency at 0. */
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

	/** Writes the rows as a flat JSON object. */
	void write(Path f) {
		try {
			Files.createDirectories(f.getParent());
			StringBuilder sb = new StringBuilder("{\n");
			int i = 0;
			for (var e : out.entrySet()) {
				sb.append("  \"").append(e.getKey()).append("\": ");
				if (e.getValue() instanceof String s) sb.append('"').append(s.replace("\\", "/").replace("\"", "'")).append('"');
				else sb.append(e.getValue());
				sb.append(++i < out.size() ? ",\n" : "\n");
			}
			Files.writeString(f, sb.append("}\n").toString());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
