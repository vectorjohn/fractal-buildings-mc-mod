package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.SceneDoc;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The snowy style (4.7): valid vanilla ids, snowy biomes, steep roofs and fewer windows than plains. */
class SnowyStyleTest {
	static Set<String> lines(String resource) throws IOException {
		try (var in = SnowyStyleTest.class.getResourceAsStream("/" + resource)) {
			return new HashSet<>(List.of(new String(in.readAllBytes()).split("\n")));
		}
	}

	@Test
	void snowyIdsAreVanillaAndBiomesDisjoint() throws IOException {
		Set<String> blocks = lines("vanilla-26.3-blocks.txt"), biomes = lines("vanilla-26.3-biomes.txt");
		StyleRules s = StyleLibrary.get("snowy");
		for (String role : List.of("foundation", "floor", "wall", "trim", "roof")) {
			int n = s.palette().get(role).size();
			assertTrue(n >= 2 && n <= 5, role + " gradient " + n);
		}
		s.palette().forEach((role, ids) -> ids.forEach(b -> assertTrue(blocks.contains(b), role + ": unknown block " + b)));
		assertTrue(s.palette().get("wall").stream().anyMatch(b -> b.contains("stone")), "stone walls");
		assertTrue(s.palette().get("wall").stream().anyMatch(b -> b.contains("spruce")), "spruce walls");
		for (String b : s.biomes()) {
			assertTrue(biomes.contains(b), "unknown biome " + b);
			assertEquals("snowy", StyleLibrary.forBiome(b).id(), b);
		}
		TreeMap<String, String> seen = new TreeMap<>();
		for (String id : StyleLibrary.ids())
			for (String b : StyleLibrary.get(id).biomes()) assertEquals(null, seen.put(b, id), "biome in two styles: " + b);
	}

	/** Stats over 120 houses on flat 20x20: roofs, pitch, and windows per bounding-box facade area. */
	record Look(int roofs, int steep, int windows, long facadeArea) {
		double density() { return (double) windows / facadeArea; }
	}

	static Look look(String style) {
		int roofs = 0, steep = 0, windows = 0;
		long area = 0;
		for (long seed = 0; seed < 120; seed++) {
			V1Case c = new V1Case(seed, "house", style, 20, 20, 0, 0, Facing.NORTH, "flat:64");
			GenRequest rq = c.request();
			SceneDoc sc = new GeneratorV1().generate(rq);
			assertEquals(List.of(), Invariants.check(sc, rq.terrain()), c.replay());
			for (PlacedBuilding b : sc.buildings()) {
				for (Roof r : b.shell().roofs()) {
					if (r.form().equals("flat")) continue;
					roofs++;
					if (r.rise() >= 2 * r.run()) steep++;
				}
				for (Opening o : b.shell().openings()) if (o.kind() == OpeningKind.WINDOW) windows++;
				var bb = b.shell().bounds();
				area += 2L * (bb.sx() + bb.sz()) * Math.max(1, bb.sy());
			}
		}
		return new Look(roofs, steep, windows, area);
	}

	@Test
	void roofsAreSteepAndWindowsFewerThanPlains() {
		Look snowy = look("snowy"), plains = look("plains");
		assertTrue(snowy.roofs() > 0 && snowy.steep() >= 0.9 * snowy.roofs(), "steep roofs " + snowy);
		assertTrue(snowy.windows() > 0, "has windows " + snowy);
		assertTrue(snowy.density() < plains.density(), "window density snowy " + snowy + " vs plains " + plains);
	}
}
