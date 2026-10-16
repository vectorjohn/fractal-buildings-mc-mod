package dev.fractalbuildings.core.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-built scenes shared by tests in every module and by the viewer fixture
 * {@code viewer/fixtures/sample-house.json} (which must stay equal to {@code house()} written by
 * the IR writer). A one-storey 7 x 5 house with a gable roof along x, two rooms, a front door, an
 * interior door, four windows, on a lot whose front two rows of terrain are one block lower (so
 * the site adds a one-block foundation there). 196 voxels: floor 70, wall 69, roof 35,
 * foundation 14, door 4, window 4.
 */
public final class SampleScenes {
	private SampleScenes() {}

	/** {@code specHash} is filled in by the IR writer from the spec; it is not part of the records. */
	public static SceneDoc house() {
		BuildingSpec spec = new BuildingSpec(1, 42L, "plain", "house", 7, 5, 1, Roof.GABLE);
		List<Box> b = new ArrayList<>();
		b.add(box(0, 0, 0, 7, 1, 5, Role.FLOOR, Layer.SHELL, 0));
		b.add(box(0, 1, 0, 7, 3, 1, Role.WALL, Layer.SHELL, 0));
		b.add(box(0, 1, 4, 7, 3, 1, Role.WALL, Layer.SHELL, 0));
		b.add(box(0, 1, 1, 1, 3, 3, Role.WALL, Layer.SHELL, 0));
		b.add(box(6, 1, 1, 1, 3, 3, Role.WALL, Layer.SHELL, 0));
		b.add(box(3, 1, 1, 1, 3, 3, Role.WALL, Layer.INTERIOR, 0));
		b.add(box(0, 4, 0, 7, 1, 5, Role.FLOOR, Layer.ROOF, Box.NO_STOREY));
		b.add(box(0, 5, 0, 7, 1, 1, Role.ROOF, Layer.ROOF, Box.NO_STOREY));
		b.add(box(0, 5, 4, 7, 1, 1, Role.ROOF, Layer.ROOF, Box.NO_STOREY));
		b.add(box(0, 5, 1, 1, 1, 3, Role.WALL, Layer.ROOF, Box.NO_STOREY));
		b.add(box(6, 5, 1, 1, 1, 3, Role.WALL, Layer.ROOF, Box.NO_STOREY));
		b.add(box(0, 6, 1, 7, 1, 1, Role.ROOF, Layer.ROOF, Box.NO_STOREY));
		b.add(box(0, 6, 3, 7, 1, 1, Role.ROOF, Layer.ROOF, Box.NO_STOREY));
		b.add(box(0, 6, 2, 1, 1, 1, Role.WALL, Layer.ROOF, Box.NO_STOREY));
		b.add(box(6, 6, 2, 1, 1, 1, Role.WALL, Layer.ROOF, Box.NO_STOREY));
		b.add(box(0, 7, 2, 7, 1, 1, Role.ROOF, Layer.ROOF, Box.NO_STOREY));
		b.add(box(1, 1, 0, 1, 2, 1, Role.DOOR, Layer.SHELL, 0));
		b.add(box(3, 1, 2, 1, 2, 1, Role.DOOR, Layer.INTERIOR, 0));
		b.add(box(1, 2, 4, 1, 1, 1, Role.WINDOW, Layer.SHELL, 0));
		b.add(box(5, 2, 4, 1, 1, 1, Role.WINDOW, Layer.SHELL, 0));
		b.add(box(6, 2, 2, 1, 1, 1, Role.WINDOW, Layer.SHELL, 0));
		b.add(box(0, 2, 2, 1, 1, 1, Role.WINDOW, Layer.SHELL, 0));

		BuildingIr shell = new BuildingIr(
				spec,
				Box3.of(0, 0, 0, 7, 8, 5),
				List.of(new Storey(0, 0, 4)),
				List.of(new Volume("v0", Box3.of(0, 0, 0, 7, 5, 5), null)),
				List.of(
						new Room("r0", 0, "v0", List.of(Box3.of(1, 1, 1, 2, 3, 3)), "room"),
						new Room("r1", 0, "v0", List.of(Box3.of(4, 1, 1, 2, 3, 3)), "room")),
				List.of(
						new Opening("o0", OpeningKind.DOOR, Box3.of(1, 1, 0, 1, 2, 1), Facing.NORTH, "r0", Opening.OUTSIDE),
						new Opening("o1", OpeningKind.DOOR, Box3.of(3, 1, 2, 1, 2, 1), Facing.EAST, "r0", "r1"),
						new Opening("o2", OpeningKind.WINDOW, Box3.of(1, 2, 4, 1, 1, 1), Facing.SOUTH, "r0", Opening.OUTSIDE),
						new Opening("o3", OpeningKind.WINDOW, Box3.of(5, 2, 4, 1, 1, 1), Facing.SOUTH, "r1", Opening.OUTSIDE),
						new Opening("o4", OpeningKind.WINDOW, Box3.of(6, 2, 2, 1, 1, 1), Facing.EAST, "r1", Opening.OUTSIDE),
						new Opening("o5", OpeningKind.WINDOW, Box3.of(0, 2, 2, 1, 1, 1), Facing.WEST, "r0", Opening.OUTSIDE)),
				List.of(new Roof(Roof.GABLE, "v0", Axis.X, 5, 1, 1)),
				b);

		PlacedBuilding placed = new PlacedBuilding(
				"b0", List.of("lot0"), 100, 64, -40, 0, shell,
				List.of(box(0, -1, 0, 7, 1, 2, Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY)));

		int nx = 11, nz = 9;
		int[] floorY = new int[nx * nz];
		for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) floorY[j * nx + i] = j < 4 ? 63 : 64;
		TerrainPatch terrain = new TerrainPatch(98, -42, nx, nz, floorY);

		return new SceneDoc(1, 42L, terrain, List.of(new Lot("lot0", 98, -42, 11, 9, Facing.NORTH)), List.of(placed), List.of());
	}

	private static Box box(int x, int y, int z, int sx, int sy, int sz, Role role, Layer layer, int storey) {
		return new Box(Box3.of(x, y, z, sx, sy, sz), role, layer, storey);
	}
}
