package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.RegionField;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Golden digests of settlement plans (plan "Test infrastructure"): SHA-256 of the {@code fb-settlement/1} text of fixed
 * (seed, terrain, site) cases, with a few counts beside it so a change can be read. Generated, never hand-merged: on an
 * intended change copy {@code build/golden/settlement/*.txt} over {@code src/test/resources/golden/settlement/}. Plans only:
 * building output never enters these digests.
 */
class SettlementGoldenTest {
	static Map<String, SettlementPlan> cases() throws Exception {
		Map<String, SettlementPlan> out = new TreeMap<>();
		RegionField f7 = new RegionField(7, 1024);
		SettlementSite town = SkeletonFixtures.site(f7, 0, 0, Tier.TOWN, 350, 6);
		out.put("flat-town", Skeletons.plan(7, new FixedSiteView(town, f7), town, SkeletonFixtures.probe("flat:64"), SkeletonsTest.GEN));
		SkeletonsTest.Case c = SkeletonsTest.syntheticCase(1);
		out.put("synthetic-city", c.plan());
		RecordedProbe r = RecordedProbe.read(Path.of("../fixtures/terrain/w12345-river-s8.json.gz"));
		RegionField f = new RegionField(12345, 1024);
		Pt centre = new Pt(-900, -3500);
		Cell claimant = CellGeometry.owner(f, centre.x(), centre.z());
		SettlementSite river = new SettlementSite("river-town", Tier.TOWN, centre, 300, claimant, List.of(claimant));
		out.put("recorded-river-town", Skeletons.plan(12345, new FixedSiteView(river, f), river, CoarseProbe.of(r, r, 32), SkeletonsTest.GEN));
		return out;
	}

	@Test
	void goldenPlans() throws Exception {
		List<String> stale = new ArrayList<>();
		for (var e : cases().entrySet()) {
			SettlementPlan p = e.getValue();
			String json = SettlementJson.write(p);
			String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
			int bridges = 0;
			for (Road road : p.roads()) bridges += road.bridges().size();
			String actual = digest + "\n" + e.getKey() + ": " + p.site().tier().json + ", " + p.site().cells().size() + " cells, " + p.districts().size() + " districts, "
					+ p.roads().size() + " roads, " + bridges + " bridges, " + p.gates().size() + " gates, " + p.landmarks().size() + " landmarks, " + json.length() + " bytes\n";
			String expected;
			try (var in = SettlementGoldenTest.class.getResourceAsStream("/golden/settlement/" + e.getKey() + ".txt")) {
				expected = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
			if (!expected.equals(actual)) {
				Path out = Path.of("build/golden/settlement/" + e.getKey() + ".txt");
				Files.createDirectories(out.getParent());
				Files.writeString(out, actual);
				stale.add(e.getKey() + " (new file " + out.toAbsolutePath() + ")");
			}
		}
		assertTrue(stale.isEmpty(), "settlement goldens differ: " + stale + ". If intended, copy build/golden/settlement/*.txt to src/test/resources/golden/settlement/.");
	}
}
