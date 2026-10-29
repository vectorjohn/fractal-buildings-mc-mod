package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.List;
import java.util.Map;

/**
 * {@code steps-reach-ground} (STAIRGROUND): from every exterior ground-storey door, a walker can get down onto the real
 * ground (terrain, or the road or paving the scene's ground boxes lay over it) without a drop of more than a block or a
 * rise of more than half a block. Unlike {@code entrance-reachable} it uses the probe's own convention (a column's
 * {@code floorY} is the first free block, so the surface is at {@code floorY}) and the scene's ground boxes.
 *
 * <p>Model, in half blocks: the ground surface of a column is {@code 2 * G}; a {@link Role#STAIR} block at {@code y}
 * has its top at {@code 2y + 1} (a half step, as the mod will place stairs or slabs); any other solid block at {@code y} has
 * top {@code 2y + 2}. A walker moves to a neighbouring column's surface when it is at most 1 half block higher or 2
 * lower, with two free blocks above the surface. The walk starts at the door's floor in the cell outside the door and
 * must reach a column's ground surface (not a foundation or step) inside the lot or the cells around it.
 */
public final class StairGround {
	private StairGround() {}

	/** Columns of road, paving and deck surfaces: the level (first free block) the scene's ground boxes give, by packed (x, z). */
	static Map<Long, Integer> surfaceLevels(SceneDoc scene) {
		Map<Long, Integer> m = new TreeMap<>();
		for (Box b : scene.ground()) {
			if (!b.role().isRoad() && b.role() != Role.PAVING && b.role() != Role.DECK) continue;
			Box3 q = b.box();
			for (int z = q.z(); z < q.maxZ(); z++)
				for (int x = q.x(); x < q.maxX(); x++) m.merge(key(x, z), q.maxY(), Math::max);
		}
		return m;
	}

	static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

	/** The exterior doors of the lowest storey with rooms: the ones that need a way down. */
	static List<Opening> doors(PlacedBuilding b) {
		List<Opening> doors = new ArrayList<>();
		if (b.shell().storeys().isEmpty()) return doors;
		int lowest = Integer.MAX_VALUE;
		for (Room r : b.shell().rooms()) lowest = Math.min(lowest, r.storey());
		Storey s0 = b.shell().storeys().get(lowest == Integer.MAX_VALUE ? 0 : lowest);
		for (Opening o : b.shell().openings()) {
			if (o.kind() != OpeningKind.DOOR || o.box().y() < s0.y() || o.box().y() >= s0.y() + s0.height()) continue;
			if (o.to().equals(Opening.OUTSIDE) == o.from().equals(Opening.OUTSIDE)) continue;
			doors.add(o);
		}
		return doors;
	}

	/** One result per failing door: its id and the cell outside it (world). */
	record Fail(String door, Box3 cell, String why) {}

	/** The ids of the exterior doors of a placed building with no way down onto the ground (the {@code steps-reach-ground} measure, on the ground as the building leaves it, no roads); the generator walls up the secondary ones (DS-35). */
	public static List<String> failingDoors(PlacedBuilding b, List<Lot> lots, TerrainProbe raw) {
		List<String> ids = new ArrayList<>();
		for (Fail f : check(lots, b, VoxelGrid.of(b), dev.fractalbuildings.core.terrain.SiteGround.probe(b, raw), Map.of())) ids.add(f.door());
		return ids;
	}

	static List<Fail> check(SceneDoc scene, PlacedBuilding b, VoxelGrid g, TerrainProbe terrain, Map<Long, Integer> surface) {
		List<Lot> lots = new ArrayList<>();
		for (String id : b.lots()) for (Lot l : scene.lots()) if (l.id().equals(id)) lots.add(l);
		return check(lots, b, g, terrain, surface);
	}

