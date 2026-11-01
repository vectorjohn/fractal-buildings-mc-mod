package dev.fractalbuildings.gametest.perf;

import static dev.fractalbuildings.gametest.perf.PerfSupport.*;

import dev.fractalbuildings.mod.worldgen.LaneStats;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * PERFSRV: the world half of the performance budget (6.7) on a dedicated server, no client and no renderer, so the timings do not share cores
 * with lavapipe. One JVM runs one world of seed 987654321 (variant {@code mod}: settlements; {@code base}: the region layer's frequency at 0,
 * the same terrain with nothing of ours) and records the same rows as the old {@link PerfClientGameTest}: {@code /locate} (mod only), an
 * elytra-speed flight of a mock player ({@link MockPlayer}, real chunk loading through {@link MockPlayer#flyTo}) toward the gate city with the tick durations, the lane
 * counters, a cold and a warmed 9x9 block in the metropolis, region-file growth and heap. Writes {@code world-<variant>.json}; the
 * ratios are derived from both by {@code scripts/perf-merge.mjs}. Installed by the {@code -Dfb.testHook} hook of the mod
 * ({@code scripts/perf.sh} runs {@code :mod:runPerfServer}); inert otherwise.
 */
public final class PerfServerHarness {
	private static final int GRACE_TICKS = 40;
	private static final int VIEW = 4;

	private final PerfSupport s = new PerfSupport();
	private final boolean mod = !"base".equals(System.getProperty("fb.perf.variant", "mod"));
	// PERFBASE: the base half is tools/vanilla-server (VanillaPerfHarness), not this class; the frequency-0 variant below is kept only for ad-hoc comparison.
	private final String p = mod ? "mod." : "base.";
	private final PerfProfile prof = new PerfProfile(s, p, mod ? "mod" : "base");
	private final List<Double> tickMs = new ArrayList<>();
	private ServerPlayer player;
	private long tickStart, flightStart;
	private int ticks, grace, quiet;
	private long settleStart, lastActivity = -1;
	private static final int QUIET_TICKS = 100;
	private static final int SETTLE_MAX_S = 240;
	private boolean flying, finished;
	/** COLDARRIVAL: {@code -Pfb.perfMode=arrival} runs only the cold-arrival row (a fresh world per variant, so the metropolis is cold). */
	private final boolean arrivalMode = "arrival".equals(System.getProperty("fb.perf.mode", "main"));
	private static final int ARRIVAL_SETTLE_TICKS = 40, ARRIVAL_MAX_S = Integer.parseInt(System.getenv().getOrDefault("FB_PERF_ARRIVAL_MAX_S", "600"));
	private int arrTicks;
	private long arrStart;
	private double arrMaxTick;
	private long holdEngagedNs = -1, holdEndedNs = -1;
	private boolean arrTeleported, holdTimedOut, holdChunksFull;

	/** Entry point of the mod's test hook, called while the mod initialises (before the world exists). */
	public static void install() {
		PerfServerHarness h = new PerfServerHarness();
		if (!h.mod) baseConfig();
		h.s.phase = h.prof::phase;
		h.prof.phase("boot");
		LaneStats.enable(true);
		ServerLifecycleEvents.SERVER_STARTED.register(h::started);
		ServerTickEvents.START_SERVER_TICK.register(sv -> h.tickStart = System.nanoTime());
		ServerTickEvents.END_SERVER_TICK.register(h::tickEnd);
	}

	private void started(MinecraftServer server) {
		s.put(p + "load.start", load());
		if (arrivalMode) {
			player = MockPlayer.join(server, server.overworld(), "perf-arriver", VIEW);
			flying = true;
			return;
		}
		if (mod) prof.phase("locate");
		LaneStats.reset();
		if (mod) s.locateRows(server); else s.locateRowsBase(server);
		LaneStats.reset();
		// after /locate: the per-start lines (and their nearest-settlement read) cover the flight and what follows. Diagnostic only
		// (FB_LANE_LOG=1, set by perf.sh --world-mod-only): they cost log I/O and region reads inside the timed chunks.
		if ("1".equals(System.getenv("FB_LANE_LOG"))) LaneStats.logStarts(true);
		prof.phase("flight");
		player = MockPlayer.join(server, server.overworld(), "perf-flier", VIEW);
		flying = true;
	}

	/**
	 * True once the building work has stopped: no thread in a district start, the lookups, finished starts and postProcess calls unchanged,
	 * no worker busy but the player's own warm and that warm fast (cached data: the warmer submits one every second, so a busy pool alone
	 * never goes idle), for QUIET_TICKS ticks; or after SETTLE_MAX_S seconds.
	 */
	private boolean settled() {
		var pool = dev.fractalbuildings.mod.worldgen.Workers.pool();
		long act = LaneStats.activity();
		boolean poolIdle = pool == null || (LaneStats.neighboursRunning() == 0 && pool.getActiveCount() + pool.getQueue().size() <= LaneStats.warmsRunning() && LaneStats.lastWarmMs() < WARM_IDLE_MS);
		boolean idle = act == lastActivity && LaneStats.startsInProgress() == 0 && poolIdle;
		lastActivity = act;
		quiet = idle ? quiet + 1 : 0;
		long secs = (System.nanoTime() - settleStart) / 1_000_000_000L;
		if (grace % 100 == 0) dev.fractalbuildings.mod.FractalBuildingsMod.LOGGER.info("[PERF-DIAG] settling {} s activity={} quiet={} pool active={} queued={} warms running={} last warm {} ms {}", secs, act, quiet,
				pool == null ? -1 : pool.getActiveCount(), pool == null ? -1 : pool.getQueue().size(), LaneStats.warmsRunning(), (long) LaneStats.lastWarmMs(), LaneStats.report().replace('\n', ' '));
		if (grace % 600 == 0 && LaneStats.startsInProgress() > 0) dumpOurThreads("settle " + secs + " s");
		boolean done = quiet >= QUIET_TICKS || secs > SETTLE_MAX_S;
		if (done && LaneStats.startsInProgress() > 0) dumpOurThreads("settle end");
		return done;
	}

	/** A finished warm faster than this means the warmer is only reading caches. */
	private static final double WARM_IDLE_MS = 1000;

	/** Logs every thread running our code (worldgen and workers), with the lock it waits on and its owner: is a start stuck, and on what. */
	static void dumpOurThreads(String when) {
		var mx = java.lang.management.ManagementFactory.getThreadMXBean();
		StringBuilder sb = new StringBuilder("[PERF-DIAG] threads in our code at " + when + ":");
		for (var ti : mx.dumpAllThreads(true, false)) {
			StackTraceElement[] st = ti.getStackTrace();
			boolean ours = false;
			for (StackTraceElement e : st) if (e.getClassName().startsWith("dev.fractalbuildings.")) { ours = true; break; }
			if (!ours) continue;
			sb.append("\n  ").append(ti.getThreadName()).append(' ').append(ti.getThreadState());
			if (ti.getLockName() != null) sb.append(" on ").append(ti.getLockName());
			if (ti.getLockOwnerName() != null) sb.append(" owned by ").append(ti.getLockOwnerName());
			int shown = 0;
			for (StackTraceElement e : st) {
				sb.append("\n      at ").append(e);
				if (++shown >= 40) break;
			}
		}
		dev.fractalbuildings.mod.FractalBuildingsMod.LOGGER.info(sb.toString());
	}


	/**
	 * COLDARRIVAL: a player at spawn teleports (the {@code /tp} path, {@code ServerPlayer.teleport}, so TeleportHold can take it over) to the
	 * metropolis centre of the cold-chunk rows; the clock stops when the teleport has happened and every chunk within the player's view radius
	 * (VIEW = the server's view-distance, set by scripts/perf.sh) is FULL. "Sent" is not observable: the mock connection never acks batches.
	 */
	private Thread arrSampler;

	private void arrivalTick(MinecraftServer server) {
		if (finished) return;
		var level = server.overworld();
		if (arrTicks < ARRIVAL_SETTLE_TICKS) { arrTicks++; return; }
		if (!arrTeleported) {
			arrTeleported = true;
			dev.fractalbuildings.mod.teleport.TeleportHold.testReleased = (pl, full, timedOut) -> { holdChunksFull = full; holdTimedOut = timedOut; };
			s.put(p + "arrival.load.start", load());
			arrStart = System.nanoTime();
			arrSampler = PerfCore.sampler("arrival");
			player.teleportTo(level, METRO_X + 0.5, 160, METRO_Z + 0.5, java.util.Set.<net.minecraft.world.entity.Relative>of(), 0f, 0f, true);
			s.put(p + "arrival.teleport.call.ms", (System.nanoTime() - arrStart) / 1e6);
			return;
		}
		double tm = (System.nanoTime() - tickStart) / 1e6;
		if (tm > arrMaxTick) arrMaxTick = tm;
		boolean holding = dev.fractalbuildings.mod.teleport.TeleportHold.holding(player);
		if (holding && holdEngagedNs < 0) holdEngagedNs = System.nanoTime();
		if (!holding && holdEngagedNs >= 0 && holdEndedNs < 0) holdEndedNs = System.nanoTime();
		if (!holding) level.getChunkSource().move(player); // what the client's move packets do (MockPlayer.flyTo)
		boolean there = !holding && Math.abs(player.getX() - METRO_X) < 2 && Math.abs(player.getZ() - METRO_Z) < 2;
		int cx = METRO_X >> 4, cz = METRO_Z >> 4, ready = 0, want = (2 * VIEW + 1) * (2 * VIEW + 1);
		for (int dz = -VIEW; dz <= VIEW; dz++) for (int dx = -VIEW; dx <= VIEW; dx++) if (level.getChunkSource().getChunkNow(cx + dx, cz + dz) != null) ready++;
		boolean all = there && ready == want;
		long secs = (System.nanoTime() - arrStart) / 1_000_000_000L;
		if (arrTicks++ % 100 == 0) dev.fractalbuildings.mod.FractalBuildingsMod.LOGGER.info("[PERF-DIAG] arrival {} s holding={} there={} chunks FULL {}/{}", secs, holding, there, ready, want);
		if (!all && secs <= ARRIVAL_MAX_S) return;
		finished = true;
		if (arrSampler != null) arrSampler.interrupt();
		try {
			double ms = (System.nanoTime() - arrStart) / 1e6;
			s.put(p + "arrival.cold.metro.ms", all ? ms : -1);
			s.put(p + "arrival.cold.metro.ready", ready);
			s.put(p + "arrival.cold.metro.radius", VIEW);
			s.put(p + "arrival.maxTick.ms", arrMaxTick);
			s.put(p + "arrival.hold.engaged", holdEngagedNs >= 0 ? 1 : 0);
			s.put(p + "arrival.hold.ms", holdEngagedNs >= 0 ? ((holdEndedNs < 0 ? System.nanoTime() : holdEndedNs) - holdEngagedNs) / 1e6 : 0);
			s.put(p + "arrival.hold.timedOut", holdTimedOut ? 1 : 0);
			s.put(p + "arrival.hold.chunksFullAtRelease", holdChunksFull ? 1 : 0);
			s.put(p + "arrival.load.end", load());
			s.write(Path.of(System.getProperty("fb.perf.out", "build/perf")).resolve("world-arrival-" + (mod ? "mod" : "base") + ".json"));
		} finally {
			server.halt(false);
		}
	}

	private void tickEnd(MinecraftServer server) {
		if (arrivalMode) { if (flying) arrivalTick(server); return; }
		if (!flying || finished) return;
		if (ticks == 0) flightStart = System.nanoTime();
		else if (grace == 0) tickMs.add((System.nanoTime() - tickStart) / 1e6);
		if (ticks % 100 == 0 && grace == 0) dev.fractalbuildings.mod.FractalBuildingsMod.LOGGER.info("[PERF-DIAG] tick {} player ({}, {}) held={} loadedChunks={} starts/lookups: {}", ticks, (int) player.getX(), (int) player.getZ(), dev.fractalbuildings.mod.teleport.TeleportHold.holding(player), server.overworld().getChunkSource().getLoadedChunksCount(), LaneStats.report().replace('\n', ' '));
		if (ticks % 500 == 0 && ticks > 0 && grace == 0 && LaneStats.startsInProgress() > 0) dumpOurThreads("flight tick " + ticks);
		double d = ticks * ELYTRA_PER_TICK;
		if (d < FLIGHT_BLOCKS) {
			MockPlayer.flyTo(player, FLY_X, 160, FLY_Z - FLIGHT_BLOCKS + d);
			ticks++;
			return;
		}
		if (grace++ < GRACE_TICKS) return; // the old run waited 40 ticks after the last step
		if (settleStart == 0) {
			// the tick stats cover the flight only; the landing is when the lane counters are first read (the .atEnd rows)
			s.flightStats(p, tickMs.stream().mapToDouble(Double::doubleValue).toArray());
			s.put(p + "flight.wall.s", (System.nanoTime() - flightStart) / 1e9);
			s.put(p + "flight.ticks", ticks);
			if (mod) {
				s.put("flight.startsInProgress.atEnd", LaneStats.startsInProgress());
				s.put("flight.lane.atEnd", LaneStats.report().replace('\n', ' '));
				if (LaneStats.startsInProgress() > 0) dumpOurThreads("landing");
			}
			prof.phase("settle");
			settleStart = System.nanoTime();
		}
		// PERFSRV r4: on a cold world the city's warm and starts outlast the flight, so counters read at the landing were all 0 and the
		// inline-share row was vacuous. Keep the player at the city until the starts and workers are idle (bounded), then read them.
		if (mod && !settled()) return;
		finished = true;
		try {
			s.put(p + "flight.settle.s", (System.nanoTime() - settleStart) / 1e9);
			if (mod) s.flightLaneRows();
			s.blockRows(server, p, mod);
			prof.phase(null);
			s.write(Path.of(System.getProperty("fb.perf.out", "build/perf")).resolve("world-" + (mod ? "mod" : "base") + ".json"));
		} finally {
			server.halt(false);
		}
	}
}
