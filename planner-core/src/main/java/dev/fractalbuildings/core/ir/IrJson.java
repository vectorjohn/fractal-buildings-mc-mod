package dev.fractalbuildings.core.ir;

import static dev.fractalbuildings.core.json.JsonWriter.array;
import static dev.fractalbuildings.core.json.JsonWriter.ints;
import static dev.fractalbuildings.core.json.JsonWriter.quote;
import static dev.fractalbuildings.core.json.JsonWriter.str;

import dev.fractalbuildings.core.json.JsonWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Writes a {@link SceneDoc} as {@code fb-scene/1} JSON; same document gives byte-identical text. See docs/IR.md. */
public final class IrJson {
	private IrJson() {}

	/** Parses {@code fb-scene/1} JSON (strict; errors name the JSON path). See {@link IrReader}. */
	public static SceneDoc read(String json) { return IrReader.read(json); }

	public static String write(SceneDoc d) {
		StringBuilder sb = new StringBuilder("{\n");
		sb.append("  \"format\": ").append(quote(SceneDoc.FORMAT)).append(",\n");
		sb.append("  \"genVersion\": ").append(d.genVersion()).append(",\n");
		sb.append("  \"seed\": ").append(str(d.seed())).append(",\n");
		sb.append("  \"terrain\": ").append(d.terrain() == null ? "null" : terrain(d.terrain())).append(",\n");
		sb.append("  \"lots\": ").append(lines(d.lots(), IrJson::lot, 2)).append(",\n");
		sb.append("  \"buildings\": ").append(lines(d.buildings(), IrJson::building, 2)).append(",\n");
		if (!d.ground().isEmpty()) sb.append("  \"ground\": ").append(lines(d.ground(), IrJson::box, 2)).append(",\n");
		sb.append("  \"violations\": ").append(lines(d.violations(), IrJson::violation, 2)).append("\n}\n");
		return sb.toString();
	}

	/** Elements one per line at the given indent of the enclosing key; {@code []} when empty. */
	private static <T> String lines(List<T> items, Function<T, String> f, int indent) {
		if (items.isEmpty()) return "[]";
		String pad = " ".repeat(indent + 2);
		List<String> out = new ArrayList<>();
		for (T t : items) out.add(pad + f.apply(t));
		return "[\n" + String.join(",\n", out) + "\n" + " ".repeat(indent) + "]";
	}

	private static String terrain(TerrainPatch t) {
		JsonWriter o = JsonWriter.object().num("x0", t.x0()).num("z0", t.z0()).num("nx", t.nx()).num("nz", t.nz())
				.raw("floorY", ints(t.floorYs()));
		if (t.waters() != null) o.raw("water", ints(t.waters()));
		return o.build();
	}

	private static String lot(Lot l) {
		JsonWriter o = JsonWriter.object().text("id", l.id()).num("x", l.x()).num("z", l.z()).num("w", l.w()).num("d", l.d())
				.text("front", l.front().json());
		if (l.airRights() > 0) o.num("airRights", l.airRights()).num("frontY", l.frontY());
		return o.build();
	}

	private static String min(Box3 b) { return ints(b.x(), b.y(), b.z()); }

	private static String size(Box3 b) { return ints(b.sx(), b.sy(), b.sz()); }

	private static String part(Box3 b) { return JsonWriter.object().raw("min", min(b)).raw("size", size(b)).build(); }

	private static String box(Box b) {
		return JsonWriter.object().raw("min", min(b.box())).raw("size", size(b.box())).text("role", b.role().json())
				.text("layer", b.layer().json()).num("storey", b.storey()).build();
	}

	private static String violation(Violation v) {
		List<String> cells = new ArrayList<>();
		for (Box3 c : v.cells()) cells.add(part(c));
		return JsonWriter.object().text("invariant", v.invariant()).textOrNull("building", v.building())
				.text("message", v.message()).raw("cells", array(cells)).build();
	}

