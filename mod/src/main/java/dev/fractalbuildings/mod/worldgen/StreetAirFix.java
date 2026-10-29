package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.world.GroundPiece;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.block.BlockMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Two things a district street's air clear leaves behind (STREETSNOW and TRUNKDIRT, ROADMAP 6.17 follow-ups), found in the planned
 * {@link Role#AIR} boxes of the ground pieces round the chunk:
 * <ul>
 * <li><b>Snow over air.</b> {@code freeze_top_layer} puts a snow layer on the old ground (or on a crown the street clear then took out), one cell
 * above the street's clear: it hangs there. A snow layer on the first cell above a planned air box with air under it goes (as ROADCLEAR does for
 * intercity roads in {@code RoadFeature}); nothing natural puts a snow layer over air, and a cell a building may own is left alone.</li>
 * <li><b>Trunks on a street.</b> A neighbour chunk decorated after the street's chunk grows a tree whose trunk stands in the street air, with
 * dirt (podzol) under it in the place of the street surface block ({@code setDirtAt}; 2 x 2 dark oaks leave a 2 x 2 patch of it). A log in planned
 * air is removed (nobody plans one there), the dirt under the lowest log of a column gets the planned block back (read from the piece's raster
 * of that one cell), and {@link FoliageClear} takes what the cut leaves hanging.</li>
 * </ul>
 * Order independence: the decision uses only the plan, the ledger-free raster of the piece and the block in the cell; like {@link SpillClear} it runs
 * in every chunk after its own features and reaches {@link #MARGIN} blocks out, so whichever chunk grows the tree last has its trunk covered by a pass
 * that runs after it.
 */
public final class StreetAirFix {
	/** The same reach as {@link BuildingAirClear#MARGIN}: a trunk grown from this chunk reaches at most about 7 blocks out. */
	public static final int MARGIN = SpillClear.LEAF_MARGIN;
	public static volatile boolean ENABLED = true;
	/** Test switches: only one of the two fixes (controls), see the game test. */
	public static volatile boolean SNOW = true, TRUNKS = true;
	public static final AtomicLong SNOW_REMOVED = new AtomicLong(), LOGS_REMOVED = new AtomicLong(), DIRT_RESTORED = new AtomicLong();
	private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private StreetAirFix() {}

	public static int run(WorldGenLevel level, WorldPlanner planner, int cx, int cz) {
		if (!ENABLED || !planner.districtsNear(cx, cz)) return 0;
		boolean keepAirBase = Mutations.is(Mutations.TRUNK_BASE); // read once per run
		int x0 = cx * 16 - MARGIN, z0 = cz * 16 - MARGIN, x1 = cx * 16 + 15 + MARGIN, z1 = cz * 16 + 15 + MARGIN;
		int minY = level.getMinY();
		DistrictGrid g = planner.grid();
		DistrictGrid.TileId ta = g.tileOf(x0, z0), tb = g.tileOf(x1, z1);
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		FoliageClear.Cuts cuts = new FoliageClear.Cuts("street-fix");
		int removed = 0, lowest = Integer.MAX_VALUE;
		for (int iz = ta.iz(); iz <= tb.iz(); iz++) {
			for (int ix = ta.ix(); ix <= tb.ix(); ix++) {
				if (planner.tile(ix, iz).isEmpty()) continue;
				List<LotBrief> lots = planner.plan(ix, iz).plan().lots();
				for (GroundPiece p : planner.ground(ix, iz)) {
					List<Box> boxes = p.boxes();
					for (int i = 0; i < boxes.size(); i++) {
						Box b = boxes.get(i);
						if (b.role() != Role.AIR) continue;
						Box3 q = b.box();
						int bx0 = Math.max(x0, q.x()), bz0 = Math.max(z0, q.z()), bx1 = Math.min(x1, q.maxX() - 1), bz1 = Math.min(z1, q.maxZ() - 1);
						for (int z = bz0; z <= bz1; z++) for (int x = bx0; x <= bx1; x++) {
							if (TRUNKS) {
								for (int y = Math.max(q.y(), minY); y < q.maxY(); y++) {
									if (SpillClear.overridden(boxes, i, x, y, z) || SpillClear.lotMayOwn(lots, x, y, z)) continue;
									if (!level.getBlockState(pos.set(x, y, z)).is(BlockTags.LOGS)) continue;
									// the lowest log of the column: the base it stands on is the dirt a trunk puts there
									int by = y - 1;
									BlockState have = level.getBlockState(pos.set(x, y, z));
									cuts.note(have, x, y, z);
									level.setBlock(pos, air, WRITE_FLAGS);
									LOGS_REMOVED.incrementAndGet();
									removed++;
									lowest = Math.min(lowest, y);
									restoreBase(level, planner, p, x, by, z, pos, keepAirBase);
								}
							}
							if (SNOW) {
								int y = q.maxY();
								if (!SpillClear.overridden(boxes, i, x, y, z) && !SpillClear.lotMayOwn(lots, x, y, z)
										&& level.getBlockState(pos.set(x, y, z)).is(Blocks.SNOW) && level.getBlockState(pos.set(x, y - 1, z)).isAir()) {
									level.setBlock(pos.set(x, y, z), air, WRITE_FLAGS);
									SNOW_REMOVED.incrementAndGet();
									removed++;
								}
							}
						}
					}
				}
			}
		}
		if (cuts.size() > 0) {
			boolean[] own = new boolean[256];
			int[] surf = new int[256];
			java.util.Arrays.fill(own, true);
			java.util.Arrays.fill(surf, lowest);
			removed += FoliageClear.afterCut(level, cx, cz, own, surf, cuts);
		}
		return removed;
	}

	/** Dirt (podzol, coarse dirt) where the piece plans another solid block, under a trunk we just took out: the planned block back. */
	private static void restoreBase(WorldGenLevel level, WorldPlanner planner, GroundPiece p, int x, int y, int z, BlockPos.MutableBlockPos pos, boolean keepAirBase) {
		BlockState have = level.getBlockState(pos.set(x, y, z));
		if (!(have.is(Blocks.DIRT) || have.is(Blocks.PODZOL) || have.is(Blocks.COARSE_DIRT) || have.is(Blocks.ROOTED_DIRT))) return;
		BlockState[] planned = new BlockState[1]; boolean[] inAir = new boolean[1];
		try {
			planner.raster(p.ref(), new Box3(x, y, z, 1, 1, 1), (cx, cy, cz, role, blockId, shape) -> {
				inAir[0] = role == Role.AIR; planned[0] = role == Role.AIR ? null : BlockMapper.state(role, blockId, shape);
			});
		} catch (IllegalArgumentException e) {
			return; // the piece no longer exists in the tile (old save)
		}
		BlockState want = planned[0];
		if (keepAirBase && (want == null || want.isAir())) return; // TRUNK_BASE mutant: the pre-SPILLDIRT2 code
		if (want == null || want.isAir()) want = inAir[0] ? Blocks.AIR.defaultBlockState() : null; // a base the plan gives as air (a trunk on the street surface) goes too
		if (want == null || want == have) return;
		level.setBlock(pos.set(x, y, z), want, WRITE_FLAGS);
		DIRT_RESTORED.incrementAndGet();
	}
}
