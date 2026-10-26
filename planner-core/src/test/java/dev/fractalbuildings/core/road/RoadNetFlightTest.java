package dev.fractalbuildings.core.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.fractalbuildings.core.cache.BoundedCache;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The flight/cache hand-off of RoadNet.flight, with the racy interleaving forced instead of hoped for. */
class RoadNetFlightTest {
	/**
	 * Thread B arrives exactly when A's flight has just been removed. With the old order (remove, then store) B missed the cache,
	 * claimed a fresh flight and computed again; now the value is stored before the remove, so B must see it.
	 */
	@Test
	void claimerAfterRemoveFindsTheStoredValue() {
		BoundedCache<String, Object> cache = new BoundedCache<>(16);
		AtomicInteger computes = new AtomicInteger();
		Object[] late = new Object[1];
		ConcurrentHashMap<String, CompletableFuture<Object>> flights = new ConcurrentHashMap<>() {
			private boolean armed = true;

			@Override
			public boolean remove(Object key, Object value) {
				boolean r = super.remove(key, value);
				if (armed) { // B runs right after A's remove, before A's call returns
					armed = false;
					late[0] = RoadNet.flight(this, cache, "k", k -> { computes.incrementAndGet(); return new Object(); });
				}
				return r;
			}
		};
		Object a = RoadNet.flight(flights, cache, "k", k -> { computes.incrementAndGet(); return new Object(); });
		assertEquals(1, computes.get(), "the key is computed exactly once");
		assertSame(a, late[0], "the late claimer gets the same instance");
		assertSame(a, cache.peek("k"));
	}

	/**
	 * The diagnosed race: B misses the cache before A's store, then claims after A's remove. B's claim (first putIfAbsent on the
	 * flight map) first runs A's whole flight on the same thread; B must then find A's value by its re-check, not compute again.
	 */
	@Test
	void claimAfterAnotherFlightFinishedRechecksTheCache() {
		BoundedCache<String, Object> cache = new BoundedCache<>(16);
		AtomicInteger computes = new AtomicInteger();
		Object[] a = new Object[1];
		ConcurrentHashMap<String, CompletableFuture<Object>> flights = new ConcurrentHashMap<>() {
			private boolean armed = true;

			@Override
			public CompletableFuture<Object> putIfAbsent(String key, CompletableFuture<Object> value) {
				if (armed) {
					armed = false;
					a[0] = RoadNet.flight(this, cache, "k", k -> { computes.incrementAndGet(); return new Object(); });
				}
				return super.putIfAbsent(key, value);
			}
		};
		Object b = RoadNet.flight(flights, cache, "k", k -> { computes.incrementAndGet(); return new Object(); });
		assertEquals(1, computes.get(), "the key is computed exactly once");
		assertSame(a[0], b);
	}
}
