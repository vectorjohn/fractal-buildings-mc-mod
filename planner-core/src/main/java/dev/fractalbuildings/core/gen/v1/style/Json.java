package dev.fractalbuildings.core.gen.v1.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed reads from parsed style JSON ({@code JsonReader} output) with defaults; errors name the key. */
public final class Json {
	private Json() {}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> obj(Object v, String path) {
		if (v == null) return Map.of();
		if (!(v instanceof Map)) throw new IllegalArgumentException(path + ": expected an object");
		return (Map<String, Object>) v;
	}

	public static Map<String, Object> obj(Map<String, Object> o, String key, String path) { return obj(o.get(key), path + "." + key); }

	public static int integer(Map<String, Object> o, String key, int def, String path) {
		Object v = o.get(key);
		if (v == null) return def;
		if (!(v instanceof Long l) || l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) throw new IllegalArgumentException(path + "." + key + ": expected an integer");
		return (int) l.longValue();
	}

	public static boolean bool(Map<String, Object> o, String key, boolean def, String path) {
		Object v = o.get(key);
		if (v == null) return def;
		if (!(v instanceof Boolean b)) throw new IllegalArgumentException(path + "." + key + ": expected true or false");
		return b;
	}

	public static String string(Map<String, Object> o, String key, String def, String path) {
		Object v = o.get(key);
		if (v == null) return def;
		if (!(v instanceof String s)) throw new IllegalArgumentException(path + "." + key + ": expected a string");
		return s;
	}

	public static List<String> strings(Object v, String path) {
		if (v == null) return List.of();
		if (!(v instanceof List<?> a)) throw new IllegalArgumentException(path + ": expected an array of strings");
		List<String> out = new ArrayList<>();
		for (Object x : a) {
			if (!(x instanceof String s)) throw new IllegalArgumentException(path + ": expected an array of strings");
			out.add(s);
		}
		return List.copyOf(out);
	}

	/** {@code {"a": 3, "b": 1}} as an ordered map of positive integer weights (document order). */
	public static Map<String, Integer> weights(Map<String, Object> o, String key, Map<String, Integer> def, String path) {
		Object v = o.get(key);
		if (v == null) return def;
		Map<String, Object> m = obj(v, path + "." + key);
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, Object> e : m.entrySet()) {
			int w = integer(m, e.getKey(), 0, path + "." + key);
			if (w < 0) throw new IllegalArgumentException(path + "." + key + "." + e.getKey() + ": weight must be >= 0");
			out.put(e.getKey(), w);
		}
		return Collections.unmodifiableMap(out);
	}

	/** {@code {"role": ["a", "b"]}} as an ordered map of string lists. */
	public static Map<String, List<String>> lists(Map<String, Object> o, String key, String path) {
		Map<String, Object> m = obj(o, key, path);
		Map<String, List<String>> out = new LinkedHashMap<>();
		for (Map.Entry<String, Object> e : m.entrySet()) out.put(e.getKey(), strings(e.getValue(), path + "." + key + "." + e.getKey()));
		return Collections.unmodifiableMap(out);
	}

	/**
	 * {@code extends}: the child's top-level keys replace the parent's, except objects, which merge one
	 * level deep (the child's keys win). So a style can override one roof key and keep the rest.
	 */
	public static Map<String, Object> merge(Map<String, Object> parent, Map<String, Object> child) {
		Map<String, Object> out = new LinkedHashMap<>(parent);
		for (Map.Entry<String, Object> e : child.entrySet()) {
			Object pv = out.get(e.getKey());
			if (pv instanceof Map<?, ?> pm && e.getValue() instanceof Map<?, ?> cm) {
				Map<Object, Object> m = new LinkedHashMap<>(pm);
				m.putAll(cm);
				out.put(e.getKey(), m);
			} else {
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}
}
