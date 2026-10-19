package dev.fractalbuildings.core.gen.v1;

import java.util.List;

/**
 * What the spec policy and the property tests need to know about a type.
 *
 * @param minWidth smallest envelope width (local x) the type can build in
 * @param minDepth smallest envelope depth (local z)
 * @param minStoreys fewest storeys the policy asks for
 * @param maxStoreys most storeys the policy asks for (capped further by the world ceiling)
 * @param side envelope margin from the lot's two side edges
 * @param front envelope margin from the lot's front edge (room for front steps)
 * @param back envelope margin from the lot's back edge
 * @param allStreets every side of the lot faces a street (city block)
 * @param testLots lot sizes {w, d} the property tests and the CLI matrix use for this type
 * @param propertySeeds cases per lot size in the property test (fewer for big types)
 * @param maxGrade steepest ground the property matrix gives this type, as the run of a 1-in-run slope
 *     (1 = 45 degrees). Large types on steeper ground need per-wing ground levels (ADR 0003 W5, Phase 6.3).
 */
public record TypeLimits(int minWidth, int minDepth, int minStoreys, int maxStoreys, int side, int front, int back,
		boolean allStreets, List<int[]> testLots, int propertySeeds, int maxGrade) {
	public TypeLimits {
		testLots = List.copyOf(testLots);
		if (minStoreys < 1 || maxStoreys < minStoreys) throw new IllegalArgumentException("storeys " + minStoreys + ".." + maxStoreys);
		if (side < 0 || front < 0 || back < 0) throw new IllegalArgumentException("margins");
		if (maxGrade < 1) throw new IllegalArgumentException("maxGrade " + maxGrade);
	}

	/**
	 * A test lot {@code {w, d}} given in the front frame (width along the front), as the lot for a given
	 * front: east/west fronts see the lot turned round if it does not fit as given (narrow deep lots).
	 */
	public int[] oriented(int[] lot, dev.fractalbuildings.core.ir.Facing front) {
		boolean alongX = front == dev.fractalbuildings.core.ir.Facing.NORTH || front == dev.fractalbuildings.core.ir.Facing.SOUTH;
		int lw = alongX ? lot[0] : lot[1], ld = alongX ? lot[1] : lot[0];
		return lw < minLotWidth() || ld < minLotDepth() ? new int[] {lot[1], lot[0]} : lot;
	}

	public int minLotWidth() { return minWidth + 2 * side; }

	public int minLotDepth() { return minDepth + front + back; }
}
