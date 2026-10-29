package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.style.StyleField;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The whole city pipeline (D0): skeleton, districts, briefs, scenes and the whole-city rules. */
class CityPlannerTest {
	private static final String TERRAIN = "noise:3:70:10:300";

	record Setup(long seed, ProbeSpecs.Probes p, SettlementSite site, CoarseProbe coarse) {
		SkeletonSource source() {
			return new SkeletonSource.Direct(seed, new FixedSiteView(site, new RegionField(seed, SkeletonFixtures.CELL)), site, coarse);
		}

		CityPlanner.City plan() {
			return CityPlanner.plan(source(), coarse, p.terrain(), p.biome(), GeneratorV1.GEN_VERSION);
		}
	}

	static Setup setup(long seed, String terrain, int cx, int cz, Tier tier, int radius) {
		RegionField f = new RegionField(seed, SkeletonFixtures.CELL);
		ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
		return new Setup(seed, p, SkeletonFixtures.site(f, cx, cz, tier, radius, 4), CoarseProbe.of(p.terrain(), p.biome(), 32));
	}

	private static String bytes(CityPlanner.City c) {
		StringBuilder sb = new StringBuilder(SettlementJson.write(c.plan()));
		for (var e : c.districts().entrySet()) sb.append(e.getKey()).append(DistrictJson.write(e.getValue()));
		return sb.toString();
	}

	@Test
	void theSamePlanIsByteIdenticalAndEachDistrictIsIndependentOfTheOthers() {
		Setup s = setup(11, TERRAIN, 1, 1, Tier.CITY, 600);
		CityPlanner.City a = s.plan(), b = s.plan();
		assertEquals(bytes(a), bytes(b), "two runs");
		assertTrue(a.districts().size() > 20, a.districts().size() + " districts");
		// any order, any fresh source: plan the districts reversed, each from a source of its own, and compare
		StyleField styles = new StyleField(s.p().biome(), s.seed());
		List<DistrictSummary> sums = new ArrayList<>(a.plan().districts());
		java.util.Collections.reverse(sums);
		int checked = 0;
		for (DistrictSummary d : sums) {
			if (!a.districts().containsKey(d.id())) continue;
			SkeletonSource fresh = s.source();
			DistrictPlan alone = CityPlanner.planTileTrimmed(fresh, d.ix(), d.iz(), styles, s.p().terrain()).orElseThrow().plan();
			assertEquals(DistrictJson.write(a.districts().get(d.id())), DistrictJson.write(alone), d.id() + " planned alone");
			if (++checked >= 12) break;
		}
		assertTrue(checked >= 8);
	}

	@Test
	void settlementListsPlannedDistrictsWithTheirLots() {
		CityPlanner.City c = setup(11, TERRAIN, 1, 1, Tier.CITY, 600).plan();
		int listed = 0;
		for (DistrictSummary d : c.plan().districts()) {
			DistrictPlan p = c.districts().get(d.id());
			if (d.detail() == null) { assertTrue(p == null, d.id()); continue; }
			assertEquals(p.lots().size(), d.lots(), d.id());
			assertEquals("districts/" + d.id() + ".json", d.detail());
			listed++;
		}
		assertEquals(c.districts().size(), listed);
		assertTrue(c.plan().violations().stream().noneMatch(v -> v.invariant().equals("tile-border-agree")));
	}

	private static final Set<String> HARD = Set.of("tile-border-agree", "lot-in-tile", "lots-disjoint", "lot-dry", "lot-grade", "skeleton-in-cell", "cell-gate-agree");

