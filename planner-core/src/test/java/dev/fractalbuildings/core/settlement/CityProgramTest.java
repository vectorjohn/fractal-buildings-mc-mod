package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.settlement.tiers.WallRing;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The city and metropolis programs (D3): avenues, the old-town wall, squares, parks, the outer ring and the skyline. */
class CityProgramTest {
	private static final String[] TERRAINS = {"flat:64", "noise:3:70:10:300", "noise:2:70:5:500"};
	/** Rules the whole-city check still reports on real and synthetic ground (the ROADS item removes them); nothing else may appear. */

	record Planned(CityPlannerTest.Setup setup, CityPlanner.City city) {
		SettlementPlan plan() { return city.plan(); }
		int radius() { return setup.site().radius(); }
		Pt centre() { return setup.site().centre(); }
	}

	private static List<Planned> cities, metros;

	/** 18 cities of radius 520 to 760 on flat, rolling and gentle ground; planned once. */
	static synchronized List<Planned> cities() {
		if (cities == null) {
			cities = new ArrayList<>();
			for (int i = 0; i < 18; i++) cities.add(planned(i, Tier.CITY, 520 + 40 * (i % 6)));
		}
		return cities;
	}

	/** 10 metropolises of radius 1000 to 1180. */
	static synchronized List<Planned> metros() {
		if (metros == null) {
			metros = new ArrayList<>();
			for (int i = 0; i < 10; i++) metros.add(planned(i, Tier.METROPOLIS, 1000 + 60 * (i % 4)));
		}
		return metros;
	}

	private static Planned planned(int i, Tier tier, int radius) {
		CityPlannerTest.Setup s = CityPlannerTest.setup(900 + i, TERRAINS[i % TERRAINS.length], i % 3, -(i % 2), tier, radius);
		return new Planned(s, s.plan());
	}

	// ---- avenues

	private static boolean axisOr45(Pt a, Pt b) { return a.x() == b.x() || a.z() == b.z() || Math.abs(a.x() - b.x()) == Math.abs(a.z() - b.z()); }

	/** Roads leaving the hub: how many of the main roads start with a 45-degree leg. */
	private static int diagonalAvenues(SettlementPlan p) {
		int n = 0;
		for (Road r : p.roads()) {
			if (r.kind() != RoadKind.MAIN) continue;
			Pt a = r.points().get(0), b = r.points().get(1);
			if (a.x() != b.x() && a.z() != b.z() && Math.abs(a.x() - b.x()) >= 96) n++;
		}
		return n;
	}

	@Test
	void avenuesAreAxisAlignedOrFortyFiveDegreesAndMostCitiesCutDiagonalsThroughTheGrid() {
		int withDiagonals = 0;
		for (Planned p : cities()) {
			for (Road r : p.plan().roads()) {
				for (int k = 1; k < r.points().size(); k++) assertTrue(axisOr45(r.points().get(k - 1), r.points().get(k)), r.id() + " segment " + r.points().get(k - 1) + " to " + r.points().get(k));
			}
			if (diagonalAvenues(p.plan()) >= 2) withDiagonals++;
		}
		assertTrue(withDiagonals >= 11, withDiagonals + " of " + cities().size() + " cities have two or more diagonal avenues (the draw is 75 %)");
		for (Planned p : metros()) assertTrue(diagonalAvenues(p.plan()) >= 4, "a metropolis has the four diagonals: " + diagonalAvenues(p.plan()));
	}

	@Test
	void avenuesCrossTheGridWithoutLotsOnThem() {
		int checked = 0;
		for (Planned p : cities()) {
			for (DistrictPlan dp : p.city().districts().values()) {
				List<Rect> roads = new ArrayList<>();
				for (Street s : dp.streets()) if (s.kind() == RoadKind.MAIN || s.kind() == RoadKind.ARTERIAL) roads.addAll(SettlementChecks.footprint(s));
				for (LotBrief l : dp.lots()) {
					if (WallRing.isRingLotId(l.id())) continue; // a gate lot spans the road that passes through it
					for (Rect r : roads) assertTrue(!Rect.overlap(l.x(), l.z(), l.w(), l.d(), r.x(), r.z(), r.w(), r.d()), dp.district().id() + " lot " + l.id() + " lies on a main road or arterial");
					checked++;
				}
			}
		}
		assertTrue(checked > 3000, "lots checked " + checked);
	}

