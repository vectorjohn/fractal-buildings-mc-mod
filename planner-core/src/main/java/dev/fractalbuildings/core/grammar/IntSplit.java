package dev.fractalbuildings.core.grammar;

/**
 * Integer splitting: divide a length into parts that sum exactly to it.
 * Pure integer maths, deterministic; ties always go to the lower index.
 */
public final class IntSplit {
	private IntSplit() {}

	/**
	 * Split {@code length} into {@code mins.length} parts. Each part gets its minimum, and the
	 * remainder is shared by integer weight; the rounding leftover goes to the largest fractional
	 * remainders first (ties by index). Throws if the minimums do not fit.
	 */
	public static int[] split(int length, int[] mins, int[] weights) {
		int n = mins.length;
		if (n == 0 || weights.length != n) throw new IllegalArgumentException("parts " + n + " weights " + weights.length);
		long minSum = 0, weightSum = 0;
		for (int i = 0; i < n; i++) {
			if (mins[i] < 0 || weights[i] <= 0) throw new IllegalArgumentException("min/weight at " + i);
			minSum += mins[i];
			weightSum += weights[i];
		}
		if (minSum > length) throw new IllegalArgumentException("minimums " + minSum + " do not fit in " + length);
		long rem = length - minSum;
		int[] out = new int[n];
		long[] frac = new long[n];
		long given = 0;
		for (int i = 0; i < n; i++) {
			long share = rem * weights[i];
			long base = share / weightSum;
			frac[i] = share % weightSum;
			out[i] = mins[i] + (int) base;
			given += base;
		}
		for (long left = rem - given; left > 0; left--) {
			int best = -1;
			for (int i = 0; i < n; i++) if (frac[i] >= 0 && (best < 0 || frac[i] > frac[best])) best = i;
			out[best]++;
			frac[best] = -1;
		}
		return out;
	}

	/** Equal weights. */
	public static int[] split(int length, int... mins) {
		int[] w = new int[mins.length];
		java.util.Arrays.fill(w, 1);
		return split(length, mins, w);
	}

	/**
	 * Bays: as many parts as fit (each at least {@code minPart}); sizes differ by at most 1, larger
	 * parts first. Throws if that needs a part above {@code maxPart} or the length is below
	 * {@code minPart}.
	 */
	public static int[] repeat(int length, int minPart, int maxPart) {
		if (minPart <= 0 || maxPart < minPart) throw new IllegalArgumentException("bounds " + minPart + ".." + maxPart);
		if (length < minPart) throw new IllegalArgumentException("length " + length + " below minimum part " + minPart);
		int count = length / minPart;
		int base = length / count, extra = length % count;
		if (base + (extra > 0 ? 1 : 0) > maxPart) throw new IllegalArgumentException("length " + length + " needs a part above " + maxPart);
		int[] out = new int[count];
		for (int i = 0; i < count; i++) out[i] = base + (i < extra ? 1 : 0);
		return out;
	}
}
