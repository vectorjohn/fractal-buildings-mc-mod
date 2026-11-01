package dev.fractalbuildings.core.settlement;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Knobs and counters of the lazy quay trace for the performance measurement (QUAYLAZY step 6, PerfPlannerTest's ablation rows). The defaults are the
 * shipped behaviour; none of the knobs changes an answer, only how much terrain is read ahead and how (a hint), so a test may flip them. Not for
 * production use. Static, so a measurement that flips a knob must run on one thread of control at a time.
 */
public final class QuayTuning {
	private QuayTuning() {}

	/** How far ahead of a chain walk the terrain is prefetched, blocks; 0 reads node by node. */
	public static volatile int walkAhead = 32;
	/** Whether a lazy request prefetches its whole scan box (one parallel call) before the seed scan. */
	public static volatile boolean scanBox = true;
	/** How many walk-ahead boxes a chain walk remembers (CASCADE 2fix); 1 is the single box before it, which the two ends of a breadth-first walk flip between. */
	public static volatile int walkBoxes = 4;
	/** Walk-ahead prefetches (a node read outside every remembered box), summed over threads: each is one parallel round the walk waits for. */
	public static final AtomicLong walkPrefetches = new AtomicLong();
	/** A clock for timing the forks, set by a measuring test only (the core reads no wall clock itself: DeterminismLintTest); null: forks are counted, not timed. */
	public static volatile java.util.function.LongSupplier clock;
	/** Banks forked for fanned-out pieces, and the nanoseconds spent building them (summed over threads). */
	public static final AtomicLong forks = new AtomicLong(), forkNanos = new AtomicLong();

	public static void reset() { walkAhead = 32; walkBoxes = 4; scanBox = true; walkPrefetches.set(0); forks.set(0); forkNanos.set(0); }
}
