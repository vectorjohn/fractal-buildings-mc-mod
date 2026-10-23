package dev.fractalbuildings.core.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * A bounded LRU memo of pure functions with one future per key (the {@code CachedIrSource} pattern): threads that ask for a key
 * while another computes it wait for that result instead of computing it again, and nothing is computed while the map lock is held.
 * The first caller to reach an entry runs the task itself, so a waiter never depends on an idle worker. A task that throws removes
 * its entry and rethrows in every waiter. Eviction (also of an entry still being computed) only costs a recompute: the function
 * must depend on the key alone.
 *
 * <p>Retention (the multi-player fix): an entry used within the last {@code graceNanos} is not evicted for capacity, so a tile whose
 * chunks are still being generated (and the in-flight entry another thread is computing) survives other tiles' inserts. That lets
 * the map grow past {@code capacity}, but never past {@code capacity * hardFactor}: beyond that the eldest goes regardless. Nothing
 * is pinned for ever: protection is just "touched recently", so a stuck worker or a leaked reader releases by itself after the
 * grace period, with no release call to forget. The clock only decides what is kept, never what is answered.
 */
final class FlightCache<K, V> {
	private static final class Entry<V> {
		final CompletableFuture<V> future = new CompletableFuture<>();
		final AtomicBoolean started = new AtomicBoolean();
		long lastUse; // guarded by the map lock
	}

	private final int capacity;
	private final long graceNanos;
	private final int hardCapacity;
	private final LongSupplier clock;
	private final LinkedHashMap<K, Entry<V>> map = new LinkedHashMap<>(16, 0.75f, true);
	/** Told how each lookup was served (counters only; never changes an answer). */
	volatile Consumer<WorldPlanner.Lookup> observer;

	FlightCache(int capacity) {
		this(capacity, 0, 1, System::nanoTime); // det-ok: retention only, never an answer
	}

	FlightCache(int capacity, long graceNanos, int hardFactor, LongSupplier clock) {
		if (capacity < 1) throw new IllegalArgumentException("capacity < 1");
		if (graceNanos < 0 || hardFactor < 1) throw new IllegalArgumentException("retention");
		this.capacity = capacity;
		this.graceNanos = graceNanos;
		this.hardCapacity = (int) Math.min(Integer.MAX_VALUE, (long) capacity * hardFactor);
		this.clock = clock;
	}

	/** Evicts from the eldest end while over capacity, sparing recently used entries until the hard ceiling. Holds the map lock. */
	private void trim(Entry<V> keep, long now) {
		var it = map.entrySet().iterator();
		while (map.size() > capacity && it.hasNext()) {
			Map.Entry<K, Entry<V>> eldest = it.next();
			if (eldest.getValue() == keep) break;
			if (map.size() <= hardCapacity && now - eldest.getValue().lastUse < graceNanos) break; // access order: the rest are newer
			it.remove();
		}
	}

	V get(K key, Function<K, V> compute) {
		Entry<V> e;
		boolean wasDone = false;
		synchronized (map) {
			long now = clock.getAsLong();
			e = map.get(key);
			if (e != null) wasDone = e.future.isDone();
			else {
				e = new Entry<>();
				map.put(key, e);
			}
			e.lastUse = now;
			trim(e, now);
		}
		boolean ran = e.started.compareAndSet(false, true);
		Consumer<WorldPlanner.Lookup> obs = observer;
		if (obs != null) obs.accept(ran ? WorldPlanner.Lookup.INLINE : wasDone ? WorldPlanner.Lookup.DONE : WorldPlanner.Lookup.INFLIGHT);
		if (ran) {
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
