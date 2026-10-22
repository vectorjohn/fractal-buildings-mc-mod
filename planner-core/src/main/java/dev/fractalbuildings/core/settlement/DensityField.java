package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.Tiers;

/**
 * Pointwise settlement shape and density (plan "Type and height by density"). Every answer is a function of
 * (seed, site, snapped lattice column) alone, read through the coarse probe, so any district or cell asks the
 * same question and gets the same answer.
 *
 * <p>The footprint is a disk of the site radius whose edge is deformed by 8 angular harmonics (up to 15 %).
 * {@link #urban} is the footprint test plus dry, not a peak, not steep. {@link #base} falls from the tier cap at
 * the centre to 0 at the edge ({@code cap (1 - t^2)}) with low-frequency hashed noise; {@link #at} adds the
 * arterial bonus and applies the cap.
 */
public final class DensityField {
	public static final int HARMONICS = 8;
	public static final double WOBBLE = 0.15;
	/** Noise lattice spacing and amplitude. */
	public static final double SHOULDER = 0.80;
	public static final double CITY_SHOULDER = 0.90;
	public static final int NOISE_CELL = 160, NOISE_AMP = 8;

	private final long seed;
	private final SettlementSite site;
	private final CoarseTerrain terrain;
	private final Arterials arterials;
	private final OldTown oldTown;
	private final double[] amp = new double[HARMONICS], phase = new double[HARMONICS];
	private final double ampSum;
	private final double core;

	public DensityField(long seed, SettlementSite site, CoarseTerrain terrain, DistrictGrid grid) {
		this.seed = seed;
		this.site = site;
		this.terrain = terrain;
		double sum = 0;
		for (int k = 0; k < HARMONICS; k++) {
			amp[k] = (0.5 + Tiers.unit(Hash64.hash(seed, "site-shape", site.centre().x(), site.centre().z(), k, 0))) / (k + 1);
			phase[k] = Tiers.unit(Hash64.hash(seed, "site-shape", site.centre().x(), site.centre().z(), k, 1)) * 2 * Math.PI;
			sum += amp[k];
		}
		this.ampSum = sum;
		double cu = Tiers.unit(Hash64.hash(seed, "core-share", site.centre().x(), site.centre().z()));
		this.core = 0.18 + 0.30 * cu * cu;
		this.oldTown = new OldTown(seed, site, terrain, grid, this);
		this.arterials = new Arterials(seed, site.tier(), this, terrain, grid);
	}

	public SettlementSite site() { return site; }

	public CoarseTerrain terrain() { return terrain; }

	public Arterials arterials() { return arterials; }

	/** The walled old town of a city (no ring for other tiers). */
	public OldTown oldTown() { return oldTown; }

	/** Disk radius at an angle (radians), wobbled. */
	public double radiusAt(double theta) {
		double n = 0;
		for (int k = 0; k < HARMONICS; k++) n += amp[k] * StrictMath.cos((k + 1) * theta + phase[k]);
		return site.radius() * (1 + WOBBLE * n / ampSum);
	}

	/** Largest radius the footprint can reach. */
	public int maxRadius() { return (int) Math.ceil(site.radius() * (1 + WOBBLE)); }

	private double dist(int x, int z) {
		double dx = x - site.centre().x(), dz = z - site.centre().z();
		return StrictMath.sqrt(dx * dx + dz * dz);
	}

	/** Inside the deformed disk (the column is snapped to the lattice first). */
	public boolean inside(int x, int z) {
		int sx = terrain.snap(x), sz = terrain.snap(z);
		double dx = sx - site.centre().x(), dz = sz - site.centre().z();
		if (dx == 0 && dz == 0) return true;
		return dist(sx, sz) < radiusAt(StrictMath.atan2(dz, dx));
	}

	/** Inside the footprint and buildable: the skeleton's pre-reachability urban test. */
	public boolean urban(int x, int z) { return inside(x, z) && terrain.buildable(x, z); }

