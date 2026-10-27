package dev.fractalbuildings.core.gen.v1.interior;

import dev.fractalbuildings.core.check.Walkability;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lane-aware furniture placement for one room (DS-48, ROOMWALK): a hook goes on a wall-ring cell, away from doors and stair
 * cores, and only where the floor left over still joins every door inside cell, entry cell and stair-side cell and the use
 * cell of every piece placed so far ({@link Walkability}). Required pieces go first; a piece that has nowhere to go is
 * dropped (an optional one) or reported as {@code missing} (a required one, which the caller may demote the room for).
 * A bed has its head against a wall and reaches into the room; its hook is the foot cell and its facing points at the head,
 * which is how the mod places it.
 */
final class HookLayout {
	private HookLayout() {}

	/** One piece: the hook cell (a bed's foot), its facing, and the second cell (a bed's head) or {@code null}. */
	record Piece(String kind, int x, int z, Facing facing, int[] head) {}

	/** What was placed, and the kinds that did not fit (optional ones and required ones apart). */
	record Result(List<Piece> pieces, List<String> droppedOptional, List<String> missingRequired) {}

	static Result place(FloorPlan plan, int l, String use, List<String> kinds, boolean[][] clear, boolean[][] core, List<int[]> entry, List<int[]> join, Draws dr) { return place(plan, l, use, kinds, clear, core, entry, join, dr, false); }

