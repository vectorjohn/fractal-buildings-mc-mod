package dev.fractalbuildings.core.ir;

import java.util.List;
import java.util.Objects;

/**
 * The top-level document, JSON format {@code fb-scene/1}: what the CLI writes and the viewer
 * reads. Phase 3 scenes hold one lot and one building; the same document carries many lots and
 * buildings (district scenes, Phase 5) without a format change. {@code terrain} may be
 * null. {@code violations} is empty unless an invariant check ran. {@code ground} holds world-frame
 * boxes (roads, paving, fill, cuts, bridges; layer {@link Layer#GROUND}) and is written only when
 * non-empty.
 */
public record SceneDoc(int genVersion, long seed, TerrainPatch terrain, List<Lot> lots, List<PlacedBuilding> buildings,
		List<Box> ground, List<Violation> violations) {
	public static final String FORMAT = "fb-scene/1";

	public SceneDoc {
		lots = List.copyOf(lots);
		buildings = List.copyOf(buildings);
		ground = List.copyOf(ground);
		violations = List.copyOf(violations);
		for (PlacedBuilding b : buildings) Objects.requireNonNull(b);
		for (Box b : ground) Objects.requireNonNull(b);
	}

	/** A scene without ground boxes (everything before district scenes). */
	public SceneDoc(int genVersion, long seed, TerrainPatch terrain, List<Lot> lots, List<PlacedBuilding> buildings, List<Violation> violations) {
		this(genVersion, seed, terrain, lots, buildings, List.of(), violations);
	}
}
