package dev.fractalbuildings.core.settlement;

/** Id conventions: districts {@code d<ix>_<iz>}, negative indices written with an {@code m} ({@code d3_m2}). */
public final class Ids {
	private Ids() {}

	public static String district(int ix, int iz) { return "d" + n(ix) + "_" + n(iz); }

	/** Road of a cell skeleton: {@code r<cx>_<cz>_<n>}. */
	public static String road(int cx, int cz, int n) { return "r" + n(cx) + "_" + n(cz) + "_" + n(n); }

	/** Approach of a cell skeleton from a required exit to a road port (ADR 0004 N5): {@code k<cx>_<cz>_<n>}. */
	public static String approach(int cx, int cz, int n) { return "k" + n(cx) + "_" + n(cz) + "_" + n(n); }

	/** Arterial segment: {@code a<axis><line>_<from>} (axis 0 = a line of constant x, 1 = constant z). */
	public static String arterial(int axis, int line, int from) { return "a" + axis + n(line) + "_" + n(from); }

	/** Settlement site id {@code s<cx>_<cz>} of its claimant cell; the same for {@code --cell} and {@code --site}. */
	public static String site(int cx, int cz) { return "s" + n(cx) + "_" + n(cz); }

	static String n(int v) { return v < 0 ? "m" + -(long) v : Integer.toString(v); }
}
