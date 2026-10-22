package dev.fractalbuildings.core.gen.v1.types.wall;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.facade.OpeningPass;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.mass.MassingOps;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Faces;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.roof.RoofPass;
import dev.fractalbuildings.core.gen.v1.style.RoofStyle;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * A town-wall gatehouse (the other ring lots are {@link CurtainType} walls and {@link BastionType} corner towers; the walk joining them is {@link WalkOps}). The wall runs along the lot's front (local x); the field is
 * to the north (the front), the town to the south. A gatehouse (two towers and a passage between
 * them) stands in the segment and curtain walls run out from its towers to the lot's ends.
 *
 * <ul>
 *   <li>The <em>passage</em> is one open room, 5 wide (3 inside), one storey (5 high), with a gate 3
 *       wide and 4 high in its north wall (field side) and another in its south wall (town side). The
 *       main entrance is the north one; the town gate is a second exterior door of the same room.
 *   <li>Above the passage a <em>bridge</em> room joins the two towers on the upper storeys.
 *   <li>Each <em>tower</em> is 10 to 12 wide, the lot's depth, 2 or 3 storeys, with a stair core
 *       through every storey; the ground storey joins the passage and the curtain by doors.
 *   <li>A <em>curtain</em> is one open room (a gallery 1 to 3 wide inside) a storey high. Its flat
 *       roof is the wall walk; a door from the first upper storey of the tower opens onto it.
 *   <li>Every flat roof has a crenellated parapet (2 high, every second block removed).
 * </ul>
 *
 * Both gates are DOOR-role boxes (closed gate leaves), 3 x 4, with an Opening each. The passage is
 * walkable end to end, field gate to town gate, on the floor slab.
 */
public final class WallType implements BuildingType {
	public static final String ID = "wall";

	static final int PASSAGE_W = 5, GATE_W = 3, TOWER_MIN = 10, TOWER_MAX = 12;
	/** Ground storey height: a 4-high gate under a slab, and a stair core of 6. */
	static final int GROUND = 5;

	private static final TypeLimits LIMITS = new TypeLimits(2 * TOWER_MIN + PASSAGE_W - 2, 5, 2, 3, 0, 1, 1, false,
			List.of(new int[] {24, 7}, new int[] {32, 9}, new int[] {48, 11}, new int[] {64, 11}), 120, 3);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	@Override
	public Massing mass(BuildContext c) {
		BuildingSpec sp = c.spec();
		for (int n = Math.min(3, sp.storeys()); n >= 2; n--) {
			Massing m = attempt(c, n);
			if (m != null) return m;
		}
		// last resort (tiny height cap): the passage alone, one storey
		StoreyGrid g = StoreyGrid.of(1, c.style().massing().ground(), c.style().massing().typical());
		int x = Math.max(0, (sp.width() - PASSAGE_W) / 2);
		return new Massing(g, List.of(new Mass("passage0", "passage", x, 0, PASSAGE_W, sp.depth(), 0, 1, null, true)), List.of());
	}

	private static Massing attempt(BuildContext c, int n) {
		BuildingSpec sp = c.spec();
		Draws r = c.draws("massing/wall", n);
		StoreyGrid g = StoreyGrid.of(n, GROUND, c.style().massing().typical());
		int w = sp.width(), d = sp.depth();
		if (d < 5 || g.y(n) + 2 > sp.maxHeight()) return null;
		int twL = r.range(TOWER_MIN, TOWER_MAX), twR = r.range(TOWER_MIN, TOWER_MAX);
		while (twL + twR + PASSAGE_W - 2 > w) {
			if (twL > TOWER_MIN) twL--;
			else if (twR > TOWER_MIN) twR--;
			else return null;
		}
		int spare = w - (twL + twR + PASSAGE_W - 2);
		if (spare == 1 || spare == 2) { // too little for a curtain: the towers take it
			twL += spare;
			spare = 0;
		}
		List<Integer> lefts = new ArrayList<>();
		lefts.add(0);
		for (int s = 3; s <= spare - 3; s++) lefts.add(s);
		if (spare > 0) lefts.add(spare);
		int sl = lefts.get(r.below(lefts.size()));
		int sr = spare - sl;
		int nL = n, nR = r.chance(1, 3) ? n - 1 : n;
		if (nR < 2) nR = n;
		int gx = sl;
		Mass towerL = Mass.ground("tower0", "tower", gx, 0, twL, d, nL);
		Mass passage = new Mass("passage0", "passage", gx + twL - 1, 0, PASSAGE_W, d, 0, 1, null, true);
		Mass towerR = Mass.ground("tower1", "tower", passage.maxX() - 1, 0, twR, d, nR);
		int nb = Math.min(nL, nR);
		List<Mass> ms = new ArrayList<>();
		ms.add(passage);
		ms.add(new Mass("bridge0", "bridge", passage.x(), 0, PASSAGE_W, d, 1, nb, passage.id(), false));
		ms.add(towerL);
		ms.add(towerR);
		if (sl > 0) ms.add(curtain("curtain0", 0, sl + 1, d, r));
		if (sr > 0) ms.add(curtain("curtain1", towerR.maxX() - 1, sr + 1, d, r));
		List<Core> cores = new ArrayList<>();
		for (Mass t : List.of(towerL, towerR)) {
			Core k = MassingOps.core(g, List.of(t), ms, 0, t.to(), r);
			if (k == null) return null;
			cores.add(k);
		}
		Massing m = new Massing(g, ms, cores);
		return MassingCheck.check(m, w, d, sp.maxHeight()).isEmpty() ? m : null;
	}

