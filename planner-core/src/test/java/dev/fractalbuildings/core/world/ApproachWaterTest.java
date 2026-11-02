package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.road.Curve;
import dev.fractalbuildings.core.road.Profile;
import dev.fractalbuildings.core.road.RoadNet;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** 6.15 ROADJOIN step 2: approaches follow the water rule (ADR 0004 N5, WR1 to WR3): decks over water, never at water level. */
class ApproachWaterTest {
	/** Flat ground at y 64 with a river across x in [riverX, riverX + width), water to 63 (surface y 64... see below). */
	record River(int riverX, int width, int ground, int bed) implements TerrainProbe {
		boolean in(int x) { return x >= riverX && x < riverX + width; }
		@Override public int floorY(int x, int z) { return in(x) ? bed : ground; }
		@Override public int waterDepth(int x, int z) { return in(x) ? ground - bed : 0; }
	}

	static double[][] line(int len) {
		List<int[]> nodes = new ArrayList<>();
		nodes.add(new int[] {0, 0});
		nodes.add(new int[] {len, 0});
		return Curve.stations(nodes);
	}

	@Test
	void shortRiverGetsOneFlatDeckAboveTheWater() {
		var t = new River(60, 10, 64, 60);
		var r = Profile.approach(line(160), t, new TreeMap<>(), 3, false);
		assertEquals(0, r.wetFailures());
		assertEquals(1, r.crossings().size());
		var c = r.crossings().get(0);
		// the water's first free y is 64; the deck is at least one above it, and the span is over 6 so one air block more
		assertTrue(c.deck() >= 66, "deck " + c.deck());
		for (int k = c.first(); k <= c.last(); k++) assertEquals(c.deck(), r.deck()[k]);
		assertEquals(0, r.steepJoins());
		// the ramp onto the deck is 1 in 4 at most on dry land
		for (int k = 0; k + 1 < r.deck().length; k++) assertTrue(Math.abs(r.deck()[k] - r.deck()[k + 1]) <= Profile.MAX_STEP);
	}

	@Test
	void townTierGetsOneAirBlockOnAShortSpan() {
		var t = new River(60, 4, 64, 60); // 4 wide: not over HIGH_SPAN
		int plain = Profile.approach(line(160), t, new TreeMap<>(), 3, false).crossings().get(0).deck();
		int town = Profile.approach(line(160), t, new TreeMap<>(), 3, true).crossings().get(0).deck();
		assertTrue(plain >= 65, "deck above the water: " + plain);
		assertEquals(plain + 1, town, "town tier and above: one air block under the deck");
	}

	@Test
	void streetPinnedAtWaterLevelIsLiftedAboveTheWater() {
		var t = new River(-4, 12, 64, 60); // the exit lies on the water; the street there is at water level (64)
		var pins = new TreeMap<Integer, Integer>();
		pins.put(0, 64);
		var r = Profile.approach(line(80), t, pins, 3, false);
		for (int k = 0; k <= 1; k++) assertTrue(r.deck()[k] >= 65, "station " + k + " deck " + r.deck()[k]);
	}

	@Test
	void runOverMaxDeckRunIsCountedAsLong() {
		var t = new River(30, 120, 64, 60);
		var r = Profile.approach(line(240), t, new TreeMap<>(), 3, false);
		assertEquals(1, r.longRuns());
		var ok = Profile.approach(line(160), new River(60, 30, 64, 60), new TreeMap<>(), 3, false);
		assertEquals(0, ok.longRuns(), "a 30-block crossing is a failure of the 20 rule, not a long run");
	}

	/** LONGDECK: a 40-block water approach gets no deck at hamlet or village tier (max run 20), but does at town tier and up (max run 100). */
	@Test
	void longApproachDeckIsForTownsAndUp() {
		var t = new River(60, 40, 64, 60);
		var hamlet = Profile.approach(line(160), t, new TreeMap<>(), 3, false, Profile.MAX_CROSSING);
		assertEquals(1, hamlet.longRuns(), "a hamlet or village does not deck a 40-block run: the approach is dropped or rerouted");
		var town = Profile.approach(line(160), t, new TreeMap<>(), 3, true, Profile.MAX_DECK_RUN);
		assertEquals(0, town.longRuns());
		assertEquals(1, town.crossings().size(), "a town decks it");
		var shortRun = Profile.approach(line(160), new River(60, 20, 64, 60), new TreeMap<>(), 3, false, Profile.MAX_CROSSING);
		assertEquals(0, shortRun.longRuns(), "20 blocks or less is unchanged for every tier");
	}

