package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.region.CellDecision;
import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionPlanner;
import dev.fractalbuildings.core.region.RegionProbe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * MC2 (5.8): cities that the join rule spreads over several region cells, planned from {@link FieldSiteView}: cell by cell in any
 * order, on several threads, with caches that evict constantly, the result is the batch result; gates meet on both sides; no tile is
 * planned by two settlements.
 */
class MultiCellCityTest {
	static final String TERRAIN = "noise:3:70:10:300";

	/** A world where the join rule makes {@code seed}'s settlement at cell (cx, cz) span {@code cells} cells (found by a scan of seeds on this terrain). */
	record World(long seed, int cx, int cz, int cells) {
		static final World METROPOLIS = new World(15, 0, -1, 8);
		static final World CITY4 = new World(40, 0, 1, 4);
		static final World CITY2 = new World(80, 0, 0, 2);
	}

	record Fixture(World w, ProbeSpecs.Probes p, RegionField field, FieldRegionView fv, FieldSiteView view, SettlementSite site, CoarseProbe coarse) {
		static Fixture of(World w, int cache) {
			ProbeSpecs.Probes p = ProbeSpecs.parse(TERRAIN);
			RegionField f = new RegionField(w.seed(), 1024);
			FieldRegionView fv = new FieldRegionView(new RegionPlanner(f, RegionProbe.of(p.terrain(), p.biome())), null, cache);
			FieldSiteView view = new FieldSiteView(fv, f);
			SettlementSite site = view.siteOf(new Cell(w.cx(), w.cz())).orElseThrow();
			return new Fixture(w, p, f, fv, view, site, CoarseProbe.of(p.terrain(), p.biome(), 32));
		}

		SkeletonSource source(int cache) {
			return new SkeletonSource.Direct(w.seed(), view, site, coarse, TierPrograms.of(site.tier()), cache);
		}
	}

	@Test
	void theFixtureWorldsReallyJoinCells() {
		for (World w : List.of(World.METROPOLIS, World.CITY4, World.CITY2)) {
			Fixture f = Fixture.of(w, 8192);
			assertEquals(w.cells(), f.site().cells().size(), "seed " + w.seed());
			// every member answers with the same site
			for (Cell c : f.site().cells()) assertEquals(f.site(), f.view().siteOf(c).orElseThrow(), c.toString());
		}
	}

	/** Skeletons of the member cells built one at a time on fresh views and caches, in shuffled order, equal the batch; so does the assembled plan. */
	@Test
	void skeletonsBuiltCellByCellEqualTheBatch() {
		for (World w : List.of(World.METROPOLIS, World.CITY4)) {
			Fixture batch = Fixture.of(w, 8192);
			SettlementPlan expected = Skeletons.plan(w.seed(), batch.view(), batch.site(), batch.coarse(), GeneratorV1.GEN_VERSION);
			List<Cell> cells = new ArrayList<>(batch.site().cells());
			Collections.shuffle(cells, new Random(w.seed()));
			List<CellSkeleton> alone = new ArrayList<>();
			for (Cell c : cells) {
				Fixture fresh = Fixture.of(w, 2); // a view that forgets almost everything: a pure cache
				SettlementSite s = fresh.view().siteOf(c).orElseThrow();
				assertEquals(batch.site(), s, "the site seen from " + c);
				alone.add(Skeletons.build(w.seed(), fresh.view(), c, fresh.coarse(), new DistrictGrid(w.seed()), TierPrograms.of(s.tier())));
			}
			SettlementPlan got = Skeletons.assemble(w.seed(), batch.view(), batch.site(), batch.coarse(), GeneratorV1.GEN_VERSION, alone);
			assertEquals(SettlementJson.write(expected), SettlementJson.write(got), "seed " + w.seed());
			assertEquals(List.of(), SettlementChecks.cellGateAgree(alone));
			assertEquals(List.of(), SettlementChecks.skeletonInCell(alone, batch.field()));
		}
	}

