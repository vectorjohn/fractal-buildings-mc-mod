package dev.fractalbuildings.core.rand;

/**
 * A short, local stream of draws for one scope (one building, one room split...), seeded from a
 * {@link Hash64} sub-seed. Not thread-safe and never shared: create one per scope and use it on
 * one thread. Prefer a fresh {@link Hash64#hash} per independent decision where a decision might
 * later be added or removed, so other decisions don't shift.
 */
public final class Draws {
	private long state;

	public Draws(long seed) { this.state = seed; }

	public long nextLong() {
		state += 0x9E3779B97F4A7C15L;
		return Hash64.mix(state);
	}

	/** Uniform int in [0, bound). */
	public int below(int bound) { return Hash64.below(nextLong(), bound); }

	/** Uniform int in [lo, hi] inclusive. */
	public int range(int lo, int hi) { return Hash64.range(nextLong(), lo, hi); }

	/** True with probability num/den. */
	public boolean chance(int num, int den) { return below(den) < num; }
}
