package dev.fractalbuildings.core.ir;

/** JSON: lower case name. */
public enum OpeningKind {
	DOOR, WINDOW, STAIR;

	public String json() { return name().toLowerCase(java.util.Locale.ROOT); }
}
