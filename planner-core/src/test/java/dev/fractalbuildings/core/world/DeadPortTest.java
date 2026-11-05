package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.road.RoadNet.Col;
import dev.fractalbuildings.core.road.RoadPort;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.GateKind;
import dev.fractalbuildings.core.settlement.SettlementSite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * 6.22 DEADPORT (the 6.15 ROADJOIN dead-end-port follow-up): a graph road whose port no approach joins is (a) joined from another exit
 * gate, else (b) dropped with both its approaches (ADR 0004 WR2), unless (c) dropping it would orphan the settlement at its other end.
 * The link rule must not depend on which settlement or chunk the planner is asked about first.
 */
class DeadPortTest {
	static WorldPlanner w12345() { return WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT); }

	static WorldPlanner w987654321(WorldPlanner.Caches caps) {
		return new WorldPlanner(987654321L, WorldConfig.defaults(), JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, "world-987654321-s64")), null, caps);
	}

	static RoadNet.EdgeKey edge(CellKey p, CellKey q) { return p.compareTo(q) < 0 ? new RoadNet.EdgeKey(p, q) : new RoadNet.EdgeKey(q, p); }

	static SettlementSite site(WorldPlanner w, int cx, int cz) { return w.siteOf(new Cell(cx, cz)).orElseThrow(); }

	static RoadNet.Settlement node(WorldPlanner w, int cx, int cz) { return w.roads().settlement(new CellKey(cx, cz)).orElseThrow(); }

	static RoadPort port(WorldPlanner w, int cx, int cz, int x, int z) {
		return w.roads().portsOf(node(w, cx, cz)).stream().filter(p -> p.x() == x && p.z() == z).findFirst().orElseThrow(() -> new AssertionError("no port " + x + "," + z));
	}

	/** The three dead-end ports 6.15 r4 listed on the ROADJOIN corpus are gone: each link is dropped (no dry approach from any exit). */
	@Test
	void theCorpusDeadEndsAreDropped() {
		WorldPlanner w = w12345();
		int[][] cases = {{10, 1, 10560, 1600}, {2, 1, 2496, 2176}, {4, 0, 4755, -96}};
		for (int[] c : cases) {
			SettlementSite s = site(w, c[0], c[1]);
			// DECKDIAG F2: s2_1's approach k2_1_1 to the meeting point 1624,1270 (link to s1_1) was a 32-block deck along the shore of an s64 lake; it is dropped now,
			// and s1_1 serves that link, so it is a kept dead end (case c). The three corpus ports themselves stay dropped.
			assertEquals(c[0] == 2 ? List.of(port(w, 2, 1, 1624, 1270)) : List.of(), w.deadEndPorts(s), s.id() + " keeps a road ending at its port");
			RoadPort p = port(w, c[0], c[1], c[2], c[3]);
			assertTrue(w.droppedPorts(s).contains(p), s.id() + " port " + p + " is dropped");
			assertEquals(WorldPlanner.LinkState.DROPPED, w.linkState(edge(node(w, c[0], c[1]).owner(), p.other())));
		}
		// the whole ROADJOIN corpus: no dead end left but the one DECKDIAG F2 makes (above)
		assertEquals("w12345 2,1 port 1624,1270; ", ApproachJoinTest.deadEnds(), "dead-end-port on the corpus: only the F2 one above");
	}

	/** A dropped link draws nothing: not its road (the graph still routes it), and neither end's approach to it (s2_3 had one). */
	@Test
	void aDroppedLinkDrawsNothing() {
		WorldPlanner w = w12345();
		RoadNet.Settlement a = node(w, 2, 1), b = node(w, 2, 3);
		RoadNet.EdgeKey e = edge(a.owner(), b.owner());
		assertEquals(WorldPlanner.LinkState.DROPPED, w.linkState(e));
		RoadNet.Route r = w.roads().route(e);
		assertTrue(r.ok && !r.touching && !r.byChunk.isEmpty(), "the graph routes the link");
		int routeCols = 0, drawn = 0;
		for (var en : r.byChunk.entrySet()) {
			List<Col> mine = en.getValue();
			int cx = mine.get(0).x() >> 4, cz = mine.get(0).z() >> 4;
			List<Col> world = w.roadColumns(cx, cz);
			for (Col c : mine) { routeCols++; if (world.contains(c)) drawn++; }
		}
		assertTrue(routeCols > 100, "route columns " + routeCols);
		assertEquals(0, drawn, "columns of the dropped road drawn");
		// s2_3 has an approach to the link before the rule, and draws none after it
		assertTrue(w.approachRoads(site(w, 2, 3), b).stream().anyMatch(x -> x.port() != null && x.port().other().equals(a.owner())), "s2_3 serves the link");
		assertTrue(w.drawnApproaches(site(w, 2, 3), b).stream().noneMatch(x -> x.port() != null && x.port().other().equals(a.owner())), "s2_3 draws no approach to a dropped link");
		assertTrue(w.drawnApproaches(site(w, 2, 1), a).stream().noneMatch(x -> x.port() != null && x.port().other().equals(b.owner())));
	}

	/**
	 * Step (a): w987654321 metropolis s2_m2's skeleton approach to its port 1664,-2880 (link to s1_m4) has no dry run; the exit gate at
	 * 2240,-2976 (road k2_m3) reaches the port dry, so the link stays and the road meets the approach at the port. (Until DECKDIAG F1 this was
	 * town s5_0, port 6080,832, gate 5824,736: its own approach lost its phantom deck beside the gate and now serves the port itself, k5_0_0.)
	 */
	@Test
	void anotherExitGateJoinsThePort() {
		WorldPlanner w = w987654321(WorldPlanner.Caches.DEFAULT);
		SettlementSite s = site(w, 2, -2);
		RoadNet.Settlement n = node(w, 2, -2);
		RoadPort p = port(w, 2, -2, 1664, -2880);
		WorldPlanner.Approach a = w.drawnApproaches(s, n).stream().filter(x -> p.equals(x.port())).findFirst().orElseThrow(() -> new AssertionError("no approach joins " + p));
		assertEquals("k2_m3_91", a.road().id(), "the approach from another exit gate (ALT_ID + port index)");
		var exit = a.road().points().get(0);
		assertEquals(2240, exit.x());
		assertEquals(-2976, exit.z());
		boolean gate = false;
		for (Cell c : s.cells()) for (var g : w.source(s).skeleton(c).gates()) gate |= g.kind() == GateKind.EXIT && g.x() == exit.x() && g.z() == exit.z() && g.road() != null;
		assertTrue(gate, "it leaves from an exit gate with a road");
		assertEquals(WorldPlanner.LinkState.LIVE, w.linkState(edge(n.owner(), p.other())));
		assertEquals(List.of(), w.deadEndPorts(s));
		// it is a drawn approach like any other: one strip, from the gate's street to the port, level with the road there
		WorldPlannerRoadsTest.checkJoinsItsRoad(w, a);
		var j = JoinWorld.joins(w, 2, -2).stream().filter(x -> x.id().equals("k2_m3_91")).findFirst().orElseThrow();
		assertEquals(0, j.wetLow(), "no column on water outside a deck above it: " + j);
		assertTrue(j.contacts() > 0 && j.exitStep() <= 2, "meets its street at the gate: " + j);
	}

	/**
	 * Step (c), pending the user: w12345 town s3_m6 cannot join its link to s4_m7 (no exit faces it and no exit gate reaches it dry), but s4_m7
	 * has no other link both of whose ends are joined, so dropping it would orphan s4_m7: the road stays, counted as a dead end.
	 */
	@Test
	void aLinkThatWouldOrphanTheFarSettlementIsKeptAndCounted() {
		WorldPlanner w = w12345();
		SettlementSite s = site(w, 3, -6);
		RoadNet.Settlement n = node(w, 3, -6), far = node(w, 4, -7);
		RoadPort p = port(w, 3, -6, 3776, -6528);
		RoadNet.EdgeKey e = edge(n.owner(), far.owner());
		assertEquals(WorldPlanner.LinkState.DEAD_END, w.linkState(e));
		assertEquals(List.of(p), w.deadEndPorts(s));
		for (RoadNet.EdgeKey f : w.roads().edgesOf(far)) if (!f.equals(e)) assertFalse(w.linkState(f) == WorldPlanner.LinkState.LIVE, "s4_m7 has another live link " + f);
		assertTrue(w.drawnApproaches(site(w, 4, -7), far).stream().anyMatch(x -> x.port() != null && x.port().other().equals(n.owner())), "the far end's approach stays");
		RoadNet.Route r = w.roads().route(e);
		Col c = r.byChunk.firstEntry().getValue().get(0);
		assertTrue(w.roadColumns(c.x() >> 4, c.z() >> 4).contains(c), "the road stays");
	}

	/** Chunks whose road columns the link rule decides: along the dropped s2_1-s2_3 road, at the ports of the corpus and at s3_m6's kept road. */
	static List<int[]> ruleChunks(WorldPlanner w) {
		List<int[]> out = new ArrayList<>();
		for (RoadNet.EdgeKey e : List.of(edge(new CellKey(2, 1), new CellKey(2, 3)), edge(new CellKey(3, -6), new CellKey(4, -7)), edge(new CellKey(10, 1), new CellKey(10, 0)))) {
			int k = 0;
			for (List<Col> l : w.roads().route(e).byChunk.values()) if (k++ % 3 == 0) out.add(new int[] {l.get(0).x() >> 4, l.get(0).z() >> 4});
		}
		for (int[] p : new int[][] {{10560, 1600}, {2496, 2176}, {4755, -96}, {3776, -6528}}) out.add(new int[] {p[0] >> 4, p[1] >> 4});
		return out;
	}

	static String digest(WorldPlanner w, int[] chunk) {
		StringBuilder sb = new StringBuilder();
		for (List<Col> l : w.roadColumnsAround(chunk[0], chunk[1])) sb.append(l).append('\n');
		return sb.toString();
	}

	/**
	 * Order independence: a fresh planner asked about the rule's chunks in reverse order, from 8 threads in a shuffled order, with the
	 * settlement and approach caches at 2 entries (each settlement's approaches are rebuilt over and over), says the same as one asked in
	 * order with the default caches. The link rule reads other settlements' approaches, never another link's state, so nothing it answers
	 * can depend on what was planned first.
	 */
	@Test
	void theRuleDoesNotDependOnWhatIsAskedFirst() throws Exception {
		WorldPlanner ref = w12345();
		List<int[]> chunks = ruleChunks(ref);
		TreeMap<Integer, String> want = new TreeMap<>();
		for (int i = 0; i < chunks.size(); i++) want.put(i, digest(ref, chunks.get(i)));
		var d = WorldPlanner.Caches.DEFAULT;
		WorldPlanner rev = WorldPlannerRegionTest.planner(WorldConfig.defaults(), new WorldPlanner.Caches(d.tiles(), 2, d.skeletons(), d.region(), d.plans(), d.buildings(), d.ground()).strict());
		for (int i = chunks.size() - 1; i >= 0; i--) assertEquals(want.get(i), digest(rev, chunks.get(i)), "reverse order, chunk " + chunks.get(i)[0] + "," + chunks.get(i)[1]);
		WorldPlanner par = w12345();
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < chunks.size(); i++) order.add(i);
		Collections.shuffle(order, new Random(622));
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Future<String>> fs = new ArrayList<>();
			for (int i : order) fs.add(pool.submit(() -> digest(par, chunks.get(i))));
			for (int k = 0; k < order.size(); k++) assertEquals(want.get(order.get(k)), fs.get(k).get(), "8 threads, chunk " + chunks.get(order.get(k))[0] + "," + chunks.get(order.get(k))[1]);
		} finally {
			pool.shutdownNow();
		}
	}
}
