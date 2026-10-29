package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code stair-foot} (STAIRFOOT): the lowest step of every run of steps (an interior flight, the sill step in front of a door, the
 * step up to a roof door) has a standing cell at its foot: the cell behind it, level with the floor, is floor with two clear cells
 * over it (not a hole, a wall or a piece of furniture' column), or the foot is the last step of a lower run of the same direction.
 * A step that rises toward a door, a higher step or a floor edge is a stair block whose cell opposite the rise is its foot.
 */
public final class StairFoot {
	private StairFoot() {}

	/** A step whose foot cannot be stood on: the step cell and the cell its foot should be. */
	public record Fail(Box3 step, Box3 foot, Role footRole, Role belowRole) {}

	public static List<Fail> check(PlacedBuilding pb, VoxelGrid g) {
		List<Fail> out = new ArrayList<>();
		for (int y = g.minY; y < g.minY + g.sy; y++)
			for (int x = g.minX; x < g.minX + g.sx; x++)
				for (int z = g.minZ; z < g.minZ + g.sz; z++) {
					if (g.get(x, y, z) != Role.STAIR) continue;
					if (!inRoom(pb, x, y, z)) continue; // site steps outside the walls are STEPSGROUND's
					if (g.get(x, y + 1, z) == Role.STAIR) continue; // a buried support cell of a flight column
					if (!Invariants.walkPassable(g, x, y + 1, z) || !Invariants.walkPassable(g, x, y + 2, z)) continue; // not a step to stand on
					Facing a = ascent(g, x, y, z);
					if (a == null) continue;
					int fx = x - a.dx, fz = z - a.dz;
					if (!g.inGrid(fx, y, fz)) continue;
					if (g.get(fx, y - 1, fz) == Role.STAIR && Invariants.walkPassable(g, fx, y, fz) && ascent(g, fx, y - 1, fz) == a) continue; // the run continues down
					if (g.get(fx, y, fz) == Role.STAIR || g.get(fx, y, fz) == Role.FLOOR) continue; // a step flush with the floor beside it (the top step of a flight, its slab level): the walker is already standing level with it
					if (Invariants.walkStanding(g, fx, y, fz)) continue;
					out.add(new Fail(Box3.of(x, y, z, 1, 1, 1), Box3.of(fx, y, fz, 1, 1, 1), g.get(fx, y, fz), g.get(fx, y - 1, fz)));
				}
		return out;
	}

	/** The direction a run of steps rises toward a door: the door stands above the far side of this step or of the last step of a straight run. Flights between floors are no run to a door (stair-rise and furnished-rooms-walkable cover them). */
	private static Facing ascent(VoxelGrid g, int x, int y, int z) {
		for (Facing f : Facing.values()) {
			int cx = x, cy = y, cz = z;
			while (g.get(cx + f.dx, cy + 1, cz + f.dz) == Role.STAIR) { cx += f.dx; cy++; cz += f.dz; }
			if (g.get(cx + f.dx, cy + 1, cz + f.dz) == Role.DOOR) return f;
		}
		return null;
	}

	private static boolean inRoom(PlacedBuilding pb, int x, int y, int z) {
		for (Room r : pb.shell().rooms()) for (Box3 p : r.parts()) if (p.contains(x, y, z) || p.contains(x, y + 1, z)) return true;
		return false;
	}
}
