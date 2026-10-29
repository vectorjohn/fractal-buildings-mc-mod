package dev.fractalbuildings.mod.probe;

import dev.fractalbuildings.core.cache.Fanout;
import dev.fractalbuildings.core.probe.ParallelReads;
import java.util.List;
import java.util.TreeSet;

/**
 * PARREADS: fills a world's chunk memo ({@link ColumnMemo}) from several threads ahead of the planner's own, single-threaded reads. A chunk
 * of exact heights is vanilla noise, a pure function of seed and position, and vanilla itself generates noise on many threads at once, so
 * filling chunks in parallel gives the same values; the planner then reads them from the memo in its own order. A prefetch never fills
 * more than half the memo, so it cannot evict what it fills before the planner reads it.
 */
public final class LiveReads implements ParallelReads {
	private final ColumnMemo memo;
	private final Fanout fanout;

	public LiveReads(ColumnMemo memo, Fanout fanout) {
		this.memo = memo;
		this.fanout = fanout;
	}

	@Override public Fanout fanout() { return fanout; }

	@Override public void prefetch(List<int[]> boxes) {
		if (fanout.threads() < 2) return; // serial: filling ahead would only add chunks the planner may never read
		TreeSet<Long> keys = new TreeSet<>();
		int cap = Math.max(1, memo.capacity() / 2);
		for (int[] b : boxes) {
			for (int cz = b[1] >> 4; cz <= b[3] >> 4 && keys.size() < cap; cz++) {
				for (int cx = b[0] >> 4; cx <= b[2] >> 4 && keys.size() < cap; cx++) if (!memo.holds(cx, cz)) keys.add(ColumnMemo.key(cx, cz));
			}
		}
		if (keys.isEmpty()) return;
		long[] k = keys.stream().mapToLong(Long::longValue).toArray();
		fanout.each(k.length, i -> memo.fillChunk((int) (k[i] >> 32), (int) k[i]));
	}
}
