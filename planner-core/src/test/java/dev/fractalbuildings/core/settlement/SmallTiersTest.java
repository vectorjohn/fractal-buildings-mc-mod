package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Hamlet and village programs (D1) over 200 sites each, on three synthetic terrains. */
class SmallTiersTest {
	static final String[] TERRAINS = {"noise:3:70:10:300", "noise:5:75:25:200+river:-120:24:6", "noise:9:68:6:150"};
	static final int N = 200;

	/** Site {@code i}: seed, terrain, cell and radius all derived from i and the tier. */
	static CityPlannerTest.Setup site(int i, Tier tier) {
		long h = Hash64.hash(0xD1L, tier.json, i);
		int radius = tier.minRadius + Hash64.below(Hash64.hash(h, "r"), tier.maxRadius - tier.minRadius + 1);
		return CityPlannerTest.setup(1000 + i, TERRAINS[i % TERRAINS.length], Hash64.below(Hash64.hash(h, "cx"), 7) - 3, Hash64.below(Hash64.hash(h, "cz"), 7) - 3, tier, radius);
	}

	static long builds(CityPlanner.City c) {
		return c.districts().values().stream().flatMap(p -> p.lots().stream()).filter(LotBrief::builds).count();
	}

	static List<CityPlanner.City> cities(Tier t) {
		List<CityPlanner.City> out = new ArrayList<>();
		for (int i = 0; i < N; i++) out.add(site(i, t).plan());
		return out;
	}

	private static final List<CityPlanner.City> HAMLETS = cities(Tier.HAMLET), VILLAGES = cities(Tier.VILLAGE);

	@Test
	void hamletsHaveThreeToEightBuildingsAndNoSquare() {
		for (int i = 0; i < N; i++) {
			CityPlanner.City c = HAMLETS.get(i);
			long n = builds(c);
			assertTrue(n >= 3 && n <= 8, "hamlet " + i + " has " + n + " buildings");
			assertTrue(c.plan().landmarks().isEmpty(), "hamlet " + i + " has landmarks");
			assertTrue(c.districts().values().stream().allMatch(p -> p.plazas().isEmpty()), "hamlet " + i + " has a plaza");
		}
	}

	@Test
	void aHamletIsOneStraightTrackThroughTheHub() {
		for (int i = 0; i < N; i++) {
			List<Road> roads = HAMLETS.get(i).plan().roads();
			assertFalse(roads.isEmpty(), "hamlet " + i);
			for (Road r : roads) {
				assertEquals(RoadKind.PATH, r.kind());
				assertTrue(r.width() >= 2 && r.width() <= 3);
				assertEquals(2, r.points().size(), "a straight run: hamlet " + i);
			}
			// all pieces share one line: same x or same z, and they meet at the start point
			boolean sameX = roads.stream().allMatch(r -> r.points().get(0).x() == r.points().get(1).x());
			boolean sameZ = roads.stream().allMatch(r -> r.points().get(0).z() == r.points().get(1).z());
			assertTrue(sameX || sameZ, "one line, hamlet " + i);
			assertEquals(1, roads.stream().map(r -> r.points().get(0)).distinct().count(), "common start, hamlet " + i);
		}
	}

	@Test
	void villagesHaveTenToSixtyBuildings() {
		for (int i = 0; i < N; i++) {
			long n = builds(VILLAGES.get(i));
			assertTrue(n >= 10 && n <= 60, "village " + i + " has " + n + " buildings");
		}
	}

