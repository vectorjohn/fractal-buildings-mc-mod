package dev.fractalbuildings.core.check;

/** Invariant ids, in the fixed order violations are reported (plan "Invariants (3.9)"). */
public enum Invariant {
	IR_VALID("ir-valid"),
	IN_LOT("in-lot"),
	IN_WORLD("in-world"),
	NO_FLOATING("no-floating"),
	NO_GAP_BELOW("no-gap-below"),
	SHELL_CLOSED("shell-closed"),
	ROOF_COVERS("roof-covers"),
	ROOF_CLOSED("roof-closed"),
	ROOM_GRAPH("room-graph"),
	ROOMS_REACHABLE("rooms-reachable"),
	/** Generator v1 only: every exterior ground-floor door has a walkable way down to the terrain inside the lot. */
	ENTRANCE_REACHABLE("entrance-reachable"),
	/** Generator v1 only: from every exterior ground-floor door a walker gets down onto the real ground (terrain, road or paving) with no drop over a block and no rise over half a block (STAIRGROUND). */
	STEPS_REACH_GROUND("steps-reach-ground"),
	/** Generator v1 only: walking an interior flight from one floor to the next never needs a rise over half a block (STAIRS). */
	STAIR_RISE("stair-rise"),
	/** Generator v1 only (DS-33): at most 25 % of a building's windows are 1x1 (the accent size), for buildings with 4 or more windows. */
	WINDOW_SIZE("window-size");

	public final String id;

	Invariant(String id) { this.id = id; }
}
