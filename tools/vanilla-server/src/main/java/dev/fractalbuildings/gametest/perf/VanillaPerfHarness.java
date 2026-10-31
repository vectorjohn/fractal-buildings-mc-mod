package dev.fractalbuildings.gametest.perf;

import static dev.fractalbuildings.gametest.perf.PerfCore.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * PERFBASE: the vanilla half of the perf world run. The same flow and rows as the mod's {@code PerfServerHarness} in its base variant
 * (village {@code /locate}, an elytra-speed flight of a mock player, a cold and a neighbouring block in the metropolis, region growth, heap;
 * or, in arrival mode, a teleport to the metropolis until its view radius is FULL), but in a server that has no fractalbuildings at all:
 * no jar, mixin, structure, feature or log line of ours. Writes {@code world-base.json} / {@code world-arrival-base.json} with the
 * {@code base.} row prefix, which {@code scripts/perf-merge.mjs} merges with the mod world's rows.
 */
public final class VanillaPerfHarness implements DedicatedServerModInitializer {
	private static final int GRACE_TICKS = 40, VIEW = 4, ARRIVAL_SETTLE_TICKS = 40;
	private static final int ARRIVAL_MAX_S = Integer.parseInt(System.getenv().getOrDefault("FB_PERF_ARRIVAL_MAX_S", "600"));
	private static final String P = "base.";

	private final PerfCore s = new PerfCore();
	private final boolean arrivalMode = "arrival".equals(System.getProperty("fb.perf.mode", "main"));
	private final List<Double> tickMs = new ArrayList<>();
	private ServerPlayer player;
	private long tickStart, flightStart, arrStart;
	private int ticks, grace, arrTicks;
	private double arrMaxTick;
	private boolean flying, finished, arrTeleported;

	@Override
	public void onInitializeServer() {
		LOGGER.info("[PERF] vanilla base harness installed (Fabric Loader and Fabric API only)");
		ServerLifecycleEvents.SERVER_STARTED.register(this::started);
		ServerTickEvents.START_SERVER_TICK.register(sv -> tickStart = System.nanoTime());
		ServerTickEvents.END_SERVER_TICK.register(this::tickEnd);
	}

	private void started(MinecraftServer server) {
		s.put(P + "load.start", load());
		if (!arrivalMode) s.locateRowsBase(server);
		player = MockPlayer.join(server, server.overworld(), arrivalMode ? "perf-arriver" : "perf-flier", VIEW);
		flying = true;
	}

	private void arrivalTick(MinecraftServer server) {
		if (finished) return;
		var level = server.overworld();
		if (arrTicks < ARRIVAL_SETTLE_TICKS) { arrTicks++; return; }
		if (!arrTeleported) {
			arrTeleported = true;
			s.put(P + "arrival.load.start", load());
			arrStart = System.nanoTime();
			player.teleportTo(level, METRO_X + 0.5, 160, METRO_Z + 0.5, java.util.Set.<net.minecraft.world.entity.Relative>of(), 0f, 0f, true);
			s.put(P + "arrival.teleport.call.ms", (System.nanoTime() - arrStart) / 1e6);
			return;
		}
		double tm = (System.nanoTime() - tickStart) / 1e6;
		if (tm > arrMaxTick) arrMaxTick = tm;
		level.getChunkSource().move(player); // what the client's move packets do (MockPlayer.flyTo)
		boolean there = Math.abs(player.getX() - METRO_X) < 2 && Math.abs(player.getZ() - METRO_Z) < 2;
		int cx = METRO_X >> 4, cz = METRO_Z >> 4, ready = 0, want = (2 * VIEW + 1) * (2 * VIEW + 1);
		for (int dz = -VIEW; dz <= VIEW; dz++) for (int dx = -VIEW; dx <= VIEW; dx++) if (level.getChunkSource().getChunkNow(cx + dx, cz + dz) != null) ready++;
		boolean all = there && ready == want;
		long secs = (System.nanoTime() - arrStart) / 1_000_000_000L;
		if (arrTicks++ % 100 == 0) LOGGER.info("[PERF-DIAG] arrival {} s there={} chunks FULL {}/{}", secs, there, ready, want);
		if (!all && secs <= ARRIVAL_MAX_S) return;
		finished = true;
		try {
			double ms = (System.nanoTime() - arrStart) / 1e6;
			s.put(P + "arrival.cold.metro.ms", all ? ms : -1);
			s.put(P + "arrival.cold.metro.ready", ready);
			s.put(P + "arrival.cold.metro.radius", VIEW);
			s.put(P + "arrival.maxTick.ms", arrMaxTick);
			s.put(P + "arrival.load.end", load());
			s.write(Path.of(System.getProperty("fb.perf.out", "build/perf")).resolve("world-arrival-base.json"));
		} finally {
			server.halt(false);
		}
	}

	private void tickEnd(MinecraftServer server) {
		if (arrivalMode) { if (flying) arrivalTick(server); return; }
		if (!flying || finished) return;
		if (ticks == 0) flightStart = System.nanoTime();
		else if (grace == 0) tickMs.add((System.nanoTime() - tickStart) / 1e6);
		double d = ticks * ELYTRA_PER_TICK;
		if (d < FLIGHT_BLOCKS) {
			MockPlayer.flyTo(player, FLY_X, 160, FLY_Z - FLIGHT_BLOCKS + d);
			ticks++;
			return;
		}
		if (grace++ < GRACE_TICKS) return;
		finished = true;
		try {
			s.flightStats(P, tickMs.stream().mapToDouble(Double::doubleValue).toArray());
			s.put(P + "flight.wall.s", (System.nanoTime() - flightStart) / 1e9);
			s.put(P + "flight.ticks", ticks);
			s.put(P + "flight.settle.s", 0);
			s.blockRowsBase(server, P);
			s.write(Path.of(System.getProperty("fb.perf.out", "build/perf")).resolve("world-base.json"));
		} finally {
			server.halt(false);
		}
	}
}
