package dev.fractalbuildings.core.region;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.settlement.Tier;

/**
 * The settlement tier ladder (plan "Tier ladder", ROADMAP 5.1): weights, radius bands and density caps
 * are constants here so they are cheap to change. A cell draws its tier by weight, then a radius from
 * the tier's band: log-uniform, and for the top tier a Pareto tail (alpha 1.5) clipped at
 * {@code maxRadius}. Bands overlap, so there is no hard step between tiers.
 */
public final class Tiers {
	private Tiers() {}

	public static final int DEFAULT_MAX_RADIUS = 4096;
	/** Pareto exponent of the metropolis radius tail. */
	public static final double PARETO_ALPHA = 1.5;

	private static final double TWO53 = 9007199254740992.0;

	/** Uniform double in [0, 1) from a hash. */
	public static double unit(long h) {
		return (h >>> 11) / TWO53;
	}

	/** Tier for a cell, by weight, from the hash of (seed, cell). */
	public static Tier tier(long seed, int cx, int cz) {
		double u = unit(Hash64.hash(seed, "region/tier", cx, cz));
		double acc = 0;
		Tier[] all = Tier.values();
		for (int i = 0; i < all.length - 1; i++) {
			acc += all[i].weight;
			if (u < acc) return all[i];
		}
		return all[all.length - 1];
	}

	/** Radius for a cell of the given tier, capped at {@code maxRadius}. */
	public static int radius(long seed, int cx, int cz, Tier t, int maxRadius) {
		double u = unit(Hash64.hash(seed, "region/radius", cx, cz));
		double r;
		if (t == Tier.METROPOLIS) {
			r = t.minRadius / StrictMath.pow(1.0 - u, 1.0 / PARETO_ALPHA);
		} else {
			r = t.minRadius * StrictMath.exp(u * StrictMath.log((double) t.maxRadius / t.minRadius));
		}
		return (int) Math.min((long) maxRadius, Math.round(r));
	}

	// ---- water bias (5.10) ----

	/** Reach of the water search, in blocks; the bias weight falls linearly to 0 there. */
	public static final int WATER_REACH = 768;
	/** Spacing of the sparse lattice the water search samples (a multiple of the probe stride). */
	public static final int WATER_STEP = 192;
	/** A biased settlement whose centre is within this of large water is tagged `port` (ADR 0004 N6). */
	public static final int PORT_REACH = 384;
	/** Footprint water cap for tiers with a bias (the disk may hold coast; the urban mask keeps buildings dry). */
	public static final double WATER_CAP_BIASED = 0.4;
	/** Roll-chance factor for a biased tier whose footprint holds more water than the plain cap: coast sites are admitted but not at the cost of a different settlement count. */
	public static final double COASTAL_CHANCE = 0.5;
	/** Footprint water plus peak share at most this much (land share at least 0.5). */
	public static final double WATER_PLUS_PEAK_MAX = 0.5;

	/** Score gain B at zero distance to water: score' = score x (1 + B x w). */
	public static double waterBias(Tier t) {
		return switch (t) {
			case CITY, METROPOLIS -> 0.6;
			case TOWN -> 0.3;
			default -> 0;
		};
	}
}
