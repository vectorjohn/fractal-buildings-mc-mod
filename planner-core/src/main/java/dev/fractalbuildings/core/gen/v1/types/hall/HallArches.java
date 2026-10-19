package dev.fractalbuildings.core.gen.v1.types.hall;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Role;
import java.util.List;

/**
 * Arcades between neighbouring open spaces (nave and aisles, nave and transept, bath ranges): the
 * shared wall strip of two open masses gets 3-wide arch openings every 6 blocks above the floor, so
 * the two spaces read as one hall and not as two rooms with a keyhole door. The strip door the
 * interior pass cut stays; an arch that would touch it is skipped. Both spaces are enclosed, so
 * the shell stays closed; the pier above each arch keeps the wall up to the lower roof.
 */
final class HallArches {
	private HallArches() {}

	static final int WIDTH = 3, PITCH = 6;

	/** Cuts the arches; returns how many were cut. */
	static int run(BuildContext c) {
		Massing m = c.massing();
		StoreyGrid g = m.grid();
		List<Mass> ms = m.masses();
		int n = 0;
		for (int i = 0; i < ms.size(); i++)
			for (int j = i + 1; j < ms.size(); j++) {
				Mass a = ms.get(i), b = ms.get(j);
				if (!a.open() || !b.open() || m.party(a, b)) continue;
				int[] st = Massing.strip(a, b);
				if (st == null || Math.max(st[2], st[3]) < 9) continue;
				boolean alongX = st[3] == 1;
				int lo = alongX ? st[0] : st[1], len = alongX ? st[2] : st[3];
				// the lower space bounds the arch height: the pier above it must reach the roof
				int top = Math.min(a.to(), b.to());
				int h = Math.min(5, g.y(top) - g.y(0) - 2);
				if (h < 3) continue;
				int count = (len - 3) / PITCH;
				int start = lo + 1 + (len - 2 - (count - 1) * PITCH - WIDTH) / 2;
				for (int k = 0; k < count; k++) {
					int p = start + k * PITCH;
					if (blocked(c, alongX, st, p, g.y(0) + 1, h)) continue;
					if (alongX) c.box(Role.AIR, Layer.INTERIOR, 0, p, g.y(0) + 1, st[1], WIDTH, h, 1);
					else c.box(Role.AIR, Layer.INTERIOR, 0, st[0], g.y(0) + 1, p, 1, h, WIDTH);
					n++;
				}
			}
		return n;
	}

	private static boolean blocked(BuildContext c, boolean alongX, int[] st, int p, int y, int h) {
		for (int q = p - 1; q <= p + WIDTH; q++)
			for (int yy = y; yy < y + h; yy++) {
				Role r = alongX ? c.role(q, yy, st[1]) : c.role(st[0], yy, q);
				if (r == Role.DOOR) return true;
			}
		return false;
	}
}
