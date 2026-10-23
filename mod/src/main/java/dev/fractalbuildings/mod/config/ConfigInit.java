package dev.fractalbuildings.mod.config;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.world.level.Level;

public final class ConfigInit {
	private ConfigInit() {}

	public static void register() {
		FbConfig.ensureFile();
		FbCommands.register();
		// pin as the overworld loads; WorldConfigData.get also pins lazily, so a reader that comes first is safe too
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD) WorldConfigData.get(server);
		});
	}
}
