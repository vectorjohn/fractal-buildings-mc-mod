package dev.fractalbuildings.core.gen.v1.types.highrise;

import dev.fractalbuildings.core.gen.v1.interior.FloorPlan;
import dev.fractalbuildings.core.gen.v1.interior.FloorPlan.Cut;
import dev.fractalbuildings.core.gen.v1.interior.FloorPlan.Door;
import dev.fractalbuildings.core.gen.v1.interior.FloorPlan.Leaf;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * Plate templates for the floors of a tower: explicit, rule-driven plans that output an ordinary
 * {@link FloorPlan}, so the interior pass (partitions, room ids, hooks, flights) works on them unchanged.
 * Pure geometry on its own input record ({@link Spec}); nothing here knows the massing.
 *
 * <p>Plate frame: x runs along the street front (and the hall), z from the street side (0) to the core
 * side. Rows from the street: band B (street side units or the banquet room), one partition row, the
 * hall rows {@code [hallZ0, hallZ1)}, one partition row (the hall's back line, also the core block's
 * hall-side wall), band A (core side). The core block sits in band A: its stairwell and shaft rects are
 * given in plate cells (interior, half-open), its walls are the cells around them.
 *
 * <ul>
 * <li>CORRIDOR (typical floors): the hall across the plate, units in both bands, each with an optional
 *     private {@code entry} strip on the hall side (DS-11), facade rooms side by side behind it, back
 *     rooms behind the core block entered from the flat.</li>
 * <li>GRAND (lobby, open floor): band B and the hall rows are one room; back of house beside and behind
 *     the core block.</li>
 * <li>BANQUET: the hall rows stay a {@code corridor} foyer, the banquet room is band B behind a 2-3 wide
 *     archway; the stairwell door opens onto the foyer; kitchen and storage beside and behind the core.</li>
 * <li>STAIRHOUSE: the stairwell and a {@code plant} room over the shaft.</li>
 * </ul>
 *
 * <p>{@link #plan} returns {@code null} when the geometry doesn't fit the template (a band too shallow,
 * a side strip of 1-2 cells, ...): the caller falls back to the guillotine planner.
 */
public final class TowerPlate {
	private TowerPlate() {}

	public enum Template { CORRIDOR, GRAND, BANQUET, STAIRHOUSE }

	/**
	 * @param stairwell x0, z0, x1, z1 of the stairwell interior in plate cells, half-open; 3 or 4 deep, z0 =
	 *     {@code hallZ1 + 1}
	 * @param shaft the lift shaft's interior, same form, beside the stairwell with one wall cell between
	 *     (z0 = stairwell z0, inside its z range); its cells belong to no room and have no cut
	 * @param reserved window cells (the facade's inside cells), indexed [x][z]; partitions never end on one
	 * @param grandUse {@code lobby} or {@code open} (GRAND)
	 * @param unitTarget target unit width (cells) along the hall
	 * @param unitUse {@code flat} or {@code office}
	 * @param entryStrip true: a windowless private {@code entry} strip on the hall side of deep units
	 *     (default); false: units are wider instead and have no strip
	 * @param entranceX plate x of the street entrance (picks the lobby's office side)
	 */
	public record Spec(int w, int d, int[] stairwell, int[] shaft, int hallZ0, int hallZ1, boolean[][] reserved, Template template,
			String grandUse, int unitTarget, String unitUse, boolean entryStrip, int entranceX) {}

	/** @param stairwell leaf index of the stairwell room; @param corridor index of the corridor leaf or -1; @param demoted rooms turned into storage for shape or window */
	public record Result(FloorPlan plan, String[] uses, int stairwell, int corridor, int demoted) {}

	/** Entry strip depth for a band of depth {@code d} (DS-11), 0 for none; capped at 3 (deeper plates are B2b's). */
	static int stripDepth(int d) {
		if (d <= 7) return 0;
		return d <= 10 ? 2 : 3;
	}

	/** Test aid: when true, {@link #WHY} records where a {@code plan} call gave up (costs a stack trace per failure). */
	static volatile boolean DEBUG;
	/** Debug: where the last {@code plan} call on this thread gave up (only with {@link #DEBUG}). */
	static final ThreadLocal<String> WHY = new ThreadLocal<>();

	private static void why(String s) {
		if (DEBUG && WHY.get() == null) WHY.set(s + " @line " + new Throwable().getStackTrace()[2].getLineNumber());
	}

	static boolean no() {
		why("");
		return false;
	}

	static boolean yes() {
		why("");
		return true;
	}

	static <T> T none() {
		why("");
		return null;
	}

	public static Result plan(Spec s, Draws r) {
		if (DEBUG) WHY.set(null);
		Gen g = new Gen(s, r);
		return g.run();
	}

	// ------------------------------------------------------------------------------------------ building

	private static final class Build {
		final int w, d;
		final boolean[][] res;
		final List<Leaf> leaves = new ArrayList<>();
		final List<String> uses = new ArrayList<>();
		final List<int[]> doorReq = new ArrayList<>(); // a, b, x, z, span, arch
		int[] skip; // x0, z0, x1, z1: cells that are neither room nor cut (the lift shaft)
		boolean fail;
		int demoted;

		Build(int w, int d, boolean[][] res) {
			this.w = w;
			this.d = d;
			this.res = res;
		}

