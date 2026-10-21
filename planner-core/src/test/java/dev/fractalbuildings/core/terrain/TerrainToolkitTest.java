package dev.fractalbuildings.core.terrain;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.Rect;
import dev.fractalbuildings.core.terrain.GroundBox.Kind;
import dev.fractalbuildings.core.terrain.LatticePath.Bounds;
import dev.fractalbuildings.core.terrain.LatticePath.Pt;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerrainToolkitTest {
	/** Flat land at y 64 with a north-south river of the given width starting at x0, depth 5. */
	static TerrainProbe river(int x0, int width) {
		return new TerrainProbe() {
			@Override public int floorY(int x, int z) { return x >= x0 && x < x0 + width ? 59 : 64; }
			@Override public int waterDepth(int x, int z) { return x >= x0 && x < x0 + width ? 5 : 0; }
		};
	}

	static final Bounds BIG = new Bounds(-100, -100, 300, 300);

	// ---- WaterField

	@Test
	void waterFieldChamfer() {
		var t = river(100, 64); // water at x 100..163: samples at 128 and 160 (stride 32)
		var wf = WaterField.of(WaterSamples.of(t, 32), 0, 0, 10, 4);
		assertTrue(wf.isWater(4, 1));
		assertFalse(wf.isWater(3, 1));
		assertEquals(32, wf.distance(3, 1));
		assertEquals(32, wf.distance(6, 1));
		assertEquals(96, wf.distance(1, 1));
		assertEquals(0, wf.distance(4, 3));
		assertEquals(1, wf.componentCount());
		assertEquals(8, wf.componentSize(1));
		assertFalse(wf.isOcean(1));
		assertEquals(wf.distance(3, 1), wf.distanceAt(96, 32));
	}

	@Test
	void waterFieldDiagonalDistance() {
		WaterSamples one = new WaterSamples() {
			public int stride() { return 10; }
			public boolean water(int x, int z) { return x == 0 && z == 0; }
		};
		var wf = WaterField.of(one, 0, 0, 4, 4);
		assertEquals(14, wf.distance(1, 1));
		assertEquals(28, wf.distance(2, 2));
		assertEquals(38, wf.distance(3, 2)); // 2 diagonal + 1 straight = 28 + 10
	}

	@Test
	void oceanVersusRiverBySize() {
		// a 70 x 70 sample sea (4900 samples) beside a 1-sample pond
		WaterSamples s = new WaterSamples() {
			public int stride() { return 32; }
			public boolean water(int x, int z) {
				int i = x / 32, j = z / 32;
				return (i < 70 && j < 70) || (i == 75 && j == 5);
			}
		};
		var wf = WaterField.of(s, 0, 0, 80, 80);
		assertEquals(2, wf.componentCount());
		int sea = wf.component(0, 0), pond = wf.component(75, 5);
		assertNotEquals(sea, pond);
		assertEquals(1, sea); // scan order
		assertTrue(wf.isOcean(sea));
		assertFalse(wf.isOcean(pond));
		assertFalse(wf.isOcean(0));
	}

	// ---- LatticePath

	@Test
	void straightLineOnFlatGround() {
		var t = SyntheticTerrain.flat(64);
		var cost = LatticePath.defaultCost(t, WaterSamples.of(t, 8));
		var p = LatticePath.find(new Pt(0, 0), new Pt(64, 0), cost, BIG, 8);
		assertEquals(9, p.size());
		for (Pt q : p) assertEquals(0, q.z());
		var d = LatticePath.find(new Pt(0, 0), new Pt(40, 40), cost, BIG, 8);
		assertEquals(6, d.size());
		for (int i = 0; i < d.size(); i++) assertEquals(d.get(i).x(), d.get(i).z());
	}

	@Test
	void goesRoundAHillWhenCheaper() {
		var hill = SyntheticTerrain.hill(80, 0, 40, 60, 64);
		var cost = LatticePath.defaultCost(hill, WaterSamples.of(hill, 8));
		var p = LatticePath.find(new Pt(0, 0), new Pt(160, 0), cost, BIG, 8);
		assertFalse(p.isEmpty());
		long maxAbsZ = p.stream().mapToLong(q -> Math.abs(q.z())).max().getAsLong();
		assertTrue(maxAbsZ >= 24, "detour expected, max |z| = " + maxAbsZ);
		int peakY = p.stream().mapToInt(q -> hill.floorY(q.x(), q.z())).max().getAsInt();
		assertTrue(peakY < 90, "path avoids the summit");
	}

	@Test
	void waterRunLimit() {
		for (int[] c : new int[][] {{40, 1}, {41, 0}}) { // width, expect found
			var t = river(100, c[0]);
			var cost = LatticePath.defaultCost(t, WaterSamples.of(t, 1));
			// narrow bounds force the crossing
			var p = LatticePath.find(new Pt(90, 0), new Pt(160, 0), cost, new Bounds(80, -1, 180, 1), 1);
			assertEquals(c[1] == 1, !p.isEmpty(), "river width " + c[0]);
		}
	}

	@Test
	void waterRunUnlimitedWhenNegative() {
		var t = river(100, 60);
		var cost = LatticePath.defaultCost(t, WaterSamples.of(t, 1));
		var p = LatticePath.find(new Pt(90, 0), new Pt(170, 0), cost, new Bounds(80, -1, 180, 1), 1, -1, 100_000, false);
		assertFalse(p.isEmpty());
	}

	@Test
	void tiesAreIndependentOfNeighbourOrder() {
		// flat ground: many equal-cost paths between (0,0) and (48,16)
		var t = SyntheticTerrain.flat(64);
		var cost = LatticePath.defaultCost(t, WaterSamples.of(t, 8));
		var b = new Bounds(-40, -40, 100, 100);
		var a = LatticePath.find(new Pt(0, 0), new Pt(48, 16), cost, b, 8, 40, 100_000, false);
		var r = LatticePath.find(new Pt(0, 0), new Pt(48, 16), cost, b, 8, 40, 100_000, true);
		assertEquals(a, r);
		var again = LatticePath.find(new Pt(0, 0), new Pt(48, 16), cost, b, 8, 40, 100_000, false);
		assertEquals(a, again);
	}

	@Test
	void budgetBoundsSearchAndOffLatticeGoalThrows() {
		var t = SyntheticTerrain.flat(64);
		var cost = LatticePath.defaultCost(t, WaterSamples.of(t, 8));
		assertTrue(LatticePath.find(new Pt(0, 0), new Pt(800, 0), cost, new Bounds(-100, -100, 900, 900), 8, 40, 5, false).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> LatticePath.find(new Pt(0, 0), new Pt(7, 0), cost, BIG, 8));
	}

	// ---- Grading.road

	static List<Pt> line(int x0, int z0, int x1, int z1, int step) {
		ArrayList<Pt> out = new ArrayList<>();
		int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)) / step;
		for (int k = 0; k <= n; k++) out.add(new Pt(x0 + (x1 - x0) * k / n, z0 + (z1 - z0) * k / n));
		return out;
	}

	static void assertGrade(Grading.Profile p) {
		for (int i = 1; i < p.size(); i++) assertTrue(Math.abs(p.y()[i] - p.y()[i - 1]) <= 1, "step at " + i);
		for (int i = 3; i < p.size(); i++) {
			if (p.bridge()[i] || p.bridge()[i - 3]) continue;
			assertTrue(Math.abs(p.y()[i] - p.y()[i - 3]) <= 1, "3-block grade at " + i);
		}
	}

	@Test
	void roadOnSteepPlaneIsGradedAndFlagsExcess() {
		// ground rises 1 per 2 blocks along x; the road may rise only 1 per 3
		var t = SyntheticTerrain.plane(64, 1, 0, 2);
		var g = Grading.road(line(0, 0, 96, 0, 8), 3, t);
		assertGrade(g.profile());
		assertFalse(g.steep().isEmpty(), "ground outruns the road, excess must be flagged");
		// flagged exactly where |road - ground| > 4
		for (int i = 0; i < g.profile().size(); i++) {
			int diff = Math.abs(g.profile().y()[i] - t.floorY(g.profile().x()[i], g.profile().z()[i]));
			final int idx = i;
			boolean flagged = g.steep().stream().anyMatch(s -> idx >= s.from() && idx <= s.to());
			assertEquals(diff > Grading.MAX_CUT_FILL, flagged, "column " + i);
		}
	}

	@Test
	void roadOnGentlePlaneNeedsNoFlagsAndBoxesStayShallow() {
		var t = SyntheticTerrain.plane(64, 1, 0, 4); // 1 in 4, inside the grade limit
		var g = Grading.road(line(0, 0, 96, 0, 8), 5, t);
		assertGrade(g.profile());
		assertTrue(g.steep().isEmpty());
		for (var b : g.boxes()) {
			if (b.kind() == Kind.FILL || b.kind() == Kind.CUT) assertTrue(b.box().sy() <= Grading.MAX_CUT_FILL);
		}
		// every box lies within the road footprint (centre line +- width / 2)
		for (var b : g.boxes()) {
			assertTrue(b.box().x() >= -2 && b.box().maxX() <= 96 + 3, "x " + b.box());
			assertTrue(b.box().z() >= -2 && b.box().maxZ() <= 3, "z " + b.box());
		}
		// one road surface block per footprint cell, 97 x 5 columns
		long road = g.boxes().stream().filter(b -> b.kind() == Kind.ROAD).mapToLong(b -> b.box().volume()).sum();
		assertEquals(101L * 5, road);
	}

	@Test
	void roadOnFlatGroundIsOneBox() {
		var t = SyntheticTerrain.flat(70);
		var g = Grading.road(line(0, 0, 0, 64, 8), 3, t);
		assertEquals(1, g.boxes().size());
		assertEquals(Kind.ROAD, g.boxes().get(0).kind());
		assertEquals(69, g.boxes().get(0).box().y());
	}

	@Test
	void bridgeOverRiver() {
		var t = river(20, 24); // water x 20..43, surface 64, bed 59
		var g = Grading.road(line(0, 0, 64, 0, 8), 3, t);
		var p = g.profile();
		assertGrade(p);
		for (int i = 0; i < p.size(); i++) {
			if (p.bridge()[i]) assertEquals(67, p.y()[i], "deck at surface + 3");
		}
		long deck = g.boxes().stream().filter(b -> b.kind() == Kind.DECK).mapToLong(b -> b.box().volume()).sum();
		assertEquals(24L * 3, deck);
		var piers = g.boxes().stream().filter(b -> b.kind() == Kind.PIER).toList();
		assertEquals(3, piers.size());
		for (var b : piers) {
			assertEquals(59, b.box().y());
			assertEquals(1, b.box().sx());
			assertEquals(7, b.box().sy(), "pier reaches the deck");
		}
		assertTrue(g.steep().isEmpty(), g.steep().toString());
	}

	// ---- Grading.plaza

	@Test
	void plazaOnGentleSlopeIsLevelAndCoversRect() {
		var t = SyntheticTerrain.plane(64, 1, 0, 8); // 1 in 8
		var rect = new Rect(0, 0, 24, 16);
		var pg = Grading.plaza(rect, t).orElseThrow();
		long plaza = 0;
		boolean[][] seen = new boolean[24][16];
		for (var b : pg.boxes()) {
			if (b.kind() != Kind.PLAZA) continue;
			assertEquals(pg.level() - 1, b.box().y());
			assertEquals(1, b.box().sy());
			for (int x = b.box().x(); x < b.box().maxX(); x++) {
				for (int z = b.box().z(); z < b.box().maxZ(); z++) {
					assertFalse(seen[x][z], "overlap at " + x + "," + z);
					seen[x][z] = true;
					plaza++;
				}
			}
		}
		assertEquals(24L * 16, plaza);
		// every column of row 0 ends at the plaza surface
		for (int x = 0; x < 24; x++) {
			int g = t.floorY(x, 0), fill = 0, cut = 0;
			for (var b : pg.boxes()) {
				if (b.box().x() <= x && x < b.box().maxX() && b.box().z() <= 0 && 0 < b.box().maxZ()) {
					if (b.kind() == Kind.FILL) { assertEquals(g, b.box().y()); fill += b.box().sy(); }
					if (b.kind() == Kind.CUT) { assertEquals(pg.level(), b.box().y()); cut += b.box().sy(); }
				}
			}
			if (fill > 0) assertEquals(pg.level(), g + fill + 1, "fill column " + x);
			else if (cut > 0) assertEquals(pg.level(), g - cut, "cut column " + x);
			else assertTrue(g == pg.level() || g == pg.level() - 1, "untouched column " + x);
		}
	}

	@Test
	void plazaRefusesRoughOrWetGround() {
		assertTrue(Grading.plaza(new Rect(0, 0, 16, 16), SyntheticTerrain.plane(64, 1, 0, 1)).isEmpty());
		assertTrue(Grading.plaza(new Rect(10, 0, 20, 8), river(15, 4)).isEmpty());
		assertTrue(Grading.plaza(new Rect(0, 0, 16, 16), SyntheticTerrain.flat(64)).isPresent());
	}

	// ---- LotGround

	@Test
	void lotRelief() {
		var t = SyntheticTerrain.plane(64, 1, 0, 2);
		var lot = new Lot("a", 0, 0, 11, 9, Facing.EAST);
		var r = LotGround.relief(lot, t);
		assertEquals(64, r.min());
		assertEquals(69, r.max());
		assertEquals(5, r.relief());
		assertEquals(69, r.frontY()); // east edge x = 10
		assertEquals(64, LotGround.relief(new Lot("b", 0, 0, 11, 9, Facing.WEST), t).frontY());
		assertEquals(0, LotGround.relief(new Lot("c", 0, 0, 8, 8, Facing.NORTH), SyntheticTerrain.flat(70)).relief());
	}

	@Test
	void groundBoxesAreUsable() {
		assertNotNull(new GroundBox(Box3.of(0, 0, 0, 1, 1, 1), Kind.ROAD));
	}
}
