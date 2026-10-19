package dev.fractalbuildings.core.gen.v1.style;

import java.util.Map;

/**
 * Style JSON key {@code interior}. Owned by the interiors step (4.5); add fields with defaults that
 * keep old styles' output unchanged.
 *
 * @param roomMin smallest room side (interior air, blocks)
 */
public record InteriorStyle(int roomMin) {
	public static final InteriorStyle DEFAULT = new InteriorStyle(2);

	public static InteriorStyle from(Map<String, Object> o, String path) {
		return new InteriorStyle(Json.integer(o, "roomMin", DEFAULT.roomMin, path));
	}
}