	/** Every cell gate has a road ending on it on both sides, and the road's other end is on the same side of the border. */
	@Test
	void everyCellGateHasARoadOnBothSides() {
		int gates = 0;
		for (World w : List.of(World.METROPOLIS, World.CITY4, World.CITY2)) {
			Fixture f = Fixture.of(w, 8192);
			SkeletonSource src = f.source(64);
			for (Cell c : f.site().cells()) {
				CellSkeleton s = src.skeleton(c);
				for (Gate g : s.gates()) {
					if (g.kind() != GateKind.CELL) continue;
					gates++;
					boolean road = false;
					for (Road r : s.roads()) {
						Pt end = r.points().get(r.points().size() - 1), start = r.points().get(0);
						if ((end.x() == g.x() && end.z() == g.z()) || (start.x() == g.x() && start.z() == g.z())) road = true;
					}
					assertTrue(road, "cell " + c + " gate " + g.id() + " at " + g.x() + "," + g.z() + " has no road on its side");
				}
			}
		}
		assertTrue(gates >= 6, "cell gates seen: " + gates);
	}

	private static String bytes(CityPlanner.City c) {
		StringBuilder sb = new StringBuilder(SettlementJson.write(c.plan()));
		for (var e : c.districts().entrySet()) sb.append(e.getKey()).append(DistrictJson.write(e.getValue()));
		return sb.toString();
	}

	/** The whole 4-cell city: batch, then every district alone on a fresh source with a tiny cache, shuffled and on 8 threads. */
	@Test
	void districtsOfAJoinedCityPlanTheSameInAnyOrderOnAnyThreadWithEvictingCaches() throws Exception {
		Fixture f = Fixture.of(World.CITY4, 8192);
		CityPlanner.City batch = CityPlanner.plan(f.source(64), f.coarse(), f.p().terrain(), f.p().biome(), GeneratorV1.GEN_VERSION);
		assertTrue(batch.districts().size() > 20, "districts " + batch.districts().size());
		String expected = bytes(batch);
		assertEquals(expected, bytes(CityPlanner.plan(f.source(1), f.coarse(), f.p().terrain(), f.p().biome(), GeneratorV1.GEN_VERSION)), "a source that keeps one skeleton");

		List<DistrictSummary> sums = new ArrayList<>(batch.plan().districts());
		sums.removeIf(d -> !batch.districts().containsKey(d.id()));
		Collections.shuffle(sums, new Random(7));
		var styles = new dev.fractalbuildings.core.style.StyleField(f.p().biome(), f.w().seed());
		SkeletonSource shared = f.source(2); // evicts constantly, shared by every thread
		ExecutorService ex = Executors.newFixedThreadPool(8);
		try {
			List<Future<String>> fs = new ArrayList<>();
			List<String> ids = new ArrayList<>();
			for (DistrictSummary d : sums.subList(0, Math.min(40, sums.size()))) {
				ids.add(d.id());
				fs.add(ex.submit(() -> {
					return DistrictJson.write(CityPlanner.planTileTrimmed(shared, d.ix(), d.iz(), styles, f.p().terrain()).orElseThrow().plan());
				}));
			}
			for (int i = 0; i < ids.size(); i++) assertEquals(DistrictJson.write(batch.districts().get(ids.get(i))), fs.get(i).get(), ids.get(i));
		} finally {
			ex.shutdown();
		}
	}

	/** A tile that straddles a cell border plans the same whichever of the two cells' skeletons was built first. */
	@Test
	void districtsStraddlingACellBorderPlanTheSameWhicheverNeighbourWasBuiltFirst() {
		Fixture f = Fixture.of(World.CITY4, 8192);
		CityPlanner.City batch = CityPlanner.plan(f.source(64), f.coarse(), f.p().terrain(), f.p().biome(), GeneratorV1.GEN_VERSION);
		var styles = new dev.fractalbuildings.core.style.StyleField(f.p().biome(), f.w().seed());
		DistrictGrid grid = new DistrictGrid(f.w().seed());
		int straddling = 0;
		for (DistrictPlan dp : batch.districts().values()) {
			Rect r = dp.district().rect();
			Cell a = CellGeometry.owner(f.field(), r.x(), r.z()), b = CellGeometry.owner(f.field(), r.x() + r.w() - 1, r.z() + r.d() - 1);
			Cell c = CellGeometry.owner(f.field(), r.x() + r.w() - 1, r.z()), d = CellGeometry.owner(f.field(), r.x(), r.z() + r.d() - 1);
			if (a.equals(b) && a.equals(c) && a.equals(d)) continue;
			if (!f.site().cells().contains(a) || !f.site().cells().contains(b) || !f.site().cells().contains(c) || !f.site().cells().contains(d)) continue;
			straddling++;
			List<Cell> order = new ArrayList<>(List.of(a, b, c, d).stream().distinct().sorted(CellGeometry::compare).toList());
			String want = DistrictJson.write(dp);
			for (int rot = 0; rot < order.size(); rot++) {
				SkeletonSource src = f.source(2);
				for (int i = 0; i < order.size(); i++) src.skeleton(order.get((rot + i) % order.size()));
				assertEquals(want, DistrictJson.write(CityPlanner.planTileTrimmed(src, dp.district().ix(), dp.district().iz(), styles, f.p().terrain()).orElseThrow().plan()), dp.district().id() + " with " + order.get(rot) + " first");
			}
			if (straddling >= 6) break;
		}
		assertTrue(straddling >= 3, "straddling tiles checked: " + straddling);
	}