	/** A gallery wall 3 to 5 thick (1 to 3 inside), shifted along the depth. */
	private static Mass curtain(String id, int x, int sx, int d, Draws r) {
		return new Mass(id, "curtain", x, WalkOps.bandZ(d), sx, WalkOps.BAND, 0, 1, null, true);
	}

	// ---- roofs: flat with a crenellated parapet ----

	@Override
	public Program roofs() {
		return c -> {
			RoofStyle s = c.style().roof();
			RoofPass.run(c, new RoofStyle(s.forms(), s.rise(), s.run(), 0, 2, "wall", 1, 0));
			battlements(c);
			for (Mass cur : c.massing().masses()) if (cur.kind().equals("curtain")) WalkOps.openEnds(c, cur);
		};
	}

	/** Removes the upper parapet block of every second cell (not at corners): merlons and crenels. */
	static void battlements(BuildContext c) {
		Massing m = c.massing();
		int top = c.spec().maxHeight();
		for (Mass a : m.masses()) {
			boolean[][] ex = RoofPass.exposed(m, a);
			int y = a.topSlab(m.grid()) + 3; // the second parapet layer
			if (y >= top) continue;
			for (int i = 0; i < a.sx(); i++)
				for (int k = 0; k < a.sz(); k++) {
					int x = a.x() + i, z = a.z() + k;
					if (!ex[i][k] || (x + z) % 2 == 0 || c.role(x, y, z) != Role.WALL) continue;
					boolean ax = c.role(x - 1, y, z) == Role.WALL || c.role(x + 1, y, z) == Role.WALL;
					boolean az = c.role(x, y, z - 1) == Role.WALL || c.role(x, y, z + 1) == Role.WALL;
					if (ax && az) continue; // a corner
					c.box(Role.AIR, Layer.ROOF, dev.fractalbuildings.core.ir.Box.NO_STOREY, x, y, z, 1, 1, 1);
				}
		}
	}

	// ---- facade: the two gates ----

	@Override
	public Program facade() {
		return c -> {
			FacadePass.run(c);
			FacadePlan p = c.facadePlan();
			Massing m = c.massing();
			Mass passage = m.main();
			Face north = gateFace(m, passage, Facing.NORTH), south = gateFace(m, passage, Facing.SOUTH);
			FacadePlan.Entrance field = new FacadePlan.Entrance(north, centre(north)), town = new FacadePlan.Entrance(south, centre(south));
			List<FacadePlan.Layout> layouts = new ArrayList<>();
			for (FacadePlan.Layout l : p.layouts()) {
				Face f = l.face();
				if (f.mass().equals(passage.id()) && f.storey() == 0 && (f.equals(north) || f.equals(south))) {
					int pos = f.equals(north) ? field.pos() : town.pos();
					List<String> kinds = new ArrayList<>(l.kinds());
					for (int b = 0; b < l.bays().length; b++) if (pos >= l.bayStart(b) && pos < l.bayStart(b) + l.bays()[b]) kinds.set(b, FacadePlan.DOOR);
					l = l.withKinds(kinds);
				}
				layouts.add(l);
			}
			c.setFacadePlan(new FacadePlan(layouts, field, List.of(town)));
		};
	}

	private static Face gateFace(Massing m, Mass passage, Facing side) {
		List<Face> fs = Faces.of(m, passage.id(), 0, side);
		if (fs.size() != 1) throw new IllegalStateException("passage " + side + " face: " + fs.size() + " runs");
		return fs.get(0);
	}