	/** DECKDIAG F1: water beside the road (3 blocks off the centre line, outside its footprint) does not make a station wet. */
	record Beside() implements TerrainProbe {
		@Override public int floorY(int x, int z) { return z >= 3 ? 60 : 64; }
		@Override public int waterDepth(int x, int z) { return z >= 3 ? 4 : 0; }
	}

	@Test
	void besideTheShoreIsNotWet() {
		var r = Profile.approach(line(160), new Beside(), new TreeMap<>(), 3, true);
		assertEquals(0, r.crossings().size(), "the footprint z -1..1 is dry");
		assertEquals(0, r.wetFailures());
	}

	/** A 3-wide stream across the road between two stations. */
	@Test
	void streamBetweenStationsIsStillDecked() {
		var r = Profile.approach(line(80), new River(12, 3, 64, 60), new TreeMap<>(), 3, false);
		assertEquals(1, r.crossings().size());
		var c = r.crossings().get(0);
		assertTrue(r.span()[c.first()] && r.span()[c.last()]);
		assertTrue(c.deck() >= 65, "above the water: " + c.deck());
	}

	/** DECKDIAG F2 clause (b): a river crossed square, then a strip of puddles (depth 1): the puddles do not count as deep stations, the river does. */
	record Marsh() implements TerrainProbe {
		@Override public int floorY(int x, int z) { return x >= 60 && x < 90 ? 60 : 64; }
		@Override public int waterDepth(int x, int z) { return x >= 60 && x < 90 ? 4 : x >= 90 && x < 110 ? 1 : 0; }
	}

	@Test
	void shallowMarshAfterARiverIsStillAcross() {
		var r = Profile.approach(line(200), new Marsh(), new TreeMap<>(), 3, true);
		assertEquals(0, r.alongRuns(), "30 of river then 20 of puddles: a crossing");
	}

	/** DECKDIAG F2 clause (c), the architect's angle clause: a road straight down the middle of a 20-wide river is along it. */
	record Channel() implements TerrainProbe {
		boolean in(int x, int z) { return x >= 40 && x < 100 && z >= -10 && z < 10; }
		@Override public int floorY(int x, int z) { return in(x, z) ? 60 : 64; }
		@Override public int waterDepth(int x, int z) { return in(x, z) ? 4 : 0; }
	}

	@Test
	void deckDownTheMiddleOfARiverIsAlong() {
		var r = Profile.approach(line(160), new Channel(), new TreeMap<>(), 3, true);
		assertEquals(1, r.alongRuns(), "60 blocks down a 20-wide river: water 60 long, 20 across, over 1.5x");
		var square = Profile.approach(line(160), new River(60, 30, 64, 60), new TreeMap<>(), 3, true);
		assertEquals(0, square.alongRuns());
	}

	@Test
	void infeasiblePinsKeepTheStreetAndClampThePort() {
		// ground flat at 64; street pinned at 64 at the exit, the graph road's deck 40 higher at the end 15 stations away (28 is all 1 in 4 allows)
		var t = new River(1000, 1, 64, 60);
		var pins = new TreeMap<Integer, Integer>();
		pins.put(0, 64);
		double[][] st = line(8 * 14);
		int last = st.length - 1;
		pins.put(last, 104);
		var r = Profile.approach(st, t, pins, 3, false);
		assertEquals(64, r.deck()[0], "the street pin holds");
		assertEquals(64 + 2 * last, r.deck()[last], "the port pin is clamped to the grade limit from the street, no further");
		assertEquals(104 - (64 + 2 * last), r.portExcess(), "the step at the port is exactly the infeasible remainder");
		for (int k = 0; k + 1 < r.deck().length; k++) assertTrue(Math.abs(r.deck()[k] - r.deck()[k + 1]) <= Profile.MAX_STEP);
	}

