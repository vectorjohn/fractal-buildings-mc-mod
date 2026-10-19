package dev.fractalbuildings.core.gen.v1.interior;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import java.util.List;

/**
 * Interiors (foundation default, owned by the interiors step 4.5 afterwards): one room per mass and
 * floor (the whole space inside the walls), a door through every wall strip two masses share on a
 * common floor, and a two-row switchback stair in every core ({@link #flight}). No partitions, no
 * hooks. Rooms are created first, in mass then storey order, so room ids are stable.
 */
public final class InteriorPass {
	private InteriorPass() {}

	public static void run(BuildContext c) {
		Massing m = c.massing();
		StoreyGrid g = m.grid();
		List<Mass> ms = m.masses();
		String[][] room = new String[ms.size()][g.count()];
		for (int i = 0; i < ms.size(); i++) {
			Mass a = ms.get(i);
			for (int s = a.from(); s < a.to(); s++) {
				if (!a.floorAt(s)) continue;
				int top = a.open() ? g.y(a.to()) : g.y(s + 1);
				room[i][s] = c.room(s, a.id(), List.of(Box3.of(a.x() + 1, g.y(s) + 1, a.z() + 1, a.sx() - 2, top - g.y(s) - 1, a.sz() - 2)), "room");
			}
		}
		for (int i = 0; i < ms.size(); i++)
			for (int j = i + 1; j < ms.size(); j++) {
				int[] st = Massing.strip(ms.get(i), ms.get(j));
				if (st == null || Math.max(st[2], st[3]) < 3 || m.party(ms.get(i), ms.get(j))) continue;
				for (int s = 0; s < g.count(); s++) {
					if (room[i][s] == null || room[j][s] == null) continue;
					stripDoor(c, m, ms.get(i), st, s, room[i][s], room[j][s]);
				}
			}
		for (Core k : m.cores()) {
			for (int s = k.from(); s < k.to() - 1; s++) {
				Mass lo = m.interiorContaining(s, k.x(), k.z(), k.sx(), k.sz());
				Mass hi = m.interiorContaining(s + 1, k.x(), k.z(), k.sx(), k.sz());
				flight(c, g, k, s, room[ms.indexOf(lo)][s], room[ms.indexOf(hi)][s + 1]);
			}
		}
	}

	/** A 1x2 door through the strip at the position nearest its middle whose two sides are not in a core. */
	static void stripDoor(BuildContext c, Massing m, Mass a, int[] st, int s, String ra, String rb) {
		boolean alongX = st[3] == 1; // strip thickness in z: runs along x
		int lo = alongX ? st[0] : st[1], len = alongX ? st[2] : st[3];
		int y = m.grid().y(s) + 1;
		int best = -1;
		for (int p = lo + 1; p < lo + len - 1; p++) {
			int x = alongX ? p : st[0], z = alongX ? st[1] : p;
			int ax = alongX ? x : x - 1, az = alongX ? z - 1 : z;
			int bx = alongX ? x : x + 1, bz = alongX ? z + 1 : z;
			if (m.inCore(s, ax, az) || m.inCore(s, bx, bz)) continue;
			if (best < 0 || Math.abs(2 * p - (2 * lo + len - 1)) < Math.abs(2 * best - (2 * lo + len - 1))) best = p;
		}
		if (best < 0) throw new IllegalStateException("no door position in the strip of " + a.id() + " on storey " + s);
		int x = alongX ? best : st[0], z = alongX ? st[1] : best;
		// facing: from a's side into the other mass
		Facing f = alongX ? (a.interior(x, z - 1) ? Facing.SOUTH : Facing.NORTH) : (a.interior(x - 1, z) ? Facing.EAST : Facing.WEST);
		c.box(Role.DOOR, Layer.INTERIOR, s, x, y, z, 1, 2, 1);
		c.opening(OpeningKind.DOOR, Box3.of(x, y, z, 1, 2, 1), f, ra, rb);
	}

	/**
	 * The flight from storey s to s + 1 in core k: a straight run of h - 1 solid step columns
	 * (h = the storey's height), even flights (counted from the core's first storey) on the core's back
	 * row (larger z, or larger x for a core along z) ascending towards +x (+z), odd flights on the front
	 * row ascending the other way, so each flight stands on an intact slab. The slab above the steps
	 * is cut. Plan "Core contract" has the walk-through.
	 */
	public static void flight(BuildContext c, StoreyGrid g, Core k, int s, String from, String to) {
		int h = g.height(s), y = g.y(s);
		boolean even = (s - k.from()) % 2 == 0;
		boolean ax = k.alongX();
		int len = ax ? k.sx() : k.sz();
		int base = ax ? k.x() : k.z();
		int row = ax ? (even ? k.maxZ() - 1 : k.z()) : (even ? k.maxX() - 1 : k.x());
		int first = Integer.MAX_VALUE;
		for (int i = 0; i <= h - 2; i++) {
			int col = even ? base + 1 + i : base + len - 2 - i;
			first = Math.min(first, col);
			if (ax) c.box(Role.STAIR, Layer.INTERIOR, s, col, y + 1, row, 1, i + 1, 1);
			else c.box(Role.STAIR, Layer.INTERIOR, s, row, y + 1, col, 1, i + 1, 1);
		}
		int top = g.y(s + 1);
		if (ax) c.box(Role.AIR, Layer.INTERIOR, s, first, top, row, h - 1, 1, 1);
		else c.box(Role.AIR, Layer.INTERIOR, s, row, top, first, 1, 1, h - 1);
		Facing f = ax ? (even ? Facing.EAST : Facing.WEST) : (even ? Facing.SOUTH : Facing.NORTH);
		Box3 run = ax ? Box3.of(first, y + 1, row, h - 1, h - 1, 1) : Box3.of(row, y + 1, first, 1, h - 1, h - 1);
		c.opening(OpeningKind.STAIR, run, f, from, to);
	}
}
