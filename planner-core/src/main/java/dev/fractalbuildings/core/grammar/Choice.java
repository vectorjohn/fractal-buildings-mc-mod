package dev.fractalbuildings.core.grammar;

import dev.fractalbuildings.core.rand.Hash64;
import java.util.List;

/**
 * Weighted rendezvous choice. Each option scores {@code -ln(u) / weight}; the lowest score wins.
 * Adding an option never changes the pick unless the new option wins; removing the winner moves to a
 * deterministic runner-up. Uses StrictMath, which is bit-exact across JVMs.
 */
public final class Choice {
	private Choice() {}

	public record Option(String key, int weight) {
		public Option {
			if (weight <= 0) throw new IllegalArgumentException("weight " + weight + " for " + key);
		}
	}

	public static Option opt(String key, int weight) { return new Option(key, weight); }

	public static double score(long seed, String purpose, Option o) {
		long h = Hash64.hash(seed, purpose, Hash64.of(o.key()));
		long bits = h >>> 11;
		double u = bits == 0 ? Double.MIN_VALUE : bits * 0x1.0p-53;
		return -StrictMath.log(u) / o.weight();
	}

	public static String pick(long seed, String purpose, List<Option> options) {
		if (options.isEmpty()) throw new IllegalArgumentException("no options");
		Option best = null;
		double bestScore = 0;
		for (Option o : options) {
			double s = score(seed, purpose, o);
			if (best == null || s < bestScore || (s == bestScore && o.key().compareTo(best.key()) < 0)) {
				best = o;
				bestScore = s;
			}
		}
		return best.key();
	}

	public static String pick(long seed, String purpose, Option... options) { return pick(seed, purpose, List.of(options)); }
}
