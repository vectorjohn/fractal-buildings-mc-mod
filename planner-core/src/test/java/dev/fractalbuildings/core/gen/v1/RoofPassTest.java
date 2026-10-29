package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.style.RoofStyle;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Roofs close over every fixture massing, for every form and pitch (plan step R). */
class RoofPassTest {
	static final List<String> FORMS = List.of("gable", "hip", "flat", "shed", "mansard", "pyramid");
	static final int[][] PITCHES = {{1, 1}, {2, 1}, {1, 2}};
	static final TerrainProbe FLAT = SyntheticTerrain.flat(64);

	static StyleRules withRoof(String base, RoofStyle roof) {
		StyleRules s = StyleLibrary.get(base);
		return new StyleRules(s.id(), s.biomes(), s.massing(), roof, s.facade(), s.interior(), s.palette(), s.colours());
	}

	static RoofStyle roof(String form, int rise, int run, int overhang, int dormers) {
		return new RoofStyle(Map.of(form, 1), rise, run, overhang, 1, "wall", 99, dormers);
	}

	/** Like {@link TestMassings#scene} but with an explicit style object and spec. */
	static SceneDoc scene(BuildingSpec spec, Massing m, StyleRules style) {
		BuildingIr shell = ShellGeneratorV1.generate(spec, new TestMassings.FixtureType(m), style);
		Lot lot = new Lot("lot0", 0, 0, spec.width() + 2, spec.depth() + 2, Facing.NORTH);
		GenRequest req = new GenRequest(spec.specSeed(), lot, "fixture", spec.style(), FLAT, (x, z) -> "minecraft:plains");
		PlacedBuilding b = InstancePassV1.place(shell, req, TestMassings.FixtureType.LIMITS);
		TerrainPatch patch = TerrainPatch.sample(FLAT, lot.x() - 2, lot.z() - 2, lot.w() + 4, lot.d() + 4);
		return new SceneDoc(GeneratorV1.GEN_VERSION, spec.specSeed(), patch, List.of(lot), List.of(b), List.of());
	}

	@Test
	void everyFixtureFormAndPitchPassesAllInvariants() {
		List<String> bad = new ArrayList<>();
		int n = 0;
		for (Map.Entry<String, Massing> f : TestMassings.all().entrySet())
			for (String form : FORMS)
				for (int[] p : PITCHES)
					for (long seed = 0; seed < 10; seed++) {
						StyleRules style = withRoof(seed % 2 == 0 ? "plain" : "medieval", roof(form, p[0], p[1], (int) (seed % 3), 100));
						SceneDoc s = scene(TestMassings.spec(seed, style.id()), f.getValue(), style);
						List<Violation> v = Invariants.check(s);
						n++;
						if (!v.isEmpty()) bad.add(f.getKey() + " " + form + " " + p[0] + "/" + p[1] + " seed " + seed + ": " + v.get(0).invariant() + " " + v.get(0).message() + " " + v.get(0).cells());
					}
		if (!bad.isEmpty()) fail(bad.size() + " of " + n + " cases fail, first:\n" + String.join("\n", bad.subList(0, Math.min(10, bad.size()))));
	}

	@Test
	void everyRoofedVolumeHasARecordAndTheFormMatchesTheStyle() {
		for (Map.Entry<String, Massing> f : TestMassings.all().entrySet()) {
			StyleRules style = withRoof("plain", roof("hip", 1, 1, 0, 0));
			BuildingIr ir = ShellGeneratorV1.generate(TestMassings.spec(1, "plain"), new TestMassings.FixtureType(f.getValue()), style);
			assertFalse(ir.roofs().isEmpty(), f.getKey());
			for (Roof r : ir.roofs()) assertTrue(r.form().equals("hip") || r.form().equals("flat"), f.getKey() + " " + r.form());
		}
	}

