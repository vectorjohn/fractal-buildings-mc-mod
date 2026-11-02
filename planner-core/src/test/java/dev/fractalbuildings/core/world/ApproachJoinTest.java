package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 6.15 ROADJOIN step 1: an approach's exit end is pinned to the district street it joins, so the two surfaces meet (docs/plans/roadjoin.md).
 * Corpus: the playtest world's hamlet s3_1 and metropolis s1_2 (committed fixtures) and the five world-12345 cells of {@link WorldPlannerRoadsTest}.
 */
class ApproachJoinTest {
	record Row(String world, String cell, JoinWorld.Join join) {
		@Override public String toString() { return world + " " + cell + " " + join; }
	}

	/** dead-end-port and long-run counts of the corpus, printed by corpusJoins. */
	static String deadEnds() {
		StringBuilder sb = new StringBuilder();
		WorldPlanner pt = JoinWorld.playtest();
		for (int[] c : new int[][] {{3, 1}, {1, 2}}) for (var p : JoinWorld.deadEndPorts(pt, c[0], c[1])) sb.append("pt ").append(c[0]).append(",").append(c[1]).append(" port ").append(p.x()).append(",").append(p.z()).append("; ");
		WorldPlanner w = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		for (int[] c : WorldPlannerRoadsTest.CELLS) for (var p : JoinWorld.deadEndPorts(w, c[0], c[1])) sb.append("w12345 ").append(c[0]).append(",").append(c[1]).append(" port ").append(p.x()).append(",").append(p.z()).append("; ");
		return sb.toString();
	}

	static List<Row> corpus() {
		List<Row> rows = new ArrayList<>();
		WorldPlanner pt = JoinWorld.playtest();
		for (int[] c : new int[][] {{3, 1}, {1, 2}}) for (var j : JoinWorld.joins(pt, c[0], c[1])) rows.add(new Row("pt", c[0] + "," + c[1], j));
		WorldPlanner w = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		for (int[] c : WorldPlannerRoadsTest.CELLS) for (var j : JoinWorld.joins(w, c[0], c[1])) rows.add(new Row("w12345", c[0] + "," + c[1], j));
		return rows;
	}

	@Test
	void hamletGateMeetsTheStreet() {
		WorldPlanner pt = JoinWorld.playtest();
		var joins = JoinWorld.joins(pt, 3, 1);
		var g0 = joins.stream().filter(j -> j.exit().x() == 3616 && j.exit().z() == 1440).findFirst().orElseThrow();
		System.out.println("hamlet g0: " + g0);
		assertTrue(g0.contacts() > 0, "touches the street");
		assertTrue(g0.maxStep() <= 1, "step at the hamlet gate: " + g0);
	}

	@Test
	void corpusJoins() {
		int touching = 0, stepped = 0, atExit = 0, farBad = 0, farStep = 0;
		for (Row r : corpus()) {
			System.out.println(r);
			farBad += r.join().farBad(); farStep = Math.max(farStep, r.join().farStep());
			if (r.join().contacts() > 0) { touching++; if (r.join().maxStep() >= 2) stepped++; }
			if (r.join().exitTilePlanned() && r.join().gap() == 0 && r.join().exitStep() > 2) { atExit++; System.out.println("EXIT STEP " + r); }
		}
		int wetLow = 0;
		for (Row r : corpus()) wetLow += r.join().wetLow();
		assertEquals(0, wetLow, "no drawn approach column on water outside a deck above it (ADR 0004 WR2, WR4)");
		System.out.println("dead-end-port: " + deadEnds());
		System.out.println("far contacts: " + farBad + " columns over 1, worst step " + farStep);
		System.out.println("touching " + touching + " stepped(>=2) " + stepped + " exitStep>1 " + atExit);
		assertTrue(stepped * 3 <= touching, "most approaches that touch a street meet it level: " + stepped + " of " + touching);
		assertEquals(0, farBad, "an approach meets every street it crosses or touches, other than its exit, within 1 (DS-31 joins are level): worst step " + farStep);
		assertEquals(0, atExit, "an approach meets its street at the exit within a ramp step");
	}

	/** 6.15 step 3: every exit the corpus approaches leave from sits in a tile the settlement plans (playtest metropolis exit 3264,1856 and world-12345 k2_1_1 were not). */
	@Test
	void exitsSitInPlannedTiles() {
		int n = 0;
		for (Row r : corpus()) {
			n++;
			assertTrue(r.join().exitTilePlanned(), "exit in a tile no one plans: " + r);
		}
		assertTrue(n >= 13, "corpus size " + n); // 16 before 6.22 DEADPORT; k3_7_1 and km11_m3_2 join links whose far end has no approach, now dropped; DECKDIAG/LONGDECK: village s4_1 (pt) lost its lake-shore approach, so its link to s1_2 is dropped and s1_2 draws no k2_1_1 (13 of 14)
		var metropolis = JoinWorld.joins(JoinWorld.playtest(), 1, 2);
		assertTrue(metropolis.stream().noneMatch(j -> j.exit().x() == 3264 && j.exit().z() == 1856), "the unplanned metropolis exit moved");
	}
}
