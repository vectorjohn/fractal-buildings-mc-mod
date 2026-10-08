package dev.fractalbuildings.core;

import java.util.List;

/**
 * PROVISIONAL smoke-path IR (irVersion 0). Phase 3 designs the real Building IR; this only has to
 * carry axis-aligned role-tagged boxes from the planner to the viewer. Boxes are applied in order,
 * later boxes override earlier ones where they overlap.
 */
public record SmokeIr(int irVersion, int[] size, List<Box> boxes) {
	public static final int VERSION = 0;

	/** Axis-aligned box: min corner and extent in blocks, with an abstract material role. */
	public record Box(int[] min, int[] size, String role) {}
}
