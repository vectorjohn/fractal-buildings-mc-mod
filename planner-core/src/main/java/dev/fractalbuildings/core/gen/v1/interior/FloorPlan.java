package dev.fractalbuildings.core.gen.v1.interior;

import dev.fractalbuildings.core.gen.v1.style.InteriorStyle;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The floor plan of one mass on one band of identical storeys, in cells relative to the mass's
 * interior (its origin is the first interior cell). Pure geometry: the interior [0, w) x [0, d) is
 * cut by recursive guillotine splits into rectangular rooms ("leaves") separated by one-cell
 * partitions; each partition has doors so that the rooms form a connected graph (a random spanning
 * tree, plus some loops). Cut lines only pass through cells that are not {@code reserved}: window
 * inside cells (so partitions meet outer walls at piers), the entrance's inside cell, the cells
 * either side of a strip door, and the cells around a stair core. The rest of the interior
 * pass (room ids, boxes, hooks) is in {@link InteriorPass}.
 *
 * @param leafAt room index of each cell, -1 for a partition cell
 * @param corridor index of the corridor room, or -1
 */
record FloorPlan(int w, int d, List<Leaf> leaves, List<Cut> cuts, List<Door> doors, int[][] leafAt, int corridor) {
	/** A room rectangle. */
	record Leaf(int x, int z, int w, int d) {
		int area() { return w * d; }

		boolean contains(int cx, int cz) { return cx >= x && cx < x + w && cz >= z && cz < z + d; }
	}

	/** A partition line: {@code constX}: cells (c, lo..lo+len-1); else cells (lo..lo+len-1, c). */
	record Cut(boolean constX, int c, int lo, int len) {}

	/**
	 * A door through the partition cell (x, z), from room {@code from} to {@code to}, walking {@code facing}.
	 * An {@code arch} is a doorless opening {@code span} cells wide along the partition, starting at (x, z)
	 * and running +z for a partition along z (facing east or west), else +x.
	 */
	record Door(int x, int z, int from, int to, Facing facing, int span, boolean arch) {
		Door(int x, int z, int from, int to, Facing facing) { this(x, z, from, to, facing, 1, false); }

		boolean alongZ() { return facing == Facing.EAST || facing == Facing.WEST; }

		int cellX(int k) { return alongZ() ? x : x + k; }

		int cellZ(int k) { return alongZ() ? z + k : z; }
	}

	/** One room, no partitions. */
	static FloorPlan single(int w, int d) {
		int[][] at = new int[w][d];
		return new FloorPlan(w, d, List.of(new Leaf(0, 0, w, d)), List.of(), List.of(), at, -1);
	}

	/**
	 * Plans a floor. {@code reserved} and {@code core} are indexed [x][z]; core cells are never door
	 * sides. Falls back to a few other draws, then to one room, if the doors can't connect the rooms.
	 */
	static FloorPlan plan(int w, int d, boolean[][] reserved, boolean[][] core, List<int[]> entry, InteriorStyle st, long seed) {
		if (st.roomArea() <= 0) return single(w, d);
		FloorPlan best = null;
		int bestDeep = Integer.MAX_VALUE;
		for (int attempt = 0; attempt < ATTEMPTS && bestDeep > 0; attempt++) {
			Planner pl = new Planner(w, d, reserved, core, entry, st, Hash64.hash(seed, "attempt", attempt), SCALE[attempt]);
			FloorPlan p = pl.plan();
			if (p != null && pl.deep < bestDeep) {
				best = p;
				bestDeep = pl.deep;
			}
		}
		return best != null ? best : single(w, d);
	}

