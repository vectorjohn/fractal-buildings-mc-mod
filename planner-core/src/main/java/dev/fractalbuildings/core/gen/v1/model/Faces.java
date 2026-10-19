package dev.fractalbuildings.core.gen.v1.model;

import dev.fractalbuildings.core.ir.Facing;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code comp} for a {@link Massing}: every exterior wall run, per mass, per grid storey, per side.
 * Order: mass order, then storey, then side (north, east, south, west), then position. A wall
 * position is exterior on storey s when the column just outside it is not inside any other mass
 * that spans s (so shared wall strips and walls covered by a neighbour are left out).
 */
public final class Faces {
	private Faces() {}

	public static List<Face> of(Massing m) {
		List<Face> out = new ArrayList<>();
		StoreyGrid g = m.grid();
		for (Mass mass : m.masses()) {
			for (int s = mass.from(); s < mass.to(); s++) {
				int y0 = mass.open() && s > mass.from() ? g.y(s) : g.y(s) + 1;
				int height = g.y(s + 1) - y0;
				for (Facing side : Facing.values()) runs(m, mass, s, side, y0, height, out);
			}
		}
		return out;
	}

	/** The faces of one mass on one storey and side (convenience for tests and facade rules). */
	public static List<Face> of(Massing m, String massId, int storey, Facing side) {
		List<Face> out = new ArrayList<>();
		for (Face f : of(m)) if (f.mass().equals(massId) && f.storey() == storey && f.side() == side) out.add(f);
		return out;
	}

	private static void runs(Massing m, Mass mass, int s, Facing side, int y0, int height, List<Face> out) {
		boolean ns = side == Facing.NORTH || side == Facing.SOUTH;
		int lo = ns ? mass.x() : mass.z();
		int hi = ns ? mass.maxX() : mass.maxZ();
		int plane = switch (side) {
			case NORTH -> mass.z();
			case SOUTH -> mass.maxZ() - 1;
			case WEST -> mass.x();
			case EAST -> mass.maxX() - 1;
		};
		int start = -1;
		for (int p = lo; p <= hi; p++) {
			boolean ext = false;
			if (p < hi) {
				int cx = (ns ? p : plane) + side.dx, cz = (ns ? plane : p) + side.dz;
				ext = !m.covered(mass, s, cx, cz);
			}
			if (ext && start < 0) start = p;
			if (!ext && start >= 0) {
				out.add(new Face(mass.id(), side, s, plane, start, p, y0, height, start == lo, p == hi));
				start = -1;
			}
		}
	}
}
