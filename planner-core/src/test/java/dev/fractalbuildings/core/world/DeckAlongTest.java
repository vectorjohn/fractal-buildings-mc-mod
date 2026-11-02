package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.road.Curve;
import dev.fractalbuildings.core.road.Profile;
import dev.fractalbuildings.core.settlement.Cell;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * DECKDIAG F2: the along test (Profile.alongBank, the architect's revised DS-19 test 3) on the real long decks of John's playtest world:
 * k2_1_0 (21.31.49, the crossing he approved), s8_10 and sm4_m6 are crossings; sm11_m2 (a hamlet's deck along a lake shore) is not (s4_1 is only a coarse-dump case, see shoreRunsAreAlong).
 */
class DeckAlongTest {
	/** The skeleton's approach {@code id} of cell (cx, cz) with its port, as the router first sees it, profiled as a town's. */
	static Profile.ApproachResult skeletonProfile(WorldPlanner w, int cx, int cz, String id, double[][][] stOut) {
		var site = w.siteOf(new Cell(cx, cz)).orElseThrow();
		var owner = w.roads().settlement(new dev.fractalbuildings.core.region.CellKey(cx, cz)).orElseThrow();
		List<int[]> nodes = new ArrayList<>();
		for (Cell c : site.cells()) for (var r : w.source(site).skeleton(c).approaches()) if (r.id().equals(id)) {
			for (var p : r.points()) nodes.add(new int[] {p.x(), p.z()});
			var end = r.points().get(r.points().size() - 1);
			for (var p : w.roads().portsOf(owner)) if (Math.abs(p.x() - end.x()) <= 32 && Math.abs(p.z() - end.z()) <= 32) nodes.add(new int[] {p.x(), p.z()});
		}
		assertFalse(nodes.isEmpty(), id);
		double[][] st = Curve.stations(nodes);
		if (stOut != null) stOut[0] = st;
		return Profile.approach(st, w.probes().roadExact(), new TreeMap<>(), 3, true);
	}

	@Test
	void realCrossingsAreAcross() {
		WorldPlanner w = JoinWorld.deckdiag();
		var k = skeletonProfile(w, 1, 2, "k2_1_0", null);
		assertTrue(k.crossings().stream().anyMatch(c -> c.length() > 20), "it is a long crossing");
		assertEquals(0, k.alongRuns(), "John's approved crossing at 21.31.49 is not along water");
		assertEquals(0, skeletonProfile(w, 8, 10, "k8_10_0", null).alongRuns(), "s8_10");
		assertEquals(0, skeletonProfile(w, -4, -6, "km4_m6_0", null).alongRuns(), "sm4_m6 (80 over a 26 neck) is interim, not along");
	}

	@Test
	void shoreRunsAreAlong() {
		WorldPlanner w = JoinWorld.deckdiag();
		assertEquals(1, skeletonProfile(w, -11, -2, "km11_m2_0", null).alongRuns(), "sm11_m2: the hamlet's deck along the lake shore");
		// s4_1 (k4_1_0) is NOT a case: it is along only on the step-64 dump; on the step-4 crop both its approaches are dry (LONGDECK r2). sm11_m2 has fine terrain.
	}

	@Test
	void approachNodesKeepCrossingsAndDropShoreDecks() {
		WorldPlanner w = JoinWorld.deckdiag();
		assertTrue(w.approachNodes(w.siteOf(new Cell(1, 2)).orElseThrow()).stream().anyMatch(a -> a.road().id().equals("k2_1_0")));
		assertTrue(w.approachNodes(w.siteOf(new Cell(8, 10)).orElseThrow()).stream().anyMatch(a -> a.road().id().equals("k8_10_0")));
		assertTrue(w.approachNodes(w.siteOf(new Cell(-4, -6)).orElseThrow()).stream().anyMatch(a -> a.road().id().equals("km4_m6_0")));
		var lake = w.siteOf(new Cell(-11, -2)).orElseThrow();
		// F3: the raw skeleton route is along the lake and would be dropped; the shore-aware reroute moves west and the approach is live
		assertTrue(w.approachNodes(lake).stream().anyMatch(a -> a.road().id().equals("km11_m2_0")), "sm11_m2: the shore reroute keeps the approach");
		assertTrue(w.deadEndPorts(lake).isEmpty(), "sm11_m2: its port is no longer a dead end");
	}

	@Test
	void shoreDetourLimit() {
		List<int[]> deck = List.of(new int[] {0, 0}, new int[] {100, 0});
		assertTrue(WorldPlanner.shoreDetourOk(deck, List.of(new int[] {0, 0}, new int[] {60, 40}, new int[] {100, 0})), "1.1x, turns under 90");
		assertFalse(WorldPlanner.shoreDetourOk(deck, List.of(new int[] {0, 0}, new int[] {75, 100}, new int[] {100, 0})), "over 1.5x");
		assertFalse(WorldPlanner.shoreDetourOk(List.of(new int[] {0, 0}, new int[] {10, 0}), List.of(new int[] {0, 0}, new int[] {10, 0}, new int[] {0, 1})), "a turn over 90 degrees");
	}
}