	/**
	 * The plan with some doors between {@code open} rooms made into archways (open plan), 1 to 3 cells
	 * wide where the partition allows: every cell of the span separates the same two rooms, is not next to
	 * a stair core and has partition either side of it (so a pier stays at each end), and no other door
	 * is within a cell. Rooms, cuts and the rest of the doors are unchanged, so reachability is too.
	 */
	FloorPlan withArches(java.util.function.Predicate<String> open, String[] uses, boolean[][] core, int percent, Draws dr) {
		List<Door> out = new ArrayList<>();
		for (Door dd : doors) {
			if (!open.test(uses[dd.from()]) || !open.test(uses[dd.to()]) || !dr.chance(percent, 100)) {
				out.add(dd);
				continue;
			}
			int want = 1 + dr.below(3);
			int lo = 0, hi = 0; // span offsets relative to the door cell, inclusive
			while (hi - lo + 1 < want) {
				boolean up = (hi - lo) % 2 == 0;
				int k = up ? hi + 1 : lo - 1;
				if (!archCell(dd, k, core, doors)) break;
				if (up) hi = k; else lo = k;
			}
			out.add(new Door(dd.cellX(lo), dd.cellZ(lo), dd.from(), dd.to(), dd.facing(), hi - lo + 1, true));
		}
		return new FloorPlan(w, d, leaves, cuts, List.copyOf(out), leafAt, corridor);
	}

	/** True if the partition cell {@code k} along from door {@code dd} can be part of an archway: see {@link #withArches}. */
	private boolean archCell(Door dd, int k, boolean[][] core, List<Door> all) {
		int x = dd.cellX(k), z = dd.cellZ(k);
		int beyond = k > 0 ? k + 1 : k - 1; // a pier of partition stays at the end of the span
		if (!partition(dd.cellX(beyond), dd.cellZ(beyond))) return false;
		int ax = dd.alongZ() ? x - 1 : x, az = dd.alongZ() ? z : z - 1, bx = dd.alongZ() ? x + 1 : x, bz = dd.alongZ() ? z : z + 1;
		if (!partition(x, z) || ax < 0 || az < 0 || bx >= w || bz >= d) return false;
		if (leafAt[ax][az] != dd.from() && leafAt[ax][az] != dd.to()) return false;
		if (leafAt[bx][bz] != dd.from() && leafAt[bx][bz] != dd.to()) return false;
		if (leafAt[ax][az] == leafAt[bx][bz] || core[ax][az] || core[bx][bz]) return false;
		for (Door o : all) {
			if (o == dd) continue;
			for (int i = 0; i < o.span(); i++) if (Math.abs(o.cellX(i) - x) <= 1 && Math.abs(o.cellZ(i) - z) <= 1) return false;
		}
		return true;
	}

	private boolean partition(int x, int z) { return x >= 0 && z >= 0 && x < w && z < d && leafAt[x][z] < 0; }

	/** Plans drawn per floor; the one with the fewest rooms three or more doors from the hub wins (circulation, 5.11). */
	static final int ATTEMPTS = 8;
	/** Room-size scale (percent) per attempt: when ordinary plans leave a chain of rooms, try fewer, bigger rooms. */
	private static final int[] SCALE = {100, 100, 140, 140, 200, 200, 300, 450};

	private static final class Planner {
		final int w, d;
		final boolean[][] reserved, core;
		final List<int[]> entry;
		final InteriorStyle st;
		final long seed;
		final List<Leaf> leaves = new ArrayList<>();
		final List<Cut> cuts = new ArrayList<>();
		int corridor = -1;
		Boolean sideConstX; // while splitting the sides of a corridor: the only cut direction allowed
		/** Rooms three or more doors from the hub in the plan built (0 for a walk-through plan, which keeps its chain on purpose). */
		int deep;

		final int scale;

		Planner(int w, int d, boolean[][] reserved, boolean[][] core, List<int[]> entry, InteriorStyle st, long seed, int scale) {
			this.scale = scale;
			this.w = w;
			this.d = d;
			this.reserved = reserved;
			this.core = core;
			this.entry = entry;
			this.st = st;
			this.seed = seed;
		}

