package dev.fractalbuildings.core.settlement.tiers;

import dev.fractalbuildings.core.settlement.SkeletonContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The metropolis (D3): the city's arterials and squares, a downtown of high-rises (density 80 and up, see
 * {@link dev.fractalbuildings.core.settlement.ClassTable}) with a park in about one downtown tile in eight (the quarter rule), an outer
 * ring of arterials where the tiles inside 0.62 of the footprint radius meet those outside ({@link dev.fractalbuildings.core.settlement.Arterials}),
 * and eight avenues from the hub, the four diagonals and the four axes. No old-town wall.
 */
public final class MetropolisProgram extends CityProgram {
	@Override protected int exitCount(SkeletonContext c) { return c.holdsCentre() ? 8 : 1; }

	/** All eight compass directions. */
	@Override protected List<int[]> directions(SkeletonContext c) {
		List<int[]> out = new ArrayList<>();
		for (int k = 0; k < 8; k++) out.add(OCT[k]);
		return out;
	}

	@Override protected boolean walled(SkeletonContext c) { return false; }
}
