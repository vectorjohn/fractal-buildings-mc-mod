package dev.fractalbuildings.core.gen;

/**
 * What a settlement plan tells a building about its lot (Phase 5; docs/plans/phase5.md "P0"). Optional
 * on a {@link GenRequest}; a request without one behaves exactly as before.
 *
 * @param minStoreys fewest storeys the district wants here
 * @param maxStoreys most storeys; intersected with the type's own range by the spec policy
 * @param streets mask of the lot's sides that face a street, in the lot's local frame (1 front,
 *     2 east of the front, 4 back, 8 west of the front, as {@code BuildingSpec.streets}); the front bit is forced
 * @param airRights how far (blocks) the building may overhang the street, 0 until the jetty step (JT)
 * @param frontY street surface y in front of the lot's front edge, or {@link #NO_FRONT_Y}
 * @param walk the lot's level in the town wall's walk profile, or null
 * @param square the lot may carry the square's market hall (7.17 S4): the one eligible lot of its square; the spec policy also needs level ground
 */
public record Brief(int minStoreys, int maxStoreys, int streets, int airRights, int frontY, Walk walk, boolean square) {
	/**
	 * The level of a town-wall lot in the ring's walk profile (D2 slope step): {@code base} is the world y of the lot's ground slab, which the instance pass uses
	 * instead of the highest ground under the footprint; {@code prev} and {@code next} are the neighbouring ring lots' levels minus {@code base} (at most {@link #MAX_STEP} blocks either way),
	 * 'next' being past the lot's local +x end and 'prev' past its -x end.
	 */
	public record Walk(int base, int prev, int next) {
		public Walk {
			if (Math.abs(prev) > MAX_STEP || Math.abs(next) > MAX_STEP) throw new IllegalArgumentException("walk steps " + prev + ", " + next);
		}
	}

	/** A brief for a lot that does not carry the square's market hall. */
	public Brief(int minStoreys, int maxStoreys, int streets, int airRights, int frontY, Walk walk) {
		this(minStoreys, maxStoreys, streets, airRights, frontY, walk, false);
	}

	/** A brief without a wall-walk level. */
	public Brief(int minStoreys, int maxStoreys, int streets, int airRights, int frontY) {
		this(minStoreys, maxStoreys, streets, airRights, frontY, null);
	}

	public static final int NO_FRONT_Y = Integer.MIN_VALUE;

	/** Most blocks two neighbouring lots of a town wall may differ in level: a ramp of stair blocks that long climbs the difference on the lower lot. */
	public static final int MAX_STEP = 10;

	public Brief {
		if (minStoreys < 1 || maxStoreys < minStoreys) throw new IllegalArgumentException("brief storeys " + minStoreys + ".." + maxStoreys);
		if (streets < 0 || streets > 15) throw new IllegalArgumentException("brief streets " + streets);
		if (airRights < 0) throw new IllegalArgumentException("brief airRights " + airRights);
	}
}
