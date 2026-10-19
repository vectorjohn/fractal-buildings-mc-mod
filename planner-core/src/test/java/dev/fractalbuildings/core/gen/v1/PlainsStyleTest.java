package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.SceneDoc;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The plains style (4.7): valid vanilla ids, the plains-family biomes, and its own look (no jetties, pitched roofs). */
class PlainsStyleTest {
	static Set<String> lines(String resource) throws IOException {
		try (var in = PlainsStyleTest.class.getResourceAsStream("/" + resource)) {
			return new HashSet<>(List.of(new String(in.readAllBytes()).split("\n")));
		}
	}

	@Test
	void paletteBlocksAndBiomesAreVanilla26_3() throws IOException {
		Set<String> blocks = lines("vanilla-26.3-blocks.txt"), biomes = lines("vanilla-26.3-biomes.txt");
		for (String id : StyleLibrary.ids()) {
			StyleRules s = StyleLibrary.get(id);
			s.palette().forEach((role, ids) -> {
				for (String b : ids) assertTrue(blocks.contains(b), id + "." + role + ": unknown block " + b);
			});
			for (String b : s.biomes()) assertTrue(biomes.contains(b), id + ": unknown biome " + b);
		}
	}

	@Test
	void plainsHasAGradientPerRoleAndOwnsThePlainsFamily() {
		StyleRules p = StyleLibrary.get("plains");
		for (String role : List.of("foundation", "floor", "wall", "roof"))
			assertTrue(p.palette().get(role).size() >= 3 && p.palette().get(role).size() <= 5, role);
		for (String b : List.of("minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest", "minecraft:flower_forest")) {
			assertEquals("plains", StyleLibrary.forBiome(b).id(), b);
		}
		assertTrue(StyleLibrary.get("medieval").biomes().isEmpty(), "medieval keeps no biomes; it is requested by name");
		// no biome is claimed twice (also asserted for every style by StyleLibraryTest)
		TreeMap<String, String> seen = new TreeMap<>();
		for (String id : StyleLibrary.ids())
			for (String b : StyleLibrary.get(id).biomes()) assertEquals(null, seen.put(b, id), b);
	}

	@Test
	void plainsHousesHaveNoJettiesAndPitchedRoofsOnly() {
		HouseMassingTest.Stats s = HouseMassingTest.stats("plains");
		assertEquals(0, s.jettied(), "plains has no jetties");
		assertTrue(s.multi() >= 0.30 * s.houses(), "wings occur " + s);
		int roofs = 0, pitched = 0;
		for (long seed = 0; seed < 150; seed++) {
			V1Case c = new V1Case(seed, "house", "plains", 20, 20, 0, 0, dev.fractalbuildings.core.ir.Facing.NORTH, "flat:64");
			GenRequest rq = c.request();
			SceneDoc sc = new GeneratorV1().generate(rq);
			assertEquals(List.of(), Invariants.check(sc, rq.terrain()), c.replay());
			for (PlacedBuilding b : sc.buildings())
				for (Roof r : b.shell().roofs()) {
					roofs++;
					assertTrue(List.of("gable", "hip", "flat").contains(r.form()), r.form());
					if (!r.form().equals("flat")) pitched++;
				}
		}
		assertTrue(roofs > 0 && pitched >= 0.9 * roofs, "mostly pitched roofs: " + pitched + "/" + roofs);
	}
}
