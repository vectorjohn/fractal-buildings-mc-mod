package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.SceneDoc;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The sample house with its ridge, interior door and opening o1 removed, plus the violations found. */
class ViolationsFixtureTest {
	static SceneDoc broken() {
		SceneDoc s = InvariantsTest.mutate(b -> { b.remove(17); b.remove(15); return b; }, o -> { o.remove(1); return o; }, x -> x, 0, 0);
		return new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), s.buildings(), Invariants.check(s));
	}

	@Test
	void violationsFixtureIsCurrent() throws IOException {
		SceneDoc d = broken();
		assertFalse(d.violations().isEmpty());
		String actual = IrJson.write(d);
		Path fixture = Path.of("../viewer/fixtures/sample-house-violations.json");
		String expected = Files.exists(fixture) ? Files.readString(fixture, StandardCharsets.UTF_8) : "";
		if (!expected.equals(actual)) {
			Path out = Path.of("build/sample-house-violations.actual.json");
			Files.createDirectories(out.getParent());
			Files.writeString(out, actual, StandardCharsets.UTF_8);
			fail("fixture stale; copy " + out.toAbsolutePath() + " to " + fixture);
		}
	}
}