	/** Building: multi-line, 6-space indent (inside "buildings" at indent 4). */
	private static String building(PlacedBuilding b) {
		StringBuilder sb = new StringBuilder("{\n");
		sb.append("      ").append("\"id\": ").append(quote(b.id())).append(",\n");
		List<String> lots = new ArrayList<>();
		for (String l : b.lots()) lots.add(quote(l));
		sb.append("      \"lots\": ").append(array(lots)).append(",\n");
		sb.append("      \"origin\": ").append(ints(b.ox(), b.oy(), b.oz())).append(",\n");
		sb.append("      \"rotation\": ").append(b.rotation()).append(",\n");
		sb.append("      \"shell\": ").append(shell(b.shell())).append(",\n");
		sb.append("      \"siteBoxes\": ").append(lines(b.siteBoxes(), IrJson::box, 6)).append("\n    }");
		return sb.toString();
	}

	private static String shell(BuildingIr s) {
		String in = "        ";
		StringBuilder sb = new StringBuilder("{\n");
		sb.append(in).append("\"spec\": ").append(SpecHash.fill(JsonWriter.object(), s.spec()).build()).append(",\n");
		sb.append(in).append("\"specHash\": ").append(quote(SpecHash.of(s.spec()))).append(",\n");
		sb.append(in).append("\"bounds\": ").append(part(s.bounds())).append(",\n");
		sb.append(in).append("\"storeys\": ").append(lines(s.storeys(), IrJson::storey, 8)).append(",\n");
		sb.append(in).append("\"volumes\": ").append(lines(s.volumes(), IrJson::volume, 8)).append(",\n");
		sb.append(in).append("\"rooms\": ").append(lines(s.rooms(), IrJson::room, 8)).append(",\n");
		sb.append(in).append("\"openings\": ").append(lines(s.openings(), IrJson::opening, 8)).append(",\n");
		sb.append(in).append("\"roofs\": ").append(lines(s.roofs(), IrJson::roof, 8)).append(",\n");
		sb.append(in).append("\"boxes\": ").append(lines(s.boxes(), IrJson::box, 8));
		if (s.spec().v2Keys()) sb.append(",\n").append(in).append("\"hooks\": ").append(lines(s.hooks(), IrJson::hook, 8));
		sb.append("\n      }");
		return sb.toString();
	}

	private static String hook(Hook h) {
		return JsonWriter.object().text("id", h.id()).text("kind", h.kind()).text("room", h.room())
				.raw("pos", ints(h.x(), h.y(), h.z())).text("facing", h.facing().json()).build();
	}

	private static String storey(Storey s) {
		return JsonWriter.object().num("index", s.index()).num("y", s.y()).num("height", s.height()).build();
	}

	private static String volume(Volume v) {
		return JsonWriter.object().text("id", v.id()).raw("min", min(v.box())).raw("size", size(v.box()))
				.textOrNull("parent", v.parent()).build();
	}

	private static String room(Room r) {
		List<String> parts = new ArrayList<>();
		for (Box3 p : r.parts()) parts.add(part(p));
		return JsonWriter.object().text("id", r.id()).num("storey", r.storey()).text("volume", r.volume())
				.text("use", r.use()).raw("parts", array(parts)).build();
	}

	private static String opening(Opening o) {
		return JsonWriter.object().text("id", o.id()).text("kind", o.kind().json()).raw("min", min(o.box()))
				.raw("size", size(o.box())).text("facing", o.facing().json()).text("from", o.from())
				.text("to", o.to()).build();
	}

	private static String roof(Roof r) {
		return JsonWriter.object().text("form", r.form()).text("volume", r.volume())
				.textOrNull("ridgeAxis", r.ridgeAxis() == null ? null : r.ridgeAxis().json())
				.num("baseY", r.baseY()).num("rise", r.rise()).num("run", r.run()).build();
	}
}
