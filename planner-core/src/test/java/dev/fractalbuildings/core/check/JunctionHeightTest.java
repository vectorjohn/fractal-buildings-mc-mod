package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.settlement.DistrictInfo;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.RoadKind;
import dev.fractalbuildings.core.settlement.Street;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * JUNCTION: the {@code junction-height} rule on hand-made plans. Tile {@code ix} of these plans covers x {@code 128 ix .. 128 ix + 127},
 * z {@code 0 .. 127}; each cell is judged in the plan of the tile that builds it.
 */
class JunctionHeightTest {
	private static DistrictPlan plan(int ix, Street... streets) {
		return new DistrictPlan(2, 1, new DistrictInfo("d" + ix + "_0", ix, 0, 128 * ix, 0, 128, 128, 0, 20), List.of(streets), List.of(), List.of(), null, List.of());
	}

	private static Street line(String id, RoadKind k, int w, int x0, int z0, int x1, int z1, int y0, int y1) {
		return new Street(id, k, w, List.of(new Pt(x0, z0), new Pt(x1, z1)), new int[] {y0, y1});
	}

	@Test
	void aStreetEndingOnAnotherAtAnotherLevelFails() {
		Street main = line("a", RoadKind.MAIN, 5, 0, 50, 100, 50, 60, 60);
		Street lane = line("l", RoadKind.LANE, 3, 50, 10, 50, 50, 60, 52); // its end lies on the main road, 8 below it
		List<Violation> v = SettlementChecks.cityJunctionHeights(List.of(plan(0, main, lane)));
		assertEquals(1, v.size(), v.toString());
		assertEquals(SettlementChecks.JUNCTION_HEIGHT, v.get(0).invariant());
		assertTrue(v.get(0).message().contains("up to 8"), v.get(0).message());
	}

	@Test
	void crossingWithinOneBlockPasses() {
		Street a = line("a", RoadKind.MAIN, 5, 0, 50, 100, 50, 60, 60);
		Street b = line("b", RoadKind.STREET, 5, 50, 0, 50, 100, 59, 61); // 60 at the crossing
		assertTrue(SettlementChecks.cityJunctionHeights(List.of(plan(0, a, b))).isEmpty());
		Street c = line("c", RoadKind.STREET, 5, 50, 0, 50, 100, 10, 110); // 10 per 10 blocks: its footprint spans 58 to 62 on the main road
		assertEquals(1, SettlementChecks.cityJunctionHeights(List.of(plan(0, a, c))).size());
	}

	@Test
	void eachTileJudgesItsOwnCells() {
		Street a0 = line("a", RoadKind.MAIN, 5, 0, 50, 140, 50, 60, 60);
		Street a1 = line("a", RoadKind.MAIN, 5, 120, 50, 200, 50, 60, 60);
		Street lane = line("l", RoadKind.LANE, 3, 150, 10, 150, 50, 60, 50);
		List<Violation> v = SettlementChecks.cityJunctionHeights(List.of(plan(1, a1, lane), plan(0, a0)));
		assertEquals(1, v.size(), v.toString());
		// the same lane listed by tile 0 lies in tile 1's ground, which tile 0 does not build: not judged there
		assertTrue(SettlementChecks.cityJunctionHeights(List.of(plan(0, a0, lane))).isEmpty());
	}

	@Test
	void resultIsIndependentOfPlanOrder() {
		Street a = line("a", RoadKind.MAIN, 5, 0, 50, 255, 50, 60, 60);
		Street b = line("b", RoadKind.STREET, 5, 50, 0, 50, 100, 50, 50);
		Street c = line("c", RoadKind.STREET, 5, 180, 0, 180, 100, 64, 64);
		List<Violation> one = SettlementChecks.cityJunctionHeights(List.of(plan(0, a, b), plan(1, a, c)));
		assertEquals(2, one.size(), one.toString());
		assertEquals(one, SettlementChecks.cityJunctionHeights(List.of(plan(1, a, c), plan(0, a, b))));
	}

	@Test
	void streetsThatDoNotTouchAreNotCompared() {
		Street a = line("a", RoadKind.MAIN, 5, 0, 50, 100, 50, 60, 60);
		Street b = line("b", RoadKind.STREET, 5, 0, 60, 100, 60, 20, 20);
		assertTrue(SettlementChecks.junctions(List.of(plan(0, a, b))).isEmpty());
	}

	/** A street that ends against another's side (footprints touching across one cell edge, not overlapping) is compared across that edge. */
	@Test
	void aStreetEndingAgainstAnotherIsCompared() {
		Street main = line("a", RoadKind.MAIN, 5, 0, 50, 100, 50, 60, 60); // cells z 48..52
		Street low = line("l", RoadKind.LANE, 3, 50, 10, 50, 46, 52, 52); // end face z 45..47, against the main road's z 48
		List<Violation> v = SettlementChecks.cityJunctionHeights(List.of(plan(0, main, low)));
		assertEquals(1, v.size(), v.toString());
		assertTrue(v.get(0).message().contains("up to 8"), v.get(0).message());
		Street near = line("l", RoadKind.LANE, 3, 50, 10, 50, 46, 59, 59);
		assertTrue(SettlementChecks.cityJunctionHeights(List.of(plan(0, main, near))).isEmpty());
		Street gap = line("l", RoadKind.LANE, 3, 50, 10, 50, 45, 52, 52); // a cell short: they do not touch
		assertTrue(SettlementChecks.cityJunctionHeights(List.of(plan(0, main, gap))).isEmpty());
	}

	/** Pieces of one whole street ({@code r}, {@code r.1}: a road a square cuts) build a cell they share once, from the first piece. */
	@Test
	void piecesOfOneStreetAreNotCompared() {
		Street p0 = line("r", RoadKind.MAIN, 9, 0, 50, 40, 50, 60, 62);
		Street p1 = line("r.1", RoadKind.MAIN, 9, 44, 50, 90, 50, 64, 66); // its first column's footprint overlaps the first piece's end
		assertTrue(SettlementChecks.cityJunctionHeights(List.of(plan(0, p0, p1))).isEmpty());
		Street other = line("q", RoadKind.MAIN, 9, 44, 50, 90, 50, 64, 66);
		assertFalse(SettlementChecks.cityJunctionHeights(List.of(plan(0, p0, other))).isEmpty());
		assertEquals("r", SettlementChecks.wholeStreet("r.1"));
		assertEquals("r", SettlementChecks.wholeStreet("r"));
		assertEquals("sd0", SettlementChecks.wholeStreet("sd0"));
	}
}
