package dev.fractalbuildings.core.gen.v1.style;

import java.util.Map;

/**
 * Style JSON key {@code roof}. Owned by the roofs step (4.3); add fields with defaults that keep old
 * styles' output unchanged.
 *
 * @param forms roof form weights ({@code {"gable": 3, "hip": 1}}), for weighted {@code Choice}; known
 *     forms: gable, hip, pyramid (a hip on a square, else a hip), shed, mansard, flat
 * @param rise pitch: rise blocks up per {@code run} blocks in
 * @param run pitch run (> 0)
 * @param overhang eave overhang in blocks (0 = none), clipped to the envelope and to free columns
 * @param parapet height of the parapet ring round a flat roof (0 = none)
 * @param parapetRole {@code "wall"} or {@code "fence"}
 * @param tallStoreys a roof group whose top is this many storeys up or more gets a flat roof
 * @param dormers percent chance per eligible spot for a dormer on a street-facing slope (0 = none)
 */
public record RoofStyle(Map<String, Integer> forms, int rise, int run, int overhang, int parapet, String parapetRole,
		int tallStoreys, int dormers) {
	public static final RoofStyle DEFAULT = new RoofStyle(Map.of("gable", 1), 1, 1, 0, 1, "wall", 6, 0);

	public RoofStyle(Map<String, Integer> forms, int rise, int run, int overhang) {
		this(forms, rise, run, overhang, DEFAULT.parapet, DEFAULT.parapetRole, DEFAULT.tallStoreys, DEFAULT.dormers);
	}

	public static RoofStyle from(Map<String, Object> o, String path) {
		RoofStyle r = new RoofStyle(
				Json.weights(o, "forms", DEFAULT.forms, path),
				Json.integer(o, "rise", DEFAULT.rise, path),
				Json.integer(o, "run", DEFAULT.run, path),
				Json.integer(o, "overhang", DEFAULT.overhang, path),
				Json.integer(o, "parapet", DEFAULT.parapet, path),
				Json.string(o, "parapetRole", DEFAULT.parapetRole, path),
				Json.integer(o, "tallStoreys", DEFAULT.tallStoreys, path),
				Json.integer(o, "dormers", DEFAULT.dormers, path));
		if (r.run <= 0 || r.rise < 0) throw new IllegalArgumentException(path + ": pitch " + r.rise + "/" + r.run);
		if (r.overhang < 0 || r.overhang > 2) throw new IllegalArgumentException(path + ": overhang " + r.overhang);
		if (r.parapet < 0 || r.parapet > 3) throw new IllegalArgumentException(path + ": parapet " + r.parapet);
		if (!r.parapetRole.equals("wall") && !r.parapetRole.equals("fence")) throw new IllegalArgumentException(path + ": parapetRole " + r.parapetRole);
		if (r.tallStoreys < 1) throw new IllegalArgumentException(path + ": tallStoreys " + r.tallStoreys);
		if (r.dormers < 0 || r.dormers > 100) throw new IllegalArgumentException(path + ": dormers " + r.dormers);
		return r;
	}

	public RoofStyle withForms(Map<String, Integer> f) { return new RoofStyle(f, rise, run, overhang, parapet, parapetRole, tallStoreys, dormers); }

	public RoofStyle withPitch(int rise, int run) { return new RoofStyle(forms, rise, run, overhang, parapet, parapetRole, tallStoreys, dormers); }

	public RoofStyle withOverhang(int o) { return new RoofStyle(forms, rise, run, o, parapet, parapetRole, tallStoreys, dormers); }

	public RoofStyle withDormers(int percent) { return new RoofStyle(forms, rise, run, overhang, parapet, parapetRole, tallStoreys, percent); }
}