	@Test
	void everyCityAndMetropolisPlansWithoutViolations() {
		for (Planned p : concat()) {
			for (var v : p.plan().violations()) org.junit.jupiter.api.Assertions.fail("seed " + p.setup().seed() + " " + p.plan().site().tier() + ": " + v.invariant() + ": " + v.message());
			// the hub's piece of the street network is most of it (a few fragments aside)
			assertTrue(p.city().hubPiece() * 10 >= p.city().streetColumns() * 7, "hub piece " + p.city().hubPiece() + " of " + p.city().streetColumns());
		}
	}

	/** The brief's storey range of a high-rise follows its density in every city (a lot beside an old town or a waterfront must not fall back to the type's whole 10 to 60). */
	@Test
	void highriseStoreysFollowTheDensityInEveryCityAndMetropolis() {
		int highrises = 0;
		for (Planned p : concat()) {
			for (DistrictPlan dp : p.city().districts().values()) {
				for (LotBrief l : dp.lots()) {
					if (!l.builds() || !l.type().equals("highrise")) continue;
					highrises++;
					assertTrue(l.density() >= 70, l.id() + " density " + l.density());
					assertTrue(l.density() >= 85 ? l.minStoreys() >= 20 : l.minStoreys() >= 10 && l.maxStoreys() <= 19, "seed " + p.setup().seed() + " " + l.id() + " storeys " + l.minStoreys() + ".." + l.maxStoreys() + " at density " + l.density());
				}
			}
		}
		assertTrue(highrises > 500, "high-rises " + highrises);
	}

	private static List<Planned> concat() {
		List<Planned> all = new ArrayList<>(cities());
		all.addAll(metros());
		return all;
	}

	// ---- the old-town wall

	@Test
	void aboutSixCitiesInTenHaveAnOldTownWallWithAGateWhereEveryAvenueCrossesIt() {
		int walled = 0, hashed = 0;
		for (Planned p : cities()) {
			SettlementPlan plan = p.plan();
			if (p.setup().source().density().oldTown().walled()) hashed++;
			assertEquals(List.of(), TownProgramTest.crossingProblems(plan), "city " + p.setup().seed());
			if (plan.walls().isEmpty()) { assertTrue(plan.gates().stream().noneMatch(g -> g.kind() == GateKind.WALL)); continue; }
			walled++;
			Wall w = plan.walls().get(0);
			assertTrue(plan.walls().size() == 1 && w.closed() && w.points().size() == 4);
			long gates = plan.gates().stream().filter(g -> g.kind() == GateKind.WALL).count();
			assertTrue(gates >= 2, "a walled city has gates: " + gates);
			// no arterial enters the old town: its gates take the avenues only (arterials are 9 wide, the passage is 5)
			int xl = w.points().get(0).x(), xr = w.points().get(1).x(), zt = w.points().get(0).z(), zb = w.points().get(2).z();
			for (Road r : plan.roads()) {
				if (r.kind() != RoadKind.ARTERIAL) continue;
				for (Pt q : r.points()) assertTrue(!(q.x() >= xl && q.x() <= xr && q.z() >= zt && q.z() <= zb), "arterial " + r.id() + " enters the ring at " + q);
			}
			// the whole ring of wall lots is planned: every lot of the ring (id .w<n>) is in the districts, none is a street
			long ringLots = p.city().districts().values().stream().flatMap(d -> d.lots().stream()).filter(l -> WallRing.isRingLotId(l.id())).count();
			assertTrue(ringLots >= 8, "ring lots " + ringLots);
		}
		assertTrue(hashed >= 7 && hashed <= 15, "walled by hash " + hashed + " of " + cities().size() + " (draw is 60 %)");
		assertTrue(walled >= 5 && walled <= hashed, "walled " + walled + " of " + hashed + " drawn (some rings are refused by the ground or the cell)");
	}

