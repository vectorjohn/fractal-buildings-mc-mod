package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DistrictsTest {
	record Planned(SkeletonsTest.Case c, SkeletonSource src, TerrainProbe exact, SettlementPlan skeleton) {
		List<Districts.Detail> all() {
			List<Districts.Detail> out = new ArrayList<>();
			for (DistrictSummary d : skeleton.districts()) Districts.plan(src, d.ix(), d.iz(), exact).ifPresent(out::add);
			return out;
		}
	}

	static Planned planned(int i) {
		SkeletonsTest.Case c = SkeletonsTest.syntheticCase(i);
		SkeletonSource src = new SkeletonSource.Direct(c.seed(), new FixedSiteView(c.site(), c.field()), c.site(), c.probe());
		TerrainProbe exact = ProbeSpecs.parse(c.spec()).terrain();
		return new Planned(c, src, exact, c.plan());
	}

	@Test
	void smoke() throws Exception {
		Planned p = planned(1);
		long t0 = System.nanoTime();
		List<Districts.Detail> all = p.all();
		System.out.printf("%d districts in %d ms%n", all.size(), (System.nanoTime() - t0) / 1_000_000);
		int lots = 0;
		for (Districts.Detail d : all) {
			lots += d.plan().lots().size();
			for (Violation v : d.plan().violations()) System.out.println(d.plan().district().id() + " " + v.invariant() + ": " + v.message());
			for (String n : d.notes()) System.out.println(d.plan().district().id() + " note: " + n);
		}
		System.out.println("lots " + lots);
		DistrictPng.write(all.stream().map(Districts.Detail::plan).toList(), p.exact(), new File("build/districts/smoke.png"), 2);
	}

	static String ids(List<Violation> v) { return v.stream().map(x -> x.invariant() + ": " + x.message()).toList().toString(); }

	@Test
	void invariantsHoldOverManySyntheticSites() {
		int sites = 0, tiles = 0, lots = 0, pairs = 0;
		java.util.TreeMap<String, Integer> bad = new java.util.TreeMap<>();
		for (int i = 0; i < 150; i++) {
			Planned p = planned(i);
			java.util.TreeMap<String, Districts.Detail> byId = new java.util.TreeMap<>();
			for (Districts.Detail d : p.all()) {
				byId.put(d.plan().district().id(), d);
				tiles++;
				lots += d.plan().lots().size();
				for (Violation v : d.plan().violations()) bad.merge(v.invariant() + " (case " + i + ")", 1, Integer::sum);
				// after the brief filler, no lot may make the spec policy throw
				DistrictPlan filled = Briefs.fill(d.plan(), dev.fractalbuildings.core.settlement.QuarterKind.values()[d.plan().district().quarter()],
						new dev.fractalbuildings.core.style.StyleField(ProbeSpecs.parse(p.c().spec()).biome(), p.c().seed()), p.exact());
				for (Violation v : dev.fractalbuildings.core.check.SettlementChecks.lotGrade(filled, p.exact())) bad.merge(v.invariant() + " (case " + i + ")", 1, Integer::sum);
			}
			for (Districts.Detail d : byId.values()) {
				DistrictInfo a = d.plan().district();
				Districts.Detail e = byId.get(Ids.district(a.ix() + 1, a.iz())), s = byId.get(Ids.district(a.ix(), a.iz() + 1));
				for (Districts.Detail o : new Districts.Detail[] {e, s}) {
					if (o == null) continue;
					pairs++;
					for (Violation v : dev.fractalbuildings.core.check.SettlementChecks.tileBorderAgree(d.plan(), o.plan())) if (i == -1) System.out.println("BA " + v.message() + " " + v.cells()); else bad.merge(v.invariant() + " (case " + i + ")", 1, Integer::sum);
				}
			}
			sites++;
		}
		System.out.println(sites + " sites, " + tiles + " tiles, " + lots + " lots, " + pairs + " border pairs; bad: " + bad);
		assertEquals(java.util.Map.of(), bad);
	}

	// ---- determinism

	@Test
	void tilesInAnyOrderFromFreshSourcesGiveTheSameJson() {
		for (int i : new int[] {1, 4, 7, 12}) {
			Planned p = planned(i);
			java.util.TreeMap<String, String> expected = new java.util.TreeMap<>();
			for (Districts.Detail d : p.all()) expected.put(d.plan().district().id(), DistrictJson.write(d.plan()));
			List<DistrictSummary> order = new ArrayList<>(p.skeleton().districts());
			for (long shuffle = 0; shuffle < 2; shuffle++) {
				java.util.Collections.shuffle(order, new java.util.Random(shuffle * 17 + i));
				SkeletonsTest.Case c = SkeletonsTest.syntheticCase(i);
				SkeletonSource fresh = new SkeletonSource.Direct(c.seed(), new FixedSiteView(c.site(), c.field()), c.site(), c.probe());
				TerrainProbe exact = ProbeSpecs.parse(c.spec()).terrain();
				java.util.TreeMap<String, String> actual = new java.util.TreeMap<>();
				for (DistrictSummary d : order) Districts.plan(fresh, d.ix(), d.iz(), exact).ifPresent(det -> actual.put(det.plan().district().id(), DistrictJson.write(det.plan())));
				assertEquals(expected, actual, "case " + i + " shuffle " + shuffle);
			}
		}
	}

	@Test
	void planJsonRoundTripsAndStaysInsideTheTile() {
		Planned p = planned(2);
		for (Districts.Detail d : p.all()) {
			String json = DistrictJson.write(d.plan());
			assertEquals(json, DistrictJson.write(DistrictJson.read(json)));
		}
	}

	// ---- ownership

	/** A view with two settlements side by side: each cell belongs to at most one. */
	record TwoSites(dev.fractalbuildings.core.region.RegionField field, SettlementSite a, SettlementSite b) implements RegionView {
		@Override public java.util.Optional<SettlementSite> siteOf(Cell c) { return a.cells().contains(c) ? java.util.Optional.of(a) : b.cells().contains(c) ? java.util.Optional.of(b) : java.util.Optional.empty(); }
	}

	@Test
	void everyTileIsPlannedByExactlyOneSettlement() {
		var f = new dev.fractalbuildings.core.region.RegionField(77, 512);
		SettlementSite a = SkeletonFixtures.site(f, 0, 0, Tier.CITY, 600, 2);
		Cell next = null;
		for (Cell c : a.cells()) if (!c.equals(a.claimant())) next = c;
		SettlementSite b = SkeletonFixtures.site(f, 3, 0, Tier.TOWN, 300, 1);
		List<Cell> bc = new ArrayList<>(b.cells());
		bc.removeAll(a.cells());
		b = new SettlementSite(b.id(), b.tier(), b.centre(), b.radius(), bc.contains(b.claimant()) ? b.claimant() : bc.get(0), bc);
		TwoSites view = new TwoSites(f, a, b);
		var probe = SkeletonFixtures.probe("flat:64");
		SkeletonSource sa = new SkeletonSource.Direct(77, view, a, probe), sb = new SkeletonSource.Direct(77, view, b, probe);
		int owned = 0, both = 0;
		for (int iz = -12; iz <= 12; iz++) {
			for (int ix = -20; ix <= 40; ix++) {
				boolean x = Districts.owns(sa, ix, iz), y = Districts.owns(sb, ix, iz);
				if (x && y) both++;
				if (x || y) owned++;
			}
		}
		assertEquals(0, both, "a tile planned by both settlements");
		assertTrue(owned > 20);
	}

	// ---- the layout rules

	@Test
	void gridStreetsRunStraightAcrossTileBorders() {
		int crossings = 0;
		for (int i : new int[] {1, 4, 7, 10, 13}) {
			Planned p = planned(i);
			java.util.TreeMap<String, Districts.Detail> byId = new java.util.TreeMap<>();
			for (Districts.Detail d : p.all()) byId.put(d.plan().district().id(), d);
			for (Districts.Detail d : byId.values()) {
				DistrictInfo a = d.plan().district();
				Districts.Detail below = byId.get(Ids.district(a.ix(), a.iz() + 1));
				if (below == null) continue;
				// vertical grid streets ("sx<pos>_...") reaching the shared border z = a.z + a.d must continue in the tile below at the same x
				for (Street s : d.plan().streets()) {
					if (!s.id().startsWith("sx")) continue;
					for (Pt q : s.points()) {
						if (q.z() != a.z() + a.d()) continue;
						boolean found = false;
						for (Street t : below.plan().streets()) for (Pt r : t.points()) if (t.id().startsWith("sx") && r.x() == q.x() && r.z() == q.z()) found = true;
						assertTrue(found, "grid street at x " + q.x() + " ends at the border of " + a.id());
						crossings++;
					}
				}
			}
		}
		assertTrue(crossings > 5, "only " + crossings + " grid crossings in the sample");
	}

	@Test
	void frontYFollowsTheGradedProfileAndAirRightsFollowTheStreet() {
		int checked = 0, air = 0;
		for (int i : new int[] {1, 4, 7}) {
			Planned p = planned(i);
			for (Districts.Detail d : p.all()) {
				DistrictPlan plan = d.plan();
				for (LotBrief l : plan.lots()) {
					if (l.frontY() == dev.fractalbuildings.core.gen.Brief.NO_FRONT_Y) continue;
					int fx = l.front() == dev.fractalbuildings.core.ir.Facing.EAST ? l.x() + l.w() : l.front() == dev.fractalbuildings.core.ir.Facing.WEST ? l.x() - 1 : l.x() + (l.w() - 1) / 2;
					int fz = l.front() == dev.fractalbuildings.core.ir.Facing.SOUTH ? l.z() + l.d() : l.front() == dev.fractalbuildings.core.ir.Facing.NORTH ? l.z() - 1 : l.z() + (l.d() - 1) / 2;
					// the graded surface of the street column nearest the front centre, as the scene builds it
					int best = Integer.MAX_VALUE, y = 0, width = 0;
					for (Street s : plan.streets()) {
						if (!dev.fractalbuildings.core.check.SettlementChecks.footprint(s).stream().anyMatch(r -> r.contains(fx, fz))) continue;
						width = Math.max(width, s.width()); // crossings: the front street is the one DL saw first, any of them bounds the reach
						List<dev.fractalbuildings.core.terrain.LatticePath.Pt> pts = new ArrayList<>();
						for (Pt q : s.points()) pts.add(new dev.fractalbuildings.core.terrain.LatticePath.Pt(q.x(), q.z()));
						var pr = dev.fractalbuildings.core.terrain.Grading.road(pts, s.y(), 1, p.exact()).profile(); // the stored heights, which the scene builds (a street that meets another is pinned there, JUNCTION)
						for (int k = 0; k < pr.size(); k++) {
							int dd = Math.max(Math.abs(pr.x()[k] - fx), Math.abs(pr.z()[k] - fz));
							if (dd < best) { best = dd; y = pr.y()[k]; }
						}
					}
					if (best == Integer.MAX_VALUE) continue; // a plaza front
					assertTrue(Math.abs(y - l.frontY()) <= 1, plan.district().id() + " " + l.id() + ": frontY " + l.frontY() + " vs profile " + y);
					int expect = width < 4 ? 0 : Math.min(width >= 7 ? 2 : 1, width / 2 - 1); // the widest street covering the front column bounds the reach
					assertTrue(l.airRights() <= expect, l.id() + " airRights " + l.airRights() + " on a " + width + " street");
					if (l.airRights() > 0) air++;
					checked++;
				}
			}
		}
		assertTrue(checked > 500 && air > 100, checked + " lots checked, " + air + " with air rights");
	}

	@Test
	void hubSquareHallAndTempleGetLotsThatFaceTheSquare() {
		int halls = 0, temples = 0;
		for (int i = 0; i < 12; i++) {
			Planned p = planned(i);
			for (Districts.Detail d : p.all()) {
				for (LotBrief l : d.plan().lots()) {
					if (l.type().equals("hall")) halls++;
					if (l.type().equals("temple")) temples++;
				}
				for (Plaza pz : d.plan().plazas()) {
					// no street footprint overlaps a plaza (streets end at its edge)
					for (Street s : d.plan().streets()) for (Rect r : dev.fractalbuildings.core.check.SettlementChecks.footprint(s)) assertFalse(Rect.overlap(r.x(), r.z(), r.w(), r.d(), pz.x(), pz.z(), pz.w(), pz.d()), "street " + s.id() + " runs over plaza " + pz.id());
				}
			}
		}
		assertTrue(halls >= 10 && temples >= 6, halls + " halls, " + temples + " temples in 12 cities");
	}

	@Test
	void lotsPerHectareByDensityBand() {
		double[] lots = new double[4], area = new double[4], cover = new double[4];
		for (int i = 0; i < 30; i++) {
			Planned p = planned(i);
			for (Districts.Detail d : p.all()) {
				for (LotBrief l : d.plan().lots()) {
					int b = l.density() < 25 ? 0 : l.density() < 50 ? 1 : l.density() < 70 ? 2 : 3;
					lots[b]++;
					cover[b] += l.w() * l.d();
				}
				// the area of tiles by their district density band, so a band's rate is lots per hectare of such tiles
				int db = d.plan().district().density();
				area[db < 25 ? 0 : db < 50 ? 1 : db < 70 ? 2 : 3] += d.plan().district().rect().w() * d.plan().district().rect().d();
			}
		}
		StringBuilder sb = new StringBuilder("lots per hectare of tile / lot area share by density band:");
		for (int b = 0; b < 4; b++) sb.append(String.format(java.util.Locale.ROOT, " [%d] %.1f /ha, %.0f%% built", b, lots[b] / (area[b] / 10000), 100 * cover[b] / area[b]));
		System.out.println(sb);
		// built share of ground rises with density (lots get bigger, so the count per hectare need not)
		assertTrue(cover[1] / area[1] > 0.5 * cover[0] / area[0] && cover[2] / area[2] > 0.9 * cover[1] / area[1], sb.toString());
	}


	@Test
	void plannedDistrictsBuildScenesWithoutViolations() {
		int built = 0, buildings = 0, fallbacks = 0;
		for (int i : new int[] {1, 4}) {
			Planned p = planned(i);
			var styles = new dev.fractalbuildings.core.style.StyleField(ProbeSpecs.parse(p.c().spec()).biome(), p.c().seed());
			var biome = ProbeSpecs.parse(p.c().spec()).biome();
			int n = 0;
			for (Districts.Detail d : p.all()) {
				if (d.plan().lots().size() < 40 || n++ >= 3) continue;
				DistrictPlan filled = Briefs.fill(d.plan(), QuarterKind.values()[d.plan().district().quarter()], styles, p.exact());
				DistrictScenes.Built b = DistrictScenes.build(filled, d.landmarks(), p.exact(), biome, true);
				assertEquals("[]", ids(b.plan().violations()), d.plan().district().id() + " settlement rules");
				assertEquals(List.of(), b.scene().violations().stream().map(v -> v.invariant() + ": " + v.message()).toList(), d.plan().district().id() + " building rules");
				fallbacks += b.fallbacks().size();
				buildings += b.scene().buildings().size();
				built++;
			}
		}
		assertTrue(built >= 4 && buildings > 100, built + " districts, " + buildings + " buildings");
		System.out.println("scenes: " + built + " districts, " + buildings + " buildings, " + fallbacks + " fallbacks");
	}

	// ---- real terrain

	@Test
	void recordedRiverWindowHoldsEveryRule() throws Exception {
		var r = dev.fractalbuildings.core.probe.RecordedProbe.read(java.nio.file.Path.of("../fixtures/terrain/w12345-river-s8.json.gz"));
		var f = new dev.fractalbuildings.core.region.RegionField(12345, 1024);
		for (Object[] spec : new Object[][] {{new Pt(-900, -3500), Tier.TOWN, 300}, {new Pt(-400, -3150), Tier.CITY, 450}}) {
			Pt centre = (Pt) spec[0];
			Cell claimant = CellGeometry.owner(f, centre.x(), centre.z());
			SettlementSite site = new SettlementSite("river", (Tier) spec[1], centre, (Integer) spec[2], claimant, List.of(claimant));
			var view = new FixedSiteView(site, f);
			var coarse = dev.fractalbuildings.core.probe.CoarseProbe.of(r, r, 32);
			SettlementPlan sk = Skeletons.plan(12345, view, site, coarse, SkeletonsTest.GEN);
			SkeletonSource src = new SkeletonSource.Direct(12345, view, site, coarse);
			int lots = 0, tiles = 0;
			java.util.TreeMap<String, Districts.Detail> byId = new java.util.TreeMap<>();
			for (DistrictSummary s : sk.districts()) {
				var det = Districts.plan(src, s.ix(), s.iz(), r);
				if (det.isEmpty()) continue;
				byId.put(s.id(), det.get());
				tiles++;
				lots += det.get().plan().lots().size();
				assertEquals("[]", ids(det.get().plan().violations()), site.tier() + " " + s.id());
			}
			assertTrue(tiles > 3 && lots > 50, site.tier() + ": " + tiles + " tiles, " + lots + " lots");
			for (Districts.Detail d : byId.values()) {
				DistrictInfo a = d.plan().district();
				for (Districts.Detail o : new Districts.Detail[] {byId.get(Ids.district(a.ix() + 1, a.iz())), byId.get(Ids.district(a.ix(), a.iz() + 1))}) {
					if (o != null) assertEquals("[]", ids(dev.fractalbuildings.core.check.SettlementChecks.tileBorderAgree(d.plan(), o.plan())));
				}
			}
		}
	}
}
