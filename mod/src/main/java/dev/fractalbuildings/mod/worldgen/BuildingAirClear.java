package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.WorldPlanner;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Takes a vanilla tree out of the air a building claims (TREEIN, ROADMAP 6.14: "Leaves in the house!"; also STEPBURY's door approaches).
 *
 * <p>Buildings are placed in the structure step of the last decoration step and trees in the features of the same or an earlier step, so a
 * tree of a neighbouring chunk that is decorated <i>after</i> the building's chunk puts its crown (and, from a trunk standing on a lot's
 * edge, its trunk) into any air cell, a room's included. Like {@link SpillClear} for streets, this pass runs in every chunk after that chunk's own
 * features ({@code RoadFeature.place}, the top-layer step) and rewrites, within {@link #MARGIN} blocks of the chunk, the cells that the
 * ledger gives a building as air: natural leaves (not persistent), vines and the logs of a tree cluster that touches natural leaves go.
 * A trunk reaches at most about 7 blocks out and the generation region allows writes one chunk out, so the chunk that decorates a tree
 * always covers the cells its tree can reach, and the end state does not depend on the order the chunks are generated in. Persistent
 * leaves (hedges, a building's own leaf blocks), furniture and every non-air cell stay: nothing the ledger gives a building as a solid
 * block changes. The remains of a tree the cuts leave hanging go to {@link FoliageClear}.
 *
 * <p>Only the planner is read (building pieces are computed for the tiles with a lot near the window; the tile of the chunk and its
 * neighbours are computed by the generation anyway) and nothing is recorded in the {@link PieceLedger}: the owner of a cell records it.
 */
public final class BuildingAirClear {
	/** How far past the chunk cells are cleared: a crown grown from a trunk in this chunk (the same reach as {@link SpillClear#LEAF_MARGIN}). */
	public static final int MARGIN = SpillClear.LEAF_MARGIN;
	/** How far past a lot a shell may reach (air rights over the street, a lead): a tile is skipped when no lot is within this and {@link #MARGIN} of the chunk. */
	private static final int LOT_REACH = 12;
	public static volatile boolean ENABLED = true;
	/** Cells cleared and passes that cleared something, for tests and logs. */
	public static final AtomicLong LEAVES = new AtomicLong(), VINES = new AtomicLong(), LOGS = new AtomicLong(), PASSES = new AtomicLong(), CALLS = new AtomicLong(), NANOS = new AtomicLong();
	private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private BuildingAirClear() {}

	/** @return the number of blocks removed in this pass (not counting what {@link FoliageClear} removed after it) */
	public static int run(WorldGenLevel level, WorldPlanner planner, int cx, int cz) {
		if (!ENABLED || !planner.districtsNear(cx, cz)) return 0;
		long t0 = System.nanoTime(); // det-ok: report only
		try {
			return clear(level, planner, cx, cz);
		} finally {
			CALLS.incrementAndGet();
			NANOS.addAndGet(System.nanoTime() - t0); // det-ok: report only
		}
	}

	private static int clear(WorldGenLevel level, WorldPlanner planner, int cx, int cz) {
		int x0 = cx * 16 - MARGIN, z0 = cz * 16 - MARGIN, x1 = cx * 16 + 15 + MARGIN, z1 = cz * 16 + 15 + MARGIN;
		DistrictGrid g = planner.grid();
		DistrictGrid.TileId ta = g.tileOf(x0, z0), tb = g.tileOf(x1, z1);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<long[]> logs = new ArrayList<>(); // log cells in claimed air, to test after the leaves are known
		List<int[]> cuts = new ArrayList<>();
		Set<Long> cutSet = new HashSet<>(); // the leaf cells removed above
		int[] n = new int[3]; // leaves, vines, logs
		for (int iz = ta.iz(); iz <= tb.iz(); iz++) {
			for (int ix = ta.ix(); ix <= tb.ix(); ix++) {
				if (planner.tile(ix, iz).isEmpty() || !lotNear(planner.plan(ix, iz).plan().lots(), x0, z0, x1, z1)) continue;
				for (BuildingPiece b : planner.buildings(ix, iz)) {
					Box3 pb = b.box();
					int bx0 = Math.max(x0, pb.x()), bz0 = Math.max(z0, pb.z()), bx1 = Math.min(x1, pb.maxX() - 1), bz1 = Math.min(z1, pb.maxZ() - 1);
					if (bx0 > bx1 || bz0 > bz1) continue;
					Box3 clip = new Box3(bx0, pb.y(), bz0, bx1 - bx0 + 1, pb.sy(), bz1 - bz0 + 1);
					try {
						planner.raster(b.ref(), clip, (x, y, z, role, blockId, shape) -> {
							if (role != Role.AIR) return;
							BlockState have = level.getBlockState(pos.set(x, y, z));
							if (have.isAir()) return;
							if (have.is(BlockTags.LEAVES)) {
								if (have.getValueOrElse(LeavesBlock.PERSISTENT, false)) return; // a hedge or a leaf block someone placed
								cuts.add(new int[] {x, y, z});
								cutSet.add(BlockPos.asLong(x, y, z));
								level.setBlock(pos, Blocks.AIR.defaultBlockState(), WRITE_FLAGS);
								n[0]++;
							} else if (have.is(Blocks.VINE)) {
								level.setBlock(pos, Blocks.AIR.defaultBlockState(), WRITE_FLAGS);
								n[1]++;
							} else if (have.is(BlockTags.LOGS)) {
								logs.add(new long[] {x, y, z});
							}
						});
					} catch (IllegalArgumentException e) {
						// the piece no longer exists in the tile (old save): nothing to clear
					}
				}
			}
		}
		// foreign logs: a log in claimed air whose connected logs touch natural leaves (or did: the leaves above were just removed) is a tree's
		for (long[] l : logs) {
			pos.set((int) l[0], (int) l[1], (int) l[2]);
			if (!level.getBlockState(pos).is(BlockTags.LOGS)) continue; // removed with an earlier cluster
			if (!treeCluster(level, (int) l[0], (int) l[1], (int) l[2], cutSet)) continue;
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), WRITE_FLAGS);
			cuts.add(new int[] {(int) l[0], (int) l[1], (int) l[2]});
			n[2]++;
		}
		int total = n[0] + n[1] + n[2];
		if (total == 0) return 0;
		PASSES.incrementAndGet();
		LEAVES.addAndGet(n[0]);
		VINES.addAndGet(n[1]);
		LOGS.addAndGet(n[2]);
		// whatever the cuts left hanging (a crown whose trunk went, a trunk outside the claimed cells) goes the way FoliageClear removes it for a street
		try {
			boolean[] own = new boolean[256];
			int[] surf = new int[256];
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int[] c : cuts) { lo = Math.min(lo, c[1]); hi = Math.max(hi, c[1]); }
			java.util.Arrays.fill(own, true);
			java.util.Arrays.fill(surf, hi);
			surf[0] = lo;
			FoliageClear.run(level, cx, cz, own, surf, null);
		} catch (RuntimeException e) {
			WorldPlanners.FAILURES.incrementAndGet();
			dev.fractalbuildings.mod.FractalBuildingsMod.LOGGER.error("Clearing the foliage round the buildings of chunk {},{} failed", cx, cz, e);
		}
		return total;
	}

	private static boolean lotNear(List<LotBrief> lots, int x0, int z0, int x1, int z1) {
		for (LotBrief l : lots) {
			if (l.x() - LOT_REACH <= x1 && l.x() + l.w() + LOT_REACH > x0 && l.z() - LOT_REACH <= z1 && l.z() + l.d() + LOT_REACH > z0) return true;
		}
		return false;
	}

	/**
	 * Is the log part of a tree: do the logs connected to it (26-neighbours, at most 300 of them) touch a natural leaf, or a cell a leaf was just
	 * removed from? A beam, a pillar or a furniture log touches none.
	 */
	private static boolean treeCluster(WorldGenLevel level, int x, int y, int z, Set<Long> cutLeaves) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		queue.add(new int[] {x, y, z});
		seen.add(BlockPos.asLong(x, y, z));
		int[][] face = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!queue.isEmpty() && seen.size() <= 300) {
			int[] c = queue.poll();
			for (int[] f : face) {
				int nx = c[0] + f[0], ny = c[1] + f[1], nz = c[2] + f[2];
				BlockState s = level.getBlockState(pos.set(nx, ny, nz));
				if (s.is(BlockTags.LEAVES) && !s.getValueOrElse(LeavesBlock.PERSISTENT, false)) return true;
				if (s.isAir() && cutLeaves.contains(BlockPos.asLong(nx, ny, nz))) return true;
			}
			for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
				int nx = c[0] + dx, ny = c[1] + dy, nz = c[2] + dz;
				if (!seen.add(BlockPos.asLong(nx, ny, nz))) continue;
				if (level.getBlockState(pos.set(nx, ny, nz)).is(BlockTags.LOGS)) queue.add(new int[] {nx, ny, nz});
			}
		}
		return false;
	}
}
