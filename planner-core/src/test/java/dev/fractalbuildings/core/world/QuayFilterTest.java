package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.settlement.Districts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * QUAYLAZY step 2: a tile keeps the quay runs that have a footprint column within reach of it (not the runs whose bounding box does),
 * which cannot change what the tile plans, because nothing outside {@code keep} reaches it. The digests (sha-256 of each owned waterfront
 * tile's {@code DistrictJson}) were recorded on the base commit with the bounding-box filter ({@code -Dfb.quaylazy.record=true}
 * rewrites them: only on a commit whose plans are known right). QuayLazyTest's live comparison with the reference trace replaces
 * this file once step 3b's tests cover it.
 */
class QuayFilterTest {
	static final Path DIGESTS = Path.of("src/test/resources/quaylazy/filter-digests.tsv");

	static String sha(String s) {
		try {
			byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : h) sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** {@code ix,iz -> digest} of every owned waterfront tile's plan. */
	static TreeMap<String, String> digests(QuaySites site) {
		QuaySites.Open o = site.open();
		TreeMap<String, String> out = new TreeMap<>();
		List<DistrictGrid.TileId> tiles = o.waterfront();
		assertFalse(tiles.isEmpty(), site.label() + " has waterfront tiles");
		int quayTiles = 0;
		for (DistrictGrid.TileId t : tiles) {
			out.put(t.ix() + "," + t.iz(), sha(DistrictJson.write(Districts.plan(o.source(), t.ix(), t.iz(), o.exact()).orElseThrow().plan())));
			if (!Districts.quayRuns(o.source(), t.ix(), t.iz(), o.exact()).isEmpty()) quayTiles++;
		}
		assertTrue(quayTiles > 0, site.label() + " has at least one quay run, or the fixture covers nothing");
		System.out.println("QuaySites " + site.label() + ": " + tiles.size() + " waterfront tiles, " + quayTiles + " see a quay run");
		return out;
	}

	static TreeMap<String, TreeMap<String, String>> recorded() throws IOException {
		TreeMap<String, TreeMap<String, String>> out = new TreeMap<>();
		if (!Files.exists(DIGESTS)) return out;
		for (String line : Files.readAllLines(DIGESTS)) {
			if (line.isBlank() || line.startsWith("#")) continue;
			String[] f = line.split("\t");
			out.computeIfAbsent(f[0], k -> new TreeMap<>()).put(f[1] + "," + f[2], f[3]);
		}
		return out;
	}

	static void check(boolean heavy) throws IOException {
		boolean record = Boolean.getBoolean("fb.quaylazy.record");
		TreeMap<String, TreeMap<String, String>> want = record ? new TreeMap<>(recorded()) : recorded();
		for (QuaySites s : QuaySites.all()) {
			if (s.heavy() != heavy) continue;
			TreeMap<String, String> got = digests(s);
			if (record) { want.put(s.label(), got); continue; }
			assertEquals(want.get(s.label()), got, s.label() + ": every waterfront tile plans as it did with the bounding-box filter");
		}
		if (record) {
			List<String> lines = new ArrayList<>();
			lines.add("# QUAYLAZY step 2: sha-256 of DistrictJson per owned waterfront tile, recorded with the bounding-box quay filter (label, ix, iz, digest)");
			for (var e : want.entrySet()) for (var d : e.getValue().entrySet()) lines.add(e.getKey() + "\t" + d.getKey().replace(',', '\t') + "\t" + d.getValue());
			Files.createDirectories(DIGESTS.getParent());
			Files.write(DIGESTS, lines);
		}
	}

	@Test
	void theColumnFilterKeepsEveryPlan() throws IOException { check(false); }

	@Tag("heavy")
	@Test
	void theColumnFilterKeepsEveryMetropolisPlan() throws IOException { check(true); }
}
