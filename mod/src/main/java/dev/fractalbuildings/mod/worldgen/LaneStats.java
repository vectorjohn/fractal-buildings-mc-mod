package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.world.WorldPlanner;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lane counters (plan B4, read by B8 and {@code /fb lane}): how long start creation and {@code postProcess} take, how building lookups
 * were served (done ahead, joined a worker, computed inline) and the peak of concurrent {@code postProcess} calls. Off unless
 * {@code -Dfb.lane=1} (or {@link #enable} in a test): when off every hook is one volatile read.
 */
public final class LaneStats {
	private static volatile boolean on = Boolean.getBoolean("fb.lane") || "1".equals(System.getProperty("fb.lane"));

	private static final LongAdder STARTS = new LongAdder(), START_NS = new LongAdder();
	private static final LongAdder POSTS = new LongAdder(), POST_NS = new LongAdder();
	private static final LongAdder DONE = new LongAdder(), INFLIGHT = new LongAdder(), INLINE = new LongAdder();
	private static final LongAdder WARMS = new LongAdder(), WARM_NS = new LongAdder(), PREFETCHES = new LongAdder(), REFUSED = new LongAdder();
	private static final AtomicInteger POST_NOW = new AtomicInteger(), POST_PEAK = new AtomicInteger();
	private static final AtomicInteger START_NOW = new AtomicInteger(), START_PEAK = new AtomicInteger();
	private static volatile long startMaxNs;

	private LaneStats() {}

	public static boolean enabled() { return on; }

	public static void enable(boolean value) { on = value; }

	/** Hook for planners: counts how each building lookup was served. */
	public static void attach(WorldPlanner planner) {
		planner.observeBuildings(on ? LaneStats::lookup : null);
	}

	/** A token for {@link #end}; 0 when off. */
	public static long startBegin() {
		if (!on) return 0;
		int n = START_NOW.incrementAndGet();
		START_PEAK.accumulateAndGet(n, Math::max);
		return System.nanoTime();
	}

	public static void startEnd(long token) {
		if (token == 0) return;
		long d = System.nanoTime() - token;
		START_NOW.decrementAndGet();
		STARTS.increment();
		START_NS.add(d);
		synchronized (LaneStats.class) { if (d > startMaxNs) startMaxNs = d; }
	}

	/** A start that turned out not to be this structure's: not counted. */
	public static void startAbort(long token) { if (token != 0) START_NOW.decrementAndGet(); }

	public static long postBegin() {
		if (!on) return 0;
		int n = POST_NOW.incrementAndGet();
		POST_PEAK.accumulateAndGet(n, Math::max);
		return System.nanoTime();
	}

	public static void postEnd(long token) {
		if (token == 0) return;
		POST_NOW.decrementAndGet();
		POSTS.increment();
		POST_NS.add(System.nanoTime() - token);
	}

	public static void lookup(WorldPlanner.Lookup l) {
		switch (l) {
			case DONE -> DONE.increment();
			case INFLIGHT -> INFLIGHT.increment();
			case INLINE -> INLINE.increment();
		}
	}

	static void warm(long nanos) { if (on) { WARMS.increment(); WARM_NS.add(nanos); } }

	static void prefetch() { if (on) PREFETCHES.increment(); }

	static void refused() { if (on) REFUSED.increment(); }

	public static long done() { return DONE.sum(); }
	public static long inflight() { return INFLIGHT.sum(); }
	public static long inline() { return INLINE.sum(); }
	public static long starts() { return STARTS.sum(); }
	public static long posts() { return POSTS.sum(); }
	public static int postPeak() { return POST_PEAK.get(); }
	public static double postMeanMs() { long p = POSTS.sum(); return p == 0 ? 0 : POST_NS.sum() / 1e6 / p; }
	public static double startMeanMs() { long s = STARTS.sum(); return s == 0 ? 0 : START_NS.sum() / 1e6 / s; }
	public static double startMaxMs() { return startMaxNs / 1e6; }

	public static void reset() {
		for (LongAdder a : new LongAdder[] {STARTS, START_NS, POSTS, POST_NS, DONE, INFLIGHT, INLINE, WARMS, WARM_NS, PREFETCHES, REFUSED}) a.reset();
		POST_PEAK.set(0);
		START_PEAK.set(0);
		synchronized (LaneStats.class) { startMaxNs = 0; }
	}

	/** The counters as a few lines of text ({@code /fb lane}, test logs). */
	public static String report() {
		long s = STARTS.sum(), p = POSTS.sum(), w = WARMS.sum(), d = DONE.sum(), f = INFLIGHT.sum(), i = INLINE.sum(), t = d + f + i;
		String pct = t == 0 ? "-" : String.format(Locale.ROOT, "%.0f%%", 100.0 * i / t);
		return String.format(Locale.ROOT,
				"lane counters (%s): start creation %d, mean %.1f ms, max %.1f ms, peak concurrent %d%n"
				+ "postProcess %d, mean %.2f ms, peak concurrent %d%n"
				+ "building lookups: done ahead %d, joined a worker %d, inline %d (%s inline)%n"
				+ "prefetch calls %d, warm calls %d (mean %.1f ms), refused by a full pool %d",
				on ? "on" : "off, start with -Dfb.lane=1", s, s == 0 ? 0 : START_NS.sum() / 1e6 / s, startMaxNs / 1e6, START_PEAK.get(),
				p, p == 0 ? 0 : POST_NS.sum() / 1e6 / p, POST_PEAK.get(), d, f, i, pct,
				PREFETCHES.sum(), w, w == 0 ? 0 : WARM_NS.sum() / 1e6 / w, REFUSED.sum());
	}
}
