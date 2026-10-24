package dev.fractalbuildings.gametest.perf;

/** B8: {@code FB_PERF=1} runs the perf client test (heavy, minutes) and, when {@code FB_PERF=only}, skips the other client tests. */
public final class PerfGate {
	private PerfGate() {}

	public static boolean perf() { return System.getenv("FB_PERF") != null; }

	/** The other client tests return at once in a perf-only run. */
	public static boolean skipOthers() { return "only".equals(System.getenv("FB_PERF")); }
}
