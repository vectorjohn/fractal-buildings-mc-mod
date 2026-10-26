package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.gen.v1.style.StyleVariants;
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

	/** False if the ground relief over a w x d lot is too steep for a type that needs gentle ground (maxGrade > 3). */
	public static boolean reliefOk(TypeLimits lim, int relief, int w, int d) {
		return lim.maxGrade() <= 3 || (long) relief * lim.maxGrade() <= w + d + lim.maxGrade();
	}

	/** Ground relief over a lot from which a flight may need a rail on the lot line (a flight is railed from a drop of 2: {@code InstancePassV1.RAIL_DROP_FLIGHT}). */
	public static final int RAIL_ROOM_RELIEF = 2;

	/** Extra lot width (both sides of a setback) and depth a lot on sloping ground needs over the type minimum, so the building can step in from the lot line for its railed flight (MINLOT, DS-8). */
	public static final int RAIL_ROOM_WIDTH = 2, RAIL_ROOM_DEPTH = 1;

	/** Town-wall types stand on the ring's walk and keep their margins; they never step back. */
	private static final java.util.Set<String> NO_RAIL_ROOM = java.util.Set.of("wall", "curtain", "bastion");

	/**
	 * DS-8 (the lot planner applies it; the generator itself builds on any lot that fits): on ground with relief {@value #RAIL_ROOM_RELIEF} or more the lot must be {@value #RAIL_ROOM_WIDTH} wider and
	 * {@value #RAIL_ROOM_DEPTH} deeper than the type minimum, so a flight that needs a rail can stand inside the lot.
	 *
	 * @param lw lot width along the front
	 * @param ld lot depth from the front
	 */
	public static boolean railRoomOk(String type, TypeLimits lim, int relief, int lw, int ld) {
		if (relief < RAIL_ROOM_RELIEF || lim.maxGrade() > 3 || NO_RAIL_ROOM.contains(type)) return true;
		return lw >= lim.minLotWidth() + RAIL_ROOM_WIDTH && ld >= lim.minLotDepth() + RAIL_ROOM_DEPTH;
	}

	/** Types whose massing programs use air rights over the street (houses and shops). */
	public static boolean airType(String type) { return type.equals("house") || type.equals("shop"); }

	public static BuildingSpec spec(GenRequest req) { return spec(req, Types.get(req.type()).limits()); }

	/** With explicit margins (a setback tried by {@link GeneratorV1}). */
	public static BuildingSpec spec(GenRequest req, TypeLimits lim) {
		BuildingType t = Types.get(req.type());
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
		if (!reliefOk(lim, maxFloor - minFloor, lot.w(), lot.d())) {
			throw new IllegalArgumentException("lot ground relief " + (maxFloor - minFloor) + " over " + lot.w() + "x" + lot.d() + " is steeper than 1 in " + lim.maxGrade() + ", too steep for a " + t.id());
		}
		if (req.brief() != null && req.brief().walk() != null) maxFloor = Math.max(maxFloor, req.brief().walk().base()); // a wall lot stands at its ring level, at or above its highest ground
		int maxHeight = Invariants.WORLD_MAX_Y + 1 - maxFloor;
		if (maxHeight < MIN_HEIGHT) throw new IllegalArgumentException("lot ground at y " + maxFloor + " leaves no room under the world ceiling");
		long specSeed = Hash64.hash(req.worldSeed(), "building", lot.x(), lot.z());
		Draws r = new Draws(Hash64.hash(specSeed, "spec-v1"));
		int lo = lim.minStoreys(), hi = lim.maxStoreys();
		int streets = lim.allStreets() ? 15 : BuildingSpec.STREET_FRONT;
		Brief brief = req.brief();
		if (brief != null) {
			// the brief's storey range, intersected with the type's; if disjoint, the type's bound nearest the brief
			if (brief.maxStoreys() < lo) hi = lo;
			else if (brief.minStoreys() > hi) lo = hi;
			else {
				lo = Math.max(lo, brief.minStoreys());
				hi = Math.min(hi, brief.maxStoreys());
			}
			if (!lim.allStreets()) streets = brief.streets() | BuildingSpec.STREET_FRONT;
		}
		int storeys = r.range(lo, hi);
		style = StyleLibrary.get(StyleVariants.select(style.id(), t.id(), storeys, req.biome().biome(lot.x() + lot.w() / 2, lot.z() + lot.d() / 2), null, specSeed)); // tier: null until the high-rise tiers (docs/plans/highrises.md A5)
		int lead = 0;
		if (brief != null && airType(t.id()) && storeys >= 2 && brief.airRights() > 0 && brief.frontY() != Brief.NO_FRONT_Y
				&& style.massing().jetty() > 0 && minFloor >= brief.frontY()) {
			// air rights: the envelope grows by the front margin plus the air in front, so upper storeys can jetty over the street (docs/plans/phase5.md "JT")
			lead = lim.front() + Math.min(brief.airRights(), BuildingSpec.MAX_LEAD - lim.front());
		}
		return new BuildingSpec(GeneratorV1.GEN_VERSION, specSeed, style.id(), t.id(), w, d + lead, storeys, "auto", maxHeight, streets, lead);
	}
}
