package dev.fractalbuildings.core.gen.v1.types.highrise;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.facade.OpeningPass;
import dev.fractalbuildings.core.gen.v1.interior.FloorPlanner;
import dev.fractalbuildings.core.gen.v1.interior.InteriorPass;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.roof.RoofPass;
import java.util.List;

/**
 * A setback tower on a street podium (step 4.6): 20 to 60 storeys where the height cap allows (the
 * massing caps the count by {@code maxHeight}, so the top never passes y 319), one core through all
 * storeys. Lots 20x20 to 44x44, ground up to 1 in 3.
 */
public final class HighriseType implements BuildingType {
	public static final String ID = "highrise";

	private static final TypeLimits LIMITS = new TypeLimits(18, 18, 10, 60, 1, 1, 1, false,
			List.of(new int[] {20, 20}, new int[] {28, 28}, new int[] {36, 36}, new int[] {44, 44}), 12, 3);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }
	@Override
	public Massing mass(BuildContext c) { return HighriseMassing.build(c); }

	private static final TowerFloors FLOORS = new TowerFloors();
	private static final HighriseRooms ROOMS = new HighriseRooms();

	@Override
	public FloorPlanner floors() { return FLOORS; }

	@Override
	public RoomProgram rooms() { return ROOMS; }

	/** The generic facade pass with the programme's bands and taller special-floor windows, then piers ({@link HighriseFacade}, {@link TowerPiers}). */
	@Override
	public Program facade() { return HighriseFacade::run; }

	/** The style's roof rules over the tiers, then the roof guard round the roof the stair house opens onto ({@link TowerRoof}). */
	@Override
	public Program roofs() {
		return c -> {
			RoofPass.run(c, c.style().roof().withForms(java.util.Map.of("flat", 1)));
			TowerRoof.guard(c);
		};
	}

	/** The generic openings, then the stair house's two roof doors ({@link TowerRoof}). */
	@Override
	public Program openings() {
		return c -> {
			OpeningPass.run(c);
			TowerRoof.doors(c);
		};
	}

	/** The generic interior pass over the plate plans of {@link TowerFloors}, then the lift shaft and the stairwell lights. */
	@Override
	public Program interior() {
		return c -> {
			InteriorPass.run(c);
			TowerShaft.emit(c);
		};
	}
}
