package dev.fractalbuildings.mod.roads;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.road.RoadNet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RoadFeature must draw when the chunk has road columns or a spur (an always-false check once switched every road off). */
class RoadFeatureWorkTest {
	private static final RoadNet.Col COL = new RoadNet.Col(3, 4, 64, false, 3, "dirt", "none");

	@Test
	void columnsMeanWork() {
		assertTrue(new VillageRoads.Adjusted(List.of(List.of(), List.of(COL)), List.of(), 0).hasWork());
	}

	@Test
	void spurAloneMeansWork() {
		assertTrue(new VillageRoads.Adjusted(List.of(List.of()), List.of(new int[] {1, 2}), 0).hasWork());
	}

	@Test
	void nothingMeansNoWork() {
		assertFalse(new VillageRoads.Adjusted(List.of(List.of(), List.of()), List.of(), 0).hasWork());
	}
}
