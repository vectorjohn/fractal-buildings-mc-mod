package dev.fractalbuildings.core.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SampleScenesTest {
	@Test
	void sampleHouseIsInsideItsBoundsAndLot() {
		SceneDoc s = SampleScenes.house();
		PlacedBuilding b = s.buildings().get(0);
		for (Box box : b.shell().boxes()) assertTrue(b.shell().bounds().contains(box.box()), box.toString());
		Placement p = b.placement();
		Lot lot = s.lots().get(0);
		Box3 world = p.toWorld(b.shell().bounds());
		assertTrue(lot.containsColumn(world.x(), world.z()) && lot.containsColumn(world.maxX() - 1, world.maxZ() - 1));
		assertEquals(64, s.terrain().floorY(100, -38));
		assertEquals(63, s.terrain().floorY(100, -40));
		assertEquals(22, b.shell().boxes().size());
	}
}
