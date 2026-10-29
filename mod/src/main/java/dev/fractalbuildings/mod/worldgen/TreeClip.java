package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.trunkplacers.DarkOakTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.GiantTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;

/**
 * A vanilla tree's room check does not see our pieces (LEAFORDER, 2026-10-29).
 *
 * <p>{@code TreeFeature.doPlace} (26.3 decompiled, TreeFeature.java:105-107) asks {@code getMaxFreeTreeHeight} (lines 125-147) how tall the
 * tree may grow: every cell of a column round the trunk ({@code minimumSize.getSizeAtHeight}: radius 0 to 2, from the base to one above the
 * top) must be free ({@code TrunkPlacer.isFree}: air, a block in {@code #replaceable_by_trees} or a log, and no vine unless
 * {@code ignore_vines}); the first cell that is not cuts the tree to that height minus 2, and without a {@code min_clipped_height} (spruce,
 * oak, birch, dark oak) a cut tree is not placed at all. Those cells reach up to 2 blocks into the next chunk. When the next chunk has
 * already placed our pieces there (a wall, a roof, a street fill), the tree is not placed; when it decorates later, the tree stands whole and
 * our pieces only replace the cells they claim. So a whole crown beside a building or over a street came and went with chunk order
 * (FoliageClientGameTest: 268, 336 or 376 leaves over the seed 1 street; ORDERSIM {@code TreeClipOrderSimTest}).
 *
 * <p>Fix: in every cell that {@link WorldPlanner#claimRegion} says our pieces or roads may claim, the check reads the natural terrain
 * ({@link #naturallyFree}: free from the noise floor up) instead of the world, which holds our blocks or not by chunk order. That is what
 * the check sees when the tree comes first (up to vanilla features in those cells); the tree then writes logs and leaves only where
 * {@code validTreePos} allows, our pieces overwrite what they claim, and {@code SpillClear} / {@code BuildingAirClear} /
 * {@code FoliageClear} clean up as for any tree that came first. Trees away from our claims are untouched (empty region: vanilla's check).
 * Rejected: counting the region as free (the first version). Beside a road the region is 13 blocks wide and reaches far up, so trees grew
 * against hillsides vanilla clips them on and were then cut by the road (a 6-leaf orphan over the seed 31337 road in every run).
 *
 * <p>Not covered (no evidence they matter yet): other features with a room check that reads a neighbour chunk (huge mushrooms,
 * fallen trees), vanilla features inside the region that the natural terrain does not show, and vines that {@code FoliageClear} removed
 * outside our claims before a tree that minds vines checks its room.
 */
public final class TreeClip {
	/** Trees whose height this rule changed from what the world would have given (diagnostics). */
	public static final AtomicLong CHANGED = new AtomicLong();
	/** Room checks that met a claim region. */
	public static final AtomicLong NEAR = new AtomicLong();
	/** Trees not placed because a 2 x 2 trunk reaches into a building of another chunk ({@link #trunkVetoed}) that vanilla would have placed now. */
	public static final AtomicLong VETOED = new AtomicLong();

	private TreeClip() {}

	/** Off under the {@code trees} mutant. */
	public static boolean enabled() { return !Mutations.is(Mutations.TREES); }

	/** What the check reads: whether a cell is free for the tree's trunk placer (vanilla's {@code isFree} and vine test). */
	public interface Free { boolean free(BlockPos pos); }

	/** Whether a cell of the claim region counts as free: at or above the natural floor (the noise terrain, before any feature or piece). */
	public static boolean naturallyFree(WorldPlanner planner, int x, int y, int z) { return y >= planner.naturalFloorY(x, z); }

	/** The cells {@code getMaxFreeTreeHeight} reads for a tree of {@code maxTreeHeight} at {@code treePos} with a widest radius {@code r}, and the row under the trunk. */
	public static Box3 envelope(BlockPos treePos, int maxTreeHeight, int r) {
		return new Box3(treePos.getX() - r, treePos.getY() - 1, treePos.getZ() - r, 2 * r + 1, maxTreeHeight + 3, 2 * r + 1);
	}

	/** {@link #envelope} with the tree's own widest radius (at least 1: a 2 x 2 trunk's base). */
	public static Box3 room(TreeFeature tree, int maxTreeHeight, BlockPos treePos) {
		int r = 1;
		for (int y = 0; y <= maxTreeHeight + 1; y++) r = Math.max(r, tree.minimumSize().getSizeAtHeight(maxTreeHeight, y));
		return envelope(treePos, maxTreeHeight, r);
	}

	/** Trunk placers with a 2 x 2 trunk (origin to origin + 1 in x and z) that put their below-trunk block under all four cells unchecked. */
	public static boolean twoByTwo(TrunkPlacer p) { return p instanceof GiantTrunkPlacer || p instanceof DarkOakTrunkPlacer; }

