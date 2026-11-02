package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionField;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 6.40 THINDECK round 2 ({@link RoadNetwork#withWater}) on the snowy coast city (seed 12345, recorded terrain): its south quarter (tiles
 * d6_0..d8_3, about 210 lots) reaches the hub only over the bay, by the edge street on x=960 (z 16..160, 24 wet centre columns; tile 5,0
 * west of it is not planned) and the edge street on z=16 (x 800..960, 37 wet). Round 1 dropped every wet edge street and cut the quarter
 * off (875 built lots to 697). Now an edge street over a long wet run (over 20 columns, a lake) is dropped only when nothing else loses
 * its link to the hub, and every wet edge street that stays (one something hangs on, or a short crossing such as the x=960 street south
 * of z=160, 8 wet) is drawn whole inside its planned tile: full width, centre line and piers. The long-run drop is ThinDeckTest.
 */
public class EdgeStreetWaterTest {
	record Coast(SkeletonSource src, TerrainProbe exact, CityPlanner.City city) {}

	static Coast coast() throws Exception { return plan("fly-12345-coast-city.json.gz", 12345, 1152, -256, Tier.CITY, 807); }

	static Coast plan(String file, long seed, int x, int z, Tier tier, int radius) throws Exception {
		RecordedProbe t = RecordedProbe.read(Path.of("../fixtures/terrain/" + file));
		RegionField field = new RegionField(seed, 1024);
		Cell claimant = CellGeometry.owner(field, x, z);
		List<Cell> cells = new ArrayList<>();
		for (int dz = -8; dz <= 8; dz++) for (int dx = -8; dx <= 8; dx++) {
			Cell c = new Cell(claimant.cx() + dx, claimant.cz() + dz);
			long ex = field.siteX(c.cx(), c.cz()) - x, ez = field.siteZ(c.cx(), c.cz()) - z;
			if (c.equals(claimant) || ex * ex + ez * ez <= (long) radius * radius) cells.add(c);
		}
		cells.sort(CellGeometry::compare);
		SettlementSite site = new SettlementSite(Ids.site(claimant.cx(), claimant.cz()), tier, new Pt(x, z), radius, claimant, cells);
		CoarseProbe coarse = CoarseProbe.of(t, t, 32);
		SkeletonSource src = new SkeletonSource.Direct(seed, new FixedSiteView(site, field), site, coarse);
		return new Coast(src, t, CityPlanner.plan(src, coarse, t, t, GeneratorV1.GEN_VERSION));
	}

	static final DistrictGrid.Segment BAY_LINK = new DistrictGrid.Segment(DistrictGrid.AXIS_X, 6, 0, 960, 16, 160);
	static final DistrictGrid.Segment BAY_SPUR = new DistrictGrid.Segment(DistrictGrid.AXIS_X, 6, 1, 960, 160, 304);
	static final DistrictGrid.Segment SHORE_LINK = new DistrictGrid.Segment(DistrictGrid.AXIS_Z, 0, 5, 16, 800, 960);

	@Test
	void wetEdgeStreetsTheQuarterHangsOnAndShortCrossingsAreKeptWholeInsideTheirTile() throws Exception {
		Coast c = coast();
		RoadNetwork coarse = c.src().network(), net = c.src().network(c.exact());
		for (DistrictGrid.Segment s : List.of(BAY_LINK, BAY_SPUR, SHORE_LINK)) {
			assertTrue(net.oneSided(s), s + " is on the edge of the planned area");
			assertEquals(RoadKind.STREET, coarse.kind(s), s + " is a street on the coarse lattice");
		}
		assertEquals(RoadKind.STREET, net.kind(BAY_LINK), "the bay street the quarter hangs on stays");
		assertEquals(2, net.inset(BAY_LINK), "drawn on x=962: columns 960..964, all in tile 6,0 (east of the line)");
		assertEquals(RoadKind.STREET, net.kind(SHORE_LINK), "the shore street that links the bay street to the hub stays");
		assertEquals(-3, net.inset(SHORE_LINK), "drawn on z=13: rows 11..15, all in tile 5,-1 (north of the line)");
		assertEquals(RoadKind.STREET, net.kind(BAY_SPUR), "the edge street south of z=160 crosses 8 wet columns: a short crossing, kept");
		assertEquals(2, net.inset(BAY_SPUR), "and drawn whole inside tile 6,1, like the bay street it continues");
	}

	@Test
	void theSouthQuarterKeepsItsLotsAndNoEdgeStreetIsHalfBuiltOverWater() throws Exception {
		Coast c = coast();
		assertEquals(List.of(), CityPlanner.check(c.city()));
		for (String id : List.of("d6_1", "d6_2", "d7_1", "d7_2", "d8_1", "d8_2")) {
			DistrictPlan p = c.city().districts().get(id);
			assertTrue(p != null && p.lots().stream().anyMatch(LotBrief::builds), id + " of the south quarter has built lots (round 1 left it empty)");
		}
		assertEquals(List.of(), SettlementChecks.streetEdgeCut(c.city().districts().values(), c.exact()));
		for (DistrictPlan dp : c.city().districts().values()) {
			var g = DistrictScenes.ground(dp, c.exact(), Tier.CITY);
			assertEquals(List.of(), g.settlement().stream().filter(v -> v.invariant().equals(SettlementChecks.STREET_DECK_UNSUPPORTED)).toList(), dp.district().id());
		}
		edgeStreetsAreDryOrInside(c.src(), c.exact());
	}


	/**
	 * Quays and their lanes reach an edge street drawn inside its tile as they reached it on the border (Districts.nearestAttach): on
	 * world 12345 (64-block dump) the town at -9984,-896 has a wet edge street on z=-1296 drawn on z=-1294 in tile dm63_m8; before the
	 * attach rule knew moved streets, the tile's three quays found no street, were dropped with their lanes, and the tile fell from 38 lots to 6.
	 */
	@Test
	void quaysStillReachAnEdgeStreetDrawnInsideItsTile() throws Exception {
		Coast c = plan("world-12345-s64.json.gz", 12345, -9984, -896, Tier.TOWN, 439);
		var seg = new DistrictGrid.Segment(DistrictGrid.AXIS_Z, -8, -63, -1296, -10080, -9904);
		assertEquals(2, c.src().network(c.exact()).inset(seg), "the edge street is drawn inside tile dm63_m8");
		DistrictPlan p = c.city().districts().get("dm63_m8");
		assertTrue(p.streets().stream().anyMatch(s -> s.kind() == RoadKind.QUAY), "dm63_m8 keeps its quays");
		assertTrue(p.streets().stream().anyMatch(s -> s.kind() == RoadKind.LANE && s.id().startsWith("k")), "and a quay link lane");
		assertTrue(p.lots().size() >= 30, "dm63_m8 lots " + p.lots().size() + " (38 before THINDECK round 2, 6 without the attach rule)");
	}
	/**
	 * Every street the network draws on a one-sided segment is either on its line with a dry centre line (dry edge streets stay as they
	 * were) or moved wholly into its planned tile.
	 */
	public static void edgeStreetsAreDryOrInside(SkeletonSource src, TerrainProbe exact) {
		RoadNetwork net = src.network(exact);
		Arterials art = src.density().arterials();
		int[] b = net.box();
		int edge = 0, inside = 0;
		for (DistrictGrid.Segment s : art.segmentsIn(b[0], b[1], b[0] + b[2] - 1, b[1] + b[3] - 1)) {
			RoadKind k = net.kind(s);
			if (k == null || !net.oneSided(s)) continue;
			edge++;
			int o = net.inset(s), width = k == RoadKind.ARTERIAL ? art.width() : StreetRules.BORDER_WIDTH;
			if (o == 0) {
				for (int[] col : StreetCols.columns(art.points(s, s.from(), s.to()))) assertTrue(exact.waterDepth(col[0], col[1]) == 0, s + ": an edge street on its line over water at " + col[0] + "," + col[1]);
				continue;
			}
			inside++;
			boolean xAxis = s.axis() == DistrictGrid.AXIS_X;
			int kx = xAxis ? s.line() : s.index(), kz = xAxis ? s.index() : s.line();
			boolean high = net.planned(kx, kz);
			int lo = s.fixed() + o - width / 2, hi = lo + width - 1;
			assertTrue(high ? lo == s.fixed() : hi == s.fixed() - 1, s + ": the moved street fills the planned side from the line (" + lo + ".." + hi + ")");
		}
		assertTrue(edge > 0, "the settlement has edge streets");
		System.out.println("[THINDECK] " + edge + " edge streets, " + inside + " moved inside their tile");
	}
}
