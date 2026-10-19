package dev.fractalbuildings.core.gen.v1.types.highrise;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.metrics.Diversity;
import dev.fractalbuildings.core.metrics.DiversityReport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Diversity of high-rises over 256 seeds on a flat 36x36 lot (fractal-cli diversity --type highrise
 * --lot 36x36). Measured: mean distance 0.5949, 41 signatures (the 41 storey counts 20..60), no voxel repeats; floors are about 80 %.
 */
class HighriseDiversityTest {
	static DiversityReport run(String style, LongUnaryOperator seedMap) {
		GeneratorV1 g = new GeneratorV1();
		List<PlacedBuilding> l = new ArrayList<>();
		for (long s = 0; s < 256; s++) {
			var rq = new GenRequest(seedMap.applyAsLong(s), new Lot("lot0", 0, 0, 36, 36, Facing.NORTH), "highrise", style, (x, z) -> 64, (x, z) -> "plains");
			l.add(g.generate(rq).buildings().get(0));
		}
		return Diversity.of(l);
	}

	@Test
	void floors() {
		for (String style : List.of("plain", "medieval")) assertFloors(run(style, s -> s));
	}

	/** Mutant: the seed is ignored. It must fail the floors, or the floors are too weak. */
	@Test
	void seedIgnoredMutantFailsFloors() {
		DiversityReport r = run("plain", s -> 0);
		assertThrows(AssertionError.class, () -> assertFloors(r));
	}

	static void assertFloors(DiversityReport r) {
		System.out.println("highrise diversity: distance " + r.meanJaccardDistance() + " nearDup " + r.nearDuplicateRate() + " sigs " + r.distinctSignatures() + " voxelDups " + r.duplicatesByVoxelHash());
		assertTrue(r.meanJaccardDistance() >= FLOOR_DIST, "mean distance " + r.meanJaccardDistance());
		assertTrue(r.nearDuplicateRate() <= CEIL_NEARDUP, "near-dup " + r.nearDuplicateRate());
		assertTrue(r.distinctSignatures() >= FLOOR_SIGS, "signatures " + r.distinctSignatures());
		assertTrue(r.duplicatesByVoxelHash() <= CEIL_DUPS, "voxel dups " + r.duplicatesByVoxelHash());
	}

	static final double FLOOR_DIST = 0.47, CEIL_NEARDUP = 0.01;
	static final int FLOOR_SIGS = 32, CEIL_DUPS = 10;
}
