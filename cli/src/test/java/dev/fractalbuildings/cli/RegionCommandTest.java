package dev.fractalbuildings.cli;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.json.JsonReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionCommandTest {
	private static int run(Path out, String... args) {
		PrintStream se = System.err;
		System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
		try {
			return Main.run(args);
		} finally {
			System.setErr(se);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> layer0(String json) {
		Map<String, Object> m = (Map<String, Object>) JsonReader.parse(json);
		assertEquals("fb-region-dump/1", m.get("format"));
		return (Map<String, Object>) ((List<Object>) m.get("layers")).get(0);
	}

	@Test
	void writesARegionMapWithDecisionsAndIsByteStable(@TempDir Path dir) throws Exception {
		Path a = dir.resolve("a.json"), b = dir.resolve("b.json");
		String[] args = {"region", "--seed", "77", "--terrain", "flat:64", "--window", "-3000,-3000,6000,6000", "--out", null};
		args[args.length - 1] = a.toString();
		assertEquals(0, run(a, args));
		args[args.length - 1] = b.toString();
		assertEquals(0, run(b, args));
		String json = Files.readString(a);
		assertEquals(json, Files.readString(b));
		Map<String, Object> l = layer0(json);
		List<?> cells = (List<?>) l.get("cells");
		assertFalse(cells.isEmpty());
		boolean tier = false;
		for (Object c : cells) {
			Map<?, ?> cell = (Map<?, ?>) c;
			assertTrue(cell.containsKey("tier") && cell.containsKey("centreX") && cell.containsKey("radius"));
			tier |= Boolean.TRUE.equals(cell.get("city"));
		}
		assertTrue(tier, "flat plains should settle some cells");
	}

	@Test
	void readsARecordedDumpAndDefaultsTheWindowToIt(@TempDir Path dir) throws Exception {
		Path out = dir.resolve("crop.json");
		assertEquals(0, run(out, "region", "--seed", "12345", "--terrain", "recorded:../fixtures/terrain/s4-12345-crop32.json", "--out", out.toString()));
		layer0(Files.readString(out));
	}

	@Test
	void badInputExits2(@TempDir Path dir) {
		assertEquals(2, run(dir, "region", "--terrain", "flat:64"));
		assertEquals(2, run(dir, "region", "--seed", "1", "--cell-size", "10"));
		assertEquals(2, run(dir, "region", "--seed", "1", "--window", "1,2,3"));
	}
}
