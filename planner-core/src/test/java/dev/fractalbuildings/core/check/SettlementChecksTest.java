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
}