		FloorPlan plan() {
			if (st.corridorAbove() > 0 && (long) w * d / st.roomArea() >= st.corridorAbove() && corridorSplit()) {
				// leaves/cuts filled by corridorSplit
			} else {
				leaves.clear();
				cuts.clear();
				corridor = -1;
				split(0, 0, w, d);
			}
			int[][] at = new int[w][d];
			for (int[] col : at) java.util.Arrays.fill(col, -1);
			for (int i = 0; i < leaves.size(); i++) {
				Leaf l = leaves.get(i);
				for (int x = l.x(); x < l.x() + l.w(); x++) for (int z = l.z(); z < l.z() + l.d(); z++) at[x][z] = i;
			}
			List<Door> doors = doors(at);
			return doors == null ? null : new FloorPlan(w, d, List.copyOf(leaves), List.copyOf(cuts), doors, at, corridor);
		}

		boolean lineFree(boolean constX, int c, int lo, int len) {
			for (int t = lo; t < lo + len; t++) if (constX ? reserved[c][t] : reserved[t][c]) return false;
			return true;
		}

		/** A 2- or 3-wide corridor along the long axis with a partition line on each side; false if no lane is free. */
		boolean corridorSplit() {
			boolean alongX = w >= d;
			int shortExt = alongX ? d : w, longExt = alongX ? w : d, rm = st.roomMin();
			if (shortExt < 2 * rm + 4) return false;
			Draws dr = new Draws(Hash64.hash(seed, "corridor"));
			int jitter = dr.below(2);
			int bestLs = -1, bestLw = 0, bestScore = Integer.MAX_VALUE;
			for (int lw = 2; lw <= 3; lw++)
				for (int ls = rm + 1; ls <= shortExt - rm - lw - 1; ls++) {
					if (!lineFree(!alongX, ls - 1, 0, longExt) || !lineFree(!alongX, ls + lw, 0, longExt)) continue;
					int score = Math.abs(2 * ls + lw - shortExt + 2 * jitter - 1) * 2 + (lw - 2);
					if (score < bestScore) {
						bestScore = score;
						bestLs = ls;
						bestLw = lw;
					}
				}
			if (bestLs < 0) return false;
			int ls = bestLs, lw = bestLw;
			cuts.add(new Cut(!alongX, ls - 1, 0, longExt));
			cuts.add(new Cut(!alongX, ls + lw, 0, longExt));
			sideConstX = alongX; // rooms beside a corridor are cut across it, so each one has a door onto it
			if (alongX) {
				split(0, 0, w, ls - 1);
				corridor = leaves.size();
				leaves.add(new Leaf(0, ls, w, lw));
				split(0, ls + lw + 1, w, d - ls - lw - 1);
			} else {
				split(0, 0, ls - 1, d);
				corridor = leaves.size();
				leaves.add(new Leaf(ls, 0, lw, d));
				split(ls + lw + 1, 0, w - ls - lw - 1, d);
			}
			sideConstX = null;
			return true;
		}

		void split(int rx, int rz, int rw, int rd) {
			Draws dr = new Draws(Hash64.hash(seed, "split", rx, rz, rw, rd));
			int target = Math.max(1, st.roomArea() * (70 + dr.below(61)) / 100 * scale / 100);
			if (rw * rd > target) {
				boolean preferX = sideConstX != null ? sideConstX : rw > rd || (rw == rd && dr.chance(1, 2));
				int pct = 35 + dr.below(31);
				for (int pass = 0; pass < (sideConstX != null ? 1 : 2); pass++) {
					boolean constX = (pass == 0) == preferX;
					int ext = constX ? rw : rd, lo = st.roomMin(), hi = ext - 1 - st.roomMin();
					if (hi < lo) continue;
					int want = lo + (hi - lo) * pct / 100, best = -1;
					for (int c = lo; c <= hi; c++) {
						boolean free = constX ? lineFree(true, rx + c, rz, rd) : lineFree(false, rz + c, rx, rw);
						if (free && (best < 0 || Math.abs(c - want) < Math.abs(best - want))) best = c;
					}
					if (best < 0) continue;
					if (constX) {
						cuts.add(new Cut(true, rx + best, rz, rd));
						split(rx, rz, best, rd);
						split(rx + best + 1, rz, rw - best - 1, rd);
					} else {
						cuts.add(new Cut(false, rz + best, rx, rw));
						split(rx, rz, rw, best);
						split(rx, rz + best + 1, rw, rd - best - 1);
					}
					return;
				}
			}
			leaves.add(new Leaf(rx, rz, rw, rd));
		}

