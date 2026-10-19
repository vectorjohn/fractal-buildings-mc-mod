package dev.fractalbuildings.core.gen.v1.types.temple;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import java.util.List;

/**
 * A temple, chapel or shrine (step 4.6): one tall open nave on a clear axis, a porch or a bell
 * tower at the front with the door on the axis, a narrower apse at the back holding the altar, and
 * sometimes a pair of side chapels. Smaller and more axial than the hall type (market hall, town
 * hall, cathedral); lots 15x25 to 31x45. See {@link TempleMassing}.
 */
public final class TempleType implements BuildingType {
	public static final String ID = "temple";

	private static final TypeLimits LIMITS = new TypeLimits(13, 22, 2, 3, 1, 2, 1, false,
			List.of(new int[] {15, 25}, new int[] {19, 31}, new int[] {25, 38}, new int[] {31, 45}), 120, 2);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	@Override
	public Massing mass(BuildContext c) { return TempleMassing.build(c); }

	@Override
	public Program facade() { return TempleFacade::run; }

	@Override
	public RoomProgram rooms() { return TempleRooms.PROGRAM; }
}
