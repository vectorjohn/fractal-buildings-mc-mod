package dev.fractalbuildings.core.road;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.road.RoadNet.Col;
import dev.fractalbuildings.core.road.VillageJoin.Box;
import dev.fractalbuildings.core.road.VillageJoin.Result;
import dev.fractalbuildings.core.road.VillageJoin.Village;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class VillageJoinTest {
	/** A 3-wide road along z = 100 from x = 0 to 300, deck y 70 rising to 80. */
	static List<Col> road(int z, int x0, int x1) {
		List<Col> l = new ArrayList<>();
		for (int x = x0; x <= x1; x++) for (int dz = -1; dz <= 1; dz++) l.add(new Col(x, z + dz, 70 + x / 30, false, 3));
		return l;
	}

	/** Houses and a street piece in a block 140..190 x 85..125: the road runs through it. */
	static Village village() {
		List<Box> pieces = new ArrayList<>();
		List<Box> streets = new ArrayList<>();
		Box s1 = new Box(140, 104, 190, 106);
		streets.add(s1);
		pieces.add(s1);
		pieces.add(new Box(145, 90, 152, 97));
		pieces.add(new Box(160, 108, 168, 116));
		pieces.add(new Box(176, 92, 183, 99));
		pieces.add(new Box(150, 110, 156, 120));
		return new Village(pieces, streets);
	}

	static Map<Long, Col> apply(List<Col> cols, Result r) {
		Map<Long, Col> m = new HashMap<>();
		for (Col c : cols) m.put(VillageJoin.key(c.x(), c.z()), c);
		for (long k : r.removed()) m.remove(k);
		for (Col c : r.added()) m.put(VillageJoin.key(c.x(), c.z()), c);
		return m;
	}

	static boolean connected(Map<Long, Col> m, int fromX, int toX) {
		Col start = null;
		for (Col c : m.values()) if (c.x() == fromX && (start == null || c.z() < start.z())) start = c;
		assertNotNull(start);
		Map<Long, Boolean> seen = new HashMap<>();
		ArrayDeque<Col> q = new ArrayDeque<>();
		q.add(start);
		seen.put(VillageJoin.key(start.x(), start.z()), true);
		while (!q.isEmpty()) {
			Col c = q.poll();
			if (c.x() == toX) return true;
			for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
				long k = VillageJoin.key(c.x() + dx, c.z() + dz);
				Col n = m.get(k);
				if (n != null && seen.put(k, true) == null) q.add(n);
			}
		}
		return false;
	}

	@Test
	void roadBendsAroundThePiecesAndStaysConnected() {
		Village v = village();
		List<Col> cols = road(100, 0, 300);
		Result r = VillageJoin.join(v, cols);
		assertFalse(r.removed().isEmpty(), "the road ran through the village");
		Map<Long, Col> m = apply(cols, r);
		for (Col c : m.values()) for (Box b : v.pieces()) assertFalse(b.grow(2).contains(c.x(), c.z()), "road column " + c + " too near piece " + b);
		assertTrue(connected(m, 0, 300), "the bent road is one piece from end to end");
		// heights: neighbouring columns never differ by more than 1
		for (Col c : m.values()) for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
			Col n = m.get(VillageJoin.key(c.x() + d[0], c.z() + d[1]));
			if (n != null) assertTrue(Math.abs(n.y() - c.y()) <= 1, "step at " + c + " " + n);
		}
	}

	@Test
	void spurEndsOnTheStreetCentreLineAndIsAVillageStreetWide() {
		Village v = village();
		List<Col> cols = road(100, 0, 300);
		Result r = VillageJoin.join(v, cols);
		assertFalse(r.spur().isEmpty());
		assertFalse(r.ends().isEmpty());
		Box street = v.streets().get(0);
		// every spur ends ON the street piece, on its centre line (the street runs along x here: z middle)
		for (int[] e : r.ends()) {
			assertTrue(street.contains(e[0], e[1]), "the spur ends on the street piece, not beside it: " + e[0] + "," + e[1]);
			assertEquals((street.minZ() + street.maxZ()) / 2, e[1], "on the street's centre line");
		}
		Map<Long, Col> m = apply(cols, r);
		// no spur cell on a road column or inside a non-street piece
		for (int[] c : r.spur()) {
			assertFalse(m.containsKey(VillageJoin.key(c[0], c[1])), "spur cell on the road");
			for (Box b : v.pieces()) if (b != street) assertFalse(b.contains(c[0], c[1]), "spur cell inside a house piece " + b);
		}
		// the spur touches the road, and is at least 3 wide along its length and 5 wide at the road
		boolean touchesRoad = false;
		for (int[] c : r.spur()) for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (m.containsKey(VillageJoin.key(c[0] + d[0], c[1] + d[1]))) touchesRoad = true;
		assertTrue(touchesRoad, "the spur starts on the road");
		java.util.Set<Long> cells = new java.util.TreeSet<>();
		for (int[] c : r.spur()) cells.add(VillageJoin.key(c[0], c[1]));
		int[] e = r.ends().get(0);
		for (int dz = -1; dz <= 1; dz++) assertTrue(cells.contains(VillageJoin.key(e[0], e[1] + dz)) || !street.contains(e[0], e[1] + dz), "a village street wide (3) at the end: z offset " + dz);
		assertTrue(r.spur().size() >= 25, "the band is wider than a line: " + r.spur().size());
	}


	@Test
	void answerDoesNotDependOnColumnOrder() {
		Village v = village();
		List<Col> cols = road(100, 0, 300);
		Result ref = VillageJoin.join(v, cols);
		for (int seed = 0; seed < 5; seed++) {
			List<Col> s = new ArrayList<>(cols);
			Collections.shuffle(s, new Random(seed));
			Result r = VillageJoin.join(v, s);
			assertEquals(ref.removed(), r.removed());
			assertEquals(ref.added(), r.added());
			assertEquals(ref.spur().size(), r.spur().size());
			for (int i = 0; i < ref.spur().size(); i++) assertArrayEquals(ref.spur().get(i), r.spur().get(i));
		}
		// more of the road beyond the window's reach changes nothing
		Result wide = VillageJoin.join(v, road(100, -500, 800));
		assertEquals(ref.removed(), wide.removed());
		assertEquals(ref.added(), wide.added());
	}

	@Test
	void farRoadIsLeftAlone() {
		Village v = village();
		Result r = VillageJoin.join(v, road(300, 0, 300));
		assertTrue(r.isEmpty());
	}

	@Test
	void roadPassingBesideGetsASpurButKeepsItsLine() {
		Village v = village();
		List<Col> cols = road(60, 0, 300); // 25 blocks from the nearest piece
		Result r = VillageJoin.join(v, cols);
		assertTrue(r.removed().isEmpty());
		assertFalse(r.spur().isEmpty());
	}

	/** A 3-wide road whose columns carry a route material (DS-30): road gravel, deck oak planks. */
	static List<Col> materialRoad(int z, int x0, int x1) {
		List<Col> l = new ArrayList<>();
		for (Col c : road(z, x0, x1)) l.add(new Col(c.x(), c.z(), c.y(), false, 3, "minecraft:gravel", "minecraft:oak_planks"));
		return l;
	}

	@Test
	void bentColumnsKeepTheRouteMaterial() {
		Result r = VillageJoin.join(village(), materialRoad(100, 0, 300));
		assertFalse(r.added().isEmpty(), "the road was bent");
		for (Col c : r.added()) {
			assertEquals("minecraft:gravel", c.road(), "bent column " + c + " keeps the route's road block");
			assertEquals("minecraft:oak_planks", c.deck(), "bent column " + c + " keeps the route's deck block");
		}
	}

	/**
	 * A village like a vanilla one: the street piece's box (140..190 x 112..130) is much larger than its path, a 3-wide strip at
	 * z 124..126 from x 142 to 188 (as a template's dirt_path), with a house inside the box's margin; the road passes north at z = 100.
	 */
	static Village pathVillage(boolean houseInMargin) {
		List<Box> pieces = new ArrayList<>(), streets = new ArrayList<>();
		Box street = new Box(140, 112, 190, 130);
		streets.add(street);
		pieces.add(street);
		pieces.add(new Box(150, 132, 158, 140));
		if (houseInMargin) pieces.add(new Box(160, 112, 175, 121)); // a house the street's jigsaw placed inside its box, between road and path
		List<int[]> paths = new ArrayList<>();
		for (int z = 124; z <= 126; z++) for (int x = 142; x <= 188; x++) paths.add(new int[] {x, z});
		return new Village(pieces, streets, paths);
	}

	static java.util.Set<Long> keys(List<int[]> cells) {
		java.util.Set<Long> s = new java.util.TreeSet<>();
		for (int[] c : cells) s.add(VillageJoin.key(c[0], c[1]));
		return s;
	}

	/** The spur's checks: abuts a path cell (never on one), avoids non-street pieces, crosses the street box's margin, and is one 4-connected piece with the road. */
	static void assertSpurReachesPath(Village v, List<Col> cols, Result r) {
		assertFalse(r.ends().isEmpty(), "a spur was made");
		java.util.Set<Long> paths = keys(v.paths()), spur = keys(r.spur());
		Map<Long, Col> m = apply(cols, r);
		for (int[] c : r.spur()) {
			assertFalse(paths.contains(VillageJoin.key(c[0], c[1])), "spur cell " + c[0] + "," + c[1] + " is on the village's own path");
			for (Box b : v.pieces()) if (!v.streets().contains(b)) assertFalse(b.contains(c[0], c[1]), "spur cell " + c[0] + "," + c[1] + " inside a house " + b);
		}
		for (int i = 0; i < r.ends().size(); i++) {
			int[] e = r.ends().get(i), meet = r.meets().get(i);
			assertNotNull(meet, "the spur meets a path cell");
			assertTrue(paths.contains(VillageJoin.key(meet[0], meet[1])), "the met cell is a path cell");
			assertEquals(1, Math.abs(meet[0] - e[0]) + Math.abs(meet[1] - e[1]), "the end abuts the path (4-neighbour)");
			assertTrue(spur.contains(VillageJoin.key(e[0], e[1])), "the end is a spur cell");
			// 4-connected from the end through spur cells to a road column: no gap
			ArrayDeque<Long> q = new ArrayDeque<>(List.of(VillageJoin.key(e[0], e[1])));
			java.util.Set<Long> seen = new java.util.TreeSet<>(q);
			boolean road = false;
			while (!q.isEmpty() && !road) {
				long k = q.poll();
				int x = (int) k, z = (int) (k >> 32);
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					long nk = VillageJoin.key(x + d[0], z + d[1]);
					if (m.containsKey(nk)) road = true;
					else if (spur.contains(nk) && seen.add(nk)) q.add(nk);
				}
			}
			assertTrue(road, "the spur from " + e[0] + "," + e[1] + " reaches the road without a gap");
		}
		Box street = v.streets().get(0);
		boolean inMargin = false;
		for (int[] c : r.spur()) if (street.contains(c[0], c[1])) inMargin = true;
		assertTrue(inMargin, "the spur crosses the street box's grass margin to the path, it does not stop at the box");
	}

	@Test
	void spurAbutsTheVillagesOwnPathNotItsBox() {
		Village v = pathVillage(false);
		List<Col> cols = road(100, 0, 300);
		Result r = VillageJoin.join(v, cols);
		assertSpurReachesPath(v, cols, r);
		assertTrue(r.ends().get(0)[1] == 123, "met end-on from the north, on the row before the path: " + r.ends().get(0)[1]);
	}

	@Test
	void spurGoesRoundAHouseInTheStreetsMargin() {
		Village v = pathVillage(true);
		List<Col> cols = road(100, 0, 300);
		Result r = VillageJoin.join(v, cols);
		assertSpurReachesPath(v, cols, r);
	}

	@Test
	void pathSpurDoesNotDependOnColumnOrder() {
		Village v = pathVillage(true);
		List<Col> cols = road(100, 0, 300);
		Result ref = VillageJoin.join(v, cols);
		List<int[]> shuffledPaths = new ArrayList<>(v.paths());
		for (int seed = 0; seed < 4; seed++) {
			List<Col> s = new ArrayList<>(cols);
			Collections.shuffle(s, new Random(seed));
			Collections.shuffle(shuffledPaths, new Random(seed + 10));
			Result r = VillageJoin.join(new Village(v.pieces(), v.streets(), shuffledPaths), s);
			assertEquals(keys(ref.spur()), keys(r.spur()));
			assertEquals(keys(ref.ends()), keys(r.ends()));
		}
	}

	/** Every pair of 4-neighbouring spur cells, and each line's ends against the road and the path, step at most 1 (DS-31). */
	static void assertGraded(Village v, List<Col> cols, Result r, VillageJoin.Heights h) {
		assertEquals(0, r.steep(), "every spur graded");
		Map<Long, Integer> y = new HashMap<>();
		for (int[] c : r.spur()) {
			assertNotEquals(Integer.MIN_VALUE, c[2], "graded cell " + c[0] + "," + c[1]);
			y.put(VillageJoin.key(c[0], c[1]), c[2]);
		}
		for (int[] c : r.spur()) for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
			Integer n = y.get(VillageJoin.key(c[0] + d[0], c[1] + d[1]));
			if (n != null) assertTrue(Math.abs(n - c[2]) <= 1, "step " + Math.abs(n - c[2]) + " at " + c[0] + "," + c[1]);
		}
		Map<Long, Col> m = apply(cols, r);
		for (int i = 0; i < r.lines().size(); i++) {
			List<int[]> line = r.lines().get(i);
			int[] a = line.get(0), e = line.get(line.size() - 1), meet = r.meets().get(i);
			boolean byRoad = false;
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				Col c = m.get(VillageJoin.key(a[0] + d[0], a[1] + d[1]));
				if (c != null && Math.abs(c.y() - 1 - a[2]) <= 1) byRoad = true;
			}
			assertTrue(byRoad, "the line starts within 1 of the road's surface beside it: " + a[2]);
			assertTrue(Math.abs(h.top(meet[0], meet[1]) - e[2]) <= 1, "the line ends within 1 of the path's height: " + e[2] + " vs " + h.top(meet[0], meet[1]));
			for (int j = 1; j < line.size(); j++) assertTrue(Math.abs(line.get(j)[2] - line.get(j - 1)[2]) <= 1, "centre line step at " + j);
		}
	}

	@Test
	void spurIsGradedFromTheRoadDeckToThePathHeight() {
		VillageJoin.Heights h = (x, z) -> 70 + Math.max(0, z - 105) + ((x * 7 + z * 13) % 3 == 0 ? 2 : 0); // a slope with bumps
		Village v = pathVillage(true);
		List<Col> cols = road(100, 0, 300);
		Result r = VillageJoin.join(v, cols, h);
		assertSpurReachesPath(v, cols, r);
		assertGraded(v, cols, r, h);
	}

	@Test
	void tooSteepForAStraightSpurLeavesTheRoadFurtherAlong() {
		VillageJoin.Heights h = (x, z) -> 70 + 2 * Math.max(0, z - 103); // the street is 42 above the road, 23 cells away
		Village v = pathVillage(false);
		List<Col> cols = road(100, 0, 300);
		Result r = VillageJoin.join(v, cols, h);
		assertSpurReachesPath(v, cols, r);
		assertGraded(v, cols, r, h);
		assertTrue(r.lines().get(0).size() > 30, "longer than the straight 23-cell line, enough for its rise: " + r.lines().get(0).size());
	}

	@Test
	void ungradableSpurIsCounted() {
		VillageJoin.Heights h = (x, z) -> z > 110 ? 300 : 70; // a cliff no line within reach can climb in steps of 1
		Village v = pathVillage(false);
		Result r = VillageJoin.join(v, road(100, 0, 300), h);
		assertEquals(1, r.steep());
	}
}
