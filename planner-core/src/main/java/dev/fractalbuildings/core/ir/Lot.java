package dev.fractalbuildings.core.ir;

import java.util.Objects;

/**
 * A lot in world coordinates: x in [x, x + w), z in [z, z + d). {@code front} is the side facing
 * the street. Rectangles only for now; Phase 5 may add a polygon alongside.
 */
public record Lot(String id, int x, int z, int w, int d, Facing front) {
	public Lot {
		Objects.requireNonNull(id);
		Objects.requireNonNull(front);
		if (w <= 0 || d <= 0) throw new IllegalArgumentException("lot " + w + "x" + d);
	}

	public boolean containsColumn(int wx, int wz) {
		return wx >= x && wx < x + w && wz >= z && wz < z + d;
	}
}
