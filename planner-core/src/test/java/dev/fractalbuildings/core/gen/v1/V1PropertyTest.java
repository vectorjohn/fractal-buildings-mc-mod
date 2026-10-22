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
		java.util.concurrent.atomic.AtomicInteger skipped = new java.util.concurrent.atomic.AtomicInteger();
		IntStream.range(0, lots * per).parallel().forEach(i -> {
			V1Case c = V1Case.draw(t, i % lots, i / lots);
			GenRequest rq = c.request();
			try {
				SceneDoc s = new GeneratorV1().generate(rq);
				List<Violation> v = Invariants.check(s, rq.terrain());
				if (!v.isEmpty()) bad.add(i + ": " + v.get(0).invariant() + " (" + v.get(0).message() + ") replay: " + c.replay());
			} catch (IllegalArgumentException e) {
				// a lot the spec policy rejects by design (SpecPolicyV1 relief rule) is a skipped case, as for `fractal-cli check`
				if (isPolicyRejection(e, t)) skipped.incrementAndGet();
				else bad.add(i + ": " + e + " replay: " + c.replay());
			} catch (RuntimeException e) {
				bad.add(i + ": " + e + " replay: " + c.replay());
			}
		});
		if (skipped.get() > 0) System.out.println("V1PropertyTest " + t.id() + ": " + skipped.get() + " of " + lots * per + " cases skipped (lot too steep for the type, SpecPolicyV1)");
		// the skip must not hide a generator that rejects most lots
		if (skipped.get() * 50 > lots * per) bad.add("-1: " + skipped.get() + " of " + lots * per + " cases rejected as too steep (over 2%)");
		if (!bad.isEmpty()) {
			List<String> sorted = new ArrayList<>(bad);
			sorted.sort(java.util.Comparator.comparingInt(s -> Integer.parseInt(s.substring(0, s.indexOf(':')))));
			fail(t.id() + ": " + sorted.size() + " of " + lots * per + " cases fail; first:\n" + String.join("\n", sorted.subList(0, Math.min(10, sorted.size()))));
		}
	}

	/** Only the policy's own relief rejection: "lot ground relief N over WxD is steeper than 1 in G, too steep for a <type>". */
	static boolean isPolicyRejection(IllegalArgumentException e, BuildingType t) {
		String m = e.getMessage();
		return m != null && m.startsWith("lot ground relief ") && m.endsWith(", too steep for a " + t.id());
	}
}
