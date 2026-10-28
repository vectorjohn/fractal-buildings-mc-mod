package dev.fractalbuildings.mod.worldgen;

import com.mojang.brigadier.CommandDispatcher;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Plans ahead of moving players (plan B4, P2). Once a second, for each player in the overworld, the workers {@code warm} the region and
 * the road networks around the player and around where the player will be in {@link #LEAD_SECONDS} at the current speed, and
 * {@code prefetch} the tile under each of those points (its buildings, and the plans of its neighbours). All of it fills pure caches:
 * it changes how long a chunk takes, never what it contains (ADR 0003 D6; the game test compares a warmed and a cold world).
 *
 * <p>At most one warming task per player is queued or running, so a slow warm (a metropolis' skeletons take seconds) cannot pile up
 * behind itself.
 */
public final class RegionWarmer {
	/** Ticks between two warms of one player. */
	static final int PERIOD = 20;
	/** How far ahead along the current velocity the second warm point lies, seconds. Elytra at 60 b/s reaches 300 blocks. */
	static final double LEAD_SECONDS = 5;
	/** Longest lead, blocks. */
	static final double MAX_LEAD = 600;

	private static final Map<UUID, State> STATE = new ConcurrentHashMap<>();

	private static final class State {
		double lastX, lastZ;
		boolean seen;
		volatile boolean busy;
		long lastTile = Long.MIN_VALUE, lastAhead = Long.MIN_VALUE;
	}

	private RegionWarmer() {}

	public static void register() {
		Workers.register();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % PERIOD != 0) return;
			Executor workers = Workers.pool();
			if (workers == null) return;
			ServerLevel level = server.getLevel(Level.OVERWORLD);
			if (level == null) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.level() != level) continue;
				tick(level, p.getUUID(), p.getX(), p.getZ(), workers);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % (PERIOD * 60) == 0) STATE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATE.clear());
		CommandRegistrationCallback.EVENT.register((d, access, env) -> addCommand(d));
	}

	/** One player's warm, called on the server thread once a second. */
	static boolean tick(ServerLevel level, UUID id, double x, double z, Executor workers) {
		Optional<WorldPlanner> wp = WorldPlanners.of(level, level.getChunkSource().getGenerator());
		if (wp.isEmpty()) return false;
		return warmPlayer(wp.get(), id, x, z, workers);
	}

	/** The warm of one player on this planner (tests call it directly); false when the player's previous warm is still busy or the pool is full. */
	public static boolean warmPlayer(WorldPlanner planner, UUID id, double x, double z, Executor workers) {
		State s = STATE.computeIfAbsent(id, k -> new State());
		double vx = s.seen ? x - s.lastX : 0, vz = s.seen ? z - s.lastZ : 0; // blocks per second (one call a second)
		s.lastX = x;
		s.lastZ = z;
		s.seen = true;
		double len = Math.hypot(vx, vz), lead = Math.min(len * LEAD_SECONDS, MAX_LEAD);
		double ax = len < 1e-6 ? x : x + vx / len * lead, az = len < 1e-6 ? z : z + vz / len * lead;
		return submit(planner, s, (int) Math.floor(x), (int) Math.floor(z), (int) Math.floor(ax), (int) Math.floor(az), workers);
	}

	/** Queues the warm of the player's point and the point ahead; a no-op while the player's previous warm is still queued or running. */
	static boolean submit(WorldPlanner planner, State s, int x, int z, int ax, int az, Executor workers) {
		if (s.busy) return false;
		s.busy = true;
		LaneStats.warmBegin(); // counted from the queue: a queued warm is the warmer's too (the perf settle tells it from building work)
		try {
			workers.execute(() -> {
				long t0 = System.nanoTime();
				try {
					int radius = 2 * planner.config().cellSize();
					warmAt(planner, s, x, z, radius, false);
					if (Thread.currentThread().isInterrupted()) return; // the pool is stopping
					warmAt(planner, s, ax, az, radius, true);
				} catch (RuntimeException e) {
					FractalBuildingsMod.LOGGER.debug("Warming around {},{} failed; the chunks will compute what they need", x, z, e);
				} finally {
					LaneStats.warm(System.nanoTime() - t0);
					s.busy = false;
				}
			});
			return true;
		} catch (RuntimeException refused) {
			s.busy = false;
			LaneStats.warmAbort();
			LaneStats.refused();
			return false;
		}
	}

	private static void warmAt(WorldPlanner planner, State s, int x, int z, int radius, boolean ahead) {
		planner.warm(x, z, radius);
		planner.warmQuays(x, z, radius); // METROFILL R1: split from warm() so the region-layer budget row stays the region layer
		DistrictGrid.Tile t = planner.grid().tileAt(x, z);
		long key = ((long) t.ix() << 32) ^ (t.iz() & 0xffffffffL);
		if ((ahead ? s.lastAhead : s.lastTile) == key) return;
		if (ahead) s.lastAhead = key; else s.lastTile = key;
		if (planner.tile(t.ix(), t.iz()).isPresent()) {
			LaneStats.prefetch();
			planner.prefetch(t.ix(), t.iz());
		}
	}

	/**
	 * Warms one point now (the teleport hold, TPHOLD): resolves the region and road networks around it on a worker and starts the
	 * prefetch of the tile under it. The future completes when the warm is done; at once if the level has no planner or the pool is
	 * missing or full (the chunks then compute what they need), and also after a failure. Call from the server thread.
	 */
	public static java.util.concurrent.CompletableFuture<Void> warmPoint(ServerLevel level, int x, int z) {
		Optional<WorldPlanner> wp = WorldPlanners.of(level, level.getChunkSource().getGenerator());
		Executor workers = Workers.pool();
		if (wp.isEmpty() || workers == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
		return warmPoint(wp.get(), x, z, workers);
	}

	/** {@link #warmPoint(ServerLevel, int, int)} on a given planner and pool (tests call it directly). */
	public static java.util.concurrent.CompletableFuture<Void> warmPoint(WorldPlanner planner, int x, int z, Executor workers) {
		java.util.concurrent.CompletableFuture<Void> done = new java.util.concurrent.CompletableFuture<>();
		try {
			workers.execute(() -> {
				try {
					warmAt(planner, newState(), x, z, 2 * planner.config().cellSize(), false);
				} catch (RuntimeException e) {
					FractalBuildingsMod.LOGGER.debug("Warming point {},{} failed; the chunks will compute what they need", x, z, e);
				} finally {
					done.complete(null);
				}
			});
		} catch (RuntimeException refused) {
			LaneStats.refused();
			done.complete(null);
		}
		return done;
	}

	/** Test hook: a warm state for {@link #submit}. */
	static State newState() { return new State(); }

	private static void addCommand(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("fb").then(Commands.literal("lane").executes(ctx -> {
			String text = LaneStats.report();
			ctx.getSource().sendSuccess(() -> Component.literal(text), false);
			return 1;
		})));
	}
}
