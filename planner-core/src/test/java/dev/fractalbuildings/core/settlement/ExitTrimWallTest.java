package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.style.StyleField;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 6.25 MAINSTUB: a road through a wall gate is never cut short of the gate; the wall gate is a destination. */
class ExitTrimWallTest {
	@Test
	void aCutNeverEndsInsideTheWall() {
		int mattered = 0, checked = 0;
		for (TownProgramTest.Town t : TownProgramTest.towns(60)) {
			if (t.plan().walls().isEmpty()) continue;
			var s = t.setup();
			SkeletonSource src = s.source();
			StyleField styles = new StyleField(s.p().biome(), s.seed());
			java.util.Map<String, DistrictPlan> raw = new java.util.TreeMap<>();
			for (var d : t.plan().districts()) CityPlanner.planTile(src, d.ix(), d.iz(), styles, s.p().terrain()).ifPresent(x -> raw.put(d.id(), x.plan()));
			ExitTrim.Plans plans = (ix, iz) -> raw.get(Ids.district(ix, iz));
			var cuts = ExitTrim.cuts(src, plans);
			for (ExitTrim.Candidate c : ExitTrim.candidates(src)) {
				if (c.walls().isEmpty()) continue;
				List<int[]> cols = ExitTrim.columns(c.road());
				int wall = ExitTrim.wallColumn(cols, c.walls());
				if (wall <= 0) continue;
				checked++;
				var cut = cuts.get(c.road().id());
				if (ExitTrim.lastServed(src, c.road().id(), c.road().width(), cols, plans) < wall) mattered++;
				assertTrue(cut == null || cut.index() >= wall, "road " + c.road().id() + " cut at " + (cut == null ? -1 : cut.index()) + " inside its wall gate at " + wall);
			}
		}
		System.out.println("WALLTRIM checked " + checked + " mattered " + mattered);
		assertTrue(checked > 0, "walled towns with a gateless wall-gate road");
	}
}
