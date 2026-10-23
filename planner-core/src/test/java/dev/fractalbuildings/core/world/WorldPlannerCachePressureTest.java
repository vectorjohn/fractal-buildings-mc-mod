package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * The multi-player over-capacity case (P6 CACHE): more tiles are being generated at once than the caches hold, and their chunks
 * interleave. Each chunk asks for the tile's buildings and ground again. Without retention every ask evicts someone else's entry and
 * the generator reruns for every lot of the tile; with it each tile is computed once. Answers are the same either way.
 */
class WorldPlannerCachePressureTest {
	private static final int PLAYERS = 12, ROUNDS = 20, CAP = 4;

	private static int[] run(WorldPlanner.Caches caps, List<String> digests) {
		WorldPlanner w = WorldPlannerOrderTest.GATE.planner(caps, null);
		AtomicInteger inline = new AtomicInteger();
		w.observeBuildings(l -> { if (l == WorldPlanner.Lookup.INLINE) inline.incrementAndGet(); });
		List<int[]> tiles = WorldPlannerOrderTest.TILE_LIST.subList(0, PLAYERS);
		for (int round = 0; round < ROUNDS; round++) { // one chunk of every player's tile per round: the streams interleave
			for (int[] t : tiles) {
				w.buildings(t[0], t[1]);
				w.ground(t[0], t[1]);
			}
		}
		for (int[] t : tiles) digests.add(WorldPlannerOrderTest.digest(w, t[0], t[1]));
		return new int[] {inline.get()};
	}

	@Test
	void interleavedTileStreamsPastTheCapComputeEachTileOnce() {
		WorldPlanner.Caches base = new WorldPlanner.Caches(4096, 64, 64, 8192, 16, CAP, CAP);
		List<String> strictDigests = new ArrayList<>(), keptDigests = new ArrayList<>();
		int strict = run(base.strict(), strictDigests)[0];
		int kept = run(base, keptDigests)[0];
		System.out.println("buildings recomputes for " + PLAYERS + " tiles x " + ROUNDS + " chunks, cap " + CAP + ": strict LRU " + strict + ", retention " + kept);
		assertTrue(strict >= PLAYERS * ROUNDS, "strict LRU thrashes: " + strict);
		assertEquals(PLAYERS, kept, "one computation per tile with retention");
		assertEquals(strictDigests, keptDigests, "answers do not depend on the cache");
		for (int i = 0; i < PLAYERS; i++) assertEquals(WorldPlannerOrderTest.BASELINE.get(WorldPlannerOrderTest.id(WorldPlannerOrderTest.TILE_LIST.get(i))), keptDigests.get(i));
	}

	@Test
	void pastTheHardCeilingTheEldestGoesAnyway() {
		AtomicLong now = new AtomicLong();
		FlightCache<Integer, Integer> c = new FlightCache<>(2, 1_000, 3, now::get);
		for (int k = 0; k < 100; k++) {
			c.get(k, x -> x);
			assertTrue(c.size() <= 6, "size " + c.size());
		}
		assertEquals(6, c.size());
	}

	@Test
	void protectionExpiresByItselfSoNothingIsPinnedForEver() {
		AtomicLong now = new AtomicLong();
		FlightCache<Integer, Integer> c = new FlightCache<>(2, 1_000, 8, now::get);
		for (int k = 0; k < 6; k++) c.get(k, x -> x);
		assertEquals(6, c.size(), "all recent: kept past the cap");
		now.addAndGet(5_000);
		c.get(100, x -> x); // the next insert trims everything stale
		assertEquals(2, c.size(), "stale entries are released");
	}

	@Test
	void anInFlightEntrySurvivesOtherInsertsAndIsNotComputedTwice() throws Exception {
		AtomicLong now = new AtomicLong();
		FlightCache<Integer, Integer> c = new FlightCache<>(1, 1_000_000, 8, now::get);
		CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
		AtomicInteger runs = new AtomicInteger();
		Thread slow = new Thread(() -> c.get(0, k -> {
			runs.incrementAndGet();
			started.countDown();
			try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); }
			return 42;
		}));
		slow.start();
		assertTrue(started.await(10, TimeUnit.SECONDS));
		for (int k = 1; k <= 5; k++) c.get(k, x -> x); // others flood past the cap of 1
		AtomicInteger second = new AtomicInteger();
		Thread joiner = new Thread(() -> second.set(c.get(0, k -> { runs.incrementAndGet(); return -1; })));
		joiner.start();
		release.countDown();
		slow.join(10_000);
		joiner.join(10_000);
		assertEquals(1, runs.get(), "the joiner waited for the in-flight result instead of recomputing");
		assertEquals(42, second.get());
	}

	@Test
	void aStuckWorkersEntryIsEvictedAfterTheGrace() throws Exception {
		AtomicLong now = new AtomicLong();
		FlightCache<Integer, Integer> c = new FlightCache<>(1, 1_000, 8, now::get);
		CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
		Thread stuck = new Thread(() -> c.get(0, k -> {
			started.countDown();
			try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); }
			return 1;
		}));
		stuck.start();
		assertTrue(started.await(10, TimeUnit.SECONDS));
		now.addAndGet(10_000);
		c.get(1, x -> x);
		assertEquals(1, c.size(), "the stuck entry no longer holds a slot");
		release.countDown();
		stuck.join(10_000);
	}
}