	@Test
	void tightMaxHeightFallsBackWithoutThrowing() {
		for (Map.Entry<String, Massing> f : TestMassings.all().entrySet()) {
			int top = 0;
			for (Mass m : f.getValue().masses()) top = Math.max(top, m.topSlab(f.getValue().grid()));
			for (String form : FORMS)
				for (int extra = 2; extra <= 4; extra++) {
					StyleRules style = withRoof("plain", roof(form, 2, 1, 1, 100));
					BuildingSpec s = TestMassings.spec(3, "plain");
					BuildingSpec tight = new BuildingSpec(s.genVersion(), s.specSeed(), s.style(), s.type(), s.width(), s.depth(), s.storeys(), s.roof(), top + extra, s.streets());
					SceneDoc doc = scene(tight, f.getValue(), style);
					List<Violation> v = Invariants.check(doc);
					assertTrue(v.isEmpty(), f.getKey() + " " + form + " maxHeight " + (top + extra) + ": " + (v.isEmpty() ? "" : v.get(0).invariant() + " " + v.get(0).message()));
					BuildingIr ir = ShellGeneratorV1.generate(tight, new TestMassings.FixtureType(f.getValue()), style);
					assertTrue(ir.bounds().maxY() <= top + extra);
				}
		}
	}

	@Test
	void overhangIsClippedToTheEnvelope() {
		// a building flush with the envelope's west and north edges: overhang cells there don't exist
		Massing m = new Massing(TestMassings.grid(1), List.of(TestMassings.g("main0", "main", 0, 0, 9, 7, 1)), List.of());
		for (String form : FORMS) {
			StyleRules style = withRoof("plain", roof(form, 1, 1, 2, 0));
			BuildingIr ir = ShellGeneratorV1.generate(TestMassings.spec(1, "plain"), new TestMassings.FixtureType(m), style);
			for (Box b : ir.boxes()) assertTrue(b.box().x() >= 0 && b.box().z() >= 0, form + " " + b);
		}
	}

	@Test
	void overhangAddsRoofBeyondTheWallsAtTheTopSlab() {
		Massing m = TestMassings.all().get("box1");
		int slab = m.main().topSlab(m.grid());
		BuildingIr none = ShellGeneratorV1.generate(TestMassings.spec(1, "plain"), new TestMassings.FixtureType(m), withRoof("plain", roof("hip", 1, 1, 0, 0)));
		BuildingIr two = ShellGeneratorV1.generate(TestMassings.spec(1, "plain"), new TestMassings.FixtureType(m), withRoof("plain", roof("hip", 1, 1, 2, 0)));
		long a = none.boxes().stream().filter(b -> b.role().isRoof() && b.box().y() == slab).count();
		long bb = two.boxes().stream().filter(b -> b.role().isRoof() && b.box().y() == slab).count();
		assertEquals(0, a);
		assertTrue(bb > 0);
	}

	@Test
	void dormersAreClosedWindowBoxesOnStreetSlopes() {
		Massing m = new Massing(TestMassings.grid(2), List.of(TestMassings.g("main0", "main", 4, 4, 14, 11, 2)), List.of(new dev.fractalbuildings.core.gen.v1.model.Core(6, 9, 5, 2, 0, 2)));
		StyleRules style = withRoof("plain", roof("gable", 1, 1, 0, 100));
		int found = 0;
		for (long seed = 0; seed < 8; seed++) {
			SceneDoc s = scene(TestMassings.spec(seed, "plain"), m, style);
			assertEquals(List.of(), Invariants.check(s));
			BuildingIr ir = ShellGeneratorV1.generate(TestMassings.spec(seed, "plain"), new TestMassings.FixtureType(m), style);
			found += (int) ir.boxes().stream().filter(b -> b.role() == Role.WINDOW && b.layer() == Layer.ROOF).count();
		}
		assertTrue(found > 0, "dormer windows appear");
		// off by default
		BuildingIr off = ShellGeneratorV1.generate(TestMassings.spec(1, "plain"), new TestMassings.FixtureType(m), withRoof("plain", roof("gable", 1, 1, 0, 0)));
		assertEquals(0, off.boxes().stream().filter(b -> b.role() == Role.WINDOW && b.layer() == Layer.ROOF).count());
	}

