package dev.fractalbuildings.core.ir;

/** Viewer toggle group of a box (3.6). JSON: lower case name. */
public enum Layer {
	FOUNDATION, SHELL, INTERIOR, ROOF;

	public String json() { return name().toLowerCase(java.util.Locale.ROOT); }
}
