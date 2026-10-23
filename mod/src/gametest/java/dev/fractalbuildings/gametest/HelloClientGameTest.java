package dev.fractalbuildings.gametest;

import dev.fractalbuildings.mod.HelloHut;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Client game test: opens a fixed-seed singleplayer world, places the hello hut in front of the
 * player, waits for chunks to render and takes a screenshot (build/run/clientGameTest/screenshots/).
 */
public class HelloClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("HelloClientGameTest")) return;
		try (TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(creator -> creator.setSeed("4242"))
				.create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("tp @p 0 -59 -9 0 12");
			world.getServer().runOnServer(server -> {
				Level level = server.overworld();
				// flat world surface is y=-61 (grass at -61, so air starts at -60)
				HelloHut.place(level, new BlockPos(-1, -60, 0));
			});
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("hello-hut");
		}
	}
}
