package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code door-reaches-street} (6.38 DOORREACH, settlement level): from a building's lowest exterior door a walker reaches a
 * street column (a road ground box of the scene) over columns that rise or fall at most {@link #MAX_STEP} block per step,
 * never through another building's ground-level walls, a rail or open water, and never over a column of another building's
 * lot (street columns excepted). This is a 2D reachability, like the DS-54 walk graph's first cut: headroom and half-block
 * steps are the job of {@code steps-reach-ground} and {@code door-rise}. It runs on the exterior district scene (the shell
 * boxes there carry the doors) and sees only the scene's own roads: a walk that leaves the terrain patch is not a failure
 * ({@link Result#edge}), since the street may be in the next district.
 */
public final class DoorReach {
	public static final String RULE = "door-reaches-street";
	public static final int MAX_STEP = 1;
	private DoorReach() {}

	/** Counts: buildings seen, with a door that reaches a street, whose walk left the scene (unknown), without a door, and failures. */
	public record Result(int buildings, int street, int edge, int noDoor, List<Violation> violations) {}

	private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

	/** The scene check; a walk that leaves the terrain patch is not a failure (the street may be in the next district). */
	public static Result check(SceneDoc scene) { return check(scene, false); }

	/** {@code strictEdge}: a walk that leaves the patch without finding a street fails (a test scene that holds the whole street). */
	public static Result check(SceneDoc scene, boolean strictEdge) {
		TerrainPatch t = scene.terrain();
		List<Violation> out = new ArrayList<>();
		if (t == null) return new Result(0, 0, 0, 0, out);
		Set<Long> streets = new TreeSet<>(), walk = new TreeSet<>(), blocked = new TreeSet<>();
		Map<Long, List<int[]>> foot = new TreeMap<>(); // per column, the world y ranges [from, to) of building blocks
		Map<Long, Integer> surf = new TreeMap<>();
		List<long[]> cuts = new ArrayList<>();
		for (Box g : scene.ground()) {
			Box3 q = g.box();
			for (int x = q.x(); x < q.maxX(); x++)
				for (int z = q.z(); z < q.maxZ(); z++) {
					long k = key(x, z);
					if (g.role() == Role.AIR) { cuts.add(new long[] {x, z, q.y()}); continue; }
					surf.merge(k, q.maxY(), Math::max);
					if (g.role() != Role.GROUND) walk.add(k);
					if (g.role().isRoad()) streets.add(k);
				}
		}
		for (long[] c : cuts) { long k = key((int) c[0], (int) c[1]); Integer s = surf.get(k); if (s == null || s > c[2]) surf.put(k, (int) c[2]); }
		for (PlacedBuilding b : scene.buildings()) {
			Placement pl = b.placement();
			for (Box x : b.shell().boxes()) {
				Role r = x.role();
				if (!r.solid || r == Role.STAIR || r == Role.ROOF || r.name().startsWith("ROOF")) continue; // what a walker can bump into; steps are surfaces, roofs are overhead
				Box3 q = x.box();
				Box3 w = pl.toWorld(q);
				for (int xx = w.x(); xx < w.maxX(); xx++) for (int zz = w.z(); zz < w.maxZ(); zz++) foot.computeIfAbsent(key(xx, zz), k -> new ArrayList<>()).add(new int[] {w.y(), w.maxY()});
			}
			for (Box x : b.siteBoxes()) {
				if (x.role() == Role.AIR) continue;
				Box3 w = pl.toWorld(x.box());
				for (int xx = w.x(); xx < w.maxX(); xx++)
					for (int zz = w.z(); zz < w.maxZ(); zz++) {
						long k = key(xx, zz);
						if (x.role() == Role.RAIL) { blocked.add(k); continue; }
						surf.put(k, w.maxY()); // the site's own surface wins over the terrain
						walk.add(k);
					}
			}
		}
		Map<Long, String> lotAt = new TreeMap<>();
		for (Lot l : scene.lots()) for (int x = l.x(); x < l.x() + l.w(); x++) for (int z = l.z(); z < l.z() + l.d(); z++) lotAt.put(key(x, z), l.id());
		int total = 0, ok = 0, edge = 0, noDoor = 0;
		for (PlacedBuilding b : scene.buildings()) {
			total++;
			Placement pl = b.placement();
			int low = Integer.MAX_VALUE;
			List<Box3> doors = new ArrayList<>();
			for (Box x : b.shell().boxes()) if (x.role() == Role.DOOR) { doors.add(x.box()); low = Math.min(low, x.box().y()); }
			if (doors.isEmpty()) { noDoor++; continue; }
			int needs = 0; // for a failure: the smallest step limit at which a walk exists
			String best = "none";
			Box3 bestAt = null;
			for (Box3 d : doors) {
				if (d.y() != low) continue;
				Box3 w = pl.toWorld(d);
				String res = walk(t, w.x(), w.z(), foot, blocked, streets, walk, surf, lotAt, b.lots(), MAX_STEP);
				if (bestAt == null) bestAt = w;
				if (rank(res) > rank(best)) best = res;
				if (!res.equals("street") && (!res.equals("edge") || strictEdge) && needs == 0) for (int lim = MAX_STEP + 1; lim <= 6 && needs == 0; lim++) if (walk(t, w.x(), w.z(), foot, blocked, streets, walk, surf, lotAt, b.lots(), lim).equals("street")) needs = lim;
				if (best.equals("street")) break;
			}
			if (best.equals("street")) ok++;
			else if (best.equals("edge") && !strictEdge) edge++;
			else out.add(new Violation(RULE, b.id(), "the ground-floor door of " + b.id() + " at " + bestAt.x() + "," + bestAt.z() + " (y " + (b.oy() + low) + ") has no walk to a street: steps of at most " + MAX_STEP + " block, not through another lot" + (needs > 0 ? " (a walk exists with steps of " + needs + ")" : best.equals("edge") ? " (the walk leaves the scene)" : " (none with steps up to 6)"), List.of(bestAt)));
		}
		return new Result(total, ok, edge, noDoor, out);
	}

	/** A building block in the two cells a walker occupies standing on the column's surface. */
	private static boolean solid(Map<Long, List<int[]>> foot, TerrainPatch t, Map<Long, Integer> surf, int x, int z) {
		List<int[]> l = foot.get(key(x, z));
		if (l == null) return false;
		int h = height(t, surf, x, z);
		for (int[] r : l) if (r[0] < h + 2 && r[1] > h) return true;
		return false;
	}

	private static int rank(String s) { return s.equals("street") ? 2 : s.equals("edge") ? 1 : 0; }

	private static final int[][] D4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private static int height(TerrainPatch t, Map<Long, Integer> surf, int x, int z) {
		Integer s = surf.get(key(x, z));
		return s != null ? s : t.floorY(x, z);
	}

	private static String walk(TerrainPatch t, int dx, int dz, Map<Long, List<int[]>> foot, Set<Long> blocked, Set<Long> streets, Set<Long> walk, Map<Long, Integer> surf, Map<Long, String> lotAt, List<String> own, int limit) {
		Set<Long> seen = new TreeSet<>();
		ArrayDeque<int[]> q = new ArrayDeque<>();
		for (int[] d : D4) {
			int x = dx + d[0], z = dz + d[1];
			if (solid(foot, t, surf, x, z) || !seen.add(key(x, z))) continue;
			q.add(new int[] {x, z});
		}
		String res = "none";
		while (!q.isEmpty()) {
			int[] c = q.poll();
			long k = key(c[0], c[1]);
			if (streets.contains(k)) return "street";
			if (!t.covers(c[0], c[1])) { res = "edge"; continue; }
			int y = height(t, surf, c[0], c[1]);
			for (int[] d : D4) {
				int nx = c[0] + d[0], nz = c[1] + d[1];
				long nk = key(nx, nz);
				if (seen.contains(nk) || blocked.contains(nk) || solid(foot, t, surf, nx, nz)) continue;
				if (t.covers(nx, nz) && t.waterDepth(nx, nz) > 0 && !walk.contains(nk)) continue;
				String lot = lotAt.get(nk);
				if (lot != null && !own.contains(lot) && !streets.contains(nk)) continue;
				if (t.covers(nx, nz) && Math.abs(height(t, surf, nx, nz) - y) > limit) continue;
				seen.add(nk);
				q.add(new int[] {nx, nz});
			}
		}
		return res;
	}
}
