package dev.fractalbuildings.core.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Timeout(value = 120, unit = TimeUnit.SECONDS, threadMode = org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD) // a caller that waits for helpers that never start ignores the interrupt
/** PARREADS: the caller-runs fanout runs every piece once, never deadlocks on its own pool, and passes failures on after all pieces end. */
class FanoutTest {
	@Test
	void everyPieceRunsOnce() {
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			Fanout f = Fanout.over(() -> pool, 4);
			assertEquals(5, f.threads());
			AtomicIntegerArray runs = new AtomicIntegerArray(1000);
			f.each(1000, runs::incrementAndGet);
			for (int i = 0; i < 1000; i++) assertEquals(1, runs.get(i), "piece " + i);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void noPoolIsSerialInOrder() {
		Fanout f = Fanout.over(() -> null, 4);
		assertEquals(1, f.threads());
		StringBuilder sb = new StringBuilder();
		f.each(5, i -> sb.append(i));
		assertEquals("01234", sb.toString());
	}

	/** A pool whose only thread is stuck: the caller does every piece itself instead of waiting for a helper that never starts. */
	@Test
	void aSaturatedPoolDoesNotHoldTheCaller() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(1);
		CountDownLatch release = new CountDownLatch(1), busy = new CountDownLatch(1);
		pool.execute(() -> { busy.countDown(); try { release.await(); } catch (InterruptedException ignored) { } });
		assertTrue(busy.await(10, TimeUnit.SECONDS));
		try {
			AtomicInteger n = new AtomicInteger();
			Fanout.over(() -> pool, 3).each(50, i -> n.incrementAndGet());
			assertEquals(50, n.get());
		} finally {
			release.countDown();
			pool.shutdownNow();
		}
	}

	/** Pieces that fan out again on the same one-thread pool, called from that pool's thread (a worker planning a tile): no deadlock. */
	@Test
	void nestedFanoutsOnTheirOwnPoolFinish() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(1);
		try {
			Fanout f = Fanout.over(() -> pool, 2);
			AtomicInteger leaves = new AtomicInteger();
			Future<?> done = pool.submit(() -> f.each(6, i -> f.each(6, j -> f.each(3, k -> leaves.incrementAndGet()))));
			done.get(60, TimeUnit.SECONDS);
			assertEquals(108, leaves.get());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void aStoppedPoolMeansTheCallerDoesItAll() {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		pool.shutdownNow();
		AtomicInteger n = new AtomicInteger();
		Fanout.over(() -> pool, 2).each(10, i -> n.incrementAndGet());
		assertEquals(10, n.get());
	}

	@Test
	void aFailureIsRethrownAfterThePiecesStop() {
		ExecutorService pool = Executors.newFixedThreadPool(3);
		try {
			AtomicInteger running = new AtomicInteger();
			IllegalStateException e = assertThrows(IllegalStateException.class, () -> Fanout.over(() -> pool, 3).each(40, i -> {
				running.incrementAndGet();
				try {
					if (i == 7) throw new IllegalStateException("piece 7");
					Thread.sleep(1);
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
				} finally {
					running.decrementAndGet();
				}
			}));
			assertEquals("piece 7", e.getMessage());
			assertEquals(0, running.get(), "no piece still running when each() returns");
		} finally {
			pool.shutdownNow();
		}
	}

	/** getOnce: threads that miss together on one key compute it once and all get that instance. */
	@Test
	void getOnceComputesAKeyOnce() throws Exception {
		BoundedCache<Integer, int[]> c = new BoundedCache<>(16);
		AtomicInteger computed = new AtomicInteger();
		CountDownLatch inside = new CountDownLatch(1), go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			java.util.List<Future<int[]>> fs = new java.util.ArrayList<>();
			fs.add(pool.submit(() -> c.getOnce(1, k -> {
				computed.incrementAndGet();
				inside.countDown();
				try { go.await(); } catch (InterruptedException ignored) { }
				return new int[] {k};
			})));
			assertTrue(inside.await(10, TimeUnit.SECONDS));
			for (int i = 0; i < 7; i++) fs.add(pool.submit(() -> c.getOnce(1, k -> { computed.incrementAndGet(); return new int[] {k}; })));
			Thread.sleep(50); // let the others reach the flight
			go.countDown();
			int[] first = fs.get(0).get(10, TimeUnit.SECONDS);
			for (Future<int[]> f : fs) assertSame(first, f.get(10, TimeUnit.SECONDS));
			assertEquals(1, computed.get());
		} finally {
			pool.shutdownNow();
		}
	}
}
