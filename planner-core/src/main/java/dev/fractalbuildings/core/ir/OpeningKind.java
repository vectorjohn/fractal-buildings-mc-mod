package dev.fractalbuildings.core.ir;

/** JSON: lower case name. */
public enum OpeningKind {
	DOOR, WINDOW, STAIR,
	/** A doorless opening between two rooms (an archway or open plan). */
	ARCH;

	public String json() { return name().toLowerCase(java.util.Locale.ROOT); }
}
