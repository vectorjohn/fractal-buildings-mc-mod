package dev.fractalbuildings.core.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Role;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** DS-36: roof stairs take Minecraft's corner shape from the roof stairs beside them in the grid; slabs get their half. */
class RoofStairShapeTest {
	static Shapes.RoleView view(Map<String, Role> m) {
		return (x, y, z) -> m.getOrDefault(x + "," + y + "," + z, Role.AIR);
	}

	static Shape at(Map<String, Role> m, int x, int z) {
		Role r = m.get(x + ",0," + z);
		return Shapes.of(view(m), x, 0, z, r, "minecraft:bricks", Shapes.Hints.NONE);
	}

	@Test
	void hipCornerPinwheelGivesOuterCornerOnTheDiagonalAndStraightEaves() {
		// the north-west corner of a hip: eave corner faces east, the eave row faces south, the west eave column faces east, ring 1 corner faces south
		Map<String, Role> m = new HashMap<>();
		m.put("0,0,0", Role.ROOF_STAIR_E);
		m.put("1,0,0", Role.ROOF_STAIR_S);
		m.put("2,0,0", Role.ROOF_STAIR_S);
		m.put("0,0,1", Role.ROOF_STAIR_E);
		m.put("0,0,2", Role.ROOF_STAIR_E);
		m.put("1,0,1", Role.ROOF_STAIR_S);
		m.put("1,0,2", Role.ROOF_STAIR_E);
		m.put("2,0,1", Role.ROOF_STAIR_S);
		assertEquals(Shape.stair(Facing.EAST, Shape.Corner.OUTER_RIGHT), at(m, 0, 0), "eave corner");
		assertEquals(Shape.stair(Facing.SOUTH), at(m, 1, 0), "eave row stays straight");
		assertEquals(Shape.stair(Facing.EAST), at(m, 0, 1), "eave column stays straight");
		assertEquals(Shape.stair(Facing.SOUTH, Shape.Corner.OUTER_LEFT), at(m, 1, 1), "ring 1 corner");
	}

	@Test
	void aStairBehindTurnedAcrossMakesAnInnerCorner() {
		Map<String, Role> m = new HashMap<>();
		m.put("0,0,0", Role.ROOF_STAIR_E);
		m.put("-1,0,0", Role.ROOF_STAIR_S); // behind, facing across
		assertEquals(Shape.stair(Facing.EAST, Shape.Corner.INNER_RIGHT), at(m, 0, 0));
	}

	@Test
	void slabsAreHalves() {
		Map<String, Role> m = Map.of("0,0,0", Role.ROOF_SLAB, "1,0,0", Role.ROOF_SLAB_TOP);
		assertEquals(Shape.slab(Shape.Half.BOTTOM), at(m, 0, 0));
		assertEquals(Shape.slab(Shape.Half.TOP), at(m, 1, 0));
	}

	@Test
	void turningABuildingTurnsItsRoofStairs() {
		for (Facing f : Facing.values()) assertEquals(Role.roofStair(f.rotate(1)), Role.roofStair(f).turned(1));
		assertEquals(Role.ROOF, Role.ROOF.turned(3));
		assertEquals(Role.ROOF_SLAB, Role.ROOF_SLAB.turned(2));
	}

	/** A bare shell (the sample house's frame, no rooms, no openings) of the given boxes at a world origin; boxes must lie inside its bounds. */
	static dev.fractalbuildings.core.ir.PlacedBuilding bare(int ox, int oz, dev.fractalbuildings.core.ir.Box... boxes) {
		var s = dev.fractalbuildings.core.ir.SampleScenes.house().buildings().get(0);
		var shell = new dev.fractalbuildings.core.ir.BuildingIr(s.shell().spec(), s.shell().bounds(), s.shell().storeys(), s.shell().volumes(), java.util.List.of(), java.util.List.of(), s.shell().roofs(), java.util.List.of(boxes));
		return new dev.fractalbuildings.core.ir.PlacedBuilding("t" + ox, s.lots(), ox, 64, oz, 0, shell, java.util.List.of());
	}

	static dev.fractalbuildings.core.ir.Box box(int x, int y, int z, Role r) {
		return new dev.fractalbuildings.core.ir.Box(dev.fractalbuildings.core.ir.Box3.of(x, y, z, 1, 1, 1), r, dev.fractalbuildings.core.ir.Layer.ROOF, 0);
	}

	static Shape cellOf(java.util.List<dev.fractalbuildings.core.ir.PlacedBuilding> all, int index, int x, int y, int z) {
		Shape[] out = {null};
		PieceRaster.building(all, index, dev.fractalbuildings.core.ir.Box3.of(x, y, z, 1, 1, 1), (cx, cy, cz, r, id, sh) -> out[0] = sh);
		return out[0];
	}

	/** DS36ROOFS r2: Minecraft counts every stair beside a roof stair, so the raster does too: another building's roof stair, whatever the clip. */
	@Test
	void anotherBuildingsStairBesideARoofStairTurnsItsCorner() {
		var a = bare(100, 200, box(0, 3, 0, Role.ROOF_STAIR_E));
		var b = bare(101, 200, box(0, 3, 0, Role.ROOF_STAIR_S));
		assertEquals(Shape.stair(Facing.EAST, Shape.Corner.OUTER_RIGHT), cellOf(java.util.List.of(a, b), 0, 100, 67, 200), "the later building's stair in front");
		assertEquals(Shape.stair(Facing.EAST, Shape.Corner.OUTER_RIGHT), cellOf(java.util.List.of(b, a), 1, 100, 67, 200), "the earlier building's stair in front");
		assertEquals(Shape.stair(Facing.EAST), cellOf(java.util.List.of(a), 0, 100, 67, 200), "alone it is straight");
	}

	/** An interior or porch stair counts too, with the facing its own rules give it (here: up to the step above). */
	@Test
	void anInteriorStairBesideARoofStairTurnsItsCorner() {
		var a = bare(100, 200, box(0, 3, 0, Role.ROOF_STAIR_E), box(1, 3, 0, Role.STAIR), box(1, 4, 1, Role.STAIR));
		assertEquals(Shape.stair(Facing.SOUTH), cellOf(java.util.List.of(a), 0, 101, 67, 200), "the interior stair climbs south");
		assertEquals(Shape.stair(Facing.EAST, Shape.Corner.OUTER_RIGHT), cellOf(java.util.List.of(a), 0, 100, 67, 200));
	}

	/** A cell an earlier building owns is that building's block: a stair of the later building's own boxes under it does not count. */
	@Test
	void aCellTakenByAnEarlierBuildingCountsAsItsBlock() {
		var a = bare(100, 200, box(0, 3, 0, Role.ROOF_STAIR_E), box(1, 3, 0, Role.ROOF_STAIR_S));
		var wall = bare(101, 200, new dev.fractalbuildings.core.ir.Box(dev.fractalbuildings.core.ir.Box3.of(0, 3, 0, 1, 1, 1), Role.WALL, dev.fractalbuildings.core.ir.Layer.SHELL, 0));
		assertEquals(Shape.stair(Facing.EAST), cellOf(java.util.List.of(wall, a), 1, 100, 67, 200), "the wall of the earlier building is in front, not our stair");
		assertEquals(Shape.stair(Facing.EAST, Shape.Corner.OUTER_RIGHT), cellOf(java.util.List.of(a, wall), 0, 100, 67, 200));
	}
}
