package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.road.Curve;
import dev.fractalbuildings.core.road.Profile;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.SettlementSite;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * DECKDIAG step 4 census (opt-in {@code -Dfb.census=1}, prints to stdout; run it with {@code -i}): on fine terrain only (the gate windows, the pt1709
 * step-2/4 crops, the pt1573 step-8 crop), per tier: skeleton approaches, kept, dropped, dead-end ports, kept decks over 20 and along runs.
 * Only settlements whose centre lies inside a fine crop count, and for the crop worlds only those; deck and dead-end numbers are exact there.
 */
class DeckCensusProbe {
	static final TreeMap<String, int[]> TOTAL = new TreeMap<>(); // tier -> {skeleton, kept, dropped, deadEnd, deck>20, along, ports}

	@Test
	void census() {
		Assumptions.assumeTrue(System.getProperty("fb.census") != null, "opt-in");
		for (GateWorld g : GateWorld.all()) one("gate:" + g.name(), g.planner(WorldPlanner.Caches.DEFAULT, null), List.of(g.site()), List.of(g.dump()));
		var fine = JoinWorld.read(GateWorld.FIXTURES, "pt1709-lake-sm11_m2-s4", "pt1709-neck-sm4_m6-s4", "pt1709-bay-s8_10-s4", "pt1709-join-s2");
		WorldPlanner w = JoinWorld.deckdiag();
		var ss = sitesIn(w, fine);
		for (int[] c : new int[][] {{1, 2}, {-11, -2}, {-4, -6}, {8, 10}}) w.siteOf(new Cell(c[0], c[1])).ifPresent(s -> { if (ss.stream().noneMatch(q -> q.id().equals(s.id()))) ss.add(s); }); // the playtest long-deck places
		one("pt1709", w, ss, fine);
		var f2 = JoinWorld.read(GateWorld.FIXTURES, "pt1573-metro-s8");
		WorldPlanner p = JoinWorld.pt1573();
		one("pt1573", p, sitesIn(p, f2), f2);
		for (var e : TOTAL.entrySet()) {
			int[] t = e.getValue();
			System.out.printf("CENSUS-TOTAL %-10s skeleton %3d kept %3d dropped %3d | ports %3d deadEnd %3d | kept decks>20 %3d along %d%n", e.getKey(), t[0], t[1], t[2], t[6], t[3], t[4], t[5]);
		}
	}

	static boolean in(List<RecordedProbe> fine, int x, int z) {
		for (RecordedProbe r : fine) if (x >= r.x0() && z >= r.z0() && x <= r.x0() + (r.nx() - 1) * r.step() && z <= r.z0() + (r.nz() - 1) * r.step()) return true;
		return false;
	}

	static List<SettlementSite> sitesIn(WorldPlanner w, List<RecordedProbe> fine) {
		List<SettlementSite> out = new ArrayList<>();
		java.util.TreeSet<String> seen = new java.util.TreeSet<>();
		for (RecordedProbe r : fine) {
			int cx = r.x0() + (r.nx() - 1) * r.step() / 2, cz = r.z0() + (r.nz() - 1) * r.step() / 2;
			for (var s : w.roads().settlementsNear(cx, cz, (r.nx() * r.step()) / 2 + 256)) {
				if (!in(fine, s.x(), s.z())) continue;
				var site = w.siteOf(new Cell(s.owner().cx(), s.owner().cz()));
				if (site.isPresent() && seen.add(site.get().id())) out.add(site.get());
			}
		}
		return out;
	}

	static void one(String label, WorldPlanner w, List<SettlementSite> sites, List<RecordedProbe> fine) {
		for (SettlementSite site : sites) {
			try {
				var owner = w.roads() == null ? java.util.Optional.<dev.fractalbuildings.core.road.RoadNet.Settlement>empty() : w.roads().settlement(new dev.fractalbuildings.core.region.CellKey(site.claimant().cx(), site.claimant().cz()));
				int skeleton = 0;
				for (Cell c : site.cells()) skeleton += w.source(site).skeleton(c).approaches().size();
				var kept = w.approachNodes(site);
				int deck = 0, along = 0;
				for (var an : kept) {
					List<int[]> nodes = new ArrayList<>(an.nodes());
					var pr = Profile.approach(Curve.stations(nodes), w.probes().roadExact(), new TreeMap<>(), an.road().width(), true);
					for (var c : pr.crossings()) if (c.length() > 20) deck++;
					System.out.println("CENSUS-APPROACH " + site.id() + " " + an.road().id() + " width " + an.road().width() + " crossings " + pr.crossings().stream().map(c -> String.valueOf(c.length())).toList());
					along += pr.alongRuns();
				}
				int dead = owner.isEmpty() ? 0 : w.deadEndPorts(site).size(), ports = owner.isEmpty() ? 0 : w.roads().portsOf(owner.get()).size();
				int[] t = TOTAL.computeIfAbsent(site.tier().name(), k -> new int[7]);
				t[0] += skeleton; t[1] += kept.size(); t[2] += skeleton - kept.size(); t[3] += dead; t[4] += deck; t[5] += along; t[6] += ports;
				if (true)
					System.out.printf("CENSUS %s %s %s skeleton %d kept %d dead %d decks>20 %d along %d%n", label, site.id(), site.tier(), skeleton, kept.size(), dead, deck, along);
			} catch (RuntimeException e) {
				System.out.println("CENSUS " + label + " " + site.id() + " ERROR " + e);
			}
		}
	}
}
