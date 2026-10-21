package dev.fractalbuildings.core.region;

import dev.fractalbuildings.core.rand.Hash64;
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
		return (int) (0.3 * field.cellSize) + Math.min(plannedRadius(cx, cz), footprintCap) + 2 * STRIDE;
	}

	private record Foot(double suit, double water, double peak, boolean centreBad, Map<String, Double> groups) {}

	/** Sample the footprint disk on the stride lattice. */
	private Foot footprint(int cx, int cz, int radius) {
		int r = Math.min(radius, footprintCap);
		int step = STRIDE * Math.max(1, (int) Math.ceil(r / (4.0 * STRIDE)));
		int n = Math.max(1, r / step);
		double sum = 0, water = 0, peak = 0;
		boolean centreBad = false;
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
				if (w) water++;
				else if (p) peak++;
				else sum += BiomeClass.multiplier(b);
				g.merge(w ? "water" : BiomeClass.group(b), 1, Integer::sum);
			}
		}
		TreeMap<String, Double> gf = new TreeMap<>();
		for (var e : g.entrySet()) gf.put(e.getKey(), e.getValue() / (double) count);
		return new Foot(sum / count, water / count, peak / count, centreBad, gf);
	}

	private double score(Foot f) {
		return f.centreBad || f.water > maxWaterFrac || f.peak > maxPeakFrac ? 0 : f.suit;
	}

	public CellDecision decide(int cx, int cz) {
		long seed = field.seed;
		int sx = field.siteX(cx, cz), sz = field.siteZ(cx, cz);
		double roll = Tiers.unit(Hash64.hash(seed, "region/roll", cx, cz));
		Tiers.Tier tier = Tiers.tier(seed, cx, cz);
		int drawn = Tiers.radius(seed, cx, cz, tier, maxRadius);
		int radius = Math.min(drawn, (int) (clampFrac * field.cellSize));

		int siteX = snap(sx), siteZ = snap(sz);
		Foot siteFoot = footprint(siteX, siteZ, radius);
		int bestX = siteX, bestZ = siteZ, bestK = 0;
		Foot best = siteFoot;
		double bestScore = score(siteFoot);
		for (int k = 1; k < candidates; k++) {
			double a = Tiers.unit(Hash64.hash(seed, "region/cand", cx, cz, k, 0)) * (2 * Math.PI);
			double d = StrictMath.sqrt(Tiers.unit(Hash64.hash(seed, "region/cand", cx, cz, k, 1))) * 0.3 * field.cellSize;
			int x = snap(sx + StrictMath.cos(a) * d), z = snap(sz + StrictMath.sin(a) * d);
			RegionField.Hit hit = field.query(x, z);
			if (hit.cx() != cx || hit.cz() != cz) continue;
			if (field.edgeDistance(x, z) < edgeMarginFrac * field.cellSize) continue;
			Foot f = footprint(x, z, radius);
			double s = score(f);
			if (s > bestScore) {
				bestScore = s;
				best = f;
				bestX = x;
				bestZ = z;
				bestK = k;
			}
		}
		double chance = baseChance * bestScore;
		String reason;
		boolean settled = false;
		if (best.water > maxWaterFrac) reason = "water";
		else if (best.peak > maxPeakFrac) reason = "peak";
		else if (bestScore < minSuitability) reason = "unsuitable";
		else if (roll >= chance) reason = "roll";
		else {
			reason = "settled";
			settled = true;
		}
		int edge = (int) Math.round(field.edgeDistance(bestX, bestZ));
		int fit = (int) Math.floor(0.95 * edge);
		radius = Math.min(radius, fit);
		return new CellDecision(cx, cz, siteX, siteZ, settled, reason, tier, bestX, bestZ, drawn, radius, fit, edge,
			roll, chance, bestScore, score(siteFoot), bestK, best.water, best.peak, best.groups);
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
