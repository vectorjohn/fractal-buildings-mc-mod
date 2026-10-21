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
	public static final int NOISE_CELL = 160, NOISE_AMP = 8;

	private final long seed;
	private final SettlementSite site;
	private final CoarseTerrain terrain;
	private final Arterials arterials;
	private final double[] amp = new double[HARMONICS], phase = new double[HARMONICS];
	private final double ampSum;

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
		this.arterials = new Arterials(seed, site.tier(), this, terrain, grid);
	}

	public SettlementSite site() { return site; }

	public CoarseTerrain terrain() { return terrain; }

	public Arterials arterials() { return arterials; }

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

	private int noise(int x, int z) {
		int i = Math.floorDiv(x, NOISE_CELL), j = Math.floorDiv(z, NOISE_CELL), fx = Math.floorMod(x, NOISE_CELL), fz = Math.floorMod(z, NOISE_CELL);
		int v00 = lat(i, j), v10 = lat(i + 1, j), v01 = lat(i, j + 1), v11 = lat(i + 1, j + 1);
		int top = v00 * (NOISE_CELL - fx) + v10 * fx, bottom = v01 * (NOISE_CELL - fx) + v11 * fx;
		return (top * (NOISE_CELL - fz) + bottom * fz) / (NOISE_CELL * NOISE_CELL);
	}

	private int lat(int i, int j) { return Hash64.below(Hash64.hash(seed, "density-noise", i, j), 2 * NOISE_AMP + 1) - NOISE_AMP; }

	/** Density before the arterial bonus, 0..cap; 0 outside the footprint. */
	public int base(int x, int z) {
		int sx = terrain.snap(x), sz = terrain.snap(z);
		if (!inside(sx, sz)) return 0;
		double dx = sx - site.centre().x(), dz = sz - site.centre().z();
		double t = (dx == 0 && dz == 0) ? 0 : dist(sx, sz) / radiusAt(StrictMath.atan2(dz, dx));
		int cap = site.tier().densityCap;
		int raw = (int) StrictMath.floor(cap * (1 - t * t) + 0.5) + noise(sx, sz);
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
