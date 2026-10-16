package dev.fractalbuildings.core.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SampleFixtureTest {
	@Test
	void writerOutputMatchesViewerFixtureByteForByte() throws IOException {
		String actual = IrJson.write(SampleScenes.house());
		Path fixture = Path.of("../viewer/fixtures/sample-house.json");
		String expected = Files.readString(fixture, StandardCharsets.UTF_8);
		if (!expected.equals(actual)) {
			Path out = Path.of("build/sample-house.actual.json");
			Files.createDirectories(out.getParent());
			Files.writeString(out, actual, StandardCharsets.UTF_8);
			fail("fixture differs from writer output; actual written to " + out.toAbsolutePath());
		}
	}

	@Test
	void writingTwiceIsIdentical() {
		assertEquals(IrJson.write(SampleScenes.house()), IrJson.write(SampleScenes.house()));
	}
}