	@Test
	void theOldTownHoldsNoBlocksOrTowersAndTheGateCrossingRuleCanFail() {
		boolean tried = false;
		for (Planned p : cities()) {
			SettlementPlan plan = p.plan();
			if (plan.walls().isEmpty()) continue;
			Wall w = plan.walls().get(0);
			int xl = w.points().get(0).x(), xr = w.points().get(1).x(), zt = w.points().get(0).z(), zb = w.points().get(2).z();
			for (DistrictPlan dp : p.city().districts().values()) {
				for (LotBrief l : dp.lots()) {
					if (!l.builds() || WallRing.isRingLotId(l.id())) continue;
					int cx = l.x() + l.w() / 2, cz = l.z() + l.d() / 2;
					if (cx > xl + 12 && cx < xr - 12 && cz > zt + 12 && cz < zb - 12) assertTrue(!l.type().equals("block") && !l.type().equals("highrise") && l.maxStoreys() <= ClassTable.OLD_TOWN_CAP, "inside the walls: " + l.id() + " " + l.type() + " " + l.maxStoreys());
				}
			}
			if (tried) continue;
			List<Gate> kept = new ArrayList<>(plan.gates());
			kept.removeIf(g -> g.kind() == GateKind.WALL);
			SettlementPlan stripped = new SettlementPlan(plan.genVersion(), plan.seed(), plan.site(), plan.bounds(), plan.coarse(), plan.quarters(), plan.cells(), plan.districts(), plan.roads(), kept, plan.landmarks(), plan.walls(), plan.violations());
			assertTrue(!TownProgramTest.crossingProblems(stripped).isEmpty(), "a plan whose gates were removed must fail the crossing rule");
			tried = true;
		}
		assertTrue(tried);
	}

	// ---- squares at arterial crossings

	@Test
	void aboutOneArterialCrossingInFourHasASquareThatTouchesItsArterials() {
		int crossings = 0, squares = 0, touching = 0;
		for (Planned p : cities()) {
			SettlementPlan plan = p.plan();
			Arterials art = p.setup().source().density().arterials();
			DistrictGrid g = new DistrictGrid(p.setup().seed());
			Rect b = plan.bounds();
			DistrictGrid.TileId lo = g.tileOf(b.x(), b.z()), hi = g.tileOf(b.x() + b.w(), b.z() + b.d());
			int inner = 0;
			for (int kz = lo.iz() + 1; kz <= hi.iz(); kz++) {
				for (int kx = lo.ix() + 1; kx <= hi.ix(); kx++) {
					boolean v = art.is(g.segments(kx, kz)[0]) || art.is(g.segments(kx, kz - 1)[0]), h = art.is(g.segments(kx, kz)[2]) || art.is(g.segments(kx - 1, kz)[2]);
					if (v && h) inner++;
				}
			}
			crossings += inner;
			Pt hubAt = p.centre();
			for (Landmark l : plan.landmarks()) {
				if (l.kind() != LandmarkKind.SQUARE) continue;
				if (l.x() <= hubAt.x() && hubAt.x() < l.x() + l.w() && l.z() <= hubAt.z() && hubAt.z() < l.z() + l.d()) continue; // the hub square (or the hub's tile)
				DistrictPlan dp = p.city().districts().get(l.district());
				if (dp == null) continue;
				squares++;
				boolean touch = false;
				for (Street s : dp.streets()) {
					if (s.kind() != RoadKind.ARTERIAL) continue;
					for (Rect r : SettlementChecks.footprint(s)) if (Rect.overlap(l.x() - 1, l.z() - 1, l.w() + 2, l.d() + 2, r.x(), r.z(), r.w(), r.d())) touch = true;
				}
				if (touch) touching++;
			}
		}
		assertTrue(crossings > 40, "crossings " + crossings); // METROFILL R1: arterials on every second line only (71 over these cities, 100+ before)
		double share = squares / (double) crossings;
		assertTrue(share > 0.5 && share < 1.2, // METROFILL R1: arterial crossings are 4x rarer, so every one gets a square (SQUARE_ONE_IN 1); share includes the few non-crossing squares
				 squares + " squares at " + crossings + " arterial crossings: " + share);
		assertTrue(touching >= squares * 0.8, touching + " of " + squares + " squares touch an arterial"); // METROFILL R1 r4: crossing squares sit flush with both arterials (68 of 80; the rest are old-town small squares and joined cells' squares); 0.65 before. How district detail moves them: CrossingSquareTest
	}

