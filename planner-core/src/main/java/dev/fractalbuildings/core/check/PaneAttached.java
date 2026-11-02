package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.raster.PieceRaster;
import dev.fractalbuildings.core.raster.StyleAt;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DS-51 {@code pane-attached} (GLASSSTUB): a window cell is a glass pane in the world, and a pane joins only to
 * blocks on its four horizontal sides. Three ways a pane floats or sticks out of a facade, each a failure:
 * <ul>
 * <li>{@code lone}: no pane or full block beside it (a wall, trim, beam, roof...; not air, a door, a fence or a stair);</li>
 * <li>{@code corner}: it joins only along an L, a block on one side and another on a perpendicular side with open air on
 * the other two (a pane at an outer corner);</li>
 * <li>{@code capped}: nothing above it (open air or the edge of the grid): a pane at a wall top has no lintel.</li>
 * </ul>
 * Two views: {@link #check(VoxelGrid)} reads one building's own cells, {@link #checkScene} reads the cells the world
 * ends up with (the {@link PieceRaster} of every building of the scene, in list order, plus the ground), where an earlier
 * building's cell or air clear takes a cell from a later one and can strip the walls from beside its pane.
 */
public final class PaneAttached {
	private PaneAttached() {}

	private static final int[] DX = {1, -1, 0, 0}, DZ = {0, 0, 1, -1};

	/** A failing pane: cell (local for {@link #check}, world for {@link #checkScene}) and the reason. */
	public record Fail(int x, int y, int z, String why) {}

	/** Roles a pane joins to: another pane, or a full solid block. */
	static boolean attaches(Role r) {
		if (r == Role.WINDOW) return true;
		return r.solid && r != Role.AIR && r != Role.FENCE && r != Role.RAIL && r != Role.STAIR && !r.isRoofStair() && r != Role.ROOF_SLAB && r != Role.ROOF_SLAB_TOP;
	}

	/** Why the pane at (x, y, z) fails, or null. {@code role} answers for any cell (AIR where nothing is). */
	static String judge(RoleAt role, int x, int y, int z) {
		boolean[] n = new boolean[4];
		int count = 0;
		for (int d = 0; d < 4; d++) {
			n[d] = attaches(role.at(x + DX[d], y, z + DZ[d]));
			if (n[d]) count++;
		}
		if (count == 0) return "lone";
		if (count == 2 && (n[0] || n[1]) && (n[2] || n[3])) return "corner";
		if (role.at(x, y + 1, z) == Role.AIR) return "capped";
		return null;
	}

	@FunctionalInterface
	interface RoleAt { Role at(int x, int y, int z); }

	/** Failing panes of one building alone, local cells in (y, z, x) order. */
	public static List<Fail> check(VoxelGrid g) {
		List<Fail> out = new ArrayList<>();
		RoleAt at = (x, y, z) -> g.inGrid(x, y, z) ? g.get(x, y, z) : Role.AIR;
		for (int y = g.minY; y < g.minY + g.sy; y++)
			for (int z = g.minZ; z < g.minZ + g.sz; z++)
				for (int x = g.minX; x < g.minX + g.sx; x++) {
					if (g.get(x, y, z) != Role.WINDOW) continue;
					String why = judge(at, x, y, z);
					if (why != null) out.add(new Fail(x, y, z, why));
				}
		return out;
	}

	/** Failing panes of the scene as the world gets them, by building id (in building order), world cells in (y, z, x) order. */
	public static Map<String, List<Fail>> checkScene(SceneDoc scene) {
		List<PlacedBuilding> all = scene.buildings();
		Map<String, List<Fail>> out = new java.util.LinkedHashMap<>();
		if (all.isEmpty()) return out;
		Box3 clip = null;
		for (PlacedBuilding b : all) {
			Box3 w = b.placement().toWorld(b.shell().bounds());
			clip = clip == null ? w : union(clip, w);
		}
		Map<Long, Role> cells = new java.util.TreeMap<>();
		Map<Long, Integer> owner = new java.util.TreeMap<>();
		for (int i = 0; i < all.size(); i++) {
			final int k = i;
			PieceRaster.building(all, i, clip, (x, y, z, role, id, shape) -> {
				long key = key(x, y, z);
				cells.put(key, role);
				owner.put(key, k);
			});
		}
		if (!scene.ground().isEmpty()) {
			PieceRaster.ground(scene.ground(), all, StyleAt.nearest(all, "medieval"), clip, (x, y, z, role, id, shape) -> cells.putIfAbsent(key(x, y, z), role));
		}
		RoleAt at = (x, y, z) -> cells.getOrDefault(key(x, y, z), Role.AIR);
		List<long[]> panes = new ArrayList<>();
		for (Map.Entry<Long, Role> e : cells.entrySet()) if (e.getValue() == Role.WINDOW && owner.containsKey(e.getKey())) panes.add(new long[] {e.getKey()});
		panes.sort((a, b) -> Long.compare(a[0], b[0])); // deterministic: by packed key
		for (long[] p : panes) {
			int x = kx(p[0]), y = ky(p[0]), z = kz(p[0]);
			String why = judge(at, x, y, z);
			if (why != null) out.computeIfAbsent(all.get(owner.get(p[0])).id(), s -> new ArrayList<>()).add(new Fail(x, y, z, why));
		}
		for (List<Fail> l : out.values()) l.sort(java.util.Comparator.comparingInt(Fail::y).thenComparingInt(Fail::z).thenComparingInt(Fail::x));
		return out;
	}

	private static Box3 union(Box3 a, Box3 b) {
		int x0 = Math.min(a.x(), b.x()), y0 = Math.min(a.y(), b.y()), z0 = Math.min(a.z(), b.z());
		return new Box3(x0, y0, z0, Math.max(a.maxX(), b.maxX()) - x0, Math.max(a.maxY(), b.maxY()) - y0, Math.max(a.maxZ(), b.maxZ()) - z0);
	}

	// pack (x, y, z): x and z within +-2^23, y within +-2^15 (offset keeps the sort order meaningful, not (y, z, x))
	private static long key(int x, int y, int z) { return ((long) (x + (1 << 23)) << 40) | ((long) (y + (1 << 15)) << 24) | (z + (1 << 23)) & 0xFFFFFFL; }
	private static int kx(long k) { return (int) (k >>> 40) - (1 << 23); }
	private static int ky(long k) { return (int) ((k >>> 24) & 0xFFFF) - (1 << 15); }
	private static int kz(long k) { return (int) (k & 0xFFFFFFL) - (1 << 23); }
}
