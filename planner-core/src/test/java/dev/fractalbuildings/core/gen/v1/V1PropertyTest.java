package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.fail;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Generator v1 through every invariant, for every registered type: {@code propertySeeds} cases per
 * test lot size ({@code -Dfb.propertyScale=N} multiplies them). A type added to {@link Types} is
 * covered without touching this file. A failure prints the replay command for the CLI.
 */
class V1PropertyTest {
	static final int SCALE = Integer.getInteger("fb.propertyScale", 1);

	@TestFactory
	List<DynamicTest> everyTypePassesEveryInvariant() {
		List<DynamicTest> tests = new ArrayList<>();
		for (BuildingType t : Types.all()) tests.add(DynamicTest.dynamicTest(t.id(), () -> run(t)));
		return tests;
	}

	static void run(BuildingType t) {
		int lots = t.limits().testLots().size();
		int per = t.limits().propertySeeds() * SCALE;
		List<String> bad = java.util.Collections.synchronizedList(new ArrayList<>());
		IntStream.range(0, lots * per).parallel().forEach(i -> {
			V1Case c = V1Case.draw(t, i % lots, i / lots);
			GenRequest rq = c.request();
			try {
				SceneDoc s = new GeneratorV1().generate(rq);
				List<Violation> v = Invariants.check(s, rq.terrain());
				if (!v.isEmpty()) bad.add(i + ": " + v.get(0).invariant() + " (" + v.get(0).message() + ") replay: " + c.replay());
			} catch (RuntimeException e) {
				bad.add(i + ": " + e + " replay: " + c.replay());
			}
		});
		if (!bad.isEmpty()) {
			List<String> sorted = new ArrayList<>(bad);
			sorted.sort(java.util.Comparator.comparingInt(s -> Integer.parseInt(s.substring(0, s.indexOf(':')))));
			fail(t.id() + ": " + sorted.size() + " of " + lots * per + " cases fail; first:\n" + String.join("\n", sorted.subList(0, Math.min(10, sorted.size()))));
		}
	}
}
