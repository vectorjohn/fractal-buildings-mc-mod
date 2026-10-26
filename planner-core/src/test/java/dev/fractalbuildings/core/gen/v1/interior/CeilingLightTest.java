package dev.fractalbuildings.core.gen.v1.interior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.interior.FloorPlan.Leaf;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The ceiling_light site rule (DS-24): a grid of pitch at most 10, half a pitch from the walls; a line of pitch 8 down a corridor. */
class CeilingLightTest {
	@Test
	void roomOf26x10GetsAGridWithinTenOfEachOtherAndFiveOfTheWalls() {
		Leaf lf = new Leaf(3, 4, 26, 10);
		List<int[]> g = InteriorPass.ceilingGrid(lf);
		assertEquals(3, g.size());
		for (int[] p : g) assertTrue(p[0] >= lf.x() && p[0] < lf.x() + lf.w() && p[1] >= lf.z() && p[1] < lf.z() + lf.d());
		for (int i = 1; i < g.size(); i++) assertTrue(g.get(i)[0] - g.get(i - 1)[0] <= 10, "pitch");
		assertCover(lf, g, 6);
		assertTrue(g.get(0)[0] - lf.x() <= 5 && lf.x() + lf.w() - 1 - g.get(g.size() - 1)[0] <= 5, "half a pitch from the walls");
	}

	@Test
	void everyCellOfEveryShapeIsWithinSixOfALight() {
		for (int w = 2; w <= 40; w++)
			for (int d = 2; d <= 40; d++) {
				Leaf lf = new Leaf(0, 0, w, d);
				assertCover(lf, InteriorPass.ceilingGrid(lf), 6);
			}
	}

	@Test
	void corridorGetsALineEightApartAndAtLeastOne() {
		for (int len = 2; len <= 44; len++)
			for (int wd = 2; wd <= 3; wd++) {
				Leaf lf = new Leaf(0, 0, len, wd);
				List<int[]> g = InteriorPass.ceilingGrid(lf);
				assertTrue(g.size() >= 1);
				for (int i = 1; i < g.size(); i++) assertTrue(g.get(i)[0] - g.get(i - 1)[0] <= 8, "len " + len);
				for (int[] p : g) assertTrue(p[1] >= 0 && p[1] < wd);
			}
	}

	static void assertCover(Leaf lf, List<int[]> lights, int reach) {
		for (int x = lf.x(); x < lf.x() + lf.w(); x++)
			for (int z = lf.z(); z < lf.z() + lf.d(); z++) {
				boolean lit = false;
				for (int[] l : lights) lit |= Math.max(Math.abs(l[0] - x), Math.abs(l[1] - z)) <= reach;
				assertTrue(lit, lf.w() + "x" + lf.d() + " cell " + x + "," + z + " dark");
			}
	}
}
