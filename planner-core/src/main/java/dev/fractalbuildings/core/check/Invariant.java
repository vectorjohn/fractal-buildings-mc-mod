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
	/** Generator v1 only (DS-36): every exposed top cell of a pitched roof is a stair or a slab, never a full block. */
	ROOF_SMOOTH("roof-smooth"),
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
	/** Generator v1 only (DS-48, ROOMWALK): in every furnished room one clear lane joins the doors, stairs and each piece's use cell. */
	FURNISHED_ROOMS_WALKABLE("furnished-rooms-walkable"),
	/** Generator v1 only (STAIRFOOT): the lowest step of every run of steps (flight, door sill step, roof door step) has a floor cell to stand on at its foot, not a hole, a wall or a piece of furniture. */
	STAIR_FOOT("stair-foot"),
	/** Generator v1 only (DS-45): a shop or workshop door sits at most 2 above the street in front of it, a house door at most 5, on every lot within the planner's limit (Briefs.SHOP_FRONT_RISE, HOUSE_FRONT_RISE; steeper lots wait for the bank buildings and are a design warning). {@code flight-landings} (DS-45 "Stair runs": at most 3 risers in a straight run) is a design warning for now, see {@code DoorRise.warnings}. */
	DOOR_RISE("door-rise"),
	/** Generator v1 only (DS-35, DS35DOORS): a building of 2+ storeys or 80+ ground cells has two exterior doors, none into a bedroom; a high-rise doors on two faces and a main door 2+ wide; a workshop wing and lean-to a door each. */
	ENTRANCE_COUNT("entrance-count");

	public final String id;

	Invariant(String id) { this.id = id; }
}
