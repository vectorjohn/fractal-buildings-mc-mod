package dev.fractalbuildings.core.gen.v1.types.hall;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.interior.InteriorPass;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import java.util.List;

/**
 * Hall / civic (step 4.6): large open spans. Sub-kinds by draw: market hall, town hall, cathedral,
 * bath ({@link HallMassing}). Lots from 30 x 30 up to the 200 x 200 case; ground up to 1 in 3.
 */
public final class HallType implements BuildingType {
	public static final String ID = "hall";

	private static final TypeLimits LIMITS = new TypeLimits(20, 20, 1, 3, 2, 2, 2, false,
			List.of(new int[] {30, 30}, new int[] {56, 44}, new int[] {90, 64}, new int[] {120, 80}, new int[] {200, 200}), 40, 3);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	@Override
	public Massing mass(BuildContext c) { return HallMassing.build(c); }

	@Override
	public RoomProgram rooms() { return HallRooms.PROGRAM; }

	/** Tall windows on open masses; wide arcade doors on a market hall. */
	@Override
	public Program facade() { return HallFacade::facade; }

	@Override
	public Program openings() { return HallFacade::openings; }

	/** Wide spans get low pitches. */
	@Override
	public Program roofs() { return HallRoofs::run; }

	/** The shared interior pass, then arcades between neighbouring open spaces. */
	@Override
	public Program interior() {
		return c -> {
			InteriorPass.run(c);
			HallArches.run(c);
		};
	}
}
