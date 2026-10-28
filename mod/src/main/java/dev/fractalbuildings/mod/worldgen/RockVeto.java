package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * Keeps vanilla boulders off our pieces (ROCKVETO, 2026-10-28). {@code BlockBlobFeature.place} (26.3 bytecode) first lowers its origin
 * while {@code can_place_on} fails below it, then for three rounds sets every cell of the box {@code +-i,j,k} (each 0 or 1) round the
 * origin whose distance is within a radius of at most 1.5 with {@code setBlockAndUpdate} (no replace check, no structure check), and
 * moves the origin by (-1..0, -1..0, -1..0) between rounds. Its cells therefore lie within x, z in [ox-3, ox+1] and y in [oy-3-descent,
 * oy+1]. A boulder whose origin chunk is decorated after the neighbour chunk that holds our street overwrites a street cell with mossy
 * cobblestone; one decorated before is overwritten by it. ORDERSIM S3 found it (SC1 6 x 6, a forest_rock over a street at 1439,64,-9405).
 * In the game it is {@code minecraft:forest_rock} in old growth pine and spruce taiga, which the taiga style builds cities in.
 *
 * <p>Covered: {@code BlockBlobFeature} only. The other 26.3 features checked with {@code javap} either test replaceability before they
 * write (tree and fallen tree via {@code validTreePos}, disks, ores, multiface growth, ice spike, iceberg) or are not in the overworld
 * (end platform, huge fungus). The mixin asks {@link #vetoes} first: a boulder whose box can meet a cell our districts or roads own is not
 * placed, in any order. A pure function of the origin and the world's planner.
 */
public final class RockVeto {
	/** Boulders not placed, for tests and logs. */
	public static final AtomicLong VETOED = new AtomicLong();
	private static final AtomicLong LOGGED = new AtomicLong();

	/** How far the origin may be lowered by the {@code can_place_on} search before the first blob; a deeper search is not covered. */
	public static final int DESCENT = 8;

	private RockVeto() {}

	/** The cells a boulder at {@code origin} (the feature's input position) can write, grown by one block, and the cells below that the descent can reach. */
	public static Box3 box(BlockPos origin) {
		return new Box3(origin.getX() - 4, origin.getY() - 4 - DESCENT, origin.getZ() - 4, 7, 6 + DESCENT, 7);
	}

	/** True when the boulder at {@code origin} must not be placed. Failures place it (vanilla) and count in {@link WorldPlanners#FAILURES}. */
	public static boolean vetoes(WorldGenLevel level, ChunkGenerator gen, BlockPos origin) {
		if (Mutations.is(Mutations.ROCKS) || level.getLevel().dimension() != Level.OVERWORLD) return false;
		Optional<WorldPlanner> planner = WorldPlanners.of(level.getLevel(), gen);
		if (planner.isEmpty()) return false;
		return vetoes(planner.get(), origin);
	}

	/** The pure decision (no level, no dimension): the {@code ROCKS} mutant, the claim test and the counters. */
	public static boolean vetoes(WorldPlanner planner, BlockPos origin) {
		if (Mutations.is(Mutations.ROCKS)) return false;
		try {
			if (!planner.claimsNear(box(origin))) return false;
		} catch (RuntimeException e) {
			WorldPlanners.FAILURES.incrementAndGet();
			FractalBuildingsMod.LOGGER.error("Could not tell whether the boulder at {} meets a settlement or road; placing it", origin, e);
			return false;
		}
		VETOED.incrementAndGet();
		if (LOGGED.incrementAndGet() <= 20) FractalBuildingsMod.LOGGER.info("[rock-veto] no boulder at {}: it would meet our pieces", origin);
		return true;
	}
}
