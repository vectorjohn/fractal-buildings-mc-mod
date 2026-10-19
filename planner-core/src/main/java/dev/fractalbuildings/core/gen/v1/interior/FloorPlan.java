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

	/** A door through the partition cell (x, z), from room {@code from} to {@code to}, walking {@code facing}. */
	record Door(int x, int z, int from, int to, Facing facing) {}

	/** One room, no partitions. */
	static FloorPlan single(int w, int d) {
		int[][] at = new int[w][d];
		return new FloorPlan(w, d, List.of(new Leaf(0, 0, w, d)), List.of(), List.of(), at, -1);
	}

	/**
	 * Plans a floor. {@code reserved} and {@code core} are indexed [x][z]; core cells are never door
	 * sides. Falls back to a few other draws, then to one room, if the doors can't connect the rooms.
	 */
	static FloorPlan plan(int w, int d, boolean[][] reserved, boolean[][] core, InteriorStyle st, long seed) {
		if (st.roomArea() <= 0) return single(w, d);
		for (int attempt = 0; attempt < 4; attempt++) {
			FloorPlan p = new Planner(w, d, reserved, core, st, Hash64.hash(seed, "attempt", attempt)).plan();
			if (p != null) return p;
		}
		return single(w, d);
	}

	private static final class Planner {
		final int w, d;
		final boolean[][] reserved, core;
		final InteriorStyle st;
		final long seed;
		final List<Leaf> leaves = new ArrayList<>();
		final List<Cut> cuts = new ArrayList<>();
		int corridor = -1;

		Planner(int w, int d, boolean[][] reserved, boolean[][] core, InteriorStyle st, long seed) {
			this.w = w;
			this.d = d;
			this.reserved = reserved;
			this.core = core;
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
			return true;
		}

		void split(int rx, int rz, int rw, int rd) {
			Draws dr = new Draws(Hash64.hash(seed, "split", rx, rz, rw, rd));
			int target = Math.max(1, st.roomArea() * (70 + dr.below(61)) / 100);
			if (rw * rd > target) {
				boolean preferX = rw > rd || (rw == rd && dr.chance(1, 2));
				int pct = 35 + dr.below(31);
				for (int pass = 0; pass < 2; pass++) {
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
			for (long k : keys) {
				int a = find(parent, (int) (k / 100000)), b = find(parent, (int) (k % 100000));
				boolean tree = a != b;
				if (tree) {
					parent[a] = b;
					joins++;
				} else if (!dr.chance(st.loopPercent(), 100)) continue;
				List<Door> l = cand.get(k);
				int n = l.size(), mid = n / 2, spread = n / 4;
				out.add(l.get(Math.max(0, Math.min(n - 1, mid + dr.range(-spread, spread)))));
			}
			return joins == leaves.size() - 1 ? out : null;
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
