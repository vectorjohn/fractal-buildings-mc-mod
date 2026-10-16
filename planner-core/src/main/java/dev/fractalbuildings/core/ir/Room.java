package dev.fractalbuildings.core.ir;

import java.util.List;
import java.util.Objects;

/**
 * A room: the air it occupies, as one or more boxes (v0 uses one; L-shaped rooms use several),
 * on one storey of one volume. {@code use} is a free tag ("room" in v0; "hall", "stair", "shop" later).
 */
public record Room(String id, int storey, String volume, List<Box3> parts, String use) {
	public Room {
		Objects.requireNonNull(id);
		Objects.requireNonNull(volume);
		Objects.requireNonNull(use);
		parts = List.copyOf(parts);
		if (parts.isEmpty()) throw new IllegalArgumentException("room " + id + " has no parts");
	}
}
