package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.IrValidator;
import dev.fractalbuildings.core.ir.SceneDoc;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SampleSettlementTest {
	private static final Path FIXTURE = Path.of("../viewer/fixtures/settlement/sample");

	/** The committed viewer fixture is what the writer produces (scene buildings aside: see below). */
	@Test
	void fixtureMatchesWriterByteForByte() throws IOException {
		SampleSettlement.Sample s = SampleSettlement.sample();
		for (Map.Entry<String, String> e : s.files().entrySet()) {
			Path p = FIXTURE.resolve(e.getKey());
			String expected = Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
			String actual = e.getValue();
			if (e.getKey().startsWith("scenes/") && expected != null) {
				// the buildings inside a scene change whenever a building step changes the generator: compare everything else
				expected = IrJson.write(withoutBuildings(IrJson.read(expected)));
				actual = IrJson.write(withoutBuildings(IrJson.read(actual)));
			}
			if (!actual.equals(expected)) {
				fail(p + " is stale; regenerate: scripts/dev ./gradlew :cli:run --args=\"sample-city --out-dir viewer/fixtures/settlement/sample\" "
						+ "(from the repo root; then commit the files)");
			}
		}
		try (var walk = Files.walk(FIXTURE)) {
			long files = walk.filter(Files::isRegularFile).count();
			assertEquals(s.files().size(), files, "fixture directory has files the sample does not write");
		}
	}

	private static SceneDoc withoutBuildings(SceneDoc s) {
		return new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), java.util.List.of(), s.ground(), java.util.List.of());
	}

	@Test
	void sampleIsDeterministicAndPassesItsChecks() {
		assertEquals(SampleSettlement.sample().files(), SampleSettlement.sample().files());
		SampleSettlement.Sample s = SampleSettlement.sample();
		assertEquals(4, s.settlement().districts().size());
		int lots = 0;
		for (DistrictPlan d : s.districts().values()) {
			lots += d.lots().size();
			assertEquals(List_of(), SettlementChecks.district(d, s.settlement().landmarks()), "violations in " + d.district().id());
			assertEquals(s.settlement().district(d.district().id()).lots(), d.lots().size());
		}
		assertEquals(40, lots);
	}

	@Test
	void sampleSceneIsValidAndHasGroundAndWater() {
		SceneDoc scene = SampleSettlement.sample().scenes().get("scenes/d0_0.json");
		assertTrue(IrValidator.validate(scene).isEmpty());
		assertTrue(scene.violations().isEmpty(), "building invariants: " + scene.violations());
		assertEquals(12, scene.buildings().size());
		assertTrue(!scene.ground().isEmpty());
		assertTrue(scene.terrain().waters() != null && java.util.Arrays.stream(scene.terrain().waters()).anyMatch(w -> w > 0), "the river shows as water");
		// the scene round-trips through the strict reader
		assertEquals(IrJson.write(scene), IrJson.write(IrJson.read(IrJson.write(scene))));
	}

	@Test
	void bigGridRoundTripsAndHasManyLots() {
		SampleSettlement.Sample s = SampleSettlement.big(512);
		assertEquals(16, s.settlement().districts().size());
		assertEquals(16 * 36, s.districts().values().stream().mapToInt(d -> d.lots().size()).sum());
		for (DistrictPlan d : s.districts().values()) {
			assertEquals(List_of(), SettlementChecks.district(d, List_of()), d.district().id());
			String j = DistrictJson.write(d);
			assertEquals(j, DistrictJson.write(DistrictJson.read(j)));
		}
		String j = SettlementJson.write(s.settlement());
		assertEquals(j, SettlementJson.write(SettlementJson.read(j)));
	}

	private static <T> java.util.List<T> List_of() { return java.util.List.of(); }
}
