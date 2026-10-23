package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;

/**
 * STAIRS: walking an interior flight ({@link OpeningKind#STAIR}) from one floor to the next never needs a jump. Model in half
 * blocks along the flight's line: a column is walked at its topmost block with two free blocks above it; a {@link Role#STAIR}
 * block at y is entered on its low half (top y + 1/2) and left on its high half (top y + 1); any other solid block at y is
 * entered and left at y + 1. Each step from one column to the next may rise at most half a block. The line runs from the cell
 * before the flight (the floor it starts from) to the cell after it (the floor it arrives on), which must be standable too.
 */
final class StairRise {
	private StairRise() {}

	/** One result per failing flight: its opening id and a local cell where the rise is too big. */
	record Fail(String opening, Box3 cell, String why) {}

	static List<Fail> check(PlacedBuilding b, VoxelGrid g) {
		List<Fail> fails = new ArrayList<>();
		for (Opening o : b.shell().openings()) {
			if (o.kind() != OpeningKind.STAIR) continue;
			Facing f = o.facing();
			Box3 r = o.box();
			int n = (f.dx != 0 ? r.sx() : r.sz());
			int x0 = f.dx < 0 ? r.maxX() - 1 : r.x();
			int z0 = f.dz < 0 ? r.maxZ() - 1 : r.z();
			int yTop = r.maxY() + 2;
			int prevExit = Integer.MIN_VALUE;
			boolean broken = false;
			for (int k = -1; k < n && !broken; k++) {
				int x = x0 + k * f.dx, z = z0 + k * f.dz;
				int[] lv = level(g, x, z, r.y() - 1, yTop);
				if (lv == null) { fails.add(new Fail(o.id(), Box3.of(x, r.y(), z, 1, 1, 1), "nothing to stand on")); broken = true; }
				else if (k >= 0 && lv[0] - prevExit > 1) { fails.add(new Fail(o.id(), Box3.of(x, r.y(), z, 1, 1, 1), "rise of " + (lv[0] - prevExit) + " half blocks")); broken = true; }
				else prevExit = lv[1];
			}
			if (broken) continue;
			// off the last step onto the floor: straight on, or sideways onto the landing of the next flight (a plain floor block)
			int lx = x0 + (n - 1) * f.dx, lz = z0 + (n - 1) * f.dz;
			boolean off = false;
			for (Facing d : new Facing[] {f, f.rotate(1), f.rotate(3)}) {
				int[] lv = level(g, lx + d.dx, lz + d.dz, r.y() - 1, yTop);
				off |= lv != null && lv[0] == lv[1] && lv[0] >= prevExit && lv[0] - prevExit <= 1;
			}
			if (!off) fails.add(new Fail(o.id(), Box3.of(lx, r.y(), lz, 1, 1, 1), "no floor level with the last step to step off onto"));
		}
		return fails;
	}

	/** {entry, exit} in half blocks of the topmost standable block of the column in [yLo, yHi], or null. */
	private static int[] level(VoxelGrid g, int x, int z, int yLo, int yHi) {
		for (int y = yHi; y >= yLo; y--) {
			Role role = g.get(x, y, z);
			if (!role.solid) continue;
			if (g.get(x, y + 1, z).solid || g.get(x, y + 2, z).solid) return null; // blocked head room: not standable here
			return role == Role.STAIR ? new int[] {2 * y + 1, 2 * y + 2} : new int[] {2 * y + 2, 2 * y + 2};
		}
		return null;
	}
}
