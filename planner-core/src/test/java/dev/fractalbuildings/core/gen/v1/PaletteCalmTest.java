package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.json.JsonReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PALETTE (Phase 6, the user's playtest: walls read "noisy, checkerboardy"): the bulk roles of every style keep their
 * variants close in colour, so per-cell variation reads as texture. Colours come from {@code block-colours.json}
 * (texture averages), and the viewer's {@code colours} lists must stay parallel to the palette (same length, same colour).
 */
class PaletteCalmTest {
	private static final List<String> BULK = List.of("foundation", "floor", "wall", "roof");

	@SuppressWarnings("unchecked")
	private static Map<String, Object> table() throws IOException {
		try (var in = PaletteCalmTest.class.getResourceAsStream("/block-colours.json")) {
			return (Map<String, Object>) JsonReader.parse(new String(in.readAllBytes()));
		}
	}

	private static int[] rgb(String hex) {
		int v = Integer.parseInt(hex.substring(1), 16);
		return new int[] {v >> 16 & 255, v >> 8 & 255, v & 255};
	}

	private static double dist(String a, String b) {
		int[] p = rgb(a), q = rgb(b);
		return Math.sqrt(Math.pow(p[0] - q[0], 2) + Math.pow(p[1] - q[1], 2) + Math.pow(p[2] - q[2], 2));
	}

	@Test
	void bulkRolesHaveCloseColoursAndViewerColoursMatchTheBlocks() throws IOException {
		Map<String, Object> tbl = table();
		for (String id : StyleLibrary.ids()) {
			StyleRules s = StyleLibrary.get(id);
			for (String role : BULK) {
				List<String> ids = s.palette().get(role), cols = s.colours().get(role);
				assertEquals(ids.size(), cols.size(), id + "." + role + ": colours parallel to palette");
				double worst = 0;
				for (int i = 0; i < ids.size(); i++) {
					assertNotNull(tbl.get(ids.get(i)), "block-colours.json lacks " + ids.get(i));
					assertEquals(tbl.get(ids.get(i)), cols.get(i), id + "." + role + "[" + i + "] viewer colour for " + ids.get(i));
					for (int j = 0; j < i; j++) worst = Math.max(worst, dist(cols.get(i), cols.get(j)));
				}
				double limit = role.equals("wall") ? 45 : 85; // walls are the bulk of a facade; roofs/foundations may carry one accent
				assertTrue(worst <= limit, id + "." + role + " variants differ by " + Math.round(worst) + " (limit " + limit + ")");
			}
		}
	}
}
