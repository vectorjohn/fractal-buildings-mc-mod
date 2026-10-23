package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * JUNCTION: quay runs are graded in one canonical order and direction (a later run meets an earlier one at its level), whatever order
 * and direction a tile's contour window hands them in, so the tiles that see the same quays grade them alike.
 */
class QuayOrderTest {
	static List<int[]> run(int x0, int z0, int x1, int z1) { return StreetCols.columns(List.of(new Pt(x0, z0), new Pt(x1, z1))); }

	static String text(List<List<int[]>> runs) {
		StringBuilder sb = new StringBuilder();
		for (List<int[]> r : runs) sb.append(r.get(0)[0]).append(',').append(r.get(0)[1]).append("->").append(r.get(r.size() - 1)[0]).append(',').append(r.get(r.size() - 1)[1]).append(' ');
		return sb.toString().trim();
	}

	@Test
	void orderAndDirectionDoNotDependOnTheWindow() {
		List<List<int[]>> a = List.of(run(100, 50, 20, 50), run(0, 80, 60, 80), run(30, 10, 30, 70));
		String expected = "30,10->30,70 20,50->100,50 0,80->60,80"; // each from its smaller end, by smallest column (z first, then x)
		assertEquals(expected, text(Districts.canonicalQuays(a)));
		List<List<int[]>> b = new ArrayList<>();
		for (List<int[]> r : a) { List<int[]> c = new ArrayList<>(r); Collections.reverse(c); b.add(0, c); }
		assertEquals(expected, text(Districts.canonicalQuays(b)));
	}
}
