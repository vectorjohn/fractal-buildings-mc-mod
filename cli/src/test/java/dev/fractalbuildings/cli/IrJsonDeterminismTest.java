package dev.fractalbuildings.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IrJsonDeterminismTest {
	private static final String[] ARGS = {"gen", "--seed", "1", "--lot", "20x20", "--style", "medieval", "--out"};

	private static byte[] gen(Path file) throws Exception {
		String[] a = java.util.Arrays.copyOf(ARGS, ARGS.length + 1);
		a[ARGS.length] = file.toString();
		assertEquals(0, Main.run(a));
		return Files.readAllBytes(file);
	}

	@Test
	void sameInputGivesByteIdenticalJson(@TempDir Path dir) throws Exception {
		assertArrayEquals(gen(dir.resolve("a.json")), gen(dir.resolve("b.json")));
	}

	@Test
	void jsonHasTwoRolesAndVersion(@TempDir Path dir) throws Exception {
		String s = new String(gen(dir.resolve("a.json")), StandardCharsets.UTF_8);
		assertTrue(s.contains("\"irVersion\": 0"));
		assertTrue(s.contains("\"role\": \"wall\"") && s.contains("\"role\": \"roof\""));
	}

	/** Keeps the committed viewer fixture in sync: regenerate with the CLI if this fails. */
	@Test
	void viewerFixtureMatchesCliOutput(@TempDir Path dir) throws Exception {
		Path fixture = Path.of("..", "viewer", "fixtures", "box5.json");
		assertArrayEquals(
				Files.readAllBytes(fixture),
				gen(dir.resolve("a.json")),
				"viewer/fixtures/box5.json is stale; regenerate: fractal-cli gen --seed 1 --lot 20x20 --style medieval --out viewer/fixtures/box5.json");
	}
}
