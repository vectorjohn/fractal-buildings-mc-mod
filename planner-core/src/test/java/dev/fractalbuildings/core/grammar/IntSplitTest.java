package dev.fractalbuildings.core.grammar;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.rand.Draws;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class IntSplitTest {
	@Test
	void splitSumsExactlyAndHoldsMinimums() {
		Draws r = new Draws(1);
		for (int c = 0; c < 10_000; c++) {
			int n = r.range(1, 6);
			int[] mins = new int[n], weights = new int[n];
			int minSum = 0;
			for (int i = 0; i < n; i++) {
				mins[i] = r.range(0, 5);
				weights[i] = r.range(1, 9);
				minSum += mins[i];
			}
			int len = minSum + r.range(0, 60);
			int[] out = IntSplit.split(len, mins, weights);
			assertEquals(len, Arrays.stream(out).sum());
			for (int i = 0; i < n; i++) assertTrue(out[i] >= mins[i]);
			assertArrayEquals(out, IntSplit.split(len, mins, weights));
		}
	}

	@Test
	void splitFollowsWeightsAndTiesGoToLowerIndex() {
		assertArrayEquals(new int[] {4, 3, 3}, IntSplit.split(10, 0, 0, 0));
		assertArrayEquals(new int[] {2, 6}, IntSplit.split(8, new int[] {0, 0}, new int[] {1, 3}));
		// 7 split 1:1 gives the extra cell to part 0
		assertArrayEquals(new int[] {4, 3}, IntSplit.split(7, 0, 0));
	}

	@Test
	void splitThrowsWhenMinimumsDoNotFit() {
		assertThrows(IllegalArgumentException.class, () -> IntSplit.split(5, 3, 3));
	}

	@Test
	void repeatMakesBaysDifferingByAtMostOne() {
		for (int len = 2; len <= 400; len++) {
			int[] bays = IntSplit.repeat(len, 2, 3);
			assertEquals(len, Arrays.stream(bays).sum());
			int lo = Arrays.stream(bays).min().getAsInt(), hi = Arrays.stream(bays).max().getAsInt();
			assertTrue(hi - lo <= 1 && lo >= 2 && hi <= 3, len + " -> " + Arrays.toString(bays));
			for (int i = 1; i < bays.length; i++) assertTrue(bays[i - 1] >= bays[i], "larger first");
		}
		assertThrows(IllegalArgumentException.class, () -> IntSplit.repeat(1, 2, 3));
		assertThrows(IllegalArgumentException.class, () -> IntSplit.repeat(7, 2, 2));
	}
}
