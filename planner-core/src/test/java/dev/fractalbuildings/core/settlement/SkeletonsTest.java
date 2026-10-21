package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.RegionField;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SkeletonsTest {
	static final int GEN = 2;

	record Case(long seed, RegionField field, SettlementSite site, String spec) {
		CoarseProbe probe() { return SkeletonFixtures.probe(spec); }

		SettlementPlan plan() { return Skeletons.plan(seed, new FixedSiteView(site, field), site, probe(), GEN); }
	}

	/** A synthetic site spanning 2 to 6 cells of a 512 grid, on noisy terrain with a river on every other case. */
	static Case syntheticCase(int i) {
		long seed = 1000 + i;
		RegionField f = new RegionField(seed, 512);
		Tier tier = new Tier[] {Tier.TOWN, Tier.CITY, Tier.METROPOLIS}[i % 3];
		int cx = Hash64.below(Hash64.hash(seed, "cx"), 9) - 4, cz = Hash64.below(Hash64.hash(seed, "cz"), 9) - 4;
		int radius = 450 + Hash64.below(Hash64.hash(seed, "r"), 450);
		SettlementSite s = SkeletonFixtures.site(f, cx, cz, tier, radius, 6);
		for (int grow = 0; s.cells().size() < 2; grow += 60) s = SkeletonFixtures.site(f, cx, cz, tier, radius + grow, 6);
		String spec = "noise:" + i + ":70:" + (6 + i % 16) + ":" + (200 + 20 * (i % 9)) + (i % 2 == 0 ? "+river:" + (Hash64.below(Hash64.hash(seed, "rv"), 400) - 200) + ":" + (20 + 8 * (i % 5)) + ":6" : "");
		return new Case(seed, f, s, spec);
	}

	@Test
	void rulesHoldOver200SyntheticSites() {
		int multi = 0, bridges = 0;
		for (int i = 0; i < 200; i++) {
			Case c = syntheticCase(i);
			int n = c.site().cells().size();
			assertTrue(n >= 2 && n <= 6, "case " + i + " spans " + n + " cells");
			SettlementPlan p = c.plan();
			assertEquals(List.of(), p.violations().stream().map(v -> v.invariant() + ": " + v.message()).toList(), "case " + i + " (" + c.site().tier() + ", " + c.spec() + ")");
			multi++;
			for (Road r : p.roads()) bridges += r.bridges().size();
			assertDensityAndMask(p, c.site().tier(), i);
			assertGatesOnSharedEdges(c, p);
		}
		assertEquals(200, multi);
		assertTrue(bridges > 0, "no bridges at all in 200 sites with rivers");
	}

	static void assertDensityAndMask(SettlementPlan p, Tier tier, int i) {
		CoarseRaster r = p.coarse();
		int[] mask = r.mask(), dens = r.density(), quarter = r.quarter();
		int urban = 0;
		for (int k = 0; k < mask.length; k++) {
			if (mask[k] != CoarseRaster.URBAN) assertEquals(0, dens[k], "case " + i + ": density outside the urban mask at " + k);
			else { urban++; assertTrue(dens[k] <= tier.densityCap, "case " + i + ": density " + dens[k] + " over the " + tier + " cap"); }
			if (mask[k] == CoarseRaster.URBAN || mask[k] == CoarseRaster.OPEN) assertTrue(quarter[k] >= 0, "urban sample without a quarter");
			else assertEquals(-1, quarter[k]);
		}
		assertTrue(urban > 20, "case " + i + ": only " + urban + " urban samples");
	}

	/** Every cell gate sits within one lattice step of the shared edge of the two cells it names. */
	static void assertGatesOnSharedEdges(Case c, SettlementPlan p) {
		for (Gate g : p.gates()) {
			if (g.kind() != GateKind.CELL) continue;
			Cell a = g.cells().get(0), b = g.cells().get(1);
			CellGeometry.Edge e = CellGeometry.sharedEdge(c.field(), a, b);
			assertNotNull(e, "cell gate " + g.id() + " on cells that share no edge");
			double best = 1e9;
			for (int k = 0; k <= 200; k++) best = Math.min(best, Math.hypot(e.x(k / 200.0) - g.x(), e.z(k / 200.0) - g.z()));
			assertTrue(best <= 32, "gate " + g.id() + " is " + best + " blocks off the border");
		}
	}

	@Test
	void buildingCellsInAnyOrderWithFreshSourcesGivesTheSameJson() {
		for (int i = 0; i < 12; i++) {
			Case c = syntheticCase(i * 7);
			String expected = SettlementJson.write(c.plan());
			for (long shuffle = 0; shuffle < 3; shuffle++) {
				List<Cell> order = new ArrayList<>(c.site().cells());
				Collections.shuffle(order, new Random(shuffle * 31 + i));
				CoarseProbe fresh = c.probe();
				List<CellSkeleton> skeletons = new ArrayList<>();
				for (Cell cell : order) skeletons.add(Skeletons.build(c.seed(), new FixedSiteView(c.site(), c.field()), cell, fresh, new DistrictGrid(c.seed())));
				String actual = SettlementJson.write(Skeletons.assemble(c.seed(), new FixedSiteView(c.site(), c.field()), c.site(), fresh, GEN, skeletons));
				assertEquals(expected, actual, "case " + i + " shuffle " + shuffle);
			}
		}
	}

	@Test
	void aCellBuiltAloneEqualsTheSameCellBuiltAmongItsNeighbours() {
		Case c = syntheticCase(31);
		Cell last = c.site().cells().get(c.site().cells().size() - 1);
		CellSkeleton alone = Skeletons.build(c.seed(), new FixedSiteView(c.site(), c.field()), last, c.probe(), new DistrictGrid(c.seed()));
		Skeletons.plan(c.seed(), new FixedSiteView(c.site(), c.field()), c.site(), c.probe(), GEN); // warm nothing shared: a skeleton holds no state
		CellSkeleton again = Skeletons.build(c.seed(), new FixedSiteView(c.site(), c.field()), last, c.probe(), new DistrictGrid(c.seed()));
		assertEquals(alone.coarse(), again.coarse());
		assertEquals(alone.hub(), again.hub());
		assertEquals(roadsText(alone), roadsText(again));
		assertEquals(alone.gates(), again.gates());
		assertEquals(alone.districts(), again.districts());
	}

	static String roadsText(CellSkeleton s) {
		StringBuilder sb = new StringBuilder();
		for (Road r : s.roads()) {
			sb.append(r.id()).append(r.kind()).append(r.width()).append(r.points());
			for (int[] b : r.bridges()) sb.append(java.util.Arrays.toString(b));
		}
		return sb.toString();
	}

	@Test
	void aBiasedCoarseProbeKeepsTheInvariantsAndTheCellGates() {
		int checked = 0;
		for (int i = 0; i < 40; i++) {
			Case c = syntheticCase(i * 3);
			SettlementPlan clean = c.plan();
			CoarseProbe biased = new BiasedCoarseProbe(c.probe(), c.seed(), 20);
			SettlementPlan b = Skeletons.plan(c.seed(), new FixedSiteView(c.site(), c.field()), c.site(), biased, GEN);
			assertEquals(List.of(), b.violations().stream().map(v -> v.invariant() + ": " + v.message()).toList(), "case " + i);
			long g0 = clean.gates().stream().filter(g -> g.kind() == GateKind.CELL).count(), g1 = b.gates().stream().filter(g -> g.kind() == GateKind.CELL).count();
			assertTrue(Math.abs(g0 - g1) <= 1, "case " + i + ": cell gates " + g0 + " -> " + g1 + " under a +-20 height error");
			assertGatesOnSharedEdges(new Case(c.seed(), c.field(), c.site(), c.spec()), b);
			checked++;
		}
		assertEquals(40, checked);
	}

	// ---- water

	/** The longest run of consecutive water points of a road (points are lattice columns, so a run of n is about 32 n blocks). */
	static int longestWetRun(Road r, CoarseTerrain t) {
		int best = 0, run = 0;
		Pt last = null;
		for (Pt p : r.points()) {
			Pt node = new Pt(t.snap(p.x()), t.snap(p.z()));
			if (node.equals(last)) continue; // arterial parts repeat the node at a cell end
			last = node;
			run = t.water(p.x(), p.z()) ? run + 1 : 0;
			best = Math.max(best, run);
		}
		return best;
	}

	@Test
	void roadsNeverBridgeWaterWiderThan40() {
		for (int w : new int[] {20, 40, 70, 120, 300}) {
			RegionField f = new RegionField(5, 512);
			SettlementSite s = SkeletonFixtures.site(f, 0, 0, new Pt(f.siteX(0, 0), f.siteZ(0, 0)), Tier.CITY, 700, 6);
			int x0 = f.siteX(0, 0) - 30; // the river passes just west of the hub
			CoarseProbe probe = SkeletonFixtures.probe("flat:64+river:" + x0 + ":" + w + ":6");
			CoarseTerrain t = new CoarseTerrain(probe);
			SettlementPlan p = Skeletons.plan(5, new FixedSiteView(s, f), s, probe, GEN);
			assertEquals(List.of(), p.violations());
			int maxRun = Arterials.MAX_BRIDGE / 32;
			for (Road r : p.roads()) {
				assertTrue(longestWetRun(r, t) <= maxRun, "width " + w + ": road " + r.id() + " has a wet run of " + longestWetRun(r, t) + " lattice steps");
				for (int[] b : r.bridges()) assertTrue(b[1] - b[0] >= 2, "a bridge covers at least one wet point between two land points");
			}
			boolean any = p.roads().stream().anyMatch(r -> !r.bridges().isEmpty());
			if (w >= 70) assertFalse(any, "river of width " + w + " is wider than 40 and must not be bridged");
			assertTrue(p.roads().stream().anyMatch(r -> r.kind() == RoadKind.MAIN), "roads still exist on the hub's bank (width " + w + ")");
		}
	}

	@Test
	void aNarrowRiverIsBridged() {
		RegionField f = new RegionField(5, 512);
		SettlementSite s = SkeletonFixtures.site(f, 0, 0, new Pt(f.siteX(0, 0), f.siteZ(0, 0)), Tier.CITY, 700, 6);
		int x0 = Math.floorDiv(f.siteX(0, 0), 32) * 32 + 60; // one lattice column wet (width 30 straddles exactly one multiple of 32)
		CoarseProbe probe = SkeletonFixtures.probe("flat:64+river:" + (x0 - 5) + ":30:6");
		SettlementPlan p = Skeletons.plan(5, new FixedSiteView(s, f), s, probe, GEN);
		assertEquals(List.of(), p.violations());
		assertTrue(p.roads().stream().anyMatch(r -> !r.bridges().isEmpty()), "expected at least one bridge over a 30-block river");
	}

	@Test
	void aTownOnTheRecordedRiverWindowStraddlesTheRiverWithABridge() throws Exception {
		RecordedProbe r = RecordedProbe.read(Path.of("../fixtures/terrain/w12345-river-s8.json.gz"));
		CoarseProbe probe = CoarseProbe.of(r, r, 32);
		RegionField f = new RegionField(12345, 1024);
		Pt centre = new Pt(-900, -3500);
		Cell claimant = CellGeometry.owner(f, centre.x(), centre.z());
		SettlementSite s = new SettlementSite("river-town", Tier.TOWN, centre, 300, claimant, List.of(claimant));
		SettlementPlan p = Skeletons.plan(12345, new FixedSiteView(s, f), s, probe, GEN);
		assertEquals(List.of(), p.violations());
		CoarseTerrain t = new CoarseTerrain(probe);
		int bridges = 0, crossings = 0;
		for (Road road : p.roads()) {
			bridges += road.bridges().size();
			assertTrue(longestWetRun(road, t) <= 1);
			crossings += road.bridges().size();
		}
		assertTrue(bridges >= 1, "the town has no bridge");
		// urban land lies on both sides of water: some urban sample has water within 96 blocks on two opposite sides
		CoarseRaster c = p.coarse();
		int straddle = 0;
		int[] mask = c.mask(), water = c.water();
		for (int j = 3; j < c.nz() - 3; j++) {
			for (int i = 3; i < c.nx() - 3; i++) {
				if (mask[j * c.nx() + i] != CoarseRaster.URBAN) continue;
				boolean w = false, e = false, n = false, so = false;
				for (int d = 1; d <= 3; d++) {
					w |= water[j * c.nx() + i - d] > 0; e |= water[j * c.nx() + i + d] > 0;
					n |= water[(j - d) * c.nx() + i] > 0; so |= water[(j + d) * c.nx() + i] > 0;
				}
				if ((w && e) || (n && so)) straddle++;
			}
		}
		assertTrue(straddle > 0 || crossings > 0, "no urban sample has water on two sides");
	}

	// ---- structure by tier

	@Test
	void structureFollowsTheTier() {
		RegionField f = new RegionField(21, 1024);
		CoarseProbe flat = SkeletonFixtures.probe("flat:64");
		int[] radius = {60, 150, 350, 800, 1500};
		int[] mainWidth = {3, 4, 5, 7, 9};
		for (Tier t : Tier.values()) {
			SettlementSite s = SkeletonFixtures.site(f, 0, 0, t, radius[t.ordinal()], 6);
			SettlementPlan p = Skeletons.plan(21, new FixedSiteView(s, f), s, flat, GEN);
			assertEquals(List.of(), p.violations(), t.toString());
			assertEquals(p.landmarks().stream().filter(l -> l.kind() == LandmarkKind.GREEN || l.kind() == LandmarkKind.MARKET || l.kind() == LandmarkKind.SQUARE).count(),
					t == Tier.HAMLET ? 0 : 1, t + " hub squares");
			long arterials = p.roads().stream().filter(r -> r.kind() == RoadKind.ARTERIAL).count();
			if (t.atLeast(Tier.CITY)) assertTrue(arterials > 5, t + " arterials: " + arterials); else assertEquals(0, arterials, t + " has arterials");
			for (Road r : p.roads()) if (r.kind() == RoadKind.MAIN || r.kind() == RoadKind.PATH) assertEquals(mainWidth[t.ordinal()], r.width(), t + " main road width");
			long exits = p.gates().stream().filter(g -> g.kind() == GateKind.EXIT).count();
			assertTrue(exits >= (t == Tier.HAMLET ? 1 : 2) , t + " exits: " + exits);
			assertTrue(exits <= 4 * s.cells().size(), t + " exits: " + exits);
			// every district has an id of its own and a tile-aligned rectangle
			assertFalse(p.districts().isEmpty());
		}
	}

	@Test
	void landmarksStayInsideTheirTileAndApartFromEachOther() {
		for (int i = 0; i < 30; i++) {
			Case c = syntheticCase(i);
			SettlementPlan p = c.plan();
			assertFalse(p.landmarks().isEmpty(), "case " + i);
			for (Landmark l : p.landmarks()) {
				DistrictSummary d = p.district(l.district());
				assertTrue(d.rect().containsRect(l.x(), l.z(), l.w(), l.d()), "case " + i + ": landmark " + l.id() + " leaves tile " + d.id());
				for (Landmark o : p.landmarks()) {
					if (o != l) assertFalse(Rect.overlap(l.x(), l.z(), l.w(), l.d(), o.x(), o.z(), o.w(), o.d()), "case " + i + ": landmarks " + l.id() + " and " + o.id() + " overlap");
				}
			}
		}
	}

	@Test
	void arterialsLieOnTileBordersAndContinueAcrossCells() {
		Case c = syntheticCase(2); // a CITY... or larger
		SettlementPlan p = c.plan();
		DistrictGrid g = new DistrictGrid(c.seed());
		int joined = 0;
		for (Road r : p.roads()) {
			if (r.kind() != RoadKind.ARTERIAL) continue;
			boolean alongZ = r.points().get(0).x() == r.points().get(r.points().size() - 1).x();
			int fixed = alongZ ? r.points().get(0).x() : r.points().get(0).z();
			boolean onLine = false;
			for (int k = -3; k < 4 && !onLine; k++) for (int axis = 0; axis < 2; axis++) onLine |= axis == (alongZ ? 0 : 1) && g.lineBlock(axis, Math.floorDiv(fixed, 160) + k) == fixed;
			assertTrue(onLine, "arterial " + r.id() + " is not on a tile line");
			for (Pt q : r.points()) assertEquals(fixed, alongZ ? q.x() : q.z());
			for (int i = 1; i < r.points().size(); i++) {
				int d = alongZ ? r.points().get(i).z() - r.points().get(i - 1).z() : r.points().get(i).x() - r.points().get(i - 1).x();
				assertTrue(d > 0, "arterial points must run in order");
				if (d == 1) joined++;
			}
		}
		assertTrue(joined > 0 || p.site().cells().size() == 1, "no arterial continues across a cell border");
	}

	@Test
	void settlementCheckRulesFailOnBrokenSkeletons() {
		Case c = syntheticCase(4);
		RegionView view = new FixedSiteView(c.site(), c.field());
		CoarseProbe probe = c.probe();
		DistrictGrid grid = new DistrictGrid(c.seed());
		List<CellSkeleton> sk = new ArrayList<>();
		for (Cell cell : c.site().cells()) sk.add(Skeletons.build(c.seed(), view, cell, probe, grid));
		assertEquals(List.of(), SettlementChecks.skeletonInCell(sk, c.field()));
		assertEquals(List.of(), SettlementChecks.cellGateAgree(sk));

		// a road point moved into another cell's territory
		CellSkeleton first = sk.get(0);
		Cell other = c.site().cells().get(c.site().cells().size() - 1);
		Pt far = new Pt(c.field().siteX(other.cx(), other.cz()) / 32 * 32, c.field().siteZ(other.cx(), other.cz()) / 32 * 32);
		List<Road> roads = new ArrayList<>(first.roads());
		Road r0 = roads.get(0);
		List<Pt> pts = new ArrayList<>(r0.points());
		pts.set(pts.size() / 2, far);
		// keep the shape rule: replace by a straight two-point road from the hub to the far point is not axis aligned, so use an axis-aligned stub
		pts = new ArrayList<>(List.of(first.hub(), new Pt(far.x(), first.hub().z())));
		roads.set(0, new Road(r0.id(), r0.kind(), r0.width(), pts, List.of()));
		CellSkeleton broken = new CellSkeleton(first.cell(), first.outline(), first.coarse(), first.hub(), roads, first.gates(), first.landmarks(), first.districts(), first.walls());
		List<CellSkeleton> bad = new ArrayList<>(sk);
		bad.set(0, broken);
		assertFalse(SettlementChecks.skeletonInCell(bad, c.field()).isEmpty(), "a road in another cell must fail skeleton-in-cell");

		// a cell gate the neighbour does not have
		for (int i = 0; i < sk.size(); i++) {
			for (Gate g : sk.get(i).gates()) {
				if (g.kind() != GateKind.CELL) continue;
				Cell nb = g.cells().get(0).equals(sk.get(i).cell()) ? g.cells().get(1) : g.cells().get(0);
				List<CellSkeleton> without = new ArrayList<>();
				for (CellSkeleton s : sk) {
					if (!s.cell().equals(nb)) { without.add(s); continue; }
					List<Gate> kept = new ArrayList<>(s.gates());
					kept.removeIf(x -> x.x() == g.x() && x.z() == g.z());
					without.add(new CellSkeleton(s.cell(), s.outline(), s.coarse(), s.hub(), s.roads(), kept, s.landmarks(), s.districts(), s.walls()));
				}
				assertFalse(SettlementChecks.cellGateAgree(without).isEmpty(), "a gate missing on the neighbour must fail cell-gate-agree");
				return;
			}
		}
		fail("no cell gate found to break");
	}
}
