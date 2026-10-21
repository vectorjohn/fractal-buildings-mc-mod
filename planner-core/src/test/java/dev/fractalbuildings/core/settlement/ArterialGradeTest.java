package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import org.junit.jupiter.api.Test;

/** TR: an arterial runs only where the coarse lattice says a road can be graded along it. */
class ArterialGradeTest {
	private static Arterials on(String spec) {
		ProbeSpecs.Probes p = ProbeSpecs.parse(spec);
		return new Arterials(1, Tier.CITY, null, new CoarseTerrain(CoarseProbe.of(p.terrain(), p.biome(), 32)), new DistrictGrid(1));
	}

	private static final DistrictGrid.Segment ALONG_X = new DistrictGrid.Segment(DistrictGrid.AXIS_Z, 0, 0, 0, 0, 320);

	@Test
	void gentleGroundPasses() {
		assertTrue(on("plane:64:1:0:4").gradeOk(ALONG_X)); // 8 per 32 blocks
		assertTrue(on("flat:64").gradeOk(ALONG_X));
	}

	@Test
	void groundSteeperThanOneInTwoFails() {
		assertFalse(on("plane:64:1:0:1").gradeOk(ALONG_X)); // 32 per 32 blocks
		assertFalse(on("plane:64:3:0:2").gradeOk(ALONG_X)); // 48 per 32
	}

	/** An arterial of a flat city stops being one when the same segment is asked on ground steeper than 1 in 2 (the density and hash stay as they were). */
	@Test
	void isDropsASegmentOnSteepGround() {
		SkeletonsTest.Case c = SkeletonsTest.syntheticCase(1);
		SkeletonSource src = new SkeletonSource.Direct(c.seed(), new FixedSiteView(c.site(), c.field()), c.site(), c.probe());
		Arterials flat = src.density().arterials();
		Pt m = c.site().centre();
		Arterials ramp = new Arterials(c.seed(), c.site().tier(), src.density(), terrain("plane:64:1:1:1"), src.grid());
		int tested = 0;
		for (DistrictGrid.Segment s : flat.segmentsIn(m.x() - 400, m.z() - 400, m.x() + 400, m.z() + 400)) {
			if (!flat.is(s)) continue;
			tested++;
			assertFalse(ramp.is(s), "segment " + s);
		}
		assertTrue(tested > 0);
	}

	private static CoarseTerrain terrain(String spec) {
		ProbeSpecs.Probes p = ProbeSpecs.parse(spec);
		return new CoarseTerrain(CoarseProbe.of(p.terrain(), p.biome(), 32));
	}
}
