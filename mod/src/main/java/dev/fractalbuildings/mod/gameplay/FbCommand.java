package dev.fractalbuildings.mod.gameplay;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.PlacesIndex;
import dev.fractalbuildings.core.world.PlaytestNote;
import dev.fractalbuildings.mod.api.Settlements;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
				.then(Commands.literal("places").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> places(c.getSource(), PLACES_RADIUS, null, null))
						.then(Commands.argument("radius", IntegerArgumentType.integer(16, PLACES_MAX_RADIUS))
								.executes(c -> places(c.getSource(), IntegerArgumentType.getInteger(c, "radius"), null, null))
								.then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
										.executes(c -> places(c.getSource(), IntegerArgumentType.getInteger(c, "radius"), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z")))))))
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

	/** Default and largest radius of {@code /fb places}, blocks. */
	static final int PLACES_RADIUS = 256, PLACES_MAX_RADIUS = 4096;

	/** File the index is written to, relative to the world folder. */
	public static final String PLACES_FILE = "fractalbuildings/places.json";

	/** Writes the places index for the square around (x, z) to {@code <world>/fractalbuildings/places.json}; returns the index. */
	public static java.util.Map<String, Object> writePlaces(WorldPlanner w, Path worldRoot, int x, int z, int radius) throws IOException {
		java.util.Map<String, Object> index = PlacesIndex.build(w, x, z, radius);
		Path file = worldRoot.resolve(PLACES_FILE);
		Files.createDirectories(file.getParent());
		Files.writeString(file, PlaytestNote.json(index), StandardCharsets.UTF_8);
		return index;
	}

	private static int places(CommandSourceStack s, int radius, Integer x, Integer z) {
		Optional<WorldPlanner> w = planner(s);
		if (w.isEmpty()) return 0;
		int cx = x != null ? x : (int) Math.floor(s.getPosition().x()), cz = z != null ? z : (int) Math.floor(s.getPosition().z());
		Path root = s.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		java.util.Map<String, Object> m;
		try {
			m = writePlaces(w.get(), root, cx, cz, radius);
		} catch (IOException e) {
			s.sendFailure(Component.literal("Could not write " + PLACES_FILE + ": " + e));
			return 0;
		}
		@SuppressWarnings("unchecked") var sets = (java.util.List<java.util.Map<String, Object>>) m.get("settlements");
		s.sendSuccess(() -> Component.literal("Places within " + radius + " blocks of " + cx + "," + cz + ": " + sets.size() + " settlement(s), written to " + PLACES_FILE), false);
		return 1;
	}
}
