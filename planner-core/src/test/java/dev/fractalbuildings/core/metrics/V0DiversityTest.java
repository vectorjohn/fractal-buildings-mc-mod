package dev.fractalbuildings.core.metrics;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v0.GeneratorV0;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongUnaryOperator;
import org.junit.jupiter.api.Test;

/** Diversity of generator v0 over 256 seeds on a flat 20x20 lot. Measured (plain = medieval in v0): distance 0.6884, near-dup 0.0033, 182 signatures, 64 voxel repeats; floors are ~80% of those. */
class V0DiversityTest {
	static DiversityReport run(String style, LongUnaryOperator seedMap) {
		GeneratorV0 g = new GeneratorV0();
		List<PlacedBuilding> l = new ArrayList<>();
		for (long s = 0; s < 256; s++) {
			var rq = new GenRequest(seedMap.applyAsLong(s), new Lot("lot0", 0, 0, 20, 20, Facing.NORTH), style, (x, z) -> 64, (x, z) -> "plains");
			l.add(g.generate(rq).buildings().get(0));
		}
		return Diversity.of(l);
	}

	@Test
	void plainFloors() {
		DiversityReport r = run("plain", s -> s);
		assertFloors(r);
	}

	@Test
	void medievalFloors() {
		DiversityReport r = run("medieval", s -> s);
		assertFloors(r);
	}

	/** Mutant: the seed is ignored. It must fail the floors, or the floors are too weak. */
	@Test
	void seedIgnoredMutantFailsFloors() {
		DiversityReport r = run("plain", s -> 0);
		assertThrows(AssertionError.class, () -> assertFloors(r));
	}

	static void assertFloors(DiversityReport r) {
		assertTrue(r.meanJaccardDistance() >= FLOOR_DIST, "mean distance " + r.meanJaccardDistance());
		assertTrue(r.nearDuplicateRate() <= CEIL_NEARDUP, "near-dup " + r.nearDuplicateRate());
		assertTrue(r.distinctSignatures() >= FLOOR_SIGS, "signatures " + r.distinctSignatures());
		assertTrue(r.duplicatesByVoxelHash() <= CEIL_DUPS, "voxel dups " + r.duplicatesByVoxelHash());
	}

	static final double FLOOR_DIST = 0.55, CEIL_NEARDUP = 0.01;
	static final int FLOOR_SIGS = 145, CEIL_DUPS = 80;
}
