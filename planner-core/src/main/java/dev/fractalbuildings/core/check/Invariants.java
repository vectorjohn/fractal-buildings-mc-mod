package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Geometric invariants over the voxel grid of each placed building (plan "Invariants (3.9)").
 * Output order is fixed: building order, then {@link Invariant} order, then cell order
 * (local y, z, x). {@link Invariant#IR_VALID} violations come from {@code IrValidator} and are listed first;
 * the geometric checks follow per building.
 */
public final class Invariants {
	private Invariants() {}

	public static final int MAX_CELLS = 64;
	public static final int WORLD_MIN_Y = -64;
	public static final int WORLD_MAX_Y = 319;

	/** Checks against the scene's own terrain patch. */
	public static List<Violation> check(SceneDoc scene) { return check(scene, null); }

	/**
	 * @param terrain ground height source; {@code null} means the scene's patch, where columns
	 *                outside the patch count as floorY = -64.
	 */
	public static List<Violation> check(SceneDoc scene, TerrainProbe terrain) {
		TerrainProbe ground = terrain != null ? terrain : patchProbe(scene.terrain());
		List<Violation> out = new ArrayList<>(dev.fractalbuildings.core.ir.IrValidator.validate(scene));
		java.util.Map<Long, Integer> surface = scene.genVersion() >= 2 && !scene.ground().isEmpty() ? StairGround.surfaceLevels(scene) : java.util.Map.of();
		for (PlacedBuilding b : scene.buildings()) new BuildingCheck(scene, b, ground, out, surface).run();
		return out;
	}

	private static TerrainProbe patchProbe(TerrainPatch p) {
		return (x, z) -> p.covers(x, z) ? p.floorY(x, z) : WORLD_MIN_Y;
	}

	/** A cell people can walk through: air or a doorway (cells outside the grid count as passable). */
	static boolean walkPassable(VoxelGrid g, int x, int y, int z) {
		if (!g.inGrid(x, y, z)) return true;
		Role r = g.get(x, y, z);
		return r == Role.AIR || r == Role.DOOR;
	}

	/** A cell a person can stand in: two passable cells over a solid one, all inside the grid. */
	static boolean walkStanding(VoxelGrid g, int x, int y, int z) {
		return g.inGrid(x, y, z) && walkPassable(g, x, y, z) && walkPassable(g, x, y + 1, z) && g.inGrid(x, y - 1, z) && walkFloor(g.get(x, y - 1, z));
	}

	/**
	 * The walking rules of {@code rooms-reachable}: every cell reachable on foot (four directions, steps of one up or
	 * down) from the standing cells of the exterior doors. Index of a cell: {@code ((y - minY) * sz + (z - minZ)) * sx + (x - minX)}.
	 * Shared with the high-rise design checks ({@link HighriseChecks}).
	 */
	static boolean[] reachFromStreetDoors(VoxelGrid g, List<Opening> openings) {
		boolean[] seen = new boolean[g.sx * g.sy * g.sz];
		int[] queue = new int[seen.length];
		int head = 0, tail = 0;
		for (Opening o : openings) {
			if (o.kind() != OpeningKind.DOOR) continue;
			if (!o.from().equals(Opening.OUTSIDE) && !o.to().equals(Opening.OUTSIDE)) continue;
			Box3 q = o.box();
			for (int y = q.y(); y < q.maxY(); y++)
				for (int z = q.z(); z < q.maxZ(); z++)
					for (int x = q.x(); x < q.maxX(); x++)
						if (walkStanding(g, x, y, z) && !seen[cellIndex(g, x, y, z)]) {
							seen[cellIndex(g, x, y, z)] = true;
							queue[tail++] = cellIndex(g, x, y, z);
						}
		}
		int[] hx = {1, -1, 0, 0}, hz = {0, 0, 1, -1};
		while (head < tail) {
			int c = queue[head++];
			int cx = g.minX + c % g.sx, cz = g.minZ + (c / g.sx) % g.sz, cy = g.minY + c / (g.sx * g.sz);
			for (int d = 0; d < 4; d++) {
				for (int dy = -1; dy <= 1; dy++) {
					int nx = cx + hx[d], ny = cy + dy, nz = cz + hz[d];
					if (!walkStanding(g, nx, ny, nz)) continue;
					if (dy == 1 && !walkPassable(g, cx, cy + 2, cz)) continue;
					if (dy == -1 && !walkPassable(g, nx, ny + 2, nz)) continue;
					int ni = cellIndex(g, nx, ny, nz);
					if (seen[ni]) continue;
					seen[ni] = true;
					queue[tail++] = ni;
				}
			}
		}
		return seen;
	}

	static int cellIndex(VoxelGrid g, int x, int y, int z) { return ((y - g.minY) * g.sz + (z - g.minZ)) * g.sx + (x - g.minX); }
	/** A block a walker can stand on: solid, but not a fence or rail (1.5 high: a player, who steps 0.6, cannot get onto its top). */
	public static boolean walkFloor(Role r) { return r.solid && r != Role.FENCE && r != Role.RAIL; }

	private static final class BuildingCheck {
		final SceneDoc scene;
		final PlacedBuilding b;
		final TerrainProbe ground;
		final List<Violation> out;
		final java.util.Map<Long, Integer> surface;
		final VoxelGrid g;
		boolean[] outside; // flood over the grid plus a one-cell margin
		int ox, oy, oz, ex, ey, ez; // margin-grid origin and extent

		BuildingCheck(SceneDoc scene, PlacedBuilding b, TerrainProbe ground, List<Violation> out, java.util.Map<Long, Integer> surface) {
			this.surface = surface;
			this.scene = scene;
			this.b = b;
			this.ground = dev.fractalbuildings.core.terrain.SiteGround.probe(b, ground);
			this.out = out;
			this.g = VoxelGrid.of(b);
		}

		void run() {
			inLot();
			inWorld();
			noFloating();
			noGapBelow();
			floodOutside();
			shellClosed();
			roofChecks();
			roomGraph();
			roomsReachable();
			entranceReachable();
			stepsReachGround();
			stairRise();
			windowSize();
		}

		void windowSize() {
			if (scene.genVersion() < 2) return;
			Glazing.Stats s = Glazing.measure(b, g);
			if (s.windows() < 4 || s.windows1x1() * 4 <= s.windows()) return;
			List<Box3> cells = new ArrayList<>();
			for (int[] c : s.small()) cells.add(g.worldCell(c[0], c[1], c[2]));
			report(Invariant.WINDOW_SIZE, s.windows1x1() + " of " + s.windows() + " windows are 1x1 (DS-33: at most a quarter)", cells, cells.size());
		}

		void stairRise() {
			if (scene.genVersion() < 2) return;
			List<StairRise.Fail> fails = StairRise.check(b, g);
			if (fails.isEmpty()) return;
			List<Box3> cells = new ArrayList<>();
			for (StairRise.Fail f : fails) cells.add(g.placement().toWorld(f.cell()));
			report(Invariant.STAIR_RISE, fails.size() + " interior flight(s) need a jump or lead nowhere: " + fails.get(0).opening() + " (" + fails.get(0).why() + ")", cells, cells.size());
		}

		void stepsReachGround() {
			if (scene.genVersion() < 2) return;
			List<StairGround.Fail> fails = StairGround.check(scene, b, g, ground, surface);
			if (fails.isEmpty()) return;
			List<Box3> cells = new ArrayList<>();
			List<String> names = new ArrayList<>();
			for (StairGround.Fail f : fails) { cells.add(f.cell()); names.add(f.door()); }
			report(Invariant.STEPS_REACH_GROUND, "exterior door(s) " + String.join(", ", names) + " have no way down onto the ground (a drop over a block, or no walkable block in front of the last step)", cells, cells.size());
		}

		int floorY(int lx, int lz) { return ground.floorY(g.worldX(lx, lz), g.worldZ(lx, lz)); }

		void report(Invariant inv, String message, List<Box3> cells, int total) {
			String m = total > cells.size() ? message + " (" + total + " cells, first " + cells.size() + " listed)" : message;
			out.add(new Violation(inv.id, b.id(), m, cells));
		}

		/** Collects the cells flagged by {@code bad} in (y, z, x) order and reports them if any. */
		void reportCells(Invariant inv, String message, java.util.function.Predicate<int[]> bad, boolean solidOnly) {
			List<Box3> cells = new ArrayList<>();
			int[] total = {0};
			g.forEach((x, y, z, role) -> {
				if (solidOnly && !(role.solid || role == Role.DOOR)) return;
				if (bad.test(new int[] {x, y, z})) {
					total[0]++;
					if (cells.size() < MAX_CELLS) cells.add(g.worldCell(x, y, z));
				}
			});
			if (total[0] > 0) report(inv, message, cells, total[0]);
		}

		void inLot() {
			List<Lot> lots = new ArrayList<>();
			for (String id : b.lots()) for (Lot l : scene.lots()) if (l.id().equals(id)) lots.add(l);
			reportCells(Invariant.IN_LOT, "voxels outside the building's lot(s)", c -> {
				int wx = g.worldX(c[0], c[2]), wz = g.worldZ(c[0], c[2]);
				int wy = g.worldY(c[1]);
				for (Lot l : lots) if (l.allows(wx, wy, wz)) return false;
				return true;
			}, false);
		}

		void inWorld() {
			reportCells(Invariant.IN_WORLD, "voxels outside world height " + WORLD_MIN_Y + ".." + WORLD_MAX_Y, c -> {
				int wy = g.worldY(c[1]);
				return wy < WORLD_MIN_Y || wy > WORLD_MAX_Y;
			}, false);
		}

		boolean solidish(int x, int y, int z) {
			Role r = g.get(x, y, z);
			return r.solid || r == Role.DOOR;
		}

		void noFloating() {
			int n = g.sx * g.sy * g.sz;
			boolean[] seen = new boolean[n];
			boolean[] floating = new boolean[n];
			int[] queue = new int[n];
			for (int y = g.minY; y < g.minY + g.sy; y++)
				for (int z = g.minZ; z < g.minZ + g.sz; z++)
					for (int x = g.minX; x < g.minX + g.sx; x++) {
						int idx = cell(x, y, z);
						if (seen[idx] || !solidish(x, y, z)) continue;
						int head = 0, tail = 0;
						queue[tail++] = idx;
						seen[idx] = true;
						boolean grounded = false;
						while (head < tail) {
							int c = queue[head++];
							int cx = g.minX + c % g.sx, cz = g.minZ + (c / g.sx) % g.sz, cy = g.minY + c / (g.sx * g.sz);
							if (g.worldY(cy) <= floorY(cx, cz)) grounded = true; // at the ground, or buried in it (a paved cut floor or a kerb below the effective ground)
							for (int d = 0; d < 6; d++) {
								int nx = cx + DX[d], ny = cy + DY[d], nz = cz + DZ[d];
								if (!g.inGrid(nx, ny, nz) || !solidish(nx, ny, nz)) continue;
								int ni = cell(nx, ny, nz);
								if (seen[ni]) continue;
								seen[ni] = true;
								queue[tail++] = ni;
							}
						}
						if (!grounded) for (int i = 0; i < tail; i++) floating[queue[i]] = true;
					}
			reportCells(Invariant.NO_FLOATING, "solid voxels not connected to the ground",
					c -> floating[cell(c[0], c[1], c[2])], true);
		}

		int cell(int x, int y, int z) { return ((y - g.minY) * g.sz + (z - g.minZ)) * g.sx + (x - g.minX); }

		void noGapBelow() {
			if (b.shell().storeys().isEmpty()) return;
			int y0 = b.shell().storeys().get(0).y();
			Set<Long> cols = new TreeSet<>();
			for (Volume v : b.shell().volumes()) {
				if (v.box().y() > y0) continue;
				for (int z = v.box().z(); z < v.box().maxZ(); z++)
					for (int x = v.box().x(); x < v.box().maxX(); x++) cols.add(((long) z << 32) | (x & 0xffffffffL));
			}
			List<Box3> cells = new ArrayList<>();
			int total = 0;
			for (long key : cols) {
				int x = (int) key, z = (int) (key >> 32);
				int lowest = Integer.MIN_VALUE;
				for (int y = g.minY; y < g.minY + g.sy && lowest == Integer.MIN_VALUE; y++) if (g.get(x, y, z).solid) lowest = y;
				int fy = floorY(x, z);
				if (lowest == Integer.MIN_VALUE || g.worldY(lowest) != fy) {
					total++;
					if (cells.size() < MAX_CELLS) cells.add(g.worldCell(x, lowest == Integer.MIN_VALUE ? y0 : lowest, z));
				}
			}
			if (total > 0) report(Invariant.NO_GAP_BELOW, "columns whose lowest voxel is not at the ground", cells, total);
		}

		static final int[] DX = {1, -1, 0, 0, 0, 0};
		static final int[] DY = {0, 0, 1, -1, 0, 0};
		static final int[] DZ = {0, 0, 0, 0, 1, -1};

		/**
		 * Flood of air from outside the grid, treating every non-air role as closed, and also the cells of a
		 * declared {@code window} opening that are air (an unglazed window hole: the opening is the declaration
		 * that the hole is meant, so an undeclared gap in a wall is still a leak).
		 */
		void floodOutside() {
			ox = g.minX - 1; oy = g.minY - 1; oz = g.minZ - 1;
			ex = g.sx + 2; ey = g.sy + 2; ez = g.sz + 2;
			outside = new boolean[ex * ey * ez];
			int[] queue = new int[outside.length];
			int head = 0, tail = 0;
			boolean[] hole = new boolean[outside.length];
			for (Opening o : b.shell().openings()) {
				if (o.kind() != OpeningKind.WINDOW) continue;
				Box3 q = o.box();
				for (int y = q.y(); y < q.maxY(); y++)
					for (int z = q.z(); z < q.maxZ(); z++)
						for (int x = q.x(); x < q.maxX(); x++)
							if (x >= ox && y >= oy && z >= oz && x < ox + ex && y < oy + ey && z < oz + ez && g.get(x, y, z) == Role.AIR) hole[((y - oy) * ez + (z - oz)) * ex + (x - ox)] = true;
			}
			outside[0] = true;
			queue[tail++] = 0;
			while (head < tail) {
				int c = queue[head++];
				int cx = c % ex, cz = (c / ex) % ez, cy = c / (ex * ez);
				for (int d = 0; d < 6; d++) {
					int nx = cx + DX[d], ny = cy + DY[d], nz = cz + DZ[d];
					if (nx < 0 || ny < 0 || nz < 0 || nx >= ex || ny >= ey || nz >= ez) continue;
					int ni = (ny * ez + nz) * ex + nx;
					if (outside[ni] || hole[ni] || g.get(ox + nx, oy + ny, oz + nz).closesShell) continue;
					outside[ni] = true;
					queue[tail++] = ni;
				}
			}
		}

		boolean isOutside(int x, int y, int z) {
			return outside[((y - oy) * ez + (z - oz)) * ex + (x - ox)];
		}

		void shellClosed() {
			List<Box3> cells = new ArrayList<>();
			int total = 0;
			for (Room r : b.shell().rooms()) {
				for (Box3 p : r.parts()) {
					for (int y = p.y(); y < p.maxY(); y++)
						for (int z = p.z(); z < p.maxZ(); z++)
							for (int x = p.x(); x < p.maxX(); x++) {
								if (!g.inGrid(x, y, z) && !(x >= ox && y >= oy && z >= oz && x < ox + ex && y < oy + ey && z < oz + ez)) continue;
								if (g.get(x, y, z) != Role.AIR || !isOutside(x, y, z)) continue;
								boolean well = false; // above a stair cut down through the end of a wall gallery: open on purpose
								for (int yy = g.minY; yy < y && !well; yy++) well = b.shell().spec().type().equals("curtain") && g.get(x, yy, z) == Role.STAIR && yy >= y - 12;
								if (well) continue;
								total++;
								if (cells.size() < MAX_CELLS) cells.add(g.worldCell(x, y, z));
							}
				}
			}
			if (total > 0) report(Invariant.SHELL_CLOSED, "room air connected to the outside", cells, total);
		}

		void roofChecks() {
			List<Box3> uncovered = new ArrayList<>(), open = new ArrayList<>();
			int nUncovered = 0, nOpen = 0;
			Set<String> roofed = new LinkedHashSet<>();
			for (Roof r : b.shell().roofs()) roofed.add(r.volume());
			for (Volume v : b.shell().volumes()) {
				if (!roofed.contains(v.id())) continue;
				Box3 vb = v.box();
				for (int z = vb.z(); z < vb.maxZ(); z++)
					for (int x = vb.x(); x < vb.maxX(); x++) {
						int top = Integer.MIN_VALUE;
						for (int y = g.minY + g.sy - 1; y >= vb.maxY(); y--) if (g.get(x, y, z) == Role.ROOF) { top = y; break; }
						if (top == Integer.MIN_VALUE) {
							boolean cut = false; // (curtains only) a stair cut down through the roof (a town wall walk descending to a tower doorway) is open on purpose
							for (int y = g.minY; y < vb.maxY() && !cut; y++) cut = b.shell().spec().type().equals("curtain") && g.get(x, y, z) == Role.STAIR && g.get(x, vb.maxY(), z) == Role.AIR;
							if (cut) continue;
							nUncovered++;
							if (uncovered.size() < MAX_CELLS) uncovered.add(g.worldCell(x, vb.maxY(), z));
							continue;
						}
						for (int y = vb.maxY(); y < top; y++) {
							if (g.get(x, y, z) == Role.AIR && isOutside(x, y, z)) {
								nOpen++;
								if (open.size() < MAX_CELLS) open.add(g.worldCell(x, y, z));
							}
						}
					}
			}
			if (nUncovered > 0) report(Invariant.ROOF_COVERS, "volume columns with no roof voxel above", uncovered, nUncovered);
			if (nOpen > 0) report(Invariant.ROOF_CLOSED, "air under the roof connected to the outside", open, nOpen);
		}

		void roomGraph() {
			Set<String> reached = new LinkedHashSet<>();
			reached.add(Opening.OUTSIDE);
			boolean grew = true;
			while (grew) {
				grew = false;
				for (Opening o : b.shell().openings()) {
					if (o.kind() == OpeningKind.WINDOW) continue;
					boolean f = reached.contains(o.from()), t = reached.contains(o.to());
					if (f && !t) { reached.add(o.to()); grew = true; }
					if (t && !f) { reached.add(o.from()); grew = true; }
				}
			}
			for (Room r : b.shell().rooms()) {
				if (reached.contains(r.id())) continue;
				List<Box3> cells = new ArrayList<>();
				for (Box3 p : r.parts()) cells.add(g.placement().toWorld(p));
				out.add(new Violation(Invariant.ROOM_GRAPH.id, b.id(), "room " + r.id() + " has no door or stair path from outside", cells));
			}
		}

		boolean passable(int x, int y, int z) {
			if (!g.inGrid(x, y, z)) return true;
			Role r = g.get(x, y, z);
			return r == Role.AIR || r == Role.DOOR;
		}


		boolean standing(int x, int y, int z) {
			return g.inGrid(x, y, z) && passable(x, y, z) && passable(x, y + 1, z) && g.inGrid(x, y - 1, z) && walkFloor(g.get(x, y - 1, z));
		}

		void roomsReachable() {
			if (b.shell().rooms().isEmpty()) return;
			boolean[] seen = reachFromStreetDoors(g, b.shell().openings());
			for (Room r : b.shell().rooms()) {
				boolean hit = false;
				for (Box3 p : r.parts()) {
					for (int y = p.y(); y < p.maxY() && !hit; y++)
						for (int z = p.z(); z < p.maxZ() && !hit; z++)
							for (int x = p.x(); x < p.maxX() && !hit; x++)
								if (g.inGrid(x, y, z) && seen[cell(x, y, z)]) hit = true;
				}
				if (hit) continue;
				List<Box3> cells = new ArrayList<>();
				for (Box3 p : r.parts()) cells.add(g.placement().toWorld(p));
				out.add(new Violation(Invariant.ROOMS_REACHABLE.id, b.id(), "room " + r.id() + " not reachable on foot from an exterior door", cells));
			}
		}

		/**
		 * Generator v1 (genVersion >= 2): from the cell outside each exterior door of the lowest storey with rooms
		 * storey, a walk (rooms-reachable's moves, terrain solid up to each column's floorY, never
		 * back through a door) reaches a position standing on the terrain itself, in one of the
		 * building's lots and outside its footprint.
		 */
		void entranceReachable() {
			if (scene.genVersion() < 2 || b.shell().storeys().isEmpty()) return;
			int lowest = Integer.MAX_VALUE; // the lowest storey with rooms: storey 0, but the open leg zone of a building on stilts has none
			for (Room r : b.shell().rooms()) lowest = Math.min(lowest, r.storey());
			Storey s0 = b.shell().storeys().get(lowest == Integer.MAX_VALUE ? 0 : lowest);
			List<Opening> doors = new ArrayList<>();
			for (Opening o : b.shell().openings()) {
				if (o.kind() != OpeningKind.DOOR || o.box().y() < s0.y() || o.box().y() >= s0.y() + s0.height()) continue;
				if (o.to().equals(Opening.OUTSIDE) == o.from().equals(Opening.OUTSIDE)) continue;
				doors.add(o);
			}
			List<Lot> lots = new ArrayList<>();
			for (String id : b.lots()) for (Lot l : scene.lots()) if (l.id().equals(id)) lots.add(l);
			if (doors.isEmpty() || lots.isEmpty()) return;
			// local x/z window: the lots' corners and the grid, one cell of slack
			int x0 = g.minX, x1 = g.minX + g.sx - 1, z0 = g.minZ, z1 = g.minZ + g.sz - 1;
			for (Lot l : lots)
				for (int cx : new int[] {l.x(), l.x() + l.w() - 1})
					for (int cz : new int[] {l.z(), l.z() + l.d() - 1}) {
						int lx = g.placement().localX(cx, cz), lz = g.placement().localZ(cx, cz);
						x0 = Math.min(x0, lx); x1 = Math.max(x1, lx); z0 = Math.min(z0, lz); z1 = Math.max(z1, lz);
					}
			x0--; x1++; z0--; z1++;
			int nx = x1 - x0 + 1, nz = z1 - z0 + 1;
			int[] top = new int[nx * nz]; // terrain top as local y
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int z = z0; z <= z1; z++)
				for (int x = x0; x <= x1; x++) {
					int t = floorY(x, z) - 1 - b.oy(); // the top solid block: floorY is the first free one
					top[(z - z0) * nx + (x - x0)] = t;
					lo = Math.min(lo, t);
					hi = Math.max(hi, t);
				}
			int ylo = Math.min(lo, g.minY), yhi = Math.max(hi, g.minY + g.sy) + 3, ny = yhi - ylo + 1;
			boolean[] footprint = new boolean[nx * nz];
			for (Volume v : b.shell().volumes()) {
				if (v.box().y() != 0) continue;
				for (int z = v.box().z(); z < v.box().maxZ(); z++)
					for (int x = v.box().x(); x < v.box().maxX(); x++) footprint[(z - z0) * nx + (x - x0)] = true;
			}
			List<Box3> bad = new ArrayList<>();
			List<String> names = new ArrayList<>();
			for (Opening o : doors) {
				Facing out = o.to().equals(Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2);
				int sx = o.box().x() + out.dx, sz = o.box().z() + out.dz, sy = o.box().y();
				boolean[] seen = new boolean[nx * ny * nz];
				int[] queue = new int[seen.length];
				int head = 0, tail = 0;
				for (int y = sy; y >= sy - 1; y--) {
					if (sx < x0 || sx > x1 || sz < z0 || sz > z1 || !walkStanding(top, x0, z0, nx, sx, y, sz)) continue;
					int i = ((y - ylo) * nz + (sz - z0)) * nx + (sx - x0);
					if (!seen[i]) { seen[i] = true; queue[tail++] = i; }
				}
				boolean ok = false;
				int[] hx = {1, -1, 0, 0}, hz = {0, 0, 1, -1};
				while (head < tail && !ok) {
					int c = queue[head++];
					int cx = x0 + c % nx, cz = z0 + (c / nx) % nz, cy = ylo + c / (nx * nz);
					int col = (cz - z0) * nx + (cx - x0);
					if (!footprint[col] && cy - 1 == top[col]) {
						int wx = g.worldX(cx, cz), wz = g.worldZ(cx, cz);
						for (Lot l : lots) if (l.containsColumn(wx, wz)) ok = true;
						if (ok) break;
					}
					for (int d = 0; d < 4; d++) {
						for (int dy = -1; dy <= 1; dy++) {
							int px = cx + hx[d], py = cy + dy, pz = cz + hz[d];
							if (px < x0 || px > x1 || pz < z0 || pz > z1 || py < ylo || py > yhi) continue;
							if (g.get(px, py, pz) == Role.DOOR || g.get(px, py + 1, pz) == Role.DOOR) continue;
							if (!walkStanding(top, x0, z0, nx, px, py, pz)) continue;
							if (dy == 1 && !walkPassable(top, x0, z0, nx, cx, cy + 2, cz)) continue;
							if (dy == -1 && !walkPassable(top, x0, z0, nx, px, py + 2, pz)) continue;
							int ni = ((py - ylo) * nz + (pz - z0)) * nx + (px - x0);
							if (seen[ni]) continue;
							seen[ni] = true;
							queue[tail++] = ni;
						}
					}
				}
				if (!ok) {
					bad.add(g.placement().toWorld(o.box()));
					names.add(o.id());
				}
			}
			if (!bad.isEmpty()) report(Invariant.ENTRANCE_REACHABLE, "exterior door(s) " + String.join(", ", names) + " have no walkable way down to the terrain inside the lot", bad, bad.size());
		}

		boolean walkPassable(int[] top, int x0, int z0, int nx, int x, int y, int z) {
			if (y <= top[(z - z0) * nx + (x - x0)]) return false;
			Role r = g.get(x, y, z);
			return r == Role.AIR || r == Role.DOOR;
		}

		boolean walkStanding(int[] top, int x0, int z0, int nx, int x, int y, int z) {
			if (!walkPassable(top, x0, z0, nx, x, y, z) || !walkPassable(top, x0, z0, nx, x, y + 1, z)) return false;
			return y - 1 <= top[(z - z0) * nx + (x - x0)] || walkFloor(g.get(x, y - 1, z));
		}
	}
}
