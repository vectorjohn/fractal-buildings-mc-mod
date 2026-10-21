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

	@Test
	void rejectsBadArguments(@TempDir Path dir) {
		String out = dir.toString();
		assertEquals(2, run("city", "--seed", "7", "--out-dir", out)); // neither --cell nor --site
		assertEquals(2, run("city", "--seed", "7", "--cell", "0,0", "--site", "0,0,town,300", "--out-dir", out));
		assertEquals(2, run("city", "--seed", "7", "--site", "0,0,castle,300", "--out-dir", out));
		assertEquals(2, run("city", "--seed", "7", "--site", "0,0,city,30000", "--out-dir", out)); // too many cells
	}
}
