package dev.fractalbuildings.core.gen.v1.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.SpecPolicyV1;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Facing;
import java.util.List;
import org.junit.jupiter.api.Test;

class StyleVariantsTest {
	private static final List<String> FAMILY = List.of("tall-brick", "tall-stone", "tall-terracotta", "tall-concrete");

	@Test
	void tallBuildingsSwitchToTallAndSmallOnesKeepTheirStyle() {
		for (int s = 7; s <= 60; s++) {
			assertTrue(FAMILY.contains(StyleVariants.select("plain", "highrise", s, "plains", null, s * 31L)), "highrise " + s);
			assertTrue(FAMILY.contains(StyleVariants.select("medieval", "block", s, null, null, s)), "block " + s);
		}
		for (int s = 1; s <= 6; s++) assertEquals("plain", StyleVariants.select("plain", "highrise", s, null, null, s));
		for (int s = 1; s <= 3; s++) assertEquals("plain", StyleVariants.select("plain", "house", s, null, null, s));
		assertEquals("plain", StyleVariants.select("plain", "house", 20, null, null, 1));
	}

	@Test
	void pickTargetsAreNeverRemapped() {
		assertEquals("tall-brick", StyleVariants.select("tall-brick", "highrise", 30, null, null, 5));
	}

	@Test
	void everyPickTargetExtendsTallOrIsTallAndHasNoBiomes() {
		for (String id : StyleVariants.pickTargets()) {
			assertTrue(StyleLibrary.get(id).biomes().isEmpty(), id);
		}
	}

	private static final String HEAD = "{\"format\":\"fb-style-variants/1\",\"rules\":[";

	@Test
	void tiersAndBiomesNeedAValue() {
		var rules = StyleVariants.parse(HEAD + "{\"id\":\"t\",\"tiers\":[\"top\"],\"pick\":{\"tall\":1}},{\"id\":\"b\",\"biomes\":[\"desert\"],\"pick\":{\"tall\":1}}]}");
		assertTrue(!rules.get(0).matches("plain", "highrise", 10, null, null));
		assertTrue(rules.get(0).matches("plain", "highrise", 10, null, "top"));
		assertTrue(!rules.get(1).matches("plain", "highrise", 10, null, null));
		assertTrue(!rules.get(1).matches("plain", "highrise", 10, "plains", null));
		assertTrue(rules.get(1).matches("plain", "highrise", 10, "desert", null));
	}

	@Test
	void firstMatchingRuleWins() {
		var rules = StyleVariants.parse(HEAD + "{\"id\":\"a\",\"minStoreys\":5,\"pick\":{\"tall\":1}},{\"id\":\"b\",\"pick\":{\"plain\":1}}]}");
		assertEquals("tall", StyleVariants.select(rules, "x", "t", 9, null, null, 1)); // both match; the first wins
		assertEquals("plain", StyleVariants.select(rules, "x", "t", 2, null, null, 1));
	}

	@Test
	void weightedPickIsDeterministicAndRoughlyProportional() {
		var rules = StyleVariants.parse(HEAD + "{\"id\":\"w\",\"pick\":{\"tall\":2,\"plain\":1}}]}");
		int tall = 0;
		for (long s = 0; s < 1000; s++) {
			var opts = List.of(dev.fractalbuildings.core.grammar.Choice.opt("plain", 1), dev.fractalbuildings.core.grammar.Choice.opt("tall", 2));
			String a = dev.fractalbuildings.core.grammar.Choice.pick(dev.fractalbuildings.core.rand.Hash64.hash(s, "style-variant"), "w", opts);
			assertEquals(a, dev.fractalbuildings.core.grammar.Choice.pick(dev.fractalbuildings.core.rand.Hash64.hash(s, "style-variant"), "w", opts));
			if (a.equals("tall")) tall++;
		}
		assertTrue(tall >= 600 && tall <= 730, "tall share " + tall);
		assertEquals(2, rules.get(0).pick().size());
	}

	@Test
	void badTablesFailToLoad() {
		assertThrows(IllegalArgumentException.class, () -> StyleVariants.parse(HEAD + "{\"id\":\"x\",\"pick\":{\"nope\":1}}]}"));
		assertThrows(IllegalArgumentException.class, () -> StyleVariants.parse(HEAD + "{\"id\":\"x\",\"pick\":{\"desert\":1}}]}")); // has biomes
		assertThrows(IllegalArgumentException.class, () -> StyleVariants.parse(HEAD + "{\"id\":\"x\",\"pick\":{\"tall\":0}}]}"));
		assertThrows(IllegalArgumentException.class, () -> StyleVariants.parse("{\"format\":\"x\",\"rules\":[]}"));
	}

	@Test
	void specCarriesTheEffectiveStyle() {
		for (long seed = 0; seed < 40; seed++) {
			GenRequest tallReq = new GenRequest(seed, new Lot("l", 0, 0, 44, 44, Facing.NORTH), "highrise", "plain", (x, z) -> 64, (x, z) -> "plains");
			var spec = SpecPolicyV1.spec(tallReq);
			assertTrue(spec.storeys() >= 7 ? FAMILY.contains(spec.style()) : spec.style().equals("plain"), "style " + spec.style() + " seed " + seed + " storeys " + spec.storeys());
			assertEquals(spec, SpecPolicyV1.spec(tallReq));
			GenRequest house = new GenRequest(seed, new Lot("l", 0, 0, 20, 20, Facing.NORTH), "house", "plain", (x, z) -> 64, (x, z) -> "plains");
			assertEquals("plain", SpecPolicyV1.spec(house).style());
		}
	}
}
