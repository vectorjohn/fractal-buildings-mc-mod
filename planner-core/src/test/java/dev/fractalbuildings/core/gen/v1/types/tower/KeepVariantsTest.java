package dev.fractalbuildings.core.gen.v1.types.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.SpecPolicyV1;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleVariants;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A2k (DS-1): towers of 7+ storeys switch to a masonry keep palette; no log or plank in the structural roles. */
class KeepVariantsTest {
	static final List<String> BASES = List.of("plain", "medieval", "desert", "jungle", "snowy", "taiga", "plains");
	static final List<String> ROLES = List.of("wall", "beam", "trim", "deck", "rail", "fence", "foundation", "roof", "stair");

	static boolean wooden(String id) {
		return id.endsWith("_log") || id.endsWith("_planks") || id.endsWith("_fence") || id.endsWith("_wood") || id.contains("stripped_") || id.contains("bamboo");
	}

	@Test
	void rulesMapEachBaseToItsKeep() {
		for (int s = 7; s <= 10; s++) {
			assertEquals("keep-desert", StyleVariants.select("desert", "tower", s, null, null, s));
			assertEquals("keep-jungle", StyleVariants.select("jungle", "tower", s, null, null, s));
			for (String b : List.of("plain", "medieval", "snowy", "taiga", "plains")) assertEquals("keep", StyleVariants.select(b, "tower", s, null, null, s), b);
		}
		assertEquals("medieval", StyleVariants.select("medieval", "tower", 6, null, null, 1));
		assertEquals("medieval", StyleVariants.select("medieval", "house", 9, null, null, 1));
		assertEquals("keep", StyleVariants.select("keep", "tower", 9, null, null, 1));
	}

	@Test
	void keepPalettesHaveNoWoodInStructuralRoles() {
		for (String id : List.of("keep", "keep-desert", "keep-jungle")) {
			var st = StyleLibrary.get(id);
			assertTrue(st.biomes().isEmpty(), id);
			for (String role : ROLES) for (String b : st.palette().get(role)) assertFalse(wooden(b), id + " " + role + " " + b);
		}
	}

	@Test
	void everyTallTowerSpecGetsAKeepWithMasonry() {
		int tall = 0;
		for (String base : BASES) {
			for (long seed = 0; seed < 150; seed++) {
				ProbeSpecs.Probes p = ProbeSpecs.parse("flat:64");
				BuildingSpec sp = SpecPolicyV1.spec(new GenRequest(seed, new Lot("l", 0, 0, 15, 15, Facing.NORTH), "tower", base, p.terrain(), p.biome()));
				if (sp.storeys() < 7) { assertEquals(base, sp.style(), base + " " + seed); continue; }
				tall++;
				assertTrue(sp.style().startsWith("keep"), base + " " + seed + " " + sp.style());
				for (String role : ROLES) for (String b : StyleLibrary.get(sp.style()).palette().get(role)) assertFalse(wooden(b), sp.style() + " " + role + " " + b);
			}
		}
		assertTrue(tall >= 20, "tall towers sampled: " + tall);
	}
}
