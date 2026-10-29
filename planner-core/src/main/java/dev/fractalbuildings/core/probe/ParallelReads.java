package dev.fractalbuildings.core.probe;

import dev.fractalbuildings.core.cache.Fanout;
import java.util.List;

/**
 * How a planner may read terrain on several threads (PARREADS). Live terrain is vanilla noise, a pure function of seed and column that costs
 * milliseconds per chunk, and a cold settlement reads hundreds of thousands of columns; the planner's logic stays single-threaded and in its own
 * order, and this only lets the probe's memo be filled ahead of it. Nothing here may change an answer: {@link #NONE} (no prefetch, every piece on
 * the caller) gives the same plans, byte for byte.
 */
public interface ParallelReads {
	/**
	 * A hint that the exact terrain of these boxes ({x0, z0, x1, z1}, inclusive) is about to be read through the planner's exact probe: an
	 * implementation with a memo fills it, maybe in parallel, and returns when done. Callers pass what they will read next, not more than the
	 * memo holds (a box set larger than the memo would evict its own start).
	 */
	void prefetch(List<int[]> boxes);

	/** Independent pure pieces of planner work (cell decisions, say), run as {@link Fanout#each}. */
	Fanout fanout();

	/** No prefetch, every piece on the caller: recorded and synthetic terrain, the CLI, tests. */
	ParallelReads NONE = new ParallelReads() {
		@Override public void prefetch(List<int[]> boxes) { }

		@Override public Fanout fanout() { return Fanout.SERIAL; }
	};
}