	/** Core size of a city or metropolis, as a fraction of the footprint radius: most cities a compact core (0.18 to 0.28), some packed centres (up to 0.48). */
	public double coreFraction() { return core; }

	private int noise(int x, int z) {
		int i = Math.floorDiv(x, NOISE_CELL), j = Math.floorDiv(z, NOISE_CELL), fx = Math.floorMod(x, NOISE_CELL), fz = Math.floorMod(z, NOISE_CELL);
		int v00 = lat(i, j), v10 = lat(i + 1, j), v01 = lat(i, j + 1), v11 = lat(i + 1, j + 1);
		int top = v00 * (NOISE_CELL - fx) + v10 * fx, bottom = v01 * (NOISE_CELL - fx) + v11 * fx;
		return (top * (NOISE_CELL - fz) + bottom * fz) / (NOISE_CELL * NOISE_CELL);
	}

	private int lat(int i, int j) { return Hash64.below(Hash64.hash(seed, "density-noise", i, j), 2 * NOISE_AMP + 1) - NOISE_AMP; }

	/**
	 * The share of the cap at a radius fraction t for a city or metropolis: a small tall core (1 at the centre, {@value #SHOULDER} at the core edge, so
	 * the 85-and-up high-rise density only reaches 0.8 of the core), then a long fall from the shoulder through block and mid-rise density to the
	 * low-rise rim.
	 */
	double cityProfile(double t) {
		double sh = site.tier() == Tier.CITY ? CITY_SHOULDER : SHOULDER; // a city (cap 85) keeps more of its fabric: squares and arterials need density 50 and up
		if (t < core) return 1 - (1 - sh) * StrictMath.pow(t / core, 1.5);
		double s = (t - core) / (1 - core);
		return sh * (1 - StrictMath.pow(Math.min(1, s), 1.6));
	}

	/** Density before the arterial bonus, 0..cap; 0 outside the footprint. */
	public int base(int x, int z) { return base(x, z, false); }

	/**
	 * The street fabric's density (ROADS3): {@link #base} on the plain {@code cap (1 - t^2)} profile every tier had before the skyline
	 * profile, keyed to the share of the radius rather than to building height. Arterials and squares read it, so a metropolis's
	 * smaller tall core (SKYLINE) does not also thin its main streets and squares; heights and types still read {@link #at}.
	 * For towns and smaller it equals {@link #base}.
	 */
	public int fabric(int x, int z) { return base(x, z, true); }

	/** {@link #fabric} plus the arterial bonus, at most the tier cap (what {@link #at} is to {@link #base}). */
	public int fabricAt(int x, int z) {
		if (!inside(x, z)) return 0;
		return Math.min(site.tier().densityCap, fabric(x, z) + (arterials.near(terrain.snap(x), terrain.snap(z)) ? Arterials.BONUS : 0));
	}

	private int base(int x, int z, boolean fabric) {
		int sx = terrain.snap(x), sz = terrain.snap(z);
		if (!inside(sx, sz)) return 0;
		double dx = sx - site.centre().x(), dz = sz - site.centre().z();
		double t = (dx == 0 && dz == 0) ? 0 : dist(sx, sz) / radiusAt(StrictMath.atan2(dz, dx));
		int cap = site.tier().densityCap;
		double f = site.tier().atLeast(Tier.CITY) && !fabric ? cityProfile(t) : 1 - t * t;
		int raw = (int) StrictMath.floor(cap * f + 0.5) + noise(sx, sz);
		return Math.max(0, Math.min(cap, raw));
	}

	/** Density 0..100, at most the tier cap: {@link #base} plus the arterial bonus. 0 outside the footprint. */
	public int at(int x, int z) {
		if (!inside(x, z)) return 0;
		int b = base(x, z);
		int cap = site.tier().densityCap;
		return Math.min(cap, b + (arterials.near(terrain.snap(x), terrain.snap(z)) ? Arterials.BONUS : 0));
	}
}
