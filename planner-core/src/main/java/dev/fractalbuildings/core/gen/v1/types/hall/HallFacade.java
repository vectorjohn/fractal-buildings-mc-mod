package dev.fractalbuildings.core.gen.v1.types.hall;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.facade.OpeningPass;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import java.util.ArrayList;
import java.util.List;

/**
 * Hall facades: the shared bay layout with tall windows on open masses (a hall's windows run up to
 * four high where the wall allows), and, on the ground storey of a market hall's {@code arcade}
 * mass, a row of glazed arches (3 wide, 3 high from the floor, every 9 blocks along every face).
 * They are windows, not doors: a door per arch would each need its own flight of front steps, and
 * on a slope the lot margin cannot hold that many (entrance-reachable fails); see the log.
 */
final class HallFacade {
	private HallFacade() {}

	static final int ARCH_W = 3, ARCH_H = 3, SLOT = 9, WINDOW_MAX = 4;

	/** The shared facade pass, with tall windows on the faces of open masses. */
	static void facade(BuildContext c) {
		List<FacadePlan.Layout> layouts = FacadePass.layout(c);
		Massing m = c.massing();
		for (int i = 0; i < layouts.size(); i++) {
			FacadePlan.Layout l = layouts.get(i);
			if (!m.mass(l.face().mass()).open() || l.win() == null) continue;
			int h = Math.min(WINDOW_MAX, l.face().height() - l.win().sill() - 1);
			if (h <= l.win().height()) continue;
			layouts.set(i, new FacadePlan.Layout(l.face(), l.start(), l.bays(), l.kinds(), new FacadePlan.Win(l.win().width(), h, l.win().sill())));
		}
		List<FacadePlan.Entrance> entrances = new ArrayList<>();
		for (int e = 0; e < m.entrances().size(); e++) entrances.add(FacadePass.entrance(c, m, layouts, m.entrances().get(e), e));
		FacadePlan plan = new FacadePlan(layouts, entrances.get(0), entrances.subList(1, entrances.size()));
		c.setFacadePlan(plan);
		FacadePass.decorate(c, plan);
	}

	/** The shared opening pass, then the arcade: glazed arches along every ground face of a market hall. */
	static void openings(BuildContext c) {
		OpeningPass.run(c);
		FacadePlan plan = c.facadePlan();
		Massing m = c.massing();
		for (Face f : c.faces()) {
			Mass mass = m.mass(f.mass());
			if (!mass.kind().equals("arcade") || f.storey() != 0) continue;
			int slots = (f.length() - 2) / SLOT;
			int first = f.a() + 1 + (f.length() - 2 - slots * SLOT) / 2;
			for (int k = 0; k < slots; k++) {
				int p = first + k * SLOT + (SLOT - ARCH_W) / 2;
				if (nearEntrance(plan, f, p)) continue;
				arcadeBay(c, f, p);
			}
		}
	}

	private static boolean nearEntrance(FacadePlan plan, Face f, int p) {
		for (FacadePlan.Entrance e : plan.entrances()) {
			if (e.face().equals(f) && e.pos() + e.width() + 1 > p && p + ARCH_W + 1 > e.pos()) return true;
		}
		return false;
	}

	/**
	 * A 3 x 3 bay at positions [p, p + 3) from the floor, if the wall is there and the room behind it
	 * is free: a glazed arch from the floor up (window kind).
	 */
	private static void arcadeBay(BuildContext c, Face f, int p) {
		String room = null;
		for (int q = p; q < p + ARCH_W; q++)
			for (int y = f.y0(); y < f.y0() + ARCH_H; y++) {
				Role w = c.role(f.x(q), y, f.z(q));
				if (w != Role.WALL && w != Role.WINDOW && w != Role.FOUNDATION) return;
				if (c.role(f.insideX(q), y, f.insideZ(q)) != Role.AIR || c.role(f.outsideX(q), y, f.outsideZ(q)) != Role.AIR) return;
				String r = c.roomAt(f.insideX(q), y, f.insideZ(q));
				if (r == null || room != null && !room.equals(r)) return;
				room = r;
			}
		boolean ns = f.side() == dev.fractalbuildings.core.ir.Facing.NORTH || f.side() == dev.fractalbuildings.core.ir.Facing.SOUTH;
		int sx = ns ? ARCH_W : 1, sz = ns ? 1 : ARCH_W;
		Box3 box = Box3.of(f.x(p), f.y0(), f.z(p), sx, ARCH_H, sz);
		c.box(Role.WINDOW, Layer.SHELL, f.storey(), box.x(), box.y(), box.z(), sx, ARCH_H, sz);
		c.opening(OpeningKind.WINDOW, box, f.side(), room, Opening.OUTSIDE);
	}
}
