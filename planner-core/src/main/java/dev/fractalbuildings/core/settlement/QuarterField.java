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
	private final DistrictGrid grid;
	private final DensityField density;

	public QuarterField(long seed, SettlementSite site, CoarseTerrain terrain) { this(seed, site, terrain, null); }

	/**
	 * With the settlement's density field: the area inside a city's old-town walls is the old town whatever the hashed quarters say, and a
	 * metropolis's park tiles are the downtown tiles (centre density at least {@value #DOWNTOWN_DENSITY}) that a hash picks.
	 */
	public QuarterField(long seed, SettlementSite site, CoarseTerrain terrain, DensityField density) {
		this.seed = seed;
		this.site = site;
		this.terrain = terrain;
		this.grid = site.tier() == Tier.METROPOLIS ? new DistrictGrid(seed) : null;
		this.density = density;
	}

	/** Share of downtown tiles that are parks: one in {@value #PARK_ONE_IN}. */
	public static final int PARK_ONE_IN = 8;

	/** Is the tile round the column a downtown park: one tile in {@value #PARK_ONE_IN} of those whose centre is downtown (the whole tile is then open ground)? */
	boolean parkTile(int x, int z) {
		if (density == null) return false;
		DistrictGrid.TileId id = grid.tileOf(x, z);
		return parks.get(((long) id.ix() << 32) ^ (id.iz() & 0xffffffffL), k -> {
			DistrictGrid.Tile t = grid.tile(id.ix(), id.iz());
			if (t.contains(terrain.snap(site.centre().x()), terrain.snap(site.centre().z()))) return false; // the hub's tile has the hub square and its hall and temple
			if (Hash64.below(Hash64.hash(seed, "downtown-park", t.ix(), t.iz()), PARK_ONE_IN) != 0) return false;
			return density.at((t.minX() + t.maxX()) / 2, (t.minZ() + t.maxZ()) / 2) >= DOWNTOWN_DENSITY;
		});
	}

	private final dev.fractalbuildings.core.cache.BoundedCache<Long, Boolean> parks = new dev.fractalbuildings.core.cache.BoundedCache<>(1024); // pure memo

	public QuarterKind at(int x, int z, int density) {
		Tier tier = site.tier();
		if (tier == Tier.METROPOLIS && parkTile(x, z)) return QuarterKind.PARK;
		if (tier == Tier.METROPOLIS && density >= DOWNTOWN_DENSITY) return QuarterKind.DOWNTOWN;
		if (tier.atLeast(Tier.TOWN) && terrain.waterWithin(x, z, WATERFRONT_DIST)) return QuarterKind.WATERFRONT;
		if (this.density != null && this.density.oldTown().contains(x, z)) return QuarterKind.OLD_TOWN;
		if (density < OUTSKIRTS_DENSITY) return QuarterKind.OUTSKIRTS;
		QuarterKind k = seeded(x, z);
		// IRR1: a residential quarter within 0.3 radius of the hub is the old town column by column, not only when its seed point is that close (a city gets one near its hub far more often)
		if (k == QuarterKind.RESIDENTIAL && tier.atLeast(Tier.CITY)) {
			long cx = x - site.centre().x(), cz = z - site.centre().z(), r = (long) (0.3 * site.radius());
			if (cx * cx + cz * cz <= r * r) return QuarterKind.OLD_TOWN;
		}
		return k;
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
