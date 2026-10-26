package dev.fractalbuildings.core.gen.v1.style;

import dev.fractalbuildings.core.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The styles shipped in {@code /styles/} on the classpath. {@code styles/index.txt} lists the ids, one
 * per line (union-merged in git, so parallel style steps each add a line); the order of the file
 * does not matter, ids are sorted. A style may name a parent with {@code "extends": "<id>"}
 * ({@link Json#merge}). Loaded once, immutable, safe on any thread.
 */
public final class StyleLibrary {
	private StyleLibrary() {}

	/** Request style that means "pick from the biome at the lot". */
	public static final String AUTO = "auto";
	/** Style used for a biome no style lists. */
	public static final String FALLBACK = "plain";

	private static volatile Map<String, StyleRules> styles;

	public static List<String> ids() { return List.copyOf(all().keySet()); }

	/** The style, or {@link IllegalArgumentException} naming the known ids. */
	public static StyleRules get(String id) {
		StyleRules s = all().get(id);
		if (s == null && id.startsWith("test-") && StyleLibrary.class.getResource("/styles/" + id + ".json") != null) return StyleRules.from(id, merged(id, new ArrayList<>())); // unindexed test-only styles (test classpath), never in ids()
		if (s == null) throw new IllegalArgumentException("unknown style '" + id + "', expected one of " + ids() + " or " + AUTO);
		return s;
	}

	/** The first style (by id) that lists the biome, else {@link #FALLBACK}. */
	public static StyleRules forBiome(String biome) {
		for (StyleRules s : all().values()) if (s.biomes().contains(biome)) return s;
		return get(FALLBACK);
	}

	private static Map<String, StyleRules> all() {
		Map<String, StyleRules> s = styles;
		if (s == null) {
			synchronized (StyleLibrary.class) {
				if (styles == null) styles = load();
				s = styles;
			}
		}
		return s;
	}

	private static Map<String, StyleRules> load() {
		TreeSet<String> ids = new TreeSet<>();
		for (String line : resource("index.txt").split("\n")) {
			String t = line.strip();
			if (!t.isEmpty() && !t.startsWith("#")) ids.add(t);
		}
		Map<String, StyleRules> out = new TreeMap<>();
		for (String id : ids) out.put(id, StyleRules.from(id, merged(id, new ArrayList<>())));
		return java.util.Collections.unmodifiableMap(out);
	}

	/** The style's JSON with its {@code extends} chain applied. */
	public static Map<String, Object> merged(String id, List<String> chain) {
		if (chain.contains(id)) throw new IllegalArgumentException("styles: extends cycle " + chain + " -> " + id);
		chain.add(id);
		Map<String, Object> o = Json.obj(JsonReader.parse(resource(id + ".json")), "styles/" + id + ".json");
		String parent = Json.string(o, "extends", null, "styles/" + id + ".json");
		if (parent == null) return o;
		Map<String, Object> child = new java.util.LinkedHashMap<>(o);
		child.remove("extends");
		return Json.merge(merged(parent, chain), child);
	}

	private static String resource(String name) {
		try (InputStream in = StyleLibrary.class.getResourceAsStream("/styles/" + name)) {
			if (in == null) throw new IllegalArgumentException("missing resource styles/" + name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
