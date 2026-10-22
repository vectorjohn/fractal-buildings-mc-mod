package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.settlement.DistrictInfo;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.Landmark;
import dev.fractalbuildings.core.settlement.LandmarkKind;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.settlement.Plaza;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.RoadKind;
import dev.fractalbuildings.core.settlement.SampleSettlement;
import dev.fractalbuildings.core.settlement.Street;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementChecksTest {
	private static LotBrief lot(String id, int x, int z, int w, int d) {
		return new LotBrief(id, x, z, w, d, Facing.SOUTH, "house", "plain", 1, 2, 1, 0, 64, 20, "build");
	}

	private static DistrictPlan plan(List<Street> streets, List<Plaza> plazas, LotBrief... lots) {
		return new DistrictPlan(2, 1, new DistrictInfo("d0_0", 0, 0, 0, 0, 128, 128, 0, 20), streets, plazas, List.of(lots), null, List.of());
	}

	private static List<String> ids(List<Violation> v) { return v.stream().map(Violation::invariant).distinct().toList(); }

	@Test
	void cleanPlanPasses() {
		DistrictPlan p = plan(List.of(), List.of(), lot("a", 0, 0, 20, 20), lot("b", 20, 0, 20, 20), lot("c", 0, 20, 20, 20));
		assertEquals(List.of(), SettlementChecks.district(p, List.of()), "touching lots are disjoint");
	}

	@Test
	void sampleDistrictsPass() {
		var s = SampleSettlement.sample();
		for (DistrictPlan d : s.districts().values()) assertEquals(List.of(), SettlementChecks.district(d, s.settlement().landmarks()));
	}

	@Test
	void lotOutsideItsTileFails() {
		DistrictPlan p = plan(List.of(), List.of(), lot("a", 120, 0, 20, 20));
		assertEquals(List.of("lot-in-tile"), ids(SettlementChecks.lotInTile(p)));
		assertEquals(List.of("lot-in-tile"), ids(SettlementChecks.district(p, List.of())));
		assertTrue(SettlementChecks.lotInTile(p).get(0).message().contains("a"));
	}

	@Test
	void overlapsFail() {
		assertEquals(List.of("lots-disjoint"), ids(SettlementChecks.district(plan(List.of(), List.of(), lot("a", 0, 0, 20, 20), lot("b", 19, 5, 20, 20)), List.of())));
		// street: a 5 wide street along z = 30 covers z 28..32
		Street st = new Street("s", RoadKind.STREET, 5, List.of(new Pt(0, 30), new Pt(100, 30)), new int[] {64, 64});
		assertEquals(List.of(), SettlementChecks.district(plan(List.of(st), List.of(), lot("a", 0, 0, 20, 28)), List.of()));
		assertEquals(List.of("lots-disjoint"), ids(SettlementChecks.district(plan(List.of(st), List.of(), lot("a", 0, 0, 20, 29)), List.of())));
		// a diagonal street
		Street dg = new Street("g", RoadKind.LANE, 3, List.of(new Pt(0, 60), new Pt(40, 100)), new int[] {64, 64});
		assertEquals(List.of("lots-disjoint"), ids(SettlementChecks.district(plan(List.of(dg), List.of(), lot("a", 18, 78, 4, 4)), List.of())));
		assertEquals(List.of(), SettlementChecks.district(plan(List.of(dg), List.of(), lot("a", 30, 60, 4, 4)), List.of()));
		// plaza and landmark
		Plaza pz = new Plaza("p", "market", 50, 50, 10, 10, 64);
		assertEquals(List.of("lots-disjoint"), ids(SettlementChecks.district(plan(List.of(), List.of(pz), lot("a", 55, 55, 20, 20)), List.of())));
		Landmark lm = new Landmark("l0", LandmarkKind.TEMPLE, 80, 80, 10, 10, "d0_0");
		assertEquals(List.of("lots-disjoint"), ids(SettlementChecks.district(plan(List.of(), List.of(), lot("a", 85, 85, 20, 20)), List.of(lm))));
		assertEquals(List.of(), SettlementChecks.district(plan(List.of(), List.of(), lot("a", 90, 90, 20, 20)), List.of(lm)));
	}

	// ---- district detail rules (DL): each fails on a deliberately broken plan

	private static Street street(String id, RoadKind k, int w, Pt... pts) {
		int[] y = new int[pts.length];
		java.util.Arrays.fill(y, 64);
		return new Street(id, k, w, List.of(pts), y);
	}

	@Test
	void lotFrontageNeedsStreetAlongTheFront() {
		Street st = street("s", RoadKind.STREET, 5, new Pt(0, 30), new Pt(100, 30)); // rows 28..32
		LotBrief ok = new LotBrief("a", 10, 8, 20, 20, Facing.SOUTH, "house", "plain", 1, 2, 1, 0, 64, 20, "build"); // bottom edge z 28 touches
		assertEquals(List.of(), SettlementChecks.lotFrontage(plan(List.of(st), List.of(), ok)));
		LotBrief gap = new LotBrief("b", 10, 4, 20, 20, Facing.SOUTH, "house", "plain", 1, 2, 1, 0, 64, 20, "build"); // two rows short of the street
		assertEquals(List.of("lot-frontage"), ids(SettlementChecks.lotFrontage(plan(List.of(st), List.of(), gap))));
		LotBrief wrongSide = new LotBrief("c", 10, 8, 20, 20, Facing.NORTH, "house", "plain", 1, 2, 1, 0, 64, 20, "build");
		assertEquals(List.of("lot-frontage"), ids(SettlementChecks.lotFrontage(plan(List.of(st), List.of(), wrongSide))));
		// a plaza counts
		Plaza pz = new Plaza("p", "market", 0, 30, 40, 10, 64);
		LotBrief onPlaza = new LotBrief("d", 10, 10, 20, 20, Facing.SOUTH, "house", "plain", 1, 2, 1, 0, 64, 20, "build");
		assertEquals(List.of(), SettlementChecks.lotFrontage(plan(List.of(), List.of(pz), onPlaza)));
	}

	@Test
	void lotDryAndLotGradeFail() {
		LotBrief l = new LotBrief("a", 10, 10, 20, 20, Facing.SOUTH, "house", "plain", 1, 2, 1, 0, 64, 20, "build");
		DistrictPlan p = plan(List.of(), List.of(), l);
		assertEquals(List.of(), SettlementChecks.lotDry(p, (x, z) -> 64));
		dev.fractalbuildings.core.probe.TerrainProbe wet = new dev.fractalbuildings.core.probe.TerrainProbe() {
			@Override public int floorY(int x, int z) { return 60; }
			@Override public int waterDepth(int x, int z) { return x == 15 && z == 15 ? 3 : 0; }
		};
		assertEquals(List.of("lot-dry"), ids(SettlementChecks.lotDry(p, wet)));
		// a block on a 1:1 slope, and a tower that is too big for its lot
		LotBrief block = new LotBrief("b", 10, 10, 60, 60, Facing.SOUTH, "block", "plain", 2, 4, 15, 0, 64, 80, "build");
		assertEquals(List.of("lot-grade"), ids(SettlementChecks.lotGrade(plan(List.of(), List.of(), block), (x, z) -> 64 + x)));
		assertEquals(List.of(), SettlementChecks.lotGrade(plan(List.of(), List.of(), block), (x, z) -> 64));
		LotBrief tiny = new LotBrief("c", 10, 10, 6, 6, Facing.SOUTH, "tower", "plain", 4, 6, 1, 0, 64, 20, "build");
		assertEquals(List.of("lot-grade"), ids(SettlementChecks.lotGrade(plan(List.of(), List.of(), tiny), (x, z) -> 64)));
	}

	@Test
	void roadsConnectedFailsForAnIslandStreet() {
		Street border = street("a", RoadKind.STREET, 5, new Pt(0, 64), new Pt(40, 64)); // reaches the tile's west edge
		Street joined = street("b", RoadKind.LANE, 3, new Pt(40, 64), new Pt(40, 90));
		Street island = street("i", RoadKind.LANE, 3, new Pt(80, 100), new Pt(80, 120));
		assertEquals(List.of(), SettlementChecks.roadsConnected(plan(List.of(border, joined), List.of())));
		assertEquals(List.of("roads-connected"), ids(SettlementChecks.roadsConnected(plan(List.of(border, joined, island), List.of()))));
		Street road = street("r", RoadKind.MAIN, 5, new Pt(77, 100), new Pt(77, 110)); // a skeleton road anchors what touches it
		assertEquals(List.of(), SettlementChecks.roadsConnected(plan(List.of(border, joined, island, road), List.of())));
	}

	@Test
	void tileBorderAgreeFailsWhenOnlyOneSideHasTheStreet() {
		DistrictPlan west = new DistrictPlan(2, 1, new DistrictInfo("d0_0", 0, 0, 0, 0, 128, 128, 0, 20), List.of(street("s", RoadKind.LANE, 3, new Pt(100, 50), new Pt(140, 50))), List.of(), List.of(), null, List.of());
		DistrictPlan eastSame = new DistrictPlan(2, 1, new DistrictInfo("d1_0", 1, 0, 128, 0, 128, 128, 0, 20), List.of(street("s", RoadKind.LANE, 3, new Pt(100, 50), new Pt(140, 50))), List.of(), List.of(), null, List.of());
		DistrictPlan eastNone = new DistrictPlan(2, 1, new DistrictInfo("d1_0", 1, 0, 128, 0, 128, 128, 0, 20), List.of(), List.of(), List.of(), null, List.of());
		assertEquals(List.of(), SettlementChecks.tileBorderAgree(west, eastSame));
		assertEquals(List.of("tile-border-agree"), ids(SettlementChecks.tileBorderAgree(west, eastNone)));
		assertEquals(List.of(), SettlementChecks.tileBorderAgree(west, west)); // not neighbours: nothing to compare
	}


	// ---- whole-city rules (D0)

	private static DistrictPlan tile(int ix, int iz, List<Street> streets, LotBrief... lots) {
		return new DistrictPlan(2, 1, new DistrictInfo("d" + ix + "_" + iz, ix, iz, ix * 128, iz * 128, 128, 128, 0, 20), streets, List.of(), List.of(lots), null, List.of());
	}

	private static LotBrief frontLot(String id, int x, int z) { return lot(id, x, z, 10, 10); } // faces south: z + 10 is its street row

	@Test
	void cityRoadsConnectedFlagsALotOnAPieceThatMissesTheHub() {
		Street main = street("m", RoadKind.MAIN, 5, new Pt(10, 64), new Pt(200, 64)); // across both tiles, through the hub
		Street dead = street("x", RoadKind.LANE, 3, new Pt(40, 110), new Pt(90, 110));  // a lane on its own
		LotBrief onMain = frontLot("a", 20, 54), onDead = frontLot("b", 50, 100);
		Pt hub = new Pt(100, 64);
		assertEquals(List.of(), SettlementChecks.cityRoadsConnected(List.of(tile(0, 0, List.of(main), onMain), tile(1, 0, List.of(main))), hub));
		// a lot fronting the lane that touches nothing: one violation naming the lot
		var v = SettlementChecks.cityRoadsConnected(List.of(tile(0, 0, List.of(main, dead), onMain, onDead), tile(1, 0, List.of(main))), hub);
		assertEquals(List.of("roads-connected"), ids(v));
		assertTrue(v.get(0).message().contains("lot b"), v.get(0).message());
		// the same lane without a lot is a detached road (ROADS2): counted as a stray and reported as roads-attached, not roads-connected
		var r = SettlementChecks.cityRoads(List.of(tile(0, 0, List.of(main, dead), onMain), tile(1, 0, List.of(main))), hub);
		assertEquals(List.of("roads-attached"), ids(r.violations()));
		assertTrue(r.violations().get(0).message().contains("x 39..91"), r.violations().get(0).message());
		assertEquals(1, r.strays());
		// joining the lane to the road fixes the first case
		Street join = street("j", RoadKind.LANE, 3, new Pt(60, 64), new Pt(60, 110));
		assertEquals(List.of(), SettlementChecks.cityRoadsConnected(List.of(tile(0, 0, List.of(main, dead, join), onMain, onDead), tile(1, 0, List.of(main))), hub));
	}

	@Test
	void cityRoadsConnectedFlagsAHubWithNoStreetNear() {
		Street far = street("m", RoadKind.MAIN, 5, new Pt(10, 10), new Pt(100, 10));
		var v = SettlementChecks.cityRoadsConnected(List.of(tile(0, 0, List.of(far), frontLot("a", 20, 0))), new Pt(60, 100));
		assertEquals(List.of("roads-connected"), ids(v));
		assertTrue(v.get(0).message().contains("hub"));
	}

	@Test
	void cityRoadsConnectedSplitAcrossATileBorderIsTwoPieces() {
		// the road is missing in the east tile: its lot's street is a separate piece from the hub's
		Street west = street("m", RoadKind.MAIN, 5, new Pt(10, 64), new Pt(120, 64));
		Street east = street("m", RoadKind.MAIN, 5, new Pt(150, 64), new Pt(250, 64));
		var v = SettlementChecks.cityRoadsConnected(List.of(tile(0, 0, List.of(west)), tile(1, 0, List.of(east), frontLot("e", 170, 54))), new Pt(60, 64));
		assertEquals(List.of("roads-connected"), ids(v));
		Street bridge = street("b", RoadKind.MAIN, 5, new Pt(120, 64), new Pt(150, 64));
		assertEquals(List.of(), SettlementChecks.cityRoadsConnected(List.of(tile(0, 0, List.of(west, bridge)), tile(1, 0, List.of(east), frontLot("e", 170, 54))), new Pt(60, 64)));
	}

	@Test
	void cityTileBordersChecksEveryNeighbourPair() {
		Street s = street("s", RoadKind.LANE, 3, new Pt(100, 50), new Pt(140, 50));
		assertEquals(List.of(), SettlementChecks.cityTileBorders(List.of(tile(0, 0, List.of(s)), tile(1, 0, List.of(s)))));
		assertEquals(List.of("tile-border-agree"), ids(SettlementChecks.cityTileBorders(List.of(tile(0, 0, List.of(s)), tile(1, 0, List.of())))));
		// a south neighbour is compared too
		Street v = street("v", RoadKind.LANE, 3, new Pt(50, 100), new Pt(50, 140));
		assertEquals(List.of("tile-border-agree"), ids(SettlementChecks.cityTileBorders(List.of(tile(0, 0, List.of(v)), tile(0, 1, List.of())))));
		assertEquals(List.of(), SettlementChecks.cityTileBorders(List.of(tile(0, 0, List.of(v)), tile(2, 2, List.of())))); // not neighbours
	}

	private static DistrictPlan plan(List<Street> streets, List<Plaza> plazas) { return plan(streets, plazas, new LotBrief[0]); }
}
