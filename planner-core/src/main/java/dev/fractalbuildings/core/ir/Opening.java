package dev.fractalbuildings.core.ir;

import java.util.Objects;

/**
 * A connection in the room graph: a door or window in a wall, or a stair between storeys.
 * {@code from} and {@code to} are room ids or {@link #OUTSIDE}. {@code facing} is the direction a
 * person walking from {@code from} to {@code to} faces (for a stair: the direction of ascent).
 * The box is the opening's own voxels (doorway, window pane, stair run).
 */
public record Opening(String id, OpeningKind kind, Box3 box, Facing facing, String from, String to) {
	public static final String OUTSIDE = "outside";

	public Opening {
		Objects.requireNonNull(id);
		Objects.requireNonNull(kind);
		Objects.requireNonNull(box);
		Objects.requireNonNull(facing);
		Objects.requireNonNull(from);
		Objects.requireNonNull(to);
	}
}
