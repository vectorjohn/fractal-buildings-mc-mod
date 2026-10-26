package dev.fractalbuildings.core.gen.v1.types.highrise;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.interior.InteriorPass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Role;

/**
 * The lift shaft and the stairwell's lights (plan docs/plans/highrises.md, B3a), emitted after the interior pass.
 * The plates leave the shaft's 3 x 3 cells without room or cut and wall them in; this opens the column: air from the
 * pit floor (storey 0's floor, kept) up through every slab to the top habitable storey's ceiling (the machine room's
 * floor, kept), a 1 x 2 glass doorway ({@code window} role, not a facade window, not a door; later Create replaces it)
 * in the hall-side wall at the shaft's middle on every storey, a {@code light} on the pit floor against the back wall,
 * and a {@code ceiling_light} in the landing row of each stairwell at its door cell. With stairwell depth 4 a
 * 2-high {@code rail} screen closes the guard row between the landing row and the core on every storey.
 */
public final class TowerShaft {
	private TowerShaft() {}

	public static void emit(BuildContext c) {
		Massing m = c.massing();
		TowerBlock b = TowerBlock.of(m, TowerBlock.SD);
		if (b == null) return;
		StoreyGrid g = m.grid();
		int n = g.count() - 1; // habitable storeys 0..n-1, the stair house is storey n
		int[] sh = b.shaft(), sw = b.stairwell();
		int midX = sh[0] + sh[2] / 2, hallWallZ = b.hallZ1();
		boolean carve = shaftFree(c, g, sh, n);
		for (int s = 0; s < n && carve; s++) {
			int top = s == n - 1 ? g.y(n) : g.y(s + 1) + 1; // through the slab, except under the machine room
			c.box(Role.AIR, Layer.INTERIOR, s, sh[0], g.y(s) + 1, sh[1], sh[2], top - g.y(s) - 1, sh[3]);
			c.box(Role.WINDOW, Layer.INTERIOR, s, midX, g.y(s) + 1, hallWallZ, 1, 2, 1);
			if (b.guardRow() >= 0) c.box(Role.RAIL, Layer.INTERIOR, s, sw[0] + 1, g.y(s) + 1, b.guardRow(), sw[2] - 2, 2, 1);
		}
		// pit light: a corner cell against the back wall, in the storey 0 room the doorway faces
		String hall = c.roomAt(midX, g.y(0) + 1, hallWallZ - 1);
		if (carve && hall != null) c.hook("light", hall, sh[0], g.y(0) + 1, sh[1] + sh[3] - 1, Facing.NORTH);
		// stairwell landing light at the door cell, every storey incl. the stair house
		int doorX = sw[0] + sw[2] / 2;
		for (int s = 0; s <= n; s++) {
			String well = c.roomAt(doorX, g.y(s) + 1, sw[1]);
			if (well != null && isStairwell(c, well)) c.hook(InteriorPass.CEILING_LIGHT, well, doorX, g.y(s) + 1, sw[1], Facing.NORTH);
		}
	}

	/** True if no room of any habitable storey stands on the shaft's cells: every floor came from a plate. A floor the generic planner drew (a plate that did not fit) has rooms there, and carving the column would open holes in them. */
	static boolean shaftFree(BuildContext c, StoreyGrid g, int[] sh, int n) {
		for (int s = 0; s < n; s++)
			for (int dz = 0; dz < sh[3]; dz++)
				for (int dx = 0; dx < sh[2]; dx++) if (c.roomAt(sh[0] + dx, g.y(s) + 1, sh[1] + dz) != null) return false;
		return true;
	}

	private static boolean isStairwell(BuildContext c, String id) {
		for (var r : c.rooms()) if (r.id().equals(id)) return r.use().equals("stairwell");
		return false;
	}
}
