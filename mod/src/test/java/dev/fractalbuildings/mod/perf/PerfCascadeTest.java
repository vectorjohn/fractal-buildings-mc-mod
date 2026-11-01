package dev.fractalbuildings.mod.perf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * CASCADE step 1: the cascade count harness (docs/plans/jflazy.md). Prints the counts of {@link CascadeCount} and writes
 * {@code mod/build/perf/cascade.md}; runs the walk twice and requires identical counts (they are deterministic).
 * Run: {@code scripts/dev ./gradlew :mod:test -Dfb.perf.cascade=1 --tests '*PerfCascadeTest*'}. Never in the normal run.
 */
class PerfCascadeTest {
	@Test
	@EnabledIfSystemProperty(named = "fb.perf.cascade", matches = ".+")
	void cascade() throws Exception {
		CascadeCount.Result r = CascadeCount.run();
		String md = CascadeCount.markdown(r);
		Path dir = Path.of(System.getProperty("fb.perf.out", "build/perf"));
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("cascade.md"), md);
		System.out.println(md);
		assertTrue(r.step("1 plan").counts().get("rawPlans") > 0, "the plan of the chunk's tile planned nothing");
		CascadeCount.Result again = CascadeCount.run();
		org.junit.jupiter.api.Assertions.assertEquals(r.steps().get(1).counts(), again.steps().get(1).counts(), "counts are not deterministic");
		org.junit.jupiter.api.Assertions.assertEquals(r.steps().get(1).fillsByPart(), again.steps().get(1).fillsByPart(), "fills by part are not deterministic");
	}
}
