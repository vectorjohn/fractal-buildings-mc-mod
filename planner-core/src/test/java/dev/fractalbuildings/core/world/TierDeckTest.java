package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.road.Curve;
import dev.fractalbuildings.core.road.Profile;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * LONGDECK where the rule is applied (WorldPlanner.routeNodes and drawApproach): hamlets and villages get no approach deck over 20 blocks,
 * towns and up keep one up to 100. The cases are real approaches whose skeleton route has one across run of 21-100 blocks (not along
 * water, nothing over 100) and for which no dry reroute exists, so the tier is the only reason the approach is kept or dropped.
 */
class TierDeckTest {
	/** The profile of a skeleton approach (with its port) at the town limit: the run the tier rule decides on. */
	static Profile.ApproachResult atTownLimit(WorldPlanner w, int cx, int cz, String id) {
		var site = w.siteOf(new Cell(cx, cz)).orElseThrow();
		var owner = w.roads().settlement(new dev.fractalbuildings.core.region.CellKey(cx, cz)).orElseThrow();
		List<int[]> nodes = new ArrayList<>();
		for (Cell c : site.cells()) for (var r : w.source(site).skeleton(c).approaches()) if (r.id().equals(id)) {
			for (var p : r.points()) nodes.add(new int[] {p.x(), p.z()});
			var end = r.points().get(r.points().size() - 1);
			for (var p : w.roads().portsOf(owner)) if (Math.abs(p.x() - end.x()) <= 32 && Math.abs(p.z() - end.z()) <= 32) { nodes.add(new int[] {p.x(), p.z()}); break; }
		}
		return Profile.approach(Curve.stations(nodes), w.probes().roadExact(), new TreeMap<>(), 3, true, Profile.MAX_DECK_RUN);
	}

	static int longest(Profile.ApproachResult r) {
		int l = 0;
		for (var c : r.crossings()) l = Math.max(l, c.length());
		return l;
	}

	static boolean kept(WorldPlanner w, int cx, int cz, String id) {
		return w.approachNodes(w.siteOf(new Cell(cx, cz)).orElseThrow()).stream().anyMatch(a -> a.road().id().equals(id));
	}

	@Test
	void hamletDropsAnAcrossRunOver20() {
		WorldPlanner w = DeadPortTest.w987654321(WorldPlanner.Caches.DEFAULT);
		assertEquals(Tier.HAMLET, w.siteOf(new Cell(2, 9)).orElseThrow().tier());
		var r = atTownLimit(w, 2, 9, "k2_9_0");
		assertTrue(longest(r) > 20 && longest(r) <= Profile.MAX_DECK_RUN && r.longRuns() == 0 && r.alongRuns() == 0, "an across run of 21-100: " + longest(r));
		assertTrue(!kept(w, 2, 9, "k2_9_0"), "a hamlet gets no long deck: the approach is dropped");
	}

	@Test
	void villageDropsAnAcrossRunOver20() {
		WorldPlanner w = JoinWorld.pt1573();
		assertEquals(Tier.VILLAGE, w.siteOf(new Cell(-6, 4)).orElseThrow().tier());
		var r = atTownLimit(w, -6, 4, "km6_4_0");
		assertTrue(longest(r) > 20 && longest(r) <= Profile.MAX_DECK_RUN && r.longRuns() == 0 && r.alongRuns() == 0, "an across run of 21-100: " + longest(r));
		assertTrue(!kept(w, -6, 4, "km6_4_0"), "a village gets no long deck: the approach is dropped");
	}

	@Test
	void townKeepsAnAcrossRunOver20() {
		WorldPlanner w = JoinWorld.deckdiag();
		assertEquals(Tier.TOWN, w.siteOf(new Cell(8, 10)).orElseThrow().tier());
		var r = atTownLimit(w, 8, 10, "k8_10_0");
		assertTrue(longest(r) > 20 && r.longRuns() == 0 && r.alongRuns() == 0, "an across run of 21-100: " + longest(r));
		assertTrue(kept(w, 8, 10, "k8_10_0"), "a town keeps its 44-block crossing deck");
	}
}
