package dev.fractalbuildings.core.raster;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns placed buildings and ground boxes into cells: role, block id and {@link Shape}, each cell owned by exactly one
 * piece. Pure and order-independent: a piece's cells depend only on its own boxes (and, for ground, on the buildings
 * it yields to), never on what a neighbouring chunk has placed.
 *
 * <p><b>Ownership</b> (see docs/IR.md, "Ownership of cells"): a building owns every cell its boxes touch (a solid cell
 * or an explicit {@code air} clear); the ground owns the cells of its boxes that no building touches. Of two buildings
 * touching the same cell the one earlier in the list owns it. Every piece is clipped to the {@code clip} box, so
 * pieces that partition space partition the cells.
 *
 * <p>Each piece is rastered into a grid over {@code clip} grown by 1 block on every side; neighbours across the clip
 * border are read from that grid, so shapes (connections, stair direction) agree on both sides of a chunk border.
 */
public final class PieceRaster {
	private PieceRaster() {}

	/**
	 * Cells of building {@code index} of {@code all} (the buildings of its tile, in list order) inside {@code clip}.
	 * Cells touched by an earlier building are not emitted.
	 */
	public static void building(List<PlacedBuilding> all, int index, Box3 clip, CellSink out) {
		PlacedBuilding b = all.get(index);
		Box3 region = grow(clip);
		Grid g = Grid.of(b, region);
		if (g == null) return;
		List<Grid> earlier = new ArrayList<>();
		for (int i = 0; i < index; i++) {
			Grid e = Grid.of(all.get(i), clip);
			if (e != null) earlier.add(e);
		}
		emit(g, clip, b.shell().spec().style(), null, earlier, out);
	}

	/** Cells of one building alone (no other building to yield to). */
	public static void building(PlacedBuilding b, Box3 clip, CellSink out) { building(List.of(b), 0, clip, out); }

	/**
	 * Cells of the ground boxes (world frame, list order, later wins, {@code air} clears) inside {@code clip}, except
	 * where one of {@code buildings} touches the cell.
	 */
	public static void ground(List<Box> boxes, List<PlacedBuilding> buildings, StyleAt style, Box3 clip, CellSink out) {
		Grid g = Grid.ofWorld(boxes, grow(clip));
		if (g == null) return;
		List<Grid> yield = new ArrayList<>();
		for (PlacedBuilding b : buildings) {
			Grid e = Grid.of(b, clip);
			if (e != null) yield.add(e);
		}
		emit(g, clip, null, style, yield, out);
	}

	private static void emit(Grid g, Box3 clip, String style, StyleAt styleAt, List<Grid> yield, CellSink out) {
		Box3 r = intersect(g.region, clip);
		if (r == null) return;
		for (int y = r.y(); y < r.maxY(); y++)
			for (int z = r.z(); z < r.maxZ(); z++)
				for (int x = r.x(); x < r.maxX(); x++) {
					int v = g.val(x, y, z);
					if (v == 0) continue;
					boolean taken = false;
					for (Grid e : yield) if (e.val(x, y, z) != 0) { taken = true; break; }
					if (taken) continue;
					Role role = v < 0 ? Role.AIR : g.roles[v - 1];
					String st = style != null ? style : styleAt.at(x, z);
					String id = Variant.pick(st, role, x, y, z);
					out.cell(x, y, z, role, id, role == Role.AIR ? Shape.NONE : Shapes.of(g, x, y, z, role, id, g.hints(x, y, z, v)));
				}
	}

	static Box3 grow(Box3 b) { return new Box3(b.x() - 1, b.y() - 1, b.z() - 1, b.sx() + 2, b.sy() + 2, b.sz() + 2); }

	static Box3 intersect(Box3 a, Box3 b) {
		int x0 = Math.max(a.x(), b.x()), y0 = Math.max(a.y(), b.y()), z0 = Math.max(a.z(), b.z());
		int x1 = Math.min(a.maxX(), b.maxX()), y1 = Math.min(a.maxY(), b.maxY()), z1 = Math.min(a.maxZ(), b.maxZ());
		return x1 <= x0 || y1 <= y0 || z1 <= z0 ? null : new Box3(x0, y0, z0, x1 - x0, y1 - y0, z1 - z0);
	}

	/**
	 * Dense world-frame grid over {@code region}. Value 0 = untouched, -1 = cleared by an {@code air} box,
	 * k + 1 = made by box k (index into {@code roles}, {@code worlds}).
	 */
	static final class Grid implements Shapes.RoleView {
		final Box3 region;
		final int[] vals;
		Role[] roles;
		Box3[] worlds;
		Shape.Axis3[] axes;
		PlacedBuilding building;
		List<Opening> openings = List.of();

		private Grid(Box3 region) {
			this.region = region;
			this.vals = new int[(int) region.volume()];
		}

		int val(int x, int y, int z) {
			if (!region.contains(x, y, z)) return 0;
			return vals[((y - region.y()) * region.sz() + (z - region.z())) * region.sx() + (x - region.x())];
		}

