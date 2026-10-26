package dev.fractalbuildings.core.gen.v1.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Mass timber (docs/design/styles/mass-timber.md, DS-1 exception): forest high-rises of 7..20 storeys only. */
class MassTimberRuleTest {
	private static final List<String> TALL = List.of("tall-brick", "tall-stone", "tall-terracotta", "tall-concrete");
	private static final Map<String, String> BIOME_WOOD = Map.ofEntries(
			Map.entry("minecraft:taiga", "spruce"), Map.entry("minecraft:old_growth_pine_taiga", "spruce"),
			Map.entry("minecraft:old_growth_spruce_taiga", "spruce"), Map.entry("minecraft:dark_forest", "spruce"),
			Map.entry("minecraft:forest", "oak"), Map.entry("minecraft:flower_forest", "oak"),
			Map.entry("minecraft:birch_forest", "birch"), Map.entry("minecraft:old_growth_birch_forest", "birch"),
			Map.entry("minecraft:cherry_grove", "cherry"), Map.entry("minecraft:pale_garden", "pale-oak"),
			Map.entry("minecraft:jungle", "jungle"), Map.entry("minecraft:sparse_jungle", "jungle"), Map.entry("minecraft:bamboo_jungle", "jungle"));

	private static boolean isTimber(String id) { return id.startsWith("mass-timber"); }

	private static Path resources() { return Path.of("src/main/resources/styles"); }

	@Test
	void noRuleThatPicksMassTimberExceedsTwentyStoreys() throws IOException {
		String text = Files.readString(resources().resolve("variants.json"));
		var rules = StyleVariants.parse(text);
		int n = 0;
		for (var r : rules) {
			if (r.pick().keySet().stream().noneMatch(MassTimberRuleTest::isTimber)) continue;
			n++;
			assertTrue(r.maxStoreys() <= 20, r.id() + " maxStoreys " + r.maxStoreys());
			assertTrue(r.minStoreys() >= 1, r.id());
			assertTrue(r.minStoreys() >= 7, r.id() + ": under 7 storeys needs no timber exception");
			assertTrue(r.biomes() != null && !r.biomes().isEmpty(), r.id() + " names biomes");
		}
		assertEquals(6, n);
	}

	@Test
	void forestHighrisesPickTheirSpeciesUpToTwentyStoreysAndNeverAbove() {
		for (var e : BIOME_WOOD.entrySet()) {
			String want = "mass-timber-" + e.getValue();
			var seen = new TreeSet<String>();
			for (long s = 0; s < 300; s++) {
				for (int storeys : new int[] {7, 15, 20}) seen.add(StyleVariants.select("plain", "highrise", storeys, e.getKey(), null, s));
				for (int storeys : new int[] {21, 30, 60}) assertTrue(TALL.contains(StyleVariants.select("plain", "highrise", storeys, e.getKey(), null, s)), e.getKey() + " " + storeys);
			}
			assertEquals(new TreeSet<>(List.of(want, "tall-stone")), seen, e.getKey());
		}
	}

	@Test
	void shareLeansToTimberAndSixStoreysOrOtherBiomesNeverGetIt() {
		for (String biome : BIOME_WOOD.keySet()) {
			int timber = 0;
			for (long s = 0; s < 1000; s++) if (isTimber(StyleVariants.select("plain", "highrise", 15, biome, null, s))) timber++;
			assertTrue(timber >= 620 && timber <= 780, biome + " timber share " + timber + "/1000 (7:3)");
		}
		for (String biome : new String[] {"minecraft:plains", "minecraft:desert", "minecraft:savanna", "minecraft:snowy_plains", null})
			for (int storeys = 7; storeys <= 40; storeys++)
				for (long s = 0; s < 20; s++) assertFalse(isTimber(StyleVariants.select("plain", "highrise", storeys, biome, null, s)), biome + " " + storeys);
		for (int storeys = 1; storeys <= 6; storeys++) assertEquals("plain", StyleVariants.select("plain", "highrise", storeys, "minecraft:taiga", null, 3));
		assertEquals("plain", StyleVariants.select("plain", "house", 15, "minecraft:taiga", null, 3));
	}

	@Test
	void timberStylesAreStructurallyConsistent() {
		for (String id : StyleLibrary.ids()) {
			if (!isTimber(id)) continue;
			StyleRules s = StyleLibrary.get(id);
			assertTrue(s.biomes().isEmpty(), id);
			for (String role : List.of("wall", "beam", "trim", "foundation", "roof", "deck", "rail", "door")) {
				assertEquals(1, s.palette().get(role).size(), id + "." + role + " is one block");
			}
			String wall = s.palette().get("wall").get(0);
			assertTrue(wall.endsWith("_planks") && !wall.contains("bamboo"), id + " wall " + wall);
			String beam = s.palette().get("beam").get(0), trim = s.palette().get("trim").get(0);
			assertTrue(beam.startsWith("minecraft:stripped_") && beam.endsWith("_wood"), id + " beam " + beam);
			assertTrue(trim.startsWith("minecraft:stripped_") && trim.endsWith("_wood"), id + " trim " + trim);
			assertEquals("minecraft:smooth_stone", s.palette().get("foundation").get(0), id);
			assertEquals("minecraft:smooth_stone", s.palette().get("deck").get(0), id);
			assertEquals("minecraft:iron_bars", s.palette().get("rail").get(0), id);
			assertTrue(!s.palette().get("wall").contains("minecraft:bamboo_planks") && s.palette().values().stream().flatMap(List::stream).noneMatch(b -> b.endsWith("_log") || b.contains("bamboo")), id + " has a log or bamboo");
		}
	}
}
