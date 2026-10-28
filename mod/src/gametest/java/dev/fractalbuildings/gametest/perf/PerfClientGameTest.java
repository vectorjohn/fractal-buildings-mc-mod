package dev.fractalbuildings.gametest.perf;

import static dev.fractalbuildings.gametest.perf.PerfSupport.*;

import dev.fractalbuildings.mod.worldgen.LaneStats;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * B8 (6.7), the OLD in-client form of the world half: runs in the integrated server of a lavapipe-rendered client, so its timings share
 * cores with the renderer. {@code scripts/perf.sh} now uses the renderer-free {@link PerfServerHarness}; this one runs only with
 * {@code FB_PERF} set ({@code scripts/perf.sh --client}, kept to compare old and new numbers, see docs/log/2026-10-26-p6-perfsrv.md).
 * Two normal worlds of seed 987654321: one with settlements, one with the region layer's frequency at 0. Each flies the same path at
 * elytra speed toward the gate city, then generates the same two 9x9-chunk blocks in the metropolis; writes {@code world.json}.
 */
// gametest-order: 10
public class PerfClientGameTest implements FabricClientGameTest {
	private final PerfSupport s = new PerfSupport();

	/** One world: flight, cold block, warmed block. {@code p} is "mod." or "base.". */
	private void world(ClientGameTestContext context, String p, boolean mod) {
		if (!mod) baseConfig();
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(SEED)).create()) {
			context.runOnClient(client -> client.options.renderDistance().set(4));
			s.put(p + "load.start", load());
			LaneStats.reset();
			if (mod) world.getServer().runOnServer(s::locateRows);
			LaneStats.reset();
			// elytra-speed flight toward the gate city, from the far side (the metropolis stays more than 6 km away, so its block below is cold); tick durations on the server thread
			world.getServer().runCommand("gamemode spectator @p");
			AtomicInteger ticks = new AtomicInteger();
			AtomicBoolean done = new AtomicBoolean();
			List<Double> tickMs = new ArrayList<>();
			AtomicReference<Long> start = new AtomicReference<>(0L);
			ServerTickEvents.START_SERVER_TICK.register(sv -> start.set(System.nanoTime()));
			ServerTickEvents.END_SERVER_TICK.register(sv -> {
				if (done.get()) return;
				int n = ticks.get();
				synchronized (tickMs) { if (n > 0) tickMs.add((System.nanoTime() - start.get()) / 1e6); }
				ServerPlayer pl = sv.getPlayerList().getPlayers().isEmpty() ? null : sv.getPlayerList().getPlayers().get(0);
				if (pl == null) return;
				double d = n * ELYTRA_PER_TICK;
				if (d >= FLIGHT_BLOCKS) { done.set(true); return; }
				pl.teleportTo(CITY_X, 160, CITY_Z - FLIGHT_BLOCKS + d);
				ticks.incrementAndGet();
			});
			int need = (int) (FLIGHT_BLOCKS / ELYTRA_PER_TICK) + 40;
			long t0 = System.nanoTime();
			context.waitTicks(need);
			s.put(p + "flight.wall.s", (System.nanoTime() - t0) / 1e9);
			s.put(p + "flight.ticks", ticks.get());
			double[] tm;
			synchronized (tickMs) { tm = tickMs.stream().mapToDouble(Double::doubleValue).toArray(); }
			s.flightStats(p, tm);
			if (mod) world.getServer().runOnServer(sv -> s.flightLaneRows());
			world.getServer().runOnServer(sv -> s.blockRows(sv, p, mod));
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
		s.derive();
		s.write(Path.of(System.getenv().getOrDefault("FB_PERF_OUT", "build/perf")).resolve("world.json"));
	}
}
