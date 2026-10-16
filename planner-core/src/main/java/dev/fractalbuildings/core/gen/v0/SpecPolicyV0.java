package dev.fractalbuildings.core.gen.v0;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.Styles;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;

/** Request to spec: the only place that sizes a building against its lot. */
public final class SpecPolicyV0 {
	private SpecPolicyV0() {}

	public static final int MIN_LOT = 7;

	public static BuildingSpec spec(GenRequest req) {
		Styles.require(req.style());
		Lot lot = req.lot();
		if (lot.w() < MIN_LOT || lot.d() < MIN_LOT) {
			throw new IllegalArgumentException("lot " + lot.w() + "x" + lot.d() + " is smaller than " + MIN_LOT + "x" + MIN_LOT);
		}
		boolean alongX = lot.front() == Facing.NORTH || lot.front() == Facing.SOUTH;
		int lw = alongX ? lot.w() : lot.d();
		int ld = alongX ? lot.d() : lot.w();
		long specSeed = Hash64.hash(req.worldSeed(), "building", lot.x(), lot.z());
		Draws r = new Draws(Hash64.hash(specSeed, "spec"));
		int width = r.range(5, Math.min(lw - 2, 15));
		int depth = r.range(5, Math.min(ld - 2, 11));
		int storeys = r.range(1, 3);
		if (width < 7) storeys = 1;
		return new BuildingSpec(GeneratorV0.GEN_VERSION, specSeed, req.style(), "house", width, depth, storeys, Roof.GABLE);
	}
}
