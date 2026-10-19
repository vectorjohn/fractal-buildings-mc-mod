package dev.fractalbuildings.core.gen.v1.style;

import java.util.Map;

/**
 * Style JSON key {@code facade}. Owned by the facades step (4.4); add fields with defaults that keep
 * old styles' output unchanged.
 *
 * @param bayMin smallest bay width (wall positions per window bay)
 * @param bayMax largest bay width
 * @param sill window bottom, in blocks above the storey's standing level (y0 of the face)
 * @param windowHeight window height in blocks
 */
public record FacadeStyle(int bayMin, int bayMax, int sill, int windowHeight) {
	public static final FacadeStyle DEFAULT = new FacadeStyle(2, 3, 1, 1);

	public static FacadeStyle from(Map<String, Object> o, String path) {
		FacadeStyle f = new FacadeStyle(
				Json.integer(o, "bayMin", DEFAULT.bayMin, path),
				Json.integer(o, "bayMax", DEFAULT.bayMax, path),
				Json.integer(o, "sill", DEFAULT.sill, path),
				Json.integer(o, "windowHeight", DEFAULT.windowHeight, path));
		if (f.bayMin < 1 || f.bayMax < f.bayMin || f.windowHeight < 1 || f.sill < 0) throw new IllegalArgumentException(path + ": bad facade " + f);
		return f;
	}
}
