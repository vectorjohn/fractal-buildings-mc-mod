package dev.fractalbuildings.gametest.perf;

import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * COLDTICK (6.37): what the server thread is doing in a long tick. A daemon thread polls every 100 ms; once a tick (START to END of
 * {@code tickServer}) or the gap after it (the task queue the server runs between ticks) is over {@link #LIMIT_MS}, it logs the server
 * thread's stack, then a shorter stack every second while the same stall lasts (at most {@link #MAX_DUMPS}), and once per stall the stacks
 * of the chunk workers and our pool that are running (what the server thread may be waiting on). Lines start with {@code <prefix> long tick}.
 * Diagnostic test code only (wall clock is fine here); inert until {@link #install} and while {@code armed} says no.
 */
final class LongTickWatch {
	static final long LIMIT_MS = 1500;
	static final int MAX_DUMPS = 20;

	private final String prefix;
	private final Supplier<String> phase;
	private final Supplier<Boolean> armed;
	private volatile Thread serverThread;
	private volatile long tickStart, tickEnd;
	private volatile boolean inTick;
	private volatile long stallId;

	private LongTickWatch(String prefix, Supplier<String> phase, Supplier<Boolean> armed) {
		this.prefix = prefix;
		this.phase = phase;
		this.armed = armed;
	}

	/** Registers the tick events and starts the watcher. {@code phase} labels the dumps; {@code armed} false skips them (e.g. before the player exists). */
	static LongTickWatch install(String prefix, Supplier<String> phase, Supplier<Boolean> armed) {
		LongTickWatch w = new LongTickWatch(prefix, phase, armed);
		ServerTickEvents.START_SERVER_TICK.register(sv -> { long now = System.nanoTime(); w.ended("between ticks", w.tickEnd, now); w.serverThread = Thread.currentThread(); w.tickStart = now; w.inTick = true; w.stallId++; w.mark(); });
		ServerTickEvents.END_SERVER_TICK.register(sv -> { long now = System.nanoTime(); w.ended("in tick", w.tickStart, now); w.tickEnd = now; w.inTick = false; w.stallId++; w.mark(); });
		FractalBuildingsMod.LOGGER.info("{} long-tick watch on: max heap {} MB, {} cpus, GCs {}", prefix, Runtime.getRuntime().maxMemory() >> 20, Runtime.getRuntime().availableProcessors(),
			ManagementFactory.getGarbageCollectorMXBeans().stream().map(java.lang.management.GarbageCollectorMXBean::getName).toList());
		Thread t = new Thread(w::run, "coldtick-watch");
		t.setDaemon(true);
		t.start();
		return w;
	}

	/** GC count and time (ms) at the last tick boundary. */
	private volatile long gcCount0, gcMs0;

	private void mark() { long[] g = gc(); gcCount0 = g[0]; gcMs0 = g[1]; }

	private static long[] gc() {
		long n = 0, ms = 0;
		for (var b : ManagementFactory.getGarbageCollectorMXBeans()) { n += Math.max(0, b.getCollectionCount()); ms += Math.max(0, b.getCollectionTime()); }
		return new long[] { n, ms };
	}

	/** GC since the last tick boundary and the heap now: tells a collector pause from a server-thread wait (a paused watcher sees neither). */
	private String gcSince() {
		long[] g = gc();
		Runtime r = Runtime.getRuntime();
		return "GC " + (g[1] - gcMs0) + " ms in " + (g[0] - gcCount0) + " collections, heap " + ((r.totalMemory() - r.freeMemory()) >> 20) + "/" + (r.maxMemory() >> 20) + " MB";
	}

	/** On the server thread at a tick boundary: one summary line for a stall of 1 s or more (the dumps only start at LIMIT_MS). */
	private void ended(String where, long from, long now) {
		if (from == 0) return;
		long ms = (now - from) / 1_000_000L;
		if (ms < 1000) return;
		Boolean a;
		try { a = armed.get(); } catch (RuntimeException e) { a = Boolean.TRUE; }
		if (!Boolean.TRUE.equals(a)) return;
		FractalBuildingsMod.LOGGER.info("{} long tick #{} ended ({}, phase {}): {} ms, {}", prefix, stallId, where, phase.get(), ms, gcSince());
	}

	private void run() {
		long seen = -1, nextDumpAt = 0;
		int dumps = 0;
		try {
			while (true) {
				Thread.sleep(100);
				Thread st = serverThread;
				if (st == null || tickEnd == 0) continue;
				long id = stallId;
				boolean in = inTick;
				long since = (System.nanoTime() - (in ? tickStart : tickEnd)) / 1_000_000L;
				if (since < LIMIT_MS) continue;
				if (id != seen) { seen = id; dumps = 0; nextDumpAt = 0; }
				if (dumps >= MAX_DUMPS || since < nextDumpAt) continue;
				Boolean a;
				try { a = armed.get(); } catch (RuntimeException e) { a = Boolean.TRUE; }
				if (!Boolean.TRUE.equals(a)) continue;
				StringBuilder b = new StringBuilder();
				b.append(prefix).append(" long tick #").append(id).append(in ? " (in tick" : " (between ticks").append(", phase ").append(phase.get())
					.append(", ").append(since).append(" ms so far, dump ").append(dumps + 1).append("), ").append(gcSince()).append(", server thread ").append(st.getState()).append(':');
				StackTraceElement[] tr = st.getStackTrace();
				int depth = dumps == 0 ? 90 : 40;
				for (int i = 0; i < Math.min(depth, tr.length); i++) b.append("\n    at ").append(tr[i]);
				FractalBuildingsMod.LOGGER.info(b.toString());
				if (dumps == 0) workers();
				dumps++;
				nextDumpAt = since + 1000;
			}
		} catch (InterruptedException ignored) { }
	}

	/** The running chunk workers and pool threads (not parked), with up to 30 frames each. */
	private void workers() {
		StringBuilder b = new StringBuilder(prefix).append(" long tick workers:");
		int n = 0;
		for (ThreadInfo ti : ManagementFactory.getThreadMXBean().dumpAllThreads(true, false)) {
			String name = ti.getThreadName();
			if (!(name.startsWith("Worker-") || name.startsWith("fractalbuildings") || name.startsWith("IO-Worker") || name.contains("Chunk"))) continue;
			StackTraceElement[] st = ti.getStackTrace();
			if (st.length == 0) continue;
			if (ti.getThreadState() != Thread.State.RUNNABLE && !busy(st)) continue; // idle pool thread
			b.append("\n  ").append(name).append(' ').append(ti.getThreadState());
			if (ti.getLockName() != null) b.append(" on ").append(ti.getLockName());
			if (ti.getLockOwnerName() != null) b.append(" owned by ").append(ti.getLockOwnerName());
			for (int i = 0; i < Math.min(30, st.length); i++) b.append("\n      at ").append(st[i]);
			n++;
		}
		b.append("\n  (").append(n).append(" threads shown)");
		FractalBuildingsMod.LOGGER.info(b.toString());
	}

	/** A frame of ours or of Minecraft outside its thread-pool loops: the thread is doing (or waiting inside) real work. */
	private static boolean busy(StackTraceElement[] st) {
		for (StackTraceElement e : st) {
			String c = e.getClassName();
			if (c.startsWith("dev.fractalbuildings.") && !c.contains("LongTickWatch")) return true;
			if (c.startsWith("net.minecraft.") && !c.startsWith("net.minecraft.util.thread.") && !c.startsWith("net.minecraft.TracingExecutor") && !c.startsWith("net.minecraft.Util")) return true;
		}
		return false;
	}
}