		/** Door candidates per pair of rooms; a random spanning tree (corridor edges first) plus loops. Null if not connected. */
		List<Door> doors(int[][] at) {
			Map<Long, List<Door>> cand = new TreeMap<>();
			for (Cut cut : cuts) {
				for (int t = cut.lo(); t < cut.lo() + cut.len(); t++) {
					int ax = cut.constX() ? cut.c() - 1 : t, az = cut.constX() ? t : cut.c() - 1;
					int bx = cut.constX() ? cut.c() + 1 : t, bz = cut.constX() ? t : cut.c() + 1;
					if (ax < 0 || az < 0 || bx >= w || bz >= d) continue;
					int a = at[ax][az], b = at[bx][bz];
					if (a < 0 || b < 0 || a == b || core[ax][az] || core[bx][bz]) continue;
					int x = cut.constX() ? cut.c() : t, z = cut.constX() ? t : cut.c();
					cand.computeIfAbsent((long) Math.min(a, b) * 100000 + Math.max(a, b), k -> new ArrayList<>())
							.add(new Door(x, z, a, b, cut.constX() ? Facing.EAST : Facing.SOUTH));
				}
			}
			List<Long> keys = new ArrayList<>(cand.keySet());
			Draws dr = new Draws(Hash64.hash(seed, "doors"));
			for (int i = keys.size() - 1; i > 0; i--) java.util.Collections.swap(keys, i, dr.below(i + 1));
			if (corridor >= 0) { // stable partition: corridor edges first
				List<Long> first = new ArrayList<>(), rest = new ArrayList<>();
				for (long k : keys) (k / 100000 == corridor || k % 100000 == corridor ? first : rest).add(k);
				keys = first;
				keys.addAll(rest);
			}
			int[] parent = new int[leaves.size()];
			for (int i = 0; i < parent.length; i++) parent[i] = i;
			List<Door> out = new ArrayList<>();
			int joins = 0;
			// Circulation (5.11): most plans hang every room off a hub (the corridor, else the best-connected
			// room, preferring the entry room and the room with the stairs) by shortest paths, so a room is
			// reached through the hub rather than a chain of rooms. A seeded few keep the random tree: a
			// walk-through layout now and then.
			int hub = corridor;
			walkThrough = false;
			if (hub < 0 && leaves.size() > 2) {
				if (dr.chance(WALK_THROUGH_PERCENT, 100)) walkThrough = true;
				else hub = pickHub(keys, dr);
			}
			java.util.Set<Long> used = new java.util.TreeSet<>();
			if (hub >= 0) {
				int[] dist = new int[leaves.size()];
				java.util.Arrays.fill(dist, -1);
				dist[hub] = 0;
				List<Integer> order = new ArrayList<>(List.of(hub));
				List<Long> sorted = new ArrayList<>(cand.keySet());
				for (int h = 0; h < order.size(); h++)
					for (long k : sorted) {
						int a = (int) (k / 100000), b = (int) (k % 100000);
						int o = a == order.get(h) ? b : b == order.get(h) ? a : -1;
						if (o >= 0 && dist[o] < 0) {
							dist[o] = dist[order.get(h)] + 1;
							order.add(o);
						}
					}
				for (int r : order) {
					if (r == hub) continue;
					List<Long> up = new ArrayList<>();
					for (long k : sorted) {
						int a = (int) (k / 100000), b = (int) (k % 100000);
						if ((a == r && dist[b] == dist[r] - 1) || (b == r && dist[a] == dist[r] - 1)) up.add(k);
					}
					long k = up.get(dr.below(up.size()));
					used.add(k);
					parent[find(parent, (int) (k / 100000))] = find(parent, (int) (k % 100000));
					joins++;
					out.add(pickDoor(cand.get(k), dr));
				}
			}
			for (long k : keys) {
				if (used.contains(k)) continue;
				int a = find(parent, (int) (k / 100000)), b = find(parent, (int) (k % 100000));
				boolean tree = a != b;
				if (tree) {
					parent[a] = b;
					joins++;
				} else if (!dr.chance(st.loopPercent(), 100)) continue;
				out.add(pickDoor(cand.get(k), dr));
			}
			if (joins != leaves.size() - 1) return null;
			deep = walkThrough ? 0 : deepRooms(out, hub);
			return out;
		}

