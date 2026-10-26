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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * The B0 checks over real generator output: 32 V1 high-rise cases (8 seeds x 4 lots). B3a passes the checks it owns; the full pass
 * test below is enabled by B3b (baseline in docs/log/2026-10-25-p7-hr-b0.md, after B3a in docs/log/2026-10-26-p7-hr-b3a.md).
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

	/** B3a's checks pass: lobby, hall access, shaft, windows, lights. Floor repeat (window bands), roof reachability and the stair house's door wait for B3b. */
	@Test
	void generatedHighrisesPassTheChecksOfTheInteriorStep() {
		Map<String, Integer> n = counts(cases());
		for (String id : List.of(HighriseChecks.LOBBY, HighriseChecks.HALL_ACCESS, HighriseChecks.SHAFT, HighriseChecks.ROOM_WINDOW, HighriseChecks.ROOM_LIGHT))
			assertEquals(0, n.get(id), id + " warnings; all: " + n);
		System.out.println("high-rise design warnings left for B3b: " + n);
	}

	@Disabled("until B3b: facade bands (floor-repeat), roof doors and the rail ring (roof-reachable, stairwell-enclosed on the stair house)")
	@Test
	void generatedHighrisesPassEveryDesignCheck() {
		Map<String, Integer> n = counts(cases());
		for (var e : n.entrySet()) assertEquals(0, e.getValue(), e.getKey() + " warnings; all: " + n);
	}
}
