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
	WINDOW_SIZE("window-size"),
	/** Generator v1 only (DS-45): a shop or workshop door sits at most 2 above the street in front of it, a house door at most 5, on every lot within the planner's limit (Briefs.SHOP_FRONT_RISE, HOUSE_FRONT_RISE; steeper lots wait for the bank buildings and are a design warning). {@code flight-landings} (DS-45 "Stair runs": at most 3 risers in a straight run) is a design warning for now, see {@code DoorRise.warnings}. */
	DOOR_RISE("door-rise");

	public final String id;

	Invariant(String id) { this.id = id; }
}
