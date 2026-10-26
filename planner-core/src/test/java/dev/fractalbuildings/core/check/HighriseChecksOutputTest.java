package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The B0 checks over real generator output: 32 V1 high-rise cases (8 seeds x 4 lots). Baseline in docs/log/2026-10-25-p7-hr-b0.md, after B3a in docs/log/2026-10-26-p7-hr-b3a.md; since B3b every check passes.
 */
class HighriseChecksOutputTest {
	static final int[] LOTS = {20, 28, 36, 44};

	static List<SceneDoc> cases() {
		List<SceneDoc> out = new ArrayList<>();
		for (int lot : LOTS)
			for (long seed = 1; seed <= 8; seed++) {
				ProbeSpecs.Probes p = ProbeSpecs.parse("flat:70");
				out.add(new GeneratorV1().generate(new GenRequest(seed, new Lot("lot0", 0, 0, lot, lot, Facing.NORTH), "highrise", seed % 2 == 0 ? "medieval" : "plain", p.terrain(), p.biome())));
			}
		return out;
	}

	/** First message of each check over the cases, for a failing run. */
	static String firstMessages(List<SceneDoc> scenes) {
		Map<String, String> m = new TreeMap<>();
		for (SceneDoc s : scenes) for (Violation v : HighriseChecks.check(s)) m.putIfAbsent(v.invariant(), v.message());
		return m.toString();
	}

	static Map<String, Integer> counts(List<SceneDoc> scenes) {
		Map<String, Integer> n = new TreeMap<>();
		for (String id : HighriseChecks.IDS) n.put(id, 0);
		for (SceneDoc s : scenes) for (Violation v : HighriseChecks.check(s)) n.merge(v.invariant(), 1, Integer::sum);
		return n;
	}

	@Test
	void checksRunOnGeneratedHighrisesAndAreDeterministic() {
		List<SceneDoc> a = cases();
		assertEquals(32, a.size());
		Map<String, Integer> first = counts(a);
		assertEquals(first, counts(cases()));
		System.out.println("high-rise design warnings over 32 cases: " + first);
	}

	@Test
	void generatedHighrisesPassEveryDesignCheck() {
		List<SceneDoc> a = cases();
		Map<String, Integer> n = counts(a);
		for (var e : n.entrySet()) assertEquals(0, e.getValue(), e.getKey() + " warnings; all: " + n + "; first: " + firstMessages(a));
	}
}
