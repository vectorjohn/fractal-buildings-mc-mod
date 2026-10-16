package dev.fractalbuildings.core.ir;

import java.util.Objects;

/**
 * A massing volume (local frame, roof excluded). {@code parent} is the id of the volume it stands
 * on, or null: setback towers are chains of volumes, each inside its parent's footprint (ADR 0001).
 */
public record Volume(String id, Box3 box, String parent) {
	public Volume {
		Objects.requireNonNull(id);
		Objects.requireNonNull(box);
	}
}
