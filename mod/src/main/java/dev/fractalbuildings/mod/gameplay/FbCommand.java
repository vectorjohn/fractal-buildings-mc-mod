package dev.fractalbuildings.mod.gameplay;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.api.Settlements;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /fb info} and {@code /fb locate [tier]} (plan B5). Registered next to {@code /fb config} (Brigadier merges the two {@code literal("fb")}
 * registrations). {@code /locate} finds the nearest district, not the centre; {@code /fb locate} gives the settlement's centre.
 */
public final class FbCommand {
	/** How far {@code /fb locate} looks: region cells around the player (cells are 1024 blocks by default). */
	static final int SEARCH_CELLS = 8;

	private FbCommand() {}

	public static void register() { CommandRegistrationCallback.EVENT.register((d, access, env) -> add(d)); }

	static void add(CommandDispatcher<CommandSourceStack> d) {
		LiteralArgumentBuilder<CommandSourceStack> locate = Commands.literal("locate").executes(c -> locate(c.getSource(), Tier.HAMLET));
		for (Tier t : Tier.values()) locate.then(Commands.literal(t.json).executes(c -> locate(c.getSource(), t)));
		d.register(Commands.literal("fb")
				.then(Commands.literal("info").executes(c -> info(c.getSource())))
				.then(locate));
	}

	private static Optional<WorldPlanner> planner(CommandSourceStack s) {
		ServerLevel level = s.getLevel();
		Optional<WorldPlanner> p = WorldPlanners.of(level, level.getChunkSource().getGenerator());
		if (p.isEmpty()) s.sendFailure(Component.literal("This world has no settlements (its chunk generator is not a noise generator)"));
		return p;
	}

	/** The text {@code /fb info} prints for a position: a pure function of the planner so tests can call it. */
	public static String describe(WorldPlanner w, int x, int z) {
		Optional<Settlements.At> at = Settlements.at(w, x, z);
		if (at.isEmpty()) return "No settlement here";
		Settlements.At a = at.get();
		String lot = a.lotId() == null ? "no lot (street, plaza or open ground)" : "lot " + a.lotId() + ": " + a.buildingType() + ", style " + a.style() + ", " + a.hooks().size() + " hooks";
		return "Settlement " + a.settlementId() + " (" + a.tier().json + "), district tile " + a.tileX() + "," + a.tileZ() + ", " + lot;
	}

	private static int info(CommandSourceStack s) {
		Optional<WorldPlanner> w = planner(s);
		if (w.isEmpty()) return 0;
		String text = describe(w.get(), (int) Math.floor(s.getPosition().x()), (int) Math.floor(s.getPosition().z()));
		s.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int locate(CommandSourceStack s, Tier min) {
		Optional<WorldPlanner> w = planner(s);
		if (w.isEmpty()) return 0;
		int px = (int) Math.floor(s.getPosition().x()), pz = (int) Math.floor(s.getPosition().z());
		Optional<SettlementInfo> found = w.get().nearest(px, pz, min, SEARCH_CELLS);
		if (found.isEmpty()) {
			s.sendFailure(Component.literal("No " + min.json + " or larger settlement within " + SEARCH_CELLS + " region cells"));
			return 0;
		}
		SettlementInfo i = found.get();
		int x = i.site().centre().x(), z = i.site().centre().z();
		Component coords = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", x, "~", z)).withStyle(
				st -> st.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.SuggestCommand("/tp @s " + x + " ~ " + z))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip"))));
		s.sendSuccess(() -> Component.literal("The nearest " + i.tier().json + " (" + i.site().id() + ", radius " + i.site().radius() + ") is at ").append(coords)
				.append(" (" + Math.round(i.distance()) + " blocks away)"), false);
		return 1;
	}
}
