package dev.fractalbuildings.core.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class IrValidatorTest {
	private static final SceneDoc OK = SampleScenes.house();

	private static SceneDoc withShell(UnaryOperator<BuildingIr> f) {
		PlacedBuilding b = OK.buildings().get(0);
		PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), f.apply(b.shell()), b.siteBoxes());
		return new SceneDoc(OK.genVersion(), OK.seed(), OK.terrain(), OK.lots(), List.of(nb), OK.violations());
	}

	private static BuildingIr shell(BuildingIr s, List<Storey> st, List<Volume> v, List<Room> r, List<Opening> o, List<Roof> ro, List<Box> bx) {
		return new BuildingIr(s.spec(), s.bounds(), st, v, r, o, ro, bx);
	}

	private static void expect(SceneDoc d, String fragment) {
		List<Violation> v = IrValidator.validate(d);
		assertTrue(v.stream().anyMatch(x -> x.invariant().equals("ir-valid") && x.message().contains(fragment)), "wanted '" + fragment + "' in " + v);
	}

	@Test
	void samplePasses() {
		assertEquals(List.of(), IrValidator.validate(OK));
	}

	@Test
	void duplicateRoomId() {
		expect(withShell(s -> {
			List<Room> r = new ArrayList<>(s.rooms());
			r.add(r.get(0));
			return shell(s, s.storeys(), s.volumes(), r, s.openings(), s.roofs(), s.boxes());
		}), "duplicate room id r0");
	}

	@Test
	void unknownLot() {
		PlacedBuilding b = OK.buildings().get(0);
		PlacedBuilding nb = new PlacedBuilding("b0", List.of("nope"), 0, 0, 0, 0, b.shell(), List.of());
		expect(new SceneDoc(1, 1, null, OK.lots(), List.of(nb), List.of()), "unknown lot nope");
	}

	@Test
	void openingReferencesUnknownRoom() {
		expect(withShell(s -> {
			List<Opening> o = new ArrayList<>(s.openings());
			Opening x = o.get(0);
			o.set(0, new Opening(x.id(), x.kind(), x.box(), x.facing(), "r9", x.to()));
			return shell(s, s.storeys(), s.volumes(), s.rooms(), o, s.roofs(), s.boxes());
		}), "unknown room r9");
	}

	@Test
	void roomUnknownVolumeAndRoofUnknownVolume() {
		expect(withShell(s -> shell(s, s.storeys(), s.volumes(),
				List.of(new Room("r0", 0, "vX", List.of(Box3.of(1, 1, 1, 1, 1, 1)), "room")), List.of(), s.roofs(), s.boxes())), "unknown volume vX");
		expect(withShell(s -> shell(s, s.storeys(), s.volumes(), s.rooms(), s.openings(),
				List.of(new Roof("gable", "vY", Axis.X, 5, 1, 1)), s.boxes())), "roof references unknown volume vY");
	}

	@Test
	void boxOutsideBounds() {
		expect(withShell(s -> {
			List<Box> b = new ArrayList<>(s.boxes());
			b.add(new Box(Box3.of(6, 0, 0, 2, 1, 1), Role.WALL, Layer.SHELL, 0));
			return shell(s, s.storeys(), s.volumes(), s.rooms(), s.openings(), s.roofs(), b);
		}), "outside bounds");
	}

	@Test
	void storeysNotContiguous() {
		expect(withShell(s -> shell(s, List.of(new Storey(0, 0, 4), new Storey(1, 6, 4)), s.volumes(), s.rooms(), s.openings(), s.roofs(), s.boxes())),
				"storey 1 starts at y=6, expected 4");
		expect(withShell(s -> shell(s, List.of(new Storey(1, 0, 4)), s.volumes(), s.rooms(), s.openings(), s.roofs(), s.boxes())),
				"storey 0 has index 1");
	}

	@Test
	void boxStoreyUnknown() {
		expect(withShell(s -> shell(s, s.storeys(), s.volumes(), s.rooms(), s.openings(), s.roofs(),
				List.of(new Box(Box3.of(0, 0, 0, 1, 1, 1), Role.WALL, Layer.SHELL, 5)))), "unknown storey 5");
	}
}
