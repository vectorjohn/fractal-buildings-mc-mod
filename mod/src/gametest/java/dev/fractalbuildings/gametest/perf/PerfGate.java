package dev.fractalbuildings.gametest.perf;

/**
 * B8: {@code FB_PERF=1} runs the in-client perf test (heavy, minutes; {@code scripts/perf.sh --client}) and, when {@code FB_PERF=only},
 * skips the other client tests. The default perf run is the dedicated-server {@link PerfServerHarness}.
 */
public final class PerfGate {
	private PerfGate() {}

	public static boolean perf() { return System.getenv("FB_PERF") != null; }

	/** The other client tests return at once in a perf-only run. */
	public static boolean skipOthers() { return "only".equals(System.getenv("FB_PERF")); }
}
