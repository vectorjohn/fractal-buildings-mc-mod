package dev.fractalbuildings.core.gen.v1.roof;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.ir.Axis;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Roof;

/**
 * Roofs (foundation default, owned by the roofs step 4.3 afterwards). Per mass, the columns of its
 * footprint that nothing stands on are its exposed top ({@link #exposed}). A mass whose whole top is
 * exposed gets v0's gable (pitch 1/1, ridge along the longer side, gable end walls), if it fits
 * under {@code maxHeight}; a partly covered mass gets a flat roof (one roof layer on the top slab)
 * over its exposed columns; a fully covered mass gets none.
 */
public final class RoofPass {
	private RoofPass() {}

	public static void run(BuildContext c) {
		Massing m = c.massing();
		for (Mass mass : m.masses()) {
			boolean[][] e = exposed(m, mass);
			int n = 0;
			for (boolean[] row : e) for (boolean b : row) if (b) n++;
			if (n == 0) continue;
			int base = mass.topSlab(m.grid()) + 1;
			boolean ridgeX = mass.sx() >= mass.sz();
			int rise = ((ridgeX ? mass.sz() : mass.sx()) + 1) / 2;
			if (n == mass.sx() * mass.sz() && base + rise <= c.spec().maxHeight()) gable(c, mass, base, ridgeX);
			else flat(c, mass, base, e);
		}
	}

	/**
	 * Footprint columns (indexed [x - mass.x][z - mass.z]) with nothing standing above the mass's top:
	 * a column is covered if another mass contains it and reaches higher, or reaches as high and
	 * comes earlier in the list (so of two equal masses sharing a strip, the earlier one roofs it).
	 */
	public static boolean[][] exposed(Massing m, Mass mass) {
		int top = mass.box(m.grid()).maxY();
		int idx = m.masses().indexOf(mass);
		boolean[][] e = new boolean[mass.sx()][mass.sz()];
		for (int i = 0; i < mass.sx(); i++)
			for (int k = 0; k < mass.sz(); k++) {
				int cx = mass.x() + i, cz = mass.z() + k;
				boolean covered = false;
				for (int j = 0; j < m.masses().size() && !covered; j++) {
					Mass o = m.masses().get(j);
					if (o == mass || !o.contains(cx, cz)) continue;
					int otop = o.box(m.grid()).maxY();
					if (otop > top || (otop == top && j < idx)) covered = true;
				}
				e[i][k] = !covered;
			}
		return e;
	}

	static void gable(BuildContext c, Mass mass, int base, boolean ridgeX) {
		int across = ridgeX ? mass.sz() : mass.sx();
		int along = ridgeX ? mass.sx() : mass.sz();
		for (int row = 0; row < (across + 1) / 2; row++) {
			int p = row, q = across - 1 - row, y = base + row;
			roofRow(c, mass, ridgeX, y, p, along);
			if (q != p) roofRow(c, mass, ridgeX, y, q, along);
			int gap = q - p - 1;
			if (gap > 0) {
				gableEnd(c, mass, ridgeX, y, 0, p + 1, gap);
				gableEnd(c, mass, ridgeX, y, along - 1, p + 1, gap);
			}
		}
		c.roof(new Roof(Roof.GABLE, mass.id(), ridgeX ? Axis.X : Axis.Z, base, 1, 1));
	}

	private static void roofRow(BuildContext c, Mass m, boolean ridgeX, int y, int p, int along) {
		if (ridgeX) c.box(Role.ROOF, Layer.ROOF, Box.NO_STOREY, m.x(), y, m.z() + p, along, 1, 1);
		else c.box(Role.ROOF, Layer.ROOF, Box.NO_STOREY, m.x() + p, y, m.z(), 1, 1, along);
	}

	private static void gableEnd(BuildContext c, Mass m, boolean ridgeX, int y, int a, int from, int len) {
		if (ridgeX) c.box(Role.WALL, Layer.ROOF, Box.NO_STOREY, m.x() + a, y, m.z() + from, 1, 1, len);
		else c.box(Role.WALL, Layer.ROOF, Box.NO_STOREY, m.x() + from, y, m.z() + a, len, 1, 1);
	}

	static void flat(BuildContext c, Mass mass, int base, boolean[][] e) {
		// one box per run of exposed columns along x, per z row
		for (int k = 0; k < mass.sz(); k++) {
			int start = -1;
			for (int i = 0; i <= mass.sx(); i++) {
				boolean on = i < mass.sx() && e[i][k];
				if (on && start < 0) start = i;
				if (!on && start >= 0) {
					c.box(Role.ROOF, Layer.ROOF, Box.NO_STOREY, mass.x() + start, base, mass.z() + k, i - start, 1, 1);
					start = -1;
				}
			}
		}
		c.roof(new Roof("flat", mass.id(), null, base, 0, 1));
	}
}