		int leaf(int x, int z, int lw, int ld, String use) {
			if (lw < 2 || ld < 2 || x < 0 || z < 0 || x + lw > w || z + ld > d) {
				fail = yes();
				return 0;
			}
			leaves.add(new Leaf(x, z, lw, ld));
			uses.add(use);
			return leaves.size() - 1;
		}

		void door(int a, int bb, int x, int z) { doorReq.add(new int[] {a, bb, x, z, 1, 0}); }

		void arch(int a, int bb, int x, int z, int span) { doorReq.add(new int[] {a, bb, x, z, span, 1}); }

		boolean window(Leaf l) {
			for (int x = l.x(); x < l.x() + l.w(); x++) for (int z = l.z(); z < l.z() + l.d(); z++) if (res[x][z]) return true;
			return false;
		}

		boolean lineFree(boolean constX, int c, int lo, int hi) {
			for (int t = lo; t < hi; t++) if (constX ? res[c][t] : res[t][c]) return false;
			return true;
		}

		Result finish(int stair, int corridor) {
			if (fail) return none();
			int[][] at = new int[w][d];
			for (int[] col : at) java.util.Arrays.fill(col, -1);
			for (int i = 0; i < leaves.size(); i++) {
				Leaf l = leaves.get(i);
				for (int x = l.x(); x < l.x() + l.w(); x++)
					for (int z = l.z(); z < l.z() + l.d(); z++) {
						if (at[x][z] >= 0) return none();
						at[x][z] = i;
					}
			}
			// uses that need a shape or a window get demoted (offices, bedrooms, kitchens) or fail (flats)
			for (int i = 0; i < leaves.size(); i++) {
				Leaf l = leaves.get(i);
				String u = uses.get(i);
				boolean thin = Math.max(l.w(), l.d()) > 2 * Math.min(l.w(), l.d());
				switch (u) {
					case "flat" -> {
						if (thin || !window(l)) return none();
					}
					case "bedroom", "office", "kitchen" -> {
						if (thin || ((u.equals("bedroom") || u.equals("office")) && !window(l))) { uses.set(i, "storage"); demoted++; }
					}
					default -> {}
				}
			}
			boolean[][] cutCell = new boolean[w][d];
			for (int x = 0; x < w; x++)
				for (int z = 0; z < d; z++)
					if (at[x][z] < 0 && !(skip != null && x >= skip[0] && x < skip[2] && z >= skip[1] && z < skip[3])) {
						if (res[x][z]) return none();
						cutCell[x][z] = true;
					}
			List<Cut> cuts = new ArrayList<>();
			boolean[][] covered = new boolean[w][d];
			for (int z = 0; z < d; z++)
				for (int x = 0; x < w; ) {
					if (!cutCell[x][z]) { x++; continue; }
					int e = x;
					while (e < w && cutCell[e][z]) e++;
					if (e - x >= 2) {
						cuts.add(new Cut(false, z, x, e - x));
						for (int k = x; k < e; k++) covered[k][z] = true;
					}
					x = e;
				}
			for (int x = 0; x < w; x++)
				for (int z = 0; z < d; ) {
					if (!cutCell[x][z] || covered[x][z]) { z++; continue; }
					int e = z;
					while (e < d && cutCell[x][e] && !covered[x][e]) e++;
					cuts.add(new Cut(true, x, z, e - z));
					z = e;
				}
			List<Door> doors = new ArrayList<>();
			for (int[] q : doorReq) {
				Facing f = facing(at, q[0], q[1], q[2], q[3]);
				if (f == null) return none();
				boolean alongZ = f == Facing.EAST || f == Facing.WEST;
				for (int k = 1; k < q[4]; k++)
					if (facing(at, q[0], q[1], alongZ ? q[2] : q[2] + k, alongZ ? q[3] + k : q[3]) != f) return none();
				doors.add(new Door(q[2], q[3], q[0], q[1], f, q[4], q[5] == 1));
			}
			FloorPlan plan = new FloorPlan(w, d, List.copyOf(leaves), List.copyOf(cuts), List.copyOf(doors), at, corridor);
			return new Result(plan, uses.toArray(new String[0]), stair, corridor, demoted);
		}

		/** Facing of a door through partition cell (x, z) from room a to b, null if the cell isn't a partition between them. */
		private Facing facing(int[][] at, int a, int bb, int x, int z) {
			if (x < 0 || z < 0 || x >= w || z >= d || at[x][z] >= 0) return null;
			if (skip != null && x >= skip[0] && x < skip[2] && z >= skip[1] && z < skip[3]) return null;
			if (x > 0 && x < w - 1) {
				if (at[x - 1][z] == a && at[x + 1][z] == bb) return Facing.EAST;
				if (at[x + 1][z] == a && at[x - 1][z] == bb) return Facing.WEST;
			}
			if (z > 0 && z < d - 1) {
				if (at[x][z - 1] == a && at[x][z + 1] == bb) return Facing.SOUTH;
				if (at[x][z + 1] == a && at[x][z - 1] == bb) return Facing.NORTH;
			}
			return null;
		}
	}

	// ------------------------------------------------------------------------------------------ generator

	private static final class Gen {
		final Spec s;
		final Draws r;
		final Build b;
		final int w, d;
		final int sx0, sz0, sx1, sz1, hx0, hx1;
		final int blockL, blockR; // wall columns of the core block, -1 / w when flush with the plate edge
		int stair, hall = -1;

