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
 * <li>STAIRHOUSE: the stairwell and a {@code plant} room over the shaft, each with its own roof door (none between them).</li>
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

	/** @param stairwell leaf index of the stairwell room; @param corridor index of the corridor leaf or -1; @param demoted rooms turned into storage for shape or window; @param studio per room: a flat with no bedroom door (it needs a bed hook) */
	public record Result(FloorPlan plan, String[] uses, int stairwell, int corridor, int demoted, boolean[] studio) {}

	/**
	 * Entry zone depth for a band of depth {@code d}, 0 for none (architect, deep flats, 2026-10-26): none up to 10 deep (fewer, wider
	 * units; the hall door opens into the flat), 3 from 11 (the entry in front of the flat only); capped at 3 (deeper plates are B2b's).
	 */
	static int stripDepth(int d) { return d <= 10 ? 0 : 3; }

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

		boolean windowIn(int x0, int sx, int sz) {
			for (int x = x0; x < x0 + sx; x++) for (int z = 0; z < sz; z++) if (res[x][z]) return true;
			return false;
		}

		boolean window(Leaf l) {
			for (int x = l.x(); x < l.x() + l.w(); x++) for (int z = l.z(); z < l.z() + l.d(); z++) if (res[x][z]) return true;
			return false;
		}

		boolean lineFree(boolean constX, int c, int lo, int hi) {
			for (int t = lo; t < hi; t++) if (constX ? res[c][t] : res[t][c]) return false;
			return true;
		}

		/** What the floor is for: {@code office}, {@code flat}, {@code banquet}, {@code grand}; picks the use of a big windowed store room. */
		/** Leaves that may be galley kitchens (3:1): the rest of a wrap-around strip with no window pair. */
		final java.util.Set<Integer> galleys = new java.util.HashSet<>(); // det-ok: membership only

		String kind = "";

		/** Largest store room (cells, a 6x6 room): beyond it a leftover is split into closets or takes a habitable use. */
		static final int CLOSET = 36;

		/**
		 * Store rooms are closets (DS-32 review): at most {@link #CLOSET} cells and 2:1. A bigger leftover with a window takes the
		 * floor's habitable use when it has a door to a room of that kind; else it is cut into closets, chained by doors from the
		 * piece that had the original door. A banquet floor's kitchen is the biggest windowless-or-not leftover if its own is small.
		 */
		void refineStores() {
			if (kind.equals("banquet")) bigKitchen();
			for (int pass = 0; pass < 4; pass++) {
				boolean changed = false;
				for (int i = 0; i < leaves.size(); i++) {
					if (!uses.get(i).equals("storage")) continue;
					Leaf l = leaves.get(i);
					int a = l.w() * l.d();
					boolean thin = Math.max(l.w(), l.d()) > 2 * Math.min(l.w(), l.d());
					boolean grandFloor = kind.equals("banquet") || kind.equals("grand"); // public floors: a windowed leftover of 24+ cells is a lounge or meeting room, not a store
					if (!thin && a <= (grandFloor ? 23 : CLOSET)) continue;
					if (!thin && window(l)) {
						String u = promoteFor(i);
						if (u != null) { uses.set(i, u); changed = true; continue; }
					}
					if (!thin && a <= CLOSET) continue;
					if (!hasArch(i) && split(i)) changed = true;
				}
				if (!changed) break;
			}
		}

		private boolean hasArch(int i) {
			for (int[] q : doorReq) if ((q[0] == i || q[1] == i) && q[4] > 1) return true;
			return false;
		}

		/** An office or flat with no door onto a hall (corridor, lobby, open floor, entry) or through its own use to one that has, is a store room (hall-access). */
		private void ensureHallAccess() {
			java.util.Set<String> halls = java.util.Set.of("corridor", "lobby", "open", "entry");
			boolean[] ok = new boolean[leaves.size()];
			for (int i = 0; i < ok.length; i++) ok[i] = doorsTo(i, halls);
			for (boolean grew = true; grew; ) {
				grew = false;
				for (int i = 0; i < ok.length; i++) {
					String u = uses.get(i);
					if (ok[i] || !(u.equals("office") || u.equals("flat"))) continue;
					for (int[] q : doorReq) {
						int o = q[0] == i ? q[1] : q[1] == i ? q[0] : -1;
						if (o >= 0 && ok[o] && (uses.get(o).equals(u) || uses.get(o).equals("kitchen"))) { ok[i] = true; grew = true; break; } // own use, or the unit's kitchen that opens onto its entry
					}
				}
			}
			for (int i = 0; i < ok.length; i++) {
				String u = uses.get(i);
				if (!ok[i] && (u.equals("office") || u.equals("flat"))) { uses.set(i, "storage"); demoted++; }
			}
		}

		private boolean doorsTo(int i, java.util.Set<String> kinds) {
			for (int[] q : doorReq) {
				int o = q[0] == i ? q[1] : q[1] == i ? q[0] : -1;
				if (o >= 0 && kinds.contains(uses.get(o))) return true;
			}
			return false;
		}

		/** True if leaf {@code i} has a door to an office that itself has a door onto a hall: the promoted room is a room of that tenancy. */
		private boolean officeWithHallDoor(int i) {
			java.util.Set<String> halls = java.util.Set.of("corridor", "lobby", "open", "entry");
			for (int[] q : doorReq) {
				int o = q[0] == i ? q[1] : q[1] == i ? q[0] : -1;
				if (o < 0 || !uses.get(o).equals("office")) continue;
				if (doorsTo(o, halls)) return true;
			}
			return false;
		}

		private String promoteFor(int i) {
			return switch (kind) {
				case "office" -> officeWithHallDoor(i) ? "office" : null;
				case "flat" -> doorsTo(i, java.util.Set.of("flat")) ? "bedroom" : null;
				case "banquet", "grand" -> doorsTo(i, java.util.Set.of("corridor", "banquet", "open", "lobby")) ? "open" : null;
				default -> null;
			};
		}

		/** A banquet floor's kitchen is at least {@link #CLOSET} + 5 cells where a leftover that size has a door to the foyer or banquet room. */
		private void bigKitchen() {
			int k = uses.indexOf("kitchen");
			if (k < 0) return;
			Leaf kl = leaves.get(k);
			if (kl.area() >= CLOSET + 5) return;
			int best = -1;
			for (int i = 0; i < leaves.size(); i++) {
				if (!uses.get(i).equals("storage")) continue;
				Leaf l = leaves.get(i);
				boolean thin = Math.max(l.w(), l.d()) > 2 * Math.min(l.w(), l.d());
				if (thin || l.area() <= kl.area() || !doorsTo(i, java.util.Set.of("corridor", "banquet", "open"))) continue;
				if (best < 0 || l.area() > leaves.get(best).area()) best = i;
			}
			if (best < 0) return;
			uses.set(best, "kitchen");
			uses.set(k, "storage");
		}

		/** Cuts leaf {@code i} along its long side into pieces of aspect 2 or less (two pieces if it is only too big). False if no legal cut. */
		private boolean split(int i) {
			List<Leaf> sLeaves = new ArrayList<>(leaves);
			List<String> sUses = new ArrayList<>(uses);
			List<int[]> sDoors = new ArrayList<>();
			for (int[] q : doorReq) sDoors.add(q.clone());
			boolean bad = false;
			Leaf l = leaves.get(i);
			boolean alongX = l.w() >= l.d();
			int len = alongX ? l.w() : l.d(), sh = alongX ? l.d() : l.w();
			if (sh < 3) return false;
			int k = Math.max(2, (len + 1 + 2 * sh) / (2 * sh + 1));
			if (Math.max(l.w(), l.d()) <= 2 * Math.min(l.w(), l.d())) k = 2;
			if ((len - (k - 1)) / k < 3) k = (len + 1) / 4;
			if (k < 2) return false;
			int base = len - (k - 1);
			int[] cut = new int[k - 1]; // cut cell offsets along the long side
			int pos = 0;
			for (int c = 0; c < k - 1; c++) {
				int pieceLen = base / k + (c < base % k ? 1 : 0);
				int want = pos + pieceLen, got = -1;
				for (int dl = 0; dl <= 2 && got < 0; dl++)
					for (int sg = 1; sg >= -1 && got < 0; sg -= 2) {
						int o = want + sg * dl;
						int cell = (alongX ? l.x() : l.z()) + o;
						boolean free = alongX ? lineFree(true, cell, l.z(), l.z() + l.d()) : lineFree(false, cell, l.x(), l.x() + l.w());
						if (o - pos >= 3 && len - o - 1 >= 3 * (k - 1 - c) + (k - 2 - c) && free) got = o;
						if (dl == 0) break;
					}
				if (got < 0) return false;
				cut[c] = got;
				pos = got + 1;
			}
			int[] start = new int[k], size = new int[k];
			int p = 0;
			for (int c = 0; c < k; c++) {
				start[c] = p;
				size[c] = (c < k - 1 ? cut[c] : len) - p;
				p = (c < k - 1 ? cut[c] + 1 : len);
				if (size[c] < 3) return false;
			}
			int[] idx = new int[k];
			for (int c = 0; c < k; c++) {
				Leaf pl = alongX ? new Leaf(l.x() + start[c], l.z(), size[c], l.d()) : new Leaf(l.x(), l.z() + start[c], l.w(), size[c]);
				if (c == 0) { leaves.set(i, pl); idx[c] = i; }
				else { leaves.add(pl); uses.add("storage"); idx[c] = leaves.size() - 1; }
			}
			for (int[] q : doorReq) { // doors of the old leaf go to the piece beside the door cell
				for (int side = 0; side < 2; side++) {
					if (q[side] != i) continue;
					boolean moved = false;
					for (int c = 0; c < k && !moved; c++) if (beside(leaves.get(idx[c]), q[2], q[3])) { q[side] = idx[c]; moved = true; }
					// the door cell sat on a new cut: slide it one cell to where the old room's piece and the other room both touch it
					for (int c = 0; c < k && !moved; c++)
						for (int[] dd : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
							int nx = q[2] + dd[0], nz = q[3] + dd[1];
							if (beside(leaves.get(idx[c]), nx, nz) && beside(leaves.get(q[1 - side]), nx, nz) && !leaves.get(idx[c]).contains(nx, nz) && !leaves.get(q[1 - side]).contains(nx, nz)) { q[2] = nx; q[3] = nz; q[side] = idx[c]; moved = true; break; }
						}
					if (!moved) bad = true;
				}
			}
			// every closet opens from a non-storage room; a piece that touches none is entered from a neighbouring piece that does (one store behind
			// another at most); if some piece has neither the leftover stays one bigger store room
			boolean[] direct = new boolean[k];
			for (int c = 0; c < k && !bad; c++) {
				direct[c] = doorsToRoom(idx[c]);
				if (direct[c]) continue;
				for (int j = 0; j < leaves.size() && !direct[c]; j++) {
					String uj = uses.get(j);
					if (j == idx[c] || isPiece(idx, j) || uj.equals("storage") || uj.equals("stairwell") || uj.equals("plant")) continue;
					int[] cell = gapCell(leaves.get(idx[c]), leaves.get(j));
					if (cell != null) { door(idx[c], j, cell[0], cell[1]); direct[c] = true; }
				}
			}
			for (int c = 0; c < k && !bad; c++) { // a store behind a piece that is itself entered through a piece would be two deep
				if (direct[c]) continue;
				for (int[] q : doorReq) {
					int o = q[0] == idx[c] ? q[1] : q[1] == idx[c] ? q[0] : -1;
					if (o >= 0 && !isPiece(idx, o) && uses.get(o).equals("storage") && !doorsToRoom(o)) bad = true;
				}
			}
			for (int c = 0; c < k && !bad; c++) {
				if (direct[c]) continue;
				int n = c > 0 && direct[c - 1] ? c - 1 : c + 1 < k && direct[c + 1] ? c + 1 : -1;
				if (n < 0) { bad = true; break; }
				int lo = Math.min(c, n), cellOff = cut[lo];
				int cell = (alongX ? l.x() : l.z()) + cellOff;
				int mid = (alongX ? l.z() : l.x()) + (sh - 1) / 2;
				door(idx[c], idx[n], alongX ? cell : mid, alongX ? mid : cell);
			}
			if (bad) {
				leaves.clear(); leaves.addAll(sLeaves);
				uses.clear(); uses.addAll(sUses);
				doorReq.clear(); doorReq.addAll(sDoors);
				return false;
			}
			return true;
		}

		private boolean doorsToRoom(int i) {
			for (int[] q : doorReq) {
				int o = q[0] == i ? q[1] : q[1] == i ? q[0] : -1;
				if (o >= 0 && !uses.get(o).equals("storage")) return true;
			}
			return false;
		}

		private static boolean isPiece(int[] idx, int j) {
			for (int x : idx) if (x == j) return true;
			return false;
		}

		/** A partition cell one wide between the rectangles of {@code a} and {@code b} where a door can go (the middle of their shared run), or null. */
		private int[] gapCell(Leaf a, Leaf b) {
			int[][] cand = new int[0][];
			if (a.x() + a.w() + 1 == b.x() || b.x() + b.w() + 1 == a.x()) {
				int x = a.x() + a.w() + 1 == b.x() ? a.x() + a.w() : b.x() + b.w();
				int lo = Math.max(a.z(), b.z()), hi = Math.min(a.z() + a.d(), b.z() + b.d());
				if (hi - lo >= 1) cand = new int[][] {{x, lo + (hi - lo - 1) / 2}};
			} else if (a.z() + a.d() + 1 == b.z() || b.z() + b.d() + 1 == a.z()) {
				int z = a.z() + a.d() + 1 == b.z() ? a.z() + a.d() : b.z() + b.d();
				int lo = Math.max(a.x(), b.x()), hi = Math.min(a.x() + a.w(), b.x() + b.w());
				if (hi - lo >= 1) cand = new int[][] {{lo + (hi - lo - 1) / 2, z}};
			}
			for (int[] c : cand) {
				for (Leaf l : leaves) if (l.contains(c[0], c[1])) return null;
				if (skip != null && c[0] >= skip[0] && c[0] < skip[2] && c[1] >= skip[1] && c[1] < skip[3]) return null;
				return c;
			}
			return null;
		}

		private static boolean beside(Leaf l, int x, int z) {
			return l.contains(x + 1, z) || l.contains(x - 1, z) || l.contains(x, z + 1) || l.contains(x, z - 1);
		}

		Result finish(int stair, int corridor) {
			if (fail) return none();
			// uses that need a shape or a window get demoted (offices, bedrooms, kitchens) or fail (flats)
			for (int i = 0; i < leaves.size(); i++) {
				Leaf l = leaves.get(i);
				String u = uses.get(i);
				boolean thin = Math.max(l.w(), l.d()) > 2 * Math.min(l.w(), l.d());
				switch (u) {
					case "flat" -> {
						// a living room with no window (a style with few windows, a facade bay blanked by a pier) becomes a store room rather than failing the plan
						if (thin || !window(l)) { uses.set(i, "storage"); demoted++; }
					}
					case "bedroom", "office", "kitchen" -> {
						if (u.equals("kitchen") && galleys.contains(i) ? Math.max(l.w(), l.d()) > 3 * Math.min(l.w(), l.d()) : thin || ((u.equals("bedroom") || u.equals("office")) && !window(l))) { uses.set(i, "storage"); demoted++; }
					}
					default -> {}
				}
			}
			boolean[][] cutCell = new boolean[w][d];
			ensureHallAccess();
			refineStores();
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
			boolean[] studio = new boolean[leaves.size()];
			for (int i = 0; i < studio.length; i++) {
				if (!uses.get(i).equals("flat")) continue;
				studio[i] = true;
				for (Door dd : doors) {
					int o = dd.from() == i ? dd.to() : dd.to() == i ? dd.from() : -1;
					if (o >= 0 && uses.get(o).equals("bedroom")) studio[i] = false;
				}
			}
			return new Result(plan, uses.toArray(new String[0]), stair, corridor, demoted, studio);
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
				if (c == 3 && D - c - 1 >= 3 && legalRow(bd, c, a, e)) return c;
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
				return decline(bd, xa, xb, hall, blockSide);
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
				return decline(bd, xa, xb, hall, blockSide);
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
				// deep unit (D >= 11): only the part in front of the flat is the entry (a foyer, 6 wide at most, the rest a closet);
				// in front of a bedroom is its wardrobe, reached from the bedroom; the bedrooms open off the flat
				int pz = bd.z0(0, E);
				for (int i = 0; i < n; i++) id[i] = b.leaf(rx[i], bd.z0(E + 1, D), wd[i], F, uses[i]);
				for (int i = 0; i < n; i++) {
					if (i == main) {
						int ew = Math.min(wd[i], 6);
						int en = b.leaf(rx[i], pz, ew, E, "entry");
						b.door(hall, en, hallDoorX(rx[i], rx[i] + ew), bd.line);
						b.door(en, id[i], rx[i] + ew / 2, bd.zAt(E));
						int rr = wd[i] - ew - 1;
						if (rr >= 3) {
							int cl = b.leaf(rx[i] + ew + 1, pz, rr, E, "storage");
							b.door(en, cl, rx[i] + ew, pz + (E - 1) / 2);
						}
					} else {
						int ward = b.leaf(rx[i], pz, wd[i], E, "storage");
						b.door(id[i], ward, rx[i] + wd[i] / 2, bd.zAt(E));
						b.door(id[main], id[i], i < main ? rx[main] - 1 : rx[main] + wd[main], bd.zAt(E + 1 + (F - 1) / 2));
					}
				}
			} else {
				for (int i = 0; i < n; i++) id[i] = b.leaf(rx[i], bd.z0(0, D), wd[i], D, uses[i]);
				int dx = Math.max(rx[main], Math.min(rx[main] + wd[main] - 1, hx));
				if (office) {
					// every office opens on the hall (hall-access: an office is never reached through another)
					for (int i = 0; i < n; i++) b.door(hall, id[i], i == main ? dx : hallDoorX(rx[i], rx[i] + wd[i]), bd.line);
				} else {
					b.door(hall, id[main], dx, bd.line);
					int t = (D - 1) / 2;
					if (main > 0) b.door(id[main], id[main - 1], rx[main] - 1, bd.zAt(t));
					if (main < n - 1) b.door(id[main], id[main + 1], rx[main] + wd[main], bd.zAt(t));
				}
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
			b.kind = s.unitUse().equals("office") ? "office" : "flat";
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
			wrapOk = sz1 < d;
			if (blockL > 0) bandUnits(ba, 0, blockL, 1, 0, adj);
			if (blockR < w) bandUnits(ba, blockR + 1, w, -1, 1, adj);
			if (b.fail) return none();
			if (declined[0] != null || declined[1] != null) {
				// a strip beside the block too narrow for a unit: with a unit on the other side it is a closet; else the strips and the
				// back room are one wrap-around unit (the strip is its entry, or an office of the tenancy, the back room its flat or office)
				boolean unitBeside = adj[0] >= 0 && declined[0] == null || adj[1] >= 0 && declined[1] == null;
				boolean wrapped = false;
				if (!unitBeside) {
					int mark = b.leaves.size();
					wrapped = wrap(ba, office);
					if (!wrapped && b.leaves.size() != mark) return none();
				}
				if (!wrapped) for (int[] dcl : declined) if (dcl != null) closets(ba, dcl[0], dcl[1], hall);
				if (b.fail) return none();
				if (wrapped) return b.finish(stair, hall);
			}
			if (!behind(office ? "office" : "bedroom", new int[][] {mainList(adj[0]), mainList(adj[1])}, -1)) return none();
			return b.fail ? null : b.finish(stair, hall);
		}

		int[][] declined = new int[2][];
		boolean wrapOk;

		/** A beside-the-block unit that is too narrow for a unit: remembered for the wrap-around unit (or closets), nothing built yet. */
		int decline(Band bd, int xa, int xb, int hall, int blockSide) {
			if (wrapOk && blockSide != 0 && bd.a) {
				declined[blockSide > 0 ? 0 : 1] = new int[] {xa, xb};
				return -1;
			}
			closets(bd, xa, xb, hall);
			return -1;
		}

		/** True if a window cell lies in the rectangle (x, z, lw, ld). */
		boolean winIn(int x, int z, int lw, int ld) { return b.window(new Leaf(x, z, lw, ld)); }

		/**
		 * The wrap-around unit: a side strip beside the block. Flats: the 3-deep part by the hall door (6 wide at most) is the entry, the
		 * rest of the strip, beside the back room, is a room of the flat (one or two, each with a window); offices: the strip is one
		 * office or two stacked (cut at 2:1 depth, the second entered from the first). The back room behind the block is the unit's
		 * flat or office, entered from the strip's last room. The other strip, if too narrow too, is an office (towers) or a closet row.
		 */
		boolean wrap(Band ba, boolean office) {
			int D = ba.depth, zA = ba.z0(0, D), z0 = sz1 + 1, bd = d - z0;
			int es = declined[0] != null && declined[1] != null ? r.below(2) : declined[0] != null ? 0 : 1;
			int[] e = declined[es];
			int W = e[1] - e[0];
			int x0 = Math.min(sx0, hx0), x1 = Math.max(sx1, hx1);
			int link;
			if (!office) {
				int f = stripSplit(e, z0, bd);
				String restUse = "flat"; // no window pair beside the back room: the rest of the strip is the flat's kitchen (needs no window), entered from the entry
				if (f < 0) { f = stripSplit(e, z0, bd, false); restUse = "kitchen"; }
				int ed = z0 - 1 - zA;
				if (f < 0 || ed < 2) return false;
				int ew = Math.min(W, 6);
				int ex = es == 0 ? e[0] : e[1] - ew; // the spare cells (strip wider than 6) go to the block side, never to the facade
				int en = b.leaf(ex, zA, ew, Math.min(ed, 3), "entry");
				b.door(hall, en, hallDoorX(ex, ex + ew), ba.line);
				link = b.leaf(e[0], z0, W, f, restUse);
				if (restUse.equals("kitchen")) b.galleys.add(link);
				b.door(en, link, ex + ew / 2, z0 - 1);
				if (f < bd) {
					int r2 = b.leaf(e[0], z0 + f + 1, W, bd - f - 1, restUse);
					if (restUse.equals("kitchen")) b.galleys.add(r2);
					b.door(link, r2, e[0] + (W - 1) / 2, z0 + f);
					link = r2;
				}
			} else {
				link = officeStrip(ba, e);
				if (link < 0) return false;
			}
			int near = backRoom(x0, x1, z0, bd, office ? "office" : "flat", es == 0);
			if (!join(near, new int[] {link}, es == 0 ? blockL : blockR, z0)) return false;
			int os = 1 - es;
			if (declined[os] != null && !(office && officeStrip(ba, declined[os]) >= 0)) closets(ba, declined[os][0], declined[os][1], hall);
			return true;
		}

		/**
		 * Length of the first of one or two stacked rooms over the strip {@code e} from row {@code zTop}, {@code len} rows: the whole strip
		 * if it is at most 2:1 and has a window, else a cut with each part at most 2:1, with a window, the cut row clear of side windows.
		 * -1 if none.
		 */
		int stripSplit(int[] e, int zTop, int len) { return stripSplit(e, zTop, len, true); }

		int stripSplit(int[] e, int zTop, int len, boolean needWin) {
			int W = e[1] - e[0];
			if (W < 3) return -1;
			if (len <= 2 * W && len >= 3 && (!needWin || winIn(e[0], zTop, W, len))) return len;
			if (!needWin && len <= 3 * W && len >= 3) return len; // a galley kitchen
			for (int c = 3; c <= len - 4; c++) {
				int fc = len / 2 + (c % 2 == 0 ? c / 2 : -(c + 1) / 2); // search outwards from the middle
				if (fc < 3 || fc > len - 4 || fc > 2 * W || len - fc - 1 > 2 * W) continue;
				int z = zTop + fc;
				if ((e[0] == 0 && s.reserved()[0][z]) || (e[1] == w && s.reserved()[w - 1][z])) continue;
				if (!needWin || winIn(e[0], zTop, W, fc) && winIn(e[0], zTop + fc + 1, W, len - fc - 1)) return fc;
			}
			return -1;
		}

		/** A narrow side strip of band A as offices with a hall door (see {@link #stripSplit}); the last office's leaf, or -1 with nothing built. */
		int officeStrip(Band ba, int[] e) {
			int D = ba.depth, zA = ba.z0(0, D), W = e[1] - e[0];
			int f = stripSplit(e, zA, D);
			if (f < 0) return -1;
			int link = b.leaf(e[0], zA, W, f, "office");
			b.door(hall, link, hallDoorX(e[0], e[1]), ba.line);
			if (f < D) {
				int o2 = b.leaf(e[0], zA + f + 1, W, D - f - 1, "office");
				b.door(link, o2, e[0] + (W - 1) / 2, zA + f);
				link = o2;
			}
			return link;
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
					int doorway = hx0 + 1; // the shaft's glass doorway looks into the grand room, never at a cut
					if (a > 0 && (a == doorway || !b.lineFree(true, a, 0, depthG))) continue;
					if (c > 0 && (w - c - 1 == doorway || !b.lineFree(true, w - c - 1, 0, depthG))) continue;
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
			b.kind = banquet ? "banquet" : "grand";
			int hw = s.hallZ1() - s.hallZ0();
			int bandB = s.hallZ0() - 1;
			if (bandB < 3) return none();
			String g = s.grandUse();
			int depthG = banquet ? bandB : s.hallZ1();
			String mainUse = banquet ? "banquet" : g;
			int[] ant = anterooms(depthG, banquet ? 30 : 25, !banquet);
			int a = ant[0], c = ant[1];
			if (a > 0 && !b.windowIn(0, a, depthG)) a = 0; // an anteroom needs a window bay of its own (facade bands are fixed per tier)
			if (c > 0 && !banquet && !b.windowIn(w - c, c, depthG)) c = 0;
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
			if (!hasL && !hasR) {
				// no unit stands beside the block (narrow side strips became store rooms): the rooms behind it become a store
				// reached through one of those closets, the one nearest the middle
				int[] l = closetBeside(blockL - 1, true, z0), rr = closetBeside(blockR + 1, false, z0);
				if (l.length == 0 && rr.length == 0) return no();
				boolean left = rr.length == 0 || l.length > 0 && r.below(2) == 0;
				int[] cl = left ? l : rr;
				int room = b.leaf(x0, z0, x1 - x0, bd, "storage");
				return join(room, cl, left ? blockL : blockR, z0);
			}
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

		/** Store rooms whose edge column is {@code col} (their right edge for the left side) and that share rows with the back rows from {@code z0}: candidates for a door through the block's end column. */
		int[] closetBeside(int col, boolean leftSide, int z0) {
			List<Integer> out = new ArrayList<>();
			for (int i = 0; i < b.leaves.size(); i++) {
				Leaf l = b.leaves.get(i);
				if (!b.uses.get(i).equals("storage") || (leftSide ? l.x() + l.w() - 1 : l.x()) != col || l.z() + l.d() <= z0 || l.z() >= d) continue;
				out.add(i);
			}
			return out.stream().mapToInt(Integer::intValue).toArray();
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
			// no door between them: each opens onto the roof by its own door (TowerRoof, B3b)
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
