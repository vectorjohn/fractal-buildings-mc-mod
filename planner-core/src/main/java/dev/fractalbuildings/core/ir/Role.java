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
	STAIR(true, true);

	public final boolean solid;
	public final boolean closesShell;

	Role(boolean solid, boolean closesShell) {
		this.solid = solid;
		this.closesShell = closesShell;
	}

	public String json() { return name().toLowerCase(java.util.Locale.ROOT); }
}
