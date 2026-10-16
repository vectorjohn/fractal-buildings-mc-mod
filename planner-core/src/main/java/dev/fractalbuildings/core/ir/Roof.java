package dev.fractalbuildings.core.ir;

import java.util.Objects;

/**
 * Roof description over one volume (the voxels are in the boxes). {@code form} is a string so
 * styles can add forms ("gable" in v0; "hip", "flat", "mansard" later). The pitch is
 * {@code rise} blocks up per {@code run} blocks in (integers only). {@code baseY} is the local y
 * of the lowest roof layer. {@code ridgeAxis} is null for forms without a ridge.
 */
public record Roof(String form, String volume, Axis ridgeAxis, int baseY, int rise, int run) {
	public static final String GABLE = "gable";

	public Roof {
		Objects.requireNonNull(form);
		Objects.requireNonNull(volume);
		if (rise < 0 || run <= 0) throw new IllegalArgumentException("pitch " + rise + "/" + run);
	}
}