	/** {@code relaxed}: a piece may stand next to (not on) a clear cell, for a room that would otherwise be left empty. */
	static Result place(FloorPlan plan, int l, String use, List<String> kinds, boolean[][] clear, boolean[][] core, List<int[]> entry, List<int[]> join, Draws dr, boolean relaxed) {
		FloorPlan.Leaf lf = plan.leaves().get(l);
		int w = plan.w(), d = plan.d();
		boolean[][] base = new boolean[w][d]; // blocked by the room's own shape
		for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) base[x][z] = plan.leafAt()[x][z] != l || core[x][z];
		boolean[][] doorNear = new boolean[w][d]; // within one cell (also diagonally) of a door or arch
		List<int[]> targets = new ArrayList<>();
		for (FloorPlan.Door dd : plan.doors())
			for (int k = 0; k < dd.span(); k++) {
				int cx = dd.cellX(k), cz = dd.cellZ(k);
				for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) if (x >= 0 && z >= 0 && x < w && z < d) doorNear[x][z] = true;
				if (dd.from() != l && dd.to() != l) continue;
				for (Facing f : Facing.values()) {
					int nx = cx + f.dx, nz = cz + f.dz;
					if (nx >= 0 && nz >= 0 && nx < w && nz < d && plan.leafAt()[nx][nz] == l) targets.add(new int[] {nx, nz});
				}
			}
		for (int x = lf.x(); x < lf.x() + lf.w(); x++)
			for (int z = lf.z(); z < lf.z() + lf.d(); z++) if (clear[x][z] && !core[x][z]) targets.add(new int[] {x, z});
		boolean wide = Walkability.wide(use, lf.w(), lf.d());
		targets = Walkability.connected(base, targets, wide);

		List<String> order = new ArrayList<>(kinds);
		order.sort(Comparator.comparing(Walkability::optional)); // stable: required first, then optional, each in the program's order
		List<Piece> placed = new ArrayList<>();
		List<String> dropped = new ArrayList<>(), missing = new ArrayList<>();
		boolean[][] banned = new boolean[w][d]; // cells too close to a placed piece for the next one (one cell apart)
		boolean narrow = Math.min(lf.w(), lf.d()) == 3;
		Facing furnished = null, endWall = null; // narrow room: the long wall the furniture goes on, and the end wall away from the door
		if (narrow) {
			boolean alongX = lf.w() >= lf.d();
			furnished = (dr.below(2) == 0) ? (alongX ? Facing.NORTH : Facing.WEST) : (alongX ? Facing.SOUTH : Facing.EAST);
			Facing lo = alongX ? Facing.WEST : Facing.NORTH, hi = alongX ? Facing.EAST : Facing.SOUTH;
			double dl = 0, dh = 0;
			for (int[] t : targets) { double p = alongX ? t[0] - lf.x() : t[1] - lf.z(); dl += p; dh += (alongX ? lf.w() : lf.d()) - 1 - p; }
			endWall = dl >= dh ? hi : lo; // doors near the low end: the high end wall is away from them
		}
		for (String kind : order) {
			List<Piece> cands = candidates(kind, plan, l, lf, clear, doorNear, banned, placed, w, d, relaxed);
			List<Piece> pref = new ArrayList<>(), rest = new ArrayList<>();
			for (Piece p : cands) {
				Facing wall = p.head() != null ? p.facing() : p.facing().rotate(2);
				if (!narrow || wall == furnished || wall == endWall) pref.add(p); else rest.add(p);
			}
			Piece chosen = pick(pref, dr, base, targets, placed, wide, plan, l);
			if (chosen == null) chosen = pick(rest, dr, base, targets, placed, wide, plan, l);
			if (chosen == null) { (Walkability.optional(kind) ? dropped : missing).add(kind); continue; }
			placed.add(chosen);
			for (int x = chosen.x() - 1; x <= chosen.x() + 1; x++) for (int z = chosen.z() - 1; z <= chosen.z() + 1; z++) if (x >= 0 && z >= 0 && x < w && z < d) banned[x][z] = true;
			if (chosen.head() != null)
				for (int x = chosen.head()[0] - 1; x <= chosen.head()[0] + 1; x++) for (int z = chosen.head()[1] - 1; z <= chosen.head()[1] + 1; z++) if (x >= 0 && z >= 0 && x < w && z < d) banned[x][z] = true;
		}
		return new Result(placed, dropped, missing);
	}

	/** Wall-ring placements of one kind, in a fixed order: cells not near a door, entrance, stair core or an earlier piece. */
	private static List<Piece> candidates(String kind, FloorPlan plan, int l, FloorPlan.Leaf lf, boolean[][] clear, boolean[][] doorNear, boolean[][] banned, List<Piece> placed, int w, int d, boolean relaxed) {
		List<Piece> out = new ArrayList<>();
		Facing[] fs = Facing.values();
		for (int x = lf.x(); x < lf.x() + lf.w(); x++)
			for (int z = lf.z(); z < lf.z() + lf.d(); z++) {
				if (!free(x, z, clear, doorNear, banned, w, d, relaxed)) continue;
				for (Facing f : fs) { // f: the direction of the wall the cell stands against
					int nx = x + f.dx, nz = z + f.dz;
					if (nx >= 0 && nz >= 0 && nx < w && nz < d && plan.leafAt()[nx][nz] == l) continue; // not a wall
					if (kind.equals("bed")) { // head here against the wall, foot one cell into the room: the hook is the foot and faces the head
						int fx = x - f.dx, fz = z - f.dz;
						if (fx < 0 || fz < 0 || fx >= w || fz >= d || plan.leafAt()[fx][fz] != l || !free(fx, fz, clear, doorNear, banned, w, d, relaxed)) continue;
						out.add(new Piece(kind, fx, fz, f, new int[] {x, z}));
					} else out.add(new Piece(kind, x, z, f.rotate(2), null));
				}
			}
		return out;
	}

	private static boolean free(int x, int z, boolean[][] clear, boolean[][] doorNear, boolean[][] banned, int w, int d, boolean relaxed) {
		if (banned[x][z] || doorNear[x][z]) return false;
		if (relaxed) return !clear[x][z];
		for (int i = Math.max(0, x - 1); i <= Math.min(w - 1, x + 1); i++) for (int j = Math.max(0, z - 1); j <= Math.min(d - 1, z + 1); j++) if (clear[i][j]) return false;
		return true;
	}

	/** The first candidate (from a random start, going round) whose placement keeps the lane, or null. */
	private static Piece pick(List<Piece> cands, Draws dr, boolean[][] base, List<int[]> targets, List<Piece> placed, boolean wide, FloorPlan plan, int l) {
		if (cands.isEmpty()) return null;
		int start = dr.below(cands.size());
		for (int i = 0; i < cands.size(); i++) {
			Piece p = cands.get((start + i) % cands.size());
			if (walkable(base, targets, placed, p, wide, plan, l)) return p;
		}
		return null;
	}

	/** True if, with {@code add} placed as well, the lane joins every target and every piece's use cell. */
	static boolean walkable(boolean[][] base, List<int[]> targets, List<Piece> placed, Piece add, boolean wide, FloorPlan plan, int l) {
		int w = base.length, d = base[0].length;
		boolean[][] blocked = new boolean[w][d];
		for (int x = 0; x < w; x++) blocked[x] = base[x].clone();
		List<int[]> tg = new ArrayList<>(targets);
		List<List<int[]>> groups = new ArrayList<>();
		List<Piece> all = new ArrayList<>(placed);
		all.add(add);
		for (Piece p : all) {
			blocked[p.x()][p.z()] = true;
			if (p.head() != null) blocked[p.head()[0]][p.head()[1]] = true;
		}
		for (Piece p : all) {
			if (p.head() != null) {
				List<int[]> sides = new ArrayList<>();
				for (Facing f : new Facing[] {p.facing().rotate(1), p.facing().rotate(3)}) {
					sides.add(new int[] {p.x() + f.dx, p.z() + f.dz});
					sides.add(new int[] {p.head()[0] + f.dx, p.head()[1] + f.dz});
				}
				groups.add(sides);
			} else if (Walkability.hasUseCell(p.kind())) {
				int ux = p.x() + p.facing().dx, uz = p.z() + p.facing().dz;
				if (ux < 0 || uz < 0 || ux >= w || uz >= d || plan.leafAt()[ux][uz] != l) return false;
				tg.add(new int[] {ux, uz});
			}
		}
		return Walkability.cutOff(blocked, tg, groups, wide) == 0;
	}
}
