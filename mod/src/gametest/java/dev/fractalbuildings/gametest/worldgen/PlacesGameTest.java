package dev.fractalbuildings.gametest.worldgen;

import dev.fractalbuildings.core.probe.SyntheticBiomes;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.mod.gameplay.FbCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * C15: {@code /fb places}. The gametest world is flat (no settlements), so the file is written for a planner on a synthetic plain (seed 1: a
 * hamlet near the origin, as in GameplayGameTest) into the world folder, twice, and must be identical and name the hamlet; the command itself
 * is checked on the flat world (registered, writes nothing and says why).
 */
// gametest-order: 7
public class PlacesGameTest {
	private static WorldPlanner planner(long seed) {
		WorldProbes pr = WorldProbes.of(SyntheticTerrain.flat(70), SyntheticBiomes.constant("minecraft:plains"));
		return new WorldPlanner(seed, WorldConfig.defaults().withIntercityRoads(false), pr, null);
	}

	@GameTest
	public void writesADeterministicIndexIntoTheWorldFolder(GameTestHelper helper) throws Exception {
		MinecraftServer server = helper.getLevel().getServer();
		Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		Path file = root.resolve(FbCommand.PLACES_FILE);
		Files.deleteIfExists(file);
		SettlementInfo near = planner(1).nearest(0, 0, Tier.HAMLET, 2).orElseThrow();
		int x = near.site().centre().x(), z = near.site().centre().z();
		FbCommand.writePlaces(planner(1), root, x, z, 128);
		String a = Files.readString(file);
		Files.delete(file);
		FbCommand.writePlaces(planner(1), root, x, z, 128);
		String b = Files.readString(file);
		Files.delete(file);
		if (!a.equals(b)) helper.fail("two runs gave different files");
		if (!a.contains("\"format\": \"fb-places/1\"") || !a.contains("\"" + near.site().id() + "\"")) helper.fail("index misses " + near.site().id() + ":\n" + a);
		if (!a.contains("\"districts\"") || !a.contains("\"lotsByType\"")) helper.fail("no districts in the index");

		var node = server.getCommands().getDispatcher().getRoot().getChild("fb").getChild("places");
		if (node == null) helper.fail("/fb places is not registered");
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "fb places 64");
		if (Files.exists(file)) helper.fail("the command wrote a file on a flat world");
		helper.succeed();
	}
}
