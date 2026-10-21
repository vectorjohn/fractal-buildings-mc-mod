package dev.fractalbuildings.core.settlement;

import java.util.Objects;

/** A levelled open area (square, market, green...) at surface height {@code y}. */
public record Plaza(String id, String kind, int x, int z, int w, int d, int y) {
	public Plaza {
		Objects.requireNonNull(id);
		Objects.requireNonNull(kind);
		if (w <= 0 || d <= 0) throw new IllegalArgumentException("plaza " + id + " " + w + "x" + d);
	}
}
