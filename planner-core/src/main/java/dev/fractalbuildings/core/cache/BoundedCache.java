package dev.fractalbuildings.core.cache;

import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * A bounded, thread-safe LRU memo for pure functions (ADR 0003 D6). {@link #get} returns what the function returns, whether the
 * value was cached, evicted or never seen: the function runs outside the lock, so it may call back into the same cache or others,
 * and two threads that miss together both compute (equal) values and one wins. The contract is on the function: it must depend on
 * the key alone. Capacity only changes how often it is recomputed.
 *
 * <p>Optional retention: an entry used within {@code graceNanos} is not evicted for capacity, up to {@code capacity * hardFactor}
 * entries (then the eldest goes regardless). Protection expires by itself, so nothing can be pinned for ever; it only decides what
 * is kept, never what is answered.
 */
public final class BoundedCache<K, V> {
	private static final class Slot<V> {
		final V value;
		long lastUse;

		Slot(V value, long lastUse) {
			this.value = value;
			this.lastUse = lastUse;
		}
	}

	private final int capacity;
	private final long graceNanos;
	private final int hardCapacity;
	private final LongSupplier clock;
	private final LinkedHashMap<K, Slot<V>> map = new LinkedHashMap<>(16, 0.75f, true);
	private long hits, misses;

	public BoundedCache(int capacity) {
		this(capacity, 0, 1, System::nanoTime); // det-ok: retention only, never an answer
	}

	public BoundedCache(int capacity, long graceNanos, int hardFactor, LongSupplier clock) {
		if (capacity < 1) throw new IllegalArgumentException("capacity < 1");
		if (graceNanos < 0 || hardFactor < 1) throw new IllegalArgumentException("retention");
		this.capacity = capacity;
		this.graceNanos = graceNanos;
		this.hardCapacity = (int) Math.min(Integer.MAX_VALUE, (long) capacity * hardFactor);
		this.clock = clock;
	}

	private void trim(long now) {
		var it = map.entrySet().iterator();
		while (map.size() > capacity && it.hasNext()) {
			Slot<V> eldest = it.next().getValue();
			if (map.size() <= hardCapacity && now - eldest.lastUse < graceNanos) break; // access order: the rest are newer
			it.remove();
		}
	}

	public V get(K key, Function<K, V> compute) {
		synchronized (map) {
			long now = clock.getAsLong();
			Slot<V> hit = map.get(key);
			if (hit != null) {
				hits++;
				hit.lastUse = now;
				trim(now);
				return hit.value;
			}
			misses++;
		}
		V v = compute.apply(key);
		synchronized (map) {
			long now = clock.getAsLong();
			Slot<V> prev = map.get(key);
			if (prev != null) { // another thread finished first: keep one instance for identity-based callers
				prev.lastUse = now;
				return prev.value;
			}
			map.put(key, new Slot<>(v, now));
			trim(now);
		}
		return v;
	}

	/** Keys being computed by {@link #getOnce}, made on first use. */
	private volatile java.util.concurrent.ConcurrentHashMap<K, java.util.concurrent.CompletableFuture<V>> flights;

	/**
	 * {@link #get}, but a thread that misses while another computes the same key waits for that value instead of computing it again
	 * (PARREADS: several threads filling one memo). Only for functions that never wait, directly or not, on a key that waits on them (a leaf
	 * such as a terrain chunk or a cell decision, or an acyclic graph): a waiter waits for a computation that is running. The answer is the
	 * same as {@link #get}'s; a failure reaches every waiter and caches nothing. A waiter counts a miss, so {@link #misses} still counts asks.
	 */
	public V getOnce(K key, Function<K, V> compute) {
		return get(key, k -> {
			var fl = flights;
			if (fl == null) {
				synchronized (map) {
					if ((fl = flights) == null) flights = fl = new java.util.concurrent.ConcurrentHashMap<>();
				}
			}
			java.util.concurrent.CompletableFuture<V> mine = new java.util.concurrent.CompletableFuture<>();
			java.util.concurrent.CompletableFuture<V> other = fl.putIfAbsent(k, mine);
			if (other != null) {
				try {
					return other.join();
				} catch (java.util.concurrent.CompletionException e) {
					if (e.getCause() instanceof RuntimeException re) throw re;
					if (e.getCause() instanceof Error er) throw er;
					throw e;
				}
			}
			try {
				// stored before the flight is removed (finally), and re-checked after winning the claim: a thread that missed just before
				// another's store and claims just after its remove finds the value instead of computing it again
				V cached = peek(k);
				if (cached != null) {
					mine.complete(cached);
					return cached;
				}
				V v = putIfAbsent(k, compute.apply(k));
				mine.complete(v);
				return v;
			} catch (Throwable e) {
				mine.completeExceptionally(e);
				throw e;
			} finally {
				fl.remove(k, mine);
			}
		});
	}

	/** The cached value or null; counts no hit or miss and leaves the retention timestamp alone, but (access-order map) moves the entry to the LRU tail. */
	public V peek(K key) {
		synchronized (map) {
			Slot<V> s = map.get(key);
			return s == null ? null : s.value;
		}
	}

	/** Stores {@code v} unless a value is already there; returns the value now cached (the first instance wins). */
	public V putIfAbsent(K key, V v) {
		synchronized (map) {
			long now = clock.getAsLong();
			Slot<V> prev = map.get(key);
			if (prev != null) {
				prev.lastUse = now;
				return prev.value;
			}
			map.put(key, new Slot<>(v, now));
			trim(now);
			return v;
		}
	}

	/** Drops every entry (memory pressure, tests); what is asked next is recomputed, so answers do not change. */
	public void clear() {
		synchronized (map) { map.clear(); }
	}

	public int size() {
		synchronized (map) { return map.size(); }
	}

	public int capacity() { return capacity; }

	public long hits() {
		synchronized (map) { return hits; }
	}

	public long misses() {
		synchronized (map) { return misses; }
	}
}
