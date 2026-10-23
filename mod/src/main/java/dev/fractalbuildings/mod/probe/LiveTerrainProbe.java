package dev.fractalbuildings.mod.probe;

import dev.fractalbuildings.core.cache.BoundedCache;
import dev.fractalbuildings.core.probe.TerrainProbe;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Exact terrain heights from vanilla worldgen, with the definition the recorded dumps use
 * ({@code TerrainDumpTool}, which calls {@link #column}): surface = first non-air from the top + 1,
 * floor = first non-air non-fluid + 1, both from {@code ChunkGenerator.getBaseColumn} over
 * {@link #H}. {@code floorY} = floor, {@code waterDepth} = surface - floor.
 *
 * <p>Reads go through a per-world {@link ColumnMemo} of whole chunks (16 x 16 floors and water
 * depths), filled by a {@link ChunkFill} method; a district read touches each chunk once. The memo
 * only memoises: eviction changes speed, never values. Thread-safe.
 */
public final class LiveTerrainProbe implements TerrainProbe {
	/** The height window of the dumps and of the overworld. */
	public static final LevelHeightAccessor H = LevelHeightAccessor.create(-64, 384);

	private final ColumnMemo memo;

	public LiveTerrainProbe(NoiseBasedChunkGenerator gen, RandomState rs, ChunkFill fill, int chunkCapacity) {
		this.memo = new ColumnMemo(chunkCapacity, (cx, cz) -> fill.fill(gen, rs, cx, cz));
	}

	public ColumnMemo memo() { return memo; }

	@Override public int floorY(int x, int z) { return memo.floor(x, z); }

	@Override public int waterDepth(int x, int z) { return memo.water(x, z); }

	/** {surface, floor} of one column, exactly as the dumps define them. */
	public static int[] column(NoiseBasedChunkGenerator gen, RandomState rs, int x, int z) {
		NoiseColumn col = gen.getBaseColumn(x, z, H, rs);
		int surf = Integer.MIN_VALUE, floor = Integer.MIN_VALUE;
		for (int y = H.getMaxY(); y >= H.getMinY(); y--) {
			var st = col.getBlock(y);
			if (st.isAir()) continue;
			if (surf == Integer.MIN_VALUE) surf = y + 1;
			if (st.getFluidState().isEmpty()) {
				floor = y + 1;
				break;
			}
		}
		return new int[] {surf, floor};
	}
}
