package dev.fractalbuildings.core.settlement;

import java.util.List;

/**
 * Per-tier policy hooks of the cell skeleton. {@link DefaultProgram} serves every tier; steps D1 to D3 add one
 * class per tier that overrides what differs. Hooks only read the {@link SkeletonContext} and are pure.
 */
public interface TierProgram {
	/** A main road found from the hub to a target: lattice points (empty when no route exists) and bridge ranges. */
	record Route(Pt target, List<Pt> points, List<int[]> bridges) {}

	/** The hub column (a lattice column the cell owns), or null when the cell has no urban column. */
	Pt hub(SkeletonContext c);

	/** Exit targets: lattice columns on the urban edge, away from the hub, that main roads run to. */
	List<Pt> exits(SkeletonContext c, Pt hub);

	/** A route from the hub to each target (gates first, then exits), in order. */
	List<Route> mainRoads(SkeletonContext c, Pt hub, List<Pt> targets);

	RoadKind mainKind(Tier tier);

	int mainWidth(Tier tier);

	/** Landmarks around the hub (provisional ids), only called for the cell that holds the site centre. */
	List<Landmark> landmarks(SkeletonContext c, Pt hub);

	/** Wall lines; none until the town program (D2). */
	default List<Wall> walls(SkeletonContext c, Pt hub) { return List.of(); }

	/** Used by district detail (DL). */
	StreetLayout streetLayout(Tier tier, QuarterKind quarter, int density);
}
