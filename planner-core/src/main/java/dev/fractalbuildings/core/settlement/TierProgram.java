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

	/** Width of this cell's main roads: the tier's, unless the program narrows it (a walled city's avenues pass 5-wide gates). */
	default int mainWidth(SkeletonContext c) { return mainWidth(c.site.tier()); }

	/** Landmarks around the hub (provisional ids), only called for the cell that holds the site centre. */
	List<Landmark> landmarks(SkeletonContext c, Pt hub);

	/** Landmarks that every cell lists for the columns it owns, not tied to the hub (D3: squares at arterial crossings); none by default. */
	default List<Landmark> cellLandmarks(SkeletonContext c) { return List.of(); }

	/** Wall lines; none by default. */
	default List<Wall> walls(SkeletonContext c, Pt hub) { return List.of(); }

	/** Walls and the gates in them. */
	record WallPlan(List<Wall> walls, List<Gate> gates) {
		public WallPlan {
			walls = List.copyOf(walls);
			gates = List.copyOf(gates);
		}
	}

	/** Walls and their gates, given the roads the cell's skeleton made (the town ring needs the gates its roads cross). */
	default WallPlan walls(SkeletonContext c, Pt hub, List<Road> roads) { return new WallPlan(walls(c, hub), List.of()); }

	/** Does district detail branch lanes off the main roads and carry ports across tile borders (false: the main road is the only street)? */
	default boolean lanes() { return true; }

	/** Does district detail carry hashed lane ports across tile borders that have no street? */
	default boolean ports() { return true; }

	/** Used by district detail (DL). */
	StreetLayout streetLayout(Tier tier, QuarterKind quarter, int density);
}