	@Test
	void tallAndPartlyCoveredGroupsGetFlatRoofsWithParapets() {
		Massing m = TestMassings.all().get("setback");
		BuildingIr ir = TestMassings.shell(m, 1, "medieval");
		for (Roof r : ir.roofs()) assertEquals("flat", r.form(), r.volume());
		assertEquals(3, ir.roofs().size(), "podium ring, shaft ring, crown top");
		assertTrue(ir.boxes().stream().anyMatch(b -> b.layer() == Layer.ROOF && b.role() == Role.WALL && b.box().sy() >= 1 && b.box().y() > m.main().topSlab(m.grid())));
		BuildingIr fence = ShellGeneratorV1.generate(TestMassings.spec(1, "plain"), new TestMassings.FixtureType(m), withRoof("plain", new RoofStyle(Map.of("gable", 1), 1, 1, 0, 1, "fence", 3, 0)));
		assertTrue(fence.boxes().stream().anyMatch(b -> b.role() == Role.FENCE));
	}

	@Test
	void wingsOfTheSameTopShareOneRoofGroup() {
		// T with gables: both masses are roofed as gables, and the wing's ridge runs on into the main roof
		Massing m = TestMassings.all().get("T");
		StyleRules style = withRoof("plain", roof("gable", 1, 1, 0, 0));
		BuildingIr ir = ShellGeneratorV1.generate(TestMassings.spec(1, "plain"), new TestMassings.FixtureType(m), style);
		assertEquals(2, ir.roofs().size());
		assertEquals(List.of("gable", "gable"), ir.roofs().stream().map(Roof::form).toList());
		assertTrue(ir.roofs().stream().map(Roof::ridgeAxis).distinct().count() == 2, "crossed ridges");
	}

	@Test
	void rowUnitsKeepTheirOwnRoofs() {
		Massing m = TestMassings.all().get("row");
		BuildingIr ir = ShellGeneratorV1.generate(TestMassings.spec(1, "plain"), new TestMassings.FixtureType(m), withRoof("plain", roof("gable", 1, 1, 0, 0)));
		assertEquals(3, ir.roofs().size());
		// gable ends at the party walls: wall voxels in the roof layer at the shared columns' neighbours
		assertTrue(ir.boxes().stream().filter(b -> b.layer() == Layer.ROOF && b.role() == Role.WALL).count() >= 6);
	}

	@Test
	void deterministic() {
		for (Massing m : TestMassings.all().values()) {
			StyleRules style = withRoof("medieval", roof("gable", 1, 1, 1, 100));
			BuildingIr a = ShellGeneratorV1.generate(TestMassings.spec(5, "medieval"), new TestMassings.FixtureType(m), style);
			BuildingIr b = ShellGeneratorV1.generate(TestMassings.spec(5, "medieval"), new TestMassings.FixtureType(m), style);
			assertEquals(a, b);
		}
	}

	/**
	 * Review aid: writes every fixture once per roof form as viewer batches
	 * ({@code -Dfb.dumpRoofForms=DIR}, then {@code gallery.html?dir=roof-<form>}).
	 */
	@Test
	@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "fb.dumpRoofForms", matches = ".+")
	void dumpForms() throws Exception {
		for (String form : FORMS) {
			java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("fb.dumpRoofForms"), "roof-" + form);
			java.nio.file.Files.createDirectories(dir);
			List<String> seeds = new ArrayList<>();
			int i = 0;
			for (Map.Entry<String, Massing> e : TestMassings.all().entrySet()) {
				StyleRules style = withRoof("medieval", new RoofStyle(Map.of(form, 1), 1, 1, 1, 1, "wall", 6, 60));
				SceneDoc s = scene(TestMassings.spec(i, "medieval"), e.getValue(), style);
				s = new SceneDoc(s.genVersion(), i, s.terrain(), s.lots(), s.buildings(), Invariants.check(s));
				java.nio.file.Files.writeString(dir.resolve("seed-" + i + ".json"), dev.fractalbuildings.core.ir.IrJson.write(s));
				seeds.add(Integer.toString(i++));
			}
			java.nio.file.Files.writeString(dir.resolve("index.json"), "{\"type\": \"fixture\", \"style\": \"medieval\", \"names\": \""
					+ String.join(",", TestMassings.all().keySet()) + "\", \"seeds\": [" + String.join(", ", seeds) + "]}\n");
		}
	}
}
