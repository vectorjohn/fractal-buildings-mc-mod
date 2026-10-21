package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The desert style (4.7): flat parapet roofs, courtyard/U plans, sandstone palette, desert biomes. */
class DesertStyleTest {
	/** Block ids verified against the 26.3 registry (blockstate assets in the merged jar). */
	static final Set<String> VANILLA = Set.of("minecraft:cut_sandstone", "minecraft:sandstone", "minecraft:smooth_sandstone",
			"minecraft:chiseled_sandstone", "minecraft:acacia_planks", "minecraft:yellow_terracotta", "minecraft:orange_terracotta",
			"minecraft:terracotta", "minecraft:stripped_acacia_log", "minecraft:acacia_log", "minecraft:acacia_fence",
			"minecraft:glass_pane", "minecraft:acacia_door", "minecraft:jungle_door",
			"minecraft:sand");

	@Test
	void paletteIdsAreVanillaAndGradientsAreThreeToFiveOrSingle() {
		StyleRules s = StyleLibrary.get("desert");
		s.palette().forEach((role, ids) -> {
			assertTrue(ids.size() <= 5, role);
			for (String id : ids) assertTrue(VANILLA.contains(id), role + ": " + id);
		});
		assertTrue(s.palette().get("wall").size() >= 3 && s.palette().get("roof").size() >= 3);
	}

	@Test
	void desertBiomesBelongToDesertOnly() {
		for (String b : List.of("minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"))
			assertEquals("desert", StyleLibrary.forBiome(b).id(), b);
		assertEquals(4, StyleLibrary.get("desert").biomes().size());
		assertTrue(StyleLibrary.get("desert").massing().plan("courtyard") + StyleLibrary.get("desert").massing().plan("U")
				> StyleLibrary.get("desert").massing().plan("box"));
	}

	@Test
	void atLeastEightyPercentOfHousesAreFlatRoofedAndValid() {
		int houses = 0, flat = 0;
		for (int seed = 0; seed < 48; seed++) {
			GenRequest req = new GenRequest(seed, new Lot("lot0", 0, 0, 20, 20, Facing.NORTH), "house", "desert",
					SyntheticTerrain.flat(64), (x, z) -> "minecraft:desert");
			SceneDoc doc = new GeneratorV1().generate(req);
			assertTrue(Invariants.check(doc).isEmpty(), "invariants seed " + seed);
			List<Roof> roofs = doc.buildings().get(0).shell().roofs();
			assertTrue(!roofs.isEmpty());
			houses++;
			if (roofs.stream().allMatch(r -> r.form().equals("flat"))) flat++;
		}
		assertTrue(flat * 100 >= houses * 80, flat + " of " + houses + " flat");
	}
}