		@Override
		public Role role(int x, int y, int z) {
			int v = val(x, y, z);
			return v <= 0 ? Role.AIR : roles[v - 1];
		}

		/** Grid of a building over the part of {@code region} its boxes can reach, or null when none. */
		static Grid of(PlacedBuilding b, Box3 region) {
			Placement p = b.placement();
			List<Box> boxes = new ArrayList<>(b.shell().boxes());
			boxes.addAll(b.siteBoxes());
			Box3[] worlds = new Box3[boxes.size()];
			Box3 hull = null;
			for (int i = 0; i < worlds.length; i++) {
				worlds[i] = p.toWorld(boxes.get(i).box());
				hull = hull == null ? worlds[i] : union(hull, worlds[i]);
			}
			if (hull == null) return null;
			// room parts are the building's interior air: it clears them, so terrain, trees and grass yield
			List<Box3> parts = new ArrayList<>();
			for (var room : b.shell().rooms())
				for (Box3 part : room.parts()) {
					Box3 w = p.toWorld(part);
					parts.add(w);
					hull = union(hull, w);
				}
			Box3 r = intersect(region, grow(hull));
			if (r == null) return null;
			Grid g = new Grid(r);
			for (Box3 w : parts) g.fill(w, -1);
			g.building = b;
			g.openings = b.shell().openings();
			g.roles = new Role[boxes.size()];
			g.worlds = worlds;
			g.axes = new Shape.Axis3[boxes.size()];
			boolean odd = b.rotation() % 2 == 1;
			for (int i = 0; i < worlds.length; i++) {
				Box b0 = boxes.get(i);
				g.roles[i] = b0.role();
				Box3 l = b0.box();
				Shape.Axis3 a;
				if (l.sy() >= l.sx() && l.sy() >= l.sz()) a = Shape.Axis3.Y;
				else if (l.sx() >= l.sz()) a = odd ? Shape.Axis3.Z : Shape.Axis3.X;
				else a = odd ? Shape.Axis3.X : Shape.Axis3.Z;
				g.axes[i] = a;
				g.fill(worlds[i], b0.role() == Role.AIR ? -1 : i + 1);
			}
			return g;
		}

		static Grid ofWorld(List<Box> boxes, Box3 region) {
			Box3 hull = null;
			for (Box b : boxes) hull = hull == null ? b.box() : union(hull, b.box());
			if (hull == null) return null;
			Box3 r = intersect(region, hull);
			if (r == null) return null;
			Grid g = new Grid(r);
			g.roles = new Role[boxes.size()];
			g.worlds = new Box3[boxes.size()];
			g.axes = new Shape.Axis3[boxes.size()];
			for (int i = 0; i < boxes.size(); i++) {
				Box b = boxes.get(i);
				g.roles[i] = b.role();
				g.worlds[i] = b.box();
				Box3 l = b.box();
				g.axes[i] = l.sy() >= l.sx() && l.sy() >= l.sz() ? Shape.Axis3.Y : l.sx() >= l.sz() ? Shape.Axis3.X : Shape.Axis3.Z;
				g.fill(b.box(), b.role() == Role.AIR ? -1 : i + 1);
			}
			return g;
		}

		private void fill(Box3 w, int v) {
			Box3 c = intersect(w, region);
			if (c == null) return;
			for (int y = c.y(); y < c.maxY(); y++)
				for (int z = c.z(); z < c.maxZ(); z++) {
					int base = ((y - region.y()) * region.sz() + (z - region.z())) * region.sx() - region.x();
					for (int x = c.x(); x < c.maxX(); x++) vals[base + x] = v;
				}
		}

		/** The openings and box axis relevant to one non-air cell. */
		Shapes.Hints hints(int x, int y, int z, int v) {
			Shape.Axis3 axis = axes[v - 1];
			if (building == null) return new Shapes.Hints(null, null, null, axis);
			Role role = roles[v - 1];
			if (role != Role.DOOR && role != Role.STAIR) return new Shapes.Hints(null, null, null, axis);
			Placement p = building.placement();
			for (Opening o : openings) {
				boolean door = role == Role.DOOR && (o.kind() == OpeningKind.DOOR || o.kind() == OpeningKind.ARCH);
				boolean stair = role == Role.STAIR && o.kind() == OpeningKind.STAIR;
				if (!door && !stair) continue;
				Box3 w = p.toWorld(o.box());
				if (!w.contains(x, y, z)) continue;
				Facing f = p.toWorld(o.facing());
				return door ? new Shapes.Hints(w, f, null, axis) : new Shapes.Hints(null, null, f, axis);
			}
			return new Shapes.Hints(null, null, null, axis);
		}

		private static Box3 union(Box3 a, Box3 b) {
			int x0 = Math.min(a.x(), b.x()), y0 = Math.min(a.y(), b.y()), z0 = Math.min(a.z(), b.z());
			return new Box3(x0, y0, z0, Math.max(a.maxX(), b.maxX()) - x0, Math.max(a.maxY(), b.maxY()) - y0, Math.max(a.maxZ(), b.maxZ()) - z0);
		}
	}
}
