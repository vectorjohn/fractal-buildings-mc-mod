package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** METROFILL DX: {@link PlanOptions#diagonals()} off gives cities and metropolises with axis streets only; the default changes nothing. */
class DiagonalSwitchTest {
	static boolean diagonalStep(List<Pt> pts) {
		for (int i = 1; i < pts.size(); i++) if (pts.get(i - 1).x() != pts.get(i).x() && pts.get(i - 1).z() != pts.get(i).z()) return true;
		return false;
	}

	@Test
	void defaultOptionsAreTheOldPrograms() {
		for (Tier t : Tier.values()) assertSame(TierPrograms.of(t), TierPrograms.of(t, PlanOptions.DEFAULT));
		assertTrue(TierPrograms.of(Tier.CITY).diagonals());
		assertFalse(TierPrograms.of(Tier.CITY, new PlanOptions(false)).diagonals());
		assertSame(TierPrograms.of(Tier.TOWN), TierPrograms.of(Tier.TOWN, new PlanOptions(false)), "the switch only touches city and metropolis");
	}

	@Test
	void offSkeletonsHaveNoDiagonalRoadAndEveryRuleHolds() {
		PlanOptions off = new PlanOptions(false);
		int cities = 0, defaultDiagonal = 0;
		for (int i = 1; i < 31; i++) {
			SkeletonsTest.Case c = SkeletonsTest.syntheticCase(i);
			if (c.site().tier() == Tier.TOWN) continue;
			cities++;
			SettlementPlan on = c.plan();
			for (Road r : on.roads()) if (diagonalStep(r.points())) { defaultDiagonal++; break; }
			SettlementPlan p = Skeletons.plan(c.seed(), new FixedSiteView(c.site(), c.field()), c.site(), c.probe(), SkeletonsTest.GEN, TierPrograms.of(c.site().tier(), off));
			assertEquals(List.of(), p.violations().stream().map(v -> v.invariant() + ": " + v.message()).toList(), "case " + i);
			for (Road r : p.roads()) assertFalse(diagonalStep(r.points()), "case " + i + " road " + r.id());
		}
		assertTrue(cities >= 15);
		assertTrue(defaultDiagonal >= 5, "the control: with diagonals on, many of these have one (" + defaultDiagonal + ")");
	}

	@Test
	void offDistrictsHaveNoDiagonalStreetAndEveryRuleHoldsInAnyOrder() {
		PlanOptions off = new PlanOptions(false);
		int tiles = 0;
		for (int i : new int[] {2, 5, 8, 11, 14}) {
			SkeletonsTest.Case c = SkeletonsTest.syntheticCase(i);
			FixedSiteView view = new FixedSiteView(c.site(), c.field());
			TerrainProbe exact = ProbeSpecs.parse(c.spec()).terrain();
			TierProgram prog = TierPrograms.of(c.site().tier(), off);
			SettlementPlan sk = Skeletons.plan(c.seed(), view, c.site(), c.probe(), SkeletonsTest.GEN, prog);
			SkeletonSource src = new SkeletonSource.Direct(c.seed(), view, c.site(), c.probe(), prog);
			TreeMap<String, String> forward = new TreeMap<>(), backward = new TreeMap<>();
			List<DistrictSummary> ds = new ArrayList<>(sk.districts());
			for (DistrictSummary s : ds) Districts.plan(src, s.ix(), s.iz(), exact).ifPresent(d -> {
				forward.put(d.plan().district().id(), DistrictJson.write(d.plan()));
				assertEquals(List.of(), d.plan().violations().stream().map(v -> v.invariant() + ": " + v.message()).toList(), d.plan().district().id());
				for (Street st : d.plan().streets()) assertFalse(diagonalStep(st.points()), "case " + c.seed() + " " + d.plan().district().id() + " street " + st.id());
			});
			SkeletonSource fresh = new SkeletonSource.Direct(c.seed(), view, c.site(), c.probe(), prog);
			java.util.Collections.reverse(ds);
			for (DistrictSummary s : ds) Districts.plan(fresh, s.ix(), s.iz(), exact).ifPresent(d -> backward.put(d.plan().district().id(), DistrictJson.write(d.plan())));
			assertEquals(forward, backward, "case " + i);
			tiles += forward.size();
		}
		assertTrue(tiles > 20);
	}
}
