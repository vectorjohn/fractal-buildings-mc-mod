package dev.fractalbuildings.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code roads} (ROADMAP 6.10): byte-stable output, the committed viewer dumps are current, and the report states the guarantees. */
class RoadsCommandTest {
	private static String run(Path out, long seed) {
		PrintStream so = System.out;
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
		try {
			assertEquals(0, Main.run(new String[] {"roads", "--seed", Long.toString(seed), "--terrain", "recorded:../fixtures/terrain/world-" + seed + "-s64.json.gz", "--out", out.toString()}));
		} finally {
			System.setOut(so);
		}
		return buf.toString(StandardCharsets.UTF_8);
	}

	@Test
	void outputIsByteStableAndTheCommittedViewerDumpIsCurrent(@TempDir Path dir) throws Exception {
		String report = run(dir.resolve("a.json"), 12345);
		run(dir.resolve("b.json"), 12345);
		assertEquals(Files.readString(dir.resolve("a.json")), Files.readString(dir.resolve("b.json")), "two runs write the same bytes");
		try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(Path.of("../viewer/fixtures/region/roads-12345.json.gz")))) {
			assertEquals(Files.readString(dir.resolve("a.json")), new String(in.readAllBytes(), StandardCharsets.UTF_8),
				"viewer/fixtures/region/roads-12345.json.gz is stale: regenerate with `fractal-cli roads` and gzip -9n");
		}
		assertTrue(report.contains("0 with a routable neighbour (must be 0)"), report);
		assertTrue(report.contains("tagged port"), report);
		assertTrue(Files.readString(dir.resolve("a.json")).contains("\"roads\":{\"lgap\":1536"));
	}
}
