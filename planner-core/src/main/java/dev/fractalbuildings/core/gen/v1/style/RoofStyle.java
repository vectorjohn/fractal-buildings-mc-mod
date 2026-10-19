package dev.fractalbuildings.core.gen.v1.style;

import java.util.Map;

/**
 * Style JSON key {@code roof}. Owned by the roofs step (4.3); add fields with defaults that keep old
 * styles' output unchanged.
 *
 * @param forms roof form weights ({@code {"gable": 3, "hip": 1}}), for weighted {@code Choice}
 * @param rise pitch: rise blocks up per {@code run} blocks in
 * @param run pitch run (> 0)
 * @param overhang eave overhang in blocks (0 = none)
 */
public record RoofStyle(Map<String, Integer> forms, int rise, int run, int overhang) {
	public static final RoofStyle DEFAULT = new RoofStyle(Map.of("gable", 1), 1, 1, 0);

	public static RoofStyle from(Map<String, Object> o, String path) {
		RoofStyle r = new RoofStyle(
				Json.weights(o, "forms", DEFAULT.forms, path),
				Json.integer(o, "rise", DEFAULT.rise, path),
				Json.integer(o, "run", DEFAULT.run, path),
				Json.integer(o, "overhang", DEFAULT.overhang, path));
		if (r.run <= 0 || r.rise < 0) throw new IllegalArgumentException(path + ": pitch " + r.rise + "/" + r.run);
		return r;
	}
}
