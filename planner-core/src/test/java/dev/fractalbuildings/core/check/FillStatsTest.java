package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CoarseRaster;
import dev.fractalbuildings.core.settlement.DistrictInfo;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.RoadKind;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Street;
import dev.fractalbuildings.core.settlement.Tier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** METROFILL M0: the fill metrics over hand-built plans (no terrain). Site centre 64,64, radius 100, density 100 and dry everywhere. */
class FillStatsTest {
	private static final SettlementSite SITE = new SettlementSite("t", Tier.CITY, new Pt(64, 64), 100, new Cell(0, 0), List.of(new Cell(0, 0)));

	private static CoarseRaster raster() {
		int n = 81; // 9 x 9, step 32 from -64,-64
		int[] z = new int[n], den = new int[n], mask = new int[n];
		Arrays.fill(den, 100);
		Arrays.fill(mask, 1);
		return new CoarseRaster(-64, -64, 32, 9, 9, z, z, z, List.of("plains"), mask, den, z);
	}

	private static Street street(String id, RoadKind k, int... xz) {
		Pt[] ps = new Pt[xz.length / 2];
		for (int i = 0; i < ps.length; i++) ps[i] = new Pt(xz[2 * i], xz[2 * i + 1]);
		return new Street(id, k, 5, List.of(ps), new int[ps.length]);
	}

	private static DistrictPlan tile(int ix, List<Street> streets, LotBrief... lots) {
		return new DistrictPlan(2, 1, new DistrictInfo("d" + ix + "_0", ix, 0, ix * 128, 0, 128, 128, 0, 100), streets, List.of(), List.of(lots), null, List.of());
	}

	private static LotBrief lot(String id, int x, int z, int w, int d) {
		return new LotBrief(id, x, z, w, d, Facing.NORTH, "house", "plain", 1, 2, 1, 0, 64, 100, "build");
	}

	private static FillStats of(DistrictPlan... ds) { return FillStats.of(List.of(ds), SITE, raster()); }

	private static long count(FillStats s, String id) { return s.checks(Tier.CITY).stream().filter(v -> v.invariant().equals(id)).count(); }

	@Test
	void twoParallelStreets10ApartAnd60LongWithNothingBetweenAreOneSliver() {
		FillStats s = of(tile(0, List.of(street("a", RoadKind.STREET, 20, 40, 80, 40), street("b", RoadKind.STREET, 20, 55, 80, 55))));
		assertEquals(1, s.slivers.size());
		assertEquals(1, count(s, FillStats.ROAD_SLIVER));
		assertEquals(65 * 10, s.sliverArea);
		assertTrue(s.emptyShortGapPct() > 0);
	}

	@Test
	void aLotBetweenThemRemovesTheSliver() {
		FillStats s = of(tile(0, List.of(street("a", RoadKind.STREET, 20, 40, 80, 40), street("b", RoadKind.STREET, 20, 55, 80, 55)), lot("l", 20, 43, 60, 10)));
		assertEquals(0, s.slivers.size());
		assertEquals(0, count(s, FillStats.ROAD_SLIVER));
	}

	@Test
	void theSameGapOnly20LongIsNotASliver() {
		FillStats s = of(tile(0, List.of(street("a", RoadKind.STREET, 40, 40, 60, 40), street("b", RoadKind.STREET, 40, 55, 60, 55))));
		assertEquals(0, s.slivers.size());
	}

	@Test
	void frontageCountsTheStreetEdgeAWithALot() {
		// street z 38..42 over x 18..82: edge columns 65 north + 65 south + 5 + 5 at the ends; a lot against 32 of the south ones
		FillStats s = of(tile(0, List.of(street("a", RoadKind.STREET, 20, 40, 80, 40)), lot("l", 18, 43, 32, 10)));
		assertEquals(140, s.all.edge());
		assertEquals(32, s.all.edgeFilled());
		assertEquals(100.0 * 32 / 140, s.all.frontagePct(), 1e-9);
		assertEquals(32, s.frontageByKind.get("street")[0]);
	}

	@Test
	void aStreetListedByTwoTilesIsCountedOnce() {
		Street st = street("a", RoadKind.ARTERIAL, 100, 40, 160, 40);
		FillStats s = of(tile(0, List.of(st)), tile(1, List.of(st)));
		assertEquals(60, s.lengthByKind.get("arterial"), 0.01);
	}

	@Test
	void checksStayQuietBelowACityAndFireOnABareCore() {
		FillStats s = of(tile(0, List.of(street("a", RoadKind.ARTERIAL, 20, 40, 80, 40))));
		assertTrue(s.core().emptyPct() > FillStats.MAX_CORE_EMPTY_PCT);
		assertTrue(s.checks(Tier.TOWN).isEmpty());
		List<Violation> vs = s.checks(Tier.CITY);
		assertEquals(1, count(s, FillStats.CORE_FILL));
		assertEquals(1, count(s, FillStats.ROAD_HIERARCHY)); // all arterial
		assertEquals(2, vs.size());
	}

	@Test
	void deadEndsAreEndsWithNoOtherStreetNear() {
		// a T: the stem ends against the bar (not a dead end); the bar's two ends and the stem's far end are
		FillStats s = of(tile(0, List.of(street("bar", RoadKind.STREET, 20, 40, 100, 40), street("stem", RoadKind.STREET, 60, 42, 60, 90))));
		assertEquals(3, s.deadEnds);
	}

	@Test
	void jsonIsStable() {
		FillStats a = of(tile(0, List.of(street("a", RoadKind.STREET, 20, 40, 80, 40))));
		assertEquals(a.toJson(), of(tile(0, List.of(street("a", RoadKind.STREET, 20, 40, 80, 40)))).toJson());
		assertTrue(a.toJson().startsWith("{\"blockLots\": 0"), a.toJson().substring(0, 40));
	}
}
