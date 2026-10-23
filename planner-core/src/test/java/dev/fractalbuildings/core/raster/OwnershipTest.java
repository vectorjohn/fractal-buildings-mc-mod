package dev.fractalbuildings.core.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class OwnershipTest {
	static final Box3 ALL = Box3.of(-1000, -100, -1000, 3000, 600, 3000);

	/** Both buildings at (100, 64, 200) rotation 0 and 90 deg; ground in the world frame. */
	@Test
	void groundYieldsToBuildingsAndEarlierBuildingsWin() {
		PlacedBuilding a = ShapesTest.make(0, List.of(ShapesTest.box(0, 0, 0, 4, 1, 4, Role.FLOOR), ShapesTest.box(1, 0, 1, 2, 1, 2, Role.AIR)), List.of());
		PlacedBuilding b = ShapesTest.make(0, List.of(ShapesTest.box(2, 0, 0, 4, 1, 4, Role.WALL)), List.of());
		List<Box> ground = List.of(new Box(Box3.of(96, 64, 196, 12, 1, 12), Role.ROAD, Layer.GROUND, Box.NO_STOREY));
		Map<String, String> owner = new TreeMap<>();
		Map<String, Role> role = new TreeMap<>();
		for (int i = 0; i < 2; i++) {
			String o = "b" + i;
			PieceRaster.building(List.of(a, b), i, ALL, (x, y, z, r, id, s) -> { assertEquals(null, owner.put(x + "," + y + "," + z, o)); role.put(x + "," + y + "," + z, r); });
		}
		PieceRaster.ground(ground, List.of(a, b), (x, z) -> "plain", ALL, (x, y, z, r, id, s) -> { assertEquals(null, owner.put(x + "," + y + "," + z, "g")); role.put(x + "," + y + "," + z, r); });
		// a's floor, its carved hole (air, still a's), b's wall where a has nothing, the road everywhere else
		assertEquals("b0", owner.get("100,64,200"));
		assertEquals(Role.AIR, role.get("101,64,201"));
		assertEquals("b0", owner.get("101,64,201"));
		assertEquals("b0", owner.get("103,64,200"));
		assertEquals(Role.FLOOR, role.get("103,64,200"));
		assertEquals("b1", owner.get("105,64,200"));
		assertEquals("g", owner.get("96,64,196"));
		assertEquals("g", owner.get("105,64,205"));
		assertEquals(Role.ROAD, role.get("105,64,205"));
		// every column of the 12 x 12 road area is owned once
		for (int x = 96; x < 108; x++) for (int z = 196; z < 208; z++) assertEquals(true, owner.containsKey(x + ",64," + z), x + "," + z);
	}
}
