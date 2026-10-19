package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.fail;

import dev.fractalbuildings.core.ir.IrJson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SHA-256 of 32 fixed v1 cases per type against {@code golden/v1/<type>.txt}: catches run-to-run and
 * JVM-to-JVM differences (map order, identity hashes) and any unintended output change. The files
 * are generated, never hand-merged: on a mismatch the test writes {@code build/golden/v1/<type>.txt};
 * if the change is intended, copy it over (plan "Golden digests").
 */
class GoldenDigestV1Test {
	static final int CASES = 32;

	static List<String> compute(BuildingType t) throws Exception {
		List<String> lines = new ArrayList<>();
		int lots = t.limits().testLots().size();
		for (int i = 0; i < CASES; i++) {
			V1Case c = V1Case.draw(t, i % lots, 1_000_000 + i, V1Case.GOLDEN_STYLES);
			String json;
			try {
				json = IrJson.write(new GeneratorV1().generate(c.request()));
			} catch (RuntimeException e) {
				json = "error: " + e.getMessage();
			}
			byte[] d = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
			lines.add(i + " " + HexFormat.of().formatHex(d));
		}
		return lines;
	}

	@Test
	void digestsMatchTheRecordedFiles() throws Exception {
		List<String> stale = new ArrayList<>();
		for (BuildingType t : Types.all()) {
			List<String> actual = compute(t);
			List<String> expected;
			try (InputStream in = GoldenDigestV1Test.class.getResourceAsStream("/golden/v1/" + t.id() + ".txt")) {
				expected = in == null ? List.of() : List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
			}
			if (!expected.equals(actual)) {
				Path out = Path.of("build/golden/v1/" + t.id() + ".txt");
				Files.createDirectories(out.getParent());
				Files.writeString(out, String.join("\n", actual) + "\n");
				stale.add(t.id() + " (new digests in " + out.toAbsolutePath() + ")");
			}
		}
		if (!stale.isEmpty()) {
			fail("v1 golden digests differ for " + stale + ". If the change is intended, copy build/golden/v1/*.txt to "
					+ "planner-core/src/test/resources/golden/v1/ (never hand-merge these files).");
		}
	}
}
