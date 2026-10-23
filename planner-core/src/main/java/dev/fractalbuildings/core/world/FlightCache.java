package dev.fractalbuildings.core.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A bounded LRU memo of pure functions with one future per key (the {@code CachedIrSource} pattern): threads that ask for a key
 * while another computes it wait for that result instead of computing it again, and nothing is computed while the map lock is held.
 * The first caller to reach an entry runs the task itself, so a waiter never depends on an idle worker. A task that throws removes
 * its entry and rethrows in every waiter. Eviction (also of an entry still being computed) only costs a recompute: the function
 * must depend on the key alone.
 */
final class FlightCache<K, V> {
	private static final class Entry<V> {
		final CompletableFuture<V> future = new CompletableFuture<>();
		final AtomicBoolean started = new AtomicBoolean();
	}

	private final int capacity;
	private final LinkedHashMap<K, Entry<V>> map = new LinkedHashMap<>(16, 0.75f, true);

	FlightCache(int capacity) {
		if (capacity < 1) throw new IllegalArgumentException("capacity < 1");
		this.capacity = capacity;
	}

	V get(K key, Function<K, V> compute) {
		Entry<V> e;
		synchronized (map) {
			e = map.get(key);
			if (e == null) {
				e = new Entry<>();
				map.put(key, e);
				var it = map.entrySet().iterator();
				while (map.size() > capacity && it.hasNext()) {
					Map.Entry<K, Entry<V>> eldest = it.next();
					if (eldest.getValue() != e) it.remove();
					else break; // capacity 1 and this entry is the only one: keep it
				}
			}
		}
		if (e.started.compareAndSet(false, true)) {
			try {
				e.future.complete(compute.apply(key));
			} catch (Throwable t) {
				synchronized (map) { map.remove(key, e); }
				e.future.completeExceptionally(t);
			}
		}
		try {
			return e.future.join();
		} catch (CompletionException ex) {
			if (ex.getCause() instanceof RuntimeException r) throw r;
			if (ex.getCause() instanceof Error r) throw r;
			throw ex;
		}
	}

	void clear() {
		synchronized (map) { map.clear(); }
	}

	int size() {
		synchronized (map) { return map.size(); }
	}
}
