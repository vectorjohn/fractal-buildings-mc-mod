package dev.fractalbuildings.mod.teleport;

import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.worldgen.RegionWarmer;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import dev.fractalbuildings.mod.worldgen.Workers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Holds a long same-dimension teleport into cold terrain until the destination is generated (TPHOLD). Vanilla shows its loading screen
 * on join, respawn and dimension change, but a same-dimension {@code /tp} only moves the player and the client sees void until chunks
 * arrive. {@code ServerPlayerTeleportMixin} hands such a teleport here: the player stays put, the {@link RegionWarmer} warms the
 * destination, a ticket loads the destination chunk and its neighbours to FULL, an action-bar message says so, and the teleport is
 * replayed when they are ready or after {@link #timeoutMillis}. Server-only (unmodded clients work), asynchronous (polled once a
 * tick, never blocks), and it only changes timing: the generated blocks are the same.
 *
 * <p>Off with {@code -Dfractalbuildings.teleportHold=false}.
 */
public final class TeleportHold {
	/** Loads the destination chunks and keeps them; removed by hand when the hold ends, expires after a minute as a safety net. */
	public static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE,
		Identifier.fromNamespaceAndPath(FractalBuildingsMod.MOD_ID, "teleport_hold"), new TicketType(20L * 60, TicketType.FLAG_LOADING));

	/** Chunks around the destination chunk that must be FULL (1 = the 3 x 3 around it). */
	static final int RADIUS = 1;
	/** Jumps shorter than this are never held, whatever the view distance. */
	static final int MIN_DISTANCE = 64;
	/** The default of {@link #timeoutMillis}: 60 s (John 2026-11-01: "For the tp hold, 60s is fine"; was 90 s in TPHOLD15 r2; the hold still ends as soon as the 3 x 3 is FULL, so the cap only matters on cold metropolis arrivals, about 33 to 48 s on a quiet beebox after CASCADE). */
	public static final long DEFAULT_TIMEOUT_MILLIS = 60_000;
	/** Longest wait, milliseconds of wall clock (a lagging server still counts real time), before the teleport happens anyway. */
	public static volatile long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
	/** Jumps shorter than this many blocks are never held; null means the server's view distance. */
	public static volatile Integer minDistanceOverride = null;
	public static volatile boolean enabled = !"false".equals(System.getProperty("fractalbuildings.teleportHold"));
	/**
	 * TPHOLD15 option 3 (John: "hold for what the tile requires"): on a planner-backed level the hold ends once the destination tile is planned
	 * and warmed (or at the limit), without waiting for the 3 x 3 chunks. OFF by default: the teleport then places the player in a chunk that is
	 * not FULL yet, and vanilla loads it synchronously on the server thread, which freezes the whole server until it is generated (observed:
	 * about 40 s on beebox after a 15 s limit, see docs/log/2026-10-31-p6-tphold15.md). Players never fall or take damage. On with
	 * -Dfractalbuildings.teleportHoldReleaseOnPlan=true.
	 */
	public static volatile boolean releaseOnPlan = "true".equals(System.getProperty("fractalbuildings.teleportHoldReleaseOnPlan"));
	/** Test hook: replaces the "destination chunks are FULL" future. */
	public static volatile Supplier<CompletableFuture<?>> testLoad = null;
	/** Test hook: replaces the destination warm future and marks the level as planner-backed. */
	public static volatile Supplier<CompletableFuture<Void>> testWarm = null;
	/** Test hook: sees (player, destination chunk all FULL?, timed out?) at the moment a held teleport is replayed. */
	public static volatile Released testReleased = null;

	@FunctionalInterface
	public interface Released { void on(ServerPlayer player, boolean chunksFull, boolean timedOut); }

	private static final class Hold {
		final ServerPlayer player;
		TeleportTransition transition;
		ChunkPos pos;
		CompletableFuture<Void> warm;
		CompletableFuture<?> load;
		boolean ticketed;
		boolean planned; // a planner-backed level: the hold ends when the destination is planned, not when its chunks are FULL
		ServerLevel ticketLevel;
		Vec3 dest;
		int age;
		final long limitMillis = timeoutMillis;
		final long deadline = System.nanoTime() + limitMillis * 1_000_000L;
		final Supplier<CompletableFuture<?>> load0 = testLoad;
		final Released released = testReleased;

		Hold(ServerPlayer player, TeleportTransition transition) { this.player = player; this.transition = transition; }
	}

	private static final Map<UUID, Hold> HOLDS = new LinkedHashMap<>();
	private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);

	private TeleportHold() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HOLDS.clear());
	}

	public static boolean holding(ServerPlayer p) { return HOLDS.containsKey(p.getUUID()); }

	/**
	 * Called at the head of {@code ServerPlayer.teleport}. True when the teleport was taken over (the caller cancels it); false when it
	 * should go ahead: short hop, other dimension, destination already loaded, disabled, or our own replay.
	 */
	public static boolean intercept(ServerPlayer player, TeleportTransition t) {
		if (REPLAYING.get() || !enabled || player.isRemoved()) return false;
		Hold existing = HOLDS.remove(player.getUUID()); // any other teleport (also a portal) cancels the waiting one
		if (existing != null) end(existing);
		ServerLevel level = t.newLevel();
		if (level != player.level() || level.getServer() == null) return false;
		Vec3 d = destination(player, t);
		if (!needsHold(player, level, d)) return false;
		Hold h = new Hold(player, t);
		h.dest = d;
		h.pos = ChunkPos.containing(BlockPos.containing(d));
		Supplier<CompletableFuture<Void>> tw = testWarm;
		h.planned = tw != null || (releaseOnPlan && WorldPlanners.of(level, level.getChunkSource().getGenerator()).isPresent() && Workers.pool() != null);
		h.warm = tw != null ? tw.get() : RegionWarmer.warmPoint(level, (int) Math.floor(d.x), (int) Math.floor(d.z));
		HOLDS.put(player.getUUID(), h);
		player.sendOverlayMessage(message());
		return true;
	}

	/** Where the player ends up: relative axes (/tp ~10 ~ ~) are resolved against the player. */
	static Vec3 destination(ServerPlayer player, TeleportTransition t) {
		return PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(player), PositionMoveRotation.of(t), t.relatives()).position();
	}

	static boolean needsHold(ServerPlayer player, ServerLevel level, Vec3 dest) {
		int view = level.getServer().getPlayerList().getViewDistance();
		int min = minDistanceOverride != null ? minDistanceOverride : Math.max(MIN_DISTANCE, (view + 1) * 16);
		double dx = dest.x - player.getX(), dz = dest.z - player.getZ();
		if (dx * dx + dz * dz < (double) min * min) return false;
		return !loaded(level, ChunkPos.containing(BlockPos.containing(dest)));
	}

	/** True when the destination chunk and its neighbours are all FULL already. */
	static boolean loaded(ServerLevel level, ChunkPos c) {
		for (int dz = -RADIUS; dz <= RADIUS; dz++) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) if (level.getChunkSource().getChunkNow(c.x() + dx, c.z() + dz) == null) return false;
		}
		return true;
	}

	static Component message() { return Component.literal("Generating terrain…"); }

	private static void tick() {
		if (HOLDS.isEmpty()) return;
		for (Hold h : new ArrayList<>(HOLDS.values())) {
			ServerPlayer p = h.player;
			ServerLevel level = p.level() instanceof ServerLevel l ? l : null;
			if (p.isRemoved() || level == null || p.hasDisconnected() || p.isDeadOrDying() || level != h.transition.newLevel()) {
				end(h);
				HOLDS.remove(p.getUUID());
				continue;
			}
			h.age++;
			if (h.age % 20 == 0) p.sendOverlayMessage(message());
			if (!h.ticketed && h.warm.isDone()) {
				h.ticketed = true;
				Supplier<CompletableFuture<?>> test = h.load0;
				h.ticketLevel = level;
				h.load = test != null ? test.get() : level.getChunkSource().addTicketAndLoadWithRadius(TICKET, h.pos, RADIUS);
			}
			boolean ready = (h.load != null && h.load.isDone()) || (h.planned && h.warm.isDone());
			boolean timedOut = !ready && System.nanoTime() - h.deadline >= 0;
			if (!ready && !timedOut) continue;
			HOLDS.remove(p.getUUID());
			if (timedOut) FractalBuildingsMod.LOGGER.warn("Teleport hold for {} timed out after {} ms; teleporting anyway", p.getName().getString(), (System.nanoTime() - h.deadline) / 1_000_000L + h.limitMillis);
			Released r = h.released;
			if (r != null) r.on(p, loaded(level, h.pos), timedOut);
			REPLAYING.set(true);
			try {
				p.teleport(h.transition);
			} finally {
				REPLAYING.set(false);
			}
			p.sendOverlayMessage(Component.empty());
			end(h);
		}
	}

	/** Drops the ticket, if one was taken. */
	private static void end(Hold h) {
		if (h.ticketed && h.ticketLevel != null) h.ticketLevel.getChunkSource().removeTicketWithRadius(TICKET, h.pos, RADIUS);
	}

	/** The resolved destination of the player's waiting teleport, or null. */
	public static Vec3 destination(ServerPlayer p) {
		Hold h = HOLDS.get(p.getUUID());
		return h == null ? null : h.dest;
	}

	/** Test hook: true while the hold of this player still owns its ticket. */
	public static boolean ticketHeld(ServerPlayer p) {
		Hold h = HOLDS.get(p.getUUID());
		return h != null && h.ticketed;
	}
}
