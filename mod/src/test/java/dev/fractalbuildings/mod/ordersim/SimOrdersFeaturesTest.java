package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ORDERSIM S3: the orders are permutations that put every adjacent pair both ways round, and the feature calls are a pure function of the seed. */
class SimOrdersFeaturesTest {
	@BeforeAll
	static void boot() { SimBoot.boot(); }

	@Test
	void everyOrderIsAPermutationOfTheWindow() {
		List<SimOrders.Order> orders = SimOrders.fast(10, -4, 5, 4, 7L);
		assertEquals(List.of("forward", "reverse", "shuffle-1", "shuffle-2", "spiral", "checker"), orders.stream().map(SimOrders.Order::name).toList());
		for (SimOrders.Order o : orders) {
			Set<Long> seen = new HashSet<>();
			for (int[] c : o.chunks()) {
				assertTrue(c[0] >= 10 && c[0] < 15 && c[1] >= -4 && c[1] < 0, o.name() + " " + c[0] + "," + c[1]);
				assertTrue(seen.add(((long) c[0] << 32) | (c[1] & 0xFFFFFFFFL)), o.name() + " repeats a chunk");
			}
			assertEquals(20, seen.size(), o.name());
		}
	}

	@Test
	void forwardAndReversePutEveryAdjacentPairBothWays() {
		List<SimOrders.Order> orders = SimOrders.fast(0, 0, 4, 4, 1L);
		int[] pos = new int[16], pos2 = new int[16];
		for (int i = 0; i < 16; i++) { pos[orders.get(0).chunks().get(i)[1] * 4 + orders.get(0).chunks().get(i)[0]] = i; pos2[orders.get(1).chunks().get(i)[1] * 4 + orders.get(1).chunks().get(i)[0]] = i; }
		for (int z = 0; z < 4; z++) for (int x = 0; x < 3; x++) assertTrue((pos[z * 4 + x] < pos[z * 4 + x + 1]) != (pos2[z * 4 + x] < pos2[z * 4 + x + 1]));
		// the checker: every chunk has a neighbour before it and one after it, except the first and last parity corners
		SimOrders.Order checker = orders.get(5);
		assertEquals(0, Math.floorMod(checker.chunks().get(0)[0] + checker.chunks().get(0)[1], 2));
		assertEquals(1, Math.floorMod(checker.chunks().get(15)[0] + checker.chunks().get(15)[1], 2));
		// the spiral starts in the middle
		int[] first = orders.get(4).chunks().get(0);
		assertTrue(first[0] >= 1 && first[0] <= 2 && first[1] >= 1 && first[1] <= 2, first[0] + "," + first[1]);
	}

	@Test
	void ordersAreDeterministic() {
		assertEquals(SimOrders.fast(3, 3, 6, 6, 9L).get(2).chunks().stream().map(c -> c[0] + "," + c[1]).toList(), SimOrders.fast(3, 3, 6, 6, 9L).get(2).chunks().stream().map(c -> c[0] + "," + c[1]).toList());
		assertNotEquals(SimOrders.shuffle(0, 0, 6, 6, 9L, 1).chunks().stream().map(c -> c[0] + "," + c[1]).toList(), SimOrders.shuffle(0, 0, 6, 6, 9L, 2).chunks().stream().map(c -> c[0] + "," + c[1]).toList());
	}

	@Test
	void hashedCallsArePureAndInTheirChunk() {
		var terrain = (dev.fractalbuildings.core.probe.TerrainProbe) (x, z) -> 64;
		List<SimFeatures.Call> a = SimFeatures.hashed(5L, -3, 8, 4, 4, 3, terrain, false), b = SimFeatures.hashed(5L, -3, 8, 4, 4, 3, terrain, false);
		assertEquals(a, b);
		assertEquals(48, a.size());
		for (SimFeatures.Call c : a) {
			assertTrue(c.chunkX() >= -3 && c.chunkX() < 1 && c.chunkZ() >= 8 && c.chunkZ() < 12);
			assertEquals(SimFeatures.stepOf(c.key()), c.step());
			assertNotEquals("freeze_top_layer", c.key(), "snow only in snowy scenarios");
		}
		assertTrue(SimFeatures.hashed(5L, -3, 8, 4, 4, 3, terrain, true).stream().anyMatch(c -> c.key().equals("freeze_top_layer")));
		assertEquals(Decoration.LAKES, SimFeatures.stepOf("lake_lava"));
	}

	@Test
	void aScriptedDarkOakFindsASeedThatPlaces() {
		SimFeatures.Call c = SimFeatures.scripted("dark_oak", 3, 64, 3, 1L, (x, z) -> 64, (x, z) -> "dark_forest");
		assertNotNull(c);
		assertEquals("dark_oak", c.key());
		assertEquals(c, SimFeatures.scripted("dark_oak", 3, 64, 3, 1L, (x, z) -> 64, (x, z) -> "dark_forest"), "the first seed that places is stable");
	}
}