	// ---- the metropolis

	@Test
	void theMetropolisOuterRingIsOneClosedLoopOfArterialsAtAboutSixTenthsOfTheRadius() {
		for (Planned p : metros().subList(0, 4)) {
			SkeletonSource src = p.setup().source();
			Arterials art = src.density().arterials();
			DistrictGrid g = new DistrictGrid(p.setup().seed());
			Pt c = p.centre();
			int reach = p.radius() * 5 / 4;
			DistrictGrid.TileId lo = g.tileOf(c.x() - reach, c.z() - reach), hi = g.tileOf(c.x() + reach, c.z() + reach);
			TreeMap<String, Integer> degree = new TreeMap<>();
			int ringSegs = 0, arterial = 0;
			for (int kz = lo.iz(); kz <= hi.iz() + 1; kz++) {
				for (int kx = lo.ix(); kx <= hi.ix() + 1; kx++) {
					DistrictGrid.Segment[] ss = {g.segments(kx, kz)[0], g.segments(kx, kz)[2]}; // the segments starting at this corner, going +z and +x
					for (DistrictGrid.Segment s : ss) {
						if (!art.outerRing(s)) continue;
						ringSegs++;
						int mx = Arterials.ax(s, (s.from() + s.to()) / 2), mz = Arterials.az(s, (s.from() + s.to()) / 2);
						double r = Math.hypot(mx - c.x(), mz - c.z()) / p.radius();
						assertTrue(r > 0.4 && r < 0.85, "ring segment at " + r + " of the radius");
						if (art.is(s)) arterial++;
						int ex = s.axis() == DistrictGrid.AXIS_X ? kx : kx + 1, ez = s.axis() == DistrictGrid.AXIS_X ? kz + 1 : kz;
						degree.merge(kx + "," + kz, 1, Integer::sum);
						degree.merge(ex + "," + ez, 1, Integer::sum);
					}
				}
			}
			assertTrue(ringSegs > 12, "ring segments " + ringSegs);
			for (var e : degree.entrySet()) assertTrue(e.getValue() % 2 == 0, "the ring is a closed curve: tile corner " + e.getKey() + " has " + e.getValue() + " ring segments");
			// every ring segment whose ends are urban is an arterial (a ring broken only by water or a peak)
			assertTrue(arterial >= ringSegs * 0.6, arterial + " of " + ringSegs + " ring segments are arterials");
		}
	}

	@Test
	void aboutOneDowntownTileInEightIsAParkWithNoBuildings() {
		int downtown = 0, parks = 0;
		for (Planned p : metros()) {
			SkeletonSource src = p.setup().source();
			QuarterField q = src.quarters();
			for (DistrictSummary d : p.plan().districts()) {
				DistrictGrid.Tile t = new DistrictGrid(p.setup().seed()).tile(d.ix(), d.iz());
				int cx = (t.minX() + t.maxX()) / 2, cz = (t.minZ() + t.maxZ()) / 2;
				if (src.density().at(cx, cz) < QuarterField.DOWNTOWN_DENSITY) continue;
				if (t.contains(src.density().terrain().snap(p.centre().x()), src.density().terrain().snap(p.centre().z()))) continue; // the hub tile
				downtown++;
				if (!q.parkTile(cx, cz)) continue;
				parks++;
				DistrictPlan dp = p.city().districts().get(d.id());
				if (dp != null) for (LotBrief l : dp.lots()) assertTrue(!l.builds(), "a park tile has a building: " + l.id());
			}
		}
		assertTrue(downtown > 100, "downtown tiles " + downtown);
		assertTrue(parks * 100 >= downtown * 7 && parks * 100 <= downtown * 20, parks + " parks in " + downtown + " downtown tiles (one in eight)");
	}

