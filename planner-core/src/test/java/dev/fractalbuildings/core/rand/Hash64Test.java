package dev.fractalbuildings.core.rand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Hash64Test {
	@Test
	void stableAcrossRunsAndJvms() {
		// Golden values: changing the mixer changes every building. Update only with a genVersion bump.
		assertEquals(GOLDEN_A, Hash64.hash(12345L, "brief", 3, -7));
		assertEquals(GOLDEN_B, Hash64.of("rooms"));
	}

	@Test
	void keysAndPurposesSeparate() {
		long a = Hash64.hash(1L, "brief", 3, -7);
		assertNotEquals(a, Hash64.hash(1L, "brief", -7, 3));
		assertNotEquals(a, Hash64.hash(1L, "rooms", 3, -7));
		assertNotEquals(a, Hash64.hash(1L, "brief", 3, -7, 0));
		// Seeds differing only above bit 32 must differ (S4: casting the seed to int was a bug).
		assertNotEquals(Hash64.hash(1L, "brief"), Hash64.hash(1L | (1L << 40), "brief"));
	}

	@Test
	void belowIsInRangeAndRoughlyUniform() {
		int[] counts = new int[7];
		Draws d = new Draws(99);
		for (int i = 0; i < 70_000; i++) counts[d.below(7)]++;
		for (int c : counts) assertTrue(c > 9_500 && c < 10_500, "count " + c);
		for (int i = 0; i < 1000; i++) {
			int v = Hash64.range(Hash64.hash(5, "r", i), -3, 3);
			assertTrue(v >= -3 && v <= 3);
		}
	}

	static final long GOLDEN_A = 4691405561155795685L;
	static final long GOLDEN_B = -887189279436067470L;
}
