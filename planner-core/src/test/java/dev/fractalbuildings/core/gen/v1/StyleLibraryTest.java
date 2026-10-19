package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.Role;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Every style file loads, names every role in its palette and colours, and no biome maps to two styles. */
class StyleLibraryTest {
	@Test
	void everyStyleLoadsWithAFullPalette() {
		assertTrue(StyleLibrary.ids().containsAll(List.of("plain", "medieval")));
		for (String id : StyleLibrary.ids()) {
			StyleRules s = StyleLibrary.get(id);
			for (Role r : Role.values()) {
				if (r == Role.AIR) continue;
				String k = r.name().toLowerCase(Locale.ROOT);
				assertTrue(s.palette().containsKey(k) && !s.palette().get(k).isEmpty(), id + " palette lacks " + k);
				assertEquals(s.palette().get(k).size(), s.colours().get(k).size(), id + " colours for " + k + " must parallel the palette");
				for (String c : s.colours().get(k)) assertTrue(c.matches("#[0-9a-f]{6}"), id + " colour " + c);
			}
			assertTrue(s.massing().ground() >= 4 && s.massing().typical() >= 4, id);
		}
	}

	@Test
	void biomesMapToOneStyle() {
		TreeMap<String, String> seen = new TreeMap<>();
		for (String id : StyleLibrary.ids())
			for (String b : StyleLibrary.get(id).biomes()) {
				String prev = seen.put(b, id);
				if (prev != null) fail("biome " + b + " is claimed by " + prev + " and " + id);
			}
		assertEquals("plains", StyleLibrary.forBiome("minecraft:plains").id());
		assertEquals(StyleLibrary.FALLBACK, StyleLibrary.forBiome("minecraft:the_void").id());
	}

	@Test
	void extendsMergesOneLevelDeep() {
		StyleRules m = StyleLibrary.get("medieval");
		assertEquals(1, m.massing().jetty());
		assertEquals(4, m.massing().ground());
		assertThrows(IllegalArgumentException.class, () -> StyleLibrary.get("gothic"));
	}
}