	// ---- the skyline

	@Test
	void theSkylineRisesTowardsTheCentreAndNoHighriseStandsBelowDensity70() {
		TreeMap<Integer, long[]> band = new TreeMap<>();
		int tallestInside = 0;
		for (Planned p : metros()) {
			int tallest = 0, tallestNear = 0;
			for (DistrictPlan dp : p.city().districts().values()) {
				for (LotBrief l : dp.lots()) {
					if (!l.builds()) continue;
					double r = Math.hypot(l.x() + l.w() / 2.0 - p.centre().x(), l.z() + l.d() / 2.0 - p.centre().z()) / p.radius();
					long[] b = band.computeIfAbsent((int) Math.min(4, r * 5), k -> new long[2]);
					b[0] += l.maxStoreys();
					b[1]++;
					tallest = Math.max(tallest, l.maxStoreys());
					if (r <= 0.3) tallestNear = Math.max(tallestNear, l.maxStoreys());
					if (l.type().equals("highrise")) {
						assertTrue(l.density() >= 70, "highrise at density " + l.density() + " " + l.id());
						assertTrue(l.density() >= 85 ? l.maxStoreys() >= 20 : l.minStoreys() >= 10 && l.maxStoreys() <= 19, "highrise storeys " + l.minStoreys() + ".." + l.maxStoreys() + " at density " + l.density());
					}
				}
			}
			if (tallestNear == tallest) tallestInside++;
		}
		assertTrue(tallestInside >= 9, "the tallest storey count is reached within 0.3 radius of the centre in " + tallestInside + " of " + metros().size() + " metropolises");
		double prev = Double.MAX_VALUE;
		for (var e : band.entrySet()) {
			double mean = e.getValue()[0] / (double) e.getValue()[1];
			assertTrue(mean < prev, "mean top storeys in band " + e.getKey() + " is " + mean + ", not below " + prev);
			prev = mean;
		}
		assertTrue(band.get(0)[0] / (double) band.get(0)[1] > 12 && band.get(4)[0] / (double) band.get(4)[1] < 4, "a skyline at the centre and houses at the edge");
	}

	/** P5 skyline: the tall core is a small share of the city, heights inside it are peaked (not a flat plateau), and a long tail of mid-rise and low buildings runs outward. */
	@Test
	void theTallCoreIsASmallShareWithAPeakedHeightDistributionAndALongTail() {
		int built = 0, tall = 0, mid = 0, low = 0, top = 0;
		for (Planned p : metros()) {
			for (DistrictPlan dp : p.city().districts().values()) {
				for (LotBrief l : dp.lots()) {
					if (!l.builds()) continue;
					built++;
					int s = l.maxStoreys();
					if (s >= 20) tall++;
					if (s >= 45) top++;
					if (s >= 10 && s <= 19) mid++;
					if (s <= 4) low++;
				}
			}
		}
		assertTrue(tall > 0 && tall * 100 <= built * 8, tall + " of " + built + " lots reach 20 storeys: the tall core is at most 8 per cent of the city");
		assertTrue(top * 100 <= tall * 20, top + " of " + tall + " towers reach 45 storeys: the tallest are few");
		assertTrue(mid * 100 >= built * 3, mid + " mid-rise lots of " + built);
		assertTrue(low * 100 >= built * 80, low + " lots of 4 storeys or fewer, of " + built + ": the long tail");
	}

