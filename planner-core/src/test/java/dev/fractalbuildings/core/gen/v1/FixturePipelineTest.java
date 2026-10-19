package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every {@link TestMassings} fixture through the whole v1 pipeline passes every invariant, on flat and sloped ground. */
class FixturePipelineTest {
	static final Map<String, TerrainProbe> GROUND = Map.of(
			"flat", SyntheticTerrain.flat(64),
			"slope", SyntheticTerrain.plane(64, 1, 1, 3));

	@Test
	void everyFixturePassesTheInvariants() {
		List<String> bad = new ArrayList<>();
		int n = 0;
		for (Map.Entry<String, Massing> f : TestMassings.all().entrySet())
			for (String style : List.of("plain", "medieval"))
				for (String ground : List.of("flat", "slope"))
					for (long seed = 0; seed < 10; seed++) {
						SceneDoc s = TestMassings.scene(f.getValue(), seed, style, GROUND.get(ground));
						List<Violation> v = Invariants.check(s);
						n++;
						if (!v.isEmpty()) bad.add(f.getKey() + " " + style + " " + ground + " seed " + seed + ": " + v.get(0).invariant() + " " + v.get(0).message() + " " + v.get(0).cells());
					}
		if (!bad.isEmpty()) fail(bad.size() + " of " + n + " cases fail, first: " + String.join("\n", bad.subList(0, Math.min(10, bad.size()))));
	}

	@Test
	void fixtureShellsHaveTheExpectedShape() {
		Map<String, Massing> all = TestMassings.all();
		BuildingIr setback = TestMassings.shell(all.get("setback"), 1, "plain");
		assertEquals(10, setback.storeys().size());
		assertEquals(3, setback.volumes().size());
		assertEquals(9, setback.openings().stream().filter(o -> o.kind() == OpeningKind.STAIR).count(), "one flight per storey change");
		assertEquals(1, setback.openings().stream().filter(o -> o.kind() == OpeningKind.DOOR && o.to().equals("outside")).count());
		BuildingIr courtyard = TestMassings.shell(all.get("courtyard"), 1, "plain");
		// 4 strips x 2 storeys
		assertEquals(8, courtyard.openings().stream().filter(o -> o.kind() == OpeningKind.DOOR && !o.to().equals("outside")).count());
		BuildingIr hall = TestMassings.shell(all.get("hall"), 1, "plain");
		assertEquals(3, hall.rooms().size(), "hall (one room) + annex (two storeys)");
		assertTrue(hall.roofs().stream().anyMatch(r -> r.volume().equals("hall0") && r.form().equals("gable")));
	}

	@Test
	void rowUnitsHaveTheirOwnDoorsAndNoDoorsThroughPartyWalls() {
		BuildingIr row = TestMassings.shell(TestMassings.all().get("row"), 1, "plain");
		assertEquals(3, row.openings().stream().filter(o -> o.kind() == OpeningKind.DOOR && o.to().equals("outside")).count());
		assertEquals(0, row.openings().stream().filter(o -> o.kind() == OpeningKind.DOOR && !o.to().equals("outside")).count());
	}

	@Test
	void sameFixtureSameBytes() {
		for (Massing m : TestMassings.all().values()) {
			String a = IrJson.write(TestMassings.scene(m, 7, "medieval", GROUND.get("slope")));
			String b = IrJson.write(TestMassings.scene(m, 7, "medieval", GROUND.get("slope")));
			assertEquals(a, b);
			assertEquals(TestMassings.scene(m, 7, "medieval", GROUND.get("slope")), IrJson.read(a), "round trip");
		}
	}
}
