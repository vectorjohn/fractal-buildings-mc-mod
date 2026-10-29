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
	ROAD_CITY(true, true),
	/** Pitched-roof stair, high side north (DS-36); the mod makes it the roof material's stair. Appended last; the facing is local to the building and turns with its rotation. */
	ROOF_STAIR_N(true, true),
	/** As {@link #ROOF_STAIR_N}, high side east. */
	ROOF_STAIR_E(true, true),
	/** As {@link #ROOF_STAIR_N}, high side south. */
	ROOF_STAIR_S(true, true),
	/** As {@link #ROOF_STAIR_N}, high side west. */
	ROOF_STAIR_W(true, true),
	/** Pitched-roof slab, bottom half (DS-36): the roof material's slab. */
	ROOF_SLAB(true, true),
	/** Pitched-roof slab, top half (DS-36). */
	ROOF_SLAB_TOP(true, true);

	public final boolean solid;
	public final boolean closesShell;

	Role(boolean solid, boolean closesShell) {
		this.solid = solid;
		this.closesShell = closesShell;
	}

	/** A roof cell of any kind: the full block, a roof stair or a roof slab. All take the style's {@code roof} palette. */
	public boolean isRoof() { return this == ROOF || isRoofStair() || this == ROOF_SLAB || this == ROOF_SLAB_TOP; }

	public boolean isRoofStair() { return this == ROOF_STAIR_N || this == ROOF_STAIR_E || this == ROOF_STAIR_S || this == ROOF_STAIR_W; }

	/** The roof stair whose high side faces {@code f}. */
	public static Role roofStair(Facing f) {
		return switch (f) { case NORTH -> ROOF_STAIR_N; case EAST -> ROOF_STAIR_E; case SOUTH -> ROOF_STAIR_S; case WEST -> ROOF_STAIR_W; };
	}

	/** High-side direction of a roof stair, else null. */
	public Facing roofStairFacing() {
		return switch (this) { case ROOF_STAIR_N -> Facing.NORTH; case ROOF_STAIR_E -> Facing.EAST; case ROOF_STAIR_S -> Facing.SOUTH; case ROOF_STAIR_W -> Facing.WEST; default -> null; };
	}

	/** This role after the building is turned {@code quarterTurns} quarter turns clockwise (only roof stairs change). */
	public Role turned(int quarterTurns) {
		Facing f = roofStairFacing();
		return f == null ? this : roofStair(f.rotate(quarterTurns));
	}

	/** A street or road surface of any tier (ROAD, ROAD_TOWN, ROAD_MAIN, ROAD_CITY). */
	public boolean isRoad() { return this == ROAD || this == ROAD_TOWN || this == ROAD_MAIN || this == ROAD_CITY; }

	/** Roles that one style picks for a whole tile (DS-30): the road roles and bridge decks. */
	public boolean isCarriageway() { return isRoad() || this == DECK; }

	public String json() { return name().toLowerCase(java.util.Locale.ROOT); }
}
