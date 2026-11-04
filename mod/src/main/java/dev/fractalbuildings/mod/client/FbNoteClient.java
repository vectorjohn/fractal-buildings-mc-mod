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
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
 * runs in this process, the planner's view of the position ({@link PlanLookup}, read on the server thread). On by default (FBNOTEON), in a release client too; off only when
 * {@link #enabled()} says so (then the command is not registered). Nothing here is dev-only except the git commit field. How-to: docs/PLAYTEST.md.
 */
public final class FbNoteClient implements ClientModInitializer {
	public static final String PROPERTY = "fractalbuildings.notes";
	/** The pre-FBNOTEON spelling of the property and of the config key {@code notes}; still read. */
	public static final String OLD_PROPERTY = "fractalbuildings.debugNotes";
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
		ClientReceiveMessageEvents.ALLOW_GAME.register(FbNoteClient::allowGame);
		FractalBuildingsMod.LOGGER.info("[FBNOTE] /fbnote enabled");
	}

	/** The switch ({@link PlaytestNote#enabled}): on unless {@code -Dfractalbuildings.notes=false} or {@code notes=false} in {@code config/fractalbuildings-client.properties} (old spelling {@code debugNotes}, also read). */
	public static boolean enabled() {
		String cfg = null;
		Path p = FabricLoader.getInstance().getConfigDir().resolve(CONFIG);
		if (Files.exists(p)) {
			try (InputStream in = Files.newInputStream(p)) {
				Properties props = new Properties();
				props.load(in);
				cfg = props.getProperty("notes", props.getProperty("debugNotes"));
			} catch (IOException e) {
				FractalBuildingsMod.LOGGER.warn("Could not read {}: {}", p, e.toString());
			}
		}
		String sys = System.getProperty(PROPERTY);
		if (sys == null || sys.isBlank()) sys = System.getProperty(OLD_PROPERTY);
		return PlaytestNote.enabled(sys, cfg);
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

	/** Client ticks to wait for the server's {@code /seed} reply before the note is written without a seed (about 2 s). */
	static final int SEED_TIMEOUT_TICKS = 40;
	static final String SEED_KEY = "commands.seed.success";
	private static final Pattern SEED_NUMBER = Pattern.compile("-?\\d+");
	/** Test hook: treat the world as a remote server even in singleplayer, so the {@code /seed} route can be tested without a dedicated server. */
	public static volatile boolean testForceRemote;

	private static Consumer<Long> seedWaiter;
	private static int seedTicks;

	/** The seed in the rendered text of vanilla's {@code /seed} reply ("Seed: [123]", any language: the seed is the only number); null when there is none. */
	static Long parseSeed(String rendered) {
		if (rendered == null) return null;
		Matcher m = SEED_NUMBER.matcher(rendered);
		if (!m.find()) return null;
		try {
			return Long.parseLong(m.group());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** True if the server's command tree offers {@code /seed} to this player, i.e. they have the permission (the server sends a player only the commands they may run). */
	static boolean canAskSeed(Minecraft client) {
		var conn = client.getConnection();
		return conn != null && conn.getCommands().getRoot().getChild("seed") != null;
	}

	/** Runs vanilla {@code /seed} and calls {@code done} with the seed, or with null on a timeout; the reply is kept out of the chat. */
	static void requestSeed(Minecraft client, Consumer<Long> done) {
		if (seedWaiter != null) seedWaiter.accept(null);
		seedWaiter = done;
		seedTicks = SEED_TIMEOUT_TICKS;
		client.getConnection().sendCommand("seed");
	}

	private static boolean allowGame(Component message, boolean overlay) {
		if (seedWaiter == null || overlay) return true;
		if (!(message.getContents() instanceof TranslatableContents tc) || !SEED_KEY.equals(tc.getKey())) return true;
		Long seed = parseSeed(message.getString());
		if (seed == null) return true;
		Consumer<Long> w = seedWaiter;
		seedWaiter = null;
		w.accept(seed);
		return false;
	}

	private static void tick(Minecraft client) {
		if (seedWaiter != null && --seedTicks <= 0) {
			Consumer<Long> w = seedWaiter;
			seedWaiter = null;
			w.accept(null);
		}
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
		MinecraftServer server = testForceRemote ? null : client.getSingleplayerServer();
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
		final boolean singleplayer = server != null;
		final String shotName = name;
		Screenshot.grab(dir, shotName + ".png", client.gameRenderer.mainRenderTarget(), 1, msg -> client.execute(() -> {
			if (client.player != null) client.player.sendSystemMessage(Component.literal("fbnote saved " + shotName + " (.png, .json)"
					+ (singleplayer ? "" : "; planner details (settlement, building, room) are singleplayer-only, so the note has position, biome and block (and the seed, for an operator)")));
		}));
		if (server == null) {
			Map<String, Object> pl = new LinkedHashMap<>();
			pl.put("available", false);
			pl.put("note", "planner data is only recorded in singleplayer (the integrated server runs in this process); this is a remote server");
			note.put("planner", pl);
			if (canAskSeed(client)) {
				// an operator: ask vanilla /seed (reply hidden from chat); the file is written when the reply or the timeout is in
				requestSeed(client, seed -> {
					note.put("seed", seed);
					write(json, note);
				});
			} else {
				write(json, note);
			}
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