		boolean walkThrough;

		/** Rooms three or more doors from where you arrive (the rooms with an entrance or a way in from another mass; else the stair room; else the hub). */
		int deepRooms(List<Door> doors, int hub) {
			int n = leaves.size();
			int[] dist = new int[n];
			java.util.Arrays.fill(dist, -1);
			List<Integer> queue = new ArrayList<>();
			for (int i = 0; i < n; i++) for (int[] e : entry) if (leaves.get(i).contains(e[0], e[1]) && dist[i] < 0) { dist[i] = 0; queue.add(i); }
			for (int i = 0; i < n && queue.isEmpty(); i++) {
				Leaf lf = leaves.get(i);
				for (int x = lf.x(); x < lf.x() + lf.w() && dist[i] < 0; x++) for (int z = lf.z(); z < lf.z() + lf.d(); z++) if (core[x][z]) { dist[i] = 0; queue.add(i); break; }
			}
			if (queue.isEmpty() && hub >= 0) { dist[hub] = 0; queue.add(hub); }
			if (queue.isEmpty()) return 0;
			for (int h = 0; h < queue.size(); h++)
				for (Door dd : doors) {
					int a = dd.from(), b = dd.to(), cur = queue.get(h);
					int o = a == cur ? b : b == cur ? a : -1;
					if (o >= 0 && dist[o] < 0) { dist[o] = dist[cur] + 1; queue.add(o); }
				}
			int deep = 0;
			for (int dd : dist) if (dd >= 3) deep++;
			return deep;
		}

		static final int WALK_THROUGH_PERCENT = 15;

		static Door pickDoor(List<Door> l, Draws dr) {
			int n = l.size(), mid = n / 2, spread = n / 4;
			return l.get(Math.max(0, Math.min(n - 1, mid + dr.range(-spread, spread))));
		}

		/** The room to hang the others off: most neighbours, the entry room and the stair room favoured, ties by draw. */
		int pickHub(List<Long> keys, Draws dr) {
			int[] deg = new int[leaves.size()];
			for (long k : keys) {
				deg[(int) (k / 100000)]++;
				deg[(int) (k % 100000)]++;
			}
			boolean[] arrive = new boolean[deg.length], hasCore = new boolean[deg.length];
			boolean anyArrive = false;
			for (int i = 0; i < deg.length; i++) {
				Leaf lf = leaves.get(i);
				for (int[] e : entry) arrive[i] |= lf.contains(e[0], e[1]);
				for (int x = lf.x(); x < lf.x() + lf.w(); x++) for (int z = lf.z(); z < lf.z() + lf.d(); z++) hasCore[i] |= core[x][z];
				anyArrive |= arrive[i];
			}
			int[] near = new int[deg.length]; // next to an arrival room that has no other neighbour
			for (long k : keys) {
				int a = (int) (k / 100000), b = (int) (k % 100000);
				if (arrive[a] && deg[a] == 1) near[b] = 50;
				if (arrive[b] && deg[b] == 1) near[a] = 50;
			}
			int best = 0, bestScore = -1;
			for (int i = 0; i < deg.length; i++) {
				// the hub is where you arrive (an entry, or a strip door to the wing), else the stairs
				int score = 2 * (2 * deg[i] + (arrive[i] && deg[i] >= 2 ? 100 : 0) + near[i] + (hasCore[i] ? (anyArrive ? 2 : 50) : 0)) + dr.below(2);
				if (score > bestScore) {
					bestScore = score;
					best = i;
				}
			}
			return best;
		}

		static int find(int[] p, int i) {
			while (p[i] != i) {
				p[i] = p[p[i]];
				i = p[i];
			}
			return i;
		}
	}
}
