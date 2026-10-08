package dev.fractalbuildings.cli;

import dev.fractalbuildings.core.Planner;
import java.util.HashMap;
import java.util.Map;

/** Stub CLI: {@code gen --seed N --lot WxD --style NAME}. Item 0.4 makes it write IR JSON. */
public final class Main {
	private Main() {}

	public static void main(String[] args) {
		System.exit(run(args));
	}

	static int run(String[] args) {
		if (args.length == 0 || !args[0].equals("gen")) {
			System.err.println("usage: gen --seed N --lot WxD --style NAME");
			return 2;
		}
		Map<String, String> opts = parseOptions(args);
		System.out.println("fractal-cli " + Planner.version() + " gen (stub) " + opts);
		return 0;
	}

	static Map<String, String> parseOptions(String[] args) {
		Map<String, String> opts = new java.util.TreeMap<>();
		for (int i = 1; i + 1 < args.length; i += 2) {
			if (args[i].startsWith("--")) {
				opts.put(args[i].substring(2), args[i + 1]);
			}
		}
		return opts;
	}
}
