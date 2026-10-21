package dev.fractalbuildings.core.ir;

import java.util.Objects;

/**
 * A lot in world coordinates: x in [x, x + w), z in [z, z + d). {@code front} is the side facing
 * the street. Rectangles only for now; Phase 5 may add a polygon alongside.
 *
 * <p>{@code airRights} (Phase 5 JT; JSON keys {@code airRights} and {@code frontY} written only when above 0): how far
 * past the front edge, over the street, a building may extend, at world y of at least {@code frontY + 3}
 * ({@code frontY} is the street surface in front of the front edge, its first free y). The {@code in-lot}
 * invariant allows voxels in that strip and nowhere else outside the lot.
 */
public record Lot(String id, int x, int z, int w, int d, Facing front, int airRights, int frontY) {
	/** Clearance, in blocks, between the street surface and anything built in the air-rights strip. */
	public static final int AIR_CLEARANCE = 3;

	public Lot {
		Objects.requireNonNull(id);
		Objects.requireNonNull(front);
		if (w <= 0 || d <= 0) throw new IllegalArgumentException("lot " + w + "x" + d);
		if (airRights < 0) throw new IllegalArgumentException("airRights " + airRights);
	}

	/** A lot without air rights. */
	public Lot(String id, int x, int z, int w, int d, Facing front) { this(id, x, z, w, d, front, 0, 0); }

	public Lot withAir(int airRights, int frontY) { return new Lot(id, x, z, w, d, front, airRights, frontY); }

	public boolean containsColumn(int wx, int wz) {
		return wx >= x && wx < x + w && wz >= z && wz < z + d;
	}

	/** True if the column lies in the strip over the street in front of the front edge, {@code airRights} deep. */
	public boolean inAirStrip(int wx, int wz) {
		if (airRights <= 0) return false;
		return switch (front) {
			case NORTH -> wx >= x && wx < x + w && wz < z && wz >= z - airRights;
			case SOUTH -> wx >= x && wx < x + w && wz >= z + d && wz < z + d + airRights;
			case WEST -> wz >= z && wz < z + d && wx < x && wx >= x - airRights;
			case EAST -> wz >= z && wz < z + d && wx >= x + w && wx < x + w + airRights;
		};
	}

	/** True if a voxel at world {@code (wx, wy, wz)} is inside the lot's column or in its air-rights strip high enough above the street. */
	public boolean allows(int wx, int wy, int wz) {
		return containsColumn(wx, wz) || inAirStrip(wx, wz) && wy >= frontY + AIR_CLEARANCE;
	}
}
