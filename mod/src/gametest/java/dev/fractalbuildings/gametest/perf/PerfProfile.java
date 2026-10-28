package dev.fractalbuildings.gametest.perf;

import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

/**
 * PROFILE: per-phase JFR recordings and exact per-phase counters for the perf world half, on only when {@code FB_PERF_JFR=<dir>} is set
 * ({@code scripts/profile.sh}). Each {@link #phase} call closes the running phase (dumps {@code <dir>/<variant>-<nn>-<phase>.jfr} and
 * records its rows) and opens the next. Rows per phase {@code <p>phase.<name>.*}: wall seconds, process CPU (includes GC and JIT
 * threads), CPU and allocated bytes per thread group (thread name with its digits replaced by N; exact, from the ThreadMXBean, polled
 * every 250 ms so a pool thread that ends mid-phase keeps its count), GC count and time. Recording settings: the JDK's {@code profile}
 * configuration plus the JDK 25 CPU-time sampler ({@code jdk.CPUTimeSample}, one sample per 10 ms of CPU per thread), which, unlike
 * {@code jdk.ExecutionSample}, samples every busy thread. Deep stacks need {@code -XX:FlightRecorderOptions:stackdepth=...} on the JVM
 * (the script sets it through JAVA_TOOL_OPTIONS). Inert when the variable is unset.
 */
final class PerfProfile {
	private static final String DIR = System.getenv("FB_PERF_JFR");
	private final PerfSupport s;
	private final String p;
	private final String variant;
	private Recording rec;
	private String name;
	private int n;
	private long wall0, proc0;
	private Map<Long, long[]> threads0;
	private long gcCount0, gcMs0;
	private final Map<Long, long[]> seen = new HashMap<>();
	private final Map<Long, String> groupOf = new HashMap<>();
	private Thread poller;

	PerfProfile(PerfSupport s, String p, String variant) {
		this.s = s;
		this.p = p;
		this.variant = variant;
	}

	static boolean enabled() {
		return DIR != null && !DIR.isBlank();
	}

	/** Closes the running phase, if any, and starts {@code next} (null: stop). */
	void phase(String next) {
		if (!enabled()) return;
		// Minecraft's own JFR events (minecraft.ChunkGeneration per target status, StructureGeneration, region I/O) are only emitted while
		// its JvmProfiler runs; it keeps its own recording (debug/ in the run dir), ours pick the events up too.
		if (name == null && n == 0 && !net.minecraft.util.profiling.jfr.JvmProfiler.INSTANCE.isRunning())
			net.minecraft.util.profiling.jfr.JvmProfiler.INSTANCE.start(net.minecraft.util.profiling.jfr.Environment.SERVER);
		if (name != null) close();
		name = next;
		if (next == null) {
			if (poller != null) poller.interrupt();
			if (net.minecraft.util.profiling.jfr.JvmProfiler.INSTANCE.isRunning()) net.minecraft.util.profiling.jfr.JvmProfiler.INSTANCE.stop();
			return;
		}
		try {
			Configuration c = Configuration.getConfiguration("profile");
			Map<String, String> set = new HashMap<>(c.getSettings());
			set.put("jdk.CPUTimeSample#enabled", "true");
			set.put("jdk.CPUTimeSample#throttle", "10ms");
			set.put("jdk.CPUTimeSample#stackTrace", "true");
			rec = new Recording(set);
			rec.setName(variant + "-" + next);
			rec.setToDisk(true);
			rec.start();
		} catch (Exception e) {
			throw new IllegalStateException("PROFILE: cannot start JFR", e);
		}
		wall0 = System.nanoTime();
		proc0 = processCpu();
		threads0 = snapshot();
		if (poller == null) {
			poller = new Thread(() -> {
				try {
					while (true) {
						Thread.sleep(250);
						snapshot();
					}
				} catch (InterruptedException ignored) {
					// stopped
				}
			}, "fb-profile-poller");
			poller.setDaemon(true);
			poller.start();
		}
		long[] gc = gc();
		gcCount0 = gc[0];
		gcMs0 = gc[1];
		FractalBuildingsMod.LOGGER.info("[PROFILE] phase {} started", next);
	}

