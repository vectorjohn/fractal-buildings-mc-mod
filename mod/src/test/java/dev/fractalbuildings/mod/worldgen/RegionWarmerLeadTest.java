package dev.fractalbuildings.mod.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** METROWARM: the warmer's look-ahead grows with speed and is capped. */
class RegionWarmerLeadTest {
	@Test
	void growsWithSpeed() {
		double prev = -1;
		for (double v = 0; v <= 70; v += 1) {
			double l = RegionWarmer.leadBlocks(v);
			assertTrue(l >= prev, "lead must not shrink with speed at " + v);
			prev = l;
		}
		assertEquals(0, RegionWarmer.leadBlocks(0), 1e-9);
		assertEquals(4 * RegionWarmer.LEAD_SECONDS, RegionWarmer.leadBlocks(4), 1e-9, "walking keeps the old 5 s lead");
		assertTrue(RegionWarmer.leadBlocks(60) >= 1500, "elytra looks well past the old 300 blocks");
	}

	@Test
	void isCapped() {
		assertEquals(RegionWarmer.MAX_LEAD, RegionWarmer.leadBlocks(1000), 1e-9);
		assertTrue(RegionWarmer.leadBlocks(60) <= RegionWarmer.MAX_LEAD);
	}
}
