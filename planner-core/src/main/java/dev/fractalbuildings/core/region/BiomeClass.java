package dev.fractalbuildings.core.region;

import dev.fractalbuildings.core.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Biome to city-likelihood multiplier and palette group. The numbers live in the resource
 * {@code region/biome-classes.json} (values from spike S4); water and peak biomes are recognised by rule.
 * Ids may be namespaced; only the path is looked at.
 */
public final class BiomeClass {
	private BiomeClass() {}

	private record Entry(double multiplier, String group) {}

	private static final Entry DEFAULT;
	private static final Map<String, Entry> TABLE = new TreeMap<>();

	static {
		try (InputStream in = BiomeClass.class.getResourceAsStream("/region/biome-classes.json")) {
			if (in == null) throw new IllegalStateException("missing resource region/biome-classes.json");
			@SuppressWarnings("unchecked")
			Map<String, Object> root = (Map<String, Object>) JsonReader.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			DEFAULT = entry(root.get("default"));
			@SuppressWarnings("unchecked")
			Map<String, Object> biomes = (Map<String, Object>) root.get("biomes");
			for (var e : biomes.entrySet()) TABLE.put(e.getKey(), entry(e.getValue()));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Entry entry(Object o) {
		List<?> l = (List<?>) o;
		return new Entry(((Number) l.get(0)).doubleValue(), (String) l.get(1));
	}

	/** The id without its namespace. */
	public static String path(String id) {
		int c = id.indexOf(':');
		return c < 0 ? id : id.substring(c + 1);
	}

	public static boolean isWater(String id) {
		String b = path(id);
		return b.endsWith("ocean") || b.endsWith("river") || b.equals("mushroom_fields");
	}

	public static boolean isPeak(String id) {
		String b = path(id);
		return b.equals("jagged_peaks") || b.equals("frozen_peaks") || b.equals("stony_peaks");
	}

	/** 0 for water and peaks; 1 for the best city land down to 0.25 for swamps and shores; 0.4 if unlisted. */
	public static double multiplier(String id) {
		if (isWater(id) || isPeak(id)) return 0;
		return TABLE.getOrDefault(path(id), DEFAULT).multiplier();
	}

	/** Coarse palette group; a settlement "straddles" when two groups each cover a fifth of it. */
	public static String group(String id) {
		if (isWater(id)) return "water";
		if (isPeak(id)) return "mountain";
		return TABLE.getOrDefault(path(id), DEFAULT).group();
	}
}
