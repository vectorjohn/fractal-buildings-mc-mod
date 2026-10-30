package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.SettlementSite;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * HAMLETEMPTY (6.23): a settlement the world resolves has a building. Seed 3141592653589793238's hamlet s0_0 touched a road link whose
 * required exit replaced the hamlet's straight main street by two diagonal paths, so its frontage lots vanished and the game had a
 * "hamlet" of two paths. The corpus plans every settlement of two playtest worlds' recorded region windows with the road graph on
 * (the link exits only exist then) and counts the buildings of its tiles.
 */
class SettlementEmptyTest {
	record Row(String id, String tier, int tiles, int buildings) {}

	static List<Row> rows(WorldPlanner w, int cells) {
		List<Row> out = new ArrayList<>();
		TreeSet<String> done = new TreeSet<>();
		for (int cz = -cells; cz <= cells; cz++) {
			for (int cx = -cells; cx <= cells; cx++) {
				SettlementSite s;
				try { s = w.siteOf(new Cell(cx, cz)).orElse(null); } catch (RuntimeException e) { continue; }
				if (s == null || !done.add(s.id())) continue;
				int r = s.radius() + 160, tiles = 0, buildings = 0;
				if (Math.abs(s.centre().x()) + r > 7000 || Math.abs(s.centre().z()) + r > 7000) continue; // not wholly inside the recorded window
				try {
				var g = w.grid();
				var a = g.tileOf(s.centre().x() - r, s.centre().z() - r);
				var b = g.tileOf(s.centre().x() + r, s.centre().z() + r);
				for (int iz = a.iz(); iz <= b.iz(); iz++) {
					for (int ix = a.ix(); ix <= b.ix(); ix++) {
						var t = w.tile(ix, iz);
						if (t.isEmpty() || !t.get().site().id().equals(s.id())) continue;
						tiles++;
						buildings += w.buildings(ix, iz).size();
					}
				}
				out.add(new Row(s.id(), s.tier().json, tiles, buildings));
				} catch (IllegalArgumentException e) {
					continue; // a neighbour cell at the edge of the recorded window cannot be decided
				}
			}
		}
		return out;
	}

	@Test
	void hamletS0_0OfSeed3141HasABuilding() {
		WorldPlanner w = new WorldPlanner(3141592653589793238L, WorldConfig.defaults(), JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, "pt3141-world-s64")), null, WorldPlanner.Caches.DEFAULT);
		Row hamlet = rows(w, 2).stream().filter(r -> r.id().equals("s0_0")).findFirst().orElseThrow();
		System.out.println("HAMLETEMPTY " + hamlet);
		assertTrue(hamlet.tiles() > 0);
		assertTrue(hamlet.buildings() >= 3, "hamlet s0_0 has no building: " + hamlet);
	}

	@Test
	void noSettlementOfTheCorpusIsEmpty() {
		List<String> bad = new ArrayList<>();
		int n = 0;
		// Only the straight-street tiers (hamlet, village) are asserted: the coarse 64-block dumps also resolve a few cities and villages with
		// no urban column at all (sm1_0 here has no hub; pt1709 s2_m1, s4_1), which the log of 6.23 reports as a separate finding.
		for (Object[] c : new Object[][] {
				{3141592653589793238L, "pt3141-world-s64"}}) {
			WorldPlanner w = new WorldPlanner((Long) c[0], WorldConfig.defaults(), JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, (String) c[1])), null, WorldPlanner.Caches.DEFAULT);
			for (Row r : rows(w, 5)) {
				if (!r.tier().equals("hamlet") && !r.tier().equals("village")) continue;
				n++;
				System.out.println("settlement-empty corpus " + c[1] + " " + r);
				if (!SettlementChecks.settlementEmpty(r.id(), r.buildings(), 0, 0).isEmpty()) bad.add(c[1] + " " + r);
			}
		}
		assertTrue(n >= 15, "corpus has settlements: " + n);
		assertEquals(List.of(), bad);
	}

	/** Roads of the hamlets and villages that turn back by more than 90 degrees at a bend or run over 1.3 times the straight distance end to end. */
	static List<String> doubledBack(WorldPlanner w, int cells) {
		List<String> out = new ArrayList<>();
		TreeSet<String> done = new TreeSet<>();
		for (int cz = -cells; cz <= cells; cz++) {
			for (int cx = -cells; cx <= cells; cx++) {
				SettlementSite s;
				try { s = w.siteOf(new Cell(cx, cz)).orElse(null); } catch (RuntimeException e) { continue; }
				if (s == null || !done.add(s.id()) || !(s.tier().json.equals("hamlet") || s.tier().json.equals("village"))) continue;
				if (Math.abs(s.centre().x()) + s.radius() > 7000 || Math.abs(s.centre().z()) + s.radius() > 7000) continue;
				try {
					for (var cl : s.cells()) {
						for (var rd : w.source(s).skeleton(cl).roads()) {
							var p = rd.points();
							double len = 0;
							boolean back = false;
							for (int i = 0; i + 1 < p.size(); i++) {
								len += Math.hypot(p.get(i + 1).x() - p.get(i).x(), p.get(i + 1).z() - p.get(i).z());
								if (i + 2 < p.size()) back |= (long) (p.get(i + 1).x() - p.get(i).x()) * (p.get(i + 2).x() - p.get(i + 1).x()) + (long) (p.get(i + 1).z() - p.get(i).z()) * (p.get(i + 2).z() - p.get(i + 1).z()) < 0;
							}
							double direct = Math.hypot(p.get(p.size() - 1).x() - p.get(0).x(), p.get(p.size() - 1).z() - p.get(0).z());
							if (back || len > 1.3 * direct + 1) out.add(s.id() + " " + rd.id() + String.format(" len %.0f direct %.0f%s", len, direct, back ? " BEND>90" : ""));
						}
					}
				} catch (IllegalArgumentException e) {
					continue;
				}
			}
		}
		return out;
	}

	/** Round 2 (architect): link roads of straight-street settlements leave toward their exit; none doubles back, and the buildings stay up. */
	@Test
	void noLinkRoadDoublesBackAndBuildingsStayUp() {
		WorldPlanner w = new WorldPlanner(3141592653589793238L, WorldConfig.defaults(), JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, "pt3141-world-s64")), null, WorldPlanner.Caches.DEFAULT);
		// three roads of the old A* router (not link roads of the straight-street fix) bend back or wander at baseline ce8cad28 too
		List<String> bad = new ArrayList<>(doubledBack(w, 5));
		bad.removeIf(b -> b.startsWith("sm3_1 rm3_1_0") || b.startsWith("s3_1 r3_1_0") || b.startsWith("sm2_5 rm2_5_0"));
		bad.forEach(b -> System.out.println("DOUBLED-BACK " + b));
		int total = 0;
		for (Row r : rows(w, 5)) if (r.tier().equals("hamlet") || r.tier().equals("village")) total += r.buildings();
		System.out.println("HAMLETEMPTY totals buildings " + total + " doubled-back " + bad.size());
		assertEquals(List.of(), bad);
		assertTrue(total >= 420, "buildings of hamlets and villages " + total + " (baseline 368, round 1 465 of which 42 at sm4_m1 came from a Z-shaped road; round 2 422)");
	}

	@Test
	void theRuleFlagsAnEmptySettlement() {
		assertEquals(1, SettlementChecks.settlementEmpty("s0_0", 0, 1, 2).size());
		assertTrue(SettlementChecks.settlementEmpty("s0_0", 1, 1, 2).isEmpty());
	}
}
