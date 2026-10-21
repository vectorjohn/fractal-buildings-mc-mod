package dev.fractalbuildings.core.style;

import dev.fractalbuildings.core.gen.v1.style.Json;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.json.JsonReader;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.rand.Hash64;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Which style a place takes, from the biomes around it, blended across borders (Phase 5, 5.4).
 *
 * <p>{@link #weights} votes the biomes on a 16-block lattice inside a 48-block disk around the point
 * (about 29 samples), so a lot near a border draws from both sides in proportion to how much of its
 * surroundings lies on each. {@link #pick} is a hashed weighted draw from those weights, a pure
 * function of (seed, lot). The medieval style lists no biomes and is never chosen here (opt-in by
 * name). Pure and stateless apart from the probe.
 */
public final class StyleField {
	public static final int STEP = 16;
	public static final int RADIUS = 48;

	/** A style id and its vote count. */
	public record Weight(String style, int weight) {}

	private static volatile Map<String, String> fallback;
	private static volatile Map<String, String> byBiome;

	private final BiomeProbe biomes;
	private final long seed;

	public StyleField(BiomeProbe biomes, long seed) {
		this.biomes = biomes;
		this.seed = seed;
	}

	/** Style votes around (x, z), sorted by style id; never empty (all-water gives plain). */
	public List<Weight> weights(int x, int z) {
		TreeMap<String, Integer> votes = new TreeMap<>();
		int n = RADIUS / STEP;
		for (int i = -n; i <= n; i++)
			for (int j = -n; j <= n; j++) {
				if (i * i + j * j > n * n) continue;
				vote(votes, biomes.biome(x + i * STEP, z + j * STEP));
			}
		return toList(votes);
	}

	/** The style for a lot at (lotX, lotZ): hashed weighted draw over {@link #weights}. */
	public String pick(int lotX, int lotZ) {
		List<Weight> w = weights(lotX, lotZ);
		int total = 0;
		for (Weight e : w) total += e.weight();
		int r = Hash64.below(Hash64.hash(seed, "lot-style", lotX, lotZ), total);
		for (Weight e : w) {
			r -= e.weight();
			if (r < 0) return e.style();
		}
		return w.get(w.size() - 1).style();
	}

	/** Most common style over the tile's samples on the world 16-lattice; ties go to the lower id. */
	public String dominant(int minX, int minZ, int sizeX, int sizeZ) {
		TreeMap<String, Integer> votes = new TreeMap<>();
		int x0 = Math.floorDiv(minX + STEP - 1, STEP) * STEP, z0 = Math.floorDiv(minZ + STEP - 1, STEP) * STEP;
		for (int x = x0; x < minX + sizeX; x += STEP)
			for (int z = z0; z < minZ + sizeZ; z += STEP) vote(votes, biomes.biome(x, z));
		if (votes.isEmpty()) vote(votes, biomes.biome(minX + sizeX / 2, minZ + sizeZ / 2));
		String best = null;
		int bw = -1;
		for (Weight e : toList(votes)) if (e.weight() > bw) { best = e.style(); bw = e.weight(); }
		return best;
	}

	/** The style a biome votes for, or null for water (no vote). Unlisted biomes are plain. */
	public static String styleOf(String biome) {
		if (isWater(biome)) return null;
		String s = byBiome().get(biome);
		return s != null ? s : StyleLibrary.FALLBACK;
	}

	static boolean isWater(String b) { return b.endsWith("ocean") || b.endsWith("river"); }

	private static void vote(Map<String, Integer> votes, String biome) {
		String s = styleOf(biome);
		if (s != null) votes.merge(s, 1, Integer::sum);
	}

	private static List<Weight> toList(TreeMap<String, Integer> votes) {
		if (votes.isEmpty()) return List.of(new Weight(StyleLibrary.FALLBACK, 1));
		return votes.entrySet().stream().map(e -> new Weight(e.getKey(), e.getValue())).toList();
	}

	private static Map<String, String> byBiome() {
		Map<String, String> m = byBiome;
		if (m == null) {
			synchronized (StyleField.class) {
				if (byBiome == null) {
					TreeMap<String, String> t = new TreeMap<>(fallback());
					// style lists take precedence over the fallback file
					for (String id : StyleLibrary.ids())
						for (String b : StyleLibrary.get(id).biomes()) t.put(b, firstClaim(b));
					byBiome = java.util.Collections.unmodifiableMap(t);
				}
				m = byBiome;
			}
		}
		return m;
	}

	private static String firstClaim(String biome) {
		for (String id : StyleLibrary.ids()) if (StyleLibrary.get(id).biomes().contains(biome)) return id;
		throw new IllegalStateException(biome);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> fallback() {
		Map<String, String> m = fallback;
		if (m == null) {
			try (InputStream in = StyleField.class.getResourceAsStream("/styles/biome-fallback.json")) {
				if (in == null) throw new IllegalArgumentException("missing resource styles/biome-fallback.json");
				Map<String, Object> o = Json.obj(JsonReader.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)), "biome-fallback.json");
				TreeMap<String, String> t = new TreeMap<>();
				for (Map.Entry<String, Object> e : ((Map<String, Object>) o.get("map")).entrySet()) {
					String style = (String) e.getValue();
					StyleLibrary.get(style); // fails loudly on a typo
					t.put(e.getKey(), style);
				}
				fallback = m = java.util.Collections.unmodifiableMap(t);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return m;
	}
}
