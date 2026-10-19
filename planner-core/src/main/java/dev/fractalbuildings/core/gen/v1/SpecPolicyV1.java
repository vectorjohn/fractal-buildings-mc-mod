package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;

/**
 * Request to spec for generator v1: the only place that reads the site before the shell. The
 * envelope is the lot minus the type's margins (in the lot's local frame: width along the front);
 * {@code maxHeight} keeps the building under the world ceiling wherever the instance pass sets it
 * down on this lot (the ground slab never sits above the lot's highest column); the style is the
 * request's, or the biome's at the lot centre for {@code auto}.
 */
public final class SpecPolicyV1 {
	private SpecPolicyV1() {}

	/** Smallest {@code maxHeight} the policy accepts: one 4-high storey, its top slab and a roof layer. */
	public static final int MIN_HEIGHT = 6;

	public static BuildingSpec spec(GenRequest req) {
		BuildingType t = Types.get(req.type());
		TypeLimits lim = t.limits();
		Lot lot = req.lot();
		boolean alongX = lot.front() == Facing.NORTH || lot.front() == Facing.SOUTH;
		int lw = alongX ? lot.w() : lot.d();
		int ld = alongX ? lot.d() : lot.w();
		int w = lw - 2 * lim.side(), d = ld - lim.front() - lim.back();
		if (w < lim.minWidth() || d < lim.minDepth()) {
			throw new IllegalArgumentException("lot " + lot.w() + "x" + lot.d() + " is too small for a " + t.id() + " (needs "
					+ lim.minLotWidth() + " along the front and " + lim.minLotDepth() + " deep)");
		}
		StyleRules style = StyleLibrary.AUTO.equals(req.style())
				? StyleLibrary.forBiome(req.biome().biome(lot.x() + lot.w() / 2, lot.z() + lot.d() / 2))
				: StyleLibrary.get(req.style());
		int maxFloor = Integer.MIN_VALUE, minFloor = Integer.MAX_VALUE;
		for (int x = lot.x(); x < lot.x() + lot.w(); x++)
			for (int z = lot.z(); z < lot.z() + lot.d(); z++) {
				int fy = req.terrain().floorY(x, z);
				maxFloor = Math.max(maxFloor, fy);
				minFloor = Math.min(minFloor, fy);
			}
		// types that need gentle ground (maxGrade > 3: the city block) reject a lot steeper than 1 in maxGrade; others adapt with foundations and steps
		if (lim.maxGrade() > 3 && (long) (maxFloor - minFloor) * lim.maxGrade() > lot.w() + lot.d() + lim.maxGrade()) {
			throw new IllegalArgumentException("lot ground relief " + (maxFloor - minFloor) + " over " + lot.w() + "x" + lot.d() + " is steeper than 1 in " + lim.maxGrade() + ", too steep for a " + t.id());
		}
		int maxHeight = Invariants.WORLD_MAX_Y + 1 - maxFloor;
		if (maxHeight < MIN_HEIGHT) throw new IllegalArgumentException("lot ground at y " + maxFloor + " leaves no room under the world ceiling");
		long specSeed = Hash64.hash(req.worldSeed(), "building", lot.x(), lot.z());
		Draws r = new Draws(Hash64.hash(specSeed, "spec-v1"));
		int storeys = r.range(lim.minStoreys(), lim.maxStoreys());
		int streets = lim.allStreets() ? 15 : BuildingSpec.STREET_FRONT;
		return new BuildingSpec(GeneratorV1.GEN_VERSION, specSeed, style.id(), t.id(), w, d, storeys, "auto", maxHeight, streets);
	}
}
