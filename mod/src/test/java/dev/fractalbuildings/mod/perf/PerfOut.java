package dev.fractalbuildings.mod.perf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Collects the numbers of one perf run and writes them as a flat JSON object (read by {@code scripts/perf-check.mjs}). */
public final class PerfOut {
	private final Map<String, Object> values = new LinkedHashMap<>();

	public PerfOut put(String key, double v) {
		values.put(key, Math.round(v * 100.0) / 100.0);
		System.out.println("PERF " + key + " = " + String.format(Locale.ROOT, "%.2f", v));
		return this;
	}

	public PerfOut put(String key, String v) {
		values.put(key, v);
		System.out.println("PERF " + key + " = " + v);
		return this;
	}

	public static String load() {
		try {
			return Files.readString(Path.of("/proc/loadavg")).split(" ")[0];
		} catch (IOException e) {
			return "?";
		}
	}

	public void write(Path file) throws IOException {
		StringBuilder sb = new StringBuilder("{\n");
		int i = 0;
		for (var e : values.entrySet()) {
			sb.append("  \"").append(e.getKey()).append("\": ");
			if (e.getValue() instanceof String s) sb.append('"').append(s.replace("\"", "'")).append('"');
			else sb.append(String.format(Locale.ROOT, "%s", e.getValue()));
			sb.append(++i < values.size() ? ",\n" : "\n");
		}
		Files.createDirectories(file.getParent());
		Files.writeString(file, sb.append("}\n").toString());
	}
}
