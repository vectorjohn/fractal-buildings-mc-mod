package dev.fractalbuildings.mod.probe;

import dev.fractalbuildings.core.cache.BoundedCache;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Per-world memo of single exact columns {surface, floor}, for sparse lattices (region 64, coarse 32) where a whole chunk would be waste. */
public final class SparseColumns {
	private final NoiseBasedChunkGenerator gen;
	private final RandomState rs;
	private final BoundedCache<Long, int[]> cache;

	public SparseColumns(NoiseBasedChunkGenerator gen, RandomState rs, int capacity) {
		this.gen = gen;
		this.rs = rs;
		this.cache = new BoundedCache<>(capacity);
	}

	/** {surface, floor}. */
	public int[] get(int x, int z) {
		return cache.get(((long) x << 32) | (z & 0xffffffffL), k -> LiveTerrainProbe.column(gen, rs, x, z));
	}

	public long misses() { return cache.misses(); }
}
