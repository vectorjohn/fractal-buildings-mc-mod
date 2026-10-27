package dev.fractalbuildings.gametest.perf;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.worldgen.LaneStats;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * B8 (6.7): the in-world half of the performance budget. Two normal worlds of seed 987654321: one with settlements, one with the region
 * layer's frequency at 0 (the same terrain, nothing of ours). Each flies the same path at elytra speed toward the gate city, then
 * generates the same two 9x9-chunk blocks in the metropolis (cold teleport, then after the warm) and flushes to disk. Compares tick time,
 * wall per chunk and region-file growth; also times {@code /locate} in a fresh area, the lane counters and the heap. Runs only with
 * {@code FB_PERF} set ({@code scripts/perf.sh}); writes {@code build/perf/world.json}.
 */
// gametest-order: 10
public class PerfClientGameTest implements FabricClientGameTest {
	private static final String SEED = "987654321";
	private static final int CITY_X = 1280, CITY_Z = -9600, METRO_X = 2432, METRO_Z = -5696;
	private static final double ELYTRA_PER_TICK = 33.0 / 20.0;
	private static final int FLIGHT_BLOCKS = Integer.parseInt(System.getenv().getOrDefault("FB_PERF_FLIGHT", "2500"));

	private final Map<String, Object> out = new LinkedHashMap<>();

	private void put(String k, double v) {
		out.put(k, Math.round(v * 100.0) / 100.0);
		FractalBuildingsMod.LOGGER.info("[PERF] {} = {}", k, String.format(Locale.ROOT, "%.2f", v));
	}

	private void put(String k, String v) {
		out.put(k, v);
		FractalBuildingsMod.LOGGER.info("[PERF] {} = {}", k, v);
	}

	private static String load() {
		try { return Files.readString(Path.of("/proc/loadavg")).split(" ")[0]; } catch (IOException e) { return "?"; }
	}

	private static String command(MinecraftServer server, String command, double x, double z) {
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

	private static long usedMb() {
		for (int i = 0; i < 3; i++) System.gc();
		Runtime r = Runtime.getRuntime();
		return (r.totalMemory() - r.freeMemory()) >> 20;
	}

	private static long regionBytes(MinecraftServer server) {
		server.saveEverything(false, true, false);
		Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		try (var s = Files.walk(dir)) {
			return s.filter(p -> p.toString().endsWith(".mca")).mapToLong(p -> p.toFile().length()).sum();
		} catch (IOException e) {
			return -1;
		}
	}

	/** Generates a (2r+1)^2 block of chunks around a block position on the server thread; returns the per-chunk wall times (ms). */
	private static double[] generate(ServerLevel level, int bx, int bz, int r, int shiftChunksX) {
		int ccx = (bx >> 4) + shiftChunksX, ccz = bz >> 4;
		List<Double> t = new ArrayList<>();
		for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
			long t0 = System.nanoTime();
			level.getChunk(ccx + dx, ccz + dz, ChunkStatus.FULL, true);
			t.add((System.nanoTime() - t0) / 1e6);
		}
		return t.stream().mapToDouble(Double::doubleValue).toArray();
	}

	private void stats(String k, double[] ms) {
		double[] s = ms.clone();
		java.util.Arrays.sort(s);
		put(k + ".mean.ms", java.util.Arrays.stream(s).average().orElse(0));
		put(k + ".median.ms", s[s.length / 2]);
		put(k + ".p95.ms", s[Math.min(s.length - 1, (int) Math.ceil(s.length * 0.95) - 1)]);
		put(k + ".max.ms", s[s.length - 1]);
	}

