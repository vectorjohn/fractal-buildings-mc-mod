package dev.fractalbuildings.core.ir;

/** Horizontal axis. JSON: "x" or "z". */
public enum Axis {
	X, Z;

	public String json() { return name().toLowerCase(java.util.Locale.ROOT); }
}
