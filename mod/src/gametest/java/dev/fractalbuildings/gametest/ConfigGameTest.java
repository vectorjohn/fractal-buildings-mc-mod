package dev.fractalbuildings.gametest;

import dev.fractalbuildings.core.world.FbSettings;
import dev.fractalbuildings.mod.config.FbCommands;
import dev.fractalbuildings.mod.config.WorldConfigData;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.SavedDataStorage;

/** B6: the config is pinned per world; a changed file does not change an existing world. */
// gametest-order: 2
public class ConfigGameTest {
	static FbSettings file(String json) { return FbSettings.parse(json, m -> {}); }

	@GameTest
	public void pinnedValuesSurviveAFileChangeAndAReload(GameTestHelper helper) throws Exception {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("fb-config-test");
		try {
			FbSettings first = file("{\"maxRadius\": 2048, \"frequency\": 0.5, \"styles\": [\"desert\", \"plain\"], \"villagers\": false}");
			FbSettings second = file("{\"maxRadius\": 4096}");
			// world creation: the file says 2048
			try (SavedDataStorage a = new SavedDataStorage(dir, server.getFixerUpper(), server.registryAccess())) {
				WorldConfigData d = a.computeIfAbsent(WorldConfigData.type(() -> first));
				if (!d.settings().equals(first)) helper.fail("not copied from the file");
				a.saveAndJoin();
			}
			// reload with the file changed to 4096: the world keeps its own values
			try (SavedDataStorage b = new SavedDataStorage(dir, server.getFixerUpper(), server.registryAccess())) {
				WorldConfigData d = b.computeIfAbsent(WorldConfigData.type(() -> second));
				if (!d.settings().equals(first)) helper.fail("reload gave " + d.settings() + " instead of " + first);
				if (d.settings().world().maxRadius() != 2048) helper.fail("maxRadius changed");
			}
			// the running world has pinned something too, and /fb config prints it
			WorldConfigData live = WorldConfigData.get(server);
			if (!FbCommands.describe(live.settings()).contains("maxRadius " + live.settings().world().maxRadius())) helper.fail("describe");
			if (live != WorldConfigData.get(server)) helper.fail("not cached");
		} finally {
			try (var s = Files.walk(dir)) { s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); }
		}
		helper.succeed();
	}
}
