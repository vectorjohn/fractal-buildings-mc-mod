package dev.fractalbuildings.core.raster;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Role;

/**
 * Shapes of cells from their neighbours in a piece's own grid (never from the world, so a chunk border changes
 * nothing). {@link #of} is the single entry; the hints carry what the grid cannot know: the opening a cell belongs to
 * and the box that made it.
 */
public final class Shapes {
	private Shapes() {}

	/** Roles of the cells around a position; {@link Role#AIR} where nothing is placed. */
	@FunctionalInterface
	public interface RoleView {
		Role role(int x, int y, int z);
	}

	/**
	 * What the grid does not know about a cell, all in world terms. {@code doorway}: world box of the door opening
	 * containing the cell, with the world {@code doorFacing} (from to). {@code ascent}: world ascent direction of the
	 * stair opening containing it. {@code logAxis}: long axis of the box that made it.
	 */
	public record Hints(Box3 doorway, Facing doorFacing, Facing ascent, Shape.Axis3 logAxis) {
		public static final Hints NONE = new Hints(null, null, null, null);
	}

	public static Shape of(RoleView g, int x, int y, int z, Role role, String blockId, Hints h) {
		switch (role) {
			case DOOR: return door(g, x, y, z, h);
			case STAIR: return stair(g, x, y, z, h);
			case BEAM: case TRIM:
				return h.logAxis() != null && axisBlock(blockId) ? Shape.log(h.logAxis()) : Shape.NONE;
			case WINDOW:
				return connectable(blockId) ? Shape.connected(connect(g, x, y, z)) : Shape.NONE;
			case FENCE: case RAIL:
				return Shape.connected(connect(g, x, y, z));
			default:
				return Shape.NONE;
		}
	}

	/** Blocks with an {@code axis} property that a box of beams or trim maps to. */
	static boolean axisBlock(String id) {
		return id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae") || id.endsWith("_pillar")
				|| id.endsWith("_block") && (id.contains("hay") || id.contains("bone"));
	}

	/** Blocks that join their neighbours: panes and bars. Plain glass is a full block. */
	static boolean connectable(String id) { return id.endsWith("_pane") || id.endsWith("bars"); }

	/** Bit mask of the horizontal neighbours that are solid (walls, windows, fences and the rest). */
	public static int connect(RoleView g, int x, int y, int z) {
		int m = 0;
		for (Facing f : Facing.values()) if (g.role(x + f.dx, y, z + f.dz).solid) m |= Shape.bit(f);
		return m;
	}

	static Shape door(RoleView g, int x, int y, int z, Hints h) {
		int below = 0;
		while (g.role(x, y - 1 - below, z) == Role.DOOR) below++;
		Shape.Half half = below % 2 == 0 ? Shape.Half.LOWER : Shape.Half.UPPER;
		Facing f = h.doorFacing();
		if (f == null) {
			// No opening: face across the wall (the wall runs through the cell's solid x or z neighbours).
			boolean alongX = g.role(x - 1, y, z).solid || g.role(x + 1, y, z).solid;
			f = alongX ? Facing.NORTH : Facing.EAST;
		}
		Facing left = f.rotate(3);
		int width = 1, k = 0;
		Box3 d = h.doorway();
		if (d != null) {
			boolean alongX = f == Facing.NORTH || f == Facing.SOUTH;
			width = alongX ? d.sx() : d.sz();
			int p = alongX ? x - d.x() : z - d.z(), size = width;
			// Position counted from the left edge as a walker facing f sees it.
			k = (alongX ? (left.dx > 0) : (left.dz > 0)) ? size - 1 - p : p;
		}
		return Shape.door(f, half, k % 2 == 0 ? Shape.Hinge.LEFT : Shape.Hinge.RIGHT);
	}

	static Shape stair(RoleView g, int x, int y, int z, Hints h) {
		Facing a = h.ascent();
		// Interior flight (STAIR opening): only the top cell of a step column is a stair; the cells under it are its support, a full block (STAIRFILL).
		if (a != null && g.role(x, y + 1, z) == Role.STAIR) return Shape.NONE;
		if (a != null && g.role(x + a.dx, y + 1, z + a.dz).solid) return Shape.stair(a);
		// The top step of an interior flight's last column stands in the slab layer: nothing higher ahead (it is left sideways onto the floor
		// beside it), but it must stay a stair, never a full block, or the walk up to it is a jump. Cells buried under it take the usual rules.
		if (a != null && g.role(x, y + 1, z) != Role.STAIR) return Shape.stair(a);
		// Prefer the next step, then a floor or surface it climbs to; never a wall.
		for (Facing f : Facing.values()) if (g.role(x + f.dx, y + 1, z + f.dz) == Role.STAIR) return Shape.stair(f);
		for (Facing f : Facing.values()) {
			Role r = g.role(x + f.dx, y + 1, z + f.dz);
			if (r == Role.FLOOR || r == Role.PAVING || r.isRoad() || r == Role.DECK || r == Role.GROUND) return Shape.stair(f);
		}
		for (Facing f : Facing.values()) if (g.role(x + f.dx, y + 1, z + f.dz) == Role.DOOR) return Shape.stair(f); // a threshold step up to a doorway, whatever stands under the door (a ground-floor door has the slab under it; STEPTOP)
		// A landing cell entered from a lower step: it stays a stair facing on, away from the step below, because a full block there is a one block jump (STEPTOP).
		for (Facing f : Facing.values()) if (g.role(x + f.dx, y - 1, z + f.dz) == Role.STAIR && g.role(x, y + 1, z) != Role.STAIR) return Shape.stair(f.rotate(2));
		return Shape.NONE;
	}
}
