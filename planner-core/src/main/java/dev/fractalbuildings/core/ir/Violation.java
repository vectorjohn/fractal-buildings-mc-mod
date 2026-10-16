package dev.fractalbuildings.core.ir;

import java.util.List;
import java.util.Objects;

/**
 * An invariant failure, for the report and the viewer overlay (3.7). {@code invariant} is a
 * stable kebab-case id (e.g. "rooms-reachable"); {@code building} is a building id or null for
 * scene-level failures; {@code cells} are the offending voxels or regions in world coordinates.
 */
public record Violation(String invariant, String building, String message, List<Box3> cells) {
	public Violation {
		Objects.requireNonNull(invariant);
		Objects.requireNonNull(message);
		cells = List.copyOf(cells);
	}
}
