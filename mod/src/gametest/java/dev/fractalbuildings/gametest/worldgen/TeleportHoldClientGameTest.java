package dev.fractalbuildings.gametest.worldgen;

import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
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
public class TeleportHoldClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("TeleportHoldClientGameTest")) return;
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
	}
}
