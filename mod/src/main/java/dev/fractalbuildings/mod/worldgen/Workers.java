package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * The shared worker pool of prefetch and warming (plan B4, P2): at most 4 daemon threads named {@code fractalbuildings-worker-N} at low
 * priority (S6: 4 workers cost 3 % of chunk throughput, 8 cost 22 %). The queue is bounded: when it is full a task is refused, which
 * prefetch and warming treat as "not now" (they never change an answer). Started with the server, shut down when it stops.
 */
public final class Workers {
	public static final String PREFIX = "fractalbuildings-worker-";
	private static final int QUEUE = 512;
	private static volatile ThreadPoolExecutor current;

	private Workers() {}

	/** Pool size: a quarter of the cpus, 1 to 4 (the chunk threads need the rest). */
	public static int defaultSize() { return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 4)); }

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> start());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
	}

	/** Starts the server's pool and hands it to {@link WorldPlanners}. A pool left from a crashed run is replaced. */
	public static synchronized void start() {
		stop();
		ThreadPoolExecutor p = create(defaultSize());
		current = p;
		WorldPlanners.setWorkers(p);
	}

	public static synchronized void stop() {
		ThreadPoolExecutor p = current;
		current = null;
		WorldPlanners.setWorkers(null);
		if (p != null) shutdown(p);
	}

	/** The running pool, or null. */
	public static ThreadPoolExecutor pool() { return current; }

	/** A pool of the given size with the production settings (tests make their own). */
	public static ThreadPoolExecutor create(int threads) {
		AtomicInteger n = new AtomicInteger();
		ThreadPoolExecutor p = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE), r -> {
			Thread t = new Thread(r, PREFIX + n.getAndIncrement());
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		});
		p.allowCoreThreadTimeOut(true);
		return p;
	}

	/** How long the stopping thread waits for the workers before it hands the wait to a reaper. */
	static final long GRACE_MS = 250;

	/**
	 * Drops queued work and interrupts the threads. The planner's tasks are pure CPU (a tile's plan or ground, seconds each, cold and under load
	 * many seconds) and only look at the interrupt flag between steps ({@code WorldPlanner.prefetch}, {@code RegionWarmer}), so a task that is
	 * mid-step cannot be stopped; it ends with the step, touches no world state, and its thread is a daemon. TESTLAYERS: the server thread used to
	 * wait 10 s for it at every world close (nine times in the client suite, "workers did not stop within 10 s"); now it waits {@link #GRACE_MS}
	 * and a daemon reaper thread reports a straggler that outlives {@link #REAP_SECONDS}. A pool that is idle (the normal case) is gone at once.
	 */
	public static void shutdown(ExecutorService p) {
		p.shutdownNow();
		try {
			if (p.awaitTermination(GRACE_MS, TimeUnit.MILLISECONDS)) return;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		FractalBuildingsMod.LOGGER.debug("Fractal Buildings workers are finishing their current step; the stop does not wait for them");
		Thread reaper = new Thread(() -> {
			try {
				if (!p.awaitTermination(REAP_SECONDS, TimeUnit.SECONDS)) FractalBuildingsMod.LOGGER.warn("Fractal Buildings workers still running {} s after the stop", REAP_SECONDS);
			} catch (InterruptedException ignored) {
				// the JVM is going down
			}
		}, "fractalbuildings-reaper");
		reaper.setDaemon(true);
		reaper.start();
	}

	static final long REAP_SECONDS = 120;

	/** Tests: waits until the pool has terminated, up to the timeout in ms. */
	public static boolean awaitTerminated(ExecutorService p, long timeoutMs) {
		try {
			return p.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** Tests: waits until the pool has no queued or running task (or the timeout, in ms, passes). */
	public static boolean awaitIdle(ThreadPoolExecutor p, long timeoutMs) {
		long end = System.nanoTime() + timeoutMs * 1_000_000L;
		while (p.getActiveCount() > 0 || !p.getQueue().isEmpty()) {
			if (System.nanoTime() > end) return false;
			try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
		}
		return true;
	}
}
