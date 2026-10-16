package dev.fractalbuildings.core.rand;

import java.nio.charset.StandardCharsets;

/**
 * Deterministic 64-bit hashing for sub-seeds (ADR 0003 D1): every random choice derives from
 * (seed, purpose, coordinates) through this mixer. No shared RNG, no truncation to int.
 *
 * <p>Purposes are short strings ("brief", "rooms", "window-rhythm"); a purpose string is part of
 * the output, so renaming one changes generated buildings.
 */
public final class Hash64 {
	private Hash64() {}

	private static final long GOLDEN = 0x9E3779B97F4A7C15L;

	/** SplitMix64 finaliser (Stafford variant 13). */
	public static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** FNV-1a over the UTF-8 bytes, then mixed. Stable across JVMs (unlike String.hashCode, which is too weak). */
	public static long of(String purpose) {
		long h = 0xCBF29CE484222325L;
		for (byte b : purpose.getBytes(StandardCharsets.UTF_8)) {
			h ^= b & 0xFF;
			h *= 0x100000001B3L;
		}
		return mix(h);
	}

	/** Hash of a seed, a purpose and any number of integer keys (order matters). */
	public static long hash(long seed, String purpose, long... keys) {
		long h = mix(seed ^ of(purpose));
		for (long k : keys) h = mix(h + GOLDEN + k * 0xC2B2AE3D27D4EB4FL);
		return h;
	}

	/** Uniform int in [0, bound) from a hash, by multiply-shift on the top 32 bits (bias below 2^-32 * bound). */
	public static int below(long h, int bound) {
		if (bound <= 0) throw new IllegalArgumentException("bound " + bound);
		return (int) (((h >>> 32) * bound) >>> 32);
	}

	/** Uniform int in [lo, hi] inclusive. */
	public static int range(long h, int lo, int hi) {
		if (hi < lo) throw new IllegalArgumentException(lo + ".." + hi);
		return lo + below(h, hi - lo + 1);
	}
}