		Gen(Spec s, Draws r) {
			this.s = s;
			this.r = r;
			this.w = s.w();
			this.d = s.d();
			this.b = new Build(w, d, s.reserved());
			sx0 = s.stairwell()[0];
			sz0 = s.stairwell()[1];
			sx1 = s.stairwell()[2];
			sz1 = s.stairwell()[3];
			hx0 = s.shaft()[0];
			hx1 = s.shaft()[2];
			blockL = Math.min(sx0, hx0) - 1;
			blockR = Math.max(sx1, hx1);
		}

		Result run() {
			if (s.template() == Template.STAIRHOUSE) return stairhouse();
			if (!geometryOk()) return null;
			b.skip = new int[] {hx0, sz0, hx1, sz1};
			return switch (s.template()) {
				case CORRIDOR -> corridor();
				case GRAND -> grand(false);
				case BANQUET -> grand(true);
				default -> null;
			};
		}

		boolean geometryOk() {
			if (s.hallZ1() - s.hallZ0() < 2 || s.hallZ0() < 4 || sz0 != s.hallZ1() + 1) return no();
			if (s.shaft()[1] != sz0 || s.shaft()[3] > sz1 || sz1 - sz0 < 3) return no();
			if (!(sx1 + 1 == hx0 || hx1 + 1 == sx0)) return no();
			if (blockL == 0 || blockR == w - 1 || blockL < -1 || blockR > w) return no();
			if (blockL > 0 && blockL < 3) return no();
			if (blockR < w && w - blockR - 1 < 3) return no();
			int behind = d - sz1 - 1;
			if (sz1 < d && behind < 3) return no();
			if (sz1 > d) return no();
			return d - s.hallZ1() - 1 >= 3;
		}

		int stairDoorX() { return sx0 + (sx1 - sx0) / 2; }

		// ---- bands

		/** One band: hall line, depth, direction. */
		final class Band {
			final boolean a;
			final int line, depth;

			Band(boolean a) {
				this.a = a;
				this.line = a ? s.hallZ1() : s.hallZ0() - 1;
				this.depth = a ? d - s.hallZ1() - 1 : s.hallZ0() - 1;
			}

			int z0(int ta, int tb) { return a ? line + 1 + ta : line - tb; }

			int zAt(int t) { return a ? line + 1 + t : line - 1 - t; }

			int facadeZ() { return zAt(depth - 1); }
		}

		// ---- units

		static int ceilDiv(int a, int c) { return (a + c - 1) / c; }

		/** Splits [a, b) into units with cut cells between; each cut at the legal x nearest the even spacing. */
		List<int[]> segment(int a, int e0, int target, int maxU, int zLo, int zHi, int minK, java.util.function.IntPredicate fit) {
			int len = e0 - a;
			int k = Math.max(minK, (len + 1) / (target + 1));
			k = Math.max(k, ceilDiv(len + 1, maxU + 1));
			List<Integer> cuts = new ArrayList<>();
			int prev = a - 1;
			for (int i = 1; i < k; i++) {
				int ideal = a + (i * (len + 1)) / k - 1;
				int best = legalCut(ideal, prev + 1, e0, zLo, zHi, 2, fit);
				if (best >= 0) {
					cuts.add(best);
					prev = best;
				}
			}
			while (!cuts.isEmpty() && !fit.test(e0 - cuts.get(cuts.size() - 1) - 1) && e0 - cuts.get(cuts.size() - 1) - 1 + (cuts.size() > 1 ? cuts.get(cuts.size() - 1) - cuts.get(cuts.size() - 2) : cuts.get(0) - a) + 1 <= maxU) cuts.remove(cuts.size() - 1);
			// too wide units get an extra cut near their middle where one is legal
			for (boolean again = true; again; ) {
				again = false;
				int start = a;
				for (int i = 0; i <= cuts.size() && !again; i++) {
					int end = i < cuts.size() ? cuts.get(i) : e0;
					if (end - start > maxU) {
						int best = legalCut(start + (end - start) / 2, start, end, zLo, zHi, 3, fit);
						if (best >= 0) {
							cuts.add(i, best);
							again = true;
						}
					}
					start = end + 1;
				}
			}
			List<int[]> out = new ArrayList<>();
			int start = a;
			for (int c : cuts) {
				out.add(new int[] {start, c});
				start = c + 1;
			}
			out.add(new int[] {start, e0});
			return out;
		}

		/** The cut cell nearest {@code ideal} (within {@code reach}) that leaves 4+ cells either side inside [lo, hi) and meets no window; -1 if none. */
		int legalCut(int ideal, int lo, int hi, int zLo, int zHi, int reach, java.util.function.IntPredicate fit) {
			for (int dl = 0; dl <= reach; dl++)
				for (int sg = 1; sg >= -1; sg -= 2) {
					int c = ideal + sg * dl;
					if (c - lo >= 4 && hi - c - 1 >= 4 && fit.test(c - lo) && b.lineFree(true, c, zLo, zHi)) return c;
					if (dl == 0) break;
				}
			return -1;
		}

		/** The entry strip depth for a whole band segment [a, e): the table's, slid a row if a side window would meet its partition; 0 for none. */
		int bandStrip(Band bd, int a, int e) {
			int D = bd.depth, E = s.entryStrip() && !s.unitUse().equals("office") ? stripDepth(D) : 0;
			if (E == 0) return 0;
			for (int dE : new int[] {0, 1, -1}) {
				int c = E + dE;
				if (c >= 2 && c <= 3 && D - c - 1 >= 3 && legalRow(bd, c, a, e)) return c;
			}
			return 0;
		}

