package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.DistrictSummary;
import dev.fractalbuildings.core.settlement.SettlementJson;
import dev.fractalbuildings.core.settlement.SettlementPlan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** METROFILL M0: the Java ring table agrees with the architect's prototype on a committed fixture. */
class FillStatsParityTest {
	/**
	 * {@code node docs/review/reports/metro-sparse-architect/fill-metrics.js viewer/fixtures/settlement/districts/synth-city}, 2026-10-27:
	 * road%, lot%, plaza%, unbuilt lot%, empty% per ring, with the line that also rasterises the settlement roads (`S.roads`) removed: the Java rasterises the districts' streets only (with the roads the core ring reads road 23.7 plaza 2.0).
	 */
	private static final double[][] NODE = {
			{22.7, 50.4, 2.9, 0.4, 23.6},
			{17.6, 64.2, 0.9, 0.0, 17.2},
			{17.1, 61.9, 0.7, 0.0, 20.4},
			{7.0, 28.7, 0.0, 0.0, 64.3}};

	@Test
	void ringTableIsWithinOnePointOfThePrototype() throws Exception {
		Path dir = Path.of("../viewer/fixtures/settlement/districts/synth-city");
		SettlementPlan plan = SettlementJson.read(Files.readString(dir.resolve("settlement.json")));
		List<DistrictPlan> ds = new ArrayList<>();
		for (DistrictSummary s : plan.districts()) {
			if (s.detail() == null) continue;
			ds.add(DistrictJson.read(Files.readString(dir.resolve(s.detail()))));
		}
		FillStats f = FillStats.of(ds, plan.site(), plan.coarse());
		for (int k = 0; k < 4; k++) {
			FillStats.Zone z = f.rings.get(FillStats.RING_NAMES[k]);
			double[] got = {z.roadPct(), z.lotPct(), z.plazaPct(), z.unbuiltPct(), z.emptyPct()};
			for (int c = 0; c < 5; c++) assertEquals(NODE[k][c], got[c], 1.0, "ring " + k + " column " + c);
		}
	}
}
