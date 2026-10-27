package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The taiga style (4.7): steep roofs, spruce palette with valid vanilla ids, its own biomes. */
class TaigaStyleTest {
	/** Block ids checked against the 26.3 client jar's blockstates. */
	static final Set<String> VALID = Set.of("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:andesite", "minecraft:stone",
			"minecraft:spruce_planks", "minecraft:dark_oak_planks", "minecraft:stripped_spruce_wood", "minecraft:stripped_spruce_log",
			"minecraft:spruce_log", "minecraft:spruce_fence", "minecraft:glass_pane", "minecraft:spruce_door", "minecraft:mud_bricks",
			"minecraft:brown_terracotta", "minecraft:deepslate_bricks",
			"minecraft:dirt_path", "minecraft:gravel", "minecraft:coarse_dirt", "minecraft:stone_bricks", "minecraft:podzol", "minecraft:dirt", "minecraft:cobbled_deepslate", "minecraft:light_gray_concrete");
	static final Set<String> BIOMES = Set.of("minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga");

	@Test
	void paletteHasThreeToFiveValidBlocksPerRoleAndBiomesAreDisjoint() {
		StyleRules s = StyleLibrary.get("taiga");
		for (Role r : Role.values()) {
			if (r == Role.AIR) continue;
			List<String> p = s.palette().get(r.name().toLowerCase(Locale.ROOT));
			for (String id : p) assertTrue(VALID.contains(id), "unknown block " + id);
		}
		for (String role : List.of("foundation", "wall", "roof")) {
			int n = s.palette().get(role).size();
			assertTrue(n >= 3 && n <= 5, role + " has " + n);
		}
		assertEquals(BIOMES, Set.copyOf(s.biomes()));
		for (String b : BIOMES) assertEquals("taiga", StyleLibrary.forBiome(b).id());
		for (String id : StyleLibrary.ids())
			if (!id.equals("taiga")) for (String b : StyleLibrary.get(id).biomes()) assertTrue(!BIOMES.contains(b), id + " claims " + b);
	}

	@Test
	void atLeastEightyPercentOfPitchedRoofsAreTwoToOneOrSteeper() {
		int pitched = 0, steep = 0, scenes = 0;
		for (String type : List.of("house", "tower"))
			for (int[] lot : new int[][] {{16, 16}, {20, 20}, {13, 13}})
				for (long seed = 0; seed < 60; seed++) {
					ProbeSpecs.Probes p = ProbeSpecs.parse("flat:64");
					GenRequest rq = new GenRequest(seed, new Lot("lot0", 0, 0, lot[0], lot[1], Facing.NORTH), type, "taiga", p.terrain(), p.biome());
					SceneDoc s = new GeneratorV1().generate(rq);
					assertTrue(Invariants.check(s, rq.terrain()).isEmpty(), type + " seed " + seed);
					scenes++;
					for (PlacedBuilding b : s.buildings())
						for (Roof r : b.shell().roofs()) {
							if (r.form().equals("flat")) continue;
							pitched++;
							if (r.rise() >= 2 * r.run()) steep++;
						}
				}
		assertTrue(pitched > scenes / 2, "too few pitched roofs: " + pitched);
		assertTrue(steep * 10 >= pitched * 8, steep + " of " + pitched + " pitched roofs are >= 2/1");
	}
}
