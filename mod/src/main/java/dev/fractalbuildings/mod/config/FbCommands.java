package dev.fractalbuildings.mod.config;

import com.mojang.brigadier.CommandDispatcher;
import dev.fractalbuildings.core.world.FbSettings;
import dev.fractalbuildings.core.world.WorldConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /fb config}. Minimal on purpose: B5 owns {@code /fb} and extends it; Brigadier merges a second
 * {@code Commands.literal("fb")} registration into the same node, so B5 can register its own children without touching this class.
 */
public final class FbCommands {
	private FbCommands() {}

	public static void register() { CommandRegistrationCallback.EVENT.register((d, access, env) -> add(d)); }

	static void add(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("fb").then(Commands.literal("config").executes(ctx -> {
			String text = describe(WorldConfigData.get(ctx.getSource().getServer()).settings());
			ctx.getSource().sendSuccess(() -> Component.literal(text), false);
			return 1;
		})));
	}

	/** One line per option; what the world was created with, not the file's current content. */
	public static String describe(FbSettings s) {
		WorldConfig w = s.world();
		return "Fractal Buildings config of this world (pinned at creation):\n"
				+ "frequency " + w.frequency() + ", maxRadius " + w.maxRadius() + ", cellSize " + w.cellSize() + "\n"
				+ "styles " + (w.styles().isEmpty() ? "all" : w.styles()) + ", villagers " + s.villagers()
				+ ", vetoVanillaVillages " + s.vetoVanillaVillages() + ", intercityRoads " + w.intercityRoads() + ", genVersion " + w.genVersion();
	}
}
