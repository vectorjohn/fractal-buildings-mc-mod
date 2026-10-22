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

	/** The town-wall types, whose doors are walled up where the lot has no room for a flight down to the ground. */
	private static final java.util.Set<String> WALL_TYPES = java.util.Set.of("wall", "curtain", "bastion");

	public static PlacedBuilding place(BuildingIr shell, GenRequest req) { return place(shell, req, Types.get(shell.spec().type()).limits()); }

	/** With explicit margins (tests place shells of unregistered types). */
	public static PlacedBuilding place(BuildingIr shell, GenRequest req, TypeLimits lim) {
		Lot lot = req.lot();
		int r = lot.front().ordinal();
		boolean odd = (r & 1) == 1;
		int lw = odd ? lot.d() : lot.w(), ld = odd ? lot.w() : lot.d();
		int w = shell.bounds().maxX(), d = shell.bounds().maxZ();
		int lead = shell.spec().lead(); // blocks of the shell in front of the usual envelope: they reach over the street (air rights)
		if (w + 2 * lim.side() > lw || d - lead + lim.front() + lim.back() > ld) {
			throw new IllegalArgumentException("envelope " + w + "x" + d + " does not fit lot " + lot.w() + "x" + lot.d() + " with the type's margins");
		}
		Placement lotFrame = new Placement(lot.x(), 0, lot.z(), r, lw, ld);
		int ax = lotFrame.worldX(lim.side(), lim.front() - lead), az = lotFrame.worldZ(lim.side(), lim.front() - lead);
		int bx = lotFrame.worldX(lim.side() + w - 1, lim.front() - lead + d - 1), bz = lotFrame.worldZ(lim.side() + w - 1, lim.front() - lead + d - 1);
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
		dev.fractalbuildings.core.gen.Brief.Walk walk = req.brief() == null ? null : req.brief().walk();
		if (walk != null) oy = Math.max(oy, walk.base()); // a town-wall lot stands at its level in the ring's walk profile (the ground under it is filled up to it)
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
		List<Opening> unreachable = new ArrayList<>();
		steps(provisional, lot, t, all, lim.stoop(), unreachable);
		BuildingIr built = shell;
		if (walk != null) all.addAll(Types.get(shell.spec().type()).walkJoins(shell, walk));
		if (!unreachable.isEmpty() && WALL_TYPES.contains(shell.spec().type())) built = sealed(shell, unreachable); // a wall tower or gatehouse standing high above steep ground: no way down in the lot, so no door

		return new PlacedBuilding("b0", List.of(lot.id()), ox, oy, oz, r, built, all);
	}

	/** The shell with these doors walled up: their boxes become wall and the openings go. */
	static BuildingIr sealed(BuildingIr shell, List<Opening> doors) {
		if (shell.spec().type().equals("curtain")) { // a gallery with no door at all: solid wall (its only way in was the guard door)
			List<Box> solid = new ArrayList<>(shell.boxes());
			for (dev.fractalbuildings.core.ir.Volume v : shell.volumes()) solid.add(new Box(v.box(), Role.WALL, Layer.SHELL, 0));
			return new BuildingIr(shell.spec(), shell.bounds(), shell.storeys(), shell.volumes(), List.of(), List.of(), shell.roofs(), solid, List.of());
		}
		List<Box> boxes = new ArrayList<>(shell.boxes());
		List<Opening> openings = new ArrayList<>(shell.openings());
		for (Opening o : doors) {
			int storey = Box.NO_STOREY;
			for (Box b : shell.boxes()) if (b.role() == Role.DOOR && b.box().equals(o.box())) storey = b.storey();
			boxes.add(new Box(o.box(), Role.WALL, Layer.SHELL, storey));
			openings.remove(o);
		}
		return new BuildingIr(shell.spec(), shell.bounds(), shell.storeys(), shell.volumes(), shell.rooms(), openings, shell.roofs(), boxes, shell.hooks());
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
	private static void steps(PlacedBuilding provisional, Lot lot, TerrainProbe t, List<Box> site, boolean stoop, List<Opening> unreachable) {
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
		boolean[] deckCells = new boolean[nx * nz]; // cells of a stoop built for an earlier door
		boolean posted = shell.volumes().stream().anyMatch(v -> v.id().startsWith("leg")); // a hall on posts: its landing is a deck on posts
		for (Opening o : doors) {
			Facing out = outward(o);
			int sx = o.box().x() + out.dx, sz = o.box().z() + out.dz;
			if (sx < x0 || sx > x1 || sz < z0 || sz > z1) continue;
			int s0 = (sz - z0) * nx + (sx - x0);
			if (deckCells[s0]) continue; // the other leaf of a double door: already on the stoop
			int[] parent = new int[nx * nz], depth = new int[nx * nz];
			int found = -1;
			boolean merged = false;
			// a stoop (shopfronts): a door above the ground gets a landing at door level across the front, tried first;
			// the flight down then starts from the landing's end instead of beside the door
			List<int[]> candidates = new ArrayList<>();
			int nOut = 0; // rows between the door row and the lot's street-facing edge row
			while (true) {
				int ex = sx + (nOut + 1) * out.dx, ez = sz + (nOut + 1) * out.dz;
				if (ex < x0 || ex > x1 || ez < z0 || ez > z1) break;
				nOut++;
			}
			int nDeck = 0;
			if (stoop && ground[s0] < oy - 1) candidates.addAll(stoopCandidates(out, sx, sz, x0, x1, z0, z1, nx, ground, oy, base, grid, fp));
			nDeck = candidates.size();
			candidates.add(new int[] {s0});
			boolean[] deck = null;
			for (int ci = 0; ci < candidates.size(); ci++) {
				int[] srcs = candidates.get(ci);
				boolean[] saved = blocked.clone();
				for (int c : srcs) blocked[c] = false;
				// pass 1: a flight of its own to the terrain. Pass 2 (only if pass 1 found none, e.g. earlier flights wall
				// the margin off on a steep slope): the flight may also end by stepping onto an earlier flight, one block
				// down or level, and share the rest of its way down.
				for (int pass = 0; pass < 2 && found < 0; pass++) {
					java.util.Arrays.fill(parent, -2);
					int[] queue = new int[nx * nz];
					int head = 0, tail = 0;
					for (int c : srcs) {
						parent[c] = -1;
						depth[c] = 0;
						queue[tail++] = c;
					}
					Facing[] dirs = {out, out.rotate(1), out.rotate(3), out.rotate(2)};
					while (head < tail && found < 0) {
						int c = queue[head++];
						int cx = x0 + c % nx, cz = z0 + c / nx, d = depth[c];
						int g = ground[c];
						int h = oy - d;
						boolean ends = d == 0 ? g >= oy - 1 : g >= h;
						if (ci < nDeck && (cx - sx) * out.dx + (cz - sz) * out.dz != nOut) ends = false; // a flight from a landing must come down on the lot's street row, not behind the building
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
				if (found >= 0) {
					if (ci < nDeck) {
						deck = new boolean[nx * nz];
						for (int c : srcs) deck[c] = true;
					}
					break;
				}
				System.arraycopy(saved, 0, blocked, 0, blocked.length);
				merged = false;
			}
			if (found < 0) {
				unreachable.add(o);
				continue;
			}
			if (deck != null) { // the landing: a level block at door level per cell; solid below, or (a hall on posts) open between posts so the floor under stays open to the street
				Facing lat = out.rotate(1);
				int outer = Integer.MIN_VALUE;
				for (int c = 0; c < deck.length; c++) if (deck[c]) outer = Math.max(outer, (x0 + c % nx - sx) * out.dx + (z0 + c / nx - sz) * out.dz);
				for (int c = 0; c < deck.length; c++) {
					if (!deck[c]) continue;
					deckCells[c] = true;
					blocked[c] = true;
					flightTop[c] = oy;
					int cx = x0 + c % nx, cz = z0 + c / nx, g = ground[c];
					boolean post = (cx - sx) * out.dx + (cz - sz) * out.dz == outer && Math.floorMod((cx - sx) * lat.dx + (cz - sz) * lat.dz, 3) == 0;
					if (!posted) site.add(new Box(Box3.of(cx, g - base, cz, 1, oy - g, 1), Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY));
					else if (post) site.add(new Box(Box3.of(cx, g - base, cz, 1, oy - g, 1), Role.BEAM, Layer.FOUNDATION, Box.NO_STOREY));
					site.add(new Box(Box3.of(cx, oy - base, cz, 1, 1, 1), Role.STAIR, Layer.FOUNDATION, Box.NO_STOREY));
				}
			}
			for (int c = found; c >= 0; c = parent[c]) {
				blocked[c] = true; // other doors' flights keep off this one
				if (deck != null && deck[c]) continue; // a landing cell, placed above
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


	/** Most cells a stoop is trimmed at one end to make room for the flight. */
	private static final int STOOP_MAX_TRIM = 8;

	/**
	 * Candidate landings (stoops) in front of a door that sits above the ground: the door's row and, when the
	 * lot leaves two or more rows outward, the next row too (the last row in the lot is left for the flight),
	 * out to the lot's edges, minus {@code k} cells at one end so that the flight has room along the facade.
	 * Smallest {@code k} first (the widest landing), both ends. Cells must be building-free, in the lot and
	 * more than a step above the terrain; every candidate contains the door's outside cell.
	 */
	private static List<int[]> stoopCandidates(Facing out, int sx, int sz, int x0, int x1, int z0, int z1, int nx, int[] ground, int oy, int base, VoxelGrid grid, boolean[][] fp) {
		Facing lat = out.rotate(1);
		int rows = 1;
		for (int j = 1; ; j++) {
			int x = sx + j * out.dx, z = sz + j * out.dz;
			if (x < x0 || x > x1 || z < z0 || z > z1) break;
			rows = j; // rows 0..j-1 may be landing; row j is the last in the lot
		}
		rows = Math.min(rows, 2);
		int[][] ext = new int[rows][2]; // per row: cells reachable towards +lat and -lat from the door's column, -1 if the row has none there
		for (int j = 0; j < rows; j++) {
			for (int side = 0; side < 2; side++) {
				int sgn = side == 0 ? 1 : -1, e = -1;
				for (int i = 0; ; i++) {
					int x = sx + j * out.dx + sgn * i * lat.dx, z = sz + j * out.dz + sgn * i * lat.dz;
					if (!deckOk(x, z, x0, x1, z0, z1, nx, ground, oy, base, grid, fp)) break;
					e = i;
				}
				ext[j][side] = e;
			}
		}
		List<int[]> cand = new ArrayList<>();
		if (ext[0][0] < 0) return cand;
		for (int k = 0; k <= STOOP_MAX_TRIM; k++) {
			for (int side = 0; side < 2; side++) { // the end the flight is at
				boolean ok = true;
				List<Integer> cells = new ArrayList<>();
				for (int j = 0; j < rows && ok; j++) {
					int hi = ext[j][0], lo = -ext[j][1];
					if (ext[j][0] < 0 && ext[j][1] < 0) continue;
					if (ext[j][1] < 0) lo = 0;
					if (ext[j][0] < 0) hi = 0;
					if (side == 0) hi -= k; else lo += k;
					if (j == 0 && (lo > 0 || hi < 0)) ok = false;
					for (int i = lo; i <= hi && ok; i++) {
						int x = sx + j * out.dx + i * lat.dx, z = sz + j * out.dz + i * lat.dz;
						cells.add((z - z0) * nx + (x - x0));
					}
				}
				if (ok) cand.add(cells.stream().mapToInt(Integer::intValue).toArray());
			}
		}
		return cand;
	}

	private static boolean deckOk(int x, int z, int x0, int x1, int z0, int z1, int nx, int[] ground, int oy, int base, VoxelGrid grid, boolean[][] fp) {
		if (x < x0 || x > x1 || z < z0 || z > z1) return false;
		if (x >= 0 && z >= 0 && x < fp.length && z < fp[0].length && fp[x][z]) return false;
		if (ground[(z - z0) * nx + (x - x0)] >= oy - 1) return false;
		for (int y = oy - base; y <= oy + 3 - base; y++) if (grid.get(x, y, z) != Role.AIR) return false;
		return true;
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