	/** Across the world, every tile is planned by at most one settlement (solo settled cells included), whatever the winners' footprints. */
	@Test
	void noTileIsPlannedByTwoSettlements() {
		int sites = 0, tiles = 0;
		for (long seed : new long[] {15, 40, 62}) {
			World w = new World(seed, 0, 0, 0);
			Fixture f = Fixture.of(w, 8192);
			TreeMap<String, SkeletonSource> sources = new TreeMap<>();
			for (int cz = -5; cz <= 5; cz++) {
				for (int cx = -5; cx <= 5; cx++) {
					var s = f.view().siteOf(new Cell(cx, cz));
					if (s.isPresent() && !sources.containsKey(s.get().id())) sources.put(s.get().id(), new SkeletonSource.Direct(seed, f.view(), s.get(), f.coarse(), TierPrograms.of(s.get().tier()), 4));
				}
			}
			sites += sources.size();
			DistrictGrid grid = new DistrictGrid(seed);
			int x0 = -5 * 1024, x1 = 5 * 1024;
			DistrictGrid.TileId a = grid.tileOf(x0, x0), b = grid.tileOf(x1, x1);
			for (int iz = a.iz(); iz <= b.iz(); iz++) {
				for (int ix = a.ix(); ix <= b.ix(); ix++) {
					List<String> owners = new ArrayList<>();
					for (var e : sources.entrySet()) if (Districts.owns(e.getValue(), ix, iz)) owners.add(e.getKey());
					assertTrue(owners.size() <= 1, "seed " + seed + " tile " + ix + "," + iz + " planned by " + owners);
					tiles++;
				}
			}
		}
		assertTrue(sites >= 20 && tiles > 1000, "sites " + sites + ", tiles " + tiles);
	}

	@Test
	void aSettledCellThatDecidesAloneIsAOneCellSite() {
		Fixture f = Fixture.of(new World(12345, 0, 0, 0), 8192);
		int solo = 0, none = 0, joined = 0;
		for (int cz = -8; cz <= 8; cz++) {
			for (int cx = -8; cx <= 8; cx++) {
				FieldRegionView.Resolved r = f.fv().cell(cx, cz);
				var s = f.view().siteOf(new Cell(cx, cz));
				if (r.site() != null) {
					joined++;
					assertTrue(s.isPresent());
				} else if (r.decision().settled()) {
					solo++;
					SettlementSite site = s.orElseThrow(() -> new AssertionError("settled cell " + r.decision().cx() + "," + r.decision().cz() + " has no site"));
					CellDecision d = r.decision();
					assertEquals(List.of(new Cell(cx, cz)), site.cells());
					assertEquals(new Cell(cx, cz), site.claimant());
					assertEquals(d.tier(), site.tier());
					assertEquals(new Pt(d.centreX(), d.centreZ()), site.centre());
					assertEquals(Ids.site(cx, cz), site.id());
				} else {
					none++;
					assertTrue(s.isEmpty(), "unsettled cell " + cx + "," + cz);
				}
			}
		}
		assertTrue(solo >= 10 && none >= 3 && joined >= 3, "solo " + solo + " none " + none + " joined " + joined);
	}
}
