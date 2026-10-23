package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.terrain.Grading;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** JUNCTION: the junction field on hand-made whole streets (crossings, a corner, a gate passage, a bridge). */
class JunctionFieldTest {
	static JunctionField.Whole line(String key, int width, int x0, int z0, int x1, int z1) {
		return new JunctionField.Whole(key, width, StreetCols.columns(List.of(new Pt(x0, z0), new Pt(x1, z1))));
	}

	/** A lattice of streets: an arterial along z = 100 and three streets across it, one ending on it, plus a diagonal road. */
	static List<JunctionField.Whole> lattice() {
		List<JunctionField.Whole> ws = new ArrayList<>();
		ws.add(line(JunctionField.lineKey(1, 100), 9, 0, 100, 400, 100));
		ws.add(line(JunctionField.lineKey(0, 100), 5, 100, 0, 100, 200));
		ws.add(line(JunctionField.lineKey(0, 200), 5, 200, 0, 200, 100)); // ends on the arterial
		ws.add(line(JunctionField.lineKey(0, 300), 5, 300, 0, 300, 200));
		ws.add(line(JunctionField.roadKey("r"), 5, 120, 0, 320, 200)); // 45 degrees, through the arterial and the street at x = 200
		return ws;
	}

	/** Every cell two whole streets cover has one level on both; every profile keeps the grade and its pins. */
	static int assertMeetsAtGrade(JunctionField f, List<JunctionField.Whole> ws) {
		int shared = 0;
		for (int z = -10; z <= 210; z++) {
			for (int x = -10; x <= 410; x++) {
				int[] l = f.levelsAt(x, z);
				if (l == null) continue;
				assertEquals(l[0], l[1], "cell " + x + "," + z);
				int n = 0;
				for (JunctionField.Whole w : ws) {
					int h = w.width() / 2;
					for (int[] c : w.cols()) if (x >= c[0] - h && x < c[0] - h + w.width() && z >= c[1] - h && z < c[1] - h + w.width()) { n++; break; }
				}
				if (n > 1) shared++;
			}
		}
		for (JunctionField.Whole w : ws) {
			JunctionField.Slice s = f.profile(w.key(), w.cols());
			assertNotNull(s, w.key());
			int[] x = new int[w.cols().size()], z = new int[x.length];
			for (int i = 0; i < x.length; i++) { x[i] = w.cols().get(i)[0]; z[i] = w.cols().get(i)[1]; }
			assertEquals(List.of(), SettlementChecks.roadGrade(w.key(), x, z, s.y()), w.key());
			TreeMap<Integer, Integer> pins = f.pins(w.key(), w.cols());
			if (pins != null) for (var e : pins.entrySet()) assertEquals((int) e.getValue(), s.y()[e.getKey()], w.key() + " pin at column " + e.getKey());
		}
		return shared;
	}

	@Test
	void streetsThatCrossOrMeetShareOneLevelOnEveryCellTheyShare() {
		List<JunctionField.Whole> ws = lattice();
		for (TerrainProbe t : List.of(SyntheticTerrain.flat(64), SyntheticTerrain.plane(64, 1, 0, 2), SyntheticTerrain.plane(64, 1, 1, 3), SyntheticTerrain.noise(7, 80, 20, 48))) {
			JunctionField f = JunctionField.of(ws, t);
			assertTrue(f.junctions().size() >= 5, f.junctions().size() + " " + f.junctions());
			int shared = assertMeetsAtGrade(f, ws);
			assertTrue(shared > 200, shared + " shared cells");
		}
	}

	/** Without the field (each street graded alone, the pre-JUNCTION planner) the same streets on a slope meet blocks apart: the test can fail. */
	@Test
	void streetsGradedAloneDoNotMeet() {
		TerrainProbe t = SyntheticTerrain.plane(64, 1, 1, 3);
		int apart = 0;
		for (JunctionField.Whole a : lattice()) {
			for (JunctionField.Whole b : lattice()) {
				if (a == b) continue;
				int[] ya = Grading.profile(JunctionField.lattice(a.cols()), t).profile().y(), yb = Grading.profile(JunctionField.lattice(b.cols()), t).profile().y();
				for (int i = 0; i < a.cols().size(); i++) for (int j = 0; j < b.cols().size(); j++) {
					if (a.cols().get(i)[0] == b.cols().get(j)[0] && a.cols().get(i)[1] == b.cols().get(j)[1] && Math.abs(ya[i] - yb[j]) > 1) apart++;
				}
			}
		}
		assertTrue(apart > 0);
	}

