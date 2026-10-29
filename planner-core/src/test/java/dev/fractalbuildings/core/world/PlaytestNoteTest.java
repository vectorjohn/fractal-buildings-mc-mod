package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Room;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** FBNOTE: the sidecar format, the debug switch, and the planner lookup behind it (on the sample town). */
class PlaytestNoteTest {
	@Test
	void sidecarJsonEscapesAndKeepsKeyOrder() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("format", PlaytestNote.FORMAT);
		m.put("comment", "stairs \"don't\" go\nto top \\ \t");
		m.put("seed", 1709797029209580947L);
		m.put("small", 987654321L);
		m.put("yaw", 12.3456f);
		m.put("x", 2721.5);
		m.put("none", null);
		m.put("list", List.of(1, 2));
		m.put("nested", Map.of("a", true));
		String j = PlaytestNote.json(m);
		assertTrue(j.indexOf("\"format\"") < j.indexOf("\"comment\"") && j.indexOf("\"comment\"") < j.indexOf("\"seed\""), j);
		assertTrue(j.contains("\"comment\": \"stairs \\\"don't\\\" go\\nto top \\\\ \\t\""), j);
		assertTrue(j.contains("\"seed\": \"1709797029209580947\"") && j.contains("\"small\": 987654321,"), j);
		assertTrue(j.contains("\"yaw\": 12.35") && j.contains("\"x\": 2721.500") && j.contains("\"none\": null") && j.contains("[1, 2]") && j.contains("\"a\": true"), j);
		assertTrue(j.endsWith("}\n"));
	}

	@Test
	void debugSwitch() {
		assertFalse(PlaytestNote.enabled(false, null, null), "off by default");
		assertTrue(PlaytestNote.enabled(true, null, null), "on in the development environment");
		assertTrue(PlaytestNote.enabled(false, "true", null));
		assertTrue(PlaytestNote.enabled(false, null, " True "));
		assertFalse(PlaytestNote.enabled(false, null, "yes"));
		assertFalse(PlaytestNote.enabled(true, "false", "true"), "an explicit property wins, even in development");
		assertTrue(PlaytestNote.enabled(false, "true", "false"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void plannerFieldsInsideABuildingAndOutsideAnySettlement() {
		FixtureWorldPlanner w = FixtureWorldPlanner.sample();
		int rooms = 0, withStreet = 0;
		for (BuildingPiece b : w.buildings(0, 0)) {
			for (Room r : b.building().shell().rooms()) {
				Box3 p = b.building().placement().toWorld(r.parts().get(0));
				int x = p.x() + p.sx() / 2, z = p.z() + p.sz() / 2;
				Map<String, Object> m = PlanLookup.at(w, x, p.y(), z);
				assertNotNull(m.get("settlement"), "inside the sample town: " + m);
				Map<String, Object> bm = (Map<String, Object>) m.get("building");
				assertEquals(b.building().id(), bm.get("id"));
				assertEquals(b.brief().id(), ((Map<String, Object>) m.get("lot")).get("id"));
				assertEquals(b.building().shell().spec().type(), bm.get("type"));
				assertEquals(b.building().shell().spec().specSeed(), bm.get("seed"));
				Map<String, Object> rm = (Map<String, Object>) m.get("room");
				assertNotNull(rm, "room at its own centre: " + m);
				rooms++;
				String json = PlaytestNote.json(m);
				assertTrue(json.contains("\"room\"") && json.contains("\"building\""), json);
				if (rooms > 40) break;
			}
			if (rooms > 40) break;
		}
		assertTrue(rooms > 10, "rooms checked " + rooms);
		// a street column: some lot-free column of the first tile resolves to a street
		for (int z = 0; z < 280 && withStreet == 0; z += 2) for (int x = 0; x < 280; x += 2) {
			if (PlanLookup.at(w, x, 80, z).containsKey("street")) { withStreet++; break; }
		}
		assertEquals(1, withStreet, "no street found in the sample town");
		Map<String, Object> far = PlanLookup.at(w, 90_000, 70, 90_000);
		assertEquals(1, far.size());
		assertTrue(far.containsKey("settlement") && far.get("settlement") == null, "" + far);
	}
}
