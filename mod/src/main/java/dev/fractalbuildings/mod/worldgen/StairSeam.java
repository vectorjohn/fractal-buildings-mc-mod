package dev.fractalbuildings.mod.worldgen;

import java.util.List;
import dev.fractalbuildings.core.world.PieceRef;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.block.BlockMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * DS36ROOFS r2: a building piece also writes the stairs one block across the chunk border that stand beside its own stairs
 * ({@link WorldPlanner#seam}), as their owner rasters them.
 *
 * <p>Why: Minecraft reads a stair's corner {@code shape} from its four horizontal neighbours. Our writes set no neighbour updates
 * ({@code UPDATE_KNOWN_SHAPE}), but a vanilla tree ends with {@code StructureTemplate.updateShapeAtEdge}, which re-runs {@code updateShape} on
 * every block beside its leaves and logs (26.3 {@code TreeFeature.place}, line 188; {@code StairBlock.updateShape}, lines 102-119). If that
 * happens while the neighbour chunk has not written the stair across the border, the stair is reshaped against a missing neighbour, and the
 * neighbour's later write (no updates) never corrects it: the order test saw {@code granite_stairs->granite_stairs} at a hip corner in two
 * orders. With the seam a stair's neighbours are in the world as soon as the stair is, and every chunk that writes a stair or one of its
 * stair neighbours rewrites it, so the last of them leaves it with the raster's shape and a later update recomputes that same shape. The
 * states are the owners' own, so a cell written twice is unchanged; the same pattern places both halves of a bed ({@code HookPlacer}).
 *
 * <p>The seam cells are not recorded in the {@link PieceLedger}: their owner piece records them in its own chunk.
 */
public final class StairSeam {
	private StairSeam() {}

	/** Same flags as the piece writes: clients see it, no neighbour reactions. */
	private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** Mutant switch for the order sim: no seam (the pre-r2 behaviour). */
	public static final String OFF = "no-stair-seam";

	/** Notes the horizontal neighbours outside chunk {@code (cx, cz)} of a stair cell of ours at {@code (x, y, z)} (one on the piece's clip border). */
	static void note(List<int[]> seam, int x, int y, int z, int cx, int cz) {
		if (x - 1 >> 4 != cx) seam.add(new int[] {x - 1, y, z});
		if (x + 1 >> 4 != cx) seam.add(new int[] {x + 1, y, z});
		if (z - 1 >> 4 != cz) seam.add(new int[] {x, y, z - 1});
		if (z + 1 >> 4 != cz) seam.add(new int[] {x, y, z + 1});
	}

	/**
	 * Writes the stairs among the noted cells ({@link WorldPlanner#stairsAt}: each as its owner rasters it, whichever building of the tile).
	 * Returns the cells written. The cells come from {@link #note}, so the same set as {@link WorldPlanner#seam} gives for the piece's clip.
	 */
	public static int write(WorldGenLevel level, WorldPlanner planner, PieceRef ref, List<int[]> cells) {
		if (ref.kind() != PieceRef.Kind.BUILDING || Mutations.is(OFF)) return 0;
		int[] n = {0};
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		planner.stairsAt(ref.ix(), ref.iz(), cells, (x, y, z, role, blockId, shape) -> {
			pos.set(x, y, z);
			if (!level.ensureCanWrite(pos)) return;
			BlockState state = BlockMapper.state(role, blockId, shape);
			if (level.getBlockState(pos) != state) {
				level.setBlock(pos, state, WRITE_FLAGS);
				n[0]++;
			}
		});
		return n[0];
	}
}
