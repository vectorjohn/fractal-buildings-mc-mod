package dev.fractalbuildings.core.gen.v1.style;

import java.util.Map;

/**
 * Style JSON key {@code massing}. Owned by the massing step (4.2); add fields here (with defaults
 * that keep old styles' output unchanged) and read them in {@link #from}.
 *
 * @param ground slab-to-slab height of the ground storey (>= 4)
 * @param typical slab-to-slab height of the other storeys (>= 4)
 * @param jetty largest jetty overhang per side, 0 = no jetties (at most MassingCheck.JETTY_MAX)
 * @param plans footprint plan weights over {@code box, L, T, U, courtyard} (a plan not listed has weight 0)
 * @param jettyChance percent of multi-storey buildings whose upper storeys overhang (needs {@code jetty > 0})
 * @param setbackChance percent of multi-storey buildings whose top storey is set back
 * @param groundExtra blocks added to the ground storey's height when {@code groundExtraChance} hits
 * @param groundExtraChance percent of buildings with a taller ground storey
 */
public record MassingStyle(int ground, int typical, int jetty, Map<String, Integer> plans, int jettyChance, int setbackChance,
		int groundExtra, int groundExtraChance) {
	public static final MassingStyle DEFAULT = new MassingStyle(4, 4, 0, Map.of("box", 1), 0, 0, 0, 0);

	public static MassingStyle from(Map<String, Object> o, String path) {
		MassingStyle m = new MassingStyle(
				Json.integer(o, "ground", DEFAULT.ground, path),
				Json.integer(o, "typical", DEFAULT.typical, path),
				Json.integer(o, "jetty", DEFAULT.jetty, path),
				Json.weights(o, "plans", DEFAULT.plans, path),
				Json.integer(o, "jettyChance", DEFAULT.jettyChance, path),
				Json.integer(o, "setbackChance", DEFAULT.setbackChance, path),
				Json.integer(o, "groundExtra", DEFAULT.groundExtra, path),
				Json.integer(o, "groundExtraChance", DEFAULT.groundExtraChance, path));
		if (m.jettyChance < 0 || m.jettyChance > 100 || m.setbackChance < 0 || m.setbackChance > 100
				|| m.groundExtraChance < 0 || m.groundExtraChance > 100 || m.groundExtra < 0) {
			throw new IllegalArgumentException(path + ": massing chances are percents, groundExtra >= 0");
		}
		return m;
	}

	/** Weight of a plan name ({@code box, L, T, U, courtyard}); 0 if not listed. */
	public int plan(String name) { return plans.getOrDefault(name, 0); }
}
