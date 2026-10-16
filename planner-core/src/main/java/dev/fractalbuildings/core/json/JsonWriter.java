package dev.fractalbuildings.core.json;

import java.util.List;

/**
 * Tiny deterministic JSON text helpers. Values are built as strings in a fixed order, so the output
 * is byte-stable. Numbers are integers only; 64-bit values should go through {@link #str(long)}
 * (JavaScript cannot hold them exactly).
 */
public final class JsonWriter {
	private final StringBuilder sb = new StringBuilder();
	private boolean first = true;
	private final boolean compact;

	private JsonWriter(boolean compact) { this.compact = compact; }

	/** One-line object: {@code {"a": 1, "b": "x"}} (compact mode: no spaces at all). */
	public static JsonWriter object() { return new JsonWriter(false); }

	public static JsonWriter compactObject() { return new JsonWriter(true); }

	/** Adds a key with an already-formatted JSON value. */
	public JsonWriter raw(String key, String json) {
		sb.append(first ? "" : compact ? "," : ", ");
		first = false;
		sb.append(quote(key)).append(compact ? ":" : ": ").append(json);
		return this;
	}

	public JsonWriter num(String key, long v) { return raw(key, Long.toString(v)); }

	public JsonWriter text(String key, String v) { return raw(key, quote(v)); }

	/** A 64-bit integer written as a decimal string. */
	public JsonWriter str64(String key, long v) { return raw(key, str(v)); }

	/** String or {@code null}. */
	public JsonWriter textOrNull(String key, String v) { return raw(key, v == null ? "null" : quote(v)); }

	public String build() { return "{" + sb + "}"; }

	public static String str(long v) { return "\"" + v + "\""; }

	public static String ints(int... a) {
		StringBuilder b = new StringBuilder("[");
		for (int i = 0; i < a.length; i++) {
			if (i > 0) b.append(", ");
			b.append(a[i]);
		}
		return b.append(']').toString();
	}

	public static String array(List<String> jsonValues) { return "[" + String.join(", ", jsonValues) + "]"; }

	public static String quote(String s) {
		StringBuilder b = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> b.append("\\\"");
				case '\\' -> b.append("\\\\");
				case '\n' -> b.append("\\n");
				case '\r' -> b.append("\\r");
				case '\t' -> b.append("\\t");
				default -> {
					if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
					else b.append(c);
				}
			}
		}
		return b.append('"').toString();
	}
}
