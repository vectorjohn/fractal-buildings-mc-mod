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
 * the road networks around the player and around where the player will be in {@link #leadBlocks} at the current speed (longer the faster the player moves), and
 * {@code prefetch} the tile under each of those points (its buildings, and the plans of its neighbours). All of it fills pure caches:
 * it changes how long a chunk takes, never what it contains (ADR 0003 D6; the game test compares a warmed and a cold world).
 *
 * <p>At most one warming task per player is queued or running, so a slow warm (a metropolis' skeletons take seconds) cannot pile up
 * behind itself.
 */
public final class RegionWarmer {
	/** Ticks between two warms of one player. */
	static final int PERIOD = 20;
	/** How far ahead along the current velocity the second warm point lies, seconds, for a walker (at or below {@link #SLOW_SPEED}). */
	static final double LEAD_SECONDS = 5;
	/** The lead in seconds for a fast flyer (at or above {@link #FAST_SPEED}); in between it grows linearly (METROWARM: an elytra flight at 60 b/s warms 1800 blocks ahead, not 300). */
	static final double FAST_LEAD_SECONDS = 30;
	/** Speeds, blocks per second, below which the lead is {@link #LEAD_SECONDS} and above which it is {@link #FAST_LEAD_SECONDS}. */
	static final double SLOW_SPEED = 10, FAST_SPEED = 40;
	/** Longest lead, blocks (fireworks elytra tops out near 60 b/s). */
	static final double MAX_LEAD = 1800;

	/** The look-ahead distance in blocks for a speed in blocks per second: grows with speed, capped at {@link #MAX_LEAD}. Pure. */
	static double leadBlocks(double speed) {
		double f = Math.max(0, Math.min(1, (speed - SLOW_SPEED) / (FAST_SPEED - SLOW_SPEED)));
		return Math.min(speed * (LEAD_SECONDS + (FAST_LEAD_SECONDS - LEAD_SECONDS) * f), MAX_LEAD);
	}

	/** {@code -Dfractalbuildings.quayTrace=whole}: warm the whole city's quay trace again (METROFILL R1 behaviour); changes no output, only timing. */
	static final boolean WHOLE_QUAYS = "whole".equals(System.getProperty("fractalbuildings.quayTrace"));

	private static final Map<UUID, State> STATE = new ConcurrentHashMap<>();

	private static final class State {
		double lastX, lastZ;
		boolean seen;
		volatile boolean busy;
		long lastTile = Long.MIN_VALUE, lastAhead = Long.MIN_VALUE;
		/** The tile whose neighbours were last queued, for the player's own point and the point ahead; a neighbour task is queued only when it changes. */
		long lastNeighbours = Long.MIN_VALUE, lastNeighboursAhead = Long.MIN_VALUE;
		/** A neighbour-plan task of this player is queued or running: at most one at a time. */
		final java.util.concurrent.atomic.AtomicBoolean neighboursBusy = new java.util.concurrent.atomic.AtomicBoolean();
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
		double len = Math.hypot(vx, vz), lead = leadBlocks(len);
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
					warmAt(planner, s, x, z, radius, false, workers, false);
					if (Thread.currentThread().isInterrupted()) return; // the pool is stopping
					warmAt(planner, s, ax, az, radius, true, workers, false);
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

	/**
	 * One warm of a point. {@code held}: a teleport hold waits for it (CASCADE A4), so only the destination settlement and the ones it links
	 * to are warmed, the tile's plan is made, and the neighbour tiles are left to the full warm that follows the release. Otherwise the
	 * neighbours of the tile are planned on the workers once the tile's own plan is done (the chunks around it read their ground), in one
	 * fire-and-forget task, so nothing here waits for them. Fills pure caches only.
	 */
	private static void warmAt(WorldPlanner planner, State s, int x, int z, int radius, boolean ahead, Executor workers, boolean held) {
		// QUAYLAZY: no whole-city quay trace (20 s of worker time on a metropolis, for quays the player may never reach); the point's own
		// tile is planned below, which traces only the quays near it, on the reads pool (warm still runs the road links beside the junction fields)
		planner.warm(x, z, radius, WHOLE_QUAYS, held);
		DistrictGrid.Tile t = planner.grid().tileAt(x, z);
		if (planner.tile(t.ix(), t.iz()).isPresent()) planner.plan(t.ix(), t.iz()); // the destination tile's plan, so a hold on warmPoint ends with its quays done
		long key = ((long) t.ix() << 32) ^ (t.iz() & 0xffffffffL);
		if (!held) planNeighbours(planner, s, key, ahead, t.ix(), t.iz(), workers);
		if ((ahead ? s.lastAhead : s.lastTile) == key) return;
		if (ahead) s.lastAhead = key; else s.lastTile = key;
		if (planner.tile(t.ix(), t.iz()).isPresent()) {
			LaneStats.prefetch();
			planner.prefetch(t.ix(), t.iz());
		}
	}

	/**
	 * Queues the plans of the 8 tiles around (ix, iz), which exist, as one task of the workers; only when the player's tile changed since
	 * the last queued task (for this point: own or ahead) and none of this player's is still queued or running, so a player standing still,
	 * or flying fast over unplanned ground, cannot fill the pool's queue. A tile skipped while one runs is retried at the next warm.
	 * Skipped when the pool refuses or is stopping. Timing only: the plans are pure.
	 */
	private static void planNeighbours(WorldPlanner planner, State s, long key, boolean ahead, int ix, int iz, Executor workers) {
		if (workers == null || (ahead ? s.lastNeighboursAhead : s.lastNeighbours) == key || !planner.tile(ix, iz).isPresent()) return;
		if (!s.neighboursBusy.compareAndSet(false, true)) return;
		if (ahead) s.lastNeighboursAhead = key; else s.lastNeighbours = key;
		LaneStats.neighbourBegin(); // the perf settle counts it as the warmer's work, and waits for it
		try {
			workers.execute(() -> {
				long t0 = System.nanoTime();
				try {
					for (int dz = -1; dz <= 1; dz++) {
						for (int dx = -1; dx <= 1; dx++) {
							if ((dx != 0 || dz != 0) && planner.tile(ix + dx, iz + dz).isPresent()) planner.plan(ix + dx, iz + dz);
							if (Thread.currentThread().isInterrupted()) return;
						}
					}
				} catch (RuntimeException e) {
					FractalBuildingsMod.LOGGER.debug("Planning the neighbours of tile {},{} failed; the chunks will compute what they need", ix, iz, e);
				} finally {
					LaneStats.neighbourEnd(System.nanoTime() - t0);
					s.neighboursBusy.set(false);
				}
			});
		} catch (RuntimeException refused) {
			LaneStats.neighbourAbort();
			s.neighboursBusy.set(false);
			if (ahead) s.lastNeighboursAhead = Long.MIN_VALUE; else s.lastNeighbours = Long.MIN_VALUE;
			LaneStats.refused();
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
					State st = newState();
					int radius = 2 * planner.config().cellSize();
					try {
						warmAt(planner, st, x, z, radius, false, workers, true); // the hold waits for the destination and its links only
					} finally {
						done.complete(null);
					}
					if (!Thread.currentThread().isInterrupted()) warmAt(planner, st, x, z, radius, false, workers, false); // released: the rest of the region and the neighbour tiles
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
