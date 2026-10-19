package dev.fractalbuildings.core.gen.v1.structure;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Role;

/**
 * Structure (foundation default, owned by the massing step 4.2 afterwards): for each mass, in mass
 * order, a floor slab per storey that has a floor, the four perimeter walls per storey (one band for
 * an open mass), and the top slab (role floor, layer roof, storey -1, as in v0). Masses that share a
 * wall strip both emit the same wall cells there; the interior pass cuts doors through them.
 */
public final class StructurePass {
	private StructurePass() {}

	public static void run(BuildContext c) {
		StoreyGrid g = c.massing().grid();
		for (Mass m : c.massing().masses()) {
			for (int s = m.from(); s < m.to(); s++) {
				if (m.floorAt(s)) c.box(Role.FLOOR, Layer.SHELL, s, m.x(), g.y(s), m.z(), m.sx(), 1, m.sz());
				if (m.open() && s > m.from()) continue;
				int y = g.y(s) + 1;
				int h = (m.open() ? g.y(m.to()) : g.y(s + 1)) - y;
				walls(c, m, s, y, h);
			}
			c.box(Role.FLOOR, Layer.ROOF, Box.NO_STOREY, m.x(), m.topSlab(g), m.z(), m.sx(), 1, m.sz());
		}
	}

	static void walls(BuildContext c, Mass m, int storey, int y, int h) {
		c.box(Role.WALL, Layer.SHELL, storey, m.x(), y, m.z(), m.sx(), h, 1);
		c.box(Role.WALL, Layer.SHELL, storey, m.x(), y, m.maxZ() - 1, m.sx(), h, 1);
		c.box(Role.WALL, Layer.SHELL, storey, m.x(), y, m.z() + 1, 1, h, m.sz() - 2);
		c.box(Role.WALL, Layer.SHELL, storey, m.maxX() - 1, y, m.z() + 1, 1, h, m.sz() - 2);
	}
}
