package dev.fractalbuildings.cli;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.settlement.SettlementJson;
import dev.fractalbuildings.core.settlement.SettlementPlan;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CityCommandTest {
	private static int run(String... args) {
		PrintStream se = System.err;
		System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
		try {
			return Main.run(args);
		} finally {
			System.setErr(se);
		}
	}

	/** MC2: `--cell` plans a settled cell that decides alone (a hamlet, a village) as a one-cell settlement, and a joined city from any of its cells alike. */
	@Test
	void cellPlansSoloSettlementsAndJoinedCitiesFromAnyMemberCell(@TempDir Path dir) throws Exception {
		String world = "recorded:../fixtures/terrain/world-987654321-s64.json.gz";
		assertEquals(0, run("city", "--seed", "987654321", "--terrain", world, "--cell", "0,0", "--out-dir", dir.resolve("hamlet").toString()));
		SettlementPlan h = SettlementJson.read(Files.readString(dir.resolve("hamlet/settlement.json")));
		assertEquals("hamlet", h.site().tier().json);
		assertEquals(1, h.site().cells().size());
		assertEquals(0, run("city", "--seed", "987654321", "--terrain", world, "--cell", "0,1", "--out-dir", dir.resolve("village").toString()));
		assertEquals("village", SettlementJson.read(Files.readString(dir.resolve("village/settlement.json"))).site().tier().json);
		// the 4-cell metropolis of gate-metropolis-mixed (owner 2,-2), reached from the owner and from a member cell: same bytes
		SettlementPlan owner = null;
		for (String c : new String[] {"2,-2", "1,-3", "2,-3", "1,-2"}) {
			int code = run("city", "--seed", "987654321", "--terrain", world, "--cell", c, "--out-dir", dir.resolve("m" + c).toString());
			assertTrue(code == 0 || code == 1, c + " exit " + code);
			SettlementPlan p = SettlementJson.read(Files.readString(dir.resolve("m" + c + "/settlement.json")));
			if (owner == null) owner = p;
			assertEquals(4, p.site().cells().size(), c);
			assertEquals(Files.readString(dir.resolve("m2,-2/settlement.json")), Files.readString(dir.resolve("m" + c + "/settlement.json")), c + " names the same city");
		}
	}

	/** MC2: a fine (windowed) dump cannot answer the join rule for a cell near its edge; `--cell` says so and points at `--site`, never a vague error. */
	@Test
	void cellOnAFineWindowReportsWhatTheWindowCannotAnswer(@TempDir Path dir) throws Exception {
		PrintStream se = System.err;
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
		int code;
		try {
			code = Main.run(new String[] {"city", "--seed", "987654321", "--terrain", "recorded:../fixtures/terrain/gate-joined-city.json.gz", "--cell", "8,0", "--out-dir", dir.toString()});
		} finally {
			System.setErr(se);
		}
		String msg = err.toString(StandardCharsets.UTF_8);
		assertEquals(2, code, msg);
		assertTrue(msg.contains("--site"), msg);
		assertTrue(msg.contains("window"), msg);
	}

	@Test
	void writesASkeletonAndATerrainCropAndIsByteStable(@TempDir Path dir) throws Exception {
		for (String d : new String[] {"a", "b"}) {
			assertEquals(0, run("city", "--seed", "7", "--terrain", "noise:3:70:10:300", "--site", "600,500,town,350", "--out-dir", dir.resolve(d).toString()));
		}
		for (String f : new String[] {"settlement.json", "terrain.json.gz"}) {
			assertArrayEquals(Files.readAllBytes(dir.resolve("a").resolve(f)), Files.readAllBytes(dir.resolve("b").resolve(f)), f + " is byte-stable");
		}
		SettlementPlan p = SettlementJson.read(Files.readString(dir.resolve("a/settlement.json")));
		assertEquals("town", p.site().tier().json);
		assertTrue(p.violations().isEmpty());
		assertFalse(p.roads().isEmpty());
		assertEquals("fb-settlement/1", SettlementPlan.FORMAT);
		// the crop is a terrain dump the recorded probe reads, on the 32 lattice, covering the plan raster
		String crop;
		try (var in = new GZIPInputStream(Files.newInputStream(dir.resolve("a/terrain.json.gz")))) { crop = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
		RecordedProbe r = RecordedProbe.parse(crop);
		assertEquals(32, r.step());
		assertEquals(p.coarse().x0(), r.x0());
		assertEquals(p.coarse().nx(), r.nx());
		int[] floor = p.coarse().floorY();
		assertEquals(floor[0], r.floorY(r.x0(), r.z0()));
	}

	/** The committed viewer fixtures are what `city` writes (regenerate with the commands in viewer/fixtures/settlement/skeleton/README.md). */
	@Test
	void viewerFixturesAreCurrent(@TempDir Path dir) throws Exception {
		String[][] cases = {
			{"river-town", "12345", "../fixtures/terrain/w12345-river-s8.json.gz", "--site", "-900,-3500,town,300"},
			{"joined-metropolis", "12345", "../fixtures/terrain/world-12345-s64.json.gz", "--cell", "6,6"}};
		for (String[] c : cases) {
			Path out = dir.resolve(c[0]);
			assertEquals(0, run("city", "--seed", c[1], "--terrain", "recorded:" + c[2], c[3], c[4], "--out-dir", out.toString()));
			for (String f : new String[] {"settlement.json", "terrain.json.gz"}) {
				Path fixture = Path.of("../viewer/fixtures/settlement/skeleton").resolve(c[0]).resolve(f);
				assertArrayEquals(Files.readAllBytes(fixture), Files.readAllBytes(out.resolve(f)), fixture + " is stale: regenerate it (viewer/fixtures/settlement/skeleton/README.md)");
			}
		}
	}


	/** The committed district fixtures are what `city --districts` writes (regenerate with viewer/fixtures/settlement/districts/README.md). */
	@Test
	void districtFixturesAreCurrent(@TempDir Path dir) throws Exception {
		String[][] cases = {
			{"river-town", "12345", "recorded:../fixtures/terrain/w12345-river-s8.json.gz", "--site", "-900,-3500,town,300"},
			{"mixed-city", "1", "recorded:../fixtures/terrain/w1-mixed-s8.json.gz", "--site", "8500,-3300,city,450"},
			{"synth-city", "1001", "noise:1:70:7:220+river:-120:24:6", "--site", "0,0,city,700", "--cell-size", "512"}};
		for (String[] c : cases) {
			Path out = dir.resolve(c[0]);
			String[] args = new String[c.length + 4];
			args[0] = "city"; args[1] = "--seed"; args[2] = c[1]; args[3] = "--terrain"; args[4] = c[2];
			System.arraycopy(c, 3, args, 5, c.length - 3);
			args[c.length + 2] = "--districts";
			args[c.length + 3] = "--out-dir";
			String[] all = java.util.Arrays.copyOf(args, c.length + 5);
			all[c.length + 4] = out.toString();
			assertEquals(0, run(all), c[0]);
			Path fixtures = Path.of("../viewer/fixtures/settlement/districts").resolve(c[0]);
			try (var files = Files.walk(fixtures)) {
				for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
					if (f.getFileName().toString().equals("README.md")) continue;
					Path rel = fixtures.relativize(f);
					assertArrayEquals(Files.readAllBytes(f), Files.readAllBytes(out.resolve(rel)), f + " is stale: regenerate it (viewer/fixtures/settlement/districts/README.md)");
				}
			}
			try (var files = Files.walk(out)) { assertEquals(files.filter(Files::isRegularFile).count(), Files.walk(fixtures).filter(Files::isRegularFile).count(), c[0] + ": file sets differ"); }
		}
	}

	@Test
	void rejectsBadArguments(@TempDir Path dir) {
		String out = dir.toString();
		assertEquals(2, run("city", "--seed", "7", "--out-dir", out)); // neither --cell nor --site
		assertEquals(2, run("city", "--seed", "7", "--cell", "0,0", "--site", "0,0,town,300", "--out-dir", out));
		assertEquals(2, run("city", "--seed", "7", "--site", "0,0,castle,300", "--out-dir", out));
		assertEquals(2, run("city", "--seed", "7", "--site", "0,0,city,30000", "--out-dir", out)); // too many cells
	}
}
