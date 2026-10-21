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
 */
public record Brief(int minStoreys, int maxStoreys, int streets, int airRights, int frontY) {
	public static final int NO_FRONT_Y = Integer.MIN_VALUE;

	public Brief {
		if (minStoreys < 1 || maxStoreys < minStoreys) throw new IllegalArgumentException("brief storeys " + minStoreys + ".." + maxStoreys);
		if (streets < 0 || streets > 15) throw new IllegalArgumentException("brief streets " + streets);
		if (airRights < 0) throw new IllegalArgumentException("brief airRights " + airRights);
	}
}