	/** The levels are a function of the streets, not of the order the field is given them in. */
	@Test
	void levelsDoNotDependOnTheOrderOfTheStreets() {
		TerrainProbe t = SyntheticTerrain.noise(3, 80, 24, 40);
		List<JunctionField.Whole> ws = lattice(), rev = new ArrayList<>(ws);
		java.util.Collections.reverse(rev);
		JunctionField a = JunctionField.of(ws, t), b = JunctionField.of(rev, t);
		for (JunctionField.Whole w : ws) {
			assertEquals(a.pins(w.key(), w.cols()), b.pins(w.key(), w.cols()), w.key());
			assertTrue(java.util.Arrays.equals(a.profile(w.key(), w.cols()).y(), b.profile(w.key(), w.cols()).y()), w.key());
		}
	}

	/** A street through a wall gate keeps the passage floor, and the street crossing it there meets it at that level. */
	@Test
	void aGatePassageKeepsItsLevel() {
		TerrainProbe t = SyntheticTerrain.plane(64, 1, 0, 3);
		JunctionField.Whole road = line(JunctionField.roadKey("r"), 5, 0, 50, 200, 50);
		TreeMap<Integer, Integer> gate = new TreeMap<>();
		for (int i = 96; i <= 104; i++) gate.put(i, 90); // ground there is about 97
		List<JunctionField.Whole> ws = List.of(new JunctionField.Whole(road.key(), 5, road.cols(), gate), line(JunctionField.lineKey(0, 100), 5, 100, 0, 100, 100));
		JunctionField f = JunctionField.of(ws, t);
		assertEquals(90, f.profile(road.key(), road.cols()).y()[100]);
		assertTrue(f.junctions().stream().anyMatch(j -> j.level() == 90), f.junctions().toString());
		assertMeetsAtGrade(f, ws);
	}

	/** Streets crossing over water meet on the deck: the junction stands at the water surface plus the clearance, at least. */
	@Test
	void aJunctionOverWaterStandsAtTheDeck() {
		TerrainProbe t = SyntheticTerrain.water(SyntheticTerrain.plane(50, 1, 0, 4), 70); // water up to 70 west of x = 80
		List<JunctionField.Whole> ws = List.of(line(JunctionField.lineKey(1, 50), 5, 0, 50, 200, 50), line(JunctionField.lineKey(0, 40), 5, 40, 0, 40, 100));
		JunctionField f = JunctionField.of(ws, t);
		assertTrue(f.junctions().stream().allMatch(j -> j.level() >= 70 + Grading.BRIDGE_CLEARANCE), f.junctions().toString());
		assertMeetsAtGrade(f, ws);
	}

	/** A street that ends a cell short of another's footprint (the two touch across a cell edge) meets it at one level too. */
	@Test
	void aStreetEndingAgainstAnotherMeetsIt() {
		TerrainProbe t = SyntheticTerrain.plane(64, 1, 1, 3);
		JunctionField.Whole art = line(JunctionField.lineKey(1, 100), 9, 0, 100, 400, 100); // cells z 96..104
		JunctionField.Whole side = line(JunctionField.lineKey(0, 200), 5, 200, 0, 200, 93); // end face up to z 95
		JunctionField f = JunctionField.of(List.of(art, side), t);
		assertEquals(1, f.junctions().size(), f.junctions().toString());
		for (int x = 198; x <= 202; x++) assertTrue(Math.abs(f.levelsAt(x, 95)[0] - f.levelsAt(x, 96)[0]) <= 1, "x " + x);
		JunctionField.Whole far = line(JunctionField.lineKey(0, 200), 5, 200, 0, 200, 92); // a cell short of touching
		assertEquals(0, JunctionField.of(List.of(art, far), t).junctions().size());
	}

	@Test
	void riseCountsTheGradersAnchors() {
		boolean[] a = new boolean[40];
		for (int i = 0; i < 40; i += 8) a[i] = true;
		assertEquals(JunctionField.rise(10, 30), JunctionField.rise(a, 10, 30)); // anchors 16, 24: 2 + 2 + 2
		assertEquals(6, JunctionField.rise(a, 10, 30));
		a[12] = true; // a water span's end: 10 to 12 allows nothing
		assertEquals(5, JunctionField.rise(a, 10, 30));
	}

	/** A column squeezed between two streets three blocks apart in height could meet neither. */
	@Test
	void twoStreetsSideBySideAtDifferentLevelsSplit() {
		TerrainProbe t = (x, z) -> z < 54 ? 60 : 63;
		List<JunctionField.Whole> ws = List.of(line(JunctionField.lineKey(1, 50), 5, 0, 50, 200, 50), line(JunctionField.lineKey(1, 57), 5, 0, 57, 200, 57));
		JunctionField f = JunctionField.of(ws, t);
		assertTrue(f.splits(100, 54, 4));
		assertFalse(f.splits(100, 50, 4));
		assertFalse(f.splits(100, 40, 4));
	}
}
