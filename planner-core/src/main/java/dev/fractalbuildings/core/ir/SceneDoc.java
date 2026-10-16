package dev.fractalbuildings.core.ir;

import java.util.List;
import java.util.Objects;

/**
 * The top-level document, JSON format {@code fb-scene/1}: what the CLI writes and the viewer
 * reads. Phase 3 scenes hold one lot and one building; the same document carries many lots and
 * buildings (and, in Phase 5, districts and roads) without a format change. {@code terrain} may be
 * null. {@code violations} is empty unless an invariant check ran.
 */
public record SceneDoc(int genVersion, long seed, TerrainPatch terrain, List<Lot> lots, List<PlacedBuilding> buildings, List<Violation> violations) {
	public static final String FORMAT = "fb-scene/1";

	public SceneDoc {
		lots = List.copyOf(lots);
		buildings = List.copyOf(buildings);
		violations = List.copyOf(violations);
		for (PlacedBuilding b : buildings) Objects.requireNonNull(b);
	}
}