	private void close() {
		double wall = (System.nanoTime() - wall0) / 1e9;
		double proc = (processCpu() - proc0) / 1e9;
		Map<Long, long[]> t1 = snapshot();
		long[] gc = gc();
		rec.stop();
		Path f = Path.of(DIR).resolve(String.format("%s-%02d-%s.jfr", variant, n++, name));
		try {
			Files.createDirectories(f.getParent());
			rec.dump(f);
		} catch (Exception e) {
			throw new IllegalStateException("PROFILE: cannot dump " + f, e);
		} finally {
			rec.close();
		}
		String q = p + "phase." + name + ".";
		s.put(q + "wall.s", wall);
		s.put(q + "cpu.process.s", proc);
		Map<String, long[]> byGroup = new TreeMap<>();
		synchronized (this) {
			for (var e : t1.entrySet()) {
				long[] a = threads0.getOrDefault(e.getKey(), new long[2]);
				long[] g = byGroup.computeIfAbsent(groupOf.get(e.getKey()), k -> new long[2]);
				g[0] += e.getValue()[0] - a[0];
				g[1] += e.getValue()[1] - a[1];
			}
		}
		double javaCpu = 0;
		long alloc = 0;
		for (var e : byGroup.entrySet()) {
			double cpu = e.getValue()[0] / 1e9;
			long al = e.getValue()[1];
			javaCpu += cpu;
			alloc += al;
			if (cpu >= 0.05 || al >= (64L << 20)) {
				s.put(q + "cpu." + e.getKey() + ".s", cpu);
				s.put(q + "alloc." + e.getKey() + ".mb", al / 1048576.0);
			}
		}
		s.put(q + "cpu.javaThreads.s", javaCpu);
		s.put(q + "alloc.mb", alloc / 1048576.0);
		s.put(q + "gc.count", gc[0] - gcCount0);
		s.put(q + "gc.ms", gc[1] - gcMs0);
		s.put(q + "jfr", f.getFileName().toString());
		FractalBuildingsMod.LOGGER.info("[PROFILE] phase {} closed: {} s wall, {} s process CPU -> {}", name, wall, proc, f);
	}

	private static long processCpu() {
		var os = ManagementFactory.getOperatingSystemMXBean();
		return os instanceof com.sun.management.OperatingSystemMXBean o ? o.getProcessCpuTime() : -1;
	}

	/** Updates, then copies, the last seen CPU ns and allocated bytes of every thread id (a thread gone since keeps its last values). */
	private synchronized Map<Long, long[]> snapshot() {
		var mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		for (Thread t : Thread.getAllStackTraces().keySet()) {
			long id = t.threadId();
			long cpu = mx.getThreadCpuTime(id);
			long al = mx.getThreadAllocatedBytes(id);
			if (cpu < 0) continue;
			if (!groupOf.containsKey(id)) groupOf.put(id, group(t.getName()));
			seen.put(id, new long[] {cpu, Math.max(0, al)});
		}
		Map<Long, long[]> copy = new HashMap<>();
		for (var e : seen.entrySet()) copy.put(e.getKey(), e.getValue().clone());
		return copy;
	}

	/** A thread's group: its name with digit runs replaced by N, other punctuation by _. */
	static String group(String threadName) {
		return threadName.replaceAll("[0-9]+", "N").replaceAll("[^A-Za-z0-9N_-]+", "_");
	}

	private static long[] gc() {
		long c = 0, ms = 0;
		for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
			c += Math.max(0, b.getCollectionCount());
			ms += Math.max(0, b.getCollectionTime());
		}
		return new long[] {c, ms};
	}
}
