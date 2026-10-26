package dev.fractalbuildings.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Golden text of {@code floorplan} for fixed seeds. A deliberate change of the generator or the format fails these;
 * regenerate with {@code FB_UPDATE_GOLDEN=1 ./gradlew :cli:test --tests '*FloorplanCommandTest*'} and read the diff.
 */
class FloorplanCommandTest {
	private static String run(String... args) {
		PrintStream so = System.out;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
		try {
			assertEquals(0, Main.run(args));
		} finally {
			System.setOut(so);
		}
		return out.toString(StandardCharsets.UTF_8);
	}

	private static void golden(String name, String actual) throws IOException {
		Path p = Path.of("src/test/resources/floorplan", name);
		if (System.getenv("FB_UPDATE_GOLDEN") != null) {
			Files.createDirectories(p.getParent());
			Files.writeString(p, actual, StandardCharsets.UTF_8);
		}
		assertEquals(Files.readString(p, StandardCharsets.UTF_8), actual, name + " differs from the golden text");
	}

	@Test
	void medievalHouseGolden() throws IOException {
		String t = run("floorplan", "--seed", "3", "--lot", "16x12", "--style", "medieval", "--type", "house");
		golden("medieval-house-s3.txt", t);
		assertTrue(t.contains("%") && t.contains("Entrances from outside:\n  o2 (door)"));
	}

	@Test
	void multiStoreyStairsGolden() throws IOException {
		String t = run("floorplan", "--seed", "5", "--lot", "20x16", "--style", "medieval", "--type", "house");
		golden("medieval-house-s5-3storeys.txt", t);
		assertTrue(t.contains("## Storey 2") && t.contains("goes UP from r3 to r6") && t.contains("arrives from below"));
	}

	@Test
	void towerWideGolden() throws IOException {
		golden("tower-s9-wide.txt", run("floorplan", "--seed", "9", "--lot", "16x14", "--style", "medieval", "--type", "tower", "--wide"));
	}

	@Test
	void fromSceneFileMatchesGeneration(@TempDir Path dir) throws IOException {
		Path f = dir.resolve("s.json");
		assertEquals(0, Main.run(new String[] {"gen", "--seed", "3", "--lot", "16x12", "--style", "medieval", "--type", "house", "--out", f.toString()}));
		String viaFile = run("floorplan", "--in", f.toString());
		assertEquals(run("floorplan", "--seed", "3", "--lot", "16x12", "--style", "medieval", "--type", "house"), viaFile);
		Path out = dir.resolve("o.txt");
		assertEquals(0, Main.run(new String[] {"floorplan", "--in", f.toString(), "--out", out.toString()}));
		assertEquals(viaFile, Files.readString(out));
	}

	@Test
	void badInputExitsTwo() {
		assertEquals(2, Main.run(new String[] {"floorplan"}));
		assertEquals(2, Main.run(new String[] {"floorplan", "--seed", "1", "--lot", "16x12", "--building", "nope"}));
	}
}
