package dev.fractalbuildings.core.gen.v1.model;

import dev.fractalbuildings.core.ir.Facing;

/**
 * One exterior wall run of one mass on one grid storey (the result of CGA's {@code comp}): the wall
 * cells on the mass's {@code side} at positions [a, b) along the side (x for north/south faces, z for
 * east/west), in the wall plane {@code plane} (z for north/south, x for east/west), for local y in
 * [y0, y0 + height). Outside every position is open air at this storey (no other mass there).
 *
 * <p>{@code convexA}/{@code convexB}: the run's first/last position is a corner of the mass (an outside
 * corner of the building); otherwise the run ends where another mass's wall abuts. Facade rules split
 * [a, b) into bays; the wall cells at a and b - 1 belong to corners or junctions.
 */
public record Face(String mass, Facing side, int storey, int plane, int a, int b, int y0, int height, boolean convexA, boolean convexB) {
	public int length() { return b - a; }

	/** Local x of position p on this face. */
	public int x(int p) { return side == Facing.NORTH || side == Facing.SOUTH ? p : plane; }

	/** Local z of position p on this face. */
	public int z(int p) { return side == Facing.NORTH || side == Facing.SOUTH ? plane : p; }

	/** Local x of the cell just inside the wall at position p. */
	public int insideX(int p) { return x(p) - side.dx; }

	/** Local z of the cell just inside the wall at position p. */
	public int insideZ(int p) { return z(p) - side.dz; }

	/** Local x of the cell just outside the wall at position p. */
	public int outsideX(int p) { return x(p) + side.dx; }

	/** Local z of the cell just outside the wall at position p. */
	public int outsideZ(int p) { return z(p) + side.dz; }
}