	private static List<Fail> check(List<Lot> lots, PlacedBuilding b, VoxelGrid g, TerrainProbe terrain, Map<Long, Integer> surface) {
		List<Fail> fails = new ArrayList<>();
		List<Opening> doors = doors(b);
		if (doors.isEmpty()) return fails;
		int x0 = g.minX, x1 = g.minX + g.sx - 1, z0 = g.minZ, z1 = g.minZ + g.sz - 1;
		for (Lot l : lots)
			for (int cx : new int[] {l.x(), l.x() + l.w() - 1})
				for (int cz : new int[] {l.z(), l.z() + l.d() - 1}) {
					int lx = g.placement().localX(cx, cz), lz = g.placement().localZ(cx, cz);
					x0 = Math.min(x0, lx); x1 = Math.max(x1, lx); z0 = Math.min(z0, lz); z1 = Math.max(z1, lz);
				}
		x0 -= 2; x1 += 2; z0 -= 2; z1 += 2; // the street in front of the lot
		int nx = x1 - x0 + 1, nz = z1 - z0 + 1;
		int[] level = new int[nx * nz]; // ground surface level (first free block) per column
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int z = z0; z <= z1; z++)
			for (int x = x0; x <= x1; x++) {
				int wx = g.worldX(x, z), wz = g.worldZ(x, z);
				int lv = terrain.floorY(wx, wz);
				Integer s = surface.get(key(wx, wz));
				if (s != null) lv = s;
				level[(z - z0) * nx + (x - x0)] = lv;
				lo = Math.min(lo, lv);
				hi = Math.max(hi, lv);
			}
		int ylo = Math.min(lo, g.worldY(g.minY)) - 2, yhi = Math.max(hi, g.worldY(g.minY + g.sy)) + 3;
		for (Opening o : doors) {
			Facing out = o.to().equals(Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2);
			int sx = o.box().x() + out.dx, sz = o.box().z() + out.dz;
			Box3 cell = g.placement().toWorld(Box3.of(sx, o.box().y(), sz, 1, 1, 1));
			if (sx < x0 || sx > x1 || sz < z0 || sz > z1) { fails.add(new Fail(o.id(), cell, "outside cell off the window")); continue; }
			int floor2 = 2 * g.worldY(o.box().y()); // the door's floor: feet level
			// breadth-first over (column, surface in half blocks)
			java.util.Set<Long> seen = new TreeSet<>();
			List<int[]> queue = new ArrayList<>();
			queue.add(new int[] {o.box().x(), o.box().z(), floor2});
			seen.add(node(o.box().x(), o.box().z(), floor2));
			boolean ok = false;
			for (int head = 0; head < queue.size() && !ok; head++) {
				int[] c = queue.get(head);
				for (Facing f : head == 0 ? new Facing[] {out} : Facing.values()) {
					int px = c[0] + f.dx, pz = c[1] + f.dz;
					if (px < x0 || px > x1 || pz < z0 || pz > z1) continue;
					int lv = level[(pz - z0) * nx + (px - x0)];
					for (int top2 = 2 * (ylo + 1); top2 <= 2 * yhi; top2++) {
						if (top2 - c[2] > 1 || c[2] - top2 > 2) continue;
						int kind = surfaceKind(g, px, pz, top2, lv);
						if (kind == NONE) continue;
						long k = node(px, pz, top2);
						if (seen.contains(k)) continue;
						seen.add(k);
						if (kind == GROUND) { ok = true; break; }
						queue.add(new int[] {px, pz, top2});
					}
					if (ok) break;
				}
			}
			if (!ok) fails.add(new Fail(o.id(), cell, "no walkable way down to the ground"));
		}
		return fails;
	}

	static final int NONE = 0, GROUND = 1, BUILT = 2;

	static long node(int x, int z, int top2) { return (((long) top2 + 4096) << 40) ^ (((long) x + 100000) << 20) ^ ((long) z + 100000); }

	/** Whether a walker can stand at {@code top2} half blocks in the column: on the ground, or on a building block, or neither. */
	static int surfaceKind(VoxelGrid g, int lx, int lz, int top2, int level) {
		boolean stair = (top2 & 1) == 1;
		int y = stair ? (top2 - 1) / 2 : top2 / 2 - 1;
		boolean groundHere = !stair && y == level - 1 && !solidBuilt(g, lx, y, lz);
		boolean built = solidBuilt(g, lx, y, lz) && (stair ? g.get(lx, (y - g.placement().oy()), lz) == Role.STAIR : g.get(lx, (y - g.placement().oy()), lz) != Role.STAIR);
		if (!groundHere && !built) return NONE;
		int body = y + 1; // the two blocks above the carrier must be free
		for (int k = body; k < body + 2; k++) if (k < level || solidBuilt(g, lx, k, lz) || g.get(lx, k - g.placement().oy(), lz) == Role.DOOR) return NONE;
		return groundHere ? GROUND : BUILT;
	}

	private static boolean solidBuilt(VoxelGrid g, int lx, int wy, int lz) {
		Role r = g.get(lx, (wy - g.placement().oy()), lz);
		return r.solid;
	}
}
