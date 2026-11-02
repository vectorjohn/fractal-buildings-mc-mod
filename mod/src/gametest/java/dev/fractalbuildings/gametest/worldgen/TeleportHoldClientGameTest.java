package dev.fractalbuildings.gametest.worldgen;

import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.teleport.TeleportHold;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;

/**
 * TPHOLD in a real client: {@code /tp} far into a cold village. The player must stay where they were until the destination is generated,
 * and the screenshot taken after the teleport must show terrain (the log says how long the hold took).
 */
// gametest-order: 9
public class TeleportHoldClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("TeleportHoldClientGameTest")) return;
		if (dev.fractalbuildings.gametest.perf.PerfGate.skipOthers()) return; // B8: a perf-only run
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(c -> c.setSeed("987654321"))
				.create()) {
			context.runOnClient(client -> client.options.renderDistance().set(6));
			AtomicReference<int[]> dest = new AtomicReference<>();
			AtomicReference<Vec3> start = new AtomicReference<>();
			world.getServer().runOnServer(server -> {
				var level = server.overworld();
				WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
				SettlementInfo n = null;
				for (int r = 1; r <= 3 && n == null; r++) n = planner.nearest(0, 0, Tier.VILLAGE, r).orElse(null);
				if (n == null) throw new IllegalStateException("no village near the origin");
				int x = n.site().centre().x(), z = n.site().centre().z();
				if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) throw new IllegalStateException("the village is not cold");
				dest.set(new int[] {x, z});
				start.set(server.getPlayerList().getPlayers().get(0).position());
			});
			int[] d = dest.get();
			world.getServer().runCommand("gamemode spectator @p");
			world.getServer().runCommand("time set noon");
			long t0 = System.nanoTime();
			world.getServer().runCommand("tp @p " + d[0] + " 110 " + d[1] + " 0 30");
			AtomicReference<Vec3> held = new AtomicReference<>();
			world.getServer().runOnServer(server -> held.set(server.getPlayerList().getPlayers().get(0).position()));
			if (held.get().distanceTo(start.get()) > 1) throw new IllegalStateException("the player moved at once: " + held.get());
			int ticks = 0;
			while (ticks < 1200) {
				context.waitTicks(5);
				ticks += 5;
				AtomicReference<Vec3> now = new AtomicReference<>();
				world.getServer().runOnServer(server -> now.set(server.getPlayerList().getPlayers().get(0).position()));
				if (now.get().distanceTo(start.get()) > 1) break;
			}
			FractalBuildingsMod.LOGGER.info("[TPHOLD] the teleport to {},{} happened after {} ms wall, about {} ticks", d[0], d[1], (System.nanoTime() - t0) / 1_000_000, ticks);
			if (ticks >= 1200) throw new IllegalStateException("the teleport never happened");
			context.waitTicks(60);
			try {
				world.getConnection().waitForChunksRender();
			} catch (AssertionError e) {
				FractalBuildingsMod.LOGGER.warn("[TPHOLD] chunks did not finish rendering in time; taking the screenshot anyway");
			}
			context.waitTicks(20);
			context.takeScreenshot("teleport-hold-arrived");
		}
		coldMetropolisSurvival(context);
	}

	/**
	 * John 2026-11-02 (never release into chunks that are not ready): a survival player teleports into a cold metropolis core at surface + 2
	 * while the stuck-generation limit is short (3 s here, so it always passes: the metropolis takes far longer; the real limit is 60 sutes).
	 * The hold must ABORT: the player stays at the origin with full health for the next seconds, the destination is never entered and the
	 * release hook never fires. (Before, the hold released the player into non-FULL chunks and the test checked they survived.)
	 */
	private static void coldMetropolisSurvival(ClientGameTestContext context) {
		final int mx = 2432, mz = -5696; // the metropolis centre of seed 987654321 (PerfSupport.METRO_X/Z)
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(c -> c.setSeed("987654321"))
				.create()) {
			context.runOnClient(client -> client.options.renderDistance().set(6));
			AtomicReference<int[]> surf = new AtomicReference<>();
			AtomicReference<Vec3> start = new AtomicReference<>();
			AtomicReference<String> released = new AtomicReference<>();
			java.util.concurrent.atomic.AtomicInteger aborted = new java.util.concurrent.atomic.AtomicInteger();
			world.getServer().runOnServer(server -> {
				var level = server.overworld();
				if (level.getChunkSource().getChunkNow(mx >> 4, mz >> 4) != null) throw new IllegalStateException("the metropolis is not cold");
				int h = level.getChunkSource().getGenerator().getBaseHeight(mx, mz, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
					level, level.getChunkSource().randomState());
				surf.set(new int[] {h, level.getMinY()});
				TeleportHold.timeoutMillis = Long.parseLong(System.getenv().getOrDefault("FB_TPHOLD_LIMIT_MS", "3000"));
				TeleportHold.testReleased = (pl, full, to) -> released.set("chunksFull=" + full + " timedOut=" + to);
				TeleportHold.testAborted = pl -> aborted.incrementAndGet();
				start.set(server.getPlayerList().getPlayers().get(0).position());
			});
			int base = surf.get()[0], minY = surf.get()[1];
			world.getServer().runCommand("gamemode survival @p");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("tp @p " + mx + " " + (base + 2) + " " + mz);
			world.getServer().runOnServer(server -> { TeleportHold.timeoutMillis = TeleportHold.DEFAULT_TIMEOUT_MILLIS; TeleportHold.testReleased = null; TeleportHold.testAborted = null; });
			long t0 = System.nanoTime();
			double hpMin = 1e9;
			boolean everHeld = false, entered = false;
			long abortedAtMs = -1;
			String last = "";
			while (abortedAtMs < 0 || (System.nanoTime() - t0) / 1_000_000L < abortedAtMs + 12_000) { // the limit, then 12 s of watching the player
				context.waitTicks(10);
				AtomicReference<double[]> st = new AtomicReference<>();
				world.getServer().runOnServer(server -> {
					var p = server.getPlayerList().getPlayers().get(0);
					st.set(new double[] {p.getX(), p.getY(), p.getZ(), p.getHealth(), TeleportHold.holding(p) ? 1 : 0});
				});
				double[] v = st.get();
				hpMin = Math.min(hpMin, v[3]);
				everHeld |= v[4] > 0;
				entered |= Math.abs(v[0] - mx) < 64 && Math.abs(v[2] - mz) < 64;
				if (abortedAtMs < 0 && v[4] == 0 && everHeld) abortedAtMs = (System.nanoTime() - t0) / 1_000_000L;
				last = String.format("t=%ds pos=%.1f,%.1f,%.1f hp=%.1f held=%s", (System.nanoTime() - t0) / 1_000_000_000L, v[0], v[1], v[2], v[3], v[4] > 0);
				if (v[1] < minY + 1) throw new IllegalStateException("the player fell into the void: " + last);
				if (v[3] < 20) throw new IllegalStateException("the player took damage: " + last);
				if ((System.nanoTime() - t0) / 1_000_000_000L > 150) throw new IllegalStateException("the hold never ended: " + last);
			}
			FractalBuildingsMod.LOGGER.info("[TPABORT] held {}, ended after about {} ms, aborts {}, release hook {}, min health {}, final: {}", everHeld, abortedAtMs, aborted.get(), released.get(), hpMin, last);
			if (!everHeld) throw new IllegalStateException("the teleport was never held");
			if (aborted.get() != 1) throw new IllegalStateException("the hold must abort exactly once, aborts " + aborted.get());
			if (released.get() != null) throw new IllegalStateException("an aborted hold must never release the player: " + released.get());
			if (entered) throw new IllegalStateException("the player must never enter the destination: " + last);
			AtomicReference<Vec3> end = new AtomicReference<>();
			world.getServer().runOnServer(server -> end.set(server.getPlayerList().getPlayers().get(0).position()));
			if (end.get().distanceTo(start.get()) > 1) throw new IllegalStateException("the player must still be at the origin: " + end.get() + " vs " + start.get());
			context.takeScreenshot("teleport-hold-aborted");
		}
	}
}
