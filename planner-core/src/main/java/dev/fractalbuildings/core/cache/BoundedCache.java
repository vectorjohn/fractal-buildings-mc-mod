package dev.fractalbuildings.core.cache;

import java.util.LinkedHashMap;
import java.util.function.Function;

/**
 * A bounded, thread-safe LRU memo for pure functions (ADR 0003 D6). {@link #get} returns what the function returns, whether the
 * value was cached, evicted or never seen: the function runs outside the lock, so it may call back into the same cache or others,
 * and two threads that miss together both compute (equal) values and one wins. The contract is on the function: it must depend on
 * the key alone. Capacity only changes how often it is recomputed.
 */
public final class BoundedCache<K, V> {
	private final int capacity;
	private final LinkedHashMap<K, V> map = new LinkedHashMap<>(16, 0.75f, true);
	private long hits, misses;

	public BoundedCache(int capacity) {
		if (capacity < 1) throw new IllegalArgumentException("capacity < 1");
		this.capacity = capacity;
	}

	public V get(K key, Function<K, V> compute) {
		synchronized (map) {
			V v = map.get(key);
			if (v != null) {
				hits++;
				return v;
			}
			misses++;
		}
		V v = compute.apply(key);
		synchronized (map) {
			V prev = map.get(key);
			if (prev != null) return prev; // another thread finished first: keep one instance for identity-based callers
			map.put(key, v);
			while (map.size() > capacity) {
				var it = map.entrySet().iterator();
				it.next();
				it.remove();
			}
		}
		return v;
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
