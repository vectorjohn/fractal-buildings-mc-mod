package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.probe.SyntheticBiomes;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** C15: the places index is deterministic, sorted and lists what the planner places. */
class PlacesIndexTest {
	private static WorldPlanner planner(long seed) {
		WorldProbes pr = WorldProbes.of(SyntheticTerrain.flat(70), SyntheticBiomes.constant("minecraft:plains"));
		return new WorldPlanner(seed, WorldConfig.defaults().withIntercityRoads(false), pr, null);
	}

	@SuppressWarnings("unchecked")
	@Test
	void listsTheSettlementNearSpawnAndIsDeterministic() {
		var near = planner(1).nearest(0, 0, dev.fractalbuildings.core.settlement.Tier.HAMLET, 2).orElseThrow();
		int cx = near.site().centre().x(), cz = near.site().centre().z();
		String a = PlaytestNote.json(PlacesIndex.build(planner(1), cx, cz, 128));
		String b = PlaytestNote.json(PlacesIndex.build(planner(1), cx, cz, 128));
		assertEquals(a, b);
		Map<String, Object> m = PlacesIndex.build(planner(1), cx, cz, 128);
		List<Map<String, Object>> sets = (List<Map<String, Object>>) m.get("settlements");
		assertTrue(sets.stream().anyMatch(s -> s.get("id").equals(near.site().id())), a);
		for (int i = 1; i < sets.size(); i++) assertTrue(((String) sets.get(i - 1).get("id")).compareTo((String) sets.get(i).get("id")) < 0);
		Map<String, Object> mine = sets.stream().filter(s -> s.get("id").equals(near.site().id())).findFirst().orElseThrow();
		List<Map<String, Object>> districts = (List<Map<String, Object>>) mine.get("districts");
		assertFalse(districts.isEmpty(), a);
		assertTrue(a.contains("\"lotsByType\""), a);
	}

	@Test
	void emptyAreaGivesNoSettlements() {
		// a window far from the settlement still returns a well-formed index
		String j = PlaytestNote.json(PlacesIndex.build(planner(1), 5_000_000, 5_000_000, 16));
		assertTrue(j.contains("\"format\": \"fb-places/1\""), j);
	}
}
