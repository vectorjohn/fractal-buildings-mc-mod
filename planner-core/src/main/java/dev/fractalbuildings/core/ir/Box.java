package dev.fractalbuildings.core.ir;

import java.util.Objects;

/**
 * A role-tagged box of the building, in its local frame. Boxes apply in list order; a later box
 * overrides earlier ones where they overlap ({@link Role#AIR} clears). {@code storey} is the
 * storey index the box belongs to, or {@link #NO_STOREY} for boxes spanning storeys (roof, gable,
 * site boxes); per-storey tagging lets later phases generate or show one floor at a time.
 */
public record Box(Box3 box, Role role, Layer layer, int storey) {
	public static final int NO_STOREY = -1;

	public Box {
		Objects.requireNonNull(box);
		Objects.requireNonNull(role);
		Objects.requireNonNull(layer);
		if (storey < NO_STOREY) throw new IllegalArgumentException("storey " + storey);
	}
}
