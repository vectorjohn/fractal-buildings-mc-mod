package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** DOOR2: a 2-wide door (test-wide-door style) on sloped ground must still have steps that reach the ground. */
class WideDoorStepsTest {
	static final List<String> STYLES = List.of("test-wide-door");

	@Test
	void wideDoorsReachTheGroundOnEveryType() {
		List<String> bad = java.util.Collections.synchronizedList(new ArrayList<>());
		for (BuildingType t : Types.all()) {
			int lots = t.limits().testLots().size();
			IntStream.range(0, lots * Integer.getInteger("fb.doorSeeds", 40)).parallel().forEach(i -> {
				V1Case c = V1Case.draw(t, i % lots, i / lots, STYLES);
				GenRequest rq = c.request();
				try {
					var v = Invariants.check(new GeneratorV1().generate(rq), rq.terrain());
					if (!v.isEmpty()) bad.add(v.get(0).invariant() + " " + v.get(0).message() + " :: " + c.replay());
				} catch (IllegalArgumentException e) {
					if (!V1PropertyTest.isPolicyRejection(e, t)) bad.add(e + " :: " + c.replay());
				}
			});
		}
		assertEquals(List.of(), bad.stream().sorted().limit(10).toList(), bad.size() + " failures");
	}
}
