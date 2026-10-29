package dev.fractalbuildings.core.raster;

import dev.fractalbuildings.core.ir.Facing;

/**
 * Block-state detail of one cell beyond its block id: which way it faces, which half, hinge, axis and which sides it
 * connects to. The mod maps (block id, shape) to a block state (A4); unused components are null / 0.
 *
 * <ul>
 *   <li>{@code facing}: doors, stairs: the world direction. Door: the direction a person walks through it from
 *       the {@code from} room to the {@code to} room (Minecraft's placement facing). Stair: the direction of ascent,
 *       i.e. towards the next higher step (Minecraft's stair {@code facing}).
 *   <li>{@code half}: {@link Half#LOWER}/{@link Half#UPPER} for doors; {@link Half#BOTTOM}/{@link Half#TOP} for
 *       stairs and slabs.
 *   <li>{@code hinge}: doors; a single door is LEFT, a 2-wide doorway is LEFT then RIGHT as seen walking through.
 *   <li>{@code axis}: logs, beams and pillars (the long axis of the box that made the cell).
 *   <li>{@code corner}: stair {@code shape} state (inner or outer corner), null for a straight stair.
 *   <li>{@code connect}: bit mask of {@link #N}, {@link #E}, {@link #S}, {@link #W} (bit = {@code Facing.ordinal()})
 *       for panes, fences and walls: which horizontal neighbours they join.
 * </ul>
 */
public record Shape(Facing facing, Half half, Hinge hinge, Axis3 axis, int connect, Corner corner) {
	/** Stair {@code shape} state beyond straight (null in {@link Shape#corner} means straight), as Minecraft names them. */
	public enum Corner { INNER_LEFT, INNER_RIGHT, OUTER_LEFT, OUTER_RIGHT }

	/** A shape with no stair corner. */
	public Shape(Facing facing, Half half, Hinge hinge, Axis3 axis, int connect) { this(facing, half, hinge, axis, connect, null); }

	public enum Half { LOWER, UPPER, BOTTOM, TOP }

	public enum Hinge { LEFT, RIGHT }

	/** Axis of a log; the ir {@code Axis} has no y. */
	public enum Axis3 { X, Y, Z }

	public static final int N = 1, E = 2, S = 4, W = 8;

	/** A plain block: nothing to set. */
	public static final Shape NONE = new Shape(null, null, null, null, 0);

	public Shape {
		if (connect < 0 || connect > 15) throw new IllegalArgumentException("connect " + connect);
	}

	/** The connect bit of a horizontal direction. */
	public static int bit(Facing f) { return 1 << f.ordinal(); }

	public boolean connects(Facing f) { return (connect & bit(f)) != 0; }

	public static Shape door(Facing facing, Half half, Hinge hinge) { return new Shape(facing, half, hinge, null, 0); }

	public static Shape stair(Facing facing) { return new Shape(facing, Half.BOTTOM, null, null, 0); }

	public static Shape log(Axis3 axis) { return new Shape(null, null, null, axis, 0); }

	public static Shape connected(int mask) { return mask == 0 ? NONE : new Shape(null, null, null, null, mask); }

	public static Shape stair(Facing facing, Corner corner) { return new Shape(facing, Half.BOTTOM, null, null, 0, corner); }

	/** A slab (bottom or top half). */
	public static Shape slab(Half half) { return new Shape(null, half, null, null, 0, null); }
}