	@Test
	void everyVillageHasItsTempleOrHallFacingTheGreen() {
		int temples = 0, halls = 0, crossed = 0;
		for (int i = 0; i < N; i++) {
			CityPlanner.City c = VILLAGES.get(i);
			List<Plaza> greens = c.districts().values().stream().flatMap(p -> p.plazas().stream()).filter(p -> p.kind().equals("green")).toList();
			assertEquals(1, greens.size(), "village " + i + " has one green");
			Plaza g = greens.get(0);
			List<LotBrief> faces = c.districts().values().stream().flatMap(p -> p.lots().stream()).filter(l -> l.type().equals("hall") || l.type().equals("temple")).toList();
			assertEquals(1, faces.size(), "village " + i + " has one landmark building");
			LotBrief l = faces.get(0);
			assertTrue(l.builds());
			// flush against the green on the side its front looks at
			boolean flush = switch (l.front()) {
				case NORTH -> l.z() == g.z() + g.d();
				case SOUTH -> l.z() + l.d() == g.z();
				case WEST -> l.x() == g.x() + g.w();
				case EAST -> l.x() + l.w() == g.x();
			};
			assertTrue(flush, "village " + i + ": " + l + " against " + g);
			if (l.type().equals("temple")) temples++; else halls++;
			// the landmark never stands on the main street: no street footprint overlaps it
			for (DistrictPlan p : c.districts().values()) for (Street s : p.streets()) for (Rect r : dev.fractalbuildings.core.check.SettlementChecks.footprint(s)) assertFalse(Rect.overlap(r.x(), r.z(), r.w(), r.d(), l.x(), l.z(), l.w(), l.d()), "village " + i + " landmark on street " + s.id());
			if (c.plan().roads().stream().anyMatch(r -> r.points().get(0).x() != r.points().get(1).x() && r.points().get(0).z() != r.points().get(1).z())) fail("diagonal road in village " + i);
		}
		assertTrue(temples > 110 && temples < 170 && halls > 30, temples + " temples, " + halls + " halls: about 70/30");
	}

	@Test
	void villageMainStreetIsStraightFourWideAndHalfHaveACrossStreet() {
		int withCross = 0;
		for (int i = 0; i < N; i++) {
			List<Road> roads = VILLAGES.get(i).plan().roads();
			assertTrue(roads.size() >= 2 && roads.size() <= 4, "village " + i + " has " + roads.size() + " roads");
			for (Road r : roads) {
				assertEquals(RoadKind.MAIN, r.kind());
				assertEquals(4, r.width());
				assertEquals(2, r.points().size());
			}
			if (roads.size() > 2) withCross++;
			// every road starts on the main line or on it: all starts lie on the main line (the first road's)
			Road m = roads.get(0);
			boolean alongX = m.points().get(0).z() == m.points().get(1).z();
			for (Road r : roads.subList(0, 2)) assertEquals(alongX ? m.points().get(0).z() : m.points().get(0).x(), alongX ? r.points().get(0).z() : r.points().get(0).x());
			if (roads.size() > 2) {
				Pt j = roads.get(2).points().get(0);
				assertEquals(alongX ? m.points().get(0).z() : m.points().get(0).x(), alongX ? j.z() : j.x(), "junction on the main line, village " + i);
				int a = alongX ? roads.get(0).points().get(1).x() : roads.get(0).points().get(1).z(), b = alongX ? roads.get(1).points().get(1).x() : roads.get(1).points().get(1).z(), at = alongX ? j.x() : j.z();
				assertTrue(at > Math.min(a, b) && at < Math.max(a, b), "junction between the street ends, village " + i);
			}
		}
		assertTrue(withCross > 55 && withCross < 145, withCross + " of " + N + " villages have a cross street");
	}

	@Test
	void noViolationsAndConnectedByConstruction() {
		for (List<CityPlanner.City> tier : List.of(HAMLETS, VILLAGES)) {
			for (int i = 0; i < N; i++) {
				CityPlanner.City c = tier.get(i);
				List<Violation> v = CityPlanner.check(c);
				assertTrue(v.isEmpty(), c.plan().site().tier() + " " + i + ": " + v.stream().limit(3).toList());
				assertEquals(0, c.strays(), c.plan().site().tier() + " " + i + " has stray street fragments");
			}
		}
	}

	@Test
	void sameSiteGivesByteIdenticalPlans() {
		for (Tier t : new Tier[] {Tier.HAMLET, Tier.VILLAGE}) {
			for (int i = 0; i < 12; i++) {
				CityPlanner.City a = site(i, t).plan(), b = site(i, t).plan();
				assertEquals(SettlementJson.write(a.plan()), SettlementJson.write(b.plan()));
				for (var e : a.districts().entrySet()) assertEquals(DistrictJson.write(e.getValue()), DistrictJson.write(b.districts().get(e.getKey())));
			}
		}
	}

	private static void fail(String m) { org.junit.jupiter.api.Assertions.fail(m); }
}
