package dev.fractalbuildings.core.determinism;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Greps planner-core main sources for constructs that break determinism. Allow a line with {@code // det-ok: reason}. */
class DeterminismLintTest {
	static final List<Pattern> BANNED = List.of(
			Pattern.compile("\\bMath\\.random\\b"), Pattern.compile("\\bnew Random\\("), Pattern.compile("\\bThreadLocalRandom\\b"),
			Pattern.compile("\\bcurrentTimeMillis\\b"), Pattern.compile("\\bnanoTime\\b"),
			Pattern.compile("\\bHashMap\\b"), Pattern.compile("\\bHashSet\\b"), Pattern.compile("\\bparallelStream\\b"),
			Pattern.compile("\\bMath\\.(sin|cos|tan|pow|exp|log|atan2|cbrt|hypot)\\b"));

	static List<String> scan(Iterable<String> lines, String name) {
		List<String> bad = new ArrayList<>();
		int n = 0;
		for (String line : lines) {
			n++;
			if (line.matches(".*//\\s*det-ok:.*") || line.startsWith("import ")) continue;
			for (Pattern p : BANNED) if (p.matcher(line).find()) bad.add(name + ":" + n + " " + p + " : " + line.trim());
		}
		return bad;
	}

	static Path mainSources() {
		Path p = Path.of("src/main/java");
		return Files.isDirectory(p) ? p : Path.of("planner-core/src/main/java");
	}

	@Test
	void mainSourcesAreClean() throws IOException {
		List<String> bad = new ArrayList<>();
		try (Stream<Path> files = Files.walk(mainSources())) {
			for (Path f : files.filter(x -> x.toString().endsWith(".java")).sorted().toList()) bad.addAll(scan(Files.readAllLines(f), f.toString()));
		}
		assertTrue(bad.isEmpty(), "non-deterministic constructs:\n" + String.join("\n", bad));
	}

	/** The settlement, region, terrain-facing and style packages are inside the scanned tree (the walk covers all of main). */
	@Test
	void scanCoversThePhase5Packages() throws IOException {
		for (String pkg : List.of("settlement", "region", "probe", "style", "check", "gen/v1")) {
			Path d = mainSources().resolve("dev/fractalbuildings/core").resolve(pkg);
			assertTrue(Files.isDirectory(d), pkg + " moved; update the lint list");
			try (Stream<Path> files = Files.walk(d)) { assertTrue(files.anyMatch(x -> x.toString().endsWith(".java")), pkg + " has no sources"); }
		}
	}

	@Test
	void patternsMatchWhatTheyShouldAndNotMore() {
		assertEquals(1, scan(List.of("Map<Integer,String> m = new HashMap<>();"), "x").size());
		assertEquals(0, scan(List.of("Map<Integer,String> m = new LinkedHashMap<>();"), "x").size());
		assertEquals(1, scan(List.of("double d = Math.log(2);"), "x").size());
		assertEquals(0, scan(List.of("double d = StrictMath.log(2);"), "x").size());
		assertEquals(1, scan(List.of("new Random(4)"), "x").size());
		assertEquals(0, scan(List.of("new HashSet<>(); // det-ok: never iterated"), "x").size());
		assertEquals(1, scan(List.of("long t = System.nanoTime();"), "x").size());
	}
}
