package dev.fractalbuildings.mod.worldgen;

import static org.junit.jupiter.api.Assertions.*;

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
	private static long aliveWorkers() {
		return Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().startsWith(Workers.PREFIX) && t.isAlive()).count();
	}

	@Test
	void anIdlePoolStopsAtOnceAndLeavesNoThreads() throws Exception {
		long before = aliveWorkers();
		ThreadPoolExecutor p = Workers.create(4);
		CountDownLatch ran = new CountDownLatch(4);
		for (int i = 0; i < 4; i++) p.execute(ran::countDown);
		assertTrue(ran.await(10, TimeUnit.SECONDS));
		Workers.awaitIdle(p, 5_000);
		long t0 = System.nanoTime();
		Workers.shutdown(p);
		assertTrue((System.nanoTime() - t0) / 1_000_000 < Workers.GRACE_MS * 2, "an idle pool must stop within the grace");
		assertTrue(p.isTerminated());
		assertEquals(before, aliveWorkers(), "worker threads left");
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
