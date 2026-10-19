package dev.fractalbuildings.core.gen.v1.style;

import java.util.List;
import java.util.Map;

/**
 * Style JSON key {@code facade} (owned by the facades step 4.4). Every key has a default that means
 * "off", so a style that sets none of the decor keys (plain) gets bare bays and windows.
 *
 * <p>Rhythm: {@code bayMin..bayMax} is the width of a window bay in wall positions; {@code symmetric}
 * makes every face's bays mirror about its centre (otherwise widths are perturbed per band);
 * {@code windowWidth} 2 allows 2-wide windows in even bays of 4 or more; {@code bandSize} is how many
 * typical storeys form one band (a band is the unit that gets its own bay variant, so tall facades
 * change rhythm every band instead of repeating); {@code narrowCorners} takes a block from a bay at
 * an outside corner, {@code blankCorners} turns that bay into plain wall (faces with 4+ bays).
 *
 * <p>Entrances: {@code doorWidth} 2 makes a double door in an even bay of 4 or more where the two
 * cells behind it are free; {@code doorSurround} puts a trim lintel and jambs round every door.
 *
 * <p>Decor (all default off): {@code plinth} (foundation-role bottom row of storey 0), {@code sills},
 * {@code lintels}, {@code shutters} (round cut windows), {@code stringCourse} (trim at the slab lines),
 * {@code cornice} (trim under the eaves), {@code bressumer} (beam along a jetty's slab edge),
 * {@code corners} ({@code none}, {@code post} = beam column, {@code quoins} = alternating trim),
 * {@code timber} ({@code none}, {@code upper} = half-timbering above the ground storey, {@code all}).
 *
 * @param sill window bottom, in blocks above the storey's standing level (y0 of the face)
 * @param windowHeight window height in blocks
 */
public record FacadeStyle(int bayMin, int bayMax, int sill, int windowHeight, int windowWidth, boolean symmetric, int bandSize,
		boolean narrowCorners, boolean blankCorners, int doorWidth, boolean doorSurround, boolean plinth, boolean sills,
		boolean lintels, boolean shutters, boolean stringCourse, boolean cornice, boolean bressumer, String corners, String timber) {
	public static final List<String> CORNERS = List.of("none", "post", "quoins");
	public static final List<String> TIMBER = List.of("none", "upper", "all");

	public static final FacadeStyle DEFAULT = new FacadeStyle(2, 3, 1, 1, 1, false, 3, false, false, 1, false, false, false,
			false, false, false, false, false, "none", "none");

	public static FacadeStyle from(Map<String, Object> o, String path) {
		FacadeStyle d = DEFAULT;
		FacadeStyle f = new FacadeStyle(
				Json.integer(o, "bayMin", d.bayMin, path),
				Json.integer(o, "bayMax", d.bayMax, path),
				Json.integer(o, "sill", d.sill, path),
				Json.integer(o, "windowHeight", d.windowHeight, path),
				Json.integer(o, "windowWidth", d.windowWidth, path),
				Json.bool(o, "symmetric", d.symmetric, path),
				Json.integer(o, "bandSize", d.bandSize, path),
				Json.bool(o, "narrowCorners", d.narrowCorners, path),
				Json.bool(o, "blankCorners", d.blankCorners, path),
				Json.integer(o, "doorWidth", d.doorWidth, path),
				Json.bool(o, "doorSurround", d.doorSurround, path),
				Json.bool(o, "plinth", d.plinth, path),
				Json.bool(o, "sills", d.sills, path),
				Json.bool(o, "lintels", d.lintels, path),
				Json.bool(o, "shutters", d.shutters, path),
				Json.bool(o, "stringCourse", d.stringCourse, path),
				Json.bool(o, "cornice", d.cornice, path),
				Json.bool(o, "bressumer", d.bressumer, path),
				Json.string(o, "corners", d.corners, path),
				Json.string(o, "timber", d.timber, path));
		if (f.bayMin < 1 || f.bayMax < f.bayMin || f.windowHeight < 1 || f.sill < 0) throw new IllegalArgumentException(path + ": bad facade " + f);
		if (f.windowWidth < 1 || f.windowWidth > 2 || f.doorWidth < 1 || f.doorWidth > 2 || f.bandSize < 1) throw new IllegalArgumentException(path + ": bad facade " + f);
		if (!CORNERS.contains(f.corners)) throw new IllegalArgumentException(path + ".corners: one of " + CORNERS);
		if (!TIMBER.contains(f.timber)) throw new IllegalArgumentException(path + ".timber: one of " + TIMBER);
		return f;
	}
}
