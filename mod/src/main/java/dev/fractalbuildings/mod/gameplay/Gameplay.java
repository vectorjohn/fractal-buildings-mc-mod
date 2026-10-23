package dev.fractalbuildings.mod.gameplay;

import dev.fractalbuildings.mod.config.WorldConfigData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.world.level.Level;

/** Gameplay hooks (plan B5): furniture blocks, chests with loot and villagers for the planner's hooks, and the {@code /fb} subcommands. */
public final class Gameplay {
	/** The world's pinned {@code villagers} setting, read as the overworld loads (worldgen threads must not touch the saved data). */
	private static volatile boolean villagers = true;

	private Gameplay() {}

	public static void register() {
		FbCommand.register();
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD) villagers = WorldConfigData.get(server).settings().villagers();
		});
	}

	public static boolean villagersEnabled() { return villagers; }

	/** Tests: switch villagers without touching the saved settings. */
	public static void setVillagersForTests(boolean on) { villagers = on; }
}
