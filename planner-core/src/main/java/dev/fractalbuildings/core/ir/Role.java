package dev.fractalbuildings.core.ir;

/**
 * Abstract material role of a box. Palettes map roles to blocks (mod) or colours (viewer). JSON:
 * lower case name. Adding a role is a schema change (bump {@code fb-scene/N} only if old readers
 * would misread it; the viewer shows unknown roles in magenta).
 *
 * <p>Movement flags, used by the invariants: {@code solid} blocks movement and can be stood on;
 * {@code closesShell} counts as closed when checking that rooms are sealed (doors and windows
 * are closed then).
 */
public enum Role {
	/** Carves: clears earlier boxes. */
	AIR(false, false),
	FOUNDATION(true, true),
	FLOOR(true, true),
	WALL(true, true),
	TRIM(true, true),
	WINDOW(true, true),
	/** Passable doorway (2 high), closed for the shell check. */
	DOOR(false, true),
	ROOF(true, true),
	/** A step you can stand on; full-block steps of 1 in v0. */
	STAIR(true, true),
	/** Structural frame on show: posts, beams, columns, half-timbering, jetty bressumers (Phase 4). */
	BEAM(true, true),
	/** Thin barrier: railings, balustrades, grilles. Blocks movement and closes the shell. */
	FENCE(true, true),
	/** Street and road surface (Phase 5 district scenes). */
	ROAD(true, true),
	/** Plaza and square paving (Phase 5). */
	PAVING(true, true),
	/** Fill earth in district scenes (Phase 5). */
	GROUND(true, true),
	/** Bridge decks and piers (TR 5.3): timber, or stone in the desert. */
	DECK(true, true),
	/** Railing on the open side of exterior steps and landings (6.9): a fence or wall post on a kerb beside the flight, never across the line of travel. Appended last; older readers reject the unknown value. */
	RAIL(true, true);

	public final boolean solid;
	public final boolean closesShell;

	Role(boolean solid, boolean closesShell) {
		this.solid = solid;
		this.closesShell = closesShell;
	}

	public String json() { return name().toLowerCase(java.util.Locale.ROOT); }
}
