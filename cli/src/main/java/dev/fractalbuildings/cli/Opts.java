package dev.fractalbuildings.cli;

import dev.fractalbuildings.cli.Main.UsageException;
import dev.fractalbuildings.core.ir.Facing;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Parsed {@code --name value} options and bare {@code --flag}s, validated against what the command accepts. */
final class Opts {
	private final Map<String, String> values = new TreeMap<>();
	private final Set<String> flags = new java.util.TreeSet<>();

	Opts(String[] args, Set<String> valueNames, Set<String> flagNames) {
		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			if (!a.startsWith("--")) throw new UsageException("unexpected argument '" + a + "'");
			String n = a.substring(2);
			if (flagNames.contains(n)) flags.add(n);
			else if (valueNames.contains(n)) {
				if (i + 1 >= args.length) throw new UsageException("--" + n + " needs a value");
				values.put(n, args[++i]);
			} else throw new UsageException("unknown option '" + a + "'");
		}
	}

	boolean flag(String n) { return flags.contains(n); }

	String get(String n, String def) { return values.getOrDefault(n, def); }

	String require(String n) {
		String v = values.get(n);
		if (v == null) throw new UsageException("missing --" + n);
		return v;
	}

	boolean has(String n) { return values.containsKey(n); }

	long longValue(String n) {
		String v = require(n);
		try { return Long.parseLong(v); } catch (NumberFormatException e) { throw new UsageException("bad --" + n + ": " + v); }
	}

	static int[] lot(String s) {
		String[] p = s.split("x", -1);
		try {
			if (p.length != 2) throw new NumberFormatException();
			int w = Integer.parseInt(p[0]), d = Integer.parseInt(p[1]);
			if (w <= 0 || d <= 0) throw new NumberFormatException();
			return new int[] {w, d};
		} catch (NumberFormatException e) {
			throw new UsageException("bad lot '" + s + "', expected WxD");
		}
	}

	static int[] at(String s) {
		String[] p = s.split(",", -1);
		try {
			if (p.length != 2) throw new NumberFormatException();
			return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
		} catch (NumberFormatException e) {
			throw new UsageException("bad --at '" + s + "', expected X,Z");
		}
	}

	static Facing front(String s) {
		try { return Facing.valueOf(s.toUpperCase(Locale.ROOT)); }
		catch (IllegalArgumentException e) { throw new UsageException("bad front '" + s + "', expected north, east, south or west"); }
	}

	/** {@code A..B} (inclusive) or a single {@code N}. */
	static long[] seedRange(String s) {
		try {
			int i = s.indexOf("..");
			long a = Long.parseLong(i < 0 ? s : s.substring(0, i));
			long b = i < 0 ? a : Long.parseLong(s.substring(i + 2));
			if (b < a || b - a > 10_000_000) throw new NumberFormatException();
			return new long[] {a, b};
		} catch (NumberFormatException e) {
			throw new UsageException("bad seeds '" + s + "', expected A..B with A <= B");
		}
	}

	static List<String> list(String s) {
		List<String> out = new ArrayList<>();
		for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
		if (out.isEmpty()) throw new UsageException("empty list");
		return out;
	}
}
