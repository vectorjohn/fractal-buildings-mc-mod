package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.terrain.Grading;
import dev.fractalbuildings.core.terrain.GroundBox;
import dev.fractalbuildings.core.terrain.LatticePath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Terrain response (TR 5.3): quays along the water of the waterfront quarter, bridge decks. */
class WaterfrontTest {
	record Quay(String district, Street street) {}

	static List<Quay> quays(DistrictsTest.Planned p) {
		List<Quay> out = new ArrayList<>();
		for (Districts.Detail d : p.all()) for (Street s : d.plan().streets()) if (s.kind() == RoadKind.QUAY) out.add(new Quay(d.plan().district().id(), s));
		return out;
	}

	/** Lowest water surface within 14 blocks of a column (MAX_VALUE when none). */
	static int waterNear(TerrainProbe exact, int x, int z) {
		int best = Integer.MAX_VALUE;
		for (int dz = -14; dz <= 14; dz += 2) for (int dx = -14; dx <= 14; dx += 2) if (exact.waterDepth(x + dx, z + dz) > 0) best = Math.min(best, exact.surfaceY(x + dx, z + dz));
		return best;
	}

	/** The T2 gate windows with water in the city: a river-and-lake city, a coastal one and a jungle one. */
	static final Object[][] GATES = {
		{"gate-temperate-city", 987654321L, new Pt(1280, -9600), Tier.CITY, 477},
		{"gate-jungle-savanna", 987654321L, new Pt(-640, -3712), Tier.CITY, 790},
		{"gate-border-desert-jungle", 987654321L, new Pt(7872, -7744), Tier.CITY, 577},
		{"gate-desert-badlands", 987654321L, new Pt(6720, -6784), Tier.CITY, 827}};

	static CityPlanner.City gateCity(Object[] g, TerrainProbe[] exactOut) throws Exception {
		var r = dev.fractalbuildings.core.probe.RecordedProbe.read(java.nio.file.Path.of("../fixtures/terrain/" + g[0] + ".json.gz"));
		var f = new dev.fractalbuildings.core.region.RegionField((Long) g[1], 1024);
		Pt centre = (Pt) g[2];
		Cell claimant = CellGeometry.owner(f, centre.x(), centre.z());
		SettlementSite site = new SettlementSite("gate", (Tier) g[3], centre, (Integer) g[4], claimant, List.of(claimant));
		var coarse = dev.fractalbuildings.core.probe.CoarseProbe.of(r, r, 32);
		exactOut[0] = r;
		return CityPlanner.plan(new SkeletonSource.Direct((Long) g[1], new FixedSiteView(site, f), site, coarse), coarse, r, r, SkeletonsTest.GEN);
	}

	@Test
	void realWaterfrontsGetQuaysThatKeepTheirDistanceAndTheirHeight() throws Exception {
		int quays = 0, columns = 0, quayLots = 0, lots = 0;
		java.util.TreeMap<Integer, Integer> rise = new java.util.TreeMap<>();
		for (Object[] g : GATES) {
			TerrainProbe[] ex = new TerrainProbe[1];
			CityPlanner.City city = gateCity(g, ex);
			TerrainProbe exact = ex[0];
			for (DistrictPlan d : city.districts().values()) {
				List<Rect> quayRects = new ArrayList<>();
				for (Street s : d.streets()) {
					if (s.kind() != RoadKind.QUAY) continue;
					quays++;
					assertEquals(Quays.WIDTH, s.width());
					quayRects.addAll(SettlementChecks.footprint(s));
					List<LatticePath.Pt> pts = new ArrayList<>();
					for (Pt pt : s.points()) pts.add(new LatticePath.Pt(pt.x(), pt.z()));
					Grading.RoadGrade gr = Grading.profile(pts, exact);
					for (int k = 0; k < gr.profile().size(); k++) {
						int x = gr.profile().x()[k], z = gr.profile().z()[k], y = gr.profile().y()[k];
						columns++;
						assertFalse(gr.profile().bridge()[k], g[0] + " quay " + s.id() + " wets its feet at " + x + "," + z);
						int w = waterNear(exact, x, z);
						if (w == Integer.MAX_VALUE) continue;
						rise.merge(y - w, 1, Integer::sum);
						assertTrue(y - w >= -2 && y - w <= Quays.MAX_RISE + Grading.MAX_CUT_FILL, g[0] + " quay " + s.id() + " at " + x + "," + z + " is at y " + y + " over water at " + w);
					}
				}
				for (LotBrief l : d.lots()) {
					lots++;
					if (l.builds() && TerrainStats.frontsAny(l, quayRects)) quayLots++;
				}
			}
			assertEquals(List.of(), CityPlanner.check(city).stream().filter(v -> v.invariant().equals("tile-border-agree")).toList(), g[0].toString());
		}
		System.out.println("quay height over the nearest water surface (columns): " + rise);
		System.out.println(GATES.length + " gate cities: " + quays + " quay pieces, " + columns + " columns, " + quayLots + " of " + lots + " lots front one");
		assertTrue(quays >= 40, "quay pieces: " + quays);
		assertTrue(quayLots >= 30, "lots fronting a quay: " + quayLots);
	}

