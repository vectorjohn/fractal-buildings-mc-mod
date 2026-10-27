package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code furnished-rooms-walkable} (DS-48, ROOMWALK): in each room the floor that is not blocked (by a wall, a column or a
 * piece of furniture, a bed's head included) joins the inside cell of every door and arch, the cells beside every stair run and
 * the use cell of every piece (the cell in front of it; for a bed, a cell beside either half). See {@link Walkability} for the
 * lane widths.
 */
final class RoomWalk {
	private RoomWalk() {}

	/** A room that fails, with the number of cut-off targets and the local floor cells of its furniture (for the report). */
	record Fail(String room, String use, int cut, List<Box3> cells) {}

	static List<Fail> check(PlacedBuilding b, VoxelGrid g) {
		List<Fail> fails = new ArrayList<>();
		for (Room r : b.shell().rooms()) {
			List<Hook> hooks = new ArrayList<>();
			for (Hook h : b.shell().hooks()) if (h.room().equals(r.id()) && Walkability.blocksFloor(h.kind()) && r.parts().stream().anyMatch(p -> p.y() == h.y())) hooks.add(h); // not a market stall on the open floor below the room
			if (hooks.isEmpty()) continue;
			int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE, y = r.parts().get(0).y();
			for (Box3 p : r.parts()) {
				x0 = Math.min(x0, p.x()); z0 = Math.min(z0, p.z()); x1 = Math.max(x1, p.maxX()); z1 = Math.max(z1, p.maxZ()); y = Math.min(y, p.y());
			}
			int w = x1 - x0, d = z1 - z0;
			boolean[][] inRoom = new boolean[w][d], blocked = new boolean[w][d];
			for (Box3 p : r.parts()) for (int x = p.x(); x < p.maxX(); x++) for (int z = p.z(); z < p.maxZ(); z++) inRoom[x - x0][z - z0] = true;
			for (int x = 0; x < w; x++)
				for (int z = 0; z < d; z++)
					blocked[x][z] = !inRoom[x][z] || !Invariants.walkFloor(g.get(x + x0, y - 1, z + z0)) || !(Invariants.walkPassable(g, x + x0, y, z + z0) && Invariants.walkPassable(g, x + x0, y + 1, z + z0)
							|| g.get(x + x0, y, z + z0) == Role.STAIR && Invariants.walkPassable(g, x + x0, y + 1, z + z0) && Invariants.walkPassable(g, x + x0, y + 2, z + z0)); // a single step up (the sill inside a wall door) is walked over
			List<Box3> cells = new ArrayList<>();
			List<int[]> targets = new ArrayList<>();
			List<List<int[]>> groups = new ArrayList<>();
			for (Opening o : b.shell().openings()) {
				boolean mine = o.from().equals(r.id()) || o.to().equals(r.id());
				if (!mine) continue;
				Box3 q = o.box();
				if (o.kind() == OpeningKind.DOOR || o.kind() == OpeningKind.ARCH) {
					if (q.y() != y) continue;
					for (int x = q.x(); x < q.maxX(); x++)
						for (int z = q.z(); z < q.maxZ(); z++)
							for (Facing f : Facing.values()) {
								int nx = x + f.dx, nz = z + f.dz;
								if (q.contains(nx, q.y(), nz)) continue;
								if (nx - x0 >= 0 && nz - z0 >= 0 && nx - x0 < w && nz - z0 < d && inRoom[nx - x0][nz - z0]) targets.add(new int[] {nx - x0, nz - z0});
							}
				} else if (o.kind() == OpeningKind.STAIR) {
					Facing f = o.facing();
					int n = f.dx != 0 ? q.sx() : q.sz();
					int sx0 = f.dx < 0 ? q.maxX() - 1 : q.x(), sz0 = f.dz < 0 ? q.maxZ() - 1 : q.z();
					if (y == q.y()) groups.add(List.of(new int[] {sx0 - f.dx - x0, sz0 - f.dz - z0})); // the foot
					if (y == q.maxY()) { // the top: straight on or sideways off the last step
						int lx = sx0 + (n - 1) * f.dx - x0, lz = sz0 + (n - 1) * f.dz - z0;
						List<int[]> off = new ArrayList<>();
						for (Facing t : new Facing[] {f, f.rotate(1), f.rotate(3)}) off.add(new int[] {lx + t.dx, lz + t.dz});
						groups.add(off);
					}
				}
			}
			for (Hook h : hooks) {
				int hx = h.x() - x0, hz = h.z() - z0;
				if (hx < 0 || hz < 0 || hx >= w || hz >= d) continue;
				cells.add(Box3.of(h.x(), h.y(), h.z(), 1, 1, 1));
				blocked[hx][hz] = true;
				if (h.kind().equals("bed")) {
					int ex = hx + h.facing().dx, ez = hz + h.facing().dz;
					if (ex >= 0 && ez >= 0 && ex < w && ez < d) blocked[ex][ez] = true;
				}
			}
			for (Hook h : hooks) {
				int hx = h.x() - x0, hz = h.z() - z0;
				if (hx < 0 || hz < 0 || hx >= w || hz >= d) continue;
				if (h.kind().equals("bed")) {
					List<int[]> sides = new ArrayList<>();
					int ex = hx + h.facing().dx, ez = hz + h.facing().dz;
					for (Facing f : new Facing[] {h.facing().rotate(1), h.facing().rotate(3)}) {
						sides.add(new int[] {hx + f.dx, hz + f.dz});
						sides.add(new int[] {ex + f.dx, ez + f.dz});
					}
					groups.add(sides);
				} else if (Walkability.hasUseCell(h.kind())) {
					int ux = hx + h.facing().dx, uz = hz + h.facing().dz;
					if (ux >= 0 && uz >= 0 && ux < w && uz < d && inRoom[ux][uz]) targets.add(new int[] {ux, uz});
				}
			}
			int cut = Walkability.cutOff(blocked, targets, groups, Walkability.wide(r.use(), w, d));
			if (cut > 0) fails.add(new Fail(r.id(), r.use(), cut, cells));
		}
		return fails;
	}
}
