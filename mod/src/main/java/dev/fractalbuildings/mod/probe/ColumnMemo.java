package dev.fractalbuildings.mod.probe;

import dev.fractalbuildings.core.cache.BoundedCache;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-world memo of whole chunks of exact heights. A value is {@code int[512]}: floors at
 * {@code (z & 15) * 16 + (x & 15)}, water depths 256 further. Pure: eviction changes speed only.
 *
 * <p>Thread-safe, and a chunk that several threads miss at once is filled once (the others wait for it: PARREADS fills the memo from
 * several threads ahead of the planner's own reads, {@link LiveReads}).
 */
public final class ColumnMemo {
	/** Computes the {@code int[512]} of one chunk. */
	public interface Filler { int[] fill(int chunkX, int chunkZ); }

	private final BoundedCache<Long, int[]> cache;
	private final Filler filler;
	private final AtomicLong fills = new AtomicLong();

	public ColumnMemo(int capacityChunks, Filler filler) {
		this.cache = new BoundedCache<>(capacityChunks);
		this.filler = filler;
	}

	static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xffffffffL); }

	private int[] chunkAt(int cx, int cz) {
		return cache.getOnce(key(cx, cz), k -> {
			fills.incrementAndGet();
			return filler.fill(cx, cz);
		});
	}

	private int[] chunk(int x, int z) { return chunkAt(x >> 4, z >> 4); }

	/** Makes sure the chunk is in the memo (a prefetch piece). */
	void fillChunk(int cx, int cz) { chunkAt(cx, cz); }

	/** Whether the chunk is in the memo now (no hit or miss counted). */
	boolean holds(int cx, int cz) { return cache.peek(key(cx, cz)) != null; }

	public int capacity() { return cache.capacity(); }

	public int floor(int x, int z) { return chunk(x, z)[(z & 15) * 16 + (x & 15)]; }

	public int water(int x, int z) { return chunk(x, z)[256 + (z & 15) * 16 + (x & 15)]; }

	/** Surface height (floor plus water depth). */
	public int surface(int x, int z) {
		int[] v = chunk(x, z);
		int i = (z & 15) * 16 + (x & 15);
		return v[i] + v[256 + i];
	}

	/** Chunks filled (refills after eviction included; a chunk two threads asked for at once counts once). */
	public long fills() { return fills.get(); }

	public int size() { return cache.size(); }
}
