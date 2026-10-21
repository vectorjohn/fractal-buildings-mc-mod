package dev.fractalbuildings.cli;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.settlement.SettlementJson;
import dev.fractalbuildings.core.settlement.SettlementPlan;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T2: every gate-terrain site in fixtures/terrain/gate-sites.tsv plans from its committed dump with no violations and the recorded cell count. */
class GateTerrainTest {
	@Test
	void everyGateSitePlansFromItsDump(@TempDir Path dir) throws Exception {
		List<String> rows = Files.readAllLines(Path.of("../fixtures/terrain/gate-sites.tsv"));
		assertTrue(rows.size() >= 10, "header plus at least nine sites");
		for (String row : rows.subList(1, rows.size())) {
			String[] f = row.split("\t");
			Path out = dir.resolve(f[0]);
			PrintStream se = System.err;
			System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
			int code;
			try {
				code = Main.run(new String[] {"city", "--seed", f[1], "--terrain", "recorded:../fixtures/terrain/" + f[0] + ".json.gz",
					"--site", f[2] + "," + f[3] + "," + f[4] + "," + f[5], "--out-dir", out.toString()});
			} finally {
				System.setErr(se);
			}
			assertEquals(0, code, f[0] + " plans with no violations");
			SettlementPlan p = SettlementJson.read(Files.readString(out.resolve("settlement.json")));
			assertEquals(f[4], p.site().tier().json, f[0]);
			assertEquals(Integer.parseInt(f[6]), p.site().cells().size(), f[0] + " cells");
		}
	}
}
