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
	ENTRANCE_REACHABLE("entrance-reachable");

	public final String id;

	Invariant(String id) { this.id = id; }
}
