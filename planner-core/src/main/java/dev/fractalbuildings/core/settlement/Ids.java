package dev.fractalbuildings.core.settlement;

/** Id conventions: districts {@code d<ix>_<iz>}, negative indices written with an {@code m} ({@code d3_m2}). */
public final class Ids {
	private Ids() {}

	public static String district(int ix, int iz) { return "d" + n(ix) + "_" + n(iz); }

	private static String n(int v) { return v < 0 ? "m" + -(long) v : Integer.toString(v); }
}
