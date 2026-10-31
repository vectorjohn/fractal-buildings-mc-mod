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
	 * TPHOLD15: a survival player teleports into a cold metropolis core at surface + 2 while the hold gives up early (limit 3 s here, so
	 * it always does; the real limit is 15 s). Over the next minutes the player must not take damage, fall below the terrain or into the void,
	 * and must end up standing on the ground once chunks are FULL.
	 */
	private static void coldMetropolisSurvival(ClientGameTestContext context) {
		final int mx = 2432, mz = -5696; // the metropolis centre of seed 987654321 (PerfSupport.METRO_X/Z)
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(c -> c.setSeed("987654321"))
				.create()) {
			context.runOnClient(client -> client.options.renderDistance().set(6));
			AtomicReference<int[]> surf = new AtomicReference<>();
			AtomicReference<String> released = new AtomicReference<>();
			world.getServer().runOnServer(server -> {
				var level = server.overworld();
				if (level.getChunkSource().getChunkNow(mx >> 4, mz >> 4) != null) throw new IllegalStateException("the metropolis is not cold");
				int h = level.getChunkSource().getGenerator().getBaseHeight(mx, mz, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
					level, level.getChunkSource().randomState());
				surf.set(new int[] {h, level.getMinY()});
				TeleportHold.timeoutMillis = Long.parseLong(System.getenv().getOrDefault("FB_TPHOLD_LIMIT_MS", "3000")); // FB_TPHOLD_LIMIT_MS=15000: the real limit
				TeleportHold.testReleased = (pl, full, to) -> released.set("chunksFull=" + full + " timedOut=" + to);
			});
			int base = surf.get()[0], minY = surf.get()[1];
			world.getServer().runCommand("gamemode survival @p");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("tp @p " + mx + " " + (base + 2) + " " + mz);
			world.getServer().runOnServer(server -> { TeleportHold.timeoutMillis = TeleportHold.DEFAULT_TIMEOUT_MILLIS; TeleportHold.testReleased = null; });
			long t0 = System.nanoTime();
			double lowest = Double.MAX_VALUE, hpMin = 1e9;
			boolean standing = false, everHeld = false, arrived = false;
			int worstFull = 9, gapSamples = 0; // after the hold released: the lowest FULL count in the 3 x 3 under the player, and how many samples had < 9
			String last = "";
			while ((System.nanoTime() - t0) / 1_000_000_000L < 150 && !standing) {
				context.waitTicks(10);
				AtomicReference<double[]> st = new AtomicReference<>();
				world.getServer().runOnServer(server -> {
					var p = server.getPlayerList().getPlayers().get(0);
					var level = server.overworld();
					int cx = p.blockPosition().getX() >> 4, cz = p.blockPosition().getZ() >> 4, full = 0;
					for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) if (level.getChunkSource().getChunkNow(cx + dx, cz + dz) != null) full++;
					st.set(new double[] {p.getX(), p.getY(), p.getZ(), p.getHealth(), p.onGround() ? 1 : 0, full, TeleportHold.holding(p) ? 1 : 0});
				});
				double[] v = st.get();
				lowest = Math.min(lowest, v[1]);
				hpMin = Math.min(hpMin, v[3]);
				everHeld |= v[6] > 0;
				arrived |= v[6] == 0 && Math.abs(v[0] - mx) < 3 && Math.abs(v[2] - mz) < 3;
				last = String.format("t=%ds y=%.2f hp=%.1f onGround=%s full=%d/9 held=%s", (System.nanoTime() - t0) / 1_000_000_000L, v[1], v[3], v[4] > 0, (int) v[5], v[6] > 0);
				if (v[1] < minY + 1) throw new IllegalStateException("the player fell into the void: " + last);
				if (v[3] < 20) throw new IllegalStateException("the player took damage: " + last);
				if (v[6] == 0 && v[5] < 9) { gapSamples++; worstFull = Math.min(worstFull, (int) v[5]); }
				if (arrived && v[4] > 0 && v[5] == 9) standing = true;
			}
			FractalBuildingsMod.LOGGER.info("[TPHOLD15] surface {} (start y {}), lowest y {}, min health {}, held {}, final: {}", base, base + 2, lowest, hpMin, everHeld, last);
			FractalBuildingsMod.LOGGER.info("[TPHOLD15] release: {}; after it {} samples (0.5 s) with the 3 x 3 under the player not FULL, worst {}/9", released.get(), gapSamples, worstFull);
			if (released.get() == null || !released.get().contains("chunksFull=false")) throw new IllegalStateException("the player must land with the destination not FULL, was " + released.get());
			if (!standing) throw new IllegalStateException("the player never ended up standing on loaded ground: " + last);
			if (lowest < base - 3) throw new IllegalStateException("the player sank below the terrain (" + lowest + " < " + base + "): " + last);
			context.takeScreenshot("teleport-hold-metropolis");
		}
	}
}