	/**
	 * A 2 x 2 trunk that reaches into a building of another chunk is not placed. {@code GiantTrunkPlacer} / {@code DarkOakTrunkPlacer}
	 * (26.3, lines 36-42) call {@code placeBelowTrunkBlock} under all four cells with no free test (TrunkPlacer.java:61-68), so a tree placed
	 * after our pieces writes dirt into a wall or a street's air; vanilla's own room check only reads the origin column at the base. With
	 * the natural terrain read in the region such a tree would stand in every order and put that dirt into our cells whenever the
	 * neighbour came first (ORDERSIM SC1, a dark oak at 1457,70,-9409, dirt in a white terracotta wall). Before LEAFORDER the wall clipped
	 * it in that order and it stood in the other. Not placing it is the same in every order. Only buildings: a base in a street's or road's
	 * cells was already placed in every order (street air is free) and {@code StreetAirFix} puts the planned block back under its trunk
	 * (SPILLDIRT2, ORDERSIM SC3).
	 */
	public static boolean trunkVetoed(WorldPlanner.ClaimRegion region, TreeFeature tree, int maxTreeHeight, BlockPos treePos) {
		if (!twoByTwo(tree.trunkPlacer())) return false;
		int ocx = treePos.getX() >> 4, ocz = treePos.getZ() >> 4;
		for (int dz = 0; dz <= 1; dz++) for (int dx = 0; dx <= 1; dx++) {
			int x = treePos.getX() + dx, z = treePos.getZ() + dz;
			if (x >> 4 == ocx && z >> 4 == ocz) continue;
			for (int y = treePos.getY() - 1; y <= treePos.getY() + maxTreeHeight + 1; y++) if (region.containsBuilding(x, y, z)) return true;
		}
		return false;
	}

	/**
	 * The mixin's entry: the height the tree may grow to, or null to let vanilla answer (no planner, not the overworld, no claim near, the
	 * {@code trees} mutant, or a failure).
	 */
	public static Integer maxFreeHeight(WorldGenLevel level, TreeFeature tree, int maxTreeHeight, BlockPos treePos) {
		if (!enabled()) return null;
		try {
			if (level.getLevel().dimension() != Level.OVERWORLD) return null;
			Optional<WorldPlanner> planner = WorldPlanners.of(level.getLevel(), level.getLevel().getChunkSource().getGenerator());
			if (planner.isEmpty()) return null;
			return maxFreeHeight(planner.get(), tree, maxTreeHeight, treePos, p -> tree.trunkPlacer().isFree(level, p) && (tree.ignoreVines() || !TreeFeature.isVine(level, p)));
		} catch (RuntimeException e) {
			WorldPlanners.FAILURES.incrementAndGet();
			FractalBuildingsMod.LOGGER.error("Could not check the room of the tree at {} against our pieces; vanilla decides", treePos, e);
			return null;
		}
	}

	/** The height vanilla's {@code doPlace} turns into "not placed" for every tree (the loop's answer when the base cell is taken). */
	public static final int NOT_PLACED = -2;

	/**
	 * The pure part: vanilla's loop, reading the natural terrain in the claim region; {@link #NOT_PLACED} for a {@link #trunkVetoed} tree.
	 * Null when no cell of the room is in the region.
	 */
	public static Integer maxFreeHeight(WorldPlanner planner, TreeFeature tree, int maxTreeHeight, BlockPos treePos, Free world) {
		if (!enabled()) return null;
		WorldPlanner.ClaimRegion region = planner.claimRegion(room(tree, maxTreeHeight, treePos));
		if (region.isEmpty()) return null;
		NEAR.incrementAndGet();
		int vanilla = loop(tree, maxTreeHeight, treePos, world);
		int ours = trunkVetoed(region, tree, maxTreeHeight, treePos) ? NOT_PLACED
			: loop(tree, maxTreeHeight, treePos, p -> region.contains(p.getX(), p.getY(), p.getZ()) ? naturallyFree(planner, p.getX(), p.getY(), p.getZ()) : world.free(p));
		if (ours == NOT_PLACED && vanilla != NOT_PLACED) VETOED.incrementAndGet();
		if (ours != vanilla) CHANGED.incrementAndGet();
		return ours;
	}

	/** {@code TreeFeature.getMaxFreeTreeHeight} (26.3, lines 125-147) over a {@link Free} test. */
	public static int loop(TreeFeature tree, int maxTreeHeight, BlockPos treePos, Free free) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = 0; y <= maxTreeHeight + 1; y++) {
			int r = tree.minimumSize().getSizeAtHeight(maxTreeHeight, y);
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					p.setWithOffset(treePos, x, y, z);
					if (!free.free(p)) return y - 2;
				}
			}
		}
		return maxTreeHeight;
	}
}
