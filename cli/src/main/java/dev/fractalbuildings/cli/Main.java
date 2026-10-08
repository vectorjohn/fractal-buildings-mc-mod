package dev.fractalbuildings.cli;

import dev.fractalbuildings.core.Planner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** CLI: {@code gen --seed N --lot WxD --style NAME [--out FILE]} writes smoke IR JSON (stdout if no --out). */
public final class Main {
	private Main() {}

	public static void main(String[] args) {
		System.exit(run(args));
	}

	static int run(String[] args) {
		if (args.length == 0 || !args[0].equals("gen")) {
			System.err.println("usage: gen --seed N --lot WxD --style NAME [--out FILE]");
			return 2;
		}
		Map<String, String> opts = parseOptions(args);
		long seed;
		try {
			seed = Long.parseLong(opts.getOrDefault("seed", "0"));
		} catch (NumberFormatException e) {
			System.err.println("bad --seed: " + opts.get("seed"));
			return 2;
		}
		// --lot and --style are accepted but unused until real generation exists.
		byte[] json = IrJson.write(Planner.smokeBox(seed)).getBytes(StandardCharsets.UTF_8);
		String out = opts.get("out");
		try {
			if (out == null) {
				System.out.write(json);
				System.out.flush();
			} else {
				Path p = Path.of(out);
				if (p.getParent() != null) Files.createDirectories(p.getParent());
				Files.write(p, json);
			}
		} catch (IOException e) {
			System.err.println("write failed: " + e.getMessage());
			return 1;
		}
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
