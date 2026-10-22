package dev.fractalbuildings.core.gen.v1.types.wall;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;

/**
 * The wall walk of a town ring (D2), shared by the {@code wall} (gatehouse), {@code curtain} and {@code bastion} types.
 *
 * <p>Every ring lot is built independently, so the walk is joined by convention: the walk of a wall lot is the roof of a
 * gallery {@link #BAND} thick, centred on the ring line (the lot's middle row), at the height of the first upper storey of
 * the towers. Where two lots meet, the walk is open across the whole band at the end (no parapet), and a tower meeting a
 * walk has a two-cell doorway at its first upper storey on the ring line (the two cells {@link #cells}), which every
 * neighbour's band covers whatever the lots' depths.
 */
final class WalkOps {
	private WalkOps() {}

	/** Thickness of the walk gallery, walls included. */
	static final int BAND = 5;

	/** Envelope z of the gallery's first row, for an envelope {@code e} deep: the band centred on the ring line. */
	static int bandZ(int e) { return (e - BAND) / 2; }

	/** The two cells (of an envelope {@code e} across, in a lot with {@code front} and {@code back} margin rows) that lie on the ring line or next to it: the doorway between a tower and a walk. */
	static int[] cells(int e, int front, int back) {
		int line = (e + front + back) / 2 - front; // the lot's middle row, in envelope coordinates
		return new int[] {line - 1, line};
	}

	/**
	 * Removes the parapet across the whole band at the two ends of a curtain mass lying on the lot's ends, so the walk runs
	 * on into the next lot. The parapet is every wall block above the roof slab in the end columns.
	 */
	static void openEnds(BuildContext c, Mass cur) {
		int top = cur.topSlab(c.massing().grid());
		for (int x : new int[] {cur.x(), cur.maxX() - 1}) {
			if (x != 0 && x != c.spec().width() - 1) continue; // only the ends on the lot edge
			for (int z = cur.z(); z < cur.maxZ(); z++)
				for (int y = top + 1; y <= top + 4; y++)
					if (c.role(x, y, z) == Role.WALL) c.box(Role.AIR, Layer.ROOF, dev.fractalbuildings.core.ir.Box.NO_STOREY, x, y, z, 1, 1, 1);
		}
	}

	/**
	 * A doorway from a tower's first upper storey out through its east or west end wall onto the walk of the neighbouring lot,
	 * on the two ring-line cells (one if the other is taken by the stair), at the walk's standing level.
	 *
	 * @return whether a doorway was cut
	 */
	static boolean endDoor(BuildContext c, Massing m, Mass t, boolean east, int e, int front, int back) {
		StoreyGrid g = m.grid();
		int y = g.y(1) + 2; // the walk stands on the curtain roof, one block above the tower floor
		int ix = east ? t.maxX() - 2 : t.x() + 1, wx = east ? t.maxX() - 1 : t.x();
		int[] ok = new int[2];
		int n = 0;
		for (int z : cells(e, front, back)) if (cellOk(c, m, t, ix, y, z)) ok[n++] = z;
		if (n == 0) return false;
		int z0 = ok[0];
		int sz = n == 2 && ok[1] == ok[0] + 1 ? 2 : 1;
		cut(c, wx, y, z0, 1, sz, east ? Facing.EAST : Facing.WEST, c.roomAt(ix, y, z0));
		return true;
	}

	/** As {@link #endDoor} through the back (south) wall, on the two ring-line columns {@code cells(w)}. */
	static boolean backDoor(BuildContext c, Massing m, Mass t, int w) {
		StoreyGrid g = m.grid();
		int y = g.y(1) + 2; // the walk stands on the curtain roof, one block above the tower floor
		int iz = t.maxZ() - 2, wz = t.maxZ() - 1;
		int[] ok = new int[2];
		int n = 0;
		for (int x : cells(w, 0, 0)) if (cellOk(c, m, t, x, y, iz)) ok[n++] = x;
		if (n == 0) return false;
		int x0 = ok[0];
		int sx = n == 2 && ok[1] == ok[0] + 1 ? 2 : 1;
		cut(c, x0, y, wz, sx, 1, Facing.SOUTH, c.roomAt(x0, y, iz));
		return true;
	}

	private static boolean cellOk(BuildContext c, Massing m, Mass t, int ix, int y, int z) {
		if (ix <= t.x() || ix >= t.maxX() - 1 || z <= t.z() || z >= t.maxZ() - 1) return false;
		if (c.roomAt(ix, y, z) == null || c.role(ix, y, z) != Role.AIR || c.role(ix, y + 1, z) != Role.AIR || c.role(ix, y - 1, z) != Role.AIR) return false;
		return !m.inCore(1, ix, z);
	}

	private static boolean beside(BuildContext c, int x, int y, int z) {
		return c.role(x + 1, y, z) == Role.DOOR || c.role(x - 1, y, z) == Role.DOOR || c.role(x, y, z + 1) == Role.DOOR || c.role(x, y, z - 1) == Role.DOOR;
	}

	private static void cut(BuildContext c, int x, int y, int z, int sx, int sz, Facing side, String room) {
		// a step inside in front of each doorway cell: the sill is one block above the tower floor
		for (int i = 0; i < sx; i++)
			for (int k = 0; k < sz; k++) {
				int sx0 = x - side.dx + i, sz0 = z - side.dz + k;
				if (beside(c, sx0, y - 1, sz0)) continue; // an inner door next to the cell: a step would block the way to it (the sill is then a jump)
				c.box(Role.STAIR, Layer.INTERIOR, 1, sx0, y - 1, sz0, 1, 1, 1);
			}
		Box3 box = Box3.of(x, y, z, sx, 2, sz);
		c.box(Role.DOOR, Layer.SHELL, 1, box.x(), box.y(), box.z(), box.sx(), box.sy(), box.sz());
		c.opening(OpeningKind.DOOR, box, side, room, Opening.OUTSIDE);
	}
}