	private static int centre(Face f) { return f.a() + (f.length() - 1) / 2; }

	// ---- openings: the gates (3 x 4) and the doors onto the wall walk ----

	@Override
	public Program openings() {
		return c -> {
			Massing m = c.massing();
			OpeningPass.run(c, e -> gate(c, e));
			for (Mass cur : m.masses()) if (cur.kind().equals("curtain")) walkDoor(c, m, cur);
			endDoors(c, m);
		};
	}

	/** The gate: DOOR leaves 3 wide over the whole interior height, one Opening. */
	static FacadePlan.Entrance gate(BuildContext c, FacadePlan.Entrance e) {
		Face f = e.face();
		StoreyGrid g = c.massing().grid();
		int h = Math.min(4, g.height(0) - 1);
		String room = c.roomAt(f.insideX(e.pos()), e.y(), f.insideZ(e.pos()));
		if (room == null) throw new IllegalStateException("gate opens into no room");
		for (int dx = -1; dx <= 1; dx++)
			for (int y = 0; y < h; y++)
				if (c.role(f.insideX(e.pos()) + dx, e.y() + y, f.insideZ(e.pos())) != Role.AIR) throw new IllegalStateException("gate blocked inside");
		Box3 box = Box3.of(e.x() - 1, e.y(), e.z(), GATE_W, h, 1);
		c.box(Role.DOOR, Layer.SHELL, f.storey(), box.x(), box.y(), box.z(), box.sx(), box.sy(), box.sz());
		c.opening(OpeningKind.DOOR, box, f.side(), room, Opening.OUTSIDE);
		return e;
	}

	/** A tower end that has no curtain in the lot opens onto the neighbouring lot's walk, and a curtain reaching the lot's end opens its parapet there. */
	static void endDoors(BuildContext c, Massing m) {
		int e = c.spec().depth();
		for (Mass t : m.masses()) {
			if (!t.kind().equals("tower") || t.to() < 2) continue;
			boolean west = t.x() == 0, east = t.maxX() == c.spec().width();
			if (west) WalkOps.endDoor(c, m, t, false, e, 1, 1);
			if (east) WalkOps.endDoor(c, m, t, true, e, 1, 1);
		}
	}

	/**
	 * A door from a tower's first upper storey through its wall onto the neighbouring curtain's roof
	 * (the wall walk). The door sits one block above the tower floor, so you step up onto the roof.
	 */
	static void walkDoor(BuildContext c, Massing m, Mass curtain) {
		StoreyGrid g = m.grid();
		for (Mass t : m.masses()) {
			if (!t.kind().equals("tower") || t.to() < 2) continue;
			int[] st = Massing.strip(t, curtain);
			if (st == null) continue;
			int y = g.y(1) + 2;
			for (int z = st[1] + 1; z < st[1] + st[3] - 1; z++) {
				boolean west = curtain.x() < t.x();
				int ix = west ? t.x() + 1 : t.maxX() - 2, wx = west ? t.x() : t.maxX() - 1;
				String room = c.roomAt(ix, y, z);
				if (room == null || c.role(ix, y, z) != Role.AIR || c.role(ix, y + 1, z) != Role.AIR || m.inCore(1, ix, z)) continue;
				if (c.role(ix, y - 1, z) != Role.AIR || !t.interior(ix, z)) continue;
				Box3 box = Box3.of(wx, y, z, 1, 2, 1);
				c.box(Role.DOOR, Layer.SHELL, 1, box.x(), box.y(), box.z(), 1, 2, 1);
				c.opening(OpeningKind.DOOR, box, west ? Facing.WEST : Facing.EAST, room, Opening.OUTSIDE);
				return;
			}
		}
	}

	// ---- rooms ----

	@Override
	public RoomProgram rooms() { return ROOMS; }

	/** The passage and galleries are bare; tower rooms hold guard furniture. */
	private static final RoomProgram ROOMS = new RoomProgram() {
		@Override
		public String use(Role role, int area, Draws d) {
			return switch (role) {
				case HALL -> area > 40 ? "gallery" : "passage";
				default -> RoomProgram.DEFAULT.use(role, area, d);
			};
		}

		@Override
		public List<String> hooks(String use, int area, Draws d) {
			return switch (use) {
				case "passage" -> List.of();
				case "gallery" -> area >= 30 ? List.of("light", "chest") : List.of("light");
				case "entry", "room" -> List.of("chest", "bed", "table").subList(0, Math.min(3, 1 + area / 8));
				default -> RoomProgram.DEFAULT.hooks(use, area, d);
			};
		}
	};
}
