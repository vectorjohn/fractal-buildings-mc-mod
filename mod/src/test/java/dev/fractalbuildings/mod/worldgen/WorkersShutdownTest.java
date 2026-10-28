package dev.fractalbuildings.mod.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * TESTLAYERS: stopping the workers never holds the stopping (server) thread for the length of a running task. Planner tasks are CPU work that
 * only checks the interrupt flag between steps, so the old 10 s wait at every world close ("workers did not stop within 10 s") was the length of
 * the longest running step. Now: an idle pool is gone at once; a pool whose task ignores the interrupt returns within the grace, the queue is
 * dropped, and the thread ends with its task.
 */
class WorkersShutdownTest {

	@Test
	void anIdlePoolStopsAtOnceAndLeavesNoThreads() throws Exception {
		// WORKERSFLAKE: only this pool's own threads are checked (a JVM-wide count of PREFIX threads also saw pools of other test classes in the
		// same fork), and each is joined, since a thread can still be leaving its run loop just after the pool reports terminated.
		Set<Thread> mine = ConcurrentHashMap.newKeySet();
		ThreadPoolExecutor p = Workers.create(4);
		CountDownLatch ran = new CountDownLatch(4);
		for (int i = 0; i < 4; i++) p.execute(() -> { mine.add(Thread.currentThread()); ran.countDown(); });
		assertTrue(ran.await(10, TimeUnit.SECONDS));
		Workers.awaitIdle(p, 5_000);
		Workers.shutdown(p);
		// no wall-clock bound on shutdown: it is load-dependent; the grace path itself is covered by the two tests below
		assertTrue(Workers.awaitTerminated(p, 10_000), "an idle pool must terminate");
		assertFalse(mine.isEmpty());
		for (Thread t : mine) {
			t.join(10_000);
			assertFalse(t.isAlive(), "worker thread left: " + t.getName());
		}
	}

	@Test
	void aTaskThatIgnoresTheInterruptDoesNotHoldTheStopper() throws Exception {
		ThreadPoolExecutor p = Workers.create(1);
		CountDownLatch started = new CountDownLatch(1);
		AtomicBoolean queuedRan = new AtomicBoolean();
		CountDownLatch release = new CountDownLatch(1);
		p.execute(() -> {
			started.countDown();
			while (true) { // a step that never looks at the interrupt flag (a latch, not a spin: the test must not depend on the machine's load)
				try { if (release.await(60, TimeUnit.SECONDS)) return; } catch (InterruptedException ignored) { /* swallowed, like a CPU-bound step */ }
			}
		});
		p.execute(() -> queuedRan.set(true)); // queued behind it: dropped by the shutdown
		assertTrue(started.await(10, TimeUnit.SECONDS));
		long t0 = System.nanoTime();
		Workers.shutdown(p);
		long ms = (System.nanoTime() - t0) / 1_000_000;
		assertTrue(ms < 5_000, "the stopper waited " + ms + " ms for a running task (grace " + Workers.GRACE_MS + "; the old wait was 10 s)");
		release.countDown();
		assertTrue(Workers.awaitTerminated(p, 30_000), "the thread must end with its task");
		assertFalse(queuedRan.get(), "queued work is dropped");
	}

	@Test
	void aTaskThatChecksTheFlagBetweenStepsEndsAtOnce() throws Exception {
		ThreadPoolExecutor p = Workers.create(1);
		CountDownLatch started = new CountDownLatch(1);
		AtomicBoolean secondStep = new AtomicBoolean();
		p.execute(() -> {
			started.countDown();
			while (!Thread.currentThread().isInterrupted()) Thread.onSpinWait(); // step 1 runs until the stop interrupts it
			if (Thread.currentThread().isInterrupted()) return; // the checkpoint WorldPlanner.prefetch and RegionWarmer have
			secondStep.set(true);
		});
		assertTrue(started.await(10, TimeUnit.SECONDS));
		Workers.shutdown(p);
		assertTrue(Workers.awaitTerminated(p, 5_000));
		assertFalse(secondStep.get());
	}
}
