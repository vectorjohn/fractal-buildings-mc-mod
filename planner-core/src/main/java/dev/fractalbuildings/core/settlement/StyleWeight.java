package dev.fractalbuildings.core.settlement;

import java.util.Objects;

/** A style's share of a district (integer weight, > 0). */
public record StyleWeight(String style, int weight) {
	public StyleWeight {
		Objects.requireNonNull(style);
		if (weight <= 0) throw new IllegalArgumentException("style weight " + weight);
	}
}
