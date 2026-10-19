package dev.fractalbuildings.core.gen.v1.types.highrise;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import java.util.List;

/**
 * A setback tower on a street podium (step 4.6): 20 to 60 storeys where the height cap allows (the
 * massing caps the count by {@code maxHeight}, so the top never passes y 319), one core through all
 * storeys. Lots 20x20 to 44x44, ground up to 1 in 3.
 */
public final class HighriseType implements BuildingType {
	public static final String ID = "highrise";

	private static final TypeLimits LIMITS = new TypeLimits(18, 18, 20, 60, 1, 1, 1, false,
			List.of(new int[] {20, 20}, new int[] {28, 28}, new int[] {36, 36}, new int[] {44, 44}), 12, 3);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	@Override
	public Massing mass(BuildContext c) { return HighriseMassing.build(c); }
}
