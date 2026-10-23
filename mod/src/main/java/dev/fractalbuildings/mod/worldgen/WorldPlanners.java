package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.probe.LiveProbes;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Finds the one {@link WorldPlanner} of a level from any thread (plan B1). Keyed by the level's {@link RandomState} (one per dimension and
 * world; start creation, {@code postProcess} and {@code /locate} all reach it). Not a weak map on purpose: the planner holds its probes
 * which hold the key, so a weak entry would never go; {@link #clear} runs when the server stops.
 *
 * <p>Only noise worlds with a multi-noise biome source get a planner (the live probes read those); any other generator (flat, debug,
 * a mod's custom one) has no settlements and says so once in the log.
 */
public final class WorldPlanners {
	private static final Map<RandomState, WorldPlanner> PLANNERS = new ConcurrentHashMap<>();
	private static final Map<RandomState, Boolean> UNSUPPORTED = new ConcurrentHashMap<>();

	private static volatile WorldPlanner override;
	/** The world's pinned config (B6's saved data), set as the overworld loads; null until then. */
	private static volatile WorldConfig pinned;
	/** Where a new planner gets its config; tests replace it. */
	private static volatile Supplier<WorldConfig> config = () -> {
		WorldConfig p = pinned;
		if (p != null) return p;
		FractalBuildingsMod.LOGGER.warn("A settlement planner is being created before the world's config was pinned; using the defaults");
		return WorldConfig.defaults();
	};
	/** Runs {@link WorldPlanner#prefetch}; null (the default) means none. B4 sets it. */
	private static volatile Executor workers;
	/** Exceptions swallowed by the structures (they log and place nothing instead of crashing chunk generation); tests assert 0. */
	public static final AtomicInteger FAILURES = new AtomicInteger();

	private WorldPlanners() {}

	/** The planner for a structure start (any thread). Empty when this world's generator is not a noise generator. */
	public static Optional<WorldPlanner> of(Structure.GenerationContext ctx) {
		return find(ctx.randomState(), ctx.chunkGenerator(), ctx.seed());
	}

	/** The planner for {@code postProcess}, which has the level and the generator but no generation context. */
	public static Optional<WorldPlanner> of(ServerLevel level, ChunkGenerator generator) {
		return find(level.getChunkSource().randomState(), generator, level.getSeed());
	}

	private static Optional<WorldPlanner> find(RandomState rs, ChunkGenerator gen, long seed) {
		WorldPlanner o = override;
		if (o != null) return Optional.of(o);
		WorldPlanner p = PLANNERS.get(rs);
		if (p != null) return Optional.of(p);
		if (!(gen instanceof NoiseBasedChunkGenerator noise) || !(noise.getBiomeSource() instanceof MultiNoiseBiomeSource)) {
			if (UNSUPPORTED.put(rs, true) == null) FractalBuildingsMod.LOGGER.info("No settlements in this world: the chunk generator {} is not a noise generator with a multi-noise biome source", gen.getClass().getSimpleName());
			return Optional.empty();
		}
		return Optional.of(PLANNERS.computeIfAbsent(rs, k -> create(noise, rs, seed)));
	}

	private static WorldPlanner create(NoiseBasedChunkGenerator gen, RandomState rs, long seed) {
		LiveProbes lp = new LiveProbes(gen, rs);
		WorldProbes probes = new WorldProbes(lp.region(), lp.coarse(32), lp.exact(), lp.biome());
		WorldConfig cfg = config.get();
		FractalBuildingsMod.LOGGER.info("Settlement planner created for seed {} (cell size {}, frequency {}, max radius {})", seed, cfg.cellSize(), cfg.frequency(), cfg.maxRadius());
		WorldPlanner planner = new WorldPlanner(seed, cfg, probes, workers, WorldPlanner.Caches.forHeap(Runtime.getRuntime().maxMemory()));
		LaneStats.attach(planner);
		return planner;
	}

	/** Tests: every level uses this planner (a fixture on any terrain). {@code null} restores the normal lookup. */
	public static void overrideForTests(WorldPlanner planner) { override = planner; }

	/** The overworld loaded: its saved settings are the planner's config from now on. */
	public static void pin(WorldConfig c) { pinned = c; }

	public static void setConfigSource(Supplier<WorldConfig> source) { config = source; }

	public static void setWorkers(Executor executor) { workers = executor; }

	/** Forgets every planner (server stopped, tests). The next question builds a new one. */
	public static void clear() {
		PLANNERS.clear();
		pinned = null;
		UNSUPPORTED.clear();
	}
}
