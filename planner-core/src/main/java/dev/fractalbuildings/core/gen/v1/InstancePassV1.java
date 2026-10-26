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
	public static PlacedBuilding place(BuildingIr shell, GenRequest req, TypeLimits lim) { return placeChecked(shell, req, lim).building(); }

	/** A placement, and whether every flight and landing in it can be railed inside the lot (DS-8) and every door has a way down. */
	public record Placed(PlacedBuilding building, boolean guarded) {}

	public static Placed placeChecked(BuildingIr shell, GenRequest req, TypeLimits lim) {
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
		boolean guarded = steps(provisional, lot, t, all, lim.stoop(), unreachable) && unreachable.isEmpty();
		BuildingIr built = shell;
		if (walk != null) all.addAll(Types.get(shell.spec().type()).walkJoins(shell, walk));
		if (!unreachable.isEmpty() && WALL_TYPES.contains(shell.spec().type())) built = sealed(shell, unreachable); // a wall tower or gatehouse standing high above steep ground: no way down in the lot, so no door

		return new Placed(new PlacedBuilding("b0", List.of(lot.id()), ox, oy, oz, r, built, all), guarded);
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
	private static boolean steps(PlacedBuilding provisional, Lot lot, TerrainProbe t, List<Box> site, boolean stoop, List<Opening> unreachable) {
		BuildingIr shell = provisional.shell();
		List<Opening> doors = entranceDoors(shell);
		if (doors.isEmpty()) return true;
		boolean guarded = true; // false when a landing or flight had to go where no rail can stand inside the lot
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
		int[] outG = new int[nx * nz]; // lowest ground just outside the lot beside each edge cell, MAX_VALUE for inner cells: a rail there would stand outside the lot
		java.util.Arrays.fill(outG, Integer.MAX_VALUE);
		for (int z = z0; z <= z1; z++)
			for (int x = x0; x <= x1; x++)
				for (Facing f : Facing.values()) {
					int ax = x + f.dx, az = z + f.dz;
					if (ax >= x0 && ax <= x1 && az >= z0 && az <= z1) continue;
					int i = (z - z0) * nx + (x - x0);
					outG[i] = Math.min(outG[i], t.floorY(p.worldX(ax, az), p.worldZ(ax, az)));
				}
		int[] flightTop = new int[nx * nz]; // highest solid of a placed flight's cell, MIN_VALUE where there is none
		java.util.Arrays.fill(flightTop, Integer.MIN_VALUE);
		for (Opening o : doors) { // keep every door's own outside cell free of other flights
			Facing f = outward(o);
			int x = o.box().x() + f.dx, z = o.box().z() + f.dz;
			if (x >= x0 && x <= x1 && z >= z0 && z <= z1) blocked[(z - z0) * nx + (x - x0)] = true;
		}
		boolean[] deckCells = new boolean[nx * nz]; // cells of a stoop built for an earlier door
		List<int[]> treads = new ArrayList<>(); // every placed step or landing cell: x, z, tread level, least drop to the ground beside it that gets a rail
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
			// a door wider than one cell opens onto a landing as wide as the door: no drop beside the flight, in the doorway
			List<Integer> front = new ArrayList<>();
			boolean lowFront = false;
			Box3 ob0 = o.box();
			for (int x = ob0.x(); x < ob0.maxX(); x++)
				for (int z = ob0.z(); z < ob0.maxZ(); z++) {
					int fx = x + out.dx, fz = z + out.dz;
					if (fx < x0 || fx > x1 || fz < z0 || fz > z1) continue;
					int fi = (fz - z0) * nx + (fx - x0);
					if (!front.contains(fi)) front.add(fi);
					lowFront |= ground[fi] < oy;
				}
			boolean wide = front.size() > 1 && lowFront;
			if (stoop && ground[s0] < oy || wide) {
				for (int[] cand : stoopCandidates(out, sx, sz, x0, x1, z0, z1, nx, ground, oy, base, grid, fp)) {
					if (wide) { boolean covers = true; for (int fi : front) if (ground[fi] < oy) { boolean in = false; for (int c : cand) in |= c == fi; covers &= in; } if (!covers) continue; }
					candidates.add(cand);
				}
				if (wide) { // just the width of the door
					int[] strip = front.stream().filter(fi -> ground[fi] < oy).mapToInt(Integer::intValue).toArray();
					boolean ok = true;
					for (int fi : strip) ok &= deckOk(x0 + fi % nx, z0 + fi / nx, x0, x1, z0, z1, nx, ground, oy, base, grid, fp);
					if (ok) candidates.add(strip);
				}
			}
			// the same landings without the cells on the lot line that need a rail (only pass 0 takes these): the landing stays inside the lot
			for (int ci = 0, n = candidates.size(); ci < n; ci++) {
				int[] full = candidates.get(ci);
				int[] kept = java.util.Arrays.stream(full).filter(c -> oy - outG[c] < RAIL_DROP_LANDING).toArray();
				if (kept.length == 0 || kept.length == full.length || !connected(kept, s0, nx)) continue;
				candidates.add(kept);
			}
			nDeck = candidates.size();
			candidates.add(new int[] {s0});
			boolean[] deck = null;
			int foundPass = -1, foundCi = -1;
			for (int strictPass = 0; strictPass < 3 && found < 0; strictPass++) { // pass 0: only landings and flights whose open sides lie inside the lot (every one can be railed); pass 1: flights only (a landing on the lot line stays); pass 2: any route
			boolean strict = strictPass <= 1, strictLanding = strictPass == 0;
			for (int ci = 0; ci < candidates.size(); ci++) {
				int[] srcs = candidates.get(ci);
				if (strictPass == 0 && nDeck > 0 && ci >= nDeck) continue; // a stoop door keeps its landing while one is possible
				if (strictLanding && ci < nDeck) { boolean edge = false; for (int c : srcs) edge |= oy - outG[c] >= RAIL_DROP_LANDING; if (edge) continue; }
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
						boolean ends = d == 0 ? g >= oy && g <= oy + 1 : g >= h + 1 && g <= h + 2; // off the door floor (top oy + 1) or the last stair (top h + 3/2, one cell back) onto ground at most a block below and at most half a block above
						if (ci < nDeck && (cx - sx) * out.dx + (cz - sz) * out.dz != nOut) ends = false; // a flight from a landing must come down on the lot's street row, not behind the building
						int top = ends ? g : h; // highest solid under the feet
						// the cells the walker occupies (and the step block itself) must be free of the building
						boolean free = true;
						for (int y = top - base; y <= top + 3 - base && free; y++) free = grid.get(cx, y, cz) == Role.AIR;
						if (!free) continue;
						if (ends) { found = c; break; }
						if (strict && !(ci < nDeck && d == 0 && !strictLanding) && oy - d - outG[c] >= (ci < nDeck && d == 0 ? RAIL_DROP_LANDING : RAIL_DROP_FLIGHT)) continue; // a rail would have to stand outside the lot: this cell is not railable
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
					foundPass = strictPass;
					foundCi = ci;
					if (ci < nDeck) {
						deck = new boolean[nx * nz];
						for (int c : srcs) deck[c] = true;
					}
					break;
				}
				System.arraycopy(saved, 0, blocked, 0, blocked.length);
				merged = false;
			}
			}
			if (found < 0) {
				unreachable.add(o);
				continue;
			}
			if (foundPass >= 2) guarded = false;
			else if (foundPass == 1 && foundCi < nDeck) for (int c : candidates.get(foundCi)) guarded &= oy - outG[c] < RAIL_DROP_LANDING;
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
					treads.add(new int[] {cx, cz, oy, RAIL_DROP_LANDING});
				}
			}
			for (int c = found; c >= 0; c = parent[c]) {
				blocked[c] = true; // other doors' flights keep off this one
				if (deck != null && deck[c]) continue; // a landing cell, placed above
				flightTop[c] = c == found && !merged ? ground[c] : oy - depth[c];
				if (c == found && !merged) continue; // ends on the terrain itself
				int cx = x0 + c % nx, cz = z0 + c / nx, h = oy - depth[c], g = ground[c];
				if (h >= g) { // a step on the ground itself (h == g) needs no foundation under it
					if (h > g) site.add(new Box(Box3.of(cx, g - base, cz, 1, h - g, 1), Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY));
					site.add(new Box(Box3.of(cx, h - base, cz, 1, 1, 1), Role.STAIR, Layer.FOUNDATION, Box.NO_STOREY));
					treads.add(new int[] {cx, cz, h, RAIL_DROP_FLIGHT});
				}
			}
		}
		boolean[] doorFront = new boolean[nx * nz]; // every cell in front of a door leaf (a wide door has several): a rail there would wall the door
		for (Opening o : doors) {
			Facing f = outward(o);
			Box3 ob = o.box();
			for (int x = ob.x(); x < ob.maxX(); x++)
				for (int z = ob.z(); z < ob.maxZ(); z++) {
					int fx = x + f.dx, fz = z + f.dz;
					if (fx >= x0 && fx <= x1 && fz >= z0 && fz <= z1) doorFront[(fz - z0) * nx + (fx - x0)] = true;
				}
		}
		List<Opening> extDoors = new ArrayList<>(); // every exterior door of any storey: no rail stands in front of one at its own height
		for (Opening o : shell.openings()) if (o.kind() == OpeningKind.DOOR && o.to().equals(Opening.OUTSIDE) != o.from().equals(Opening.OUTSIDE)) extDoors.add(o);
		rails(treads, lot, p, x0, x1, z0, z1, nx, ground, blocked, doorFront, extDoors, grid, base, site);
		return guarded;
	}

	/** Least drop (blocks, tread level minus the ground beside it) that gets a rail: along a flight, and along a landing. */
	static final int RAIL_DROP_FLIGHT = 2, RAIL_DROP_LANDING = 1;

	/**
	 * Railings (6.9): for each free cell beside a step or landing where the ground is far enough below the tread, a kerb of
	 * foundation up to the tread's walking surface (tread y + 1) with a {@link Role#RAIL} block on it, so the rail stands one block above the surface it guards (fence collision 1.5: a player, who steps 0.6, cannot get onto it). Rails
	 * go only on cells that are not part of any flight, landing or door approach ({@code blocked}), are inside the lot and are
	 * free of the building, so they never cross the line of travel; a walker could only step sideways onto ground at most one
	 * block below the tread, and no rail stands there. Where several treads touch a cell the highest decides.
	 */
	private static void rails(List<int[]> treads, Lot lot, Placement p, int x0, int x1, int z0, int z1, int nx, int[] ground, boolean[] blocked, boolean[] doorFront, List<Opening> extDoors, VoxelGrid grid, int base, List<Box> site) {
		int[] level = new int[blocked.length];
		java.util.Arrays.fill(level, Integer.MIN_VALUE);
		for (int[] t : treads) {
			for (Facing f : Facing.values()) {
				int x = t[0] + f.dx, z = t[1] + f.dz;
				if (x < x0 || x > x1 || z < z0 || z > z1) continue;
				int i = (z - z0) * nx + (x - x0);
				if (blocked[i] || !lot.containsColumn(p.worldX(x, z), p.worldZ(x, z)) || t[2] - ground[i] < t[3]) continue;
				level[i] = Math.max(level[i], t[2]);
			}
		}
		// corner posts: a free cell diagonal to a tread with a rail on both its sides along x and z would leave a gap between two
		// rails that meet diagonally (wider than a player), so it gets a post too
		int[] corner = new int[blocked.length];
		java.util.Arrays.fill(corner, Integer.MIN_VALUE);
		for (int i = 0; i < level.length; i++) {
			if (level[i] != Integer.MIN_VALUE || blocked[i] || doorFront[i]) continue;
			int cx = x0 + i % nx, cz = z0 + i / nx;
			if (!lot.containsColumn(p.worldX(cx, cz), p.worldZ(cx, cz))) continue;
			int lx = Math.max(railLevel(level, x0, x1, z0, z1, nx, cx - 1, cz), railLevel(level, x0, x1, z0, z1, nx, cx + 1, cz));
			int lz = Math.max(railLevel(level, x0, x1, z0, z1, nx, cx, cz - 1), railLevel(level, x0, x1, z0, z1, nx, cx, cz + 1));
			if (lx != Integer.MIN_VALUE && lz != Integer.MIN_VALUE) corner[i] = Math.max(lx, lz);
		}
		for (int i = 0; i < level.length; i++) if (corner[i] != Integer.MIN_VALUE) level[i] = corner[i];
		// a rail's top must be 1.5 above every tread within one cell of it (a player steps 0.6 and may cut a corner), not only the
		// tread it guards: beside a flight the rail takes the level of the highest tread around it
		int[] raised = level.clone();
		for (int[] t : treads)
			for (int dz = -1; dz <= 1; dz++)
				for (int dx = -1; dx <= 1; dx++) {
					int x = t[0] + dx, z = t[1] + dz;
					if (x < x0 || x > x1 || z < z0 || z > z1) continue;
					int i = (z - z0) * nx + (x - x0);
					if (level[i] != Integer.MIN_VALUE) raised[i] = Math.max(raised[i], t[2]);
				}
		level = raised;
		for (int i = 0; i < level.length; i++) {
			if (level[i] == Integer.MIN_VALUE) continue;
			int cx = x0 + i % nx, cz = z0 + i / nx, h = level[i], g = ground[i];
			boolean free = true;
			for (int y = g - base; y <= h + 1 - base && free; y++) free = grid.get(cx, y, cz) == Role.AIR;
			if (!free) continue;
			for (Opening o : extDoors) { // a door at the rail's height is not walled in
				Facing f = outward(o);
				Box3 ob = o.box();
				if (h + 1 - base >= ob.y() && h + 1 - base < ob.maxY() && cx - f.dx >= ob.x() && cx - f.dx < ob.maxX() && cz - f.dz >= ob.z() && cz - f.dz < ob.maxZ()) free = false;
			}
			if (!free) continue;
			site.add(new Box(Box3.of(cx, g - base, cz, 1, h + 1 - g, 1), Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY));
			site.add(new Box(Box3.of(cx, h + 1 - base, cz, 1, 1, 1), Role.RAIL, Layer.FOUNDATION, Box.NO_STOREY));
		}
	}

	private static int railLevel(int[] level, int x0, int x1, int z0, int z1, int nx, int x, int z) {
		return x < x0 || x > x1 || z < z0 || z > z1 ? Integer.MIN_VALUE : level[(z - z0) * nx + (x - x0)];
	}


	/** Whether these cells form one 4-connected piece that contains {@code start}. */
	private static boolean connected(int[] cells, int start, int nx) {
		java.util.Set<Integer> left = new java.util.TreeSet<>();
		for (int c : cells) left.add(c);
		if (!left.remove(start)) return false;
		java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
		q.add(start);
		while (!q.isEmpty()) {
			int c = q.poll();
			for (int n : new int[] {c - 1, c + 1, c - nx, c + nx}) if (left.remove(n)) q.add(n);
		}
		return left.isEmpty();
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
		if (ground[(z - z0) * nx + (x - x0)] >= oy) return false;
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