		/** True if the partition row at depth {@code t} of {@code bd} over [xa, xb) meets no side window. */
		boolean legalRow(Band bd, int t, int xa, int xb) {
			int z = bd.zAt(t);
			return (xa != 0 || !s.reserved()[0][z]) && (xb != w || !s.reserved()[w - 1][z]);
		}

		/** Hall door x for a unit [xa, xb): one cell in at a plate end, else the unit's middle. */
		int hallDoorX(int xa, int xb) {
			if (xa == 0) return xa + 1;
			if (xb == w) return xb - 2;
			return (xa + xb - 1) / 2;
		}

		static final int LEFT = 0, RIGHT = 1, ANY = 2;

		/** Store rooms over [xa, xb) of a band, each at most 2 x the band depth wide (no corridor-shaped store), each with a hall door. */
		void closets(Band bd, int xa, int xb, int hall) {
			int D = bd.depth;
			int zLo = bd.a ? bd.z0(0, D) : 0, zHi = bd.a ? d : bd.line;
			List<int[]> pieces = xb - xa > 2 * D ? segment(xa, xb, 2 * D, 2 * D, zLo, zHi, 1, u -> true) : List.of(new int[] {xa, xb});
			for (int[] p : pieces) {
				int c = b.leaf(p[0], bd.z0(0, D), p[1] - p[0], D, "storage");
				b.door(hall, c, hallDoorX(p[0], p[1]), bd.line);
			}
		}

		/**
		 * One unit [xa, xb) of band {@code bd}. {@code blockSide}: -1 the core block is on the unit's left, 1
		 * on its right, 0 not next to it. Returns the main room (flat or office) leaf or -1 (closet).
		 */
		int unit(Band bd, int E, int xa, int xb, int hall, int blockSide) {
			int D = bd.depth, U = xb - xa;
			boolean office = s.unitUse().equals("office");
			int F = E > 0 ? D - E - 1 : D;
			int hx = hallDoorX(xa, xb);
			if (U < Math.max(4, (F + 1) / 2)) {
				closets(bd, xa, xb, hall);
				return -1;
			}
			boolean strip = E > 0;
			boolean endU = xa == 0 || xb == w;
			int hi = 2 * F;
			int minF = (F + 1) / 2;
			int prefMin = (2 * F + 2) / 3;
			int nMax = strip ? 3 : (endU || blockSide != 0 ? 2 : 3);
			int pref = ANY;
			if (blockSide != 0) pref = blockSide > 0 ? RIGHT : LEFT;
			if (!strip && endU) {
				int hallPref = xa == 0 ? LEFT : RIGHT;
				if (blockSide != 0 && U <= hi) nMax = 1;
				else pref = hallPref; // the hall door wins; the back room then goes to the other side
			}
			int n0 = office ? 1 : U < 9 ? 1 : U < 14 ? 2 : 3;
			int n = Math.min(n0, nMax);
			while (n < nMax && U - (n - 1) > n * hi) n++;
			Rooms rp = null;
			List<Integer> order = new ArrayList<>(List.of(n));
			for (int k = n - 1; k >= 1; k--) order.add(k);
			for (int k = n + 1; k <= nMax; k++) order.add(k);
			for (int k : order) {
				rp = rooms(k, U, F, office, strip, pref, xa, xb, bd.facadeZ());
				if (rp != null) break;
			}
			if (rp == null) {
				// shallow band (a 3-4 deep strip beside the core): no legal flat fits, a store room takes the unit
				closets(bd, xa, xb, hall);
				return -1;
			}
			n = rp.uses.length;
			String[] uses = rp.uses;
			int[] wd = rp.wd;
			int main = rp.main;
			int[] rx = new int[n];
			int xx = xa;
			for (int i = 0; i < n; i++) { rx[i] = xx; xx += wd[i] + 1; }
			int[] id = new int[n];
			if (strip) {
				int st = b.leaf(xa, bd.z0(0, E), U, E, "entry");
				b.door(hall, st, hx, bd.line);
				for (int i = 0; i < n; i++) id[i] = b.leaf(rx[i], bd.z0(E + 1, D), wd[i], F, uses[i]);
				for (int i = 0; i < n; i++) b.door(st, id[i], rx[i] + wd[i] / 2, bd.zAt(E));
			} else {
				for (int i = 0; i < n; i++) id[i] = b.leaf(rx[i], bd.z0(0, D), wd[i], D, uses[i]);
				int dx = Math.max(rx[main], Math.min(rx[main] + wd[main] - 1, hx));
				b.door(hall, id[main], dx, bd.line);
				int t = (D - 1) / 2;
				if (main > 0) b.door(id[main], id[main - 1], rx[main] - 1, bd.zAt(t));
				if (main < n - 1) b.door(id[main], id[main + 1], rx[main] + wd[main], bd.zAt(t));
			}
			// does the main room touch the block-side edge?
			boolean touches = blockSide > 0 ? main == n - 1 : blockSide < 0 && main == 0;
			return touches || blockSide == 0 ? id[main] : -2 - id[main];
		}

		record Rooms(String[] uses, int[] wd, int main) {}

