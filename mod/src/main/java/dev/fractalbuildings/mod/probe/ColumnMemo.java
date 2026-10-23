package dev.fractalbuildings.mod.probe;

import dev.fractalbuildings.core.cache.BoundedCache;

/**
 * Per-world memo of whole chunks of exact heights. A value is {@code int[512]}: floors at
 * {@code (z & 15) * 16 + (x & 15)}, water depths 256 further. Pure: eviction changes speed only.
 */
public final class ColumnMemo {
	/** Computes the {@code int[512]} of one chunk. */
	public interface Filler { int[] fill(int chunkX, int chunkZ); }

	private final BoundedCache<Long, int[]> cache;
	private final Filler filler;

	public ColumnMemo(int capacityChunks, Filler filler) {
		this.cache = new BoundedCache<>(capacityChunks);
		this.filler = filler;
	}

	private int[] chunk(int x, int z) {
		int cx = x >> 4, cz = z >> 4;
		return cache.get(((long) cx << 32) | (cz & 0xffffffffL), k -> filler.fill(cx, cz));
	}

	public int floor(int x, int z) { return chunk(x, z)[(z & 15) * 16 + (x & 15)]; }

	public int water(int x, int z) { return chunk(x, z)[256 + (z & 15) * 16 + (x & 15)]; }

	/** Surface height (floor plus water depth). */
	public int surface(int x, int z) {
		int[] v = chunk(x, z);
		int i = (z & 15) * 16 + (x & 15);
		return v[i] + v[256 + i];
	}

	public long fills() { return cache.misses(); }

	public int size() { return cache.size(); }
}
