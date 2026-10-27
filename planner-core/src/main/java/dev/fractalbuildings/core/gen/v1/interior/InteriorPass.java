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
import dev.fractalbuildings.core.ir.Opening;
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
	private record Info(int h, boolean[][] reserved, boolean[][] clear, boolean[][] core, List<int[]> entry, List<int[]> join) {
		boolean same(Info o) {
			return h == o.h && Arrays.deepEquals(reserved, o.reserved) && Arrays.deepEquals(clear, o.clear) && Arrays.deepEquals(core, o.core);
		}
	}

	/** Chance (percent) that a door between two open-plan rooms becomes an archway. */
	static final int ARCH_PERCENT = 40;
	/** Style open percent from which archways are wide (2 to 4). */
	static final int WIDE_FROM = 60;

	/** Chance (percent) that a connection between rooms of these uses is open: kitchen pairs follow the style, other public pairs are at least {@link #ARCH_PERCENT}. */
	static int pct(InteriorStyle st, String a, String b) {
		return a.equals("kitchen") || b.equals("kitchen") ? st.openPercent() : Math.max(ARCH_PERCENT, st.openPercent());
	}

	private record Site(String kind, int leaf, int x, int z, Facing facing) {}

	/** The plan shared by a band of storeys, with room uses and hook sites. */
	private record Band(int start, Info info, FloorPlan plan, String[] uses, List<Site> hooks) {}

	/** A band before the whole-building program has run: plan and uses, no archways or hooks yet. */
	private record Draft(int start, int idx, Info info, FloorPlan plan, RoomProgram.Role[] roles, int[] areas, String[] uses, boolean[] needsBed) {}

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
		FloorPlanner planner = c.type().floors();
		InteriorStyle st = c.style().interior();
		Placed[][] placed = new Placed[ms.size()][g.count()];
		Draft[][] drafts = new Draft[ms.size()][g.count()];
		List<Draft> all = new ArrayList<>();
		for (int i = 0; i < ms.size(); i++) {
			Mass a = ms.get(i);
			Draft cur = null;
			for (int s = a.from(); s < a.to(); s++) {
				if (!a.floorAt(s)) continue;
				Info info = info(c, prog, m, i, a, s);
				if (cur == null || !cur.info().same(info) || drafts[i][s - 1] != cur || prog.newBand(s) || (planner != null && planner.newBand(c, i, a, s))) {
					cur = draft(c, prog, planner, st, i, a, s, info);
					all.add(cur);
				}
				drafts[i][s] = cur;
			}
		}
		program(c, prog, all, drafts, g.count());
		java.util.Map<Draft, Band> done = new java.util.IdentityHashMap<>();
		for (int i = 0; i < ms.size(); i++) {
			Mass a = ms.get(i);
			for (int s = a.from(); s < a.to(); s++) {
				if (drafts[i][s] == null) continue;
				Band cur = done.computeIfAbsent(drafts[i][s], dr -> band(c, prog, st, dr));
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
					stripDoor(c, m, prog, st, ms.get(i), placed[i][s], placed[j][s], sp, s, c.draws("interior/stripArch", i, j, s));
				}
			}
		}
		for (int i = 0; i < ms.size(); i++) {
			for (int s = ms.get(i).from(); s < ms.get(i).to(); s++) if (placed[i][s] != null) hooks(c, g, ms.get(i), s, placed[i][s]);
		}
		for (Core k : m.cores()) {
			if (k.from() == Core.LEG) { // the first flight rises from the open floor under a raised building: it starts outside, in the room that stands on the core's cell on storey 0
				int hi = ms.indexOf(m.interiorContaining(0, k.x(), k.z(), k.sx(), k.sz()));
				flight(c, k, Core.LEG, -c.stilts().lift(), c.stilts().lift(), 0, true, Opening.OUTSIDE, placed[hi][0].idAt(k.x(), k.z()));
			}
			for (int s = Math.max(k.from(), 0); s < k.to() - 1; s++) {
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

	private static Info info(BuildContext c, RoomProgram prog, Massing m, int idx, Mass a, int s) {
		int w = a.sx() - 2, d = a.sz() - 2, ox = a.x() + 1, oz = a.z() + 1;
		boolean[][] reserved = new boolean[w][d], clear = new boolean[w][d], core = new boolean[w][d];
		List<int[]> entry = new ArrayList<>(), join = new ArrayList<>();
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
			for (int k = 0; k < e.width(); k++) { // a wide entrance clears every cell inside it (DS-48: nothing beside or in front of a door)
				int x = f.insideX(e.pos() + k) - ox, z = f.insideZ(e.pos() + k) - oz;
				mark(reserved, x, z);
				mark(clear, x, z);
				entry.add(new int[] {x, z});
			}
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
		for (int[] cell : prog.reserved(c, a, s)) { // cells a door cut later will use: the layout plans around them
			mark(reserved, cell[0] - ox, cell[1] - oz);
			mark(clear, cell[0] - ox, cell[1] - oz);
		}
		for (Mass b : m.masses()) {
			if (b == a) continue;
			int[] sp = Massing.strip(a, b);
			if (sp == null || Math.max(sp[2], sp[3]) < 3 || m.party(a, b) || !a.floorAt(s) || !b.floorAt(s)) continue;
			int[] pos = stripDoorPos(m, a, sp, s);
			int[] in = insideOf(a, sp, pos);
			mark(reserved, in[0] - ox, in[1] - oz);
			mark(clear, in[0] - ox, in[1] - oz);
			join.add(new int[] {in[0] - ox, in[1] - oz});
		}
		return new Info(c.massing().grid().height(s), reserved, clear, core, entry, join);
	}

	/** The cell inside {@code a} next to the strip cell {@code pos}. */
	private static int[] insideOf(Mass a, int[] strip, int[] pos) {
		boolean alongX = strip[3] == 1;
		int x = pos[0], z = pos[1];
		if (alongX) return a.interior(x, z - 1) ? new int[] {x, z - 1} : new int[] {x, z + 1};
		return a.interior(x - 1, z) ? new int[] {x - 1, z} : new int[] {x + 1, z};
	}

	/** Facade cells of mass {@code a} on storey {@code s} that a partition must not end on: the inside cells of window bays (the full width of each window) and entrances. Relative to the mass's interior. */
	public static boolean[][] windowCells(BuildContext c, Mass a, int s) {
		int w = a.sx() - 2, d = a.sz() - 2, ox = a.x() + 1, oz = a.z() + 1;
		boolean[][] out = new boolean[w][d];
		FacadePlan fp = c.facadePlan();
		for (FacadePlan.Layout l : fp.layouts()) {
			Face f = l.face();
			if (!f.mass().equals(a.id()) || f.storey() != s) continue;
			for (int b = 0; b < l.bays().length; b++) {
				String kind = l.kinds().get(b);
				if (FacadePlan.BLANK.equals(kind) || FacadePlan.DOOR.equals(kind)) continue;
				int lo = FacadePlan.WINDOW.equals(kind) ? l.windowStart(b) : l.bayStart(b);
				int hi = FacadePlan.WINDOW.equals(kind) ? lo + l.windowWidth(b) : l.bayStart(b) + l.bays()[b];
				for (int p = lo; p < hi; p++) mark(out, f.insideX(p) - ox, f.insideZ(p) - oz);
			}
		}
		for (FacadePlan.Entrance e : fp.entrances()) {
			Face f = e.face();
			if (!f.mass().equals(a.id()) || f.storey() != s) continue;
			for (int k = 0; k < e.width(); k++) mark(out, f.insideX(e.pos() + k) - ox, f.insideZ(e.pos() + k) - oz);
		}
		return out;
	}

	private static Draft draft(BuildContext c, RoomProgram prog, FloorPlanner planner, InteriorStyle st, int idx, Mass a, int s, Info info) {
		if (planner != null && !a.open()) {
			FloorPlanner.Planned p = planner.plan(c, idx, a, s, info.reserved(), info.core());
			if (p != null) return planned(c, idx, s, info, p);
		}
		int w = a.sx() - 2, d = a.sz() - 2;
		FloorPlan plan = a.open() ? FloorPlan.single(w, d) : FloorPlan.plan(w, d, info.reserved(), info.core(), arrivals(info), st, c.hash("interior/plan", idx, s));
		int n = plan.leaves().size();
		String[] uses = new String[n];
		RoomProgram.Role[] roles = new RoomProgram.Role[n];
		int[] areas = new int[n];
		for (int l = 0; l < n; l++) {
			FloorPlan.Leaf lf = plan.leaves().get(l);
			StoreyGrid g = c.massing().grid();
			RoomProgram.Role role = RoomProgram.Role.ROOM;
			if (a.open()) role = RoomProgram.Role.HALL;
			else if (info.entry().stream().anyMatch(e -> lf.contains(e[0], e[1]))) role = RoomProgram.Role.ENTRY;
			else if (l == plan.corridor()) role = RoomProgram.Role.CORRIDOR;
			else if (hasCore(info, lf)) role = RoomProgram.Role.STAIR;
			else if (n > 1 && doorsOf(plan, l) >= 1 && info.join().stream().anyMatch(e -> lf.contains(e[0], e[1]))) role = RoomProgram.Role.THROUGH; // the way through to another mass is a hall, not someone's room
			roles[l] = role;
			areas[l] = lf.area();
			uses[l] = prog.use(role, a.kind(), lf.area(), new RoomProgram.Where(s, g.count()), c.draws("interior/use", idx, s, l));
		}
		prog.refine(roles, areas, uses, new RoomProgram.Where(s, c.massing().grid().count()), c.draws("interior/refine", idx, s));
		return new Draft(s, idx, info, plan, roles, areas, uses, null);
	}

	/** A draft from the type's own floor planner: its uses are final; roles are corridor, stair (use {@code stairwell}) or room. */
	private static Draft planned(BuildContext c, int idx, int s, Info info, FloorPlanner.Planned p) {
		int n = p.plan().leaves().size();
		if (p.uses().length != n) throw new IllegalStateException("floor planner gave " + p.uses().length + " uses for " + n + " rooms");
		RoomProgram.Role[] roles = new RoomProgram.Role[n];
		int[] areas = new int[n];
		for (int l = 0; l < n; l++) {
			areas[l] = p.plan().leaves().get(l).area();
			roles[l] = l == p.plan().corridor() ? RoomProgram.Role.CORRIDOR : p.uses()[l].equals("stairwell") ? RoomProgram.Role.STAIR : RoomProgram.Role.ROOM;
		}
		return new Draft(s, idx, info, p.plan(), roles, areas, p.uses().clone(), p.needsBed());
	}

	/** The whole-building pass: lets the type revise uses across masses and storeys (a house's one kitchen and living room). */
	private static void program(BuildContext c, RoomProgram prog, List<Draft> all, Draft[][] drafts, int storeys) {
		List<RoomProgram.Slot> slots = new ArrayList<>();
		List<Draft> owner = new ArrayList<>();
		List<Integer> leaf = new ArrayList<>();
		java.util.Map<Draft, Integer> base = new java.util.IdentityHashMap<>();
		for (Draft dr : all) {
			base.put(dr, slots.size());
			for (int l = 0; l < dr.uses().length; l++) {
				slots.add(new RoomProgram.Slot(dr.idx(), dr.start(), span(drafts, dr), dr.roles()[l], dr.areas()[l], dr.uses()[l]));
				owner.add(dr);
				leaf.add(l);
			}
		}
		for (Draft dr : all)
			for (FloorPlan.Door door : dr.plan().doors()) {
				RoomProgram.Slot x = slots.get(base.get(dr) + door.from()), y = slots.get(base.get(dr) + door.to());
				x.next().add(y);
				y.next().add(x);
			}
		prog.refineBuilding(slots, new RoomProgram.Where(0, storeys), c.draws("interior/program"));
		for (int k = 0; k < slots.size(); k++) owner.get(k).uses()[leaf.get(k)] = slots.get(k).use;
	}

	private static int span(Draft[][] drafts, Draft dr) {
		int n = 0;
		for (Draft[] row : drafts) for (Draft x : row) if (x == dr) n++;
		return n;
	}

	private static Band band(BuildContext c, RoomProgram prog, InteriorStyle st, Draft dr) {
		String[] uses = dr.uses();
		int s = dr.start(), idx = dr.idx();
		FloorPlan plan = dr.plan().withArches(prog::openPlan, uses, dr.info().core(), (a, b) -> pct(st, a, b), WIDE_FROM, c.draws("interior/arch", idx, s));
		List<Site> hooks = new ArrayList<>();
		uses = uses.clone();
		for (int l = 0; l < uses.length; l++) uses[l] = hookSites(c, prog, plan, dr.info(), idx, s, l, uses[l], dr.needsBed() != null && dr.needsBed()[l], hooks);
		return new Band(s, dr.info(), plan, uses, hooks);
	}

	/** Where people arrive on this floor of a mass: the entrance cells, else the cells inside the doors from other masses. */
	private static List<int[]> arrivals(Info info) {
		return info.entry().isEmpty() ? info.join() : info.entry(); // a mass with the entrance is walked into from there; the others from the mass they hang off
	}

	/** How many doors and archways of the plan lead into or out of room {@code l}. */
	private static int doorsOf(FloorPlan plan, int l) {
		int n = 0;
		for (FloorPlan.Door d : plan.doors()) if (d.from() == l || d.to() == l) n++;
		return n;
	}

	private static boolean hasCore(Info info, FloorPlan.Leaf lf) {
		for (int x = lf.x(); x < lf.x() + lf.w(); x++) for (int z = lf.z(); z < lf.z() + lf.d(); z++) if (info.core()[x][z]) return true;
		return false;
	}

	/**
	 * Hook cells: against a wall or partition, away from doors, the entrance, strip doors and stair cores, and only where a lane
	 * stays clear to every door, stair and piece ({@link HookLayout}, DS-48). Returns the use the room ends with: a bedroom
	 * with no place for its bed is demoted to storage and gets a chest instead (DS-12 looks for the bedroom elsewhere).
	 */
	private static String hookSites(BuildContext c, RoomProgram prog, FloorPlan plan, Info info, int idx, int s, int l, String use, boolean needsBed, List<Site> out) {
		FloorPlan.Leaf lf = plan.leaves().get(l);
		Draws dr = c.draws("interior/hooks", idx, s, l);
		List<String> kinds = new ArrayList<>(prog.hooks(use, lf.area(), new RoomProgram.Where(s, c.massing().grid().count()), dr));
		if (needsBed) kinds.add(Math.min(1, kinds.size()), "bed"); // a studio sleeps where it lives: the bed comes right after the light
		if (kinds.contains(CEILING_LIGHT)) {
			for (int[] p : ceilingGrid(lf)) out.add(new Site(CEILING_LIGHT, l, p[0], p[1], Facing.NORTH));
			kinds.removeIf(CEILING_LIGHT::equals);
			kinds.removeIf("light"::equals); // a hung light is the room's light; no second one standing on the floor (LAMPS)
		}
		HookLayout.Result r = HookLayout.place(plan, l, use, kinds, info.clear(), info.core(), info.entry(), info.join(), dr);
		boolean bed = r.pieces().stream().anyMatch(p -> p.kind().equals("bed"));
		if (use.equals("bedroom") && !needsBed && !bed && r.missingRequired().contains("bed")) { // too small for a bed and a lane: a store room
			r = HookLayout.place(plan, l, "storage", List.of("chest"), info.clear(), info.core(), info.entry(), info.join(), dr);
			if (r.pieces().isEmpty()) r = HookLayout.place(plan, l, "storage", List.of("chest"), info.clear(), info.core(), info.entry(), info.join(), dr, true); // never an empty store room
			use = "storage";
		}
		for (HookLayout.Piece p : r.pieces()) out.add(new Site(p.kind(), l, p.x(), p.z(), p.facing()));
		return use;
	}

	/** Hook kind hung from the ceiling on a grid inside the room (not against a wall, door exclusion waived: it never blocks). */
	public static final String CEILING_LIGHT = "ceiling_light";
	/** Largest distance between ceiling lights in x or z, and the spacing along a long thin room (a corridor). */
	static final int LIGHT_PITCH = 10, LIGHT_LINE = 8;

	/**
	 * Ceiling light cells of a leaf (plan cells): a room whose short side is 3 or less gets a line along its middle every
	 * {@link #LIGHT_LINE} cells (at least one); other rooms a grid of pitch at most {@link #LIGHT_PITCH}, half a pitch
	 * from the walls, at least one at the centre.
	 */
	static List<int[]> ceilingGrid(FloorPlan.Leaf lf) {
		List<int[]> out = new ArrayList<>();
		boolean thin = Math.min(lf.w(), lf.d()) <= 3;
		boolean alongX = lf.w() >= lf.d();
		int px = thin && alongX ? LIGHT_LINE : LIGHT_PITCH, pz = thin && !alongX ? LIGHT_LINE : LIGHT_PITCH;
		int nx = thin && !alongX ? 1 : Math.max(1, (lf.w() + px - 1) / px);
		int nz = thin && alongX ? 1 : Math.max(1, (lf.d() + pz - 1) / pz);
		for (int i = 0; i < nx; i++)
			for (int j = 0; j < nz; j++) out.add(new int[] {lf.x() + (2 * i + 1) * lf.w() / (2 * nx), lf.z() + (2 * j + 1) * lf.d() / (2 * nz)});
		return out;
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
			int sx = dr.alongZ() ? 1 : dr.span(), sz = dr.alongZ() ? dr.span() : 1;
			Box3 box = Box3.of(p.ox() + dr.x(), y, p.oz() + dr.z(), sx, 2, sz);
			if (dr.arch()) {
				c.box(Role.AIR, Layer.INTERIOR, s, box.x(), box.y(), box.z(), sx, 2, sz);
				if (h >= 3 && dr.span() >= 2) c.box(Role.TRIM, Layer.INTERIOR, s, box.x(), y + 2, box.z(), sx, 1, sz); // lintel
				c.opening(OpeningKind.ARCH, box, dr.facing(), p.ids()[dr.from()], p.ids()[dr.to()]);
				continue;
			}
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

	/** True if a hook (furniture) stands on the cell (x, z) of a placed floor. */
	private static boolean hookAt(Placed p, int x, int z) {
		for (Site h : p.band().hooks()) if (p.ox() + h.x() == x && p.oz() + h.z() == z) return true;
		return false;
	}

	/** The use of the room with id {@code id} on a placed floor. */
	private static String useOf(Placed p, String id) {
		for (int l = 0; l < p.ids().length; l++) if (p.ids()[l].equals(id)) return p.band().uses()[l];
		return "";
	}

	/**
	 * A 1x2 door through the strip, joining the rooms on its two sides, or, between two public rooms and by the
	 * style's open percent, a doorless archway 1 to 3 cells wide (2 to 4 in open styles, as far as the strip and
	 * the rooms on both sides allow, with a pier of wall left at each end).
	 */
	private static void stripDoor(BuildContext c, Massing m, RoomProgram prog, InteriorStyle st, Mass a, Placed pa, Placed pb, int[] st4, int s, Draws dr) {
		int[] pos = stripDoorPos(m, a, st4, s);
		boolean alongX = st4[3] == 1;
		int x = pos[0], z = pos[1], y = m.grid().y(s) + 1;
		int[] in = insideOf(a, st4, pos);
		int ox = alongX ? x : 2 * x - in[0], oz = alongX ? 2 * z - in[1] : z; // the cell on the other side
		String ra = pa.idAt(in[0], in[1]), rb = pb.idAt(ox, oz);
		if (ra == null || rb == null) throw new IllegalStateException("strip door at " + x + "," + z + " opens into a partition");
		Facing f = alongX ? (in[1] < z ? Facing.SOUTH : Facing.NORTH) : (in[0] < x ? Facing.EAST : Facing.WEST);
		String ua = useOf(pa, ra), ub = useOf(pb, rb);
		if (prog.openPlan(ua) && prog.openPlan(ub) && dr.chance(pct(st, ua, ub), 100)) {
			int want = pct(st, ua, ub) >= WIDE_FROM ? 2 + dr.below(3) : 1 + dr.below(3);
			int lo = 0, hi = 0; // offsets along the strip from the door cell
			int stripLo = (alongX ? st4[0] : st4[1]) + 1, stripHi = (alongX ? st4[0] : st4[1]) + Math.max(st4[2], st4[3]) - 2;
			while (hi - lo + 1 < want) {
				boolean up = (hi - lo) % 2 == 0;
				int k = up ? hi + 1 : lo - 1;
				int sx = alongX ? x + k : x, sz = alongX ? z : z + k, p = alongX ? sx : sz;
				if (p < stripLo || p > stripHi) break;
				int ix = alongX ? in[0] + k : in[0], iz = alongX ? in[1] : in[1] + k, jx = alongX ? ox + k : ox, jz = alongX ? oz : oz + k;
				if (m.inCore(s, sx, sz) || m.inCore(s, ix, iz) || m.inCore(s, jx, jz) || !ra.equals(pa.idAt(ix, iz)) || !rb.equals(pb.idAt(jx, jz)) || hookAt(pa, ix, iz) || hookAt(pb, jx, jz)) break; // no furniture in the way
				if (up) hi = k; else lo = k;
			}
			int n = hi - lo + 1, bx = alongX ? x + lo : x, bz = alongX ? z : z + lo;
			Box3 box = Box3.of(bx, y, bz, alongX ? n : 1, 2, alongX ? 1 : n);
			c.box(Role.AIR, Layer.INTERIOR, s, box.x(), box.y(), box.z(), box.sx(), 2, box.sz());
			if (n >= 2 && m.grid().height(s) >= 4) c.box(Role.TRIM, Layer.INTERIOR, s, box.x(), y + 2, box.z(), box.sx(), 1, box.sz()); // lintel
			c.opening(OpeningKind.ARCH, box, f, ra, rb);
			return;
		}
		c.box(Role.DOOR, Layer.INTERIOR, s, x, y, z, 1, 2, 1);
		c.opening(OpeningKind.DOOR, Box3.of(x, y, z, 1, 2, 1), f, ra, rb);
	}

	/**
	 * The flight from storey s to s + 1 in core k: a straight run of h solid step columns (the last one in the slab layer, flush with the next floor; it is left sideways, onto the landing of the next flight)
	 * (h = the storey's height), even flights (counted from the core's first storey) on the core's back
	 * row (larger z, or larger x for a core along z) ascending towards +x (+z), odd flights on the front
	 * row ascending the other way, so each flight stands on an intact slab. The slab above the steps
	 * is cut. Plan "Core contract" has the walk-through.
	 */
	public static void flight(BuildContext c, StoreyGrid g, Core k, int s, String from, String to) {
		flight(c, k, s, g.y(s), g.height(s), g.y(s + 1), false, from, to);
	}

	/**
	 * The flight from {@code y} (the slab it stands on) up {@code h} blocks to {@code top} (the next floor's slab). {@code fromGround}: the
	 * columns start at the slab layer itself, with no slab under them (the first flight of a building on posts, from the open floor,
	 * whose local y is below 0 until the stilts lift the building).
	 */
	static void flight(BuildContext c, Core k, int s, int y, int h, int top, boolean fromGround, String from, String to) {
		boolean even = (s - k.from()) % 2 == 0;
		int y1 = fromGround ? y : y + 1; // lowest block of a column
		boolean ax = k.alongX();
		int len = ax ? k.sx() : k.sz();
		int base = ax ? k.x() : k.z();
		int row = ax ? (even ? k.maxZ() - 1 : k.z()) : (even ? k.maxX() - 1 : k.x());
		int first = Integer.MAX_VALUE, cut = Integer.MAX_VALUE; // lowest column of the run, and of the steps under the slab opening
		for (int i = 0; i <= h - 1; i++) {
			int col = fromGround ? base + len - h + i : even ? base + 1 + i : base + len - 2 - i; // a flight from the open floor ends on the core's last column, where the flights above begin their landing
			first = Math.min(first, col);
			if (i <= h - 2) cut = Math.min(cut, col);
			Layer layer = fromGround ? Layer.SHELL : Layer.INTERIOR; // the first flight of a market hall stands in the open, in view
			int st = fromGround ? -1 : s;
			if (ax) c.box(Role.STAIR, layer, st, col, y1, row, 1, i + 1 + y + 1 - y1, 1);
			else c.box(Role.STAIR, layer, st, row, y1, col, 1, i + 1 + y + 1 - y1, 1);
		}
		// The last step is the slab level itself (its top is flush with the next floor), and the walker leaves it sideways onto the
		// landing of the next flight; the slab is cut only over the other h - 1 steps, whose headroom needs it.
		int cutStorey = fromGround ? 0 : s; // the slab it cuts is the first floor of the raised building
		if (ax) c.box(Role.AIR, Layer.INTERIOR, cutStorey, cut, top, row, h - 1, 1, 1);
		else c.box(Role.AIR, Layer.INTERIOR, cutStorey, row, top, cut, 1, 1, h - 1);
		Facing f = ax ? (even ? Facing.EAST : Facing.WEST) : (even ? Facing.SOUTH : Facing.NORTH);
		Box3 run = ax ? Box3.of(first, y + 1, row, h, h, 1) : Box3.of(row, y + 1, first, 1, h, h);
		c.opening(OpeningKind.STAIR, run, f, from, to);
	}
}
