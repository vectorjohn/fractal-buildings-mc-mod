package dev.fractalbuildings.core.gen.v1.style;

import java.util.Map;

/**
 * Style JSON key {@code interior}. Owned by the interiors step (4.5); add fields with defaults that
 * keep old styles' output unchanged.
 *
 * @param roomMin smallest room side (interior air, blocks); partitions leave at least this on both sides
 * @param roomArea target area of a room (interior cells) above which a room is split again; 0 = never
 *     split (one room per mass and floor)
 * @param corridorAbove a floor whose area / roomArea reaches this gets a 2-wide corridor along its long
 *     axis; 0 = never
 * @param loopPercent chance (percent) that a pair of neighbouring rooms not joined by the spanning tree
 *     gets a door anyway
 */
public record InteriorStyle(int roomMin, int roomArea, int corridorAbove, int loopPercent) {
	public static final InteriorStyle DEFAULT = new InteriorStyle(2, 0, 0, 0);

	public InteriorStyle {
		if (roomMin < 1) throw new IllegalArgumentException("interior.roomMin " + roomMin);
		if (roomArea < 0 || corridorAbove < 0 || loopPercent < 0 || loopPercent > 100) throw new IllegalArgumentException("interior " + roomArea + " " + corridorAbove + " " + loopPercent);
	}

	public static InteriorStyle from(Map<String, Object> o, String path) {
		return new InteriorStyle(
				Json.integer(o, "roomMin", DEFAULT.roomMin, path),
				Json.integer(o, "roomArea", DEFAULT.roomArea, path),
				Json.integer(o, "corridorAbove", DEFAULT.corridorAbove, path),
				Json.integer(o, "loopPercent", DEFAULT.loopPercent, path));
	}
}
