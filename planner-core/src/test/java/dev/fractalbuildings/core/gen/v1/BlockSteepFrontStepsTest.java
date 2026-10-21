package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.ir.Facing;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regression: a city block on ground at its grade limit (1 in 16 on both axes) walls its own front steps off; later flights must merge into earlier ones. */
class BlockSteepFrontStepsTest {
	@Test
	void block16OnDiagonalLimitHasReachableEntrances() {
		V1Case c = new V1Case(89, "block", "plain", 100, 80, 31, -51, Facing.SOUTH, "plane:78:1:1:16");
		var rq = c.request();
		var scene = new GeneratorV1().generate(rq);
		assertEquals(List.of(), Invariants.check(scene, rq.terrain()), c.replay());
	}
}
