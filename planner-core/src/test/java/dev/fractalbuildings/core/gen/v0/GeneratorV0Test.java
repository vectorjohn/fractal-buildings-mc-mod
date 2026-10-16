package dev.fractalbuildings.core.gen.v0;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.*;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;
import org.junit.jupiter.api.Test;

class GeneratorV0Test {
	static final TerrainProbe FLAT = (x, z) -> 64;
	static final TerrainProbe SLOPE = (x, z) -> 50 + Math.floorMod(x * 3 + z * 2, 9);

	static Lot lot(long seed) {
		Draws r = new Draws(Hash64.hash(seed, "lot"));
		return new Lot("lot0", r.range(-500, 500), r.range(-500, 500), r.range(7, 31), r.range(7, 31), Facing.values()[r.below(4)]);
	}

	@Test
	void twoHundredSeedsGenerateOnFlatAndSlope() {
		GeneratorV0 g = new GeneratorV0();
		for (long seed = 0; seed < 200; seed++) {
			for (TerrainProbe t : new TerrainProbe[] {FLAT, SLOPE}) {
				GenRequest rq = new GenRequest(seed, lot(seed), seed % 2 == 0 ? "plain" : "medieval", t, (x, z) -> "plains");
				SceneDoc s = g.generate(rq);
				assertEquals(GeneratorV0.GEN_VERSION, s.genVersion());
				assertEquals(1, s.buildings().size());
				assertEquals(1, s.lots().size());
				assertTrue(s.violations().isEmpty());
				TerrainPatch tp = s.terrain();
				assertEquals(rq.lot().w() + 4, tp.nx());
				assertTrue(tp.covers(rq.lot().x() - 2, rq.lot().z() - 2) && tp.covers(rq.lot().x() + rq.lot().w() + 1, rq.lot().z() + rq.lot().d() + 1));
				assertEquals(rq.style(), s.buildings().get(0).shell().spec().style());
			}
		}
	}

	@Test
	void sameRequestTwiceIsEqual() {
		for (long seed = 0; seed < 50; seed++) {
			GenRequest rq = new GenRequest(seed, lot(seed), "plain", SLOPE, (x, z) -> "plains");
			assertEquals(new GeneratorV0().generate(rq), new GeneratorV0().generate(rq));
		}
	}

	@Test
	void differentSeedsGiveDifferentBuildings() {
		java.util.Set<BuildingSpec> specs = new java.util.HashSet<>();
		for (long seed = 0; seed < 100; seed++) {
			GenRequest rq = new GenRequest(seed, new Lot("lot0", 0, 0, 20, 20, Facing.NORTH), "plain", FLAT, (x, z) -> "plains");
			specs.add(new GeneratorV0().generate(rq).buildings().get(0).shell().spec());
		}
		assertTrue(specs.size() > 40, "variety " + specs.size());
	}

	@Test
	void specsRespectLotLimitsAndSingleStoreyWhenNarrow() {
		for (long seed = 0; seed < 2000; seed++) {
			GenRequest rq = new GenRequest(seed, lot(seed), "plain", FLAT, (x, z) -> "plains");
			BuildingSpec sp = SpecPolicyV0.spec(rq);
			boolean alongX = rq.lot().front() == Facing.NORTH || rq.lot().front() == Facing.SOUTH;
			assertTrue(sp.width() >= 5 && sp.width() <= Math.min(15, (alongX ? rq.lot().w() : rq.lot().d()) - 2));
			assertTrue(sp.depth() >= 5 && sp.depth() <= Math.min(11, (alongX ? rq.lot().d() : rq.lot().w()) - 2));
			assertTrue(sp.storeys() >= 1 && sp.storeys() <= 3);
			if (sp.width() < 7) assertEquals(1, sp.storeys());
		}
	}

	@Test
	void badRequestsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new GeneratorV0().generate(
				new GenRequest(1, new Lot("l", 0, 0, 6, 9, Facing.NORTH), "plain", FLAT, (x, z) -> "plains")));
		assertThrows(IllegalArgumentException.class, () -> new GeneratorV0().generate(
				new GenRequest(1, new Lot("l", 0, 0, 9, 9, Facing.NORTH), "gothic", FLAT, (x, z) -> "plains")));
	}
}