	/** The rules that hold for every site on synthetic terrain, over many sites of every tier. roads-connected is reported by city-check, not asserted. */
	@Test
	void hardRulesHoldOverSyntheticSites() {
		Tier[] tiers = Tier.values();
		int sites = 0, districts = 0;
		TreeMap<String, Integer> other = new TreeMap<>();
		for (int i = 0; i < 24; i++) {
			Tier t = tiers[i % tiers.length];
			int radius = Math.min(t.maxRadius, t.minRadius + 60 + 40 * (i % 3));
			if (t == Tier.METROPOLIS) radius = 1000;
			Setup s = setup(100 + i, i % 2 == 0 ? TERRAIN : "noise:5:75:25:200+river:-120:24:6", i % 3, -(i % 2), t, radius);
			CityPlanner.City c = s.plan();
			sites++;
			districts += c.districts().size();
			for (Violation v : CityPlanner.check(c)) {
				if (HARD.contains(v.invariant())) assertEquals("", v.invariant() + " " + v.message(), "site " + i + " " + t.json);
				else other.merge(v.invariant(), 1, Integer::sum);
			}
		}
		System.out.println("city sites: " + sites + ", " + districts + " districts, other violations " + other);
		assertTrue(districts > 100);
		// lots only front streets whose piece holds a skeleton road (Districts.markLotEligible): without that rule this was 80
		assertTrue(other.getOrDefault("roads-connected", 0) <= 15, "roads-connected " + other);
	}

	@Test
	void shiftingOneDistrictsStreetsBreaksTheTileBorders() {
		CityPlanner.City c = setup(11, TERRAIN, 1, 1, Tier.CITY, 600).plan();
		assertEquals(List.of(), SettlementChecks.cityTileBorders(c.districts().values()), "control");
		TreeMap<String, DistrictPlan> mutated = new TreeMap<>(c.districts());
		String victim = null;
		int best = 0;
		for (var e : c.districts().entrySet()) if (e.getValue().streets().size() > best) { best = e.getValue().streets().size(); victim = e.getKey(); }
		DistrictPlan p = mutated.get(victim);
		List<Street> shifted = new ArrayList<>();
		for (Street st : p.streets()) {
			List<Pt> pts = new ArrayList<>();
			for (Pt q : st.points()) pts.add(new Pt(q.x() + 4, q.z() + 4));
			shifted.add(new Street(st.id(), st.kind(), st.width(), pts, st.y()));
		}
		mutated.put(victim, new DistrictPlan(p.genVersion(), p.seed(), p.district(), shifted, p.plazas(), p.lots(), p.scene(), p.violations()));
		List<Violation> v = SettlementChecks.cityTileBorders(mutated.values());
		assertTrue(!v.isEmpty() && v.stream().allMatch(x -> x.invariant().equals("tile-border-agree")), "mutant " + victim + " must fail: " + v.size());
	}

	@Test
	void droppingAMainRoadsStreetsDisconnectsTheCity() {
		// the city rule has teeth on real plans: remove every skeleton road piece of one district and a lot behind it loses the hub
		CityPlanner.City c = setup(11, TERRAIN, 1, 1, Tier.CITY, 600).plan();
		int control = SettlementChecks.cityRoadsConnected(c.districts().values(), c.hub()).size();
		int worst = control;
		for (var e : c.districts().entrySet()) {
			DistrictPlan p = e.getValue();
			List<Street> keep = new ArrayList<>();
			for (Street st : p.streets()) if (st.kind() != RoadKind.MAIN && st.kind() != RoadKind.ARTERIAL) keep.add(st);
			if (keep.size() == p.streets().size()) continue;
			TreeMap<String, DistrictPlan> m = new TreeMap<>(c.districts());
			m.put(e.getKey(), new DistrictPlan(p.genVersion(), p.seed(), p.district(), keep, p.plazas(), p.lots(), p.scene(), p.violations()));
			worst = Math.max(worst, SettlementChecks.cityRoadsConnected(m.values(), c.hub()).size());
		}
		assertTrue(worst > control, "cutting a road must add a violation (control " + control + ", worst " + worst + ")");
	}

