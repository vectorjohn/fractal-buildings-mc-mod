package dev.fractalbuildings.core.region;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.settlement.Tier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class TiersTest {
	@Test
	void weightsSumToOne() {
		double s = 0;
		for (Tier t : Tier.values()) s += t.weight;
		assertEquals(1.0, s, 1e-9);
	}

	@Test
	void frequenciesOver20000CellsMatchTheWeights() {
		EnumMap<Tier, Integer> n = new EnumMap<>(Tier.class);
		for (int cx = 0; cx < 200; cx++) for (int cz = 0; cz < 100; cz++) n.merge(Tiers.tier(12345L, cx - 100, cz - 50), 1, Integer::sum);
		for (Tier t : Tier.values()) {
			double freq = n.getOrDefault(t, 0) / 20000.0;
			assertEquals(t.weight, freq, 0.02, t.json);
		}
	}

	@Test
	void radiiStayInTheirBandsAndMetropolisHasAParetoTailClippedAtMax() {
		List<Integer> metro = new ArrayList<>();
		int clipped = 0;
		for (int cx = 0; cx < 300; cx++) {
			for (int cz = 0; cz < 100; cz++) {
				for (Tier t : Tier.values()) {
					int r = Tiers.radius(5L, cx, cz, t, 4096);
					if (t == Tier.METROPOLIS) {
						assertTrue(r >= 1000 && r <= 4096, "metropolis " + r);
						metro.add(r);
						if (r == 4096) clipped++;
					} else {
						assertTrue(r >= t.minRadius && r <= t.maxRadius, t.json + " " + r);
					}
				}
			}
		}
		Collections.sort(metro);
		int median = metro.get(metro.size() / 2);
		assertEquals(1587, median, 60, "Pareto median 1000 * 2^(1/1.5)");
		assertEquals(StrictMath.pow(1000.0 / 4096, 1.5), clipped / (double) metro.size(), 0.02, "share clipped at maxRadius (Pareto tail)");
		assertEquals(500, Tiers.radius(5L, 1, 1, Tier.METROPOLIS, 500), "maxRadius caps the tail");
	}

	@Test
	void bandsOverlapSoTheLadderHasNoHardSteps() {
		Tier[] t = Tier.values();
		for (int i = 0; i + 1 < t.length; i++) assertTrue(t[i].maxRadius > t[i + 1].minRadius, t[i].json);
	}
}