	@Test
	void flatLandHasNoQuays() {
		SkeletonsTest.Case base = SkeletonsTest.syntheticCase(1);
		SkeletonsTest.Case dry = new SkeletonsTest.Case(base.seed(), base.field(), base.site(), "flat:70");
		SkeletonSource src = new SkeletonSource.Direct(dry.seed(), new FixedSiteView(dry.site(), dry.field()), dry.site(), dry.probe());
		DistrictsTest.Planned p = new DistrictsTest.Planned(dry, src, ProbeSpecs.parse(dry.spec()).terrain(), dry.plan());
		assertEquals(0, quays(p).size());
	}

	/** A promenade is a root of the tile rule (it follows the shore, not a road); a plain street cut off from everything is not. */
	@Test
	void aLoneQuayPassesTheTileRuleAndALoneStreetDoesNot() {
		DistrictInfo info = new DistrictInfo("d0_0", 0, 0, 0, 0, 160, 160, QuarterKind.WATERFRONT.ordinal(), 50);
		for (RoadKind kind : new RoadKind[] {RoadKind.QUAY, RoadKind.STREET}) {
			Street s = new Street("x", kind, 4, List.of(new Pt(60, 80), new Pt(100, 80)), new int[] {64, 64});
			DistrictPlan plan = new DistrictPlan(1, 1, info, List.of(s), List.of(), List.of(), null, List.of());
			boolean flagged = SettlementChecks.roadsConnected(plan).stream().anyMatch(v -> v.invariant().equals("roads-connected"));
			assertEquals(kind == RoadKind.STREET, flagged, kind.toString());
		}
	}

	@Test
	void bridgeDecksAndPiersAreDeckAndQuaysArePaved() throws Exception {
		assertEquals(Role.DECK, GroundBox.Kind.DECK.role());
		assertEquals(Role.DECK, GroundBox.Kind.PIER.role());
		assertEquals(Role.DECK, DistrictScenes.role(GroundBox.Kind.DECK));
		assertEquals(Role.GROUND, DistrictScenes.role(GroundBox.Kind.FILL));
		TerrainProbe[] ex = new TerrainProbe[1];
		CityPlanner.City city = gateCity(GATES[0], ex);
		boolean quayScene = false, bridgeScene = false;
		for (DistrictPlan d : city.districts().values()) {
			boolean hasQuay = d.streets().stream().anyMatch(s -> s.kind() == RoadKind.QUAY), build = d.lots().stream().anyMatch(LotBrief::builds);
			if (!build || (!hasQuay && quayScene)) continue;
			var built = CityPlanner.scene(city, d.district().id(), ex[0], (dev.fractalbuildings.core.probe.RecordedProbe) ex[0], true);
			long paving = built.scene().ground().stream().filter(b -> b.role() == Role.PAVING).count(), deck = built.scene().ground().stream().filter(b -> b.role() == Role.DECK).count();
			if (hasQuay) { assertTrue(paving > 0, d.district().id() + " quay paving"); quayScene = true; }
			if (deck > 0) {
				bridgeScene = true;
				
			}
			if (quayScene && bridgeScene) break;
		}
		assertTrue(quayScene, "a district with a quay");
		assertTrue(bridgeScene, "a district with a bridge deck");
	}
}
