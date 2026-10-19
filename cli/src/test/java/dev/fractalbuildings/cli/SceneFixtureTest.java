package dev.fractalbuildings.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Keeps the committed viewer fixture in sync with the generator: regenerate with the CLI when this fails. */
class SceneFixtureTest {
	@Test
	void v0Seed1MatchesCliOutput(@TempDir Path dir) throws Exception {
		Path out = dir.resolve("a.json");
		assertEquals(0, Main.run(new String[] {"gen", "--gen", "v0", "--seed", "1", "--lot", "16x12", "--style", "medieval", "--terrain", "plane:64:1:0:4", "--check", "--out", out.toString()}));
		assertArrayEquals(Files.readAllBytes(Path.of("..", "viewer", "fixtures", "v0-seed1.json")), Files.readAllBytes(out),
				"viewer/fixtures/v0-seed1.json is stale; regenerate: fractal-cli gen --gen v0 --seed 1 --lot 16x12 --style medieval --terrain plane:64:1:0:4 --check --out viewer/fixtures/v0-seed1.json");
	}
}
