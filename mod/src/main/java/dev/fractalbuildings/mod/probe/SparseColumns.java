package dev.fractalbuildings.mod.probe;

import dev.fractalbuildings.core.cache.BoundedCache;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Per-world memo of single exact columns {surface, floor}, for sparse lattices (region 64, coarse 32) where a whole chunk would be waste. */
public final class SparseColumns {
	private final NoiseBasedChunkGenerator gen;
	private final RandomState rs;
	private final BoundedCache<Long, int[]> cache;
	private final java.util.concurrent.atomic.AtomicLong fills = new java.util.concurrent.atomic.AtomicLong();

	public SparseColumns(NoiseBasedChunkGenerator gen, RandomState rs, int capacity) {
		this.gen = gen;
		this.rs = rs;
		this.cache = new BoundedCache<>(capacity);
	}

	/** The cached {surface, floor}; internal, never handed out (callers could corrupt the memo). */
	private int[] cached(int x, int z) {
		return cache.getOnce(((long) x << 32) | (z & 0xffffffffL), k -> { // once: parallel decisions and routes (PARREADS) share columns
			fills.incrementAndGet();
			return LiveTerrainProbe.column(gen, rs, x, z);
		});
	}

	/** A copy of {surface, floor}. */
	public int[] get(int x, int z) { return cached(x, z).clone(); }

	public int surface(int x, int z) { return cached(x, z)[0]; }

	public int floor(int x, int z) { return cached(x, z)[1]; }

	public int water(int x, int z) { int[] c = cached(x, z); return c[0] - c[1]; }

	/** Columns generated (refills after eviction included; a column two threads asked for at once counts once). */
	public long misses() { return fills.get(); }
}
