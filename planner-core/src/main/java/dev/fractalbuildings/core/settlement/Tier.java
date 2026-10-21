package dev.fractalbuildings.core.settlement;

/**
 * Settlement size tier (docs/plans/phase5.md "Tier ladder"), the one enum shared by the region field, which draws it, and
 * the settlement plans. Weights, radius bands and density caps are constants here so they are cheap to change. JSON: the
 * lower-case {@link #json} string.
 */
public enum Tier {
	HAMLET("hamlet", 0.25, 40, 90, 30),
	VILLAGE("village", 0.30, 80, 220, 45),
	TOWN("town", 0.25, 200, 500, 65),
	CITY("city", 0.14, 450, 1100, 85),
	METROPOLIS("metropolis", 0.06, 1000, Integer.MAX_VALUE, 100);

	public final String json;
	/** Share of cells that draw this tier. */
	public final double weight;
	/** Radius band in blocks; the metropolis band has no upper bound (Pareto tail, see {@code Tiers.maxRadius}). */
	public final int minRadius, maxRadius;
	/** Highest density the tier reaches. */
	public final int densityCap;

	Tier(String json, double weight, int minRadius, int maxRadius, int densityCap) {
		this.json = json;
		this.weight = weight;
		this.minRadius = minRadius;
		this.maxRadius = maxRadius;
		this.densityCap = densityCap;
	}

	public static Tier of(String s) {
		for (Tier v : values()) if (v.json.equals(s)) return v;
		throw new IllegalArgumentException("unknown Tier '" + s + "'");
	}

	public boolean atLeast(Tier t) { return compareTo(t) >= 0; }
}
