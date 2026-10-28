package dev.fractalbuildings.core.road;

import dev.fractalbuildings.core.road.RoadNet.Col;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Roads and a foreign village (VILLROAD): where the planned road line runs into the pieces of a structure the planner cannot see (a
 * vanilla village), the road bends around the pieces with a margin and rejoins its line, and one short spur joins it to the village's
 * nearest street piece, so the two read as one network.
 *
 * <p>Pure and total: the answer is a function of the village's piece boxes and the road columns of the window (see {@link #influence}),
 * nothing else, so every chunk that asks gets the same answer in any order. The mod reads the boxes from the structure starts and the
 * columns from the planner, and applies {@link Result} to the columns of the chunks it draws.
 *
 * <p><b>Bend</b>: columns within {@link #MARGIN} of a piece box are removed. A removed run (columns within 2 of each other) with exactly
 * two ends (clusters of surviving columns touching it) is replaced by the cheapest 8-connected path between the ends over the free
 * cells, stamped with the road's width; deck heights run linearly along it. Anything else (no path, a junction in the village) is left as
 * it is: the mod's yield to the pieces still applies there.
 *
 * <p><b>Spur</b>: when the village's path cells are known ({@link Village#paths}, read by the mod from the street pieces' templates):
 * for each road component (at most {@link #MAX_SPURS}), its column nearest to a path cell within {@link #SPUR_MAX}, a straight 4-connected
 * line toward that cell, or the cheapest path round the non-street pieces, that stops on the cell beside the first path cell it meets: the
 * spur abuts the village's own street and never overwrites it ({@link #pathSpurs}). Without path cells (unit fixtures, an unknown
 * village kind) the older rule applies: the line ends on a street box's centre line. The band is 3 wide, 5 at the road; the mod lays it
 * on the ground in the village street material.
 */
public final class VillageJoin {
	private VillageJoin() {}

	/** Free space kept between the road centre line's band and any piece box. */
	public static final int MARGIN = 4;
	/** Room beyond the margin: for the path round the village, and as far as a spur reaches (road columns farther than margin + this are not read). */
	public static final int PAD = 40;
	/** Columns this close beyond the margin go too, so a road of any width is cut whole (the widest is 4). */
	public static final int SHOULDER = 2;
	/** Longest spur, from a road column to the box it reaches. */
	public static final int SPUR_MAX = 40;
	public static final int MAX_SPURS = 2;
	/** Spur band: half-width 1 (3 wide, a village street), 2 (5 wide) for the first MOUTH cells from the road. */
	public static final int SPUR_HALF = 1, MOUTH = 2;

	public record Box(int minX, int minZ, int maxX, int maxZ) {
		public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }

		/** Squared distance from a cell to the nearest cell of the box. */
		public long dist2(int x, int z) {
			long dx = x < minX ? minX - x : x > maxX ? x - maxX : 0, dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0;
			return dx * dx + dz * dz;
		}

		public Box grow(int d) { return new Box(minX - d, minZ - d, maxX + d, maxZ + d); }

		public boolean meets(Box o) { return minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ; }
	}

	/**
	 * The piece boxes of a village (x, z only); {@code streets} are the path pieces, a subset of {@code pieces}; {@code paths} are the
	 * cells (x, z) of the street pieces' own path blocks, from their templates (empty when unknown: the spur then aims at a street box).
	 */
	public record Village(List<Box> pieces, List<Box> streets, List<int[]> paths) {
		public Village(List<Box> pieces, List<Box> streets) { this(pieces, streets, List.of()); }
	}

	/**
	 * What to change in the window's road columns: drop the columns at {@code removed} (packed {@link #key}), add {@code added}, and lay
	 * {@code spur} cells (x, z, y): the whole band, 3 wide, 5 at the road; y is the graded surface the cell is cut or filled to, or
	 * {@link Integer#MIN_VALUE} where no heights were given (lay it on the ground). {@code ends} are the centre-line end cells, one per
	 * spur, {@code meets} the village path cell each end touches (a 4-neighbour of it; null when the village's paths are unknown and the
	 * spur ends on a street box instead), {@code lines} each spur's centre line from the road to its end, and {@code steep} the spurs
	 * that could not be graded to steps of 1 (the rise is more than the length on every candidate line).
	 */
	public record Result(Set<Long> removed, List<Col> added, List<int[]> spur, List<int[]> ends, List<int[]> meets, List<List<int[]>> lines, int steep) {
		public static final Result NONE = new Result(Set.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0);

		public boolean isEmpty() { return removed.isEmpty() && added.isEmpty() && spur.isEmpty(); }
	}

	/**
	 * Ground heights for grading a spur: y of the top ground block at (x, z) as the world will have it when the village's street is placed
	 * there (so at a path cell, the path block's y). Must not depend on chunk order; called only for the spur's centre line and met cells.
	 */
	public interface Heights {
		int top(int x, int z);
	}

	public static long key(int x, int z) { return ((long) z << 32) ^ (x & 0xffffffffL); }

	/** The area the village may change: its boxes grown by margin and search room. The caller supplies the road columns of every chunk it meets. */
	public static Box influence(Village v) {
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (Box b : v.pieces()) { x0 = Math.min(x0, b.minX()); z0 = Math.min(z0, b.minZ()); x1 = Math.max(x1, b.maxX()); z1 = Math.max(z1, b.maxZ()); }
		return new Box(x0 - MARGIN - SHOULDER - PAD, z0 - MARGIN - SHOULDER - PAD, x1 + MARGIN + SHOULDER + PAD, z1 + MARGIN + SHOULDER + PAD);
	}

	/** Grid over the influence box (plus the spur reach, which the caller's columns need not cover: a spur starts at a column). */
	private static final class Grid {
		final int x0, z0, w, h;
		Grid(Box b) { x0 = b.minX(); z0 = b.minZ(); w = b.maxX() - b.minX() + 1; h = b.maxZ() - b.minZ() + 1; }
		boolean in(int x, int z) { return x >= x0 && z >= z0 && x < x0 + w && z < z0 + h; }
		int idx(int x, int z) { return (z - z0) * w + (x - x0); }
		int x(int i) { return x0 + i % w; }
		int z(int i) { return z0 + i / w; }
	}

	private static final int[][] N8 = {{0, -1}, {-1, 0}, {1, 0}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
	private static final int[][] N4 = {{0, -1}, {-1, 0}, {1, 0}, {0, 1}};

	public static Result join(Village v, List<Col> cols) { return join(v, cols, null); }

	/** As {@link #join(Village, List)}, grading each spur between the road's deck and the met path cell when {@code heights} is given. */
	public static Result join(Village v, List<Col> cols, Heights heights) {
		if (v.pieces().isEmpty() || cols.isEmpty()) return Result.NONE;
		Box inf = influence(v);
		Grid g = new Grid(inf);
		boolean[] blocked = new boolean[g.w * g.h], cut = new boolean[g.w * g.h], inBox = new boolean[g.w * g.h];
		for (Box b : v.pieces()) {
			fill(g, blocked, b.grow(MARGIN));
			fill(g, cut, b.grow(MARGIN + SHOULDER));
			fill(g, inBox, b);
		}
		TreeMapLike road = new TreeMapLike();
		for (Col c : cols) if (g.in(c.x(), c.z())) road.put(c);
		Set<Long> removed = new TreeSet<>();
		List<Col> added = new ArrayList<>();

		// ---- the bend
		List<Col> hit = new ArrayList<>();
		for (Col c : road.sorted()) if (cut[g.idx(c.x(), c.z())]) hit.add(c);
		Set<Long> hitKeys = new TreeSet<>();
		for (Col c : hit) hitKeys.add(key(c.x(), c.z()));
		Set<Long> seen = new TreeSet<>();
		for (Col seed : hit) {
			if (!seen.add(key(seed.x(), seed.z()))) continue;
			List<Col> run = new ArrayList<>(List.of(seed));
			for (int i = 0; i < run.size(); i++) { // columns within 2 of each other
				Col c = run.get(i);
				for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
					long k = key(c.x() + dx, c.z() + dz);
					if (hitKeys.contains(k) && seen.add(k)) run.add(road.get(k));
				}
			}
			// the surviving columns touching the run, in clusters
			Set<Long> touch = new TreeSet<>();
			for (Col c : run) for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) {
				long k = key(c.x() + dx, c.z() + dz);
				Col t = road.get(k);
				if (t != null && !hitKeys.contains(k)) touch.add(k);
			}
			List<List<Col>> clusters = clusters(road, touch);
			if (clusters.size() != 2) continue;
			Col a = medoid(clusters.get(0)), b = medoid(clusters.get(1));
			int width = 1;
			for (List<Col> cl : clusters) for (Col c : cl) width = Math.max(width, c.width());
			List<int[]> path = shortest(g, blocked, a.x(), a.z(), b.x(), b.z());
			if (path == null) continue;
			for (Col c : run) removed.add(key(c.x(), c.z()));
			int n = path.size() - 1, r = width / 2;
			Map<Long, Col> put = new TreeMap<>();
			List<Col> order = new ArrayList<>();
			for (int i = 0; i <= n; i++) {
				int[] p = path.get(i);
				int y = n == 0 ? a.y() : a.y() + (int) Math.floorDiv((long) (b.y() - a.y()) * i * 2 + n, 2L * n);
				for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
					int x = p[0] + dx, z = p[1] + dz;
					long k = key(x, z);
					Col old = road.get(k);
					if ((old != null && !removed.contains(k)) || put.containsKey(k)) continue;
					Col c = new Col(x, z, y, false, width, a.road(), a.deck());
					put.put(k, c);
					order.add(c);
				}
			}
			added.addAll(order);
		}
		for (long k : removed) road.remove(k);
		for (Col c : added) road.put(c);

		// ---- the spur
		List<int[]> spur = new ArrayList<>(), ends = new ArrayList<>(), meets = new ArrayList<>();
		if (!v.paths().isEmpty()) {
			List<List<int[]>> lines = new ArrayList<>();
			int steep = pathSpurs(g, v, road, heights, spur, ends, meets, lines);
			Result res = new Result(removed, added, spur, ends, meets, lines, steep);
			return res.isEmpty() ? Result.NONE : res;
		}
		Set<Long> spurSeen = new TreeSet<>();
		List<Box> targets = v.streets().isEmpty() ? v.pieces() : v.streets();
		List<List<Col>> comps = clusters(road, road.keys(), 1);
		comps.sort((p, q) -> bestCmp(p, targets, q));
		int made = 0;
		for (List<Col> comp : comps) {
			if (made >= MAX_SPURS) break;
			long best = Long.MAX_VALUE;
			Col from = null;
			Box to = null;
			for (Col c : comp) {
				for (Box b : targets) {
					long d = b.dist2(c.x(), c.z());
					if (d < best) { best = d; from = c; to = b; }
				}
			}
			if (from == null || best > (long) SPUR_MAX * SPUR_MAX) {
				if (targets == v.streets()) { // no street in reach: the nearest piece of any kind
					best = Long.MAX_VALUE;
					for (Col c : comp) for (Box b : v.pieces()) {
						long d = b.dist2(c.x(), c.z());
						if (d < best) { best = d; from = c; to = b; }
					}
				}
				if (from == null || best > (long) SPUR_MAX * SPUR_MAX) continue;
			}
			boolean street = v.streets().contains(to);
			List<int[]> cells = spurCells(g, inBox, from, to, street);
			if (cells == null || cells.isEmpty()) continue;
			for (int i = 0; i < cells.size(); i++) { // the band: 3 wide like a village street, 5 at the road (DS-52)
				int half = i < MOUTH ? SPUR_HALF + 1 : SPUR_HALF;
				int[] c = cells.get(i);
				for (int dz = -half; dz <= half; dz++) for (int dx = -half; dx <= half; dx++) {
					int x = c[0] + dx, z = c[1] + dz;
					if (!g.in(x, z) || inBox[g.idx(x, z)] && !(street && to.contains(x, z))) continue;
					long k = key(x, z);
					if (road.get(k) == null && spurSeen.add(k)) spur.add(new int[] {x, z, Integer.MIN_VALUE});
				}
			}
			ends.add(cells.get(cells.size() - 1));
			meets.add(null);
			made++;
		}
		Result res = new Result(removed, added, spur, ends, meets, List.of(), 0);
		return res.isEmpty() ? Result.NONE : res;
	}

	/** A second spur must meet the village this far (and more) from where an earlier one met it, so two spurs never run into the same street side by side. */
	public static final int SPUR_APART = 30;

	/**
	 * Spurs to the village's own path blocks ({@link Village#paths}). For each road component (nearest first, at most {@link #MAX_SPURS}),
	 * the road column and path cell nearest each other within {@link #SPUR_MAX}: a straight 4-connected line from the column toward the
	 * cell that stops on the last cell before a path cell, so the spur abuts the street and never overwrites it; when a house or other
	 * non-street piece is in the way, the cheapest 4-path round them to any cell beside a path cell. Street boxes are passable (outside
	 * their path they are the village's grass margin). The band skips path cells, non-street pieces and the road.
	 *
	 * <p><b>Grade</b> (DS-31, VILLROAD r8), when {@code heights} is given: the spur runs from the road's deck (the planner's, at the road
	 * column it leaves) to the met path cell's height, each centre cell following the ground where it can and changing by at most 1 from
	 * the one before, within reach of both ends; band cells take their centre cell's height and the mod cuts or fills each column to it.
	 * When the rise is more than the line is long, other road columns of the component are tried, nearest the path cell first, so the spur
	 * leaves the road further along (longer, and where the road is nearer the street's height). Returns the spurs that still could not be
	 * graded (laid on the ground instead).
	 */
	private static int pathSpurs(Grid g, Village v, TreeMapLike road, Heights heights, List<int[]> spur, List<int[]> ends, List<int[]> meets, List<List<int[]>> lines) {
		boolean[] hard = new boolean[g.w * g.h], path = new boolean[g.w * g.h];
		for (Box b : v.pieces()) if (!v.streets().contains(b)) fill(g, hard, b);
		List<int[]> paths = new ArrayList<>();
		for (int[] p : v.paths()) {
			if (!g.in(p[0], p[1]) || path[g.idx(p[0], p[1])]) continue;
			path[g.idx(p[0], p[1])] = true;
			paths.add(p);
		}
		paths.sort((p, q) -> p[1] != q[1] ? Integer.compare(p[1], q[1]) : Integer.compare(p[0], q[0]));
		for (int[] p : paths) hard[g.idx(p[0], p[1])] = false; // a path cell under a decoration box is still the street
		Map<Long, Integer> tops = new TreeMap<>();
		Heights top = heights == null ? null : (x, z) -> tops.computeIfAbsent(key(x, z), k -> heights.top(x, z));
		List<List<Col>> comps = clusters(road, road.keys(), 1);
		List<long[]> order = new ArrayList<>(); // {dist2, comp index, column index, path index}
		for (int ci = 0; ci < comps.size(); ci++) {
			List<Col> comp = comps.get(ci);
			long best = Long.MAX_VALUE;
			int bc = -1, bp = -1;
			for (int i = 0; i < comp.size(); i++) {
				Col c = comp.get(i);
				for (int j = 0; j < paths.size(); j++) {
					long dx = paths.get(j)[0] - c.x(), dz = paths.get(j)[1] - c.z(), d = dx * dx + dz * dz;
					if (d < best) { best = d; bc = i; bp = j; }
				}
			}
			if (bc >= 0 && best <= (long) SPUR_MAX * SPUR_MAX) order.add(new long[] {best, ci, bc, bp});
		}
		order.sort((p, q) -> p[0] != q[0] ? Long.compare(p[0], q[0]) : Long.compare(p[1], q[1]));
		Set<Long> spurSeen = new TreeSet<>();
		int steep = 0;
		for (long[] o : order) {
			if (ends.size() >= MAX_SPURS) break;
			List<Col> comp = comps.get((int) o[1]);
			Col first = comp.get((int) o[2]);
			int[] goal = paths.get((int) o[3]);
			Line best = line(g, hard, path, road, first, goal, top, true);
			if (best != null && top != null && !best.gradable()) { // too steep for its length: leave the road further along
				List<Col> cand = new ArrayList<>(comp);
				cand.sort((p, q) -> {
					long a = d2(p, goal), b = d2(q, goal);
					return a != b ? Long.compare(a, b) : p.z() != q.z() ? Integer.compare(p.z(), q.z()) : Integer.compare(p.x(), q.x());
				});
				int tried = 0;
				for (Col c : cand) {
					if (d2(c, goal) > (long) SPUR_MAX * SPUR_MAX || ++tried > 400) break;
					if (c == first) continue;
					Line l = line(g, hard, path, road, c, goal, top, false);
					if (l != null && l.gradable()) { best = l; break; }
				}
			}
			if (best == null) continue;
			boolean near = false;
			for (int[] m : meets) { long dx = m[0] - best.meet[0], dz = m[1] - best.meet[1]; if (dx * dx + dz * dz < (long) SPUR_APART * SPUR_APART) near = true; }
			if (near) continue;
			List<int[]> cells = best.cells;
			List<int[]> mine = new ArrayList<>();
			for (int i = 0; i < cells.size(); i++) { // the band: 3 wide like a village street, 5 at the road (DS-52)
				int half = i < MOUTH ? SPUR_HALF + 1 : SPUR_HALF;
				int[] c = cells.get(i);
				for (int dz = -half; dz <= half; dz++) for (int dx = -half; dx <= half; dx++) {
					int x = c[0] + dx, z = c[1] + dz;
					if (!g.in(x, z) || hard[g.idx(x, z)] || path[g.idx(x, z)]) continue;
					long k = key(x, z);
					if (road.get(k) == null && spurSeen.add(k)) mine.add(new int[] {x, z});
				}
			}
			Map<Long, Integer> y = new TreeMap<>();
			if (top != null) {
				if (!grade(best, mine, road, top, y)) steep++;
			}
			for (int[] c : mine) spur.add(new int[] {c[0], c[1], y.getOrDefault(key(c[0], c[1]), Integer.MIN_VALUE)});
			ends.add(best.end());
			meets.add(best.meet);
			List<int[]> line = new ArrayList<>();
			for (int[] c : cells) line.add(new int[] {c[0], c[1], y.getOrDefault(key(c[0], c[1]), Integer.MIN_VALUE)});
			lines.add(line);
		}
		return steep;
	}

	private static long d2(Col c, int[] p) { long dx = p[0] - c.x(), dz = p[1] - c.z(); return dx * dx + dz * dz; }

	/** A spur's centre line (from beside the road to beside the path), the path cell it meets, the road's surface where it leaves (deck y - 1), and the met path cell's height (MIN_VALUE without heights). */
	private record Line(List<int[]> cells, int[] meet, int from, int to, Col leave) {
		int[] end() { return cells.get(cells.size() - 1); }

		/** Steps of at most 1 can join the two ends (road at -1, path at the end station + 1), with a cell to spare for the mouth. */
		boolean gradable() { return to == Integer.MIN_VALUE || Math.abs(from - to) <= cells.size(); } // stations along the band can be 1 fewer than the cells (the mouth)
	}

	private static Line line(Grid g, boolean[] hard, boolean[] path, TreeMapLike road, Col from, int[] goal, Heights top, boolean round) {
		List<int[]> cells = lineToPath(g, hard, path, from, goal);
		if (cells == null && round) cells = roundToPath(g, hard, path, from);
		if (cells == null) return null;
		Col leave = from;
		int last = -1; // the spur starts beside the road's last column on its way
		for (int i = 0; i < cells.size(); i++) {
			Col c = road.get(key(cells.get(i)[0], cells.get(i)[1]));
			if (c != null) { last = i; leave = c; }
		}
		cells = new ArrayList<>(cells.subList(last + 1, cells.size()));
		if (cells.isEmpty()) return null; // the road already touches the street
		int[] end = cells.get(cells.size() - 1), meet = null;
		for (int[] d : N4) if (g.in(end[0] + d[0], end[1] + d[1]) && path[g.idx(end[0] + d[0], end[1] + d[1])]) { meet = new int[] {end[0] + d[0], end[1] + d[1]}; break; }
		if (meet == null) return null;
		return new Line(cells, meet, leave.y() - 1, top == null ? Integer.MIN_VALUE : top.top(meet[0], meet[1]), leave);
	}

	/**
	 * Grades one spur band (DS-31). Each band cell's station is its 4-connected distance over the band from the cells beside the road column it leaves, so
	 * 4-neighbours differ by at most 1 station and a profile over stations that steps at most 1 gives steps of at most 1 everywhere,
	 * also where a staircase line turns. The profile runs from the road's surface (station -1) to the met path cell's height (the end
	 * cell's station + 1), following the ground under the centre line where it can; stations past the end keep the end's height.
	 * Fills {@code out} (cell key to y); false when the rise is more than the stations allow (then the cells follow the ground).
	 */
	static boolean grade(Line l, List<int[]> band, TreeMapLike road, Heights top, Map<Long, Integer> out) {
		Map<Long, Integer> st = new TreeMap<>();
		java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
		Set<Long> in = new TreeSet<>();
		for (int[] c : band) in.add(key(c[0], c[1]));
		for (int[] c : band) {
			for (int[] d : N4) if (c[0] + d[0] == l.leave().x() && c[1] + d[1] == l.leave().z()) { long k = key(c[0], c[1]); if (st.putIfAbsent(k, 0) == null) q.add(k); break; }
		}
		if (q.isEmpty()) { long k = key(l.cells().get(0)[0], l.cells().get(0)[1]); st.put(k, 0); q.add(k); }
		int maxSt = 0;
		while (!q.isEmpty()) {
			long k = q.poll();
			int x = (int) k, z = (int) (k >> 32), s = st.get(k);
			maxSt = Math.max(maxSt, s);
			for (int[] d : N4) {
				long nk = key(x + d[0], z + d[1]);
				if (in.contains(nk) && !st.containsKey(nk)) { st.put(nk, s + 1); q.add(nk); }
			}
		}
		int[] end = l.end();
		Integer endSt = st.get(key(end[0], end[1]));
		int d = endSt == null ? l.cells().size() - 1 : endSt, from = l.from(), to = l.to();
		int[] ground = new int[maxSt + 1];
		java.util.Arrays.fill(ground, Integer.MIN_VALUE);
		for (int[] c : l.cells()) { Integer s = st.get(key(c[0], c[1])); if (s != null && ground[s] == Integer.MIN_VALUE) ground[s] = top.top(c[0], c[1]); }
		boolean ok = Math.abs(from - to) <= d + 2;
		int[] f = new int[maxSt + 1];
		int prev = from;
		for (int t = 0; t <= maxSt; t++) {
			int gy = ground[t] != Integer.MIN_VALUE ? ground[t] : prev;
			if (t > d) f[t] = f[d];
			else if (ok) {
				int lo = Math.max(Math.max(from - (t + 1), to - (d + 1 - t)), prev - 1), hi = Math.min(Math.min(from + (t + 1), to + (d + 1 - t)), prev + 1);
				f[t] = Math.max(lo, Math.min(hi, gy));
			} else f[t] = gy;
			prev = f[t];
		}
		for (int[] c : band) {
			long k = key(c[0], c[1]);
			Integer s = st.get(k);
			out.put(k, s == null ? top.top(c[0], c[1]) : f[s]);
		}
		return ok;
	}

	/** The straight 4-connected line from a road column toward a path cell, up to the cell before the first path cell it meets; null when a piece is in the way. */
	private static List<int[]> lineToPath(Grid g, boolean[] hard, boolean[] path, Col from, int[] goal) {
		List<int[]> line = new ArrayList<>();
		int x = from.x(), z = from.z();
		int nx = Math.abs(goal[0] - x), nz = Math.abs(goal[1] - z), sx = Integer.compare(goal[0], x), sz = Integer.compare(goal[1], z);
		int ix = 0, iz = 0;
		while (ix < nx || iz < nz) { // the axis that is behind its share of the way steps
			if (iz >= nz || ix < nx && (1 + 2L * ix) * nz < (1 + 2L * iz) * nx) { x += sx; ix++; } else { z += sz; iz++; }
			if (!g.in(x, z)) return null;
			int j = g.idx(x, z);
			if (path[j]) return line;
			if (hard[j]) return null;
			line.add(new int[] {x, z});
		}
		return null;
	}

	/** The cheapest 4-path from a road column to any cell beside a path cell, round the non-street pieces; null when there is none. */
	private static List<int[]> roundToPath(Grid g, boolean[] hard, boolean[] path, Col from) {
		int n = g.w * g.h;
		int[] cost = new int[n], prev = new int[n];
		Arrays.fill(cost, Integer.MAX_VALUE);
		int s = g.idx(from.x(), from.z());
		PriorityQueue<long[]> q = new PriorityQueue<>((p, o) -> p[0] != o[0] ? Long.compare(p[0], o[0]) : Long.compare(p[1], o[1]));
		cost[s] = 0;
		prev[s] = -1;
		q.add(new long[] {0, s});
		int goal = -1;
		while (!q.isEmpty()) {
			long[] cur = q.poll();
			int i = (int) cur[1];
			if (cur[0] > cost[i]) continue;
			int cx = g.x(i), cz = g.z(i);
			if (i != s) {
				boolean beside = false;
				for (int[] d : N4) if (g.in(cx + d[0], cz + d[1]) && path[g.idx(cx + d[0], cz + d[1])]) beside = true;
				if (beside) { goal = i; break; }
			}
			if (cur[0] > 10L * SPUR_MAX * 2) continue; // no detour longer than twice the reach
			for (int[] d : N4) {
				int mx = cx + d[0], mz = cz + d[1];
				if (!g.in(mx, mz)) continue;
				int j = g.idx(mx, mz);
				if (hard[j] || path[j]) continue;
				int c = cost[i] + 10;
				if (c < cost[j]) { cost[j] = c; prev[j] = i; q.add(new long[] {c, j}); }
			}
		}
		if (goal < 0) return null;
		List<int[]> out = new ArrayList<>();
		for (int i = goal; i != s; i = prev[i]) out.add(new int[] {g.x(i), g.z(i)});
		java.util.Collections.reverse(out);
		return out;
	}

	private static int bestCmp(List<Col> p, List<Box> targets, List<Col> q) {
		long a = nearest(p, targets), b = nearest(q, targets);
		if (a != b) return Long.compare(a, b);
		Col x = p.get(0), y = q.get(0);
		return x.z() != y.z() ? Integer.compare(x.z(), y.z()) : Integer.compare(x.x(), y.x());
	}

	private static long nearest(List<Col> comp, List<Box> targets) {
		long best = Long.MAX_VALUE;
		for (Col c : comp) for (Box b : targets) best = Math.min(best, b.dist2(c.x(), c.z()));
		return best;
	}

	private static void fill(Grid g, boolean[] a, Box b) {
		for (int z = Math.max(b.minZ(), g.z0); z <= Math.min(b.maxZ(), g.z0 + g.h - 1); z++)
			for (int x = Math.max(b.minX(), g.x0); x <= Math.min(b.maxX(), g.x0 + g.w - 1); x++) a[g.idx(x, z)] = true;
	}

	/** Columns by key, sorted by (z, x) on demand. */
	private static final class TreeMapLike {
		private final java.util.TreeMap<Long, Col> m = new java.util.TreeMap<>();
		// keyed by (z, x) order, not by the packed key, so iteration is (z, x)
		private final Map<Long, Col> byKey = new TreeMap<>();

		void put(Col c) { byKey.put(key(c.x(), c.z()), c); m.put(((long) c.z() << 32) + (c.x() + (1L << 31)), c); }

		Col get(long k) { return byKey.get(k); }

		void remove(long k) {
			Col c = byKey.remove(k);
			if (c != null) m.remove(((long) c.z() << 32) + (c.x() + (1L << 31)));
		}

		List<Col> sorted() { return new ArrayList<>(m.values()); }

		Set<Long> keys() { return new TreeSet<>(byKey.keySet()); }
	}

	private static List<List<Col>> clusters(TreeMapLike road, Set<Long> keys) { return clusters(road, keys, 2); }

	/** 8-connected (reach {@code reach}) clusters of the columns at {@code keys}, each sorted by (z, x), the clusters by their first column. */
	private static List<List<Col>> clusters(TreeMapLike road, Set<Long> keys, int reach) {
		List<Col> all = new ArrayList<>();
		for (long k : keys) all.add(road.get(k));
		all.sort((p, q) -> p.z() != q.z() ? Integer.compare(p.z(), q.z()) : Integer.compare(p.x(), q.x()));
		Set<Long> left = new TreeSet<>(keys);
		List<List<Col>> out = new ArrayList<>();
		for (Col s : all) {
			if (!left.remove(key(s.x(), s.z()))) continue;
			List<Col> cl = new ArrayList<>(List.of(s));
			for (int i = 0; i < cl.size(); i++) {
				Col c = cl.get(i);
				for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) {
					long k = key(c.x() + dx, c.z() + dz);
					if (left.remove(k)) cl.add(road.get(k));
				}
			}
			cl.sort((p, q) -> p.z() != q.z() ? Integer.compare(p.z(), q.z()) : Integer.compare(p.x(), q.x()));
			out.add(cl);
		}
		return out;
	}

	/** The column with the least summed squared distance to the others; ties by (z, x). */
	private static Col medoid(List<Col> cl) {
		Col best = null;
		long bestSum = Long.MAX_VALUE;
		for (Col c : cl) {
			long s = 0;
			for (Col o : cl) { long dx = c.x() - o.x(), dz = c.z() - o.z(); s += dx * dx + dz * dz; }
			if (s < bestSum) { bestSum = s; best = c; }
		}
		return best;
	}

	/** Cheapest 8-connected path over unblocked cells (corners are not cut), both ends included; null when there is none. */
	private static List<int[]> shortest(Grid g, boolean[] blocked, int sx, int sz, int tx, int tz) {
		int n = g.w * g.h;
		int[] cost = new int[n], from = new int[n];
		Arrays.fill(cost, Integer.MAX_VALUE);
		int s = g.idx(sx, sz), t = g.idx(tx, tz);
		PriorityQueue<long[]> q = new PriorityQueue<>((p, o) -> p[0] != o[0] ? Long.compare(p[0], o[0]) : Long.compare(p[1], o[1]));
		cost[s] = 0;
		from[s] = -1;
		q.add(new long[] {0, s});
		boolean found = false;
		while (!q.isEmpty()) {
			long[] cur = q.poll();
			int i = (int) cur[1];
			if (cur[0] > cost[i]) continue;
			if (i == t) { found = true; break; }
			int x = g.x(i), z = g.z(i);
			for (int[] d : N8) {
				int nx = x + d[0], nz = z + d[1];
				if (!g.in(nx, nz) || blocked[g.idx(nx, nz)]) continue;
				boolean diag = d[0] != 0 && d[1] != 0;
				if (diag && (blocked[g.idx(x + d[0], z)] || blocked[g.idx(x, z + d[1])])) continue;
				int j = g.idx(nx, nz), c = cost[i] + (diag ? 14 : 10);
				if (c < cost[j]) { cost[j] = c; from[j] = i; q.add(new long[] {c, j}); }
			}
		}
		if (!found) return null;
		List<int[]> path = new ArrayList<>();
		for (int i = t; i != -1; i = from[i]) path.add(new int[] {g.x(i), g.z(i)});
		java.util.Collections.reverse(path);
		return path;
	}

	/**
	 * The centre line of a street piece (the middle of its short side) where a spur meets it: at the box end nearest the road when the
	 * road lies beyond an end (a street end, met end-on), else on the middle line level with the road column (met side-on).
	 */
	static int[] streetTarget(Box to, Col from) {
		int w = to.maxX() - to.minX() + 1, h = to.maxZ() - to.minZ() + 1;
		if (h >= w) { // the street runs along z
			int cx = (to.minX() + to.maxX()) / 2;
			return new int[] {cx, from.z() < to.minZ() ? to.minZ() : from.z() > to.maxZ() ? to.maxZ() : from.z()};
		}
		int cz = (to.minZ() + to.maxZ()) / 2;
		return new int[] {from.x() < to.minX() ? to.minX() : from.x() > to.maxX() ? to.maxX() : from.x(), cz};
	}

	/**
	 * Centre-line cells of a spur from a road column to a box, 4-connected, not including the start. A street target ends ON the
	 * street piece's centre line ({@link #streetTarget}); any other piece is met on its nearest cell and the line stops beside it. A
	 * straight line when clear, else the cheapest 4-path round the other pieces.
	 */
	private static List<int[]> spurCells(Grid g, boolean[] inBox, Col from, Box to, boolean street) {
		int tx, tz;
		if (street) { int[] t = streetTarget(to, from); tx = t[0]; tz = t[1]; }
		else { tx = Math.max(to.minX(), Math.min(to.maxX(), from.x())); tz = Math.max(to.minZ(), Math.min(to.maxZ(), from.z())); }
		List<int[]> line = new ArrayList<>();
		int x = from.x(), z = from.z();
		int nx = Math.abs(tx - x), nz = Math.abs(tz - z), sx = Integer.compare(tx, x), sz = Integer.compare(tz, z);
		int ix = 0, iz = 0;
		boolean clear = true;
		while (ix < nx || iz < nz) { // a 4-connected line: the axis that is behind its share of the way steps
			if (iz >= nz || ix < nx && (1 + 2L * ix) * nz < (1 + 2L * iz) * nx) { x += sx; ix++; } else { z += sz; iz++; }
			if (!g.in(x, z)) { clear = false; break; }
			if (inBox[g.idx(x, z)] && !(street && to.contains(x, z))) {
				if (street || !to.contains(x, z)) clear = false;
				break;
			}
			line.add(new int[] {x, z});
		}
		if (clear && !line.isEmpty()) return line;
		if (clear) return null;
		// round the other pieces
		int n = g.w * g.h;
		int[] cost = new int[n], prev = new int[n];
		Arrays.fill(cost, Integer.MAX_VALUE);
		int s = g.idx(from.x(), from.z());
		PriorityQueue<long[]> q = new PriorityQueue<>((p, o) -> p[0] != o[0] ? Long.compare(p[0], o[0]) : Long.compare(p[1], o[1]));
		cost[s] = 0;
		prev[s] = -1;
		q.add(new long[] {0, s});
		int goal = -1;
		Box near = to.grow(1);
		while (!q.isEmpty()) {
			long[] cur = q.poll();
			int i = (int) cur[1];
			if (cur[0] > cost[i]) continue;
			int cx = g.x(i), cz = g.z(i);
			if (i != s && (street ? cx == tx && cz == tz : near.contains(cx, cz))) { goal = i; break; }
			for (int[] d : N4) {
				int mx = cx + d[0], mz = cz + d[1];
				if (!g.in(mx, mz)) continue;
				int j = g.idx(mx, mz);
				if (inBox[j] && !(street && to.contains(mx, mz))) continue; // other pieces; a non-street target is stopped beside, not entered
				int c = cost[i] + 10;
				if (c < cost[j]) { cost[j] = c; prev[j] = i; q.add(new long[] {c, j}); }
			}
		}
		if (goal < 0) return null;
		List<int[]> path = new ArrayList<>();
		for (int i = goal; i != s; i = prev[i]) path.add(new int[] {g.x(i), g.z(i)});
		java.util.Collections.reverse(path);
		return path;
	}
}
