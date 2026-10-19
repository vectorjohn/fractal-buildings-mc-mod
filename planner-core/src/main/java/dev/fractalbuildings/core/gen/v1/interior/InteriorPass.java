package dev.fractalbuildings.core.gen.v1.interior;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.style.InteriorStyle;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Interiors (owned by the interiors step 4.5): per mass and band of identical storeys, a
 * {@link FloorPlan} (rooms by recursive splitting around the stair cores, partitions, doors forming a
 * spanning tree plus loops), replicated up the band with a y shift; a door through every wall strip
 * two masses share on a common floor; a stair flight in every core ({@link #flight}); furnishing
 * hooks from the type's {@link RoomProgram}. Two storeys of a mass are one band when their height and
 * everything that constrains the plan (window inside cells, entrance, strip doors, cores) are equal,
 * so typical floors repeat and a 60-storey tower plans a handful of floors. Rooms are created first,
 * in mass, storey, room order, so room ids are stable.
 */
public final class InteriorPass {
	private InteriorPass() {}

	/** What constrains the plan of one mass on one storey; cells relative to the mass's interior. */
	private record Info(int h, boolean[][] reserved, boolean[][] clear, boolean[][] core, List<int[]> entry) {
		boolean same(Info o) {
			return h == o.h && Arrays.deepEquals(reserved, o.reserved) && Arrays.deepEquals(clear, o.clear) && Arrays.deepEquals(core, o.core);
		}
	}

	private record Site(String kind, int leaf, int x, int z, Facing facing) {}

	/** The plan shared by a band of storeys, with room uses and hook sites. */
	private record Band(int start, Info info, FloorPlan plan, String[] uses, List<Site> hooks) {}

	/** A band's plan placed on one storey of one mass. */
	private record Placed(Band band, String[] ids, int ox, int oz) {
		String idAt(int x, int z) {
			int rx = x - ox, rz = z - oz;
			if (rx < 0 || rz < 0 || rx >= band.plan().w() || rz >= band.plan().d()) return null;
			int leaf = band.plan().leafAt()[rx][rz];
			return leaf < 0 ? null : ids[leaf];
		}
	}

	public static void run(BuildContext c) {
		Massing m = c.massing();
		StoreyGrid g = m.grid();
		List<Mass> ms = m.masses();
		RoomProgram prog = c.type().rooms();
		InteriorStyle st = c.style().interior();
		Placed[][] placed = new Placed[ms.size()][g.count()];
		Band[][] bands = new Band[ms.size()][g.count()];
		for (int i = 0; i < ms.size(); i++) {
			Mass a = ms.get(i);
			Band cur = null;
			for (int s = a.from(); s < a.to(); s++) {
				if (!a.floorAt(s)) continue;
				Info info = info(c, m, i, a, s);
				if (cur == null || !cur.info().same(info) || bands[i][s - 1] != cur || prog.newBand(s)) cur = band(c, prog, st, i, a, s, info);
				bands[i][s] = cur;
				int top = a.open() ? g.y(a.to()) : g.y(s + 1);
				String[] ids = new String[cur.plan().leaves().size()];
				for (int l = 0; l < ids.length; l++) {
					FloorPlan.Leaf lf = cur.plan().leaves().get(l);
					ids[l] = c.room(s, a.id(), List.of(Box3.of(a.x() + 1 + lf.x(), g.y(s) + 1, a.z() + 1 + lf.z(), lf.w(), top - g.y(s) - 1, lf.d())), cur.uses()[l]);
				}
				placed[i][s] = new Placed(cur, ids, a.x() + 1, a.z() + 1);
			}
		}
		for (int i = 0; i < ms.size(); i++) {
			for (int s = ms.get(i).from(); s < ms.get(i).to(); s++) if (placed[i][s] != null) partitions(c, g, ms.get(i), s, placed[i][s]);
		}
		for (int i = 0; i < ms.size(); i++) {
			for (int j = i + 1; j < ms.size(); j++) {
				int[] sp = Massing.strip(ms.get(i), ms.get(j));
				if (sp == null || Math.max(sp[2], sp[3]) < 3 || m.party(ms.get(i), ms.get(j))) continue;
				for (int s = 0; s < g.count(); s++) {
					if (placed[i][s] == null || placed[j][s] == null) continue;
					stripDoor(c, m, ms.get(i), placed[i][s], placed[j][s], sp, s);
				}
			}
		}
		for (int i = 0; i < ms.size(); i++) {
			for (int s = ms.get(i).from(); s < ms.get(i).to(); s++) if (placed[i][s] != null) hooks(c, g, ms.get(i), s, placed[i][s]);
		}
		for (Core k : m.cores()) {
			for (int s = k.from(); s < k.to() - 1; s++) {
				int lo = ms.indexOf(m.interiorContaining(s, k.x(), k.z(), k.sx(), k.sz()));
				int hi = ms.indexOf(m.interiorContaining(s + 1, k.x(), k.z(), k.sx(), k.sz()));
				flight(c, g, k, s, placed[lo][s].idAt(k.x(), k.z()), placed[hi][s + 1].idAt(k.x(), k.z()));
			}
		}
	}

	// ---- plan inputs ----

	private static void mark(boolean[][] grid, int x, int z) {
		if (x >= 0 && z >= 0 && x < grid.length && z < grid[0].length) grid[x][z] = true;
	}

	private static Info info(BuildContext c, Massing m, int idx, Mass a, int s) {
		int w = a.sx() - 2, d = a.sz() - 2, ox = a.x() + 1, oz = a.z() + 1;
		boolean[][] reserved = new boolean[w][d], clear = new boolean[w][d], core = new boolean[w][d];
		List<int[]> entry = new ArrayList<>();
		FacadePlan fp = c.facadePlan();
		for (FacadePlan.Layout l : fp.layouts()) {
			Face f = l.face();
			if (!f.mass().equals(a.id()) || f.storey() != s) continue;
			for (int b = 0; b < l.bays().length; b++) {
				String kind = l.kinds().get(b);
				if (FacadePlan.BLANK.equals(kind) || FacadePlan.DOOR.equals(kind)) continue;
				int lo = FacadePlan.WINDOW.equals(kind) ? l.centre(b) : l.bayStart(b);
				int hi = FacadePlan.WINDOW.equals(kind) ? lo + 1 : l.bayStart(b) + l.bays()[b];
				for (int p = lo; p < hi; p++) mark(reserved, f.insideX(p) - ox, f.insideZ(p) - oz);
			}
		}
		for (FacadePlan.Entrance e : fp.entrances()) {
			Face f = e.face();
			if (!f.mass().equals(a.id()) || f.storey() != s) continue;
			int x = f.insideX(e.pos()) - ox, z = f.insideZ(e.pos()) - oz;
			mark(reserved, x, z);
			mark(clear, x, z);
			entry.add(new int[] {x, z});
		}
		for (Core k : m.cores()) {
			if (s < k.from() || s >= k.to() || m.interiorContaining(s, k.x(), k.z(), k.sx(), k.sz()) != a) continue;
			for (int x = k.x() - 1; x <= k.maxX(); x++)
				for (int z = k.z() - 1; z <= k.maxZ(); z++) {
					mark(reserved, x - ox, z - oz);
					mark(clear, x - ox, z - oz);
					if (k.contains(x, z)) mark(core, x - ox, z - oz);
				}
		}
		for (Mass b : m.masses()) {
			if (b == a) continue;
			int[] sp = Massing.strip(a, b);
			if (sp == null || Math.max(sp[2], sp[3]) < 3 || m.party(a, b) || !a.floorAt(s) || !b.floorAt(s)) continue;
			int[] pos = stripDoorPos(m, a, sp, s);
			int[] in = insideOf(a, sp, pos);
			mark(reserved, in[0] - ox, in[1] - oz);
			mark(clear, in[0] - ox, in[1] - oz);
		}
		return new Info(c.massing().grid().height(s), reserved, clear, core, entry);
	}

	/** The cell inside {@code a} next to the strip cell {@code pos}. */
	private static int[] insideOf(Mass a, int[] strip, int[] pos) {
		boolean alongX = strip[3] == 1;
		int x = pos[0], z = pos[1];
		if (alongX) return a.interior(x, z - 1) ? new int[] {x, z - 1} : new int[] {x, z + 1};
		return a.interior(x - 1, z) ? new int[] {x - 1, z} : new int[] {x + 1, z};
	}

	private static Band band(BuildContext c, RoomProgram prog, InteriorStyle st, int idx, Mass a, int s, Info info) {
		int w = a.sx() - 2, d = a.sz() - 2;
		FloorPlan plan = a.open() ? FloorPlan.single(w, d) : FloorPlan.plan(w, d, info.reserved(), info.core(), st, c.hash("interior/plan", idx, s));
		int n = plan.leaves().size();
		String[] uses = new String[n];
		for (int l = 0; l < n; l++) {
			FloorPlan.Leaf lf = plan.leaves().get(l);
			StoreyGrid g = c.massing().grid();
			RoomProgram.Role role = RoomProgram.Role.ROOM;
			if (a.open()) role = RoomProgram.Role.HALL;
			else if (info.entry().stream().anyMatch(e -> lf.contains(e[0], e[1]))) role = RoomProgram.Role.ENTRY;
			else if (l == plan.corridor()) role = RoomProgram.Role.CORRIDOR;
			else if (hasCore(info, lf)) role = RoomProgram.Role.STAIR;
			uses[l] = prog.use(role, lf.area(), new RoomProgram.Where(s, g.count()), c.draws("interior/use", idx, s, l));
		}
		List<Site> hooks = new ArrayList<>();
		for (int l = 0; l < n; l++) hookSites(c, prog, plan, info, idx, s, l, uses[l], hooks);
		return new Band(s, info, plan, uses, hooks);
	}

	private static boolean hasCore(Info info, FloorPlan.Leaf lf) {
		for (int x = lf.x(); x < lf.x() + lf.w(); x++) for (int z = lf.z(); z < lf.z() + lf.d(); z++) if (info.core()[x][z]) return true;
		return false;
	}

	/** Hook cells: against a wall or partition, away from doors, the entrance, strip doors and stair cores. */
	private static void hookSites(BuildContext c, RoomProgram prog, FloorPlan plan, Info info, int idx, int s, int l, String use, List<Site> out) {
		FloorPlan.Leaf lf = plan.leaves().get(l);
		Draws dr = c.draws("interior/hooks", idx, s, l);
		List<String> kinds = prog.hooks(use, lf.area(), new RoomProgram.Where(s, c.massing().grid().count()), dr);
		List<int[]> cand = new ArrayList<>(); // {x, z, wall direction}
		for (int x = lf.x(); x < lf.x() + lf.w(); x++)
			for (int z = lf.z(); z < lf.z() + lf.d(); z++) {
				if (near(info.clear(), x, z, plan.w(), plan.d())) continue;
				boolean door = false;
				for (FloorPlan.Door dd : plan.doors()) if (Math.abs(dd.x() - x) <= 1 && Math.abs(dd.z() - z) <= 1) door = true;
				if (door) continue;
				Facing[] fs = Facing.values();
				for (int f = 0; f < 4; f++) {
					int nx = x + fs[f].dx, nz = z + fs[f].dz;
					if (nx < 0 || nz < 0 || nx >= plan.w() || nz >= plan.d() || plan.leafAt()[nx][nz] < 0) {
						cand.add(new int[] {x, z, f});
						break;
					}
				}
			}
		for (String kind : kinds) {
			if (cand.isEmpty()) break;
			int[] p = cand.get(dr.below(cand.size()));
			out.add(new Site(kind, l, p[0], p[1], Facing.values()[p[2]].rotate(2)));
			cand.removeIf(q -> Math.abs(q[0] - p[0]) <= 1 && Math.abs(q[1] - p[1]) <= 1);
		}
	}

	/** True if a flagged cell lies within one cell (also diagonally) of (x, z). */
	private static boolean near(boolean[][] flags, int x, int z, int w, int d) {
		for (int i = Math.max(0, x - 1); i <= Math.min(w - 1, x + 1); i++) for (int j = Math.max(0, z - 1); j <= Math.min(d - 1, z + 1); j++) if (flags[i][j]) return true;
		return false;
	}

	// ---- emission ----

	private static void partitions(BuildContext c, StoreyGrid g, Mass a, int s, Placed p) {
		FloorPlan plan = p.band().plan();
		int y = g.y(s) + 1, h = g.height(s) - 1;
		for (FloorPlan.Cut cut : plan.cuts()) {
			if (cut.constX()) c.box(Role.WALL, Layer.INTERIOR, s, p.ox() + cut.c(), y, p.oz() + cut.lo(), 1, h, cut.len());
			else c.box(Role.WALL, Layer.INTERIOR, s, p.ox() + cut.lo(), y, p.oz() + cut.c(), cut.len(), h, 1);
		}
		for (FloorPlan.Door dr : plan.doors()) {
			Box3 box = Box3.of(p.ox() + dr.x(), y, p.oz() + dr.z(), 1, 2, 1);
			c.box(Role.DOOR, Layer.INTERIOR, s, box.x(), box.y(), box.z(), 1, 2, 1);
			c.opening(OpeningKind.DOOR, box, dr.facing(), p.ids()[dr.from()], p.ids()[dr.to()]);
		}
	}

	private static void hooks(BuildContext c, StoreyGrid g, Mass a, int s, Placed p) {
		for (Site h : p.band().hooks()) c.hook(h.kind(), p.ids()[h.leaf()], p.ox() + h.x(), g.y(s) + 1, p.oz() + h.z(), h.facing());
	}

	/** The strip cell where the door goes: nearest the strip's middle whose two sides are not in a core. */
	private static int[] stripDoorPos(Massing m, Mass a, int[] st, int s) {
		boolean alongX = st[3] == 1; // strip thickness in z: runs along x
		int lo = alongX ? st[0] : st[1], len = alongX ? st[2] : st[3];
		int best = -1;
		for (int p = lo + 1; p < lo + len - 1; p++) {
			int x = alongX ? p : st[0], z = alongX ? st[1] : p;
			int ax = alongX ? x : x - 1, az = alongX ? z - 1 : z;
			int bx = alongX ? x : x + 1, bz = alongX ? z + 1 : z;
			if (m.inCore(s, ax, az) || m.inCore(s, bx, bz)) continue;
			if (best < 0 || Math.abs(2 * p - (2 * lo + len - 1)) < Math.abs(2 * best - (2 * lo + len - 1))) best = p;
		}
		if (best < 0) throw new IllegalStateException("no door position in the strip of " + a.id() + " on storey " + s);
		return alongX ? new int[] {best, st[1]} : new int[] {st[0], best};
	}

	/** A 1x2 door through the strip, joining the rooms on its two sides. */
	private static void stripDoor(BuildContext c, Massing m, Mass a, Placed pa, Placed pb, int[] st, int s) {
		int[] pos = stripDoorPos(m, a, st, s);
		boolean alongX = st[3] == 1;
		int x = pos[0], z = pos[1], y = m.grid().y(s) + 1;
		int[] in = insideOf(a, st, pos);
		int ox = alongX ? x : 2 * x - in[0], oz = alongX ? 2 * z - in[1] : z; // the cell on the other side
		String ra = pa.idAt(in[0], in[1]), rb = pb.idAt(ox, oz);
		if (ra == null || rb == null) throw new IllegalStateException("strip door at " + x + "," + z + " opens into a partition");
		Facing f = alongX ? (in[1] < z ? Facing.SOUTH : Facing.NORTH) : (in[0] < x ? Facing.EAST : Facing.WEST);
		c.box(Role.DOOR, Layer.INTERIOR, s, x, y, z, 1, 2, 1);
		c.opening(OpeningKind.DOOR, Box3.of(x, y, z, 1, 2, 1), f, ra, rb);
	}

	/**
	 * The flight from storey s to s + 1 in core k: a straight run of h - 1 solid step columns
	 * (h = the storey's height), even flights (counted from the core's first storey) on the core's back
	 * row (larger z, or larger x for a core along z) ascending towards +x (+z), odd flights on the front
	 * row ascending the other way, so each flight stands on an intact slab. The slab above the steps
	 * is cut. Plan "Core contract" has the walk-through.
	 */
	public static void flight(BuildContext c, StoreyGrid g, Core k, int s, String from, String to) {
		int h = g.height(s), y = g.y(s);
		boolean even = (s - k.from()) % 2 == 0;
		boolean ax = k.alongX();
		int len = ax ? k.sx() : k.sz();
		int base = ax ? k.x() : k.z();
		int row = ax ? (even ? k.maxZ() - 1 : k.z()) : (even ? k.maxX() - 1 : k.x());
		int first = Integer.MAX_VALUE;
		for (int i = 0; i <= h - 2; i++) {
			int col = even ? base + 1 + i : base + len - 2 - i;
			first = Math.min(first, col);
			if (ax) c.box(Role.STAIR, Layer.INTERIOR, s, col, y + 1, row, 1, i + 1, 1);
			else c.box(Role.STAIR, Layer.INTERIOR, s, row, y + 1, col, 1, i + 1, 1);
		}
		int top = g.y(s + 1);
		if (ax) c.box(Role.AIR, Layer.INTERIOR, s, first, top, row, h - 1, 1, 1);
		else c.box(Role.AIR, Layer.INTERIOR, s, row, top, first, 1, 1, h - 1);
		Facing f = ax ? (even ? Facing.EAST : Facing.WEST) : (even ? Facing.SOUTH : Facing.NORTH);
		Box3 run = ax ? Box3.of(first, y + 1, row, h - 1, h - 1, 1) : Box3.of(row, y + 1, first, 1, h - 1, h - 1);
		c.opening(OpeningKind.STAIR, run, f, from, to);
	}
}
