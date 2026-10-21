package dev.fractalbuildings.core.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The additive fb-scene/1 keys of district scenes: terrain.water and top-level ground boxes. */
class DistrictSceneIrTest {
	private static SceneDoc scene(boolean water, boolean ground) {
		TerrainPatch t = new TerrainPatch(0, 0, 2, 2, new int[] {60, 61, 62, 63}, water ? new int[] {0, 3, 0, 0} : null);
		List<Box> g = ground ? List.of(new Box(new Box3(0, 63, 0, 2, 1, 2), Role.ROAD, Layer.GROUND, -1), new Box(new Box3(0, 60, 0, 1, 1, 1), Role.GROUND, Layer.GROUND, -1),
				new Box(new Box3(1, 63, 1, 1, 1, 1), Role.PAVING, Layer.GROUND, -1)) : List.of();
		return new SceneDoc(2, 7, t, List.of(), List.of(), g, List.of());
	}

	@Test
	void oldScenesAreUnchanged() {
		String j = IrJson.write(scene(false, false));
		assertFalse(j.contains("\"water\"") || j.contains("\"ground\""), j);
		assertEquals(j, IrJson.write(IrJson.read(j)));
	}

	@Test
	void waterAndGroundRoundTrip() {
		SceneDoc s = scene(true, true);
		String j = IrJson.write(s);
		assertTrue(j.contains("\"water\": [0, 3, 0, 0]") && j.contains("\"role\": \"paving\", \"layer\": \"ground\", \"storey\": -1"), j);
		assertTrue(j.indexOf("\"buildings\"") < j.indexOf("\"ground\"") && j.indexOf("\"ground\"") < j.indexOf("\"violations\""));
		SceneDoc back = IrJson.read(j);
		assertEquals(s, back);
		assertEquals(3, back.terrain().waterDepth(1, 0));
		assertEquals(j, IrJson.write(back));
		assertTrue(IrValidator.validate(back).isEmpty());
	}

	@Test
	void newRolesAreSolidAndClosed() {
		for (Role r : new Role[] {Role.ROAD, Role.PAVING, Role.GROUND}) assertTrue(r.solid && r.closesShell && r.json().equals(r.name().toLowerCase()));
	}

	@Test
	void validatorAndReaderCheckGroundBoxes() {
		SceneDoc bad = new SceneDoc(2, 7, null, List.of(), List.of(), List.of(new Box(new Box3(0, 0, 0, 1, 1, 1), Role.ROAD, Layer.SHELL, 0)), List.of());
		assertEquals(2, IrValidator.validate(bad).size());
		String j = IrJson.write(scene(true, true));
		assertThrows(IllegalArgumentException.class, () -> IrJson.read(j.replace("\"water\": [0, 3, 0, 0]", "\"water\": [0, 3, 0]")));
		assertThrows(IllegalArgumentException.class, () -> IrJson.read(j.replace("\"water\": [0, 3, 0, 0]", "\"water\": [0, -3, 0, 0]")));
		assertThrows(IllegalArgumentException.class, () -> IrJson.read(j.replace("\"role\": \"road\"", "\"role\": \"asphalt\"")));
	}
}
