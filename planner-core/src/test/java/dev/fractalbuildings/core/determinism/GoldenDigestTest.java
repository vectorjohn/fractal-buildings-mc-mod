package dev.fractalbuildings.core.determinism;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.v0.GeneratorV0;
import dev.fractalbuildings.core.ir.IrJson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SHA-256 of 64 fixed cases against a recorded file: catches run-to-run differences (identity hashes, map order). */
class GoldenDigestTest {
	static String hex(byte[] b) {
		StringBuilder sb = new StringBuilder();
		for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
		return sb.toString();
	}

	static List<String> compute() throws Exception {
		GeneratorV0 g = new GeneratorV0();
		List<String> lines = new ArrayList<>();
		for (long s = 0; s < 64; s++) {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(IrJson.write(g.generate(Cases.of(s))).getBytes(StandardCharsets.UTF_8));
			lines.add(s + " " + hex(d));
		}
		return lines;
	}

	@Test
	void digestsMatchRecordedFile() throws Exception {
		List<String> actual = compute();
		List<String> expected;
		try (InputStream in = GoldenDigestTest.class.getResourceAsStream("/golden/v0-digests.txt")) {
			expected = in == null ? List.of() : List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
		}
		if (!expected.equals(actual)) {
			Path out = Path.of("build/golden/v0-digests.txt");
			Files.createDirectories(out.getParent());
			Files.writeString(out, String.join("\n", actual) + "\n");
			fail("Golden digests differ from golden/v0-digests.txt. If the change is intended, bump GeneratorV0.GEN_VERSION and copy "
					+ out.toAbsolutePath() + " to planner-core/src/test/resources/golden/v0-digests.txt");
		}
	}
}
