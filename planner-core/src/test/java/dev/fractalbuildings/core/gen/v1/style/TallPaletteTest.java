package dev.fractalbuildings.core.gen.v1.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tall palettes (DS-1, DS-41, DS-42): every style a rule can pick sets its structural roles itself, in masonry. */
class TallPaletteTest {
	private static final List<String> SINGLE = List.of("wall", "beam", "trim", "roof", "foundation", "deck", "rail");
	private static final List<String> MASONRY = List.of("stone", "brick", "terracotta", "concrete", "quartz", "deepslate", "tuff",
			"blackstone", "sandstone", "iron", "copper", "glass", "andesite", "granite", "diorite");
	private static final List<String> FAMILY = List.of("tall-brick", "tall-stone", "tall-terracotta", "tall-concrete");

	private static boolean timber(String id) { return id.startsWith("mass-timber"); }

	private static Set<String> vanilla() throws IOException {
		try (var in = TallPaletteTest.class.getResourceAsStream("/vanilla-26.3-blocks.txt")) {
			return Set.of(new String(in.readAllBytes()).split("\\R"));
		}
	}

	@Test
	void everyPickTargetSetsItsRolesInMasonryWithOneWallBlock() throws IOException {
		Set<String> blocks = vanilla();
		var targets = StyleVariants.pickTargets();
		assertTrue(targets.containsAll(FAMILY), "the tall family is picked: " + targets);
		for (String id : targets) {
			StyleRules s = StyleLibrary.get(id);
			assertTrue(s.biomes().isEmpty(), id + " has biomes");
			for (String role : id.startsWith("keep") ? List.of("wall", "beam", "trim") : SINGLE) { // keeps are towers with a pitched roof list
				List<String> b = s.palette().get(role);
				assertEquals(1, b.size(), id + "." + role + " is one block (DS-42)");
				String name = b.get(0).replace("minecraft:", "");
				if (!timber(id) || !(role.equals("wall") || role.equals("beam") || role.equals("trim"))) {
					assertTrue(MASONRY.stream().anyMatch(name::contains), id + "." + role + " " + name + " is not a high-rise material");
					assertFalse(name.contains("_log") || name.contains("_wood") || name.contains("_planks") || name.contains("_fence") || name.contains("bamboo") || name.contains("_wool"), id + "." + role + " " + name);
				}
			}
			for (List<String> bs : s.palette().values()) for (String b : bs) assertTrue(blocks.contains(b.replace("minecraft:", "")) || blocks.contains(b), id + ": unknown block " + b);
		}
	}

	@Test
	void everyVariantSetsEveryRoleItselfSoNothingLeaksFromPlain() throws IOException {
		for (String id : FAMILY) {
			StyleRules s = StyleLibrary.get(id);
			for (String role : List.of("wall", "beam", "trim", "deck", "rail", "roof", "foundation", "floor", "stair", "fence")) {
				assertFalse(s.palette().get(role).get(0).matches(".*(_log|_planks|_fence)$"), id + "." + role);
			}
			assertEquals("minecraft:smooth_stone", s.palette().get("deck").get(0), id);
			// the file itself carries the keys (an inherited plain value would pass the checks above only by luck)
			String text = Files.readString(Path.of("src/main/resources/styles/" + id + ".json"));
			for (String role : List.of("wall", "beam", "trim", "deck", "rail", "roof", "floor")) assertTrue(text.contains("\"" + role + "\": [\"minecraft:"), id + " sets " + role);
		}
	}

	@Test
	void documentedTerracottaAndBrickAccents() {
		assertEquals("minecraft:smooth_sandstone", StyleLibrary.get("tall-terracotta").palette().get("beam").get(0));
		assertEquals("minecraft:smooth_sandstone", StyleLibrary.get("tall-terracotta").palette().get("trim").get(0));
		assertEquals("minecraft:smooth_sandstone", StyleLibrary.get("tall-brick").palette().get("trim").get(0));
	}

	@Test
	void everyStyleThatAHighriseCanGetMapsIntoTheFamilyAndKeepsItsTint() {
		for (String base : List.of("plain", "plains", "medieval", "desert", "jungle", "snowy", "taiga")) {
			var seen = new java.util.TreeSet<String>();
			for (long s = 0; s < 200; s++) seen.add(StyleVariants.select(base, "highrise", 12, null, null, s));
			assertTrue(FAMILY.containsAll(seen) && !seen.isEmpty(), base + " -> " + seen);
		}
		for (long s = 0; s < 200; s++) assertFalse(StyleVariants.select("desert", "highrise", 12, null, null, s).equals("tall-stone"), "desert keeps warm tones");
	}
}
