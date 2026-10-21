package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.rand.Hash64;

/**
 * Pointwise quarter kind (plan "Approach"): waterfront within 48 blocks of water for towns and up, downtown where
 * the density is at least 80 in a metropolis, outskirts under density 25, otherwise the kind drawn for the
 * nearest seed of a world-wide hashed Voronoi on a 320-block grid. A function of (seed, site, column, density).
 */
public final class QuarterField {
	public static final int GRID = 320;
	public static final int WATERFRONT_DIST = 48;
	public static final int DOWNTOWN_DENSITY = 80, OUTSKIRTS_DENSITY = 25;
	/** Market quarters only within this distance of the hub. */
	public static final int MARKET_RANGE = 200;

	private final long seed;
	private final SettlementSite site;
	private final CoarseTerrain terrain;

	public QuarterField(long seed, SettlementSite site, CoarseTerrain terrain) {
		this.seed = seed;
		this.site = site;
		this.terrain = terrain;
	}

	public QuarterKind at(int x, int z, int density) {
		Tier tier = site.tier();
		if (tier == Tier.METROPOLIS && density >= DOWNTOWN_DENSITY) return QuarterKind.DOWNTOWN;
		if (tier.atLeast(Tier.TOWN) && terrain.waterWithin(x, z, WATERFRONT_DIST)) return QuarterKind.WATERFRONT;
		if (density < OUTSKIRTS_DENSITY) return QuarterKind.OUTSKIRTS;
		return seeded(x, z);
	}

	private int seedX(int qx, int qz) { return qx * GRID + GRID / 4 + Hash64.below(Hash64.hash(seed, "quarter-site", qx, qz, 0), GRID / 2); }

	private int seedZ(int qx, int qz) { return qz * GRID + GRID / 4 + Hash64.below(Hash64.hash(seed, "quarter-site", qx, qz, 1), GRID / 2); }

	private QuarterKind seeded(int x, int z) {
		int gx = Math.floorDiv(x, GRID), gz = Math.floorDiv(z, GRID);
		long best = Long.MAX_VALUE;
		int bx = gx, bz = gz;
		for (int j = -1; j <= 1; j++) {
			for (int i = -1; i <= 1; i++) {
				long dx = seedX(gx + i, gz + j) - x, dz = seedZ(gx + i, gz + j) - z, d = dx * dx + dz * dz;
				if (d < best) {
					best = d;
					bx = gx + i;
					bz = gz + j;
				}
			}
		}
		return draw(bx, bz);
	}

	private QuarterKind draw(int qx, int qz) {
		Tier tier = site.tier();
		long cx = seedX(qx, qz) - site.centre().x(), cz = seedZ(qx, qz) - site.centre().z();
		long d2 = cx * cx + cz * cz;
		boolean near = d2 <= (long) MARKET_RANGE * MARKET_RANGE;
		int residential = tier.atLeast(Tier.VILLAGE) ? 60 : 100, workshop = tier.atLeast(Tier.VILLAGE) ? 15 : 0;
		int market = tier.atLeast(Tier.TOWN) && near ? 15 : 0, park = tier.atLeast(Tier.TOWN) ? 5 : 0;
		int roll = Hash64.below(Hash64.hash(seed, "quarter-kind", qx, qz), residential + workshop + market + park);
		QuarterKind k;
		if ((roll -= residential) < 0) k = QuarterKind.RESIDENTIAL;
		else if ((roll -= workshop) < 0) k = QuarterKind.WORKSHOP;
		else if ((roll -= market) < 0) k = QuarterKind.MARKET;
		else k = QuarterKind.PARK;
		long oldTown = (long) (0.3 * site.radius());
		if (k == QuarterKind.RESIDENTIAL && tier.atLeast(Tier.CITY) && d2 <= oldTown * oldTown) return QuarterKind.OLD_TOWN;
		return k;
	}
}
