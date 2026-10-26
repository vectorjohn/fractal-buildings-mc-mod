package dev.fractalbuildings.mod.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.settlement.LotBrief;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CITYQ r5: SpillClear leaves alone exactly the cells a building may own (the {@code in-lot} invariant), not a margin round every lot. The
 * first rule (air rights + 1 on every side) covered the street air beside lots, so forest rock there stayed (B3 headroom, seed 12345 x 655).
 */
class SpillClearTest {
	/** A lot x 10..19, z 0..9, front EAST onto a street at x 20+, air rights 3, street surface (frontY) 64. */
	static final LotBrief LOT = new LotBrief("l", 10, 0, 10, 10, Facing.EAST, "house", StyleLibrary.AUTO, 2, 2, 1, 3, 64, 50, LotBrief.BUILD);

	@Test
	void theLotAndItsHighAirStripAreTheBuildingsAndNothingElse() {
		List<LotBrief> lots = List.of(LOT);
		assertTrue(SpillClear.lotMayOwn(lots, 10, 65, 0), "inside the lot");
		assertTrue(SpillClear.lotMayOwn(lots, 19, 80, 9), "inside the lot, high");
		assertTrue(SpillClear.lotMayOwn(lots, 22, 67, 5), "air-rights strip at frontY + 3");
		assertFalse(SpillClear.lotMayOwn(lots, 22, 66, 5), "air-rights strip below the clearance: street air");
		assertFalse(SpillClear.lotMayOwn(lots, 23, 70, 5), "past the strip");
		assertFalse(SpillClear.lotMayOwn(lots, 9, 65, 5), "beside the back of the lot (the old rule's +1 margin)");
		assertFalse(SpillClear.lotMayOwn(lots, 15, 65, 10), "beside the side of the lot");
		LotBrief noAir = new LotBrief("n", 10, 0, 10, 10, Facing.EAST, "house", StyleLibrary.AUTO, 1, 1, 1, 0, Brief.NO_FRONT_Y, 50, LotBrief.BUILD);
		assertFalse(SpillClear.lotMayOwn(List.of(noAir), 20, 90, 5), "no air rights: nothing outside the lot");
	}
}
