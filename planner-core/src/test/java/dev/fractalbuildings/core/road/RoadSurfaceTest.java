package dev.fractalbuildings.core.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.road.RoadNet.Col;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The terrace rule and the per-chunk surface plan (ADR 0004 TC, WR3) on synthetic ground. */
class RoadSurfaceTest {
	@Test
	void aCutRisesInStepsOfTwoWithBenchesTwoWide() {
		int d = 70;
		int[] tops = new int[RoadSurface.CUT_REACH + 3];
		for (int u = 0; u < tops.length; u++) tops[u] = RoadSurface.top(d, u, d + 200); // a wall of ground: only the rule decides
		assertEquals(d, tops[0], "the 1-block shoulder is level with the road");
		for (int u = 1; u < RoadSurface.CUT_REACH; u++) assertTrue(tops[u] - tops[u - 1] <= RoadSurface.STEP && tops[u] >= tops[u - 1], "step at " + u);
		// benches: after the shoulder, every height is held for at least 2 columns
		int run = 1;
		for (int u = 2; u < RoadSurface.CUT_REACH; u++) {
			if (tops[u] == tops[u - 1]) run++;
			else {
				if (u > 2) assertTrue(run >= RoadSurface.BENCH, "bench of " + run + " before " + u);
				run = 1;
			}
		}
		assertEquals(d + 2, tops[1]);
		assertEquals(d + 2, tops[2]);
		assertEquals(d + 4, tops[3]);
		assertEquals(d + 200, tops[RoadSurface.CUT_REACH], "past the reach the ground stays");
	}

	@Test
	void anEmbankmentFallsOneToOneAndStopsAtItsReach() {
		int d = 70;
		assertEquals(d, RoadSurface.top(d, 0, 40));
		assertEquals(d - 3, RoadSurface.top(d, 3, 40));
		assertEquals(40, RoadSurface.top(d, RoadSurface.FILL_REACH, 40));
		assertEquals(68, RoadSurface.top(d, 3, 68), "never below the ground: no digging");
	}

	@Test
	void theRuleNeverMovesTheTopPastTheGroundOrTheDeck() {
		Random r = new Random(5);
		for (int i = 0; i < 20_000; i++) {
			int d = r.nextInt(100), g = r.nextInt(100), u = r.nextInt(16);
			int t = RoadSurface.top(d, u, g);
			assertTrue(t >= Math.min(d, g) && t <= Math.max(d, g), d + " " + g + " " + u + " -> " + t);
			if (g > d) assertTrue(t <= g && t >= d);
			if (g < d) assertTrue(t >= g && t <= d);
		}
	}

	// ---- plan ----