	@Test
	void autoScenesAreDistinctDeterministicAndIncludeTheHubTile() {
		CityPlanner.City c = setup(11, TERRAIN, 1, 1, Tier.CITY, 600).plan();
		List<String> a = CityPlanner.autoScenes(c, 4), b = CityPlanner.autoScenes(setup(11, TERRAIN, 1, 1, Tier.CITY, 600).plan(), 4);
		assertEquals(a, b);
		assertEquals(4, new TreeSet<>(a).size());
		DistrictPlan hubTile = null;
		for (DistrictPlan p : c.districts().values()) if (p.district().rect().contains(c.hub().x(), c.hub().z())) hubTile = p;
		assertTrue(hubTile != null && a.contains(hubTile.district().id()), "hub tile " + (hubTile == null ? null : hubTile.district().id()) + " in " + a);
		assertEquals(List.of(), CityPlanner.autoScenes(c, 0));
	}

	@Test
	void chosenScenesBuildWithoutViolations() {
		Setup s = setup(11, TERRAIN, 1, 1, Tier.CITY, 600);
		CityPlanner.City c = s.plan();
		int buildings = 0;
		for (String id : CityPlanner.autoScenes(c, 3)) {
			DistrictScenes.Built b = CityPlanner.scene(c, id, s.p().terrain(), s.p().biome(), true);
			assertEquals(List.of(), b.scene().violations().stream().map(v -> v.invariant() + ": " + v.message()).toList(), id);
			buildings += b.scene().buildings().size();
		}
		assertTrue(buildings > 10, buildings + " buildings");
	}

	private static boolean resiCell(LotBrief l) { return (Math.floorDiv(l.x() + l.w() / 2, 24) + Math.floorDiv(l.z() + l.d() / 2, 24)) % 2 == 0; }

	/** Briefs.fill with a quarter lookup: each lot takes the quarter at its own centre (DL known issue), not its district's. */
	@Test
	void briefsTakeTheQuarterOfTheLotNotTheDistrict() {
		Setup s = setup(11, TERRAIN, 1, 1, Tier.METROPOLIS, 1000);
		SkeletonSource src = s.source();
		StyleField styles = new StyleField(s.p().biome(), s.seed());
		DistrictPlan raw = null;
		for (DistrictSummary d : s.plan().plan().districts()) {
			var det = Districts.plan(src, d.ix(), d.iz(), s.p().terrain());
			if (det.isEmpty() || det.get().plan().lots().size() < 30) continue;
			DistrictPlan r1 = Briefs.fill(det.get().plan(), QuarterKind.RESIDENTIAL, styles, s.p().terrain()), w1 = Briefs.fill(det.get().plan(), QuarterKind.WATERFRONT, styles, s.p().terrain());
			boolean onResi = false, onWater = false; // the two quarters differ on lots of both checkerboard colours, so a wrong lookup shows
			for (int i = 0; i < r1.lots().size(); i++) if (!r1.lots().get(i).equals(w1.lots().get(i))) { if (resiCell(r1.lots().get(i))) onResi = true; else onWater = true; }
			if (onResi && onWater) { raw = det.get().plan(); break; }
		}
		assertTrue(raw != null);
		final DistrictPlan plan = raw;
		// quarter by the lot's position: a checkerboard of residential and waterfront (storeys capped at 4) cells of 24 blocks
		Briefs.QuarterAt split = (x, z, dens) -> (Math.floorDiv(x, 24) + Math.floorDiv(z, 24)) % 2 == 0 ? QuarterKind.RESIDENTIAL : QuarterKind.WATERFRONT;
		DistrictPlan mixed = Briefs.fill(plan, split, styles, s.p().terrain());
		DistrictPlan down = Briefs.fill(plan, QuarterKind.RESIDENTIAL, styles, s.p().terrain());
		DistrictPlan out = Briefs.fill(plan, QuarterKind.WATERFRONT, styles, s.p().terrain());
		int diffDown = 0, diffOut = 0;
		for (int i = 0; i < plan.lots().size(); i++) {
			LotBrief m = mixed.lots().get(i), d = down.lots().get(i), o = out.lots().get(i);
			boolean resi = resiCell(m);
			assertEquals(resi ? d : o, m, "lot " + m.id() + (resi ? " residential" : " waterfront"));
			if (!d.equals(o)) { if (resi) diffDown++; else diffOut++; }
		}
		assertTrue(diffDown + diffOut > 0 && !down.equals(out), "the two quarters give different briefs, so the test can fail");
	}
}
