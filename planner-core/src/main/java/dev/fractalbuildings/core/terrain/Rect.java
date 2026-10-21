package dev.fractalbuildings.core.terrain;

/** Axis-aligned rectangle of world columns: x in [x, x + w), z in [z, z + d). */
public record Rect(int x, int z, int w, int d) {
	public Rect {
		if (w <= 0 || d <= 0) throw new IllegalArgumentException("rect " + w + "x" + d);
	}
}
