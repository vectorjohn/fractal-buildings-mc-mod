package dev.fractalbuildings.core.gen.v1.interior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Walkability;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ROOMWALK: the trial flood fill of {@link HookLayout} is what keeps a 3x3 room walkable. */
class HookLayoutTest {
	private static HookLayout.Piece light(int x, int z) { return new HookLayout.Piece("light", x, z, Facing.SOUTH, null); }

	/** A row of lights across the middle of a 3x3 room cuts it in two: the last one must be refused, the first two accepted. */
	@Test
	void aPieceThatCutsTheRoomInTwoIsRefused() {
		FloorPlan plan = FloorPlan.single(3, 3);
		boolean[][] base = new boolean[3][3];
		List<int[]> targets = List.of(new int[] {0, 0}, new int[] {2, 2});
		List<HookLayout.Piece> placed = new ArrayList<>(List.of(light(1, 0), light(1, 1)));
		assertTrue(HookLayout.walkable(base, targets, List.of(), light(1, 0), false, plan, 0));
		assertTrue(HookLayout.walkable(base, targets, List.of(light(1, 0)), light(1, 1), false, plan, 0));
		assertFalse(HookLayout.walkable(base, targets, placed, light(1, 2), false, plan, 0), "three lights in a column cut a 3x3 room in two");
	}

	/** A chest whose front cell is boxed in by a second piece is refused too. */
	@Test
	void aPieceThatBoxesInAnotherPiecesUseCellIsRefused() {
		FloorPlan plan = FloorPlan.single(3, 3);
		boolean[][] base = new boolean[3][3];
		HookLayout.Piece chest = new HookLayout.Piece("chest", 0, 0, Facing.SOUTH, null); // use cell (0,1)
		List<int[]> targets = List.of(new int[] {2, 2});
		assertTrue(HookLayout.walkable(base, targets, List.of(), chest, false, plan, 0));
		assertTrue(HookLayout.walkable(base, targets, List.of(chest), light(1, 1), false, plan, 0));
		assertFalse(HookLayout.walkable(base, targets, List.of(chest, light(1, 1)), light(0, 2), false, plan, 0), "the chest's front cell would be sealed");
	}

	/** Whatever is placed in a crowded 3x3 room keeps its lane, and some of it is dropped. */
	@Test
	void aCrowdedRoomKeepsItsLaneAndDropsPieces() {
		FloorPlan plan = FloorPlan.single(3, 3);
		boolean[][] none = new boolean[3][3];
		boolean[][] clear = new boolean[3][3];
		clear[1][1] = true; // a target in the middle of the room
		for (long seed = 0; seed < 30; seed++) {
			List<String> kinds = List.of("bed", "chest", "chest", "table", "light", "bench", "chest", "chest");
			HookLayout.Result r = HookLayout.place(plan, 0, "bedroom", kinds, clear, none, List.of(), List.of(), new Draws(seed));
			boolean[][] blocked = new boolean[3][3];
			List<int[]> targets = new ArrayList<>(List.of(new int[] {1, 1}));
			List<List<int[]>> groups = new ArrayList<>();
			for (HookLayout.Piece p : r.pieces()) {
				blocked[p.x()][p.z()] = true;
				if (p.head() != null) blocked[p.head()[0]][p.head()[1]] = true;
			}
			for (HookLayout.Piece p : r.pieces()) if (p.head() == null && Walkability.hasUseCell(p.kind())) targets.add(new int[] {p.x() + p.facing().dx, p.z() + p.facing().dz});
			assertEquals(0, Walkability.cutOff(blocked, targets, groups, false), "seed " + seed + " " + r.pieces());
			assertTrue(r.pieces().size() < kinds.size(), "a 3x3 room cannot hold " + kinds.size() + " pieces");
		}
	}
}
