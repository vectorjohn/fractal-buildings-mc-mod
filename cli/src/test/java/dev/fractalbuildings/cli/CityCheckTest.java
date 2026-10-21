package dev.fractalbuildings.cli;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.RegionView;
import dev.fractalbuildings.core.settlement.SettlementSite;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The city pipeline through the CLI (D0): `city --scenes/--check`, `city-check`, site ids, byte-stable output. */
class CityCheckTest {
	/** Rules that real-terrain plans still break (hub squares dropped on rough ground leave gaps and hall lots without frontage, road heights differ across tile borders); see the D0 log. */
	private static final String KNOWN = "roads-connected,road-grade,lot-frontage";

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
	void cityCheckOverTheGateWindowsHasNoViolationsOtherThanTheKnownOnes(@TempDir Path dir) throws Exception {
		Path md = dir.resolve("gates.md");
		assertEquals(0, run("city-check", "--seeds", "1", "--sites", "gates:../fixtures/terrain/gate-sites.tsv", "--scenes", "auto:2", "--ignore", KNOWN, "--out", md.toString()));
		String r = Files.readString(md);
		assertTrue(r.contains("9 sites (scenes: auto:2), 0 failing"), r.substring(0, Math.min(300, r.length())));
		for (String name : List.of("gate-temperate-city", "gate-coast-city", "gate-metropolis-temperate", "gate-joined-city")) assertTrue(r.contains("| " + name + " |"), name);
		// without --ignore the same run exits 1: the known rules are really reported, so the flag is not what makes it pass
		assertEquals(1, run("city-check", "--seeds", "1", "--sites", "gates:../fixtures/terrain/gate-sites.tsv", "--scenes", "auto:2", "--out", md.toString()));
	}

	@Test
	void cityCheckOverSyntheticSitesHasNoViolationsOtherThanTheKnownOnes(@TempDir Path dir) throws Exception {
		Path md = dir.resolve("synthetic.md");
		assertEquals(0, run("city-check", "--seeds", "1..2", "--sites", "random:10", "--terrain", "noise:3:70:10:300,noise:5:75:25:200+river:-120:24:6", "--scenes", "auto:1", "--ignore", KNOWN, "--out", md.toString()));
		assertTrue(Files.readString(md).contains("40 sites"));
	}

	@Test
	void cityCheckFindsTheCellSettlementsOfARecordedWorldMap(@TempDir Path dir) throws Exception {
		Path md = dir.resolve("cells.md");
		assertEquals(0, run("city-check", "--seeds", "12345", "--sites", "cells", "--terrain", "recorded:../fixtures/terrain/world-12345-s64.json.gz", "--ignore", KNOWN, "--out", md.toString()));
		String r = Files.readString(md);
		assertTrue(r.contains("| sm3_m9 |"), "the region planner's city at cell -3,-9 is found: " + r.substring(0, 400));
		assertFalse(r.contains("| error |"), r);
	}

	@Test
	void badArgumentsExit2(@TempDir Path dir) {
		assertEquals(2, run("city-check", "--seeds", "1", "--sites", "nowhere"));
		assertEquals(2, run("city-check", "--seeds", "1", "--scenes", "all"));
		assertEquals(2, run("city-check")); // --seeds missing
		assertEquals(2, run("city", "--seed", "1", "--terrain", "flat:64", "--site", "0,0,town,300", "--scenes", "d99_99", "--out-dir", dir.toString()));
		assertEquals(2, run("city", "--seed", "1", "--terrain", "flat:64", "--site", "0,0,town,300", "--scenes", "auto:x", "--out-dir", dir.toString()));
	}

	/** Same inputs, byte-identical city: settlement, every district and every scene; and a second run in another directory. */
	@Test
	void cityOutputIsByteStableAcrossRunsWithScenes(@TempDir Path dir) throws Exception {
		for (String d : new String[] {"a", "b"}) {
			int code = run("city", "--seed", "7", "--terrain", "noise:3:70:10:300", "--site", "600,500,town,350", "--scenes", "auto:2", "--out-dir", dir.resolve(d).toString());
			assertTrue(code == 0 || code == 1, "exit " + code);
		}
		List<Path> a = files(dir.resolve("a")), b = files(dir.resolve("b"));
		assertEquals(a.stream().map(p -> dir.resolve("a").relativize(p)).toList(), b.stream().map(p -> dir.resolve("b").relativize(p)).toList());
		assertTrue(a.stream().filter(p -> p.toString().contains("/scenes/")).count() == 2, "two scenes: " + a.size() + " files");
		assertTrue(a.stream().filter(p -> p.toString().contains("/districts/")).count() >= 5);
		for (int i = 0; i < a.size(); i++) assertArrayEquals(Files.readAllBytes(a.get(i)), Files.readAllBytes(b.get(i)), a.get(i).getFileName().toString());
	}

	@Test
	void sceneSelectionNamesDistrictsAndKeepsTheirPathInTheDistrictFile(@TempDir Path dir) throws Exception {
		int c0 = run("city", "--seed", "7", "--terrain", "flat:64", "--site", "600,500,village,200", "--out-dir", dir.resolve("none").toString(), "--districts");
		assertTrue(c0 == 0 || c0 == 1);
		assertFalse(Files.exists(dir.resolve("none/scenes")), "no scenes unless asked");
		Path out = dir.resolve("some");
		int code = run("city", "--seed", "7", "--terrain", "flat:64", "--site", "600,500,village,200", "--scenes", "auto:1", "--out-dir", out.toString());
		assertTrue(code == 0 || code == 1, "exit " + code);
		List<Path> scenes;
		try (Stream<Path> s = Files.list(out.resolve("scenes"))) { scenes = s.toList(); }
		assertEquals(1, scenes.size());
		String id = scenes.get(0).getFileName().toString().replace(".json", "");
		assertTrue(Files.readString(out.resolve("districts/" + id + ".json")).contains("\"scenes/" + id + ".json\""), "the district file points at its scene");
	}

	/** `--cell` and `--site` give the same id to the same settlement (negative cells written with m). */
	@Test
	void siteIdsAreTheSameForCellAndSite() throws Exception {
		ProbeSpecs.Probes p = Scenes.probes("recorded:../fixtures/terrain/world-12345-s64.json.gz");
		RegionField field = new RegionField(12345, 1024);
		RegionView view = CityCommand.cellView(field, p);
		int checked = 0;
		for (int cz = -9; cz <= 9; cz++) {
			for (int cx = -9; cx <= 9; cx++) {
				var s = view.siteOf(new Cell(cx, cz));
				if (s.isEmpty() || s.get().id().indexOf('m') < 0) continue;
				SettlementSite site = s.get();
				SettlementSite again = CityCommand.fromSite(field, site.centre().x() + "," + site.centre().z() + "," + site.tier().json + "," + site.radius());
				assertEquals(site.id(), again.id(), "cell form and site form name " + site.centre() + " alike");
				checked++;
			}
		}
		assertTrue(checked >= 1, "settlements with negative cells found: " + checked);
	}

	private static List<Path> files(Path root) throws Exception {
		try (Stream<Path> s = Files.walk(root)) { return s.filter(Files::isRegularFile).sorted().toList(); }
	}
}
