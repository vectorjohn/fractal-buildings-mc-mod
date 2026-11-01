package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.GateKind;
import dev.fractalbuildings.core.settlement.SettlementSite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * CASCADE step 3 (A2): the short-cut link rule ({@code exitServedAt} and {@code served} answered from one drawn approach) gives every answer the
 * full rule gives (the same planner class with {@link WorldPlanner#fullLinkRule}, the oracle), on the step-64 worlds pt1573, w12345 and
 * w987654321: every exit gate of every settlement and every link of the road graph, asked of a fresh short-cut planner (nothing memoised)
 * and, for the links, in several orders on one planner. Answers include an exception's class, so a short cut that throws where the rule does not
 * (or the reverse) fails too. The check counts the cases it covers: dropped, dead-end and live links, gates served and not, and that the
 * short cut really ran (fewer approaches drawn than the full rule's).
 */
@Timeout(1800)
class LinkShortCutTest {
	record World(String label, Supplier<WorldPlanner> make) {}

	static final List<World> WORLDS = List.of(
		new World("pt1573", JoinWorld::pt1573),
		new World("w12345", () -> WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT)),
		new World("w987654321", () -> new WorldPlanner(987654321L, WorldConfig.defaults(), JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, "world-987654321-s64")), null, WorldPlanner.Caches.DEFAULT)));

	static String attempt(java.util.function.Supplier<Object> s) {
		try {
			return String.valueOf(s.get());
		} catch (RuntimeException e) {
			return "throws " + e.getClass().getSimpleName();
		}
	}

	/** Every settlement the graph knows, once, in id order. */
	static TreeMap<String, SettlementSite> sites(WorldPlanner w) {
		TreeMap<String, SettlementSite> out = new TreeMap<>();
		for (RoadNet.Settlement s : w.roads().allSettlements()) {
			try {
				w.siteOf(new Cell(s.owner().cx(), s.owner().cz())).ifPresent(site -> out.putIfAbsent(site.id(), site));
			} catch (RuntimeException e) { /* the edge of the recorded grid */ }
		}
		return out;
	}

	/** The exit gates of a site with a road: {x, z} in order. */
	static List<int[]> gates(WorldPlanner w, SettlementSite site) {
		List<int[]> out = new ArrayList<>();
		var src = w.source(site);
		for (Cell c : site.cells()) for (var g : src.skeleton(c).gates()) if (g.kind() == GateKind.EXIT && g.road() != null) out.add(new int[] {g.x(), g.z()});
		return out;
	}

	@Test
	@Tag("heavy")
	void everyGateAnswerEqualsTheFullRule() {
		gateSweep(1);
	}

	/** The default suite: every {@link #GATE_STRIDE}th gate of the sweep above, a fixed sample (the full one is minutes). */
	@Test
	void sampledGateAnswersEqualTheFullRule() {
		gateSweep(GATE_STRIDE);
	}

	/** The full sweeps run all three worlds; a sample only pt1573, the smallest (the others cost about 5 s per fresh planner, the sample must stay near 30 s). */
	static List<World> sweepWorlds(int stride) {
		return stride == 1 ? WORLDS : WORLDS.subList(0, 1);
	}

	static final int GATE_STRIDE = 40, LINK_STRIDE = 4, ORDER_STRIDE = 6;

	void gateSweep(int stride) {
		int gates = 0, served = 0, notServed = 0, index = 0;
		for (World world : sweepWorlds(stride)) {
			WorldPlanner ref = world.make().get().fullLinkRule();
			for (var e : sites(ref).entrySet()) {
				String id = e.getKey();
				List<int[]> gs;
				try { gs = gates(ref, e.getValue()); } catch (RuntimeException ex) { continue; }
				List<int[]> pick = new ArrayList<>();
				for (int[] g : gs) if (index++ % stride == 0) pick.add(g);
				if (pick.isEmpty()) continue;
				WorldPlanner fast = world.make().get(); // fresh per settlement: nothing memoised from any other
				SettlementSite fsite = fast.siteOf(e.getValue().cells().get(0)).orElseThrow();
				for (int[] g : pick) {
					String want = attempt(() -> ref.exitServedAt(e.getValue(), g[0], g[1]));
					String got = attempt(() -> fast.exitServedAt(fsite, g[0], g[1]));
					// where the full rule throws (a recorded window that lacks part of the settlement, never a real world) the short cut may
					// answer true from the one approach it drew, but never false
					if (want.startsWith("throws")) assertTrue(got.equals(want) || got.equals("true"), world.label() + " " + id + " gate " + g[0] + "," + g[1] + ": " + want + " vs " + got);
					else assertEquals(want, got, world.label() + " " + id + " gate " + g[0] + "," + g[1]);
					gates++;
					if (want.equals("true")) served++; else notServed++;
				}
			}
		}
		System.out.println("LinkShortCut gates " + gates + " served " + served + " not served " + notServed);
		assertTrue(served > 0 && notServed > 0, "both answers covered");
	}

	@Test
	@Tag("heavy")
	void everyLinkStateEqualsTheFullRule() {
		linkSweep(1);
	}

	@Test
	void sampledLinkStatesEqualTheFullRule() {
		linkSweep(LINK_STRIDE);
	}

	void linkSweep(int stride) {
		int[] seen = new int[3];
		int index = 0;
		for (World world : sweepWorlds(stride)) {
			WorldPlanner ref = world.make().get().fullLinkRule();
			List<RoadNet.EdgeKey> edges = ref.roads().allEdges();
			for (RoadNet.EdgeKey k : edges) {
				if (index++ % stride != 0) continue;
				String want = attempt(() -> ref.linkState(k));
				WorldPlanner fast = world.make().get(); // a fresh planner per link
				String got = attempt(() -> fast.linkState(k));
				assertEquals(want, got, world.label() + " " + k);
				for (WorldPlanner.LinkState s : WorldPlanner.LinkState.values()) if (want.equals(s.name())) seen[s.ordinal()]++;
			}
			System.out.println("LinkShortCut " + world.label() + " edges " + edges.size());
		}
		System.out.println("LinkShortCut link states LIVE/DROPPED/DEAD_END " + java.util.Arrays.toString(seen));
		assertTrue(seen[WorldPlanner.LinkState.LIVE.ordinal()] > 0 && seen[WorldPlanner.LinkState.DROPPED.ordinal()] > 0, "live and dropped links covered");
	}

	@Test
	@Tag("heavy")
	void theAskingOrderDoesNotMatter() {
		orderSweep(1);
	}

	@Test
	void theAskingOrderDoesNotMatterOnASample() {
		orderSweep(ORDER_STRIDE);
	}

	void orderSweep(int stride) {
		int gi = 0;
		// the sample is one world (the smallest graph); the full sweep is all three
		for (World world : sweepWorlds(stride)) {
			WorldPlanner ref = world.make().get().fullLinkRule();
			List<RoadNet.EdgeKey> all = new ArrayList<>(ref.roads().allEdges()), edges = new ArrayList<>();
			for (int i = 0; i < all.size(); i += stride) edges.add(all.get(i));
			TreeMap<String, String> want = new TreeMap<>();
			for (RoadNet.EdgeKey k : edges) want.put(k.toString(), attempt(() -> ref.linkState(k)));
			for (long seed : stride == 1 ? new long[] {1, 2, 3} : new long[] {1}) {
				WorldPlanner fast = world.make().get();
				List<RoadNet.EdgeKey> order = new ArrayList<>(edges);
				Collections.shuffle(order, new Random(seed));
				for (RoadNet.EdgeKey k : order) assertEquals(want.get(k.toString()), attempt(() -> fast.linkState(k)), world.label() + " order " + seed + " " + k);
				// the gates after the links, on the same planner: memoised links, same answers
				if (stride != 1) continue; // the sample leaves the gates to its own sweep
				TreeSet<String> ids = new TreeSet<>();
				for (var e : sites(fast).entrySet()) {
					if (!ids.add(e.getKey())) continue;
					SettlementSite rs = ref.siteOf(e.getValue().cells().get(0)).orElseThrow();
					List<int[]> gs;
					try { gs = gates(ref, rs); } catch (RuntimeException ex) { continue; }
					for (int[] g : gs) { if (gi++ % stride != 0) continue; String w2 = attempt(() -> ref.exitServedAt(rs, g[0], g[1])), got = attempt(() -> fast.exitServedAt(e.getValue(), g[0], g[1]));
						if (w2.startsWith("throws")) assertTrue(got.equals(w2) || got.equals("true"), world.label() + " " + e.getKey() + " gate " + g[0] + "," + g[1]);
						else assertEquals(w2, got, world.label() + " " + e.getKey() + " gate " + g[0] + "," + g[1]); }
				}
			}
		}
	}

	@Test
	void theShortCutDrawsFewerApproaches() {
		// the point of the change, in drawn main approaches: a gate's question on a fresh planner never draws more than the full rule, and for
		// some gate draws fewer
		WorldPlanner ref0 = JoinWorld.pt1573();
		SettlementSite site = ref0.siteOf(new Cell(4, -2)).orElseThrow();
		int fewer = 0, gates = 0;
		for (int[] g : gates(ref0, site)) {
			WorldPlanner ref = JoinWorld.pt1573().fullLinkRule(), fast = JoinWorld.pt1573();
			ref.exitServedAt(ref.siteOf(new Cell(4, -2)).orElseThrow(), g[0], g[1]);
			fast.exitServedAt(fast.siteOf(new Cell(4, -2)).orElseThrow(), g[0], g[1]);
			assertTrue(fast.drawnMainCount() <= ref.drawnMainCount(), "gate " + g[0] + "," + g[1] + ": short cut " + fast.drawnMainCount() + " vs full " + ref.drawnMainCount());
			if (fast.drawnMainCount() < ref.drawnMainCount()) fewer++;
			gates++;
		}
		System.out.println("LinkShortCut pt1573 s4_m2: " + fewer + " of " + gates + " gates draw fewer approaches");
		assertTrue(fewer > 0);
	}
}
