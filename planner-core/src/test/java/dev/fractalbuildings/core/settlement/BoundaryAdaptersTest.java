package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.terrain.GroundBox;
import dev.fractalbuildings.core.terrain.LatticePath;
import dev.fractalbuildings.core.terrain.WaterSamples;
import org.junit.jupiter.api.Test;

/** The small adapters between the terrain toolkit, the district grid and the settlement types. */
class BoundaryAdaptersTest {
	@Test
	void waterSamplesFollowTheCoarseProbesWaterRule() {
		// a shallow (depth 1) river is land for the probe unless the biome is water; the river modifier also sets the river biome
		CoarseProbe probe = SkeletonFixtures.probe("flat:64+river:60:40:6");
		WaterSamples w = WaterSamples.of(probe);
		assertEquals(32, w.stride());
		assertTrue(w.water(64, 0));
		assertFalse(w.water(0, 0));
		for (int x = 0; x < 160; x += 32) assertEquals(probe.water(x, 0), w.water(x, 0));
	}

	@Test
	void defaultCostScalesByTheSearchStepNotTheWaterStride() {
		var t = ProbeSpecs.parse("flat:64").terrain();
		WaterSamples coarse = WaterSamples.of(t, 32);
		assertEquals(10L * 8, LatticePath.defaultCost(t, coarse, 8).step(0, 0, 8, 0));
		assertEquals(14L * 8, LatticePath.defaultCost(t, coarse, 8).step(0, 0, 8, 8));
		assertEquals(10L * 32, LatticePath.defaultCost(t, coarse).step(0, 0, 32, 0), "the old overload still uses the stride");
	}

	@Test
	void groundBoxKindsMapToSceneRoles() {
		assertEquals(Role.ROAD, GroundBox.Kind.ROAD.role());
		assertEquals(Role.PAVING, GroundBox.Kind.PLAZA.role());
		assertEquals(Role.GROUND, GroundBox.Kind.FILL.role());
		assertEquals(Role.AIR, GroundBox.Kind.CUT.role());
		for (GroundBox.Kind k : GroundBox.Kind.values()) assertNotNull(k.role());
	}

	@Test
	void aDistrictTileIsAPlanRect() {
		DistrictGrid g = new DistrictGrid(3);
		for (int i = -2; i < 3; i++) {
			DistrictGrid.Tile t = g.tile(i, 2 - i);
			assertEquals(new Rect(t.minX(), t.minZ(), t.widthX(), t.widthZ()), t.rect());
		}
	}
}
