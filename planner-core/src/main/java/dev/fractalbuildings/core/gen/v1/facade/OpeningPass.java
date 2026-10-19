package dev.fractalbuildings.core.gen.v1.facade;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.style.FacadeStyle;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;

/**
 * Openings (foundation default, owned by the facades step 4.4 afterwards): the entrance door, then a
 * window at every window bay's centre where the wall is still wall, the cell inside is room air and
 * the cell outside is open (so roofs, stairs and partitions that got there first win). Runs last, so
 * its boxes cut through everything before.
 */
public final class OpeningPass {
	private OpeningPass() {}

	public static void run(BuildContext c) {
		FacadePlan plan = c.facadePlan();
		for (FacadePlan.Entrance e : plan.entrances()) door(c, e);

		FacadeStyle st = c.style().facade();
		for (FacadePlan.Layout l : plan.layouts()) {
			Face f = l.face();
			int y = f.y0() + st.sill(), h = st.windowHeight();
			if (y + h > f.y0() + f.height()) continue;
			for (int b = 0; b < l.bays().length; b++) {
				if (!FacadePlan.WINDOW.equals(l.kinds().get(b))) continue;
				int p = l.centre(b);
				String r = windowRoom(c, f, p, y, h);
				if (r == null) continue;
				c.box(Role.WINDOW, Layer.SHELL, f.storey(), f.x(p), y, f.z(p), 1, h, 1);
				c.opening(OpeningKind.WINDOW, Box3.of(f.x(p), y, f.z(p), 1, h, 1), f.side(), r, Opening.OUTSIDE);
			}
		}
	}

	/** An exterior door at the entrance (1 x 2), into the room behind it, which must be free to stand in. */
	static void door(BuildContext c, FacadePlan.Entrance e) {
		Face ef = e.face();
		int ix = ef.insideX(e.pos()), iz = ef.insideZ(e.pos());
		String room = c.roomAt(ix, e.y(), iz);
		if (room == null) throw new IllegalStateException("entrance at " + e.x() + "," + e.z() + " opens into no room");
		if (c.role(ix, e.y(), iz) != Role.AIR || c.role(ix, e.y() + 1, iz) != Role.AIR) {
			throw new IllegalStateException("entrance at " + e.x() + "," + e.z() + " is blocked inside");
		}
		c.box(Role.DOOR, Layer.SHELL, ef.storey(), e.x(), e.y(), e.z(), 1, 2, 1);
		c.opening(OpeningKind.DOOR, Box3.of(e.x(), e.y(), e.z(), 1, 2, 1), ef.side(), room, Opening.OUTSIDE);
	}

	/** The room behind a window cell column, or null if the window can't go there. */
	static String windowRoom(BuildContext c, Face f, int p, int y, int h) {
		String room = null;
		for (int k = 0; k < h; k++) {
			if (c.role(f.x(p), y + k, f.z(p)) != Role.WALL) return null;
			if (c.role(f.insideX(p), y + k, f.insideZ(p)) != Role.AIR) return null;
			if (c.role(f.outsideX(p), y + k, f.outsideZ(p)) != Role.AIR) return null;
			String r = c.roomAt(f.insideX(p), y + k, f.insideZ(p));
			if (r == null || (room != null && !room.equals(r))) return null;
			room = r;
		}
		return room;
	}
}