	/** Writes build/skyline/metropolis.png (two transects through the centre of the first two metropolises) and checks their shape. */
	@Test
	void skylineTransectsPeakAtTheCentreAndFallAway() throws Exception {
		int[][] profiles = new int[4][];
		String[] labels = new String[4];
		for (int k = 0; k < 2; k++) {
			Planned p = metros().get(k);
			for (int axis = 0; axis < 2; axis++) {
				int[] prof = SkylinePng.profile(p.city().districts().values(), p.centre(), p.radius(), axis == 0, 60);
				profiles[k * 2 + axis] = prof;
				labels[k * 2 + axis] = "metropolis seed " + p.setup().seed() + " r" + p.radius() + ", transect along " + (axis == 0 ? "x" : "z");
				int n = prof.length, inner = 0, outer = 0;
				for (int b = 0; b < n; b++) {
					boolean in = b >= n * 3 / 8 && b < n * 5 / 8, out = b < n / 8 || b >= n * 7 / 8;
					if (in) inner = Math.max(inner, prof[b]);
					if (out) outer = Math.max(outer, prof[b]);
				}
				assertTrue(inner >= 20 && outer <= 8 && inner > 2 * outer, labels[k * 2 + axis] + ": the centre reaches " + inner + " storeys, the rim " + outer);
			}
		}
		SkylinePng.write(profiles, labels, new java.io.File("build/skyline/metropolis.png"));
	}

	// ---- street blocks and the street fabric (ROADS3)

	/** Does a street footprint cover every column of the ring just outside the rectangle (all four sides and the corners)? */
	private static boolean ringIsStreet(DistrictPlan d, LotBrief l) {
		java.util.Set<Long> cover = new java.util.HashSet<>();
		for (Street s : d.streets())
			for (Rect r : SettlementChecks.footprint(s))
				for (int z = Math.max(r.z(), l.z() - 1); z < Math.min(r.z() + r.d(), l.z() + l.d() + 1); z++)
					for (int x = Math.max(r.x(), l.x() - 1); x < Math.min(r.x() + r.w(), l.x() + l.w() + 1); x++) cover.add(((long) x << 32) ^ (z & 0xffffffffL));
		for (int x = l.x() - 1; x <= l.x() + l.w(); x++) for (int z : new int[] {l.z() - 1, l.z() + l.d()}) if (!cover.contains(((long) x << 32) ^ (z & 0xffffffffL))) return false;
		for (int z = l.z(); z < l.z() + l.d(); z++) for (int x : new int[] {l.x() - 1, l.x() + l.w()}) if (!cover.contains(((long) x << 32) ^ (z & 0xffffffffL))) return false;
		return true;
	}

	/**
	 * The grid quarters form street rings (ROADS3: grid land is judged per block side, not per node, and blocks stand from density 50), so the
	 * mid-density band gets perimeter blocks: every block lot has street on all four sides, and the cities and metropolises on these gentle
	 * terrains hold blocks in the 50-to-85 band (before: 5 in all 28 settlements, almost all at 70 and up).
	 */
	@Test
	void gridQuartersFormStreetRingsSoTheMidDensityBandGetsPerimeterBlocks() {
		int blocks = 0, mid = 0, low = 0;
		List<Planned> all = new ArrayList<>(cities());
		all.addAll(metros());
		for (Planned p : all) {
			for (DistrictPlan d : p.city().districts().values()) {
				for (LotBrief l : d.lots()) {
					if (!l.type().equals("block") || !l.builds()) continue;
					blocks++;
					assertTrue(ringIsStreet(d, l), "block " + l.id() + " has a side with no street");
					if (l.density() < 85) mid++;
					if (l.density() < 70) low++;
				}
			}
		}
		System.out.println("blocks " + blocks + ", density 50-84 " + mid + ", 50-69 " + low);
		assertTrue(mid >= 60, "perimeter blocks in the 50-85 band: " + mid + " of " + blocks);
		assertTrue(low >= 20, "perimeter blocks in the 50-70 band: " + low + " of " + blocks);
	}

