package dev.fractalbuildings.mod.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.fractalbuildings.core.world.PlanLookup;
import dev.fractalbuildings.core.world.PlaytestNote;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * FBNOTE: the client-side {@code /fbnote <comment>} playtest command (client entrypoint only; nothing here is loaded on a dedicated server).
 * It takes a screenshot through the vanilla path ({@link Screenshot#grab}) into {@code screenshots/} and writes {@code <same name>.json}
 * beside it ({@link PlaytestNote}): the comment, time, seed, dimension, position, biome, the targeted block and, when the integrated server
 * runs in this process, the planner's view of the position ({@link PlanLookup}, read on the server thread). Off unless
 * {@link #enabled()}: then the command is not registered. How-to: docs/PLAYTEST.md.
 */
public final class FbNoteClient implements ClientModInitializer {
	public static final String PROPERTY = "fractalbuildings.debugNotes";
	static final String CONFIG = "fractalbuildings-client.properties";
	/** Client ticks between the command and the capture, so the chat screen has closed and a frame without it has been drawn. */
	static final int DELAY_TICKS = 3;

	private static String pendingText;
	private static int pendingTicks;

	@Override
	public void onInitializeClient() {
		if (!enabled()) return;
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> register(dispatcher, true));
		ClientTickEvents.END_CLIENT_TICK.register(FbNoteClient::tick);
		FractalBuildingsMod.LOGGER.info("[FBNOTE] /fbnote enabled");
	}

	/** The switch ({@link PlaytestNote#enabled}): {@code -Dfractalbuildings.debugNotes}, the development environment, or {@code debugNotes=true} in {@code config/fractalbuildings-client.properties}. */
	public static boolean enabled() {
		String cfg = null;
		Path p = FabricLoader.getInstance().getConfigDir().resolve(CONFIG);
		if (Files.exists(p)) {
			try (InputStream in = Files.newInputStream(p)) {
				Properties props = new Properties();
				props.load(in);
				cfg = props.getProperty("debugNotes");
			} catch (IOException e) {
				FractalBuildingsMod.LOGGER.warn("Could not read {}: {}", p, e.toString());
			}
		}
		return PlaytestNote.enabled(FabricLoader.getInstance().isDevelopmentEnvironment(), System.getProperty(PROPERTY), cfg);
	}

	/** Registers {@code /fbnote <text>} on the dispatcher when {@code on}; tests pass false to check that the command is absent. */
	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, boolean on) {
		if (!on) return;
		dispatcher.register(ClientCommands.literal("fbnote").then(ClientCommands.argument("comment", StringArgumentType.greedyString()).executes(c -> {
			String text = StringArgumentType.getString(c, "comment");
			pendingText = text;
			pendingTicks = DELAY_TICKS;
			return 1;
		})));
	}

	private static void tick(Minecraft client) {
		if (pendingText == null) return;
		if (--pendingTicks > 0) return;
		String text = pendingText;
		pendingText = null;
		if (client.player == null || client.level == null) return;
		try {
			capture(client, text);
		} catch (RuntimeException e) {
			FractalBuildingsMod.LOGGER.error("[FBNOTE] failed", e);
			client.player.sendSystemMessage(Component.literal("fbnote failed: " + e));
		}
	}

	static void capture(Minecraft client, String text) {
		Player player = client.player;
		Level level = client.level;
		File dir = client.gameDirectory;
		File shots = new File(dir, Screenshot.SCREENSHOT_DIR);
		shots.mkdirs();
		String base = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").format(LocalDateTime.now());
		String name = base;
		for (int i = 1; new File(shots, name + ".png").exists() || new File(shots, name + ".json").exists(); i++) name = base + "_" + i;

		Map<String, Object> note = new LinkedHashMap<>();
		note.put("format", PlaytestNote.FORMAT);
		note.put("comment", text);
		note.put("screenshot", name + ".png");
		note.put("time", Instant.now().toString());
		note.put("gameTime", level.getGameTime());
		MinecraftServer server = client.getSingleplayerServer();
		note.put("seed", server == null ? null : server.overworld().getSeed());
		note.put("dimension", level.dimension().identifier().toString());
		BlockPos bp = player.blockPosition();
		Map<String, Object> pos = new LinkedHashMap<>();
		pos.put("x", player.getX());
		pos.put("y", player.getY());
		pos.put("z", player.getZ());
		pos.put("yaw", player.getYRot());
		pos.put("pitch", player.getXRot());
		note.put("position", pos);
		note.put("biome", level.getBiome(bp).unwrapKey().map(k -> k.identifier().toString()).orElse(null));
		if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos t = hit.getBlockPos();
			Map<String, Object> target = new LinkedHashMap<>();
			target.put("block", BuiltInRegistries.BLOCK.getKey(level.getBlockState(t).getBlock()).toString());
			target.put("x", t.getX());
			target.put("y", t.getY());
			target.put("z", t.getZ());
			target.put("face", hit.getDirection().getName());
			note.put("target", target);
		} else {
			note.put("target", null);
		}
		Map<String, Object> mod = new LinkedHashMap<>();
		mod.put("version", FabricLoader.getInstance().getModContainer(FractalBuildingsMod.MOD_ID).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse(null));
		mod.put("commit", gitCommit());
		note.put("mod", mod);

		File json = new File(shots, name + ".json");
		final String shotName = name;
		Screenshot.grab(dir, shotName + ".png", client.gameRenderer.mainRenderTarget(), 1, msg -> client.execute(() -> {
			if (client.player != null) client.player.sendSystemMessage(Component.literal("fbnote saved " + shotName + " (.png, .json)"));
		}));
		if (server == null) {
			Map<String, Object> pl = new LinkedHashMap<>();
			pl.put("available", false);
			pl.put("note", "planner data is only recorded in singleplayer (the integrated server runs in this process); this is a remote server");
			note.put("planner", pl);
			write(json, note);
			return;
		}
		int x = bp.getX(), y = bp.getY(), z = bp.getZ();
		var dim = level.dimension();
		// the planner is read on the server thread (tiles may be planned there); the file is written from it once the answer is in
		server.execute(() -> {
			Map<String, Object> pl = new LinkedHashMap<>();
			try {
				ServerLevel sl = server.getLevel(dim);
				Optional<WorldPlanner> planner = sl == null ? Optional.empty() : WorldPlanners.of(sl, sl.getChunkSource().getGenerator());
				if (planner.isPresent()) {
					pl.put("available", true);
					pl.putAll(PlanLookup.at(planner.get(), x, y, z));
				} else {
					pl.put("available", false);
					pl.put("note", "no planner in this dimension (structures off or not a Fractal Buildings world)");
				}
			} catch (RuntimeException e) {
				pl.put("available", false);
				pl.put("note", "planner lookup failed: " + e);
			}
			note.put("planner", pl);
			write(json, note);
		});
	}

	private static void write(File f, Map<String, Object> note) {
		try {
			Files.writeString(f.toPath(), PlaytestNote.json(note), StandardCharsets.UTF_8);
		} catch (IOException e) {
			FractalBuildingsMod.LOGGER.error("[FBNOTE] could not write {}: {}", f, e.toString());
		}
	}

	/** The checkout's HEAD in a development run (git next to the game directory); null elsewhere or when git is unavailable. */
	static String gitCommit() {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return null;
		try {
			Process p = new ProcessBuilder("git", "-C", FabricLoader.getInstance().getGameDir().toString(), "rev-parse", "--short=8", "HEAD")
					.redirectErrorStream(true).start();
			if (!p.waitFor(2, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return null;
			}
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			return p.exitValue() == 0 && out.matches("[0-9a-f]{7,40}") ? out : null;
		} catch (IOException | InterruptedException e) {
			return null;
		}
	}
}
