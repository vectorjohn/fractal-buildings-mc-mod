package dev.fractalbuildings.core.gen.v1.types.house;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.mass.HouseMassing;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import java.util.List;

/**
 * A house. Massing (step 4.2): {@link HouseMassing} draws a plan (box, L, T, U, courtyard) with wings
 * of other widths and heights and, on the main block, jetties or a set-back top storey. The house
 * step (4.6) owns the type from here.
 */
public final class HouseType implements BuildingType {
	public static final String ID = "house";

	private static final RoomProgram ROOMS = new HouseRooms();

	private static final TypeLimits LIMITS = new TypeLimits(5, 5, 1, 3, 1, 1, 1, false,
			List.of(new int[] {7, 7}, new int[] {12, 10}, new int[] {20, 20}, new int[] {31, 17}), 500, 1);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	@Override
	public RoomProgram rooms() { return ROOMS; }

	@Override
	public Massing mass(BuildContext c) { return HouseMassing.build(c, LIMITS.maxStoreys()); }
}
