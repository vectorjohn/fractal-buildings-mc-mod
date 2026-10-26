package dev.fractalbuildings.core.settlement;

import java.util.TreeMap;

/**
 * How far a settlement's built-up area goes on outward from a point (DS-18, "cities taper", CITYQ r4): tall classes stay away from
 * wherever the built-up area stops, not only from the drawn radius. Terrain, water or the cell edge can end a quarter halfway out,
 * and before this the class came from the radial density alone. On the 12345 coast city the newly reachable west quarter ended in
 * ten 15 to 17 storey high-rises with nothing built beyond them.
 *
 * <p>"Built-up" is the skeleton's coarse mask: lattice columns that are {@link CoarseRaster#URBAN} (reachable urban ground) or
 * {@link CoarseRaster#OPEN} (parks inside it), read from the skeleton of whichever settlement cell owns the column, in a tile the
 * street network reaches ({@link #reachedTile}: the mask alone reached 150 blocks past the west quarter, where no street goes). That mask is
 * settlement-wide and the same for every tile, so the answer depends on (seed, site, probes, point) alone and tiles stay
 * independent of their order.
 *
 * <p>{@link #depth} samples three rays away from the site centre (straight out and 30 degrees either side) every {@link #SAMPLE}
 * blocks up to {@link #REACH}, and returns the farthest built-up sample on any of them that the run from the lot reaches without a gap over {@link #GAP}
 * blocks (a first version took any farther sample, and towers stood before 120 unbuilt blocks). {@link #cap} turns that depth into a
 * density cap: no high-rise class (density 70 and up) within {@link #LOW} blocks of the edge, and only the mid-rise (below the
 * tower band 85) within {@link #MID}. Chosen from a sweep (edge towers / high-rises on the 9 gate windows): no cap 38 / 823, 64 and 128
 * 19 / 758, 96 and 128 15 / 722, 128 and 160 12 / 662 (EdgeTaperTest; ASSUMPTIONS).
 */
public final class BuiltEdge {
	public static final int SAMPLE = 16, REACH = 144;
	/** The longest unbuilt stretch (a street, a small park) a built-up run outward may cross; farther built-up ground does not count. */
	public static final int GAP = 32;
	/** Steeper coarse ground than this per sample (16 blocks) does not count: the mask calls it buildable, but at exact relief its lots come out empty (the towers left on the 9 gate windows stood before steep land). */
	public static final int RISE = 8;
	/** Closer to the edge than this: density at most {@link #LOW_CAP} (blocks and shops, no high-rise). */
	public static final int LOW = 96;
	/** Closer than this: density at most {@link #MID_CAP} (mid-rise high-rises of 10 to 19 storeys, no towers). */
	public static final int MID = 128;
	public static final int LOW_CAP = 69, MID_CAP = 84;
	private static final double SPREAD = StrictMath.toRadians(30);

	private final SkeletonSource src;
	private final CoarseTerrain terrain;
	private final TreeMap<Cell, int[]> masks = new TreeMap<>(CellGeometry::compare);
	private final TreeMap<Cell, CoarseRaster> rasters = new TreeMap<>(CellGeometry::compare);
	private final TreeMap<Long, Boolean> tiles = new TreeMap<>();
	private RoadNetwork net;

	public BuiltEdge(SkeletonSource src) {
		this.src = src;
		this.terrain = src.density().terrain();
	}

	/**
	 * Lots only stand where the street network reaches: with a street lattice (city, metropolis), the tile must be planned and have a
	 * drawn border segment (the network draws only segments a chain from the hub reaches). Without a lattice every tile counts.
	 */
	boolean reachedTile(int x, int z) {
		if (net == null) net = src.network(); // once: a source need not cache it (the determinism test's evicting source rebuilds it per call)
		if (!net.hasLattice()) return true;
		DistrictGrid.TileId t = src.grid().tileOf(x, z);
		long k = RoadNetwork.key(t.ix(), t.iz());
		Boolean v = tiles.get(k);
		if (v == null) {
			boolean any = false;
			if (net.planned(t.ix(), t.iz())) for (DistrictGrid.Segment s : src.grid().segments(t.ix(), t.iz())) any |= net.kind(s) != null;
			v = any;
			tiles.put(k, v);
		}
		return v;
	}

	/** Is the lattice column nearest (x, z) built-up ground of this settlement? */
	public boolean builtUp(int x, int z) {
		Cell c = CellGeometry.owner(src.view().field(), x, z);
		if (!src.site().cells().contains(c)) return false;
		CoarseRaster r = rasters.get(c);
		int[] m = masks.get(c);
		if (r == null) {
			r = src.skeleton(c).coarse();
			m = r.mask();
			rasters.put(c, r);
			masks.put(c, m);
		}
		int i = Math.round((x - r.x0()) / (float) r.step()), j = Math.round((z - r.z0()) / (float) r.step());
		if (i < 0 || j < 0 || i >= r.nx() || j >= r.nz()) return false;
		int v = m[r.index(i, j)];
		if (v != CoarseRaster.URBAN && v != CoarseRaster.OPEN) return false;
		return reachedTile(x, z);
	}

	/** Farthest built-up sample outward from (x, z), 0 when none; {@link #REACH} at the site centre (no outward direction). */
	public int depth(int x, int z) {
		double dx = x - src.site().centre().x(), dz = z - src.site().centre().z(), len = StrictMath.sqrt(dx * dx + dz * dz);
		if (len < 1) return REACH;
		double a0 = StrictMath.atan2(dz, dx);
		int best = 0;
		for (int k = -1; k <= 1; k++) {
			double a = a0 + k * SPREAD, ux = StrictMath.cos(a), uz = StrictMath.sin(a);
			int last = 0; // the built-up run from the lot outward: a gap of more than GAP blocks ends it
			int prev = terrain.floorY(x, z);
			for (int s = SAMPLE; s <= REACH && s - last <= GAP + SAMPLE; s += SAMPLE) {
				int sx = (int) StrictMath.floor(x + ux * s), sz = (int) StrictMath.floor(z + uz * s), fy = terrain.floorY(sx, sz);
				if (Math.abs(fy - prev) <= RISE && builtUp(sx, sz)) last = s;
				prev = fy;
			}
			best = Math.max(best, last);
		}
		return best;
	}

	/** The density a lot at (x, z) picks its class with: {@code density}, capped near the built edge. Only densities that reach the high-rise band are looked at. */
	public int cap(int density, int x, int z) {
		if (density <= LOW_CAP) return density;
		int d = depth(x, z);
		if (d < LOW) return Math.min(density, LOW_CAP);
		if (d < MID) return Math.min(density, MID_CAP);
		return density;
	}
}