		/** True if a unit {@code U} wide and {@code F} deep can be split into rooms of legal width (with a cell to spare for sliding the cuts). */
		boolean fits(int U, int F, boolean office, int nMax) {
			int hi = 2 * F, minF = (F + 1) / 2;
			for (int n = 1; n <= nMax; n++) {
				int sumLo = office ? n * Math.max(4, minF) : n == 3 ? Math.max(5, minF) + 2 * Math.max(4, minF) : n * Math.max(4, minF);
				int avail = U - (n - 1);
				if (avail >= sumLo + (n >= 2 ? 2 : 0) && avail <= n * hi) return true;
			}
			return false;
		}

		/** Splits a unit into {@code n} rooms side by side: uses, widths, main room; null if the widths or the window-free cuts don't work out. */
		Rooms rooms(int n, int U, int F, boolean office, boolean strip, int pref, int xa, int xb, int zF) {
			int hi = 2 * F, minF = (F + 1) / 2, prefMin = (2 * F + 2) / 3;
			String[] uses = roomUses(office, n, strip, pref);
			int main = mainIndex;
			int[] lo = new int[n], pf = new int[n];
			int sumLo = 0;
			for (int i = 0; i < n; i++) {
				int m = uses[i].equals("flat") ? (n == 3 ? 5 : 4) : 4;
				lo[i] = Math.max(m, minF);
				pf[i] = Math.max(office ? Math.max(m, 6) : m, Math.max(m, prefMin));
				sumLo += lo[i];
			}
			int avail = U - (n - 1);
			if (avail < sumLo || avail > n * hi) return null;
			int[] wd = new int[n];
			int sum = 0;
			for (int i = 0; i < n; i++) {
				wd[i] = Math.max(lo[i], Math.min(hi, pf[i]));
				sum += wd[i];
			}
			while (sum > avail) {
				int bi = -1;
				for (int i = 0; i < n; i++) if (wd[i] > lo[i] && (bi < 0 || wd[i] - lo[i] > wd[bi] - lo[bi])) bi = i;
				wd[bi]--;
				sum--;
			}
			for (int guard = 0; sum < avail && guard < 1000; guard++) {
				boolean grew = false;
				for (int k = 0; k < n && sum < avail; k++) {
					int i = (main + k) % n;
					if (wd[i] < hi) { wd[i]++; sum++; grew = true; }
				}
				if (!grew) break;
			}
			// slide each internal cut to a window-free facade cell (cells inside the room are never windows)
			int cursor = xa;
			for (int i = 0; i < n - 1; i++) {
				int restLo = 0, restHi = 0;
				for (int j = i + 1; j < n; j++) { restLo += lo[j]; restHi += hi; }
				restLo += n - i - 2;
				restHi += n - i - 2;
				int cut0 = cursor + wd[i], best = -1;
				for (int dl = 0; dl <= 3 && best < 0; dl++)
					for (int sg = 1; sg >= -1 && best < 0; sg -= 2) {
						int c = cut0 + sg * dl;
						int wi = c - cursor, remain = xb - (c + 1);
						if (wi >= lo[i] && wi <= hi && remain >= restLo && remain <= restHi && !s.reserved()[c][zF]) best = c;
						if (dl == 0) break;
					}
				if (best < 0) return null;
				wd[i] = best - cursor;
				cursor = best + 1;
			}
			int used = 0;
			for (int i = 0; i < n - 1; i++) used += wd[i] + 1;
			wd[n - 1] = U - used;
			if (wd[n - 1] < lo[n - 1] || wd[n - 1] > hi) return null;
			return new Rooms(uses, wd, main);
		}

		int mainIndex;

		String[] roomUses(boolean office, int n, boolean strip, int pref) {
			String[] u = new String[n];
			int m;
			if (n == 1) m = 0;
			else if (n == 2) m = pref == LEFT ? 0 : pref == RIGHT ? 1 : r.below(2);
			else m = strip ? (pref == LEFT ? 0 : pref == RIGHT ? 2 : 1) : 1;
			for (int i = 0; i < n; i++) u[i] = office ? "office" : i == m ? "flat" : "bedroom";
			mainIndex = m;
			return u;
		}

		// ---- CORRIDOR

		Result corridor() {
			int hw = s.hallZ1() - s.hallZ0();
			hall = b.leaf(0, s.hallZ0(), w, hw, "corridor");
			stair = b.leaf(sx0, sz0, sx1 - sx0, sz1 - sz0, "stairwell");
			b.door(hall, stair, stairDoorX(), s.hallZ1());
			boolean office = s.unitUse().equals("office");
			// band B
			Band bb = new Band(false);
			if (bb.depth < 3) return none();
			bandUnits(bb, 0, w, -1, -1, null);
			// band A: segments beside the block
			Band ba = new Band(true);
			int[] adj = {-1, -1};
			if (blockL > 0) bandUnits(ba, 0, blockL, 1, 0, adj);
			if (blockR < w) bandUnits(ba, blockR + 1, w, -1, 1, adj);
			if (b.fail) return none();
			if (!behind(office ? "office" : "bedroom", new int[][] {mainList(adj[0]), mainList(adj[1])}, -1)) return none();
			return b.fail ? null : b.finish(stair, hall);
		}

		int[] mainList(int m) { return m >= 0 ? new int[] {m} : new int[0]; }

