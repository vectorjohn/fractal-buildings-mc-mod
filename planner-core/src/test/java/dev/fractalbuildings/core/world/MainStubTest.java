package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.ExitTrim;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Street;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * 6.25 MAINSTUB: main roads that end at a gate with nothing beyond it. The user's playtest 2026-10-28 (metropolis s4_m2 of seed
 * 1573336981619055086: 20.02.18, 20.42.25, 20.43.12) and the two exit roads of the 6.22 review whose link the road layer drops (w12345 s2_1
 * g4 at 2560,1984 and s6_6 g13 at 7040,6080) run past their last lot to a gate in open ground; with the cut none runs more than
 * {@link ExitTrim#MAX_STUB} columns past its last lot or street.
 */
class MainStubTest {
	/** The stub of every gateless exit road of the site, with the cuts ({@code cut}) or as planned. */
	static List<ExitTrim.Stub> stubs(WorldPlanner w, SettlementSite site, boolean cut) {
		ExitTrim.Plans plans = (ix, iz) -> w.tile(ix, iz).filter(t -> t.site().id().equals(site.id())).isPresent() ? w.rawPlan(ix, iz).plan() : null;
		return ExitTrim.stubs(w.source(site), plans, cut ? w.exitCuts(site) : Map.of());
	}

	static ExitTrim.Stub stub(List<ExitTrim.Stub> l, String road) {
		return l.stream().filter(s -> s.road().equals(road)).findFirst().orElseThrow(() -> new AssertionError("no stub for road " + road + " in " + l));
	}

	@Test
	void playtestMetropolisCutsItsTwoStubs() {
		WorldPlanner w = JoinWorld.pt1573();
		SettlementSite site = w.siteOf(new Cell(4, -2)).orElseThrow();
		List<ExitTrim.Stub> before = stubs(w, site, false), after = stubs(w, site, true);
		System.out.println("pt1573 s4_m2 before " + before + "\n after " + after);
		// 20.42.25 and 20.43.12: r3_m2_2, to a cell gate the neighbour gives no road (71 columns past its last built lot); r3_m2_4 and r4_m2_5 are
		// hashed exits with nothing beyond. 20.02.18's r3_m3_3 (a bridge to a hashed exit gate) ends beside four built lots (a shop, a workshop, two houses): not a stub.
		assertEquals(0, stub(before, "r3_m3_3").columns());
		for (String road : List.of("r3_m2_2", "r3_m2_4", "r4_m2_5")) {
			assertTrue(stub(before, road).columns() > ExitTrim.MAX_STUB, road + " as planned: " + stub(before, road));
			assertTrue(stub(after, road).columns() <= ExitTrim.MAX_STUB, road + " cut: " + stub(after, road));
			assertTrue(stub(after, road).cut(), road);
		}
		assertEquals(List.of(), SettlementChecks.exitStubs(after));
		assertFalse(SettlementChecks.exitStubs(before).isEmpty());
	}

	@Test
	void droppedLinkExitsAreCut() {
		WorldPlanner w = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		for (int[] c : new int[][] {{2, 1}, {6, 6}}) {
			SettlementSite site = w.siteOf(new Cell(c[0], c[1])).orElseThrow();
			List<ExitTrim.Stub> before = stubs(w, site, false), after = stubs(w, site, true);
			System.out.println("w12345 " + site.id() + " before " + before + "\n after " + after);
			assertTrue(before.stream().anyMatch(s -> s.columns() > ExitTrim.MAX_STUB), site.id() + " has a stub as planned: " + before);
			assertEquals(List.of(), SettlementChecks.exitStubs(after), site.id());
		}
	}

	/** Built lots that have street frontage in the raw plan and none after the cut (CityPlanner.plan, as the world draws it); ids with the district. */
	static List<String> lostFrontage(WorldPlanner w, SettlementSite site) {
		var p = w.probes();
		var src = w.source(site);
		var city = dev.fractalbuildings.core.settlement.CityPlanner.plan(src, p.coarse(), p.exact(), p.biome(), dev.fractalbuildings.core.gen.v1.GeneratorV1.GEN_VERSION);
		var styles = new dev.fractalbuildings.core.style.StyleField(p.biome(), w.seed());
		List<String> bad = new ArrayList<>();
		for (var e : city.districts().entrySet()) {
			var cutPlan = e.getValue();
			var raw = dev.fractalbuildings.core.settlement.CityPlanner.planTile(src, cutPlan.district().ix(), cutPlan.district().iz(), styles, p.exact()).orElseThrow().plan();
			java.util.Set<String> before = new java.util.TreeSet<>();
			for (var v : SettlementChecks.lotFrontage(raw)) before.add(v.message().split(" ")[1]);
			for (var v : SettlementChecks.lotFrontage(cutPlan)) {
				String id = v.message().split(" ")[1];
				boolean built = cutPlan.lots().stream().anyMatch(l -> l.id().equals(id) && l.builds());
				if (built && !before.contains(id)) bad.add(e.getKey() + "." + id);
			}
		}
		return bad;
	}

	/** The cut leaves no built house without a street: every built lot of a cut plan that had frontage still has it. */
	@Test
	void noBuiltLotLosesItsStreetToTheCut() {
		List<String> bad = new ArrayList<>();
		WorldPlanner w12345 = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		bad.addAll(lostFrontage(w12345, w12345.siteOf(new Cell(-4, 6)).orElseThrow())); // sm4_6, tile dm22_41: the lane that hung on rm4_6_2
		bad.addAll(lostFrontage(w12345, w12345.siteOf(new Cell(2, 1)).orElseThrow()));
		bad.addAll(lostFrontage(w12345, w12345.siteOf(new Cell(6, 6)).orElseThrow()));
		WorldPlanner pt = JoinWorld.pt1573();
		bad.addAll(lostFrontage(pt, pt.siteOf(new Cell(4, -2)).orElseThrow()));
		WorldPlanner w987 = new WorldPlanner(987654321L, WorldConfig.defaults(), JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, "world-987654321-s64")), null, WorldPlanner.Caches.DEFAULT);
		for (int[] c : new int[][] {{2, -2}, {4, 0}, {3, -8}, {-8, -5}}) w987.siteOf(new Cell(c[0], c[1])).ifPresent(sx -> bad.addAll(lostFrontage(w987, sx)));
		for (GateWorld g : GateWorld.all()) bad.addAll(lostFrontage(g.sharedPlanner(), g.site()));
		assertEquals(List.of(), bad);
	}

	/** The architect's leftover: r3_m2_4 (gate g2 at 3520,-1888, no link) must not cross its 70-block bridge (3712,-1728 to 3648,-1760) to a lane of six lots. */
	@Test
	void theBridgeOfR3m2_4IsGone() {
		WorldPlanner w = JoinWorld.pt1573();
		SettlementSite site = w.siteOf(new Cell(4, -2)).orElseThrow();
		ExitTrim.Cut cut = w.exitCuts(site).get("r3_m2_4");
		assertTrue(cut != null, "r3_m2_4 is cut");
		int[] end = cut.cols().get(cut.index());
		int bridgeStart = -1;
		for (int i = 0; i < cut.cols().size(); i++) { int[] c = cut.cols().get(i); if (c[0] == 3712 && c[1] == -1728) bridgeStart = i; }
		assertTrue(bridgeStart > 0 && cut.index() <= bridgeStart, "ends at the near bank, not past (3712,-1728): " + end[0] + "," + end[1] + " col " + cut.index());
	}

	/** The plan the world draws has the cut: no street piece of the road runs past it, and the tile plans do not depend on the order they are asked in. */
	@Test
	void theWorldPlanIsCutAndTheSameInAnyOrder() {
		WorldPlanner a = JoinWorld.pt1573();
		SettlementSite site = a.siteOf(new Cell(4, -2)).orElseThrow();
		ExitTrim.Cut cut = a.exitCuts(site).get("r3_m2_2");
		assertTrue(cut != null && cut.index() < cut.cols().size() - 40, "r3_m2_2 is cut well short of its gate: " + (cut == null ? null : cut.index() + " of " + cut.cols().size()));
		List<int[]> tiles = new ArrayList<>();
		for (int[] col : cut.cols()) {
			var t = a.grid().tileOf(col[0], col[1]);
			if (tiles.stream().noneMatch(q -> q[0] == t.ix() && q[1] == t.iz())) tiles.add(new int[] {t.ix(), t.iz()});
		}
		java.util.TreeMap<String, String> forward = new java.util.TreeMap<>();
		int beyond = 0;
		for (int[] t : tiles) {
			if (a.tile(t[0], t[1]).isEmpty()) continue;
			for (Street s : a.plan(t[0], t[1]).plan().streets()) {
				if (!s.id().startsWith("r3_m2_2")) continue;
				for (var p : s.points()) for (int i = cut.index() + 1; i < cut.cols().size(); i++) if (cut.cols().get(i)[0] == p.x() && cut.cols().get(i)[1] == p.z()) beyond++;
			}
			forward.put(t[0] + "," + t[1], dev.fractalbuildings.core.settlement.DistrictJson.write(a.plan(t[0], t[1]).plan()));
		}
		assertEquals(0, beyond, "street points of r3_m2_2 past its cut");
		// reverse order, a fresh planner with its caches at 2
		WorldPlanner b = new WorldPlanner(JoinWorld.PT1573_SEED, WorldConfig.defaults(), JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, "pt1573-metro-s8", "pt1573-world-s64")), null, new WorldPlanner.Caches(2, 2, 2, 2, 2, 2, 2));
		List<int[]> rev = new ArrayList<>(tiles);
		java.util.Collections.reverse(rev);
		for (int[] t : rev) {
			if (b.tile(t[0], t[1]).isEmpty()) continue;
			assertEquals(forward.get(t[0] + "," + t[1]), dev.fractalbuildings.core.settlement.DistrictJson.write(b.plan(t[0], t[1]).plan()), "tile " + t[0] + "," + t[1]);
		}
	}

	/** The user's 20.02.18: r3_m3_3 bridges to four lots at a hashed exit gate with no road beyond; the road stops at the near bank and those lots are not built. */
	@Test
	void theBridgeToNowhereIsGone() {
		WorldPlanner w = JoinWorld.pt1573();
		SettlementSite site = w.siteOf(new Cell(4, -2)).orElseThrow();
		ExitTrim.Cut cut = w.exitCuts(site).get("r3_m3_3");
		assertTrue(cut != null, "r3_m3_3 is cut");
		int[] near = cut.cols().get(cut.index());
		// the bridge starts at point 3 (3552,-2016): nothing of the road on or beyond it
		assertTrue(cut.index() < 330 && Math.abs(near[0] - 3552) + Math.abs(near[1] + 2016) <= 12, "ends at the near bank: " + near[0] + "," + near[1] + " col " + cut.index());
		int beyond = 0, built = 0;
		for (int[] col : cut.cols().subList(cut.index() + 1, cut.cols().size())) {
			var t = w.grid().tileOf(col[0], col[1]);
			if (w.tile(t.ix(), t.iz()).isEmpty()) continue;
			for (Street s : w.plan(t.ix(), t.iz()).plan().streets()) if (s.id().startsWith("r3_m3_3")) for (var p : s.points()) if (p.x() == col[0] && p.z() == col[1]) beyond++;
		}
		assertEquals(0, beyond, "street points of r3_m3_3 beyond the near bank");
		var tid = w.grid().tileOf(3488, -1920);
		for (var l : w.plan(tid.ix(), tid.iz()).plan().lots()) if (l.builds() && l.x() > 3455 && l.x() < 3500 && l.z() > -1960 && l.z() < -1920) built++;
		assertEquals(0, built, "no built lot is left at the far gate");
	}

	/** The lazy per-road path of plan() gives exactly what the whole-settlement path (CityPlanner.plan, the CLI and viewer) gives, on every tile of every settlement of pt1573 that has a cut (including the core tile, which is never cut). */
	@Test
	void theLazyCutEqualsTheWholeSettlementCut() {
		WorldPlanner w = JoinWorld.pt1573();
		java.util.TreeSet<String> sites = new java.util.TreeSet<>();
		int checked = 0, cutSites = 0;
		for (int cz = -12; cz <= 11; cz++) for (int cx = -12; cx <= 11; cx++) {
			SettlementSite site;
			java.util.TreeMap<String, ExitTrim.Cut> all;
			try {
				var so = w.siteOf(new Cell(cx, cz));
				if (so.isEmpty() || w.roads().settlement(new CellKey(cx, cz)).isEmpty() || !sites.add((site = so.get()).id())) continue;
				all = w.exitCuts(site);
			} catch (RuntimeException e) {
				continue; // a cell at the edge of the recorded grid
			}
			if (all.isEmpty()) continue;
			cutSites++;
			var p = w.probes();
			var city = dev.fractalbuildings.core.settlement.CityPlanner.plan(w.source(site), p.coarse(), p.exact(), p.biome(), dev.fractalbuildings.core.gen.v1.GeneratorV1.GEN_VERSION);
			for (var e : city.districts().entrySet()) {
				int ix = e.getValue().district().ix(), iz = e.getValue().district().iz();
				if (w.tile(ix, iz).isEmpty()) continue;
				assertEquals(dev.fractalbuildings.core.settlement.DistrictJson.write(e.getValue()), dev.fractalbuildings.core.settlement.DistrictJson.write(w.plan(ix, iz).plan()), site.id() + " tile " + ix + "," + iz);
				checked++;
			}
		}
		assertTrue(cutSites >= 3 && checked >= 50, "cut settlements " + cutSites + ", tiles checked " + checked);
	}

	@Test
	void sweep() {
		Assumptions.assumeTrue(System.getProperty("fb.mainstub") != null, "opt-in");
		sweep("w12345", WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT));
		sweep("w987654321", new WorldPlanner(987654321L, WorldConfig.defaults(), JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, "world-987654321-s64")), null, WorldPlanner.Caches.DEFAULT));
		sweep("pt1573", JoinWorld.pt1573());
	}

	static void sweep(String label, WorldPlanner w) {
		int sites = 0, stubs = 0, over = 0, overAfter = 0, cut = 0, unserved = 0;
		List<String> seen = new ArrayList<>();
		for (int cz = -12; cz <= 11; cz++) for (int cx = -12; cx <= 11; cx++) {
			try {
				var so = w.roads().settlement(new CellKey(cx, cz));
				var site = w.siteOf(new Cell(cx, cz));
				if (so.isEmpty() || site.isEmpty() || seen.contains(site.get().id())) continue;
				seen.add(site.get().id());
				sites++;
				List<ExitTrim.Stub> before = stubs(w, site.get(), false), after = stubs(w, site.get(), true);
				stubs += before.size();
				for (int i = 0; i < before.size(); i++) {
					if (before.get(i).unserved()) unserved++;
					if (before.get(i).columns() > ExitTrim.MAX_STUB) { over++; System.out.println(label + " " + site.get().id() + " " + site.get().tier() + " BEFORE " + before.get(i)); }
					if (after.get(i).columns() > ExitTrim.MAX_STUB) { overAfter++; System.out.println(label + " " + site.get().id() + " " + site.get().tier() + " AFTER " + after.get(i)); }
					if (after.get(i).cut()) cut++;
				}
			} catch (RuntimeException e) {
				// a cell at the edge of the recorded grid
			}
		}
		System.out.printf("%s SUMMARY sites %d gateless exit roads %d over %d columns: %d as planned, %d cut, %d after; %d unserved (kept whole)%n", label, sites, stubs, ExitTrim.MAX_STUB, over, cut, overAfter, unserved);
	}
}
