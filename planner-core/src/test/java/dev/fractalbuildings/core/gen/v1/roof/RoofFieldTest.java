package dev.fractalbuildings.core.gen.v1.roof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.roof.RoofField.Pitch;
import org.junit.jupiter.api.Test;

/** The pure heightfields over rectangles 3..24 x 3..24. */
class RoofFieldTest {
	static final Pitch[] PITCHES = {new Pitch(1, 1), new Pitch(2, 1), new Pitch(1, 2), new Pitch(1, 3)};

	@Test
	void gableMirrorsAcrossTheRidgeAndIsConstantAlongIt() {
		for (Pitch p : PITCHES)
			for (int sx = 3; sx <= 24; sx++)
				for (int sz = 3; sz <= 24; sz++)
					for (boolean ridgeX : new boolean[] {true, false}) {
						int[][] h = RoofField.gable(sx, sz, ridgeX, p);
						for (int x = 0; x < sx; x++)
							for (int z = 0; z < sz; z++) {
								int mx = ridgeX ? x : sx - 1 - x, mz = ridgeX ? sz - 1 - z : z;
								assertEquals(h[x][z], h[mx][mz], "mirror " + sx + "x" + sz + " " + p);
								int ax = ridgeX ? (x + 1) % sx : x, az = ridgeX ? z : (z + 1) % sz;
								assertEquals(h[x][z], h[ax][az], "constant along the ridge " + sx + "x" + sz);
								int across = ridgeX ? z : x;
								if (across == 0 || across == (ridgeX ? sz : sx) - 1) assertEquals(0, h[x][z], "eave");
							}
						assertSmooth(h, p);
					}
	}

	@Test
	void hipIsTheChessboardProfile() {
		for (Pitch p : PITCHES)
			for (int sx = 3; sx <= 24; sx++)
				for (int sz = 3; sz <= 24; sz++) {
					int[][] h = RoofField.hip(sx, sz, p);
					boolean[][] region = new boolean[sx][sz];
					boolean[][] open = new boolean[sx][sz];
					for (boolean[] r : region) java.util.Arrays.fill(r, true);
					int[][] viaDistance = RoofField.fromDistance(RoofField.distance(region, open), region, p::h);
					for (int x = 0; x < sx; x++)
						for (int z = 0; z < sz; z++) {
							int d = Math.min(Math.min(x, sx - 1 - x), Math.min(z, sz - 1 - z));
							assertEquals(p.h(d), h[x][z]);
							assertEquals(h[x][z], viaDistance[x][z], "distance transform agrees " + sx + "x" + sz);
							if (d == 0) assertEquals(0, h[x][z], "height 0 at the border");
						}
					assertSmooth(h, p);
				}
	}

	@Test
	void distanceStopsAtSolidNotAtOpenCells() {
		// 5x1 strip in a 7x3 array: the cells at the array border and the open ring give distance 1
		boolean[][] region = new boolean[7][3], open = new boolean[7][3];
		for (int x = 1; x <= 5; x++) region[x][1] = true;
		for (int x = 0; x < 7; x++) for (int z = 0; z < 3; z++) open[x][z] = !region[x][z];
		int[][] d = RoofField.distance(region, open);
		for (int x = 1; x <= 5; x++) assertEquals(1, d[x][1]);
		// a region cell with no open neighbours at all keeps growing
		open = new boolean[7][3];
		d = RoofField.distance(region, open);
		assertEquals(2, d[3][1], "array border is open: middle of a 7-wide array is 2 from the z border");
	}

	@Test
	void mansardIsSteepThenFlat() {
		assertEquals(0, RoofField.mansard(0));
		assertEquals(2, RoofField.mansard(1));
		assertEquals(4, RoofField.mansard(2));
		assertEquals(5, RoofField.mansard(3));
		assertEquals(5, RoofField.mansard(9));
	}

	static void assertSmooth(int[][] h, Pitch p) {
		int step = p.maxStep();
		for (int x = 0; x < h.length; x++)
			for (int z = 0; z < h[0].length; z++) {
				if (x + 1 < h.length) assertTrue(Math.abs(h[x][z] - h[x + 1][z]) <= step, "step in x");
				if (z + 1 < h[0].length) assertTrue(Math.abs(h[x][z] - h[x][z + 1]) <= step, "step in z");
			}
	}
}
