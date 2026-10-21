package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.json.JsonReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Strict typed access over parsed JSON for the settlement readers, same rules as the scene reader: every error is
 * an {@link IllegalArgumentException} whose message starts with the JSON path; unknown keys are ignored; missing
 * keys, wrong types and non-integers are errors.
 */
final class Rd {
	private Rd() {}

	static Map<String, Object> root(String json) { return obj(JsonReader.parse(json), "$"); }

	interface Thrower<T> { T get(); }

	/** Runs a record constructor, prefixing the path to its validation message. */
	static <T> T guard(String path, Thrower<T> f) {
		try {
			return f.get();
		} catch (IllegalArgumentException | NullPointerException e) {
			if (e.getMessage() != null && e.getMessage().startsWith("$")) throw e;
			throw new IllegalArgumentException(path + ": " + e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> obj(Object v, String path) {
		if (!(v instanceof Map)) throw new IllegalArgumentException(path + ": expected an object, got " + describe(v));
		return (Map<String, Object>) v;
	}

	@SuppressWarnings("unchecked")
	static List<Object> arr(Object v, String path) {
		if (!(v instanceof List)) throw new IllegalArgumentException(path + ": expected an array, got " + describe(v));
		return (List<Object>) v;
	}

	static String describe(Object v) {
		if (v == null) return "null";
		if (v instanceof Map) return "an object";
		if (v instanceof List) return "an array";
		if (v instanceof String) return "a string";
		if (v instanceof Boolean) return "a boolean";
		if (v instanceof Double) return "a non-integer number " + v;
		return "a number";
	}

	static Object req(Map<String, Object> o, String path, String key) {
		if (!o.containsKey(key)) throw new IllegalArgumentException(path + "." + key + ": missing required field");
		return o.get(key);
	}

	static String string(Map<String, Object> o, String path, String key) {
		Object v = req(o, path, key);
		if (!(v instanceof String s)) throw new IllegalArgumentException(path + "." + key + ": expected a string, got " + describe(v));
		return s;
	}

	static String stringOrNull(Map<String, Object> o, String path, String key) {
		Object v = req(o, path, key);
		if (v == null) return null;
		if (!(v instanceof String s)) throw new IllegalArgumentException(path + "." + key + ": expected a string or null, got " + describe(v));
		return s;
	}

	static boolean bool(Map<String, Object> o, String path, String key) {
		Object v = req(o, path, key);
		if (!(v instanceof Boolean b)) throw new IllegalArgumentException(path + "." + key + ": expected a boolean, got " + describe(v));
		return b;
	}

	static int intValue(Object v, String path) {
		if (!(v instanceof Long l)) throw new IllegalArgumentException(path + ": expected an integer, got " + describe(v));
		if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) throw new IllegalArgumentException(path + ": integer out of range: " + l);
		return (int) l.longValue();
	}

	static int integer(Map<String, Object> o, String path, String key) { return intValue(req(o, path, key), path + "." + key); }

	static long seed(Map<String, Object> o, String path, String key) {
		String s = string(o, path, key);
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(path + "." + key + ": expected a 64-bit integer in a string, got '" + s + "'");
		}
	}

	static <T> List<T> list(Map<String, Object> o, String path, String key, BiFunction<Map<String, Object>, String, T> f) {
		String p = path + "." + key;
		List<Object> a = arr(req(o, path, key), p);
		List<T> out = new ArrayList<>();
		for (int i = 0; i < a.size(); i++) {
			String ip = p + "[" + i + "]";
			out.add(f.apply(obj(a.get(i), ip), ip));
		}
		return out;
	}

	/** An array of arbitrary elements, each parsed by {@code f(value, path)}. */
	static <T> List<T> items(Map<String, Object> o, String path, String key, BiFunction<Object, String, T> f) {
		String p = path + "." + key;
		List<Object> a = arr(req(o, path, key), p);
		List<T> out = new ArrayList<>();
		for (int i = 0; i < a.size(); i++) out.add(f.apply(a.get(i), p + "[" + i + "]"));
		return out;
	}

	static List<String> strings(Map<String, Object> o, String path, String key) {
		return items(o, path, key, (v, p) -> {
			if (!(v instanceof String s)) throw new IllegalArgumentException(p + ": expected a string, got " + describe(v));
			return s;
		});
	}

	static int[] ints(Map<String, Object> o, String path, String key, int len) {
		String p = path + "." + key;
		List<Object> a = arr(req(o, path, key), p);
		if (len >= 0 && a.size() != len) throw new IllegalArgumentException(p + ": expected " + len + " integers, got " + a.size());
		int[] r = new int[a.size()];
		for (int i = 0; i < r.length; i++) r[i] = intValue(a.get(i), p + "[" + i + "]");
		return r;
	}

	static int[] intsOf(Object v, String p, int len) {
		List<Object> a = arr(v, p);
		if (a.size() != len) throw new IllegalArgumentException(p + ": expected " + len + " integers, got " + a.size());
		int[] r = new int[len];
		for (int i = 0; i < len; i++) r[i] = intValue(a.get(i), p + "[" + i + "]");
		return r;
	}

	static List<Pt> points(Map<String, Object> o, String path, String key) {
		return items(o, path, key, (v, p) -> {
			int[] a = intsOf(v, p, 2);
			return new Pt(a[0], a[1]);
		});
	}

	static Cell cell(Object v, String p) {
		int[] a = intsOf(v, p, 2);
		return new Cell(a[0], a[1]);
	}

	static <E> E enumOf(Map<String, Object> o, String path, String key, Function<String, E> of) {
		String s = string(o, path, key);
		try {
			return of.apply(s);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(path + "." + key + ": " + e.getMessage());
		}
	}

	static Violation violation(Map<String, Object> o, String p) {
		return guard(p, () -> new Violation(string(o, p, "invariant"), stringOrNull(o, p, "building"), string(o, p, "message"),
				list(o, p, "cells", Rd::box3)));
	}

	static Box3 box3(Map<String, Object> o, String p) {
		int[] m = ints(o, p, "min", 3), s = ints(o, p, "size", 3);
		return guard(p, () -> new Box3(m[0], m[1], m[2], s[0], s[1], s[2]));
	}
}
