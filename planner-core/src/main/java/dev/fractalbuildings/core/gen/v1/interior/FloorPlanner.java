package dev.fractalbuildings.core.gen.v1.interior;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Mass;

/**
 * A type's own floor planner (the high-rise's plate templates): offered each floor of each mass before the
 * generic guillotine planner is used. Returning {@code null} means "use the generic planner for this floor".
 * Must be a pure function of the context, the mass and the storey, and give the same plan for storeys the
 * interior pass would put in one band.
 */
public interface FloorPlanner {
	/** A plan with the use of each room (indexed like {@code plan.leaves()}); {@code needsBed}: a flat with no bedroom, which gets a bed hook. */
	record Planned(FloorPlan plan, String[] uses, boolean[] needsBed) {
		public Planned(FloorPlan plan, String[] uses) { this(plan, uses, new boolean[uses.length]); }
	}

	/**
	 * @param massIndex index of {@code a} in the massing
	 * @param reserved interior cells (relative to the mass's interior) where a partition must not end: window cells, entrances, core ring
	 * @param core interior cells of the stair core
	 */
	Planned plan(BuildContext c, int massIndex, Mass a, int storey, boolean[][] reserved, boolean[][] core);

	/** True if storey {@code storey} of mass {@code massIndex} must start a new band although its constraints equal those below (a change of floor kind). */
	default boolean newBand(BuildContext c, int massIndex, Mass a, int storey) { return false; }
}
