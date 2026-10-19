package dev.fractalbuildings.core.gen.v1.style;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A style (a culture's rule parameters and palette, ADR 0001 B2: styles are JSON parameters), read
 * from {@code planner-core/src/main/resources/styles/<id>.json}, format {@code fb-style/1}
 * (docs/plans/phase4.md "Style files"). Each sub-record belongs to one pass and is owned by that
 * pass's step; the palette is read by the viewer (colours) and the mod (blocks, Phase 6).
 *
 * @param biomes biome ids this style is chosen for when a request's style is {@code auto}
 * @param palette role to an ordered 3-5 block gradient (block ids, for the mod)
 * @param colours role to viewer colours ({@code "#rrggbb"}), parallel to {@code palette}
 */
public record StyleRules(String id, List<String> biomes, MassingStyle massing, RoofStyle roof, FacadeStyle facade,
		InteriorStyle interior, Map<String, List<String>> palette, Map<String, List<String>> colours) {
	public static final String FORMAT = "fb-style/1";

	public StyleRules {
		Objects.requireNonNull(id);
		biomes = List.copyOf(biomes);
	}

	/** Builds a style from its (already merged) JSON object. */
	public static StyleRules from(String id, Map<String, Object> o) {
		String p = "styles/" + id + ".json";
		String format = Json.string(o, "format", null, p);
		if (!FORMAT.equals(format)) throw new IllegalArgumentException(p + ": format must be " + FORMAT + ", got " + format);
		String declared = Json.string(o, "id", null, p);
		if (!id.equals(declared)) throw new IllegalArgumentException(p + ": id '" + declared + "' does not match the file name");
		return new StyleRules(id,
				Json.strings(o.get("biomes"), p + ".biomes"),
				MassingStyle.from(Json.obj(o, "massing", p), p + ".massing"),
				RoofStyle.from(Json.obj(o, "roof", p), p + ".roof"),
				FacadeStyle.from(Json.obj(o, "facade", p), p + ".facade"),
				InteriorStyle.from(Json.obj(o, "interior", p), p + ".interior"),
				Json.lists(o, "palette", p),
				Json.lists(o, "colours", p));
	}
}
