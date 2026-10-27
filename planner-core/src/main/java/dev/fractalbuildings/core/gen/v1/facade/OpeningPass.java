package dev.fractalbuildings.core.gen.v1.facade;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.style.FacadeStyle;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import java.util.ArrayList;
import java.util.List;

/**
 * Openings (owned by the facades step 4.4): the entrance doors, then a window in every window bay
 * where the wall is still wall, the cell inside is room air and the cell outside is open (so roofs,
 * stairs and partitions that got there first win). Runs last, so its boxes cut through everything
 * before. Then the decor that belongs to an opening that was actually cut: door surrounds, sills,
 * lintels and shutters, each only where the style asks for it and the cell is free (see {@link Decor}).
 */
public final class OpeningPass {
	private OpeningPass() {}

	private record Cut(Face face, int pos, int y, int h) {}

	public static void run(BuildContext c) { run(c, e -> door(c, e)); }

	/** As {@link #run(BuildContext)} with the types own cutter for each entrance (the wall type cuts 3 x 4 gates); it returns the entrance as cut. A cutter may return null for an entrance that gets no door. */
	public static void run(BuildContext c, java.util.function.UnaryOperator<FacadePlan.Entrance> cutDoor) { run(c, cutDoor, false); }

	/** As above; {@code unglazed} forces plain window holes (the type's choice) on top of the style's {@code unglazed} key. */
	public static void run(BuildContext c, java.util.function.UnaryOperator<FacadePlan.Entrance> cutDoor, boolean unglazed) {
		FacadePlan plan = c.facadePlan();
		FacadeStyle st = c.style().facade();
		Decor d = new Decor(c, plan);
		boolean holes = unglazed || st.unglazed();
		List<FacadePlan.Entrance> doors = new ArrayList<>();
		for (FacadePlan.Entrance e : plan.entrances()) {
			FacadePlan.Entrance cut = cutDoor.apply(e);
			if (cut != null) doors.add(cut); // null: the entrance has no door in the wall (a market hall is entered by a stair from the open floor)
		}

		List<Cut> cuts = new ArrayList<>();
		for (FacadePlan.Layout l : plan.layouts()) {
			Face f = l.face();
			FacadePlan.Win w = d.window(l);
			for (int b = 0; b < l.bays().length; b++) {
				if (!FacadePlan.WINDOW.equals(l.kinds().get(b))) continue;
				for (int k = 0; k < l.windowWidth(b); k++) {
					int p = l.windowStart(b) + k;
					if (window(c, f, p, f.y0() + w.sill(), w.height(), holes)) cuts.add(new Cut(f, p, f.y0() + w.sill(), w.height()));
				}
			}
		}

		if (st.doorSurround()) for (FacadePlan.Entrance e : doors) surround(d, e);
		for (Cut k : cuts) {
			if (st.lintels()) lintel(d, k);
			if (st.sills()) sill(d, k);
			if (st.shutters()) shutters(d, k);
		}
	}

	/**
	 * Cuts one window column (1 wide, {@code h} high from local y {@code y}) at position {@code p} of
	 * {@code f} if the wall is wall, the inside is air in one room and the outside is open air; returns
	 * whether it did. Public for types' programs (shopfronts, arcades) that cut their own windows.
	 */
	public static boolean window(BuildContext c, Face f, int p, int y, int h) { return window(c, f, p, y, h, c.style().facade().unglazed()); }

	/** As above; {@code hole} cuts the window as plain air (an unglazed opening) instead of a glass pane. */
	public static boolean window(BuildContext c, Face f, int p, int y, int h, boolean hole) {
		if (y + h > f.y0() + f.height()) return false;
		String r = windowRoom(c, f, p, y, h);
		if (r == null) return false;
		c.box(hole ? Role.AIR : Role.WINDOW, Layer.SHELL, f.storey(), f.x(p), y, f.z(p), 1, h, 1);
		c.opening(OpeningKind.WINDOW, Box3.of(f.x(p), y, f.z(p), 1, h, 1), f.side(), r, Opening.OUTSIDE);
		return true;
	}

	/**
	 * An exterior door at the entrance (width x 2), into the room behind it, which must be free to
	 * stand in. A double door whose second cell is blocked inside (or is not wall) is cut 1 wide.
	 * Returns the entrance as cut. Public for types' programs.
	 */
	public static FacadePlan.Entrance door(BuildContext c, FacadePlan.Entrance e) {
		Face ef = e.face();
		int ix = ef.insideX(e.pos()), iz = ef.insideZ(e.pos());
		String room = c.roomAt(ix, e.y(), iz);
		if (room == null) throw new IllegalStateException("entrance at " + e.x() + "," + e.z() + " opens into no room");
		if (c.role(ix, e.y(), iz) != Role.AIR || c.role(ix, e.y() + 1, iz) != Role.AIR) {
			throw new IllegalStateException("entrance at " + e.x() + "," + e.z() + " is blocked inside");
		}
		int width = 1;
		if (e.width() == 2 && secondCellOk(c, ef, e.pos() + 1, e.y(), room)) width = 2;
		boolean ns = ef.side() == dev.fractalbuildings.core.ir.Facing.NORTH || ef.side() == dev.fractalbuildings.core.ir.Facing.SOUTH;
		int sx = ns ? width : 1, sz = ns ? 1 : width;
		c.box(Role.DOOR, Layer.SHELL, ef.storey(), e.x(), e.y(), e.z(), sx, 2, sz);
		c.opening(OpeningKind.DOOR, Box3.of(e.x(), e.y(), e.z(), sx, 2, sz), ef.side(), room, Opening.OUTSIDE);
		return new FacadePlan.Entrance(ef, e.pos(), width);
	}

