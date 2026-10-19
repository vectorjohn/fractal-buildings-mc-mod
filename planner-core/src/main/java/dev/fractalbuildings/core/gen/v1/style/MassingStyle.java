package dev.fractalbuildings.core.gen.v1.style;

import java.util.Map;

/**
 * Style JSON key {@code massing}. Owned by the massing step (4.2); add fields here (with defaults
 * that keep old styles' output unchanged) and read them in {@link #from}.
 *
 * @param ground slab-to-slab height of the ground storey (>= 4)
 * @param typical slab-to-slab height of the other storeys (>= 4)
 * @param jetty largest jetty overhang per side, 0 = no jetties (at most MassingCheck.JETTY_MAX)
 */
public record MassingStyle(int ground, int typical, int jetty) {
	public static final MassingStyle DEFAULT = new MassingStyle(4, 4, 0);

	public static MassingStyle from(Map<String, Object> o, String path) {
		return new MassingStyle(
				Json.integer(o, "ground", DEFAULT.ground, path),
				Json.integer(o, "typical", DEFAULT.typical, path),
				Json.integer(o, "jetty", DEFAULT.jetty, path));
	}
}
