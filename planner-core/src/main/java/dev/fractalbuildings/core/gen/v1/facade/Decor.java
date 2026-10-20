package dev.fractalbuildings.core.gen.v1.facade;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.style.FacadeStyle;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Role;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Shared machinery of facade decor (both the facade pass, which lays wall-plane and ring decor before
 * the interiors, and the opening pass, which adds sills, lintels, shutters and door surrounds once
 * windows and doors are cut). The two rules every piece follows:
 * <ul>
 *   <li>wall-plane decor ({@link #wallFree}) replaces wall cells only, never a cell a window, a door or
 *       a type's own bay (shopfront...) will use ({@link #reserved});
 *   <li>decor outside the wall plane ({@link #outFree}) takes only free air inside the envelope, never
 *       the cell in front of a door or of a window cell.
 * </ul>
 * Decor is emitted as merged boxes (runs along the face, merged across rows) on layer SHELL.
 */
final class Decor {
	final BuildContext c;
	final FacadePlan plan;
	final FacadeStyle st;
	private final Map<Face, List<int[]>> reserved = new LinkedHashMap<>(); // face -> {p0, p1, y0, y1} half-open
	private final TreeSet<Long> fronts = new TreeSet<>();

	Decor(BuildContext c, FacadePlan plan) {
		this.c = c;
		this.plan = plan;
		this.st = c.style().facade();
		for (FacadePlan.Layout l : plan.layouts()) {
			Face f = l.face();
			FacadePlan.Win w = window(l);
			for (int i = 0; i < l.bays().length; i++) {
				String k = l.kinds().get(i);
				if (FacadePlan.BLANK.equals(k)) continue;
				if (FacadePlan.WINDOW.equals(k)) {
					int ws = l.windowStart(i), ww = l.windowWidth(i);
					reserve(f, ws, ws + ww, f.y0() + w.sill(), f.y0() + w.sill() + w.height());
					for (int p = ws; p < ws + ww; p++) front(f, p, f.y0() + w.sill(), w.height());
				} else if (FacadePlan.DOOR.equals(k)) {
					reserve(f, l.bayStart(i), l.bayStart(i) + l.bays()[i], f.y0(), f.y0() + 2);
				} else {
					reserve(f, l.bayStart(i), l.bayStart(i) + l.bays()[i], f.y0(), f.y0() + f.height());
				}
			}
		}
		for (FacadePlan.Entrance e : plan.entrances()) {
			Face f = e.face();
			reserve(f, e.pos(), e.pos() + e.width(), f.y0(), f.y0() + 2);
			for (int p = e.pos(); p < e.pos() + e.width(); p++) front(f, p, f.y0(), 2);
		}
	}

	/** The window shape of a layout: its own, else the style's. */
	FacadePlan.Win window(FacadePlan.Layout l) {
		if (l.win() != null) return l.win();
		return new FacadePlan.Win(1, st.windowHeight(), st.sill());
	}

	private void reserve(Face f, int p0, int p1, int y0, int y1) { reserved.computeIfAbsent(f, k -> new ArrayList<>()).add(new int[] {p0, p1, y0, y1}); }

	private void front(Face f, int p, int y, int h) {
		for (int k = 0; k < h; k++) fronts.add(key(f.outsideX(p), y + k, f.outsideZ(p)));
	}

	private static long key(int x, int y, int z) { return ((long) y << 40) | ((long) (z & 0xfffff) << 20) | (x & 0xfffff); }

	boolean reserved(Face f, int p, int y) {
		List<int[]> l = reserved.get(f);
		if (l == null) return false;
		for (int[] r : l) if (p >= r[0] && p < r[1] && y >= r[2] && y < r[3]) return true;
		return false;
	}

	/** The wall cell (p, y) of f is plain wall that no opening will use. */
	boolean wallFree(Face f, int p, int y) {
		return !reserved(f, p, y) && c.role(f.x(p), y, f.z(p)) == Role.WALL;
	}

	/** The cell is free air inside the envelope and not in front of a door or a window. */
	boolean outFree(int x, int y, int z) {
		if (x < 0 || z < 0 || y < 0 || x >= c.width() || z >= c.depth() || y >= c.maxHeight()) return false;
		return c.role(x, y, z) == Role.AIR && !fronts.contains(key(x, y, z));
	}

	boolean outFree(Face f, int p, int y) { return outFree(f.outsideX(p), y, f.outsideZ(p)); }

	/**
	 * Emits the cells of a mask as boxes of {@code role}. {@code mask[i][j]} is position {@code pa + i},
	 * local y {@code ya + j}; {@code out} 0 is the wall plane, 1 one cell outside it.
	 */
	void emit(Face f, Role role, int out, boolean[][] mask, int pa, int ya) {
		int np = mask.length, ny = mask[0].length;
		boolean ns = f.side() == dev.fractalbuildings.core.ir.Facing.NORTH || f.side() == dev.fractalbuildings.core.ir.Facing.SOUTH;
		List<int[]> open = new ArrayList<>(); // {i0, i1, j0}
		for (int j = 0; j <= ny; j++) {
			List<int[]> runs = new ArrayList<>();
			if (j < ny) {
				for (int i = 0; i < np;) {
					if (!mask[i][j]) { i++; continue; }
					int q = i;
					while (q < np && mask[q][j]) q++;
					runs.add(new int[] {i, q});
					i = q;
				}
			}
			List<int[]> next = new ArrayList<>();
			for (int[] o : open) {
				boolean cont = false;
				for (int[] r : runs) if (r[0] == o[0] && r[1] == o[1]) { cont = true; break; }
				if (cont) next.add(o); else flush(f, role, out, ns, pa, ya, o, j);
			}
			for (int[] r : runs) {
				boolean have = false;
				for (int[] o : next) if (o[0] == r[0] && o[1] == r[1]) { have = true; break; }
				if (!have) next.add(new int[] {r[0], r[1], j});
			}
			open = next;
		}
	}

	private void flush(Face f, Role role, int out, boolean ns, int pa, int ya, int[] o, int jEnd) {
		int p0 = pa + o[0], len = o[1] - o[0];
		int x = f.x(p0) + out * f.side().dx, z = f.z(p0) + out * f.side().dz;
		c.box(role, Layer.SHELL, f.storey(), x, ya + o[2], z, ns ? len : 1, jEnd - o[2], ns ? 1 : len);
	}

	/** A mask over a face's positions [a - 1, b] and local y [y0 - 1, y0 + height]. */
	static boolean[][] mask(Face f) { return new boolean[f.length() + 2][f.height() + 2]; }

	static int pa(Face f) { return f.a() - 1; }

	static int ya(Face f) { return f.y0() - 1; }
}
