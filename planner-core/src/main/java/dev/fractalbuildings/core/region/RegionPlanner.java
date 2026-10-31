package dev.fractalbuildings.core.region;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.settlement.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-cell settlement decision (ROADMAP 5.1), a pure function of (seed, cell, probe). The probe is queried
 * only at multiples of {@link RegionProbe#STRIDE}, so a recorded lattice replays every decision exactly.
 *
 * <p>Policy is S4's "grow": a settlement never leaves its cell, so the radius is clamped to half the
 * cell size and to what fits around the chosen centre. (Joining cells comes with the join rule, MC1.)
 */
public final class RegionPlanner {
	public static final int STRIDE = RegionProbe.STRIDE;
	public static final int PEAK_Y = 140;

	public final RegionField field;
	private final RegionProbe probe;
	public double baseChance = 0.65;
	public double minSuitability = 0.35;
	public double maxWaterFrac = 0.15, maxPeakFrac = 0.15;
	/** Candidate centres must keep this fraction of the cell size from the cell edge. */
	public double edgeMarginFrac = 0.15;
	/** Centres tried per cell: the site itself and candidates-1 hashed positions within 0.3 cell sizes. */
	public int candidates = 8;
	public int footprintCap = 2400;
	/** Grow policy: radius capped at this fraction of the cell size. */
	public double clampFrac = 0.5;
	public int maxRadius = Tiers.DEFAULT_MAX_RADIUS;
	/** Water bias (5.10): off gives the pre-R2 decisions for every tier (A/B tests, evidence). */
	public boolean waterBias = true;
	/**
	 * SKIPROLL: a cell whose roll is at or above the largest chance it could have (baseChance times the best biome
	 * multiplier) can never settle, so it is decided with no terrain read (reason "roll", terrain numbers empty). Off
	 * reads every cell in full (tests compare the two; settlements are identical).
	 */
	public boolean skipDeadRolls = true;

	public RegionPlanner(RegionField field, RegionProbe probe) {
		this.field = field;
		this.probe = probe;
	}

	static int snap(double v) {
		return (int) (Math.floorDiv((long) Math.floor(v) + STRIDE / 2, STRIDE) * STRIDE);
	}

	/** Radius the cell's tier draws, before the fit to its shape, after the grow clamp to half a cell. */
	public int plannedRadius(int cx, int cz) {
		int r = Tiers.radius(field.seed, cx, cz, Tiers.tier(field.seed, cx, cz), maxRadius);
		return Math.min(r, (int) (clampFrac * field.cellSize));
	}

	/** How far from the cell's site the probe may be read (candidates, footprint and snapping). */
	public int probeReach(int cx, int cz) {
		int r = Math.min(plannedRadius(cx, cz), footprintCap);
		if (biasOf(cx, cz) > 0) r = Math.max(r, Tiers.WATER_REACH) + Tiers.WATER_STEP;
		return (int) (0.3 * field.cellSize) + r + 2 * STRIDE;
	}

	private double biasOf(int cx, int cz) {
		return waterBias ? Tiers.waterBias(Tiers.tier(field.seed, cx, cz)) : 0;
	}

	private record Foot(double suit, double water, double peak, boolean centreBad, double waterDist, Map<String, Double> groups) {}

	/** Sample the footprint disk on the stride lattice. */
	private Foot footprint(int cx, int cz, int radius) {
		int r = Math.min(radius, footprintCap);
		int step = STRIDE * Math.max(1, (int) Math.ceil(r / (4.0 * STRIDE)));
		int n = Math.max(1, r / step);
		double sum = 0, water = 0, peak = 0;
		boolean centreBad = false;
		long nearest = Long.MAX_VALUE;
		int count = 0;
		TreeMap<String, Integer> g = new TreeMap<>();
		for (int j = -n; j <= n; j++) {
			for (int i = -n; i <= n; i++) {
				int dx = i * step, dz = j * step;
				if ((long) dx * dx + (long) dz * dz > (long) r * r) continue;
				int x = cx + dx, z = cz + dz;
				String b = probe.biome(x, z);
				boolean w = probe.water(x, z);
				boolean p = BiomeClass.isPeak(b) || probe.floorY(x, z) + probe.waterDepth(x, z) >= PEAK_Y;
				count++;
				if (dx == 0 && dz == 0) centreBad = w || p;
				if (w) nearest = Math.min(nearest, (long) dx * dx + (long) dz * dz);
				if (w) water++;
				else if (p) peak++;
				else sum += BiomeClass.multiplier(b);
				g.merge(w ? "water" : BiomeClass.group(b), 1, Integer::sum);
			}
		}
		TreeMap<String, Double> gf = new TreeMap<>();
		for (var e : g.entrySet()) gf.put(e.getKey(), e.getValue() / (double) count);
		return new Foot(sum / count, water / count, peak / count, centreBad,
			nearest == Long.MAX_VALUE ? Double.MAX_VALUE : StrictMath.sqrt((double) nearest), gf);
	}

	/** Unboosted suitability; the water cap is looser for tiers with a water bias. */
	private double score(Foot f, boolean biased) {
		double cap = biased ? Tiers.WATER_CAP_BIASED : maxWaterFrac;
		if (f.centreBad || f.water > cap || f.peak > maxPeakFrac) return 0;
		if (biased && f.water + f.peak > Tiers.WATER_PLUS_PEAK_MAX) return 0;
		return f.suit;
	}

	/** Distance (capped at WATER_REACH) from a centre to the nearest water: footprint samples and a sparse lattice. */
	private double waterDist(int x, int z, Foot f, WaterCache cache) {
		int reach = Tiers.WATER_REACH, step = Tiers.WATER_STEP;
		long best = (long) reach * reach;
		if (f.waterDist < reach) best = Math.min(best, (long) (f.waterDist * f.waterDist));
		for (int gz = Math.floorDiv(z - reach + step - 1, step); gz <= Math.floorDiv(z + reach, step); gz++) {
			for (int gx = Math.floorDiv(x - reach + step - 1, step); gx <= Math.floorDiv(x + reach, step); gx++) {
				long dx = (long) gx * step - x, dz = (long) gz * step - z, d2 = dx * dx + dz * dz;
				if (d2 >= best) continue;
				int k = cache.index(gx, gz);
				if (cache.state[k] == 0) cache.state[k] = (byte) (probe.water(gx * step, gz * step) ? 2 : 1);
				if (cache.state[k] == 2) best = d2;
			}
		}
		return StrictMath.sqrt((double) best);
	}

	/**
	 * N6: is there large water within {@link Tiers#PORT_REACH} of the centre, a water sample of the sparse lattice with a water
	 * neighbour (a body at least one lattice step wide)? Reads the same lattice samples as the water search, through its cache.
	 */
	private boolean largeWaterNear(int x, int z, WaterCache cache) {
		int step = Tiers.WATER_STEP, reach = Tiers.PORT_REACH;
		long r2 = (long) reach * reach;
		for (int gz = Math.floorDiv(z - reach, step); gz <= Math.floorDiv(z + reach, step) + 1; gz++) {
			for (int gx = Math.floorDiv(x - reach, step); gx <= Math.floorDiv(x + reach, step) + 1; gx++) {
				long dx = (long) gx * step - x, dz = (long) gz * step - z;
				if (dx * dx + dz * dz > r2 || !latticeWater(gx, gz, cache)) continue;
				for (int nz = -1; nz <= 1; nz++) {
					for (int nx = -1; nx <= 1; nx++) if ((nx != 0 || nz != 0) && latticeWater(gx + nx, gz + nz, cache)) return true;
				}
			}
		}
		return false;
	}

	/** Water at a lattice sample: through the decision's cache when the sample lies in its span, else a direct read. */
	private boolean latticeWater(int gx, int gz, WaterCache cache) {
		int i = gx - cache.gx0, j = gz - cache.gz0;
		if (i < 0 || j < 0 || i >= cache.n || j >= cache.n) return probe.water(gx * Tiers.WATER_STEP, gz * Tiers.WATER_STEP);
		int k = cache.index(gx, gz);
		if (cache.state[k] == 0) cache.state[k] = (byte) (probe.water(gx * Tiers.WATER_STEP, gz * Tiers.WATER_STEP) ? 2 : 1);
		return cache.state[k] == 2;
	}

	/** Water lattice samples already probed for one decision: a flat array over every point a candidate can reach. */
	private final class WaterCache {
		final int gx0, gz0, n;
		final byte[] state;

		WaterCache(int sx, int sz) {
			int step = Tiers.WATER_STEP, span = (int) (0.3 * field.cellSize) + STRIDE + Tiers.WATER_REACH + step;
			gx0 = Math.floorDiv(sx - span, step);
			gz0 = Math.floorDiv(sz - span, step);
			n = 2 * span / step + 3;
			state = new byte[n * n];
		}

		int index(int gx, int gz) {
			return (gz - gz0) * n + (gx - gx0);
		}
	}

	private static double boost(double score, double bias, double dw) {
		double w = Math.max(0, Math.min(1, 1 - dw / Tiers.WATER_REACH));
		return score * (1 + bias * w);
	}

	public CellDecision decide(int cx, int cz) {
		long seed = field.seed;
		int sx = field.siteX(cx, cz), sz = field.siteZ(cx, cz);
		double roll = Tiers.unit(Hash64.hash(seed, "region/roll", cx, cz));
		Tier tier = Tiers.tier(seed, cx, cz);
		int drawn = Tiers.radius(seed, cx, cz, tier, maxRadius);
		int radius = Math.min(drawn, (int) (clampFrac * field.cellSize));
		if (skipDeadRolls && roll >= baseChance * BiomeClass.maxMultiplier()) {
			int siteX = snap(sx), siteZ = snap(sz);
			int edge = (int) Math.round(field.edgeDistance(siteX, siteZ)), fit = (int) Math.floor(0.95 * edge);
			return new CellDecision(cx, cz, siteX, siteZ, false, "roll", tier, siteX, siteZ, drawn, Math.min(radius, fit), fit, edge,
				roll, 0, 0, 0, 0, 0, 0, Map.of(), -1, false);
		}
		double bias = biasOf(cx, cz);
		boolean biased = bias > 0;
		WaterCache wcache = new WaterCache(sx, sz);

		int siteX = snap(sx), siteZ = snap(sz);
		Foot siteFoot = footprint(siteX, siteZ, radius);
		double siteBase = score(siteFoot, biased), siteScore = siteBase;
		double siteDw = -1;
		if (biased) {
			siteDw = waterDist(siteX, siteZ, siteFoot, wcache);
			siteScore = boost(siteBase, bias, siteDw);
		}
		int bestX = siteX, bestZ = siteZ, bestK = 0;
		Foot best = siteFoot;
		double bestScore = siteScore, bestBase = siteBase, bestDw = siteDw;
		for (int k = 1; k < candidates; k++) {
			double a = Tiers.unit(Hash64.hash(seed, "region/cand", cx, cz, k, 0)) * (2 * Math.PI);
			double d = StrictMath.sqrt(Tiers.unit(Hash64.hash(seed, "region/cand", cx, cz, k, 1))) * 0.3 * field.cellSize;
			int x = snap(sx + StrictMath.cos(a) * d), z = snap(sz + StrictMath.sin(a) * d);
			RegionField.Hit hit = field.query(x, z);
			if (hit.cx() != cx || hit.cz() != cz) continue;
			if (field.edgeDistance(x, z) < edgeMarginFrac * field.cellSize) continue;
			Foot f = footprint(x, z, radius);
			double base = score(f, biased), s = base, dw = -1;
			if (biased && s > 0) {
				dw = waterDist(x, z, f, wcache);
				s = boost(base, bias, dw);
			}
			if (s > bestScore) {
				bestScore = s;
				bestBase = base;
				best = f;
				bestX = x;
				bestZ = z;
				bestK = k;
				bestDw = dw;
			}
		}
		double chance = baseChance * bestBase;
		if (biased && best.water > maxWaterFrac) chance *= Tiers.COASTAL_CHANCE;
		String reason;
		boolean settled = false;
		double cap = biased ? Tiers.WATER_CAP_BIASED : maxWaterFrac;
		if (best.water > cap) reason = "water";
		else if (best.peak > maxPeakFrac) reason = "peak";
		else if (bestBase < minSuitability) reason = "unsuitable";
		else if (roll >= chance) reason = "roll";
		else {
			reason = "settled";
			settled = true;
		}
		int edge = (int) Math.round(field.edgeDistance(bestX, bestZ));
		int fit = (int) Math.floor(0.95 * edge);
		radius = Math.min(radius, fit);
		return new CellDecision(cx, cz, siteX, siteZ, settled, reason, tier, bestX, bestZ, drawn, radius, fit, edge,
			roll, chance, bestScore, siteScore, bestK, best.water, best.peak, best.groups, bestDw,
			settled && biased && bestDw >= 0 && bestDw <= Tiers.PORT_REACH && largeWaterNear(bestX, bestZ, wcache));
	}

	/**
	 * Decisions, in (cz, cx) order, for every cell whose probe reach lies inside the window
	 * [x0, x1] x [z0, z1] (the area a recorded lattice covers).
	 */
	public List<CellDecision> decideWindow(int x0, int z0, int x1, int z1) {
		int S = field.cellSize;
		List<CellDecision> out = new ArrayList<>();
		for (int cz = Math.floorDiv(z0, S) - 1; cz <= Math.floorDiv(z1, S) + 1; cz++) {
			for (int cx = Math.floorDiv(x0, S) - 1; cx <= Math.floorDiv(x1, S) + 1; cx++) {
				int reach = probeReach(cx, cz), sx = field.siteX(cx, cz), sz = field.siteZ(cx, cz);
				if (sx - reach < x0 || sx + reach > x1 || sz - reach < z0 || sz + reach > z1) continue;
				out.add(decide(cx, cz));
			}
		}
		return out;
	}
}
