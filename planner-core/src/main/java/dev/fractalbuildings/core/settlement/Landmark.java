package dev.fractalbuildings.core.settlement;

import java.util.Objects;

/** A landmark footprint inside a district. */
public record Landmark(String id, LandmarkKind kind, int x, int z, int w, int d, String district) {
	public Landmark {
		Objects.requireNonNull(id);
		Objects.requireNonNull(kind);
		Objects.requireNonNull(district);
		if (w <= 0 || d <= 0) throw new IllegalArgumentException("landmark " + id + " " + w + "x" + d);
	}
}