	private static boolean secondCellOk(BuildContext c, Face f, int p, int y, String room) {
		for (int k = 0; k < 2; k++) {
			if (c.role(f.x(p), y + k, f.z(p)) != Role.WALL) return false;
			if (c.role(f.insideX(p), y + k, f.insideZ(p)) != Role.AIR) return false;
			if (c.role(f.outsideX(p), y + k, f.outsideZ(p)) != Role.AIR) return false;
			if (!room.equals(c.roomAt(f.insideX(p), y + k, f.insideZ(p)))) return false;
		}
		return true;
	}

	/** The room behind a window cell column, or null if the window can't go there. */
	static String windowRoom(BuildContext c, Face f, int p, int y, int h) {
		String room = null;
		for (int k = 0; k < h; k++) {
			if (c.role(f.x(p), y + k, f.z(p)) != Role.WALL) return null;
			if (c.role(f.insideX(p), y + k, f.insideZ(p)) != Role.AIR) return null;
			Role out = c.role(f.outsideX(p), y + k, f.outsideZ(p));
			if (out != Role.AIR && !(out == Role.ROOF && y + k == f.y0())) return null; // a lower roof's deck stands at the first row of the storey above it: a floor-to-ceiling window is cut anyway (the deck block stays outside its bottom pane)
			String r = c.roomAt(f.insideX(p), y + k, f.insideZ(p));
			if (r == null || (room != null && !room.equals(r))) return null;
			room = r;
		}
		return room;
	}

	// ---- decor of cut openings ----

	/** Trim lintel above the door and a trim jamb on each side (wall cells only). */
	private static void surround(Decor d, FacadePlan.Entrance e) {
		Face f = e.face();
		boolean[][] mk = Decor.mask(f);
		boolean any = false;
		for (int p = e.pos() - 1; p <= e.pos() + e.width(); p++) {
			boolean jamb = p < e.pos() || p >= e.pos() + e.width();
			for (int y = f.y0(); y <= f.y0() + 2; y++) {
				if (y < f.y0() + 2 && !jamb) continue;
				if (y >= f.y0() + f.height()) continue;
				if (p < f.a() || p >= f.b() || d.c.role(f.x(p), y, f.z(p)) != Role.WALL) continue;
				mk[p - Decor.pa(f)][y - Decor.ya(f)] = true;
				any = true;
			}
		}
		if (any) d.emit(f, Role.TRIM, 0, mk, Decor.pa(f), Decor.ya(f));
	}

	/** A trim cell in the wall plane just above the window, if there is wall there. */
	private static void lintel(Decor d, Cut k) {
		Face f = k.face();
		int y = k.y() + k.h();
		if (y >= f.y0() + f.height() || d.c.role(f.x(k.pos()), y, f.z(k.pos())) != Role.WALL) return;
		d.c.box(Role.TRIM, Layer.SHELL, f.storey(), f.x(k.pos()), y, f.z(k.pos()), 1, 1, 1);
	}

	/** A trim cell one out, under the window. */
	private static void sill(Decor d, Cut k) {
		Face f = k.face();
		int y = k.y() - 1;
		if (f.storey() == 0 || y <= f.y0() - 1 || !d.outFree(f, k.pos(), y)) return; // storey 0: front steps run along the facade
		d.c.box(Role.TRIM, Layer.SHELL, f.storey(), f.outsideX(k.pos()), y, f.outsideZ(k.pos()), 1, 1, 1);
	}

	/** Trim shutters one out, on the wall cells either side of the window (never at a face end). */
	private static void shutters(Decor d, Cut k) {
		Face f = k.face();
		if (f.storey() == 0) return; // front steps may run along the ground storey
		for (int p : new int[] {k.pos() - 1, k.pos() + 1}) {
			if (p <= f.a() || p >= f.b() - 1) continue;
			boolean ok = true;
			for (int y = k.y(); y < k.y() + k.h() && ok; y++) ok = d.c.role(f.x(p), y, f.z(p)) == Role.WALL && d.outFree(f, p, y);
			if (ok) d.c.box(Role.TRIM, Layer.SHELL, f.storey(), f.outsideX(p), k.y(), f.outsideZ(p), 1, k.h(), 1);
		}
	}
}
