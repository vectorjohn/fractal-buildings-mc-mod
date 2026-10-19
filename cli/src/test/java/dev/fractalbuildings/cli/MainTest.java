package dev.fractalbuildings.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.SceneDoc;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
	private record Result(int code, String out, String err) {}

	private static Result run(String... args) {
		PrintStream so = System.out, se = System.err;
		ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
		System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
		try {
			int code = Main.run(args);
			return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
		} finally {
			System.setOut(so);
			System.setErr(se);
		}
	}

	@Test
	void usageErrorsExit2() {
		assertEquals(2, run().code());
		assertEquals(2, run("nope").code());
		assertEquals(2, run("gen", "--lot", "12x10").code()); // no seed
		assertEquals(2, run("gen", "--seed", "x", "--lot", "12x10").code());
		assertEquals(2, run("gen", "--seed", "1", "--lot", "12").code());
		assertEquals(2, run("gen", "--seed", "1", "--lot", "12x10", "--style", "gothic").code());
		assertEquals(2, run("gen", "--seed", "1", "--lot", "12x10", "--terrain", "bogus:1").code());
		assertEquals(2, run("gen", "--seed", "1", "--lot", "12x10", "--front", "up").code());
		assertEquals(2, run("gen", "--seed", "1", "--lot", "3x3").code()); // below the minimum lot
		assertEquals(2, run("gen", "--seed", "1", "--lot", "12x10", "--wat", "1").code());
		assertEquals(2, run("check", "--seeds", "5..1").code());
		assertEquals(2, run("batch", "--seeds", "0..1", "--lot", "12x10").code()); // no --out-dir
	}

	@Test
	void genStdoutEqualsOutFile(@TempDir Path dir) throws Exception {
		String[] a = {"gen", "--seed", "3", "--lot", "16x12", "--style", "medieval", "--terrain", "plane:64:1:0:4", "--check"};
		Result r = run(a);
		assertEquals(0, r.code());
		Path f = dir.resolve("sub/a.json");
		String[] b = java.util.Arrays.copyOf(a, a.length + 2);
		b[a.length] = "--out";
		b[a.length + 1] = f.toString();
		assertEquals(0, run(b).code());
		assertArrayEquals(r.out().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(f));
		SceneDoc s = IrJson.read(r.out());
		assertEquals(1, s.buildings().size());
		assertEquals(0, s.violations().size());
	}

	@Test
	void genIsDeterministic() {
		String[] a = {"gen", "--seed", "9", "--lot", "20x20", "--style", "plain", "--terrain", "hill:64:20:24", "--front", "east"};
		assertEquals(run(a).out(), run(a).out());
	}

	@Test
	void batchWritesSeedFilesAndIndex(@TempDir Path dir) throws Exception {
		Result r = run("batch", "--seeds", "2..4", "--lot", "12x10", "--style", "medieval", "--out-dir", dir.toString());
		assertEquals(0, r.code(), r.err());
		assertEquals("{\"type\": \"house\", \"style\": \"medieval\", \"lot\": \"12x10\", \"terrain\": \"flat:64\", \"seeds\": [2, 3, 4]}\n",
				Files.readString(dir.resolve("index.json")));
		for (int s = 2; s <= 4; s++) {
			SceneDoc d = IrJson.read(Files.readString(dir.resolve("seed-" + s + ".json")));
			assertEquals(s, d.seed());
		}
		// gen with the same options gives the same bytes as the batch file (checks embedded in both)
		Result g = run("gen", "--seed", "3", "--lot", "12x10", "--style", "medieval", "--check");
		assertEquals(Files.readString(dir.resolve("seed-3.json")), g.out());
	}

	@Test
	void checkSeeds0to49PassesOnTheDefaultMatrix() {
		Result r = run("check", "--seeds", "0..49");
		assertEquals(0, r.code(), r.out());
		assertTrue(r.out().contains("Cases checked: 6400, failing: 0"), r.out());
	}

	@Test
	void checkWritesMarkdownAndBenchPrintsStages(@TempDir Path dir) throws Exception {
		Path md = dir.resolve("r.md");
		assertEquals(0, run("check", "--seeds", "0..1", "--lots", "12x10", "--styles", "plain", "--terrains", "flat:64", "--fronts", "north", "--out", md.toString()).code());
		assertTrue(Files.readString(md).startsWith("# Invariant report"));
		Result b = run("bench", "--n", "20", "--lots", "12x10", "--terrains", "flat:64");
		assertEquals(0, b.code(), b.err());
		for (String k : new String[] {"spec", "shell", "instance", "check"}) assertTrue(b.out().contains(k), b.out());
	}

	@Test
	void diversityReportsOverSeeds() {
		Result r = run("diversity", "--seeds", "0..15", "--lot", "20x20");
		assertEquals(0, r.code(), r.err());
		assertTrue(r.out().contains("mean pairwise"), r.out());
		assertEquals(2, run("diversity", "--lot", "20x20").code());
	}
}