		/** Units across [a, b) of a band; {@code blockSide} is where the block is for the last (1) or first (-1) unit; the main room next to it goes to adj[slot]. */
		void bandUnits(Band bd, int a, int e, int blockSide, int slot, int[] adj) {
			int D = bd.depth;
			int Eh = bandStrip(bd, a, e);
			int F = Eh > 0 ? D - Eh - 1 : D;
			boolean office = s.unitUse().equals("office");
			int minW = office ? Math.max(6, (2 * F + 2) / 3) : Math.max(4, (F + 1) / 2);
			int target = Math.max(s.unitTarget(), minW);
			int maxU = Math.max(minW, (Eh > 0 ? 3 : 2) * 2 * F + (Eh > 0 ? 2 : 1));
			int zLo = bd.a ? bd.z0(0, D) : 0, zHi = bd.a ? d : bd.line;
			List<int[]> units = segment(a, e, target, maxU, zLo, zHi, a == 0 && e == w && e - a >= 10 ? 2 : 1, u -> fits(u, F, office, Eh > 0 ? 3 : 2));
			for (int i = 0; i < units.size(); i++) {
				int[] u = units.get(i);
				int bs = 0;
				if (blockSide > 0 && i == units.size() - 1) bs = 1;
				if (blockSide < 0 && i == 0 && adj != null) bs = -1;
				int m = unit(bd, Eh, u[0], u[1], hall, bs);
				if (bs != 0 && adj != null) adj[slot] = m >= 0 ? m : -1;
				if (b.fail) return;
			}
		}

		// ---- GRAND and BANQUET

		/** Grand-room pieces along x: leaf index per column range, -1 on a cut column. */
		int[] pieceAt;

		int parentAt(int x) { return x >= 0 && x < w ? pieceAt[x] : -1; }

		/**
		 * Anteroom widths {left, right} (0: none) for a grand room {@code depthG} deep: the main room stays within
		 * {@code capTenths}/10 of its depth, anterooms are at least as wide as depth / 2.5, cuts meet no window,
		 * and (lobby, open) the main room keeps the stairwell door and, if possible, the entrance.
		 */
		int[] anterooms(int depthG, int capTenths, boolean needStair) {
			int maxMain = capTenths * depthG / 10;
			if (w <= maxMain) return new int[] {0, 0};
			int minA = Math.max(4, (depthG * 10 + 24) / 25), sd = stairDoorX();
			int[] best = null;
			int bestScore = Integer.MAX_VALUE;
			for (int a = 0; a < w; a = a == 0 ? minA : a + 1)
				for (int c = 0; c < w; c = c == 0 ? minA : c + 1) {
					int mw = w - (a > 0 ? a + 1 : 0) - (c > 0 ? c + 1 : 0);
					if (mw > maxMain || mw < Math.max(6, depthG / 2)) continue;
					if (a > capTenths * depthG / 10 || c > capTenths * depthG / 10) continue;
					if (a > 0 && !b.lineFree(true, a, 0, depthG)) continue;
					if (c > 0 && !b.lineFree(true, w - c - 1, 0, depthG)) continue;
					int m0 = a > 0 ? a + 1 : 0, m1 = m0 + mw;
					int score = ((a > 0 ? 1 : 0) + (c > 0 ? 1 : 0)) * 10 + Math.abs(a - c);
					if (needStair && (sd < m0 || sd >= m1)) score += 1000;
					if (needStair && (s.entranceX() < m0 || s.entranceX() >= m1)) score += 20;
					if (score < bestScore) {
						bestScore = score;
						best = new int[] {a, c};
					}
				}
			return best == null || bestScore >= 1000 ? new int[] {0, 0} : best;
		}

