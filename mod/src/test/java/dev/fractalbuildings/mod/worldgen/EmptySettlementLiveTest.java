package dev.fractalbuildings.mod.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.TerrainDumpTool;
import dev.fractalbuildings.mod.probe.LiveProbes;
import org.junit.jupiter.api.Test;

/**
 * 6.24 EMPTYCITY: the settlements that resolve with no building on the coarse 64-block world dumps of the two playtest seeds
 * (HAMLETEMPTY's finding: city sm1_0 of 3141592653589793238 with no road off its hub, its hamlet s0_m6, and four villages and hamlets of
 * 1709797029209580947) are built on the game's own terrain. Plans each through a {@link WorldPlanner} over {@link LiveProbes} (vanilla
 * noise, no server), as the game does, and counts the buildings of the tiles the settlement owns. They were dump artefacts: the 64-block
 * dump interpolates the 32-block skeleton lattice and the exact terrain (the city's hub sat in an interpolated pit that no road can leave).
 */
class EmptySettlementLiveTest {
	static final long PT3141 = 3141592653589793238L, PT1709 = 1709797029209580947L;

	/** Buildings in the tiles the settlement at (x, z) owns; asserts it is the expected one. */
	static int buildings(WorldPlanner w, int x, int z, String id, Tier tier) {
		SettlementSite s = w.nearest(x, z, Tier.HAMLET, 0).orElseThrow().site();
		assertEquals(id, s.id());
		assertEquals(tier, s.tier());
		int r = s.radius() + 160, n = 0, tiles = 0;
		var a = w.grid().tileOf(s.centre().x() - r, s.centre().z() - r);
		var b = w.grid().tileOf(s.centre().x() + r, s.centre().z() + r);
		for (int iz = a.iz(); iz <= b.iz(); iz++) {
			for (int ix = a.ix(); ix <= b.ix(); ix++) {
				var t = w.tile(ix, iz);
				if (t.isEmpty() || !t.get().site().id().equals(id)) continue;
				tiles++;
				n += w.buildings(ix, iz).size();
			}
		}
		System.out.println("EMPTYCITY live " + id + " " + tier.json + " tiles=" + tiles + " buildings=" + n);
		assertTrue(tiles > 0, id + " owns no tile");
		return n;
	}

	static WorldPlanner live(long seed) {
		LiveProbes lp = new LiveProbes(TerrainDumpTool.generator(), TerrainDumpTool.randomState(seed));
		return new WorldPlanner(seed, WorldConfig.defaults(), lp.world(lp.exact()), null, WorldPlanner.Caches.DEFAULT);
	}

	@Test
	void seed3141CityAndHamletAreBuilt() {
		WorldPlanner w = live(PT3141);
		// `/fb locate city` at spawn returns this city (the command reads the region field only, as nearest() does here)
		assertEquals("sm1_0", w.nearest(0, 0, Tier.CITY, 3).orElseThrow().site().id());
		assertTrue(buildings(w, -384, 256, "sm1_0", Tier.CITY) >= 100, "city sm1_0 is built");
		assertTrue(buildings(w, 384, -5696, "s0_m6", Tier.HAMLET) > 0, "hamlet s0_m6 is built");
	}

	@Test
	void seed1709VillagesAndHamletsAreBuilt() {
		WorldPlanner w = live(PT1709);
		assertTrue(buildings(w, 2560, -448, "s2_m1", Tier.VILLAGE) > 0);
		assertTrue(buildings(w, 4800, 1280, "s4_1", Tier.VILLAGE) > 0);
		assertTrue(buildings(w, -1664, -1600, "sm2_m2", Tier.HAMLET) > 0);
		assertTrue(buildings(w, -640, 3264, "sm1_3", Tier.HAMLET) > 0);
	}
}
