package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;

/**
 * Shell plus request to a placed building (generator v1). The envelope sits at the type's margins
 * inside the lot (rotation from the lot's front, as v0); the ground slab sits at the highest ground
 * under the ground footprint (the volumes that start at y 0); every footprint column whose ground is
 * lower gets a foundation down to it. Front steps (plan step S, 4.8): the slab is lifted to the
 * ground in front of any door that would be buried, and a landing plus a flight of one-block steps
 * leads from each ground-storey door down to the terrain ({@link #steps}).
 */
public final class InstancePassV1 {
	private InstancePassV1() {}

	public static PlacedBuilding place(BuildingIr shell, GenRequest req) { return place(shell, req, Types.get(shell.spec().type()).limits()); }

	/** With explicit margins (tests place shells of unregistered types). */
	public static PlacedBuilding place(BuildingIr shell, GenRequest req, TypeLimits lim) {
		Lot lot = req.lot();
		int r = lot.front().ordinal();
		boolean odd = (r & 1) == 1;
		int lw = odd ? lot.d() : lot.w(), ld = odd ? lot.w() : lot.d();
		int w = shell.bounds().maxX(), d = shell.bounds().maxZ();
		if (w + 2 * lim.side() > lw || d + lim.front() + lim.back() > ld) {
			throw new IllegalArgumentException("envelope " + w + "x" + d + " does not fit lot " + lot.w() + "x" + lot.d() + " with the type's margins");
		}
		Placement lotFrame = new Placement(lot.x(), 0, lot.z(), r, lw, ld);
		int ax = lotFrame.worldX(lim.side(), lim.front()), az = lotFrame.worldZ(lim.side(), lim.front());
		int bx = lotFrame.worldX(lim.side() + w - 1, lim.front() + d - 1), bz = lotFrame.worldZ(lim.side() + w - 1, lim.front() + d - 1);
		int ox = Math.min(ax, bx), oz = Math.min(az, bz);
		boolean[][] fp = footprint(shell);
		Placement p0 = new Placement(ox, 0, oz, r, w, d);
		TerrainProbe t = req.terrain();
		int oy = Integer.MIN_VALUE;
		for (int lx = 0; lx < w; lx++)
			for (int lz = 0; lz < d; lz++) if (fp[lx][lz]) oy = Math.max(oy, t.floorY(p0.worldX(lx, lz), p0.worldZ(lx, lz)));
		// a door whose outside ground is higher than the slab would be buried: lift the slab to it
		for (Opening o : entranceDoors(shell)) {
			Facing f = outward(o);
			int wx = p0.worldX(o.box().x() + f.dx, o.box().z() + f.dz), wz = p0.worldZ(o.box().x() + f.dx, o.box().z() + f.dz);
			if (lot.containsColumn(wx, wz)) oy = Math.max(oy, t.floorY(wx, wz) - entranceStorey(shell).y());
		}
		List<Box> site = new ArrayList<>();
		for (int lz = 0; lz < d; lz++) {
			int runStart = 0, runH = 0;
			for (int lx = 0; lx <= w; lx++) {
				int h = lx < w && fp[lx][lz] ? oy - t.floorY(p0.worldX(lx, lz), p0.worldZ(lx, lz)) : 0;
				if (lx == w || h != runH) {
					if (runH > 0) site.add(new Box(Box3.of(runStart, -runH, lz, lx - runStart, runH, 1), Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY));
					runStart = lx;
					runH = h;
				}
			}
		}
		PlacedBuilding provisional = new PlacedBuilding("b0", List.of(lot.id()), ox, oy, oz, r, shell, site);
		List<Box> all = new ArrayList<>(site);
		steps(provisional, lot, t, all);
		return new PlacedBuilding("b0", List.of(lot.id()), ox, oy, oz, r, shell, all);
	}

	/** The lowest storey with rooms (storey 0, except under stilts, where it is the open leg zone): its exterior doors need a way down. */
	public static Storey entranceStorey(BuildingIr shell) {
		int lowest = Integer.MAX_VALUE;
		for (dev.fractalbuildings.core.ir.Room r : shell.rooms()) lowest = Math.min(lowest, r.storey());
		return shell.storeys().get(lowest == Integer.MAX_VALUE ? 0 : lowest);
	}

	/** Ground-storey doors that lead outside: the ones that need a way down to the ground. */
	static List<Opening> entranceDoors(BuildingIr shell) {
		List<Opening> doors = new ArrayList<>();
		if (shell.storeys().isEmpty()) return doors;
		Storey s0 = entranceStorey(shell);
		for (Opening o : shell.openings()) {
			if (o.kind() != OpeningKind.DOOR || o.box().y() < s0.y() || o.box().y() >= s0.y() + s0.height()) continue;
			if (o.to().equals(Opening.OUTSIDE) == o.from().equals(Opening.OUTSIDE)) continue;
			doors.add(o);
		}
		return doors;
	}

	static Facing outward(Opening o) { return o.to().equals(Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2); }

