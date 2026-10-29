package dev.fractalbuildings.core.world;

import java.util.List;
import java.util.Map;

/**
 * The sidecar of an in-game playtest note (FBNOTE, {@code /fbnote <comment>}): format {@code fb-playtest-note/1}, written next to the
 * screenshot with the same base name. Pure: the mod fills the map, this writes it, {@code scripts/playtest-gallery.mjs} reads it.
 *
 * <p>Keys (all optional but {@code format} and {@code comment}): {@code comment}, {@code time} (ISO, UTC), {@code gameTime}, {@code seed},
 * {@code dimension}, {@code position} {@code {x,y,z,yaw,pitch}}, {@code biome}, {@code target} {@code {block,x,y,z}}, {@code mod}
 * {@code {version,commit}}, {@code planner} (see {@link PlanLookup}; or {@code {"available": false, "note": ...}} on a remote server).
 */
public final class PlaytestNote {
	private PlaytestNote() {}

	public static final String FORMAT = "fb-playtest-note/1";

	/** Longs beyond this are written as strings (seeds); 2^53, the largest integer a JavaScript number holds exactly. */
	static final long MAX_SAFE = 1L << 53;

	/**
	 * The debug switch: an explicit {@code -Dfractalbuildings.debugNotes=true|false} wins, else the development environment turns it on,
	 * else the {@code debugNotes} key of {@code config/fractalbuildings-client.properties}. Anything but "true" counts as false.
	 */
	public static boolean enabled(boolean development, String systemProperty, String configValue) {
		if (systemProperty != null && !systemProperty.isBlank()) return systemProperty.trim().equalsIgnoreCase("true");
		if (development) return true;
		return configValue != null && configValue.trim().equalsIgnoreCase("true");
	}

	/** Pretty JSON (2-space indent) of nested maps, lists, strings, numbers, booleans and nulls; map keys stay in insertion order. */
	public static String json(Map<String, Object> note) {
		StringBuilder sb = new StringBuilder();
		write(sb, note, 0);
		return sb.append('\n').toString();
	}

	private static void write(StringBuilder sb, Object v, int indent) {
		if (v == null) sb.append("null");
		else if (v instanceof Map<?, ?> m) {
			if (m.isEmpty()) { sb.append("{}"); return; }
			sb.append("{\n");
			int i = 0;
			for (Map.Entry<?, ?> e : m.entrySet()) {
				sb.append("  ".repeat(indent + 1)).append(quote(String.valueOf(e.getKey()))).append(": ");
				write(sb, e.getValue(), indent + 1);
				if (++i < m.size()) sb.append(',');
				sb.append('\n');
			}
			sb.append("  ".repeat(indent)).append('}');
		} else if (v instanceof List<?> l) {
			sb.append('[');
			for (int i = 0; i < l.size(); i++) {
				if (i > 0) sb.append(", ");
				write(sb, l.get(i), indent + 1);
			}
			sb.append(']');
		} else if (v instanceof Double d) sb.append(Double.isFinite(d) ? String.format(java.util.Locale.ROOT, "%.3f", d) : "null");
		else if (v instanceof Float f) sb.append(Float.isFinite(f) ? String.format(java.util.Locale.ROOT, "%.2f", f) : "null");
		else if (v instanceof Long l && Math.abs(l) > MAX_SAFE) sb.append(quote(l.toString())); // JSON readers in JS lose a long above 2^53
		else if (v instanceof Number || v instanceof Boolean) sb.append(v);
		else sb.append(quote(String.valueOf(v)));
	}

	static String quote(String s) {
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
				}
			}
		}
		return sb.append('"').toString();
	}
}
