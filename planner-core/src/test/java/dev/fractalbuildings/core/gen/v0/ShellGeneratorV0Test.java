package dev.fractalbuildings.core.gen.v0;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.*;
import dev.fractalbuildings.core.rand.Hash64;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShellGeneratorV0Test {
	static BuildingSpec spec(long seed, int w, int d, int n) {
		return new BuildingSpec(1, seed, "plain", "house", w, d, n, Roof.GABLE);
	}

	static final int[][] SIZES = {{5, 5, 1}, {6, 7, 1}, {7, 5, 2}, {9, 7, 3}, {15, 11, 3}, {7, 11, 2}, {8, 5, 1}, {5, 11, 1}, {12, 6, 3}};

	@Test
	void storeysSlabsAndBounds() {
		for (int[] s : SIZES) {
			for (long seed = 0; seed < 25; seed++) {
				BuildingIr b = ShellGeneratorV0.generate(spec(seed, s[0], s[1], s[2]));
				assertEquals(s[2], b.storeys().size());
				for (int i = 0; i < s[2]; i++) {
					assertEquals(4 * i, b.storeys().get(i).y());
					TestVoxels v = new TestVoxels(b);
					assertEquals(Role.FLOOR, v.at(0, 4 * i, 0));
				}
				for (Box box : b.boxes()) assertTrue(b.bounds().contains(box.box()), box.toString());
				for (Opening o : b.openings()) assertTrue(b.bounds().contains(o.box()));
				for (Room r : b.rooms()) for (Box3 p : r.parts()) assertTrue(b.bounds().contains(p));
				assertEquals(4 * s[2] + 1, b.volumes().get(0).box().sy());
			}
		}
	}

	@Test
	void exactlyOneOutsideDoorOnTheFrontWall() {
		for (int[] s : SIZES) {
			for (long seed = 0; seed < 50; seed++) {
				BuildingIr b = ShellGeneratorV0.generate(spec(seed, s[0], s[1], s[2]));
				List<Opening> doors = b.openings().stream()
						.filter(o -> o.kind() == OpeningKind.DOOR && (o.from().equals("outside") || o.to().equals("outside"))).toList();
				assertEquals(1, doors.size());
				Opening d = doors.get(0);
				assertEquals(0, d.box().z());
				assertEquals(0, d.box().y() - 1);
				assertEquals(Facing.NORTH, d.facing());
				assertEquals(2, d.box().sy());
				TestVoxels v = new TestVoxels(b);
				assertEquals(Role.DOOR, v.at(d.box().x(), 1, 0));
				assertEquals(Role.DOOR, v.at(d.box().x(), 2, 0));
			}
		}
	}

	@Test
	void windowsOnlyInPerimeterWallsAndNeverOnPartitionsDoorsOrStairs() {
		for (int[] s : SIZES) {
			for (long seed = 0; seed < 50; seed++) {
				BuildingSpec sp = spec(seed, s[0], s[1], s[2]);
				BuildingIr b = ShellGeneratorV0.generate(sp);
				TestVoxels v = new TestVoxels(b);
				int windows = 0;
				for (Opening o : b.openings()) {
					if (o.kind() != OpeningKind.WINDOW) continue;
					windows++;
					Box3 c = o.box();
					boolean perimeter = c.x() == 0 || c.x() == sp.width() - 1 || c.z() == 0 || c.z() == sp.depth() - 1;
					assertTrue(perimeter, "window not on perimeter: " + c);
					assertFalse(c.x() == 0 && c.z() == 0 || c.x() == sp.width() - 1 && c.z() == sp.depth() - 1, "corner");
					assertEquals(Role.WINDOW, v.at(c.x(), c.y(), c.z()));
					assertEquals(Role.WALL, v.at(c.x(), c.y() - 1, c.z()));
					assertEquals(Role.WALL, v.at(c.x(), c.y() + 1, c.z()));
					// the partition (if any) would be directly behind a front/back window
					int inward = c.z() == 0 ? 1 : c.z() == sp.depth() - 1 ? -1 : 0;
					if (inward != 0 && v.at(c.x(), c.y(), c.z() + inward).solid) fail("window blocked from inside at " + c);
				}
				assertTrue(windows >= 3 * sp.storeys(), "windows " + windows);
				assertEquals(windows, v.count(Role.WINDOW));
			}
		}
	}

	@Test
	void stairPresentIffMoreThanOneStorey() {
		for (int[] s : SIZES) {
			BuildingIr b = ShellGeneratorV0.generate(spec(3, s[0], s[1], s[2]));
			long stairs = b.openings().stream().filter(o -> o.kind() == OpeningKind.STAIR).count();
			assertEquals(s[2] - 1, stairs);
			assertEquals(3L * (s[2] - 1), new TestVoxels(b).count(Role.STAIR));
		}
	}

	@Test
	void everyRoomIsWalkableFromTheFrontDoorIncludingUpstairs() {
		for (int[] s : SIZES) {
			for (long seed = 0; seed < 200; seed++) {
				BuildingIr b = ShellGeneratorV0.generate(spec(Hash64.hash(seed, "walk"), s[0], s[1], s[2]));
				TestVoxels v = new TestVoxels(b);
				Opening door = b.openings().stream().filter(o -> o.kind() == OpeningKind.DOOR && o.to().equals("outside")).findFirst().get();
				Set<Long> reach = v.reachable(door.box().x(), 1, door.box().z());
				for (Room r : b.rooms()) {
					Box3 p = r.parts().get(0);
					boolean any = false;
					for (int x = p.x(); x < p.maxX() && !any; x++)
						for (int z = p.z(); z < p.maxZ() && !any; z++) any = reach.contains(TestVoxels.key(x, p.y(), z));
					assertTrue(any, "room " + r.id() + " unreachable, spec " + b.spec());
				}
			}
		}
	}

	@Test
	void stairHoleStartsOverTheFirstStepAndBackIsSolid() {
		BuildingIr b = ShellGeneratorV0.generate(spec(5, 9, 7, 3));
		TestVoxels v = new TestVoxels(b);
		for (int i = 0; i < 2; i++) {
			for (int k = 0; k < 3; k++) {
				assertEquals(Role.STAIR, v.at(2 + k, 4 * i + 1 + k, 5));
				assertEquals(Role.AIR, v.at(2 + k, 4 * i + 4, 5), "hole over step " + k);
			}
			assertEquals(Role.FLOOR, v.at(5, 4 * i + 4, 5), "landing slab");
			assertEquals(Role.FLOOR, v.at(1, 4 * i + 4, 5), "slab west of the hole");
		}
	}

	@Test
	void ridgeFollowsTheLongerSide() {
		assertEquals(Axis.X, ShellGeneratorV0.generate(spec(1, 9, 5, 1)).roofs().get(0).ridgeAxis());
		assertEquals(Axis.X, ShellGeneratorV0.generate(spec(1, 7, 7, 1)).roofs().get(0).ridgeAxis());
		assertEquals(Axis.Z, ShellGeneratorV0.generate(spec(1, 5, 9, 1)).roofs().get(0).ridgeAxis());
	}

	@Test
	void roofIsClosedAndCoversEveryColumn() {
		for (int[] s : SIZES) {
			BuildingIr b = ShellGeneratorV0.generate(spec(9, s[0], s[1], s[2]));
			TestVoxels v = new TestVoxels(b);
			int top = 4 * s[2];
			for (int x = 0; x < s[0]; x++)
				for (int z = 0; z < s[1]; z++) {
					boolean roof = false;
					for (int y = top + 1; y < v.sy; y++) roof |= v.at(x, y, z) == Role.ROOF;
					assertTrue(roof, "no roof over " + x + "," + z);
				}
			assertEquals(v.sy, 4 * s[2] + 1 + (Math.min(s[0] >= s[1] ? s[1] : s[0], 99) + 1) / 2);
		}
	}

	@Test
	void sameSpecSameShell() {
		assertEquals(ShellGeneratorV0.generate(spec(11, 9, 7, 2)), ShellGeneratorV0.generate(spec(11, 9, 7, 2)));
	}

	@Test
	void rejectsTooSmallSpecs() {
		assertThrows(IllegalArgumentException.class, () -> ShellGeneratorV0.generate(spec(1, 6, 7, 2)));
		assertThrows(IllegalArgumentException.class, () -> ShellGeneratorV0.generate(spec(1, 4, 7, 1)));
	}
}
