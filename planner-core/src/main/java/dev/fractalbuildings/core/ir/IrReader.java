package dev.fractalbuildings.core.ir;

import dev.fractalbuildings.core.json.JsonReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Strict {@code fb-scene/1} reader behind {@link IrJson#read}. Every error is an
 * {@link IllegalArgumentException} whose message starts with the JSON path ("$.buildings[0].shell.rooms[2].id").
 * Unknown keys are ignored (forward compatible); missing required keys, wrong types, non-integer
 * numbers and out-of-range values are errors. The stored {@code specHash} must match the spec.
 */
final class IrReader {
	private IrReader() {}

	static SceneDoc read(String json) {
		Object root = JsonReader.parse(json);
		Map<String, Object> o = obj(root, "$");
		String format = string(o, "$", "format");
		if (!SceneDoc.FORMAT.equals(format)) {
			throw new IllegalArgumentException("$.format: unsupported irVersion/format '" + format + "', expected '" + SceneDoc.FORMAT + "'");
		}
		return guard("$", () -> new SceneDoc(
				integer(o, "$", "genVersion"),
				seed(o, "$", "seed"),
				o.get("terrain") == null ? null : terrain(obj(o.get("terrain"), "$.terrain"), "$.terrain"),
				list(o, "$", "lots", IrReader::lot),
				list(o, "$", "buildings", IrReader::building),
				list(o, "$", "violations", IrReader::violation)));
	}

	// ---- typed access ----

	private interface Thrower<T> { T get(); }

	/** Runs a record constructor, prefixing the path to its validation message. */
	private static <T> T guard(String path, Thrower<T> f) {
		try {
			return f.get();
		} catch (IllegalArgumentException | NullPointerException e) {
			if (e.getMessage() != null && e.getMessage().startsWith("$")) throw e;
			throw new IllegalArgumentException(path + ": " + e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> obj(Object v, String path) {
		if (!(v instanceof Map)) throw new IllegalArgumentException(path + ": expected an object, got " + describe(v));
		return (Map<String, Object>) v;
	}

	@SuppressWarnings("unchecked")
	private static List<Object> arr(Object v, String path) {
		if (!(v instanceof List)) throw new IllegalArgumentException(path + ": expected an array, got " + describe(v));
		return (List<Object>) v;
	}

	private static String describe(Object v) {
		if (v == null) return "null";
		if (v instanceof Map) return "an object";
		if (v instanceof List) return "an array";
		if (v instanceof String) return "a string";
		if (v instanceof Boolean) return "a boolean";
		if (v instanceof Double) return "a non-integer number " + v;
		return "a number";
	}

	private static Object req(Map<String, Object> o, String path, String key) {
		if (!o.containsKey(key)) throw new IllegalArgumentException(path + "." + key + ": missing required field");
		return o.get(key);
	}

	private static String string(Map<String, Object> o, String path, String key) {
		Object v = req(o, path, key);
		if (!(v instanceof String s)) throw new IllegalArgumentException(path + "." + key + ": expected a string, got " + describe(v));
		return s;
	}

	private static String stringOrNull(Map<String, Object> o, String path, String key) {
		Object v = req(o, path, key);
		if (v == null) return null;
		if (!(v instanceof String s)) throw new IllegalArgumentException(path + "." + key + ": expected a string or null, got " + describe(v));
		return s;
	}

	private static int intValue(Object v, String path) {
		if (!(v instanceof Long l)) throw new IllegalArgumentException(path + ": expected an integer, got " + describe(v));
		if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) throw new IllegalArgumentException(path + ": integer out of range: " + l);
		return (int) l.longValue();
	}

	private static int integer(Map<String, Object> o, String path, String key) {
		return intValue(req(o, path, key), path + "." + key);
	}

	private static long seed(Map<String, Object> o, String path, String key) {
		String s = string(o, path, key);
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(path + "." + key + ": expected a 64-bit integer in a string, got '" + s + "'");
		}
	}

	private static <T> List<T> list(Map<String, Object> o, String path, String key, BiFunction<Map<String, Object>, String, T> f) {
		String p = path + "." + key;
		List<Object> a = arr(req(o, path, key), p);
		List<T> out = new ArrayList<>();
		for (int i = 0; i < a.size(); i++) {
			String ip = p + "[" + i + "]";
			out.add(f.apply(obj(a.get(i), ip), ip));
		}
		return out;
	}

	private static List<String> strings(Map<String, Object> o, String path, String key) {
		String p = path + "." + key;
		List<Object> a = arr(req(o, path, key), p);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < a.size(); i++) {
			if (!(a.get(i) instanceof String s)) throw new IllegalArgumentException(p + "[" + i + "]: expected a string, got " + describe(a.get(i)));
			out.add(s);
		}
		return out;
	}

	private static int[] ints(Map<String, Object> o, String path, String key, int len) {
		String p = path + "." + key;
		List<Object> a = arr(req(o, path, key), p);
		if (len >= 0 && a.size() != len) throw new IllegalArgumentException(p + ": expected " + len + " integers, got " + a.size());
		int[] r = new int[a.size()];
		for (int i = 0; i < r.length; i++) r[i] = intValue(a.get(i), p + "[" + i + "]");
		return r;
	}

	private static <E extends Enum<E>> E enumOf(Map<String, Object> o, String path, String key, Class<E> c) {
		String s = string(o, path, key);
		for (E e : c.getEnumConstants()) if (e.name().toLowerCase(java.util.Locale.ROOT).equals(s)) return e;
		throw new IllegalArgumentException(path + "." + key + ": unknown " + c.getSimpleName().toLowerCase(java.util.Locale.ROOT) + " '" + s + "'");
	}

	// ---- records ----

	private static TerrainPatch terrain(Map<String, Object> o, String p) {
		int[] h = ints(o, p, "floorY", -1);
		return guard(p, () -> new TerrainPatch(integer(o, p, "x0"), integer(o, p, "z0"), integer(o, p, "nx"), integer(o, p, "nz"), h));
	}

	private static Lot lot(Map<String, Object> o, String p) {
		return guard(p, () -> new Lot(string(o, p, "id"), integer(o, p, "x"), integer(o, p, "z"), integer(o, p, "w"), integer(o, p, "d"),
				enumOf(o, p, "front", Facing.class)));
	}

	/** A box from "min" and "size" arrays at the given keys of {@code o}. */
	private static Box3 box3(Map<String, Object> o, String p) {
		int[] m = ints(o, p, "min", 3);
		int[] s = ints(o, p, "size", 3);
		return guard(p, () -> new Box3(m[0], m[1], m[2], s[0], s[1], s[2]));
	}

	private static Box box(Map<String, Object> o, String p) {
		Box3 b = box3(o, p);
		return guard(p, () -> new Box(b, enumOf(o, p, "role", Role.class), enumOf(o, p, "layer", Layer.class), integer(o, p, "storey")));
	}

	private static Violation violation(Map<String, Object> o, String p) {
		return guard(p, () -> new Violation(string(o, p, "invariant"), stringOrNull(o, p, "building"), string(o, p, "message"),
				list(o, p, "cells", IrReader::box3)));
	}

	private static PlacedBuilding building(Map<String, Object> o, String p) {
		int[] origin = ints(o, p, "origin", 3);
		String sp = p + ".shell";
		BuildingIr shell = shell(obj(req(o, p, "shell"), sp), sp);
		return guard(p, () -> new PlacedBuilding(string(o, p, "id"), strings(o, p, "lots"), origin[0], origin[1], origin[2],
				integer(o, p, "rotation"), shell, list(o, p, "siteBoxes", IrReader::box)));
	}

	private static BuildingIr shell(Map<String, Object> o, String p) {
		String spp = p + ".spec";
		Map<String, Object> so = obj(req(o, p, "spec"), spp);
		int gv = integer(so, spp, "genVersion");
		int maxHeight = gv >= 2 ? integer(so, spp, "maxHeight") : BuildingSpec.DEFAULT_MAX_HEIGHT;
		int streets = gv >= 2 ? integer(so, spp, "streets") : BuildingSpec.STREET_FRONT;
		BuildingSpec spec = guard(spp, () -> new BuildingSpec(gv, seed(so, spp, "specSeed"), string(so, spp, "style"),
				string(so, spp, "type"), integer(so, spp, "width"), integer(so, spp, "depth"), integer(so, spp, "storeys"), string(so, spp, "roof"),
				maxHeight, streets));
		String hash = string(o, p, "specHash");
		if (!hash.equals(SpecHash.of(spec))) {
			throw new IllegalArgumentException(p + ".specHash: '" + hash + "' does not match the spec (expected '" + SpecHash.of(spec) + "')");
		}
		String bp = p + ".bounds";
		Box3 bounds = box3(obj(req(o, p, "bounds"), bp), bp);
		return guard(p, () -> new BuildingIr(spec, bounds,
				list(o, p, "storeys", IrReader::storey),
				list(o, p, "volumes", IrReader::volume),
				list(o, p, "rooms", IrReader::room),
				list(o, p, "openings", IrReader::opening),
				list(o, p, "roofs", IrReader::roof),
				list(o, p, "boxes", IrReader::box),
				spec.v2Keys() ? list(o, p, "hooks", IrReader::hook) : List.of()));
	}

	private static Hook hook(Map<String, Object> o, String p) {
		int[] pos = ints(o, p, "pos", 3);
		return guard(p, () -> new Hook(string(o, p, "id"), string(o, p, "kind"), string(o, p, "room"), pos[0], pos[1], pos[2],
				enumOf(o, p, "facing", Facing.class)));
	}

	private static Storey storey(Map<String, Object> o, String p) {
		return guard(p, () -> new Storey(integer(o, p, "index"), integer(o, p, "y"), integer(o, p, "height")));
	}

	private static Volume volume(Map<String, Object> o, String p) {
		Box3 b = box3(o, p);
		return guard(p, () -> new Volume(string(o, p, "id"), b, stringOrNull(o, p, "parent")));
	}

	private static Room room(Map<String, Object> o, String p) {
		return guard(p, () -> new Room(string(o, p, "id"), integer(o, p, "storey"), string(o, p, "volume"),
				list(o, p, "parts", IrReader::box3), string(o, p, "use")));
	}

	private static Opening opening(Map<String, Object> o, String p) {
		Box3 b = box3(o, p);
		return guard(p, () -> new Opening(string(o, p, "id"), enumOf(o, p, "kind", OpeningKind.class), b,
				enumOf(o, p, "facing", Facing.class), string(o, p, "from"), string(o, p, "to")));
	}

	private static Roof roof(Map<String, Object> o, String p) {
		String ra = stringOrNull(o, p, "ridgeAxis");
		Axis axis = null;
		if (ra != null) {
			for (Axis a : Axis.values()) if (a.json().equals(ra)) axis = a;
			if (axis == null) throw new IllegalArgumentException(p + ".ridgeAxis: unknown axis '" + ra + "'");
		}
		Axis fa = axis;
		return guard(p, () -> new Roof(string(o, p, "form"), string(o, p, "volume"), fa, integer(o, p, "baseY"), integer(o, p, "rise"), integer(o, p, "run")));
	}
}
