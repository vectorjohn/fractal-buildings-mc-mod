package dev.fractalbuildings.core.gen.v1.types.highrise;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;

/**
 * The top of a high-rise (plan docs/plans/highrises.md, B3b): the roof guard and the stair house's roof doors.
 *
 * <p>Guard (DS-8 amendment, DS-38): the roof the stair house opens onto, the top tier's, gets a 1-high wall parapet
 * all round (the roof pass leaves gaps where the stair house stands against the edge, filled here) with a 1-high
 * {@code rail} ring on top of it, unbroken bar the stair house's own footprint. Setback roofs keep the plain
 * parapet. Doors: the stair house's north face has one door into the stairwell (at the middle of the landing row)
 * and one into the machine room (at its middle column); neither is part of the {@code FacadePlan}.
 */
final class TowerRoof {
	private TowerRoof() {}

	/** The top tier: the mass the stair house stands on. */
	private static Mass topTier(Massing m, Mass house) {
		for (Mass a : m.masses()) if (a.id().equals(house.parent())) return a;
		return null;
	}

	private static Mass house(Massing m) {
		for (Mass a : m.masses()) if (a.kind().equals("stairhouse")) return a;
		return null;
	}

	static void guard(BuildContext c) {
		Massing m = c.massing();
		Mass house = house(m);
		if (house == null || TowerBlock.of(m, TowerBlock.SD) == null) return;
		Mass tier = topTier(m, house);
		if (tier == null) return;
		int y = tier.topSlab(m.grid()) + 3; // the roof deck is at topSlab + 1 and the parapet wall on it; the rail stands on the wall
		for (int z = tier.z(); z < tier.maxZ(); z++)
			for (int x = tier.x(); x < tier.maxX(); x++) {
				if (x != tier.x() && x != tier.maxX() - 1 && z != tier.z() && z != tier.maxZ() - 1) continue;
				if (house.contains(x, z)) continue;
				if (c.role(x, y - 1, z) != Role.WALL) c.box(Role.WALL, Layer.ROOF, -1, x, y - 1, z, 1, 1, 1);
				c.box(Role.RAIL, Layer.ROOF, -1, x, y, z, 1, 1, 1);
			}
	}

	static void doors(BuildContext c) {
		Massing m = c.massing();
		Mass house = house(m);
		TowerBlock b = TowerBlock.of(m, TowerBlock.SD);
		if (house == null || b == null) return;
		int n = m.grid().count() - 1;
		Face north = null;
		for (Face f : c.faces()) if (f.mass().equals(house.id()) && f.side() == Facing.NORTH && f.storey() == n) north = f;
		if (north == null) throw new IllegalStateException("the stair house has no north face");
		int[] sw = b.stairwell(), sh = b.shaft();
		door(c, north, sw[0] + sw[2] / 2); // the column of the stairwell door on every other storey
		door(c, north, sh[0] + (sh[2] - 1) / 2);
	}

	/**
	 * One roof door. The stair house floor stands on the tier's top slab, one below the roof deck's standing
	 * level, so the doorway starts one up and the cell inside it is a stair block rising toward the door (a sill step; the raster turns a STAIR beside a DOOR toward it) (a walker cannot climb a
	 * block from under a 2-high lintel): the way out is level with the deck.
	 */
	private static void door(BuildContext c, Face f, int pos) {
		int ix = f.insideX(pos), iz = f.insideZ(pos), y = f.y0();
		String room = c.roomAt(ix, y, iz);
		if (room == null) throw new IllegalStateException("roof door at " + f.x(pos) + "," + f.z(pos) + " opens into no room");
		if (c.role(f.outsideX(pos), y + 1, f.outsideZ(pos)) != Role.AIR) throw new IllegalStateException("roof door at " + f.x(pos) + "," + f.z(pos) + " is blocked outside");
		c.box(Role.STAIR, Layer.INTERIOR, f.storey(), ix, y, iz, 1, 1, 1);
		c.box(Role.DOOR, Layer.SHELL, f.storey(), f.x(pos), y + 1, f.z(pos), 1, 2, 1);
		c.opening(OpeningKind.DOOR, Box3.of(f.x(pos), y + 1, f.z(pos), 1, 2, 1), f.side(), room, Opening.OUTSIDE);
	}
}
