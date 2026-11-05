package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CellSkeleton;
import dev.fractalbuildings.core.settlement.Gate;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.Road;
import dev.fractalbuildings.core.settlement.SettlementSite;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * 6.22 DEADPORT on the user's playtest world of 2026-10-28 (seed 1573336981619055086, fixtures {@code pt1573-metro-s8} over {@code pt1573-world-s64}):
 * the metropolis s4_m2 round his "roads to nowhere" shots (20.02.18 at 3542,-2186; 20.42.25 at 3881,-1241; 20.43.12 at 3963,-1185; 20.25.33
 * at 3386,-1316). Those four are not dead-end ports: no intercity road or approach column lies within two chunks of any of them; they are the
 * metropolis's own main roads (r3_m3_3, an exit road over a bridge to a hashed exit gate with no link beyond; r3_m2_2, a main road ending
 * at a cell gate 3904,-1184 that the neighbour cell's skeleton gives no road). The metropolis's one dead-end port is 5312,-832, toward
 * hamlet s5_m1: no exit gate of the metropolis lies within its radius of that port, and the hamlet has no other link, so it is kept (case c).
 * {@link #spots} prints the details (opt-in, {@code -Dfb.deadport=1}).
 */
class DeadPortPlaytestTest {
	static final int[][] SPOTS = {{3542, -2186}, {3881, -1241}, {3963, -1185}, {3386, -1316}};

	@Test
	void playtestMetropolis() {
		WorldPlanner w = JoinWorld.pt1573();
		RoadNet net = w.roads();
		SettlementSite site = w.siteOf(new Cell(4, -2)).orElseThrow();
		RoadNet.Settlement s = net.settlement(new CellKey(4, -2)).orElseThrow();
		java.util.TreeMap<String, String> states = new java.util.TreeMap<>();
		for (var p : net.portsOf(s)) states.put(p.other().cx() + "," + p.other().cz(), String.valueOf(w.linkState(DeadPortTest.edge(s.owner(), p.other()))));
		org.junit.jupiter.api.Assertions.assertEquals("{2,-2=LIVE, 5,-1=DEAD_END, 5,-3=LIVE}", states.toString());
		org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("5312,-832"), w.deadEndPorts(site).stream().map(p -> p.x() + "," + p.z()).toList());
		for (int[] q : SPOTS) {
			int n = 0;
			for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) n += w.roadColumns((q[0] >> 4) + dx, (q[1] >> 4) + dz).size();
			org.junit.jupiter.api.Assertions.assertEquals(0, n, "intercity or approach columns near " + q[0] + "," + q[1]);
		}
	}

	@Test
	void spots() {
		Assumptions.assumeTrue(System.getProperty("fb.deadport") != null, "opt-in");
		WorldPlanner w = JoinWorld.pt1573();
		RoadNet net = w.roads();
		SettlementSite site = w.siteOf(new Cell(4, -2)).orElseThrow();
		RoadNet.Settlement s = net.settlement(new CellKey(4, -2)).orElseThrow();
		System.out.println("site " + site.id() + " " + site.tier() + " r " + site.radius() + " cells " + site.cells());
		for (var p : net.portsOf(s)) System.out.println("port " + p);
		for (var an : w.approachNodes(site)) System.out.println("nodes " + an.road().id() + " port " + an.port() + " n " + an.nodes().size() + " first " + an.nodes().get(0)[0] + "," + an.nodes().get(0)[1]);
		for (var a : w.approachRoads(site, s)) System.out.println("drawn " + a.road().id() + " port " + a.port() + " cols " + a.columns().size() + " wetFail " + a.wetFailures());
		System.out.println("dead " + w.deadEndPorts(site));
		System.out.println("dropped " + w.droppedPorts(site));
		for (var p : net.portsOf(s)) System.out.println("state " + p.other() + " " + w.linkState(p.other().compareTo(s.owner()) < 0 ? new RoadNet.EdgeKey(p.other(), s.owner()) : new RoadNet.EdgeKey(s.owner(), p.other())));
		var src = w.source(site);
		java.util.List<Road> all = new java.util.ArrayList<>();
		java.util.List<Gate> gates = new java.util.ArrayList<>();
		for (Cell c : site.cells()) { CellSkeleton sk = src.skeleton(c); all.addAll(sk.roads()); gates.addAll(sk.gates()); for (Road r : sk.approaches()) System.out.println("skel approach " + r.id() + " " + r.points()); }
		for (int[] q : SPOTS) {
			Road best = null; double bd = 1e18;
			for (Road r : all) for (int i = 0; i + 1 < r.points().size(); i++) { double d = seg(q, r.points().get(i), r.points().get(i + 1)); if (d < bd) { bd = d; best = r; } }
			final Road b = best;
			Pt e0 = b.points().get(0), e1 = b.points().get(b.points().size() - 1);
			String g0 = "", g1 = "";
			for (Gate g : gates) { if (g.x() == e0.x() && g.z() == e0.z()) g0 += g.kind() + "(" + g.road() + ") "; if (g.x() == e1.x() && g.z() == e1.z()) g1 += g.kind() + "(" + g.road() + ") "; }
			int t0 = 0, t1 = 0;
			for (Road r : all) if (r != b) for (Pt p : r.points()) { if (Math.abs(p.x() - e0.x()) + Math.abs(p.z() - e0.z()) <= 16) t0++; if (Math.abs(p.x() - e1.x()) + Math.abs(p.z() - e1.z()) <= 16) t1++; }
			System.out.printf("SPOT %d,%d nearest %s %s w%d at %.0f; start %s gate[%s] roads %d; end %s gate[%s] roads %d; bridges %d%n", q[0], q[1], b.id(), b.kind(), b.width(), bd, e0, g0, t0, e1, g1, t1, b.bridges().size());
			for (int[] br : b.bridges()) System.out.println("  bridge " + java.util.Arrays.toString(br) + " of " + b.points().size() + " pts " + b.points());
		}
		for (int[] q : SPOTS) {
			int n = 0;
			for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) n += w.roadColumns((q[0] >> 4) + dx, (q[1] >> 4) + dz).size();
			var id = w.grid().tileOf(q[0], q[1]);
			System.out.println("spot " + q[0] + "," + q[1] + " road cols in 5x5 chunks " + n + " tile " + id + " planned " + w.tile(id.ix(), id.iz()).isPresent());
		}
	}

	static double seg(int[] q, Pt a, Pt b) {
		double dx = b.x() - a.x(), dz = b.z() - a.z(), l2 = dx * dx + dz * dz;
		double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((q[0] - a.x()) * dx + (q[1] - a.z()) * dz) / l2));
		return Math.hypot(q[0] - (a.x() + t * dx), q[1] - (a.z() + t * dz));
	}
}