	/** One world: flight, cold block, warmed block. {@code p} is "mod." or "base.". */
	private void world(ClientGameTestContext context, String p, boolean mod) {
		if (!mod) WorldPlanners.setConfigSource(() -> new WorldConfig(WorldConfig.DEFAULT_CELL_SIZE, 0.0, WorldConfig.defaults().maxRadius(), java.util.Set.of(), WorldConfig.defaults().genVersion()));
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(SEED)).create()) {
			context.runOnClient(client -> client.options.renderDistance().set(4));
			put(p + "load.start", load());
			LaneStats.reset();
			if (mod) {
				world.getServer().runOnServer(server -> {
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
				});
			}
			LaneStats.reset();
			// elytra-speed flight toward the gate city, from the far side (the metropolis stays more than 6 km away, so its block below is cold); tick durations on the server thread
			world.getServer().runCommand("gamemode spectator @p");
			AtomicInteger ticks = new AtomicInteger();
			AtomicBoolean done = new AtomicBoolean();
			List<Double> tickMs = new ArrayList<>();
			AtomicReference<Long> start = new AtomicReference<>(0L);
			ServerTickEvents.START_SERVER_TICK.register(s -> start.set(System.nanoTime()));
			ServerTickEvents.END_SERVER_TICK.register(s -> {
				if (done.get()) return;
				int n = ticks.get();
				synchronized (tickMs) { if (n > 0) tickMs.add((System.nanoTime() - start.get()) / 1e6); }
				ServerPlayer pl = s.getPlayerList().getPlayers().isEmpty() ? null : s.getPlayerList().getPlayers().get(0);
				if (pl == null) return;
				double d = n * ELYTRA_PER_TICK;
				if (d >= FLIGHT_BLOCKS) { done.set(true); return; }
				pl.teleportTo(CITY_X, 160, CITY_Z - FLIGHT_BLOCKS + d);
				ticks.incrementAndGet();
			});
			int need = (int) (FLIGHT_BLOCKS / ELYTRA_PER_TICK) + 40;
			long t0 = System.nanoTime();
			context.waitTicks(need);
			put(p + "flight.wall.s", (System.nanoTime() - t0) / 1e9);
			put(p + "flight.ticks", ticks.get());
			double[] tm;
			synchronized (tickMs) { tm = tickMs.stream().mapToDouble(Double::doubleValue).toArray(); }
			if (tm.length > 0) stats(p + "flight.tick", tm);
			int worst = 0;
			for (int i = 0; i < tm.length; i++) if (tm[i] > tm[worst]) worst = i;
			put(p + "flight.tick.worstAt.blocksFlown", worst * ELYTRA_PER_TICK);
			double over = 0;
			for (double x : tm) if (x > 50) over++;
			put(p + "flight.ticksOver50ms", over);
			if (mod) {
				world.getServer().runOnServer(server -> {
					put("flight.lane", LaneStats.report().replace('\n', ' '));
					put("flight.lookups.done", LaneStats.done());
					put("flight.lookups.joined", LaneStats.inflight());
					put("flight.lookups.inline", LaneStats.inline());
				});
			}
			world.getServer().runOnServer(server -> {
				// cold teleport into the metropolis, then the block next to it after the warm
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
				}
				if (mod) {
					WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
					DistrictGrid.Tile t = planner.grid().tileAt(METRO_X + 9 * 16, METRO_Z);
					planner.warm(METRO_X + 9 * 16, METRO_Z, 2048);
					planner.warmQuays(METRO_X + 9 * 16, METRO_Z, 2048);
					planner.prefetch(t.ix(), t.iz());
					dev.fractalbuildings.mod.worldgen.Workers.awaitIdle(dev.fractalbuildings.mod.worldgen.Workers.pool(), 120_000);
				}
				LaneStats.reset();
				long t2 = System.nanoTime();
				double[] warm = generate(level, METRO_X, METRO_Z, 4, 9);
				put(p + "warm.block.wall.ms", (System.nanoTime() - t2) / 1e6);
				stats(p + "warm.chunk", warm);
				if (mod) put("warm.lane", LaneStats.report().replace('\n', ' '));
				if (mod) { put("warm.postProcess.mean.ms", LaneStats.postMeanMs()); put("warm.start.mean.ms", LaneStats.startMeanMs()); }
				long bytes1 = regionBytes(server);
				put(p + "region.growth.bytes", bytes1 - bytes0);
				put(p + "heap.mb", usedMb());
				put(p + "load.end", load());
			});
		} finally {
			if (!mod) WorldPlanners.resetConfigSource();
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!PerfGate.perf()) return;
		LaneStats.enable(true);
		world(context, "mod.", true);
		world(context, "base.", false);
		double m = (double) out.get("mod.warm.chunk.mean.ms"), b = (double) out.get("base.warm.chunk.mean.ms");
		put("warm.chunk.ratio", b > 0 ? m / b : 0);
		double mc = (double) out.get("mod.cold.chunk.mean.ms"), bc = (double) out.get("base.cold.chunk.mean.ms");
		put("cold.chunk.ratio", bc > 0 ? mc / bc : 0);
		double rg = (double) out.get("mod.region.growth.bytes"), rb = (double) out.get("base.region.growth.bytes");
		put("region.growth.ratio", rb > 0 ? rg / rb - 1 : 0);
		put("heap.growth.mb", (double) out.get("mod.heap.mb") - (double) out.get("base.heap.mb"));
		try {
			Path f = Path.of(System.getenv().getOrDefault("FB_PERF_OUT", "build/perf")).resolve("world.json");
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