	/**
	 * Arterials and squares read the street fabric's density ({@link DensityField#fabric}: the plain {@code cap (1 - t^2)} profile, keyed to the
	 * share of the radius), not the skyline's (ROADS3: SKYLINE's smaller tall core had thinned the metropolis's arterials and squares). On flat ground
	 * every urban tile-border segment whose fabric density at its midpoint is 80 or more is an arterial (the rule's probability is 1 there), well
	 * beyond the tall core.
	 */
	@Test
	void metropolisArterialsFollowTheStreetFabricNotTheSkyline() {
		int sure = 0, outsideCore = 0;
		for (Planned p : metros()) {
			if (!TERRAINS[(int) ((p.setup().seed() - 900) % TERRAINS.length)].equals("flat:64")) continue; // flat ground: no grade or water veto
			SkeletonSource src = p.setup().source();
			DensityField den = src.density();
			Arterials art = den.arterials();
			Pt c = p.centre();
			int r = p.radius();
			for (DistrictGrid.Segment s : art.segmentsIn(c.x() - r, c.z() - r, c.x() + r, c.z() + r)) {
				int mid = Math.floorDiv(s.from() + s.to(), 2), mx = Arterials.ax(s, mid), mz = Arterials.az(s, mid);
				if (!den.urban(Arterials.ax(s, s.from()), Arterials.az(s, s.from())) || !den.urban(Arterials.ax(s, s.to()), Arterials.az(s, s.to()))) continue;
				if (den.fabric(mx, mz) < 80) continue;
				if (!art.parityLine(s) && !art.outerRing(s)) continue; // METROFILL R1: arterials only on every second tile line
				sure++;
				if (den.base(mx, mz) < 80) outsideCore++;
				assertTrue(art.is(s), "seed " + p.setup().seed() + " segment " + s + " fabric " + den.fabric(mx, mz) + " is no arterial");
			}
		}
		System.out.println("fabric-80 segments " + sure + ", of them below skyline density 80: " + outsideCore);
		assertTrue(sure >= 40 && outsideCore >= 20, "segments checked " + sure + ", outside the tall core " + outsideCore);
	}

	/**
	 * Grid land is judged once per block side (ROADS3, {@code StreetRules.gridSideEdgeOk}): on flat dry ground, where no slope or water vetoes an
	 * edge, every stretch of a grid line between two crossing lines is drawn whole or not at all (before, a node whose own column fell below density
	 * 50 or out of the footprint cut the side short and left the blocks beside it without a street ring).
	 */
	@Test
	void gridLandIsJudgedPerBlockSideSoEachSideIsWholeOrAbsent() {
		int whole = 0;
		for (Planned p : cities()) {
			if (!TERRAINS[(int) ((p.setup().seed() - 900) % TERRAINS.length)].equals("flat:64")) continue;
			SkeletonSource src = p.setup().source();
			dev.fractalbuildings.core.probe.TerrainProbe exact0 = p.setup().p().terrain();
			StreetRules rules = new StreetRules(src, exact0);
			dev.fractalbuildings.core.probe.TerrainProbe exact = p.setup().p().terrain();
			for (DistrictPlan d : p.city().districts().values()) {
				DistrictGrid.Tile t = src.grid().tile(d.district().ix(), d.district().iz());
				for (int axis = 0; axis < 2; axis++) {
					boolean vertical = axis == DistrictGrid.AXIS_X;
					int other = vertical ? DistrictGrid.AXIS_Z : DistrictGrid.AXIS_X;
					int lo = vertical ? t.minZ() : t.minX(), hi = vertical ? t.maxZ() : t.maxX();
					for (int pos : rules.gridLines(axis, vertical ? t.ix() : t.iz())) {
						TreeMap<Integer, Set<Boolean>> bySide = new TreeMap<>();
						for (int a = Math.floorDiv(lo, 8) * 8; a + 8 <= hi; a += 8) bySide.computeIfAbsent(rules.between(other, a + 4)[0], k -> new java.util.TreeSet<>()).add(rules.gridSideEdgeOk(exact, axis, pos, a, a + 8));
						for (var e : bySide.entrySet()) {
							assertEquals(1, e.getValue().size(), "seed " + p.setup().seed() + " line " + (vertical ? "x " : "z ") + pos + ": the side from " + e.getKey() + " is partly grid");
							if (e.getValue().contains(true)) whole++;
						}
					}
				}
			}
		}
		assertTrue(whole >= 50, "whole grid sides " + whole);
	}
}