	/**
	 * Front steps: for each entrance door, a landing one cell out at slab height, then a flight down
	 * one block per cell to the ground, straight out while the lot allows, else turning along the
	 * facade (breadth-first, so the shortest flight; ties prefer straight, then clockwise). Every
	 * step stands on foundation to the ground. A door whose outside ground is level with, or one
	 * below, the slab needs none. When no route exists the door is left as it is and the
	 * {@code entrance-reachable} invariant reports it.
	 */
	private static void steps(PlacedBuilding provisional, Lot lot, TerrainProbe t, List<Box> site) {
		BuildingIr shell = provisional.shell();
		List<Opening> doors = entranceDoors(shell);
		if (doors.isEmpty()) return;
		Placement p = provisional.placement();
		int base = provisional.oy(), oy = base + entranceStorey(shell).y(); // oy: the entrance floor level (above the slab under stilts)
		VoxelGrid grid = VoxelGrid.of(provisional);
		boolean[][] fp = footprint(shell);
		int[] cs = {p.localX(lot.x(), lot.z()), p.localX(lot.x() + lot.w() - 1, lot.z()), p.localX(lot.x(), lot.z() + lot.d() - 1), p.localX(lot.x() + lot.w() - 1, lot.z() + lot.d() - 1)};
		int[] zs = {p.localZ(lot.x(), lot.z()), p.localZ(lot.x() + lot.w() - 1, lot.z()), p.localZ(lot.x(), lot.z() + lot.d() - 1), p.localZ(lot.x() + lot.w() - 1, lot.z() + lot.d() - 1)};
		int x0 = Math.min(Math.min(cs[0], cs[1]), Math.min(cs[2], cs[3])), x1 = Math.max(Math.max(cs[0], cs[1]), Math.max(cs[2], cs[3]));
		int z0 = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3])), z1 = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
		int nx = x1 - x0 + 1, nz = z1 - z0 + 1;
		boolean[] blocked = new boolean[nx * nz];
		int[] ground = new int[nx * nz];
		for (int z = z0; z <= z1; z++)
			for (int x = x0; x <= x1; x++) {
				int i = (z - z0) * nx + (x - x0);
				ground[i] = t.floorY(p.worldX(x, z), p.worldZ(x, z));
				blocked[i] = x >= 0 && z >= 0 && x < fp.length && z < fp[0].length && fp[x][z];
			}
		int[] flightTop = new int[nx * nz]; // highest solid of a placed flight's cell, MIN_VALUE where there is none
		java.util.Arrays.fill(flightTop, Integer.MIN_VALUE);
		for (Opening o : doors) { // keep every door's own outside cell free of other flights
			Facing f = outward(o);
			int x = o.box().x() + f.dx, z = o.box().z() + f.dz;
			if (x >= x0 && x <= x1 && z >= z0 && z <= z1) blocked[(z - z0) * nx + (x - x0)] = true;
		}
		for (Opening o : doors) {
			Facing out = outward(o);
			int sx = o.box().x() + out.dx, sz = o.box().z() + out.dz;
			if (sx < x0 || sx > x1 || sz < z0 || sz > z1) continue;
			blocked[(sz - z0) * nx + (sx - x0)] = false;
			int[] parent = new int[nx * nz], depth = new int[nx * nz];
			int found = -1;
			boolean merged = false;
			// pass 1: a flight of its own to the terrain. Pass 2 (only if pass 1 found none, e.g. earlier flights wall
			// the margin off on a steep slope): the flight may also end by stepping onto an earlier flight, one block
			// down or level, and share the rest of its way down.
			for (int pass = 0; pass < 2 && found < 0; pass++) {
				java.util.Arrays.fill(parent, -2);
				int[] queue = new int[nx * nz];
				int head = 0, tail = 0;
				int s = (sz - z0) * nx + (sx - x0);
				parent[s] = -1;
				queue[tail++] = s;
				Facing[] dirs = {out, out.rotate(1), out.rotate(3), out.rotate(2)};
				while (head < tail && found < 0) {
					int c = queue[head++];
					int cx = x0 + c % nx, cz = z0 + c / nx, d = depth[c];
					int g = ground[c];
					int h = oy - d;
					boolean ends = d == 0 ? g >= oy - 1 : g >= h;
					int top = ends ? g : h; // highest solid under the feet
					// the cells the walker occupies (and the step block itself) must be free of the building
					boolean free = true;
					for (int y = top - base; y <= top + 3 - base && free; y++) free = grid.get(cx, y, cz) == Role.AIR;
					if (!free) continue;
					if (ends) { found = c; break; }
					if (g > h + 2) continue;
					for (Facing f : dirs) {
						int nxx = cx + f.dx, nzz = cz + f.dz;
						if (nxx < x0 || nxx > x1 || nzz < z0 || nzz > z1) continue;
						int ni = (nzz - z0) * nx + (nxx - x0);
						if (parent[ni] != -2) continue;
						if (blocked[ni]) {
							if (pass == 1 && flightTop[ni] != Integer.MIN_VALUE && (flightTop[ni] == h - 1 || flightTop[ni] == h)) {
								found = c; // c is a step; its neighbour is an earlier flight at the right height
								merged = true;
								break;
							}
							continue;
						}
						parent[ni] = c;
						depth[ni] = d + 1;
						queue[tail++] = ni;
					}
				}
			}
			if (found < 0) continue;
			for (int c = found; c >= 0; c = parent[c]) {
				blocked[c] = true; // other doors' flights keep off this one
				flightTop[c] = c == found && !merged ? ground[c] : oy - depth[c];
				if (c == found && !merged) continue; // ends on the terrain itself
				int cx = x0 + c % nx, cz = z0 + c / nx, h = oy - depth[c], g = ground[c];
				if (h > g) {
					site.add(new Box(Box3.of(cx, g - base, cz, 1, h - g, 1), Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY));
					site.add(new Box(Box3.of(cx, h - base, cz, 1, 1, 1), Role.STAIR, Layer.FOUNDATION, Box.NO_STOREY));
				}
			}
		}
	}


	/** Local columns [x][z] under a volume that starts at y 0. */
	public static boolean[][] footprint(BuildingIr shell) {
		boolean[][] fp = new boolean[shell.bounds().maxX()][shell.bounds().maxZ()];
		for (Volume v : shell.volumes()) {
			if (v.box().y() != 0) continue;
			for (int x = v.box().x(); x < v.box().maxX(); x++) for (int z = v.box().z(); z < v.box().maxZ(); z++) fp[x][z] = true;
		}
		return fp;
	}
}