	@Test
	void lakeRoadIsNotBuilt() {
		WorldPlanner w = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		var site = w.siteOf(new dev.fractalbuildings.core.settlement.Cell(2, 1)).orElseThrow();
		for (var an : w.approachNodes(site)) assertTrue(!an.road().id().equals("k2_1_2"), "the 190-block lake deck is dropped, not drawn");
	}

	/** Water in the strip z in [-3, 60) for x in [40, 80): a road along z = 0 wades 3 blocks from the bank; one across it (river along x) does not. */
	record Strip(int x0, int x1) implements TerrainProbe {
		boolean in(int x, int z) { return x >= x0 && x < x1 && z >= -3 && z < 60; }
		@Override public int floorY(int x, int z) { return in(x, z) ? 60 : 64; }
		@Override public int waterDepth(int x, int z) { return in(x, z) ? 4 : 0; }
	}

	@Test
	void longRunAlongABankIsCountedSeparately() {
		var along = Profile.approach(line(160), new Strip(40, 80), new TreeMap<>(), 3, false); // 40 blocks of road in water 3 from the bank
		assertEquals(1, along.wetFailures());
		assertEquals(1, along.alongRuns(), "wading beside the bank");
		var across = Profile.approach(line(160), new River(60, 30, 64, 60), new TreeMap<>(), 3, false);
		assertEquals(1, across.wetFailures());
		assertEquals(0, across.alongRuns(), "a river across the road is a crossing");
	}

	@Test
	void wideRiverIsCountedNotFilled() {
		var t = new River(60, 30, 64, 60);
		var r = Profile.approach(line(160), t, new TreeMap<>(), 3, false);
		assertEquals(1, r.wetFailures());
	}

	@Test
	void shoreExitIsACrossingEnd() {
		var t = new River(-4, 12, 64, 60); // the exit lies in the water
		var pins = new TreeMap<Integer, Integer>();
		pins.put(0, 66);
		var r = Profile.approach(line(80), t, pins, 3, false);
		assertEquals(66, r.deck()[0]);
		assertTrue(r.span()[0]);
	}

	@Test
	void corpusHasNoDeckAtWaterLevel() {
		int cols = 0, wet = 0, bad = 0, fails = 0, badUnexplained = 0;
		var worlds = new ArrayList<WorldPlanner>();
		worlds.add(JoinWorld.playtest());
		worlds.add(WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT));
		for (WorldPlanner w : worlds) {
			boolean pt = w == worlds.get(0);
			List<int[]> cells = new ArrayList<>();
			if (pt) { cells.add(new int[] {3, 1}); cells.add(new int[] {1, 2}); }
			else cells.addAll(List.of(WorldPlannerRoadsTest.CELLS));
			TerrainProbe t = w.probes().exact();
			for (int[] c : cells) {
				var so = w.roads().settlement(new dev.fractalbuildings.core.region.CellKey(c[0], c[1]));
				var site = w.siteOf(new dev.fractalbuildings.core.settlement.Cell(c[0], c[1]));
				if (so.isEmpty() || site.isEmpty()) continue;
				for (var a : w.drawnApproaches(site.get(), so.get())) {
					fails += a.wetFailures();
					int aw = 0, ab = 0;
					for (RoadNet.Col k : a.columns()) {
						cols++;
						if (t.waterDepth(k.x(), k.z()) <= 0) continue;
						if (a.port() != null && Math.max(Math.abs(k.x() - a.port().x()), Math.abs(k.z() - a.port().z())) <= 4 * (int) Curve.STATION) continue; // the graph road owns the port end (RD4)
						wet++; aw++;
						if (!k.crossing() || k.y() <= t.surfaceY(k.x(), k.z())) { bad++; ab++; if (ab <= 3) System.out.println("  bad col " + k + " surf " + t.surfaceY(k.x(), k.z()) + " depth " + t.waterDepth(k.x(), k.z())); }
					}
					if (a.wetFailures() == 0) badUnexplained += ab;
					System.out.println("approach " + a.road().id() + " wet " + aw + " bad " + ab + " failures " + a.wetFailures() + " along " + a.alongRuns());
				}
			}
		}
		System.out.println("columns " + cols + " wet " + wet + " bad " + bad + " failures " + fails);
		assertEquals(0, badUnexplained, "wet approach columns that are not a deck above the water, in approaches without a failed crossing");
	}
}
