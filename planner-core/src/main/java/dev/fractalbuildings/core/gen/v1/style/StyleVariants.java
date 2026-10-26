package dev.fractalbuildings.core.gen.v1.style;

import dev.fractalbuildings.core.grammar.Choice;
import dev.fractalbuildings.core.json.JsonReader;
import dev.fractalbuildings.core.rand.Hash64;
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
 * Style variants ({@code styles/variants.json}, format {@code fb-style-variants/1}): a request style
 * (a base such as {@code plain}) is swapped for a variant style once the spec knows the building
 * type, storey count, biome and tier. Rules are tried in file order, the first match wins, and the
 * variant is a weighted draw seeded by the spec seed, so it depends only on the world seed and the
 * position. A style that is itself a pick target of any rule is never remapped again. Every pick must
 * be a loaded style with no biomes (variants extend {@code tall}, never a biome style). Loaded once.
 */
public final class StyleVariants {
	private StyleVariants() {}

	/** One rule. Absent lists are null (match anything); {@code pick} is in sorted id order. */
	record Rule(String id, List<String> styles, List<String> types, int minStoreys, int maxStoreys, List<String> biomes,
			List<String> tiers, Map<String, Integer> pick) {
		boolean matches(String base, String type, int storeys, String biome, String tier) {
			if (styles != null && !styles.contains(base)) return false;
			if (types != null && !types.contains(type)) return false;
			if (storeys < minStoreys || storeys > maxStoreys) return false;
			if (biomes != null && (biome == null || !biomes.contains(biome))) return false;
			if (tiers != null && (tier == null || !tiers.contains(tier))) return false;
			return true;
		}
	}

	private static volatile List<Rule> rules;

	/**
	 * The effective style id for a request: the first matching rule's weighted pick, else {@code base}.
	 * {@code biome} and {@code tier} may be null (rules that name biomes or tiers then never match).
	 */
	public static String select(String base, String type, int storeys, String biome, String tier, long specSeed) {
		return select(rules(), base, type, storeys, biome, tier, specSeed);
	}

	static String select(List<Rule> rs, String base, String type, int storeys, String biome, String tier, long specSeed) {
		for (Rule r : rs) if (r.pick.containsKey(base)) return base; // never remap twice
		for (Rule r : rs) {
			if (!r.matches(base, type, storeys, biome, tier)) continue;
			List<Choice.Option> opts = new ArrayList<>();
			for (Map.Entry<String, Integer> e : r.pick.entrySet()) opts.add(Choice.opt(e.getKey(), e.getValue()));
			return Choice.pick(Hash64.hash(specSeed, "style-variant"), r.id, opts);
		}
		return base;
	}

	/** Rule ids in order (for tests and tools). */
	public static List<String> ruleIds() {
		List<String> out = new ArrayList<>();
		for (Rule r : rules()) out.add(r.id);
		return out;
	}

	/** Every style id that some rule can pick, sorted. */
	public static List<String> pickTargets() {
		TreeSet<String> out = new TreeSet<>();
		for (Rule r : rules()) out.addAll(r.pick.keySet());
		return List.copyOf(out);
	}

	private static List<Rule> rules() {
		List<Rule> r = rules;
		if (r == null) {
			synchronized (StyleVariants.class) {
				if (rules == null) rules = parse(resource());
				r = rules;
			}
		}
		return r;
	}

	/** Parses and validates a table; package-private so tests can feed bad ones. */
	static List<Rule> parse(String text) {
		String path = "styles/variants.json";
		Map<String, Object> o = Json.obj(JsonReader.parse(text), path);
		if (!"fb-style-variants/1".equals(Json.string(o, "format", null, path))) throw new IllegalArgumentException(path + ": format must be fb-style-variants/1");
		if (!(o.get("rules") instanceof List<?> arr)) throw new IllegalArgumentException(path + ": rules must be an array");
		List<Rule> out = new ArrayList<>();
		TreeSet<String> ids = new TreeSet<>();
		for (int i = 0; i < arr.size(); i++) {
			String p = path + ".rules[" + i + "]";
			Map<String, Object> ro = Json.obj(arr.get(i), p);
			String id = Json.string(ro, "id", null, p);
			if (id == null || !ids.add(id)) throw new IllegalArgumentException(p + ": id missing or duplicate");
			Map<String, Integer> w = Json.weights(ro, "pick", null, p);
			if (w == null || w.isEmpty()) throw new IllegalArgumentException(p + ": pick is empty");
			Map<String, Integer> pick = new TreeMap<>();
			for (Map.Entry<String, Integer> e : w.entrySet()) {
				if (e.getValue() <= 0) throw new IllegalArgumentException(p + ".pick." + e.getKey() + ": weight must be > 0");
				if (!StyleLibrary.ids().contains(e.getKey())) throw new IllegalArgumentException(p + ".pick: unknown style '" + e.getKey() + "'");
				if (!StyleLibrary.get(e.getKey()).biomes().isEmpty()) throw new IllegalArgumentException(p + ".pick: style '" + e.getKey() + "' has biomes; variants never replace a biome style");
				pick.put(e.getKey(), e.getValue());
			}
			out.add(new Rule(id, list(ro, "styles", p), list(ro, "types", p), Json.integer(ro, "minStoreys", 1, p),
					Json.integer(ro, "maxStoreys", 1000, p), list(ro, "biomes", p), list(ro, "tiers", p), java.util.Collections.unmodifiableMap(pick)));
		}
		return List.copyOf(out);
	}

	private static List<String> list(Map<String, Object> o, String key, String p) {
		return o.get(key) == null ? null : Json.strings(o.get(key), p + "." + key);
	}

	private static String resource() {
		try (InputStream in = StyleVariants.class.getResourceAsStream("/styles/variants.json")) {
			if (in == null) throw new IllegalArgumentException("missing resource styles/variants.json");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
