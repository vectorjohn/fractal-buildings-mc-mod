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
	RAIL(true, true),
	/** Street surface of a town: stone bricks, mud bricks in the desert (DS-30, 7.10). Appended last, like every role added after v1. */
	ROAD_TOWN(true, true),
	/** Arterial and main street surface of a city or metropolis: cobbled deepslate (DS-30, 7.10). */
	ROAD_MAIN(true, true),
	/** Street and lane surface of a city or metropolis: light grey concrete (DS-30, 7.10). */
	ROAD_CITY(true, true);

	public final boolean solid;
	public final boolean closesShell;

	Role(boolean solid, boolean closesShell) {
		this.solid = solid;
		this.closesShell = closesShell;
	}

	/** A street or road surface of any tier (ROAD, ROAD_TOWN, ROAD_MAIN, ROAD_CITY). */
	public boolean isRoad() { return this == ROAD || this == ROAD_TOWN || this == ROAD_MAIN || this == ROAD_CITY; }

	/** Roles that one style picks for a whole tile (DS-30): the road roles and bridge decks. */
	public boolean isCarriageway() { return isRoad() || this == DECK; }

	public String json() { return name().toLowerCase(java.util.Locale.ROOT); }
}
