package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

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

	private RockVeto() {}

	/** The cells a boulder whose origin is {@code origin} AFTER the descent can write, grown by one block (x, z: origin-4..+2, y: origin-4..+2). */
	public static Box3 box(BlockPos origin) {
		return new Box3(origin.getX() - 4, origin.getY() - 4, origin.getZ() - 4, 7, 7, 7);
	}

	/**
	 * Replays vanilla's descent (ROCKVETO2): {@code while (y > minY + 3 && !canPlaceOn.test(level, pos.below())) pos = pos.below()}. The
	 * origin comes from the MOTION_BLOCKING heightmap, so it can start on a leaf canopy or above deep water and sink without bound; the box
	 * must be taken at the origin the blob really uses. Chosen over extending the box down to the terrain probe's surface: it reads exactly
	 * the blocks vanilla reads (same predicate, same cells), so the answer is the vanilla position whatever the neighbours already placed,
	 * and a fixed-depth box would either miss a deep drop or veto every boulder near a claim. Cost: one read per block of descent.
	 * Returns a position at or below {@code minY + 3} when vanilla gives up (it then places nothing).
	 */
	public static BlockPos descend(BlockPos origin, int minY, Predicate<BlockPos> canPlaceBelow) {
		BlockPos p = origin;
		while (p.getY() > minY + 3 && !canPlaceBelow.test(p.below())) p = p.below();
		return p;
	}

	/** True when the boulder at {@code origin} must not be placed. Failures place it (vanilla) and count in {@link WorldPlanners#FAILURES}. */
	public static boolean vetoes(WorldGenLevel level, ChunkGenerator gen, BlockPos origin, BlockPredicate canPlaceOn) {
		if (Mutations.is(Mutations.ROCKS) || level.getLevel().dimension() != Level.OVERWORLD) return false;
		Optional<WorldPlanner> planner = WorldPlanners.of(level.getLevel(), gen);
		if (planner.isEmpty()) return false;
		return vetoes(planner.get(), level.getMinY(), origin, p -> canPlaceOn.test(level, p));
	}

	/** The decision with the descent replayed: the box is taken at the origin vanilla ends up with. */
	public static boolean vetoes(WorldPlanner planner, int minY, BlockPos origin, Predicate<BlockPos> canPlaceBelow) {
		if (Mutations.is(Mutations.ROCKS)) return false;
		BlockPos landed = descend(origin, minY, canPlaceBelow);
		if (landed.getY() <= minY + 3) return false; // vanilla places nothing
		return vetoes(planner, landed);
	}

	/** The pure decision on the post-descent origin (no level, no dimension): the {@code ROCKS} mutant, the claim test and the counters. */
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
