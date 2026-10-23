package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.json.JsonReader;
import dev.fractalbuildings.core.json.JsonWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * The options of {@code config/fractalbuildings.json} and of a world's pinned copy (plan B6): the planner's {@link WorldConfig}
 * plus the two switches only the game reads. {@link #parse} never throws: a bad value is clamped or replaced by its default and
 * reported to {@code warn}.
 *
 * @param villagers whether settlements get villagers
 * @param vetoVanillaVillages whether vanilla villages are kept out of our settlements (B7)
 */
public record FbSettings(WorldConfig world, boolean villagers, boolean vetoVanillaVillages) {
	public static final double MAX_FREQUENCY = 10;
	public static final int MIN_RADIUS = 256, MAX_RADIUS = 16384;
	/** Cities wider than this take hours of chunk generation (ADR 0003, "Practical size limits"). */
	public static final int WARN_RADIUS = 4096;

	public static FbSettings defaults() { return new FbSettings(WorldConfig.defaults(), true, true); }

	public static FbSettings parse(String json, Consumer<String> warn) {
		FbSettings d = defaults();
		Object root;
		try {
			root = JsonReader.parse(json);
		} catch (RuntimeException e) {
			warn.accept("config is not valid JSON (" + e.getMessage() + "); using defaults");
			return d;
		}
		if (!(root instanceof Map<?, ?> m)) {
			warn.accept("config is not a JSON object; using defaults");
			return d;
		}
		WorldConfig w = d.world();
		double freq = num(m, "frequency", w.frequency(), warn);
		if (freq < 0 || freq > MAX_FREQUENCY) {
			double c = Math.max(0, Math.min(MAX_FREQUENCY, freq));
			warn.accept("frequency " + freq + " out of range 0.." + MAX_FREQUENCY + "; using " + c);
			freq = c;
		}
		int radius = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(num(m, "maxRadius", w.maxRadius(), warn))));
		int rc = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
		if (rc != radius) warn.accept("maxRadius " + radius + " out of range " + MIN_RADIUS + ".." + MAX_RADIUS + "; using " + rc);
		radius = rc;
		if (radius > WARN_RADIUS) warn.accept("maxRadius " + radius + " allows cities over " + 2 * WARN_RADIUS + " blocks across; they take hours of chunk generation");
		int cell = (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.round(num(m, "cellSize", w.cellSize(), warn))));
		int cc = Math.max(256, Math.min(65536, cell));
		if (cc != cell) warn.accept("cellSize " + cell + " out of range 256..65536; using " + cc);
		TreeSet<String> styles = new TreeSet<>();
		Object s = m.get("styles");
		if (s != null) {
			if (!(s instanceof List<?> l)) warn.accept("styles is not a list; enabling every style");
			else for (Object o : l) {
				if (o instanceof String id && StyleLibrary.ids().contains(id)) styles.add(id);
				else warn.accept("unknown style " + o + " ignored");
			}
			if (s instanceof List<?> l && !l.isEmpty() && styles.isEmpty()) warn.accept("no known style listed; enabling every style");
		}
		return new FbSettings(new WorldConfig(cc, freq, radius, styles, GeneratorV1.GEN_VERSION),
				bool(m, "villagers", d.villagers(), warn), bool(m, "vetoVanillaVillages", d.vetoVanillaVillages(), warn));
	}

	private static double num(Map<?, ?> m, String key, double def, Consumer<String> warn) {
		Object o = m.get(key);
		if (o == null) return def;
		if (o instanceof Number n && Double.isFinite(n.doubleValue())) return n.doubleValue();
		warn.accept(key + " is not a number; using " + def);
		return def;
	}

	private static boolean bool(Map<?, ?> m, String key, boolean def, Consumer<String> warn) {
		Object o = m.get(key);
		if (o == null) return def;
		if (o instanceof Boolean b) return b;
		warn.accept(key + " is not true or false; using " + def);
		return def;
	}

	/** The settings as the file's JSON; {@code parse(toJson())} gives them back. {@code styles} empty means every style. */
	public String toJson() {
		List<String> st = new ArrayList<>();
		for (String s : world.styles()) st.add(JsonWriter.quote(s));
		return JsonWriter.object()
				.raw("frequency", Double.toString(world.frequency()))
				.num("maxRadius", world.maxRadius())
				.raw("styles", JsonWriter.array(st))
				.raw("villagers", Boolean.toString(villagers))
				.raw("vetoVanillaVillages", Boolean.toString(vetoVanillaVillages))
				.num("cellSize", world.cellSize())
				.build();
	}
}
