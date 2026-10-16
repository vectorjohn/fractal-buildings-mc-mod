package dev.fractalbuildings.core.determinism;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.cache.CachedIrSource;
import dev.fractalbuildings.core.cache.DirectIrSource;
import dev.fractalbuildings.core.cache.IrSource;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v0.GeneratorV0;
import dev.fractalbuildings.core.gen.v0.ShellGeneratorV0;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.SceneDoc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DeterminismTest {
	static final int N = 500;

	static String json(GeneratorV0 g, GenRequest rq) { return IrJson.write(g.generate(rq)); }

	static List<GenRequest> requests() {
		List<GenRequest> l = new ArrayList<>();
		for (long s = 0; s < N; s++) l.add(Cases.of(s));
		return l;
	}

	static List<String> reference() {
		GeneratorV0 g = new GeneratorV0();
		List<String> out = new ArrayList<>();
		for (GenRequest rq : requests()) out.add(json(g, rq));
		return out;
	}

	/** Runs every request in shuffled order on a pool; returns JSON indexed by request. */
	static List<String> shuffledParallel(Supplier<GeneratorV0> gen, int threads, long shuffleSeed, Runnable before) throws Exception {
		List<GenRequest> rqs = requests();
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < rqs.size(); i++) order.add(i);
		Collections.shuffle(order, new Random(shuffleSeed));
		GeneratorV0 g = gen.get();
		if (before != null) before.run();
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<String>> fs = new ArrayList<>(Collections.nCopies(rqs.size(), null));
			for (int i : order) fs.set(i, pool.submit(() -> json(g, rqs.get(i))));
			List<String> out = new ArrayList<>();
			for (Future<String> f : fs) out.add(f.get());
			return out;
		} finally {
			pool.shutdown();
		}
	}

	static void assertSame(List<String> expected, List<String> actual, String what) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) assertEquals(expected.get(i), actual.get(i), what + " request " + i);
	}

	@Test
	void sameRequestTwiceIsByteIdentical() {
		for (long s = 0; s < 50; s++) {
			GenRequest rq = Cases.of(s);
			assertEquals(json(new GeneratorV0(), rq), json(new GeneratorV0(), rq));
		}
	}

	@Test
	void orderAndThreadsDoNotMatter() throws Exception {
		List<String> ref = reference();
		List<String> seqShuffled = new ArrayList<>(Collections.nCopies(N, null));
		List<GenRequest> rqs = requests();
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < N; i++) order.add(i);
		Collections.shuffle(order, new Random(7));
		GeneratorV0 g = new GeneratorV0();
		for (int i : order) seqShuffled.set(i, json(g, rqs.get(i)));
		assertSame(ref, seqShuffled, "sequential shuffled");
		assertSame(ref, shuffledParallel(GeneratorV0::new, 4, 1, null), "4 threads");
		assertSame(ref, shuffledParallel(GeneratorV0::new, 8, 2, null), "8 threads");
	}

	@Test
	void cacheVariantsMatchDirect() throws Exception {
		List<String> ref = reference();
		assertSame(ref, shuffledParallel(() -> new GeneratorV0(new DirectIrSource()), 4, 3, null), "direct");

		CachedIrSource cold = new CachedIrSource(10_000, null);
		assertSame(ref, shuffledParallel(() -> new GeneratorV0(cold), 1, 4, null), "cold cache");
		assertEquals(true, cold.size() > 0);
		assertSame(ref, shuffledParallel(() -> new GeneratorV0(cold), 4, 5, null), "warm cache");

		CachedIrSource tiny = new CachedIrSource(2, null);
		assertSame(ref, shuffledParallel(() -> new GeneratorV0(tiny), 8, 6, null), "capacity 2");
		assertTrue(tiny.size() <= 2);

		ExecutorService pre = Executors.newFixedThreadPool(4);
		try {
			CachedIrSource c = new CachedIrSource(10_000, pre);
			List<GenRequest> rqs = requests();
			List<Integer> order = new ArrayList<>();
			for (int i = 0; i < N; i++) order.add(i);
			Collections.shuffle(order, new Random(8));
			List<String> got = shuffledParallel(() -> new GeneratorV0(c), 4, 9, () -> {
				for (int i : order) c.prefetch(dev.fractalbuildings.core.gen.v0.SpecPolicyV0.spec(rqs.get(i)));
			});
			assertSame(ref, got, "prefetched");
			c.clear();
			assertSame(ref, shuffledParallel(() -> new GeneratorV0(c), 4, 10, null), "evict then regenerate");
		} finally {
			pre.shutdown();
		}
	}

	@Test
	void hitEqualsMissAndBuildsOncePerSpec() throws Exception {
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		CachedIrSource c = new CachedIrSource(100, null, spec -> { calls.incrementAndGet(); return ShellGeneratorV0.generate(spec); });
		BuildingSpec spec = dev.fractalbuildings.core.gen.v0.SpecPolicyV0.spec(Cases.of(3));
		BuildingIr miss = c.get(spec);
		ExecutorService pool = Executors.newFixedThreadPool(8);
		List<Future<BuildingIr>> fs = new ArrayList<>();
		for (int i = 0; i < 64; i++) fs.add(pool.submit(() -> c.get(spec)));
		for (Future<BuildingIr> f : fs) assertEquals(miss, f.get());
		pool.shutdown();
		assertEquals(1, calls.get());
		assertEquals(ShellGeneratorV0.generate(spec), miss);
	}

	@Test
	void failedBuildIsNotCached() {
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		CachedIrSource c = new CachedIrSource(4, null, spec -> {
			if (calls.incrementAndGet() == 1) throw new IllegalStateException("boom");
			return ShellGeneratorV0.generate(spec);
		});
		BuildingSpec spec = dev.fractalbuildings.core.gen.v0.SpecPolicyV0.spec(Cases.of(3));
		assertThrows(IllegalStateException.class, () -> c.get(spec));
		assertEquals(ShellGeneratorV0.generate(spec), c.get(spec));
	}

	@Test
	void rejectingExecutorDoesNotLeaveAHangingFuture() {
		CachedIrSource c = new CachedIrSource(4, r -> { throw new java.util.concurrent.RejectedExecutionException("full"); });
		BuildingSpec spec = dev.fractalbuildings.core.gen.v0.SpecPolicyV0.spec(Cases.of(3));
		c.prefetch(spec);
		assertEquals(0, c.size());
		assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> assertEquals(ShellGeneratorV0.generate(spec), c.get(spec)));
	}

	/** Mutant: a shell source whose output depends on call count (a hidden shared draw) must be caught. */
	@Test
	void mutantNondeterministicSourceIsDetected() throws Exception {
		var counter = new java.util.concurrent.atomic.AtomicInteger();
		IrSource flaky = new IrSource() {
			public BuildingIr get(BuildingSpec s) {
				BuildingSpec t = new BuildingSpec(s.genVersion(), s.specSeed() + (counter.getAndIncrement() % 2), s.style(), s.type(), s.width(), s.depth(), s.storeys(), s.roof());
				return ShellGeneratorV0.generate(t);
			}
		};
		List<String> ref = reference();
		List<String> got = shuffledParallel(() -> new GeneratorV0(flaky), 4, 11, null);
		assertNotEquals(ref, got);
		// and a cache in front hides nothing: the cached flaky source still differs from the reference
		List<String> gotCached = shuffledParallel(() -> new GeneratorV0(new CachedIrSource(2, null, flaky)), 4, 12, null);
		assertNotEquals(ref, gotCached);
	}
}
