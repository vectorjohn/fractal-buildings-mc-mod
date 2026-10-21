package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.rand.Hash64;

/** A coarse probe whose floor heights are off by a hashed amount in [-error, error] (ADR 0003 D4: the in-game screen is biased). */
final class BiasedCoarseProbe implements CoarseProbe {
	private final CoarseProbe base;
	private final long seed;
	private final int error;

	BiasedCoarseProbe(CoarseProbe base, long seed, int error) {
		this.base = base;
		this.seed = seed;
		this.error = error;
	}

	@Override public int stride() { return base.stride(); }

	@Override public int floorY(int x, int z) { return base.floorY(x, z) + Hash64.below(Hash64.hash(seed, "bias", x, z), 2 * error + 1) - error; }

	@Override public int waterDepth(int x, int z) { return base.waterDepth(x, z); }

	@Override public String biome(int x, int z) { return base.biome(x, z); }
}