		Result grand(boolean banquet) {
			int hw = s.hallZ1() - s.hallZ0();
			int bandB = s.hallZ0() - 1;
			if (bandB < 3) return none();
			String g = s.grandUse();
			int depthG = banquet ? bandB : s.hallZ1();
			String mainUse = banquet ? "banquet" : g;
			int[] ant = anterooms(depthG, banquet ? 30 : 25, !banquet);
			int a = ant[0], c = ant[1];
			pieceAt = new int[w];
			int left = -1, right = -1;
			if (a > 0) left = b.leaf(0, 0, a, depthG, "open");
			int m0 = a > 0 ? a + 1 : 0, m1 = c > 0 ? w - c - 1 : w;
			int main = b.leaf(m0, 0, m1 - m0, depthG, mainUse);
			if (c > 0) right = b.leaf(w - c, 0, c, depthG, banquet ? "storage" : "open");
			java.util.Arrays.fill(pieceAt, main);
			for (int x = 0; x < a; x++) pieceAt[x] = left;
			for (int x = w - c; x < w && c > 0; x++) pieceAt[x] = right;
			if (a > 0) pieceAt[a] = -1;
			if (c > 0) pieceAt[w - c - 1] = -1;
			int span1 = 2 + r.below(2);
			if (a > 0) b.arch(left, main, a, (depthG - span1) / 2, span1);
			if (c > 0) b.arch(main, right, w - c - 1, (depthG - span1) / 2, span1);
			if (banquet) {
				hall = b.leaf(0, s.hallZ0(), w, hw, "corridor");
				int span = 2 + r.below(2);
				b.arch(main, hall, (m0 + m1 - span) / 2, bandB, span);
				java.util.Arrays.fill(pieceAt, hall);
			} else {
				hall = -1;
			}
			stair = b.leaf(sx0, sz0, sx1 - sx0, sz1 - sz0, "stairwell");
			if (parentAt(stairDoorX()) < 0) return none();
			b.door(parentAt(stairDoorX()), stair, stairDoorX(), s.hallZ1());
			Band ba = new Band(true);
			String leftUse, rightUse;
			int cx = s.entranceX();
			int mode; // which piece of a long segment keeps the use: 0 every piece, 1 the one beside the core, 2 the one nearest the entrance
			if (banquet) {
				int sd = stairDoorX();
				boolean kitchenRight = sd <= w - sd; // the side whose plate end is farther from the stairwell door
				if (blockR >= w) kitchenRight = false;
				if (blockL <= 0) kitchenRight = true;
				leftUse = kitchenRight ? "storage" : "kitchen";
				rightUse = kitchenRight ? "kitchen" : "storage";
				mode = 1;
			} else if (g.equals("lobby")) {
				int dl = blockL > 0 ? Math.abs(cx - (blockL - 1) / 2) : Integer.MAX_VALUE, dr = blockR < w ? Math.abs(cx - (blockR + 1 + w) / 2) : Integer.MAX_VALUE;
				boolean officeLeft = dl <= dr;
				leftUse = officeLeft ? "office" : "storage";
				rightUse = officeLeft ? "storage" : "office";
				mode = 2;
			} else {
				leftUse = "office";
				rightUse = "office";
				mode = 0;
			}
			List<Integer> leftAdj = new ArrayList<>(), rightAdj = new ArrayList<>();
			if (blockL > 0) side(ba, 0, blockL, leftUse, mode, leftAdj, true);
			if (blockR < w) side(ba, blockR + 1, w, rightUse, mode, rightAdj, false);
			if (b.fail) return none();
			int kitchenSide = !banquet ? -1 : leftUse.equals("kitchen") ? 0 : 1;
			int[][] adj = {leftAdj.stream().mapToInt(Integer::intValue).toArray(), rightAdj.stream().mapToInt(Integer::intValue).toArray()};
			if (!behind("storage", adj, kitchenSide)) return none();
			return b.fail ? null : b.finish(stair, banquet ? hall : -1);
		}

		/** A back-of-house segment [xa, xb) of band A, rooms with doors onto the grand room above; adjacent rooms go to {@code adj}. */
		void side(Band ba, int xa, int xb, String use, int mode, List<Integer> adj, boolean blockOnRight) {
			int L = xb - xa, Da = ba.depth;
			if (use.equals("kitchen") && (Math.min(L, Da) < 3 || L * Da < 12)) use = "storage";
			int maxU = Math.max(3, 2 * Da);
			List<int[]> pieces = L > maxU ? segment(xa, xb, maxU, maxU, ba.z0(0, Da), d, 1, u -> true) : List.of(new int[] {xa, xb});
			int keeper = -1;
			if (mode == 1) keeper = blockOnRight ? pieces.size() - 1 : 0;
			if (mode == 2) {
				int bestD = Integer.MAX_VALUE;
				for (int i = 0; i < pieces.size(); i++) {
					int dd = Math.abs(s.entranceX() - (pieces.get(i)[0] + pieces.get(i)[1] - 1) / 2);
					if (dd < bestD) { bestD = dd; keeper = i; }
				}
			}
			for (int i = 0; i < pieces.size(); i++) {
				int[] p = pieces.get(i);
				int pw = p[1] - p[0];
				String pu = mode == 0 || i == keeper ? use : "storage";
				boolean adjacent = blockOnRight ? i == pieces.size() - 1 : i == 0;
				int hx = -1, h0 = hallDoorX(p[0], p[1]);
				for (int k = 0; k < pw && hx < 0; k++)
					for (int sg = 1; sg >= -1 && hx < 0; sg -= 2) {
						int x = h0 + sg * k;
						if (x >= Math.max(1, p[0]) && x < Math.min(w - 1, p[1]) && parentAt(x) >= 0) hx = x;
						if (k == 0) break;
					}
				if (hx < 0) { b.fail = yes(); return; }
				int f = Da;
				if (2 * Da > 3 * pw) { // deeper than 1.5 x its width: cut in depth
					int want = (3 * pw + 1) / 2;
					f = Da;
					for (int dF : new int[] {0, -1, 1}) {
						int c = Math.min(want, Da - 4) + dF;
						if (c >= 4 && c <= Da - 4 && legalRow(ba, c, p[0], p[1])) { f = c; break; }
					}
				}
				int front = b.leaf(p[0], ba.z0(0, f), pw, f, pu);
				b.door(parentAt(hx), front, hx, ba.line);
				if (adjacent) adj.add(front);
				if (f < Da) {
					int back = b.leaf(p[0], ba.z0(f + 1, Da), pw, Da - f - 1, "storage");
					b.door(front, back, (p[0] + p[1] - 1) / 2, ba.zAt(f));
					if (adjacent) adj.add(back);
				}
			}
		}