	private static List<List<Col>> around(List<Col> all, int cx, int cz) {
		List<List<Col>> out = new ArrayList<>();
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				List<Col> l = new ArrayList<>();
				for (Col c : all) if (Math.floorDiv(c.x(), 16) == cx + dx && Math.floorDiv(c.z(), 16) == cz + dz) l.add(c);
				out.add(l);
			}
		}
		return out;
	}

	/** A 3-wide road along x at z = 14..16 (spanning two chunk rows), deck surface height 71 (top block 70). */
	private static List<Col> road() {
		List<Col> l = new ArrayList<>();
		for (int x = -40; x < 60; x++) for (int z = 14; z <= 16; z++) l.add(new Col(x, z, 71, false, 3));
		return l;
	}

	private static RoadSurface.Ground hill(int base, int slope) {
		return new RoadSurface.Ground() {
			@Override public int topY(int x, int z) { return base + slope * (z - 15) / 2; }
			@Override public boolean wet(int x, int z) { return false; }
		};
	}

	/** Brute force: the nearest road column by (squared distance, dz, dx), blocks from the edge = rounded distance - 1. */
	private static int[] nearest(List<Col> road, int x, int z) {
		long best = Long.MAX_VALUE;
		int bdz = 0, bdx = 0, deck = 0;
		for (Col c : road) {
			int dx = c.x() - x, dz = c.z() - z;
			if (Math.abs(dx) > 12 || Math.abs(dz) > 12) continue;
			long d2 = (long) dx * dx + dz * dz;
			if (d2 < best || (d2 == best && (dz < bdz || (dz == bdz && dx < bdx)))) { best = d2; bdz = dz; bdx = dx; deck = c.y() - 1; }
		}
		if (best == Long.MAX_VALUE) return null;
		return new int[] {(int) StrictMath.floor(StrictMath.sqrt(best) + 0.5) - 1, deck};
	}

	@Test
	void everyColumnFollowsTheRuleFromItsNearestRoadColumn() {
		List<Col> road = road();
		for (int slope : new int[] {-4, 1, 2, 6}) {
			RoadSurface.Ground g = hill(70, slope);
			for (int cx = 0; cx <= 1; cx++) {
				for (int cz = 0; cz <= 1; cz++) {
					RoadSurface.Plan p = RoadSurface.plan(cx, cz, around(road, cx, cz), null, g);
					for (int dz = 0; dz < 16; dz++) {
						for (int dx = 0; dx < 16; dx++) {
							int x = cx * 16 + dx, z = cz * 16 + dz, i = dz * 16 + dx;
							boolean isRoad = z >= 14 && z <= 16;
							if (isRoad) {
								assertEquals(RoadSurface.ROAD, p.kind[i]);
								assertEquals(70, p.surf[i]);
								continue;
							}
							int[] n = nearest(road, x, z);
							int gr = g.topY(x, z);
							int want = n == null ? gr : RoadSurface.top(n[1], n[0], gr);
							if (want == gr) assertEquals(RoadSurface.NONE, p.kind[i], "untouched " + x + "," + z);
							else {
								assertEquals(RoadSurface.TERRACE, p.kind[i], x + "," + z);
								assertEquals(want, p.surf[i], x + "," + z + " slope " + slope);
							}
						}
					}
				}
			}
		}
	}

	@Test
	void aRoadInTheNeighbourChunkShapesTheShoulderAcrossTheBorder() {
		List<Col> road = new ArrayList<>();
		for (int x = 0; x < 16; x++) road.add(new Col(x, 17, 71, false, 3)); // chunk (0,1) only, row 17
		RoadSurface.Plan p = RoadSurface.plan(0, 0, around(road, 0, 0), null, hill(80, 0));
		assertEquals(RoadSurface.TERRACE, p.kind[15 * 16 + 5], "z 15 touches the road row");
		assertEquals(72, p.surf[15 * 16 + 5], "u = 1: the first bench");
		assertEquals(72, p.surf[14 * 16 + 5]);
		assertEquals(74, p.surf[13 * 16 + 5]);
		assertEquals(RoadSurface.NONE, p.kind[0], "z 0 is 17 blocks away");
		assertEquals(0, p.roads);
	}

	@Test
	void waterIsNeverFilledAndDistrictColumnsAreLeftAlone() {
		List<Col> road = road();
		boolean[] district = new boolean[256];
		district[0 * 16 + 3] = true; // x 3, z 16: a road column a district draws
		district[2 * 16 + 4] = true; // x 4, z 18: a shoulder column a district draws
		RoadSurface.Ground g = new RoadSurface.Ground() {
			@Override public int topY(int x, int z) { return 60; }
			@Override public boolean wet(int x, int z) { return x >= 8 && x < 12; }
		};
		RoadSurface.Plan p = RoadSurface.plan(0, 1, around(road, 0, 1), district, g);
		assertEquals(RoadSurface.DECK, p.kind[0 * 16 + 9], "a wet road column is deck");
		assertEquals(70, p.surf[0 * 16 + 9]);
		assertEquals(RoadSurface.NONE, p.kind[3 * 16 + 9], "a wet column beside the road is left alone");
		assertEquals(RoadSurface.TERRACE, p.kind[3 * 16 + 5], "a dry one is embanked");
		assertEquals(RoadSurface.NONE, p.kind[3], "the district's road column");
		assertEquals(RoadSurface.NONE, p.kind[2 * 16 + 4], "the district's shoulder column");
		assertFalse(p.empty());
	}

	@Test
	void aChunkWithNoRoadAroundIsEmptyAndReadsNoGround() {
		List<List<Col>> none = around(List.of(), 0, 0);
		RoadSurface.Plan p = RoadSurface.plan(0, 0, none, null, new RoadSurface.Ground() {
			@Override public int topY(int x, int z) { throw new AssertionError("read"); }
			@Override public boolean wet(int x, int z) { throw new AssertionError("read"); }
		});
		assertTrue(p.empty());
	}
}
