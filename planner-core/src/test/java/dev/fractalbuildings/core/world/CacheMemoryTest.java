package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Measures the heap one cached tile (plan + buildings + ground) holds, to size the caps and state the ceiling (P6 CACHE). */
class CacheMemoryTest {
	private static long used() {
		for (int i = 0; i < 4; i++) {
			System.gc();
			try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		}
		Runtime r = Runtime.getRuntime();
		return r.totalMemory() - r.freeMemory();
	}

	@Test
	void bytesPerCachedTile() {
		WorldPlanner w = WorldPlannerOrderTest.GATE.planner(new WorldPlanner.Caches(4096, 64, 64, 8192, 64, 64, 64), null);
		List<int[]> tiles = WorldPlannerOrderTest.TILE_LIST;
		int[] first = tiles.get(0);
		w.buildings(first[0], first[1]); // warm the settlement-wide memos (skeletons, roads) so they are not charged to a tile
		w.ground(first[0], first[1]);
		w.evictAll();
		for (int[] t : tiles.subList(1, 3)) { w.buildings(t[0], t[1]); w.ground(t[0], t[1]); }
		w.evictAll();
		long before = used();
		int n = 0, pieces = 0;
		for (int[] t : tiles) {
			pieces += w.buildings(t[0], t[1]).size();
			w.ground(t[0], t[1]);
			n++;
		}
		long after = used();
		long perTile = (after - before) / n;
		System.out.println("cached tile memory: " + n + " tiles, " + pieces + " buildings, " + (after - before) / 1024 + " KiB, " + perTile / 1024 + " KiB per tile");
		assertTrue(perTile < 32L << 20, "a cached tile should be far under 32 MiB, was " + perTile);
		assertNotNull(w);
	}

	@Test
	void capsScaleWithTheHeapWithinTheDocumentedCeiling() {
		WorldPlanner.Caches small = WorldPlanner.Caches.forHeap(512L << 20), big = WorldPlanner.Caches.forHeap(8L << 30);
		assertEquals(WorldPlanner.Caches.DEFAULT.buildings(), small.buildings());
		assertEquals(2 * WorldPlanner.Caches.DEFAULT.buildings(), big.buildings());
		// worst case held by retention: every memo at its hard ceiling (a tile's 0.9 MiB is spread over plan, buildings and ground)
		long worstTiles = (long) big.hardFactor() * (big.plans() + big.buildings() + big.ground()) / 3;
		assertTrue(worstTiles * WorldPlanner.Caches.BYTES_PER_TILE < (8L << 30) / 4, "ceiling " + worstTiles + " tiles");
	}
}
