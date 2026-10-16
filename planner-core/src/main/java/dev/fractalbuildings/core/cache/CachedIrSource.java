package dev.fractalbuildings.core.cache;

import dev.fractalbuildings.core.gen.ShellSource;
import dev.fractalbuildings.core.gen.v0.ShellGeneratorV0;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.SpecHash;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * LRU cache of shells keyed by {@link SpecHash}. A pure cache (ADR 0003 D6): a hit returns the same value a
 * miss would compute, because shells depend only on the spec. One future per key; the build never runs
 * while holding the map lock. A hash collision (same key, different spec) bypasses the cache.
 */
public final class CachedIrSource implements IrSource {
	private record Entry(BuildingSpec spec, BuildingIr ir) {}

	private final int capacity;
	private final Executor executor;
	private final ShellSource builder;
	private final LinkedHashMap<String, CompletableFuture<Entry>> map = new LinkedHashMap<>(16, 0.75f, true);

	public CachedIrSource(int capacity, Executor executor) { this(capacity, executor, ShellGeneratorV0::generate); }

	/** {@code executor} may be null: prefetch then builds inline. */
	public CachedIrSource(int capacity, Executor executor, ShellSource builder) {
		if (capacity < 1) throw new IllegalArgumentException("capacity < 1");
		this.capacity = capacity;
		this.executor = executor;
		this.builder = builder;
	}

	@Override
	public BuildingIr get(BuildingSpec spec) {
		String key = SpecHash.of(spec);
		CompletableFuture<Entry> f;
		boolean mine;
		synchronized (map) {
			f = map.get(key);
			mine = f == null;
			if (mine) { f = insert(key); }
		}
		if (mine) { run(key, spec, f); }
		Entry e = await(key, f);
		return e.spec().equals(spec) ? e.ir() : builder.shell(spec);
	}

	@Override
	public void prefetch(BuildingSpec spec) {
		String key = SpecHash.of(spec);
		CompletableFuture<Entry> f;
		synchronized (map) {
			if (map.containsKey(key)) return;
			f = insert(key);
		}
		if (executor == null) run(key, spec, f);
		else {
			try {
				executor.execute(() -> run(key, spec, f));
			} catch (RuntimeException | Error t) { // rejected: never leave an uncompleted future behind
				synchronized (map) { map.remove(key, f); }
				f.completeExceptionally(t);
			}
		}
	}

	public int size() { synchronized (map) { return map.size(); } }

	public void clear() { synchronized (map) { map.clear(); } }

	private CompletableFuture<Entry> insert(String key) {
		CompletableFuture<Entry> f = new CompletableFuture<>();
		map.put(key, f);
		var it = map.entrySet().iterator();
		while (map.size() > capacity && it.hasNext()) {
			Map.Entry<String, CompletableFuture<Entry>> eldest = it.next();
			if (eldest.getValue() != f) it.remove();
			else break;
		}
		return f;
	}

	private void run(String key, BuildingSpec spec, CompletableFuture<Entry> f) {
		try {
			f.complete(new Entry(spec, builder.shell(spec)));
		} catch (Throwable t) {
			synchronized (map) { map.remove(key, f); }
			f.completeExceptionally(t);
		}
	}

	private Entry await(String key, CompletableFuture<Entry> f) {
		try {
			return f.join();
		} catch (CompletionException ex) {
			if (ex.getCause() instanceof RuntimeException re) throw re;
			if (ex.getCause() instanceof Error er) throw er;
			throw ex;
		}
	}
}
