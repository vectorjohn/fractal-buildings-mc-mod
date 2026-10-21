package dev.fractalbuildings.core.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class BoundedCacheTest {
	@Test
	void sizeNeverExceedsCapacityAndTheLeastRecentlyUsedGoes() {
		BoundedCache<Integer, Integer> c = new BoundedCache<>(3);
		AtomicInteger calls = new AtomicInteger();
		Function<Integer, Integer> f = k -> { calls.incrementAndGet(); return k * 2; };
		for (int i = 0; i < 1000; i++) {
			assertEquals(i * 2, c.get(i, f));
			assertTrue(c.size() <= 3, "size " + c.size());
		}
		assertEquals(3, c.size());
		c.get(999, f);
		assertEquals(1000, calls.get(), "999 is still cached");
		c.get(0, f);
		assertEquals(1001, calls.get(), "0 was evicted and is recomputed");
	}

	@Test
	void concurrentGetsStayBoundedAndCorrect() throws Exception {
		BoundedCache<Integer, Integer> c = new BoundedCache<>(8);
		ExecutorService ex = Executors.newFixedThreadPool(8);
		try {
			List<Future<Integer>> fs = new ArrayList<>();
			for (int i = 0; i < 4000; i++) { int k = (i * 7919) % 500; fs.add(ex.submit(() -> c.get(k, x -> x + 1))); }
			for (int i = 0; i < fs.size(); i++) assertEquals((i * 7919) % 500 + 1, fs.get(i).get());
		} finally {
			ex.shutdown();
		}
		assertTrue(c.size() <= 8, "size " + c.size());
	}

	@Test
	void theFirstValueStoredWinsARace() {
		BoundedCache<String, Object> c = new BoundedCache<>(4);
		Object first = c.get("k", k -> {
			// a second computation of the same key finishes while this one runs
			Object inner = c.get("k", x -> new Object());
			assertSame(inner, c.get("k", x -> new Object()));
			return new Object();
		});
		assertSame(first, c.get("k", x -> new Object()), "one instance per key once stored");
	}

	@Test
	void theFunctionMayCallBackIntoTheCache() {
		BoundedCache<Integer, Long> fib = new BoundedCache<>(2);
		var h = new Object() { Function<Integer, Long> f; };
		h.f = n -> n < 2 ? (long) n : fib.get(n - 1, h.f) + fib.get(n - 2, h.f);
		assertEquals(6765L, fib.get(20, h.f));
		assertTrue(fib.size() <= 2);
		assertThrows(IllegalArgumentException.class, () -> new BoundedCache<Integer, Integer>(0));
	}
}