		/**
		 * Rooms behind the core block, entered from the rooms beside it. {@code adj[0]} / {@code adj[1]}: the
		 * left / right rooms next to the block's end columns (empty: none). {@code prefer}: the side to join
		 * when there is a single room, -1 for a draw.
		 */
		boolean behind(String use, int[][] adj, int prefer) {
			if (sz1 >= d) return true;
			int z0 = sz1 + 1, bd = d - z0;
			int x0 = Math.min(sx0, hx0), x1 = Math.max(sx1, hx1); // interior [x0, x1)
			boolean hasL = adj[0].length > 0 && blockL >= 1, hasR = adj[1].length > 0 && blockR < w;
			if (!hasL && !hasR) return no();
			int width = x1 - x0;
			boolean split = hasL && hasR && width >= 9;
			int m = -1;
			if (split) {
				int want = x0 + (width - 1) / 2;
				for (int dl = 0; dl <= 2 && m < 0; dl++)
					for (int sg = 1; sg >= -1 && m < 0; sg -= 2) {
						int c = want + sg * dl;
						if (c - x0 >= 4 && x1 - c - 1 >= 4 && !s.reserved()[c][d - 1] && !s.reserved()[c][z0]) m = c;
						if (dl == 0) break;
					}
				split = m >= 0;
			}
			if (split) {
				int l = backRoom(x0, m, z0, bd, use, true), rr = backRoom(m + 1, x1, z0, bd, use, false);
				return join(l, adj[0], blockL, z0) && join(rr, adj[1], blockR, z0);
			}
			boolean left = hasL && hasR ? (prefer >= 0 ? prefer == 0 : r.below(2) == 0) : hasL;
			int room = backRoom(x0, x1, z0, bd, use, left);
			return left ? join(room, adj[0], blockL, z0) : join(room, adj[1], blockR, z0);
		}

		/**
		 * A back room over [x0, x1) whose door is on its {@code nearLeft} end. If it would be wider than 2 x its depth
		 * the far part is cut off as a store room (reached through the near part), so the near part keeps aspect 2.
		 */
		int backRoom(int x0, int x1, int z0, int bd, String use, boolean nearLeft) {
			int width = x1 - x0, nw = 2 * bd;
			if (width <= nw || use.equals("storage")) return b.leaf(x0, z0, width, bd, use);
			for (int dl = 0; dl <= 2; dl++)
				for (int sg = 1; sg >= -1; sg -= 2) {
					int near = nw + sg * dl;
					int c = nearLeft ? x0 + near : x1 - near - 1; // the cut column
					int far = nearLeft ? x1 - c - 1 : c - x0;
					if (near >= 4 && near <= nw && far >= 3 && b.lineFree(true, c, z0, d)) {
						int nr, fr;
						if (nearLeft) {
							nr = b.leaf(x0, z0, near, bd, use);
							fr = b.leaf(c + 1, z0, far, bd, "storage");
							b.door(nr, fr, c, z0 + (bd - 1) / 2);
						} else {
							fr = b.leaf(x0, z0, far, bd, "storage");
							nr = b.leaf(c + 1, z0, near, bd, use);
							b.door(nr, fr, c, z0 + (bd - 1) / 2);
						}
						return nr;
					}
					if (dl == 0) break;
				}
			return b.leaf(x0, z0, width, bd, use);
		}

		/** A door from back room {@code room} through the block's wall column {@code col} to the first of {@code cands} that shares rows with it. */
		boolean join(int room, int[] cands, int col, int z0) {
			for (int c : cands) {
				Leaf l = b.leaves.get(c);
				int lo = Math.max(z0, l.z()), hi = Math.min(d, l.z() + l.d());
				if (hi - lo < 1) continue;
				int z = lo + (hi - lo - 1) / 2;
				if (z == d - 1 && hi - lo > 1) z--;
				b.door(room, c, col, z);
				return true;
			}
			return no();
		}

		// ---- STAIRHOUSE

		Result stairhouse() {
			int hh = sz1 - sz0;
			if (sz0 != 0 || sz1 != d) return none();
			boolean shaftEast = hx0 == sx1 + 1 && sx0 == 0 && hx1 == w;
			boolean shaftWest = hx0 == 0 && sx0 == hx1 + 1 && sx1 == w;
			if (!shaftEast && !shaftWest) return none();
			stair = b.leaf(sx0, sz0, sx1 - sx0, hh, "stairwell");
			b.leaf(hx0, 0, hx1 - hx0, hh, "plant");
			return b.finish(stair, -1);
		}
	}

	// ------------------------------------------------------------------------------------------ dev printer

	/** ASCII picture of a plan: one letter per room use, '#' partition, '=' lift shaft, '+' door, ':' archway. */
	public static String dump(Result res) {
		FloorPlan p = res.plan();
		char[][] g = new char[p.d()][p.w()];
		for (int z = 0; z < p.d(); z++)
			for (int x = 0; x < p.w(); x++) {
				int i = p.leafAt()[x][z];
				g[z][x] = i < 0 ? '#' : glyph(res.uses()[i]);
			}
		for (Door dd : p.doors())
			for (int k = 0; k < dd.span(); k++) g[dd.cellZ(k)][dd.cellX(k)] = dd.arch() ? ':' : '+';
		StringBuilder sb = new StringBuilder();
		for (char[] row : g) sb.append(new String(row)).append('\n');
		return sb.toString();
	}

	private static char glyph(String use) {
		return switch (use) {
			case "corridor" -> '.';
			case "flat" -> 'F';
			case "bedroom" -> 'B';
			case "office" -> 'O';
			case "entry" -> 'e';
			case "storage" -> 's';
			case "stairwell" -> 'S';
			case "kitchen" -> 'K';
			case "banquet" -> 'Q';
			case "lobby" -> 'L';
			case "open" -> 'o';
			case "plant" -> 'P';
			default -> '?';
		};
	}
}
