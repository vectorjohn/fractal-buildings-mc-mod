package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.road.RoadSurface;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.world.GroundPiece;
import dev.fractalbuildings.core.world.WorldPlanner;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Re-clears the planned street air next to the chunk being generated (CITYQ, B3 headroom on seed 12345: mossy cobblestone one block over a
 * lattice street at x 655, the last column of its chunk).
 *
 * <p>Our pieces write in the last decoration step, after every vanilla feature of their own chunk. But a neighbour chunk decorated
 * <i>later</i> runs its features then, and a feature may write a few blocks into this chunk (a forest rock, a boulder, a tree's
 * canopy): straight into the 4 blocks of air a street keeps above its surface, which this chunk's pass has already cleared. Lattice
 * streets run on tile borders, which are chunk lines, so every one of them meets this. So each ground piece, after its own cells,
 * writes the planned {@link Role#AIR} cells of the tile ground within {@link #MARGIN} blocks outside its chunk, where the world holds
 * something else.
 *
 * <p>It reads the neighbouring tiles' plans and ground (the prefetch computes them ahead, {@code WorldPlanner.prefetch}), never their
 * buildings: a cell a building may own ({@link #lotMayOwn}: its lot, or its air-rights strip above the street clearance) is left alone. Order does not matter: the cells written are
 * air in the plan (exactly one owner per cell, the ground piece of that chunk; buildings own
 * disjoint cells), so whichever pass writes last writes air, the same block the owner itself writes. Nothing else changes. The writes
 * stay inside the generation region (one chunk out), as vanilla features' do. They are not recorded in the {@link PieceLedger}: the
 * owner records the cell. A chunk with no ground piece of ours runs the pass from {@code RoadFeature} (CITYQ r5, see {@link #hasGround}).
 *
 * <p>Logs are left alone: a neighbour's tree puts dirt under its trunk (setDirtAt), over our road or wall block, and
 * clearing the trunk would leave that dirt in the street with nothing to explain it; the planned block cannot be put back from here
 * without the neighbour's buildings (block styles). Found by the B2 district order test (stone_bricks and mossy_cobblestone turned to
 * dirt under removed trunks). Natural leaves (not persistent) do go (CITYQ r7, FOLIAGE verifier: crowns of standing neighbour trees
 * grown after the street's pass stayed in the street and road headroom on seed 1): a leaf puts nothing under itself, and a crown can
 * reach further than a rock, so leaves are re-cleared up to {@link #LEAF_MARGIN} out. {@link #roadLeaves} does the same for the
 * headroom of intercity roads. Callers run the foliage pass ({@code FoliageClear}) after this one, so leaves these cuts leave hanging go too.
 */
public final class SpillClear {
	/** How far past the chunk planned air is re-cleared: covers forest rocks and boulders (blobs of radius up to 2 round a point at most 1 out). */
	public static final int MARGIN = 4;
	/** How far past the chunk natural leaves in planned air are re-cleared: a crown grown from a trunk in this chunk (stays in the generation region). */
	public static final int LEAF_MARGIN = 12;
	/** Leaves removed (street air and road headroom), for tests. */
	public static final AtomicLong LEAVES = new AtomicLong();
	/** Test switch (FOLIAGE test control): when false leaves are left alone, as before r7. */
	public static volatile boolean LEAF_CLEAR = true;
	/** Test switch: when false nothing is re-cleared. */
	public static volatile boolean ENABLED = true;
	public static final AtomicLong CLEARED = new AtomicLong();
	/** Test hook (B3): when true, each pass records per column what it did there (see {@link #why}). */
	public static volatile boolean TRACE = false;
	private static final java.util.concurrent.ConcurrentSkipListMap<Long, String> WHY = new java.util.concurrent.ConcurrentSkipListMap<>();

	/** What the passes did at column (x, z) while {@link #TRACE} was on: "pass cx,cz: reason" entries, or "no pass". */
	public static String why(int x, int z) { return WHY.getOrDefault(col(x, z), "no pass"); }

	public static void clearTrace() { WHY.clear(); }

	private static long col(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

	private static void trace(int x, int z, int cx, int cz, String what) {
		WHY.merge(col(x, z), cx + "," + cz + ": " + what, (a, b) -> a.length() > 400 ? a : a + "; " + b);
	}

	/**
	 * Does chunk (cx, cz) hold a ground piece of ours? Then {@code TilePiece} runs the pass there; otherwise {@code RoadFeature} does
	 * (CITYQ r5: a lattice street on the settlement's outer tile border has no ground piece across the border, so the chunk whose forest
	 * rock spilled onto it never ran a pass: B3 headroom on seed 12345 at x 655).
	 */
	public static boolean hasGround(WorldPlanner planner, int cx, int cz) {
		DistrictGrid g = planner.grid();
		DistrictGrid.TileId ta = g.tileOf(cx * 16, cz * 16), tb = g.tileOf(cx * 16 + 15, cz * 16 + 15);
		for (int iz = ta.iz(); iz <= tb.iz(); iz++) for (int ix = ta.ix(); ix <= tb.ix(); ix++) {
			if (planner.tile(ix, iz).isEmpty()) continue;
			for (GroundPiece p : planner.ground(ix, iz)) if (p.chunkX() == cx && p.chunkZ() == cz) return true;
		}
		return false;
	}

	private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private SpillClear() {}

	/** A later box of the piece covers the cell with something other than air (boxes apply in list order). */
	static boolean overridden(List<Box> boxes, int i, int x, int y, int z) {
		for (int j = i + 1; j < boxes.size(); j++) {
			Box b = boxes.get(j);
			Box3 q = b.box();
			if (b.role() != Role.AIR && x >= q.x() && x < q.maxX() && y >= q.y() && y < q.maxY() && z >= q.z() && z < q.maxZ()) return true;
		}
		return false;
	}

	/**
	 * Can a building own the cell? Exactly the {@code in-lot} invariant ({@link Lot#allows}): the lot's columns, or its front air-rights strip
	 * at least {@link Lot#AIR_CLEARANCE} above the street. CITYQ r5: the first version skipped every cell within air rights + 1 of a lot on
	 * every side, which covered the street air beside lots on the 12345 slope tile, so forest rock there was never cleared (B3 headroom).
	 */
	static boolean lotMayOwn(List<LotBrief> lots, int x, int y, int z) {
		for (LotBrief l : lots) {
			if (x >= l.x() && x < l.x() + l.w() && z >= l.z() && z < l.z() + l.d()) return true;
			if (l.airRights() > 0 && y >= l.frontY() + Lot.AIR_CLEARANCE
					&& new Lot(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), l.airRights(), l.frontY()).inAirStrip(x, z)) return true;
		}
		return false;
	}

	/** Re-clears planned air of the ground pieces round chunk (cx, cz), outside the chunk; returns the number of blocks written. */
	public static int run(WorldGenLevel level, WorldPlanner planner, int cx, int cz) {
		if (!ENABLED) return 0;
		int x0 = cx * 16 - LEAF_MARGIN, z0 = cz * 16 - LEAF_MARGIN, x1 = cx * 16 + 15 + LEAF_MARGIN, z1 = cz * 16 + 15 + LEAF_MARGIN;
		int minY = level.getMinY();
		DistrictGrid g = planner.grid();
		DistrictGrid.TileId ta = g.tileOf(x0, z0), tb = g.tileOf(x1, z1);
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
		int[] n = new int[1];
		long leaves = 0;
		for (int iz = ta.iz(); iz <= tb.iz(); iz++) {
			for (int ix = ta.ix(); ix <= tb.ix(); ix++) {
				if (planner.tile(ix, iz).isEmpty()) continue;
				// cells a building may own (its lot, its air-rights strip above the clearance) are left alone: no building is computed here
				List<LotBrief> lots = planner.plan(ix, iz).plan().lots();
				for (GroundPiece p : planner.ground(ix, iz)) {
					if (p.chunkX() == cx && p.chunkZ() == cz) continue;
					List<Box> boxes = p.boxes();
					for (int i = 0; i < boxes.size(); i++) {
						Box b = boxes.get(i);
						if (b.role() != Role.AIR) continue;
						Box3 q = b.box();
						int bx0 = Math.max(x0, q.x()), bz0 = Math.max(z0, q.z()), bx1 = Math.min(x1, q.maxX() - 1), bz1 = Math.min(z1, q.maxZ() - 1);
						for (int z = bz0; z <= bz1; z++) for (int x = bx0; x <= bx1; x++) {
							if (x >= cx * 16 && x < cx * 16 + 16 && z >= cz * 16 && z < cz * 16 + 16) continue; // this chunk: its own pieces wrote it
							if (TRACE) trace(x, z, cx, cz, "seen");
							boolean near = x >= cx * 16 - MARGIN && x < cx * 16 + 16 + MARGIN && z >= cz * 16 - MARGIN && z < cz * 16 + 16 + MARGIN; // past it only leaves go
							for (int y = Math.max(q.y(), minY); y < q.maxY(); y++) {
								if (overridden(boxes, i, x, y, z)) continue;
								if (lotMayOwn(lots, x, y, z)) { if (TRACE) trace(x, z, cx, cz, "y" + y + " a lot's"); continue; }
								pos.set(x, y, z);
								BlockState have = level.getBlockState(pos);
								if (have.isAir() || have.is(BlockTags.LOGS)) continue; // logs stay: see the class note
								if (LEAF_CLEAR && naturalLeaf(have)) {
									if (TRACE) trace(x, z, cx, cz, "y" + y + " leaf cleared");
									level.setBlock(pos, air, WRITE_FLAGS);
									n[0]++;
									leaves++;
									continue;
								}
								if (!near || have.is(BlockTags.LEAVES)) continue; // a persistent leaf (a hedge) is someone's
								if (level.getBlockState(above.set(x, y + 1, z)).is(BlockTags.LOGS) || level.getBlockState(above.set(x, y + 2, z)).is(BlockTags.LOGS)) { if (TRACE) trace(x, z, cx, cz, "y" + y + " under a log"); continue; } // a trunk's base
								if (TRACE) trace(x, z, cx, cz, "y" + y + " " + have.getBlock().getDescriptionId() + " cleared");
								level.setBlock(pos, air, WRITE_FLAGS);
								n[0]++;
							}
						}
					}
				}
			}
		}
		CLEARED.addAndGet(n[0]);
		LEAVES.addAndGet(leaves);
		return n[0];
	}

	/** A leaf a tree feature grew (not persistent: hedges and the leaf blocks of buildings are). */
	static boolean naturalLeaf(BlockState s) {
		return s.is(BlockTags.LEAVES) && !s.getValueOrElse(LeavesBlock.PERSISTENT, false);
	}

	/**
	 * Removes natural leaves from the headroom of the intercity road columns of the 8 chunks round chunk (cx, cz) (CITYQ r7): a crown
	 * grown here after a neighbour drew its road hangs in that road's air, and nothing else clears it. The headroom is the
	 * {@link RoadSurface#HEADROOM} cells over the surface {@code col.y() - 1}, which {@code RoadSurface.plan} sets from the column alone
	 * (road and deck columns), so every chunk computes the same cells. Columns a district draws are left to the district ({@link #run}).
	 * Only leaves are removed (the road's own clear writes air over these cells too), so order does not matter.
	 */
	public static int roadLeaves(WorldGenLevel level, WorldPlanner planner, int cx, int cz) {
		if (!ENABLED || !LEAF_CLEAR) return 0;
		List<List<RoadNet.Col>> around = planner.roadColumnsAround(cx, cz);
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int n = 0;
		for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
			if (dx == 0 && dz == 0) continue; // this chunk: its own road clear wrote it
			List<RoadNet.Col> cols = around.get((dz + 1) * 3 + (dx + 1));
			if (cols.isEmpty()) continue;
			boolean[] district = planner.districtColumns(cx + dx, cz + dz);
			for (RoadNet.Col c : cols) {
				if (district != null && district[(c.z() & 15) * 16 + (c.x() & 15)]) continue;
				for (int y = c.y(); y < c.y() + RoadSurface.HEADROOM; y++) {
					BlockState have = level.getBlockState(pos.set(c.x(), y, c.z()));
					if (!naturalLeaf(have)) continue;
					level.setBlock(pos, air, WRITE_FLAGS);
					n++;
				}
			}
		}
		LEAVES.addAndGet(n);
		return n;
	}
}
