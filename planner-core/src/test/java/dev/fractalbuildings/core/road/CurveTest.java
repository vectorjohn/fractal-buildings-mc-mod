package dev.fractalbuildings.core.road;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CurveTest {
	static final List<int[]> NODES = List.of(new int[] {0, 0}, new int[] {64, 0}, new int[] {128, 64}, new int[] {128, 192}, new int[] {256, 256});

	@Test
	void stationsAreEightBlocksApartAndEndOnTheLastNode() {
		double[][] st = Curve.stations(NODES);
		assertArrayEquals(new double[] {0, 0}, st[0]);
		assertArrayEquals(new double[] {256, 256}, st[st.length - 1]);
		for (int k = 1; k < st.length - 1; k++) {
			double d = Math.hypot(st[k][0] - st[k - 1][0], st[k][1] - st[k - 1][1]);
			// straight-line distance between stations is at most the arc length of 8 and close to it on this gentle curve
			assertTrue(d <= 8.0 + 1e-9 && d > 6.5, "station " + k + " is " + d + " from the last");
		}
	}

	@Test
	void theCurveStaysNearItsNodes() {
		double[][] st = Curve.stations(NODES);
		for (int[] n : NODES) {
			double best = 1e9;
			for (double[] s : st) best = Math.min(best, Math.hypot(s[0] - n[0], s[1] - n[1]));
			assertTrue(best <= 5, "node " + n[0] + "," + n[1] + " is " + best + " from the curve");
		}
	}

	@Test
	void sameNodesSameStationsBitForBit() {
		assertTrue(java.util.Arrays.deepEquals(Curve.stations(NODES), Curve.stations(NODES)));
		assertEquals(2, Curve.stations(List.of(new int[] {0, 0}, new int[] {8, 0})).length);
	}
}
