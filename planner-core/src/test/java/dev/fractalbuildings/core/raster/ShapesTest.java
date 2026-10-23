package dev.fractalbuildings.core.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SampleScenes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ShapesTest {
	record Cell(Role role, String id, Shape shape) {}

	static Box box(int x, int y, int z, int sx, int sy, int sz, Role r) { return new Box(Box3.of(x, y, z, sx, sy, sz), r, Layer.SHELL, 0); }

	/** The sample house's frame with our own boxes and openings, at a rotation. */
	static PlacedBuilding make(int rotation, List<Box> boxes, List<Opening> openings) {
		PlacedBuilding b = SampleScenes.house().buildings().get(0);
		BuildingIr s = b.shell();
		BuildingIr shell = new BuildingIr(s.spec(), s.bounds(), s.storeys(), s.volumes(), s.rooms(), openings, s.roofs(), boxes);
		return new PlacedBuilding("t", b.lots(), 100, 64, 200, rotation, shell, List.of());
	}

	static Map<String, Cell> raster(PlacedBuilding b) {
		Map<String, Cell> m = new TreeMap<>();
		PieceRaster.building(b, Box3.of(-1000, -100, -1000, 3000, 600, 3000), (x, y, z, r, id, sh) -> m.put(x + "," + y + "," + z, new Cell(r, id, sh)));
		return m;
	}

	@Test
	void stairFlightClimbsInTheOpeningsDirectionWhateverTheRotation() {
		// steps rise towards local +x: (0,0), (1,1), (2,2), then the floor at x 3, y 3
		List<Box> boxes = List.of(box(0, 0, 0, 1, 1, 1, Role.STAIR), box(1, 1, 0, 1, 1, 1, Role.STAIR), box(2, 2, 0, 1, 1, 1, Role.STAIR), box(3, 3, 0, 2, 1, 1, Role.FLOOR));
		Opening run = new Opening("s", OpeningKind.STAIR, Box3.of(0, 0, 0, 3, 3, 1), Facing.EAST, "a", "b");
		for (int r = 0; r < 4; r++) {
			PlacedBuilding b = make(r, boxes, List.of(run));
			Facing up = Facing.EAST.rotate(r);
			var p = b.placement();
			for (int k = 0; k < 3; k++) {
				int wx = p.worldX(k, 0), wz = p.worldZ(k, 0), wy = p.worldY(k);
				Cell c = raster(b).get(wx + "," + wy + "," + wz);
				assertEquals(Role.STAIR, c.role());
				assertEquals(Shape.stair(up), c.shape(), "rotation " + r + " step " + k);
			}
		}
	}

	@Test
	void stairWithoutAnOpeningFindsItsNextStepNotAWall() {
		List<Box> boxes = List.of(box(0, 0, 0, 1, 1, 1, Role.STAIR), box(0, 1, 1, 1, 1, 1, Role.STAIR), box(0, 2, 2, 1, 1, 1, Role.STAIR),
				box(0, 3, 3, 1, 1, 1, Role.FLOOR), box(1, 1, 0, 1, 2, 1, Role.WALL), box(1, 0, 0, 1, 1, 1, Role.WALL));
		for (int r = 0; r < 4; r++) {
			PlacedBuilding b = make(r, boxes, List.of());
			var p = b.placement();
			Cell c = raster(b).get(p.worldX(0, 0) + "," + p.worldY(0) + "," + p.worldZ(0, 0));
			assertEquals(Shape.stair(Facing.SOUTH.rotate(r)), c.shape(), "rotation " + r);
		}
	}

	@Test
	void aStepWithNothingHigherStaysAFullBlock() {
		PlacedBuilding b = make(0, List.of(box(0, 0, 0, 1, 1, 1, Role.STAIR)), List.of());
		assertEquals(Shape.NONE, raster(b).get("100,64,200").shape());
	}

	@Test
	void doorsFaceTheWalkDirectionHaveHalvesAndHingesOfADoubleDoor() {
		// a wall along x with a 1-wide door at x 2 and a 2-wide doorway at x 5..6, walked from the front (z 0) to the back
		List<Box> boxes = List.of(box(0, 0, 0, 10, 4, 1, Role.WALL), box(2, 1, 0, 1, 2, 1, Role.DOOR), box(5, 1, 0, 2, 2, 1, Role.DOOR));
		List<Opening> ops = List.of(new Opening("d1", OpeningKind.DOOR, Box3.of(2, 1, 0, 1, 2, 1), Facing.SOUTH, Opening.OUTSIDE, "r"),
				new Opening("d2", OpeningKind.DOOR, Box3.of(5, 1, 0, 2, 2, 1), Facing.SOUTH, Opening.OUTSIDE, "r"));
		for (int r = 0; r < 4; r++) {
			PlacedBuilding b = make(r, boxes, ops);
			var p = b.placement();
			Map<String, Cell> m = raster(b);
			Facing walk = Facing.SOUTH.rotate(r);
			Cell single = m.get(p.worldX(2, 0) + "," + p.worldY(1) + "," + p.worldZ(2, 0));
			assertEquals(Shape.door(walk, Shape.Half.LOWER, Shape.Hinge.LEFT), single.shape(), "rot " + r);
			assertEquals(Shape.door(walk, Shape.Half.UPPER, Shape.Hinge.LEFT), m.get(p.worldX(2, 0) + "," + p.worldY(2) + "," + p.worldZ(2, 0)).shape());
			// the walker's left: facing south the left hand is east (+x local)
			Cell a = m.get(p.worldX(5, 0) + "," + p.worldY(1) + "," + p.worldZ(5, 0)), c = m.get(p.worldX(6, 0) + "," + p.worldY(1) + "," + p.worldZ(6, 0));
			assertEquals(Shape.Hinge.RIGHT, a.shape().hinge(), "local x 5 is the walker's right (west) side, rot " + r);
			assertEquals(Shape.Hinge.LEFT, c.shape().hinge(), "rot " + r);
			assertEquals(a.shape().facing(), c.shape().facing());
		}
	}

	@Test
	void railConnectsLikeAFence() {
		PlacedBuilding b = make(0, List.of(box(0, 0, 0, 3, 1, 1, Role.RAIL)), List.of());
		Map<String, Cell> m = raster(b);
		assertEquals(Shape.E | Shape.W, m.get("101,64,200").shape().connect());
		assertEquals(Shape.E, m.get("100,64,200").shape().connect());
	}

	@Test
	void roomPartsAreBuildingAirThatGroundYieldsTo() {
		PlacedBuilding b = make(0, List.of(box(0, 0, 0, 1, 1, 1, Role.WALL)), List.of());
		var room = b.shell().rooms().get(0);
		Box3 part = b.placement().toWorld(room.parts().get(0));
		Box3 all = Box3.of(part.x() - 3, part.y() - 3, part.z() - 3, part.sx() + 6, part.sy() + 6, part.sz() + 6);
		Map<String, Cell> m = raster(b);
		Map<String, String> owner = new TreeMap<>();
		PieceRaster.ground(List.of(new Box(all, Role.GROUND, Layer.SHELL, 0)), List.of(b), (x, z) -> "plain", all, (x, y, z, r, id, s) -> owner.put(x + "," + y + "," + z, r.toString()));
		int n = 0;
		for (int y = part.y(); y < part.maxY(); y++) for (int z = part.z(); z < part.maxZ(); z++) for (int x = part.x(); x < part.maxX(); x++) {
			String k = x + "," + y + "," + z;
			assertNull(owner.get(k), "ground under room air at " + k);
			if (!(x == 100 && y == 64 && z == 200)) assertEquals(Role.AIR, m.get(k).role(), k);
			n++;
		}
		assertTrue(n > 10);
		assertEquals("GROUND", owner.get((part.x() - 1) + "," + part.y() + "," + part.z()));
	}

	@Test
	void fencesAndPanesConnectToSolidNeighboursOnly() {
		// fence run along x from 0..3, wall at x 4, a window pane at (6,1,0) between walls at 5 and 7
		List<Box> boxes = List.of(box(0, 0, 0, 4, 1, 1, Role.FENCE), box(4, 0, 0, 1, 1, 1, Role.WALL), box(5, 1, 0, 3, 1, 1, Role.WALL), box(6, 1, 0, 1, 1, 1, Role.WINDOW));
		PlacedBuilding b = make(0, boxes, List.of());
		Map<String, Cell> m = raster(b);
		String id = m.get("102,64,200").id();
		assertTrue(id.endsWith("fence"), id);
		assertEquals(Shape.E | Shape.W, m.get("102,64,200").shape().connect());
		assertEquals(Shape.E, m.get("100,64,200").shape().connect());
		assertEquals(Shape.E | Shape.W, m.get("103,64,200").shape().connect());
		Cell pane = m.get("106,65,200");
		assertEquals(Shape.E | Shape.W, pane.shape().connect(), pane.id());
		// rotated a quarter turn the same fence connects north and south
		PlacedBuilding r1 = make(1, boxes, List.of());
		var p = r1.placement();
		assertEquals(Shape.N | Shape.S, raster(r1).get(p.worldX(2, 0) + "," + p.worldY(0) + "," + p.worldZ(2, 0)).shape().connect());
	}

	@Test
	void logsTakeTheLongAxisOfTheirBox() {
		List<Box> boxes = List.of(box(0, 0, 0, 1, 5, 1, Role.BEAM), box(0, 5, 0, 6, 1, 1, Role.TRIM), box(0, 5, 3, 1, 1, 4, Role.TRIM));
		for (int r = 0; r < 4; r++) {
			PlacedBuilding b = make(r, boxes, List.of());
			var p = b.placement();
			Map<String, Cell> m = raster(b);
			Cell post = m.get(p.worldX(0, 0) + "," + p.worldY(2) + "," + p.worldZ(0, 0));
			Cell railX = m.get(p.worldX(3, 0) + "," + p.worldY(5) + "," + p.worldZ(3, 0));
			Cell railZ = m.get(p.worldX(0, 5) + "," + p.worldY(5) + "," + p.worldZ(0, 5));
			if (!Shapes.axisBlock(post.id())) continue; // plain style: planks
			assertEquals(Shape.Axis3.Y, post.shape().axis());
			boolean odd = r % 2 == 1;
			assertEquals(odd ? Shape.Axis3.Z : Shape.Axis3.X, railX.shape().axis(), "rot " + r);
			assertEquals(odd ? Shape.Axis3.X : Shape.Axis3.Z, railZ.shape().axis(), "rot " + r);
		}
	}

	@Test
	void plainBlocksHaveNoShape() {
		PlacedBuilding b = make(0, List.of(box(0, 0, 0, 3, 1, 3, Role.FLOOR), box(0, 1, 0, 3, 3, 1, Role.WALL), box(0, 4, 0, 3, 1, 3, Role.ROOF)), List.of());
		for (Cell c : raster(b).values()) assertEquals(Shape.NONE, c.shape());
		assertNull(Shape.NONE.facing());
	}
}
