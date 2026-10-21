package dev.fractalbuildings.core.settlement;

import static dev.fractalbuildings.core.json.JsonWriter.array;
import static dev.fractalbuildings.core.json.JsonWriter.ints;
import static dev.fractalbuildings.core.json.JsonWriter.quote;
import static dev.fractalbuildings.core.json.JsonWriter.str;

import dev.fractalbuildings.core.json.JsonWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Writes and reads {@code fb-settlement/1} (docs/IR.md "Settlement plans"). Same plan gives byte-identical text. */
public final class SettlementJson {
	private SettlementJson() {}

	public static String write(SettlementPlan s) {
		StringBuilder sb = new StringBuilder("{\n");
		sb.append("  \"format\": ").append(quote(SettlementPlan.FORMAT)).append(",\n");
		sb.append("  \"planVersion\": ").append(SettlementPlan.PLAN_VERSION).append(",\n");
		sb.append("  \"genVersion\": ").append(s.genVersion()).append(",\n");
		sb.append("  \"seed\": ").append(str(s.seed())).append(",\n");
		sb.append("  \"site\": ").append(site(s.site())).append(",\n");
		sb.append("  \"bounds\": ").append(JsonWriter.object().num("x", s.bounds().x()).num("z", s.bounds().z()).num("w", s.bounds().w())
				.num("d", s.bounds().d()).build()).append(",\n");
		sb.append("  \"coarse\": ").append(coarse(s.coarse())).append(",\n");
		sb.append("  \"quarters\": ").append(JsonOut.lines(s.quarters(), q -> JsonWriter.object().num("id", q.id()).text("kind", q.kind().json).build(), 2)).append(",\n");
		sb.append("  \"cells\": ").append(JsonOut.lines(s.cells(), c -> JsonWriter.object().num("cx", c.cx()).num("cz", c.cz())
				.raw("site", JsonOut.pt(c.site())).raw("polygon", JsonOut.pts(c.polygon())).build(), 2)).append(",\n");
		sb.append("  \"districts\": ").append(JsonOut.lines(s.districts(), SettlementJson::district, 2)).append(",\n");
		sb.append("  \"roads\": ").append(JsonOut.lines(s.roads(), SettlementJson::road, 2)).append(",\n");
		sb.append("  \"gates\": ").append(JsonOut.lines(s.gates(), SettlementJson::gate, 2)).append(",\n");
		sb.append("  \"landmarks\": ").append(JsonOut.lines(s.landmarks(), l -> JsonWriter.object().text("id", l.id()).text("kind", l.kind().json)
				.num("x", l.x()).num("z", l.z()).num("w", l.w()).num("d", l.d()).text("district", l.district()).build(), 2)).append(",\n");
		sb.append("  \"walls\": ").append(JsonOut.lines(s.walls(), w -> JsonWriter.object().text("id", w.id()).raw("points", JsonOut.pts(w.points()))
				.raw("closed", Boolean.toString(w.closed())).build(), 2)).append(",\n");
		sb.append("  \"violations\": ").append(JsonOut.lines(s.violations(), JsonOut::violation, 2)).append("\n}\n");
		return sb.toString();
	}

	private static String site(SettlementSite s) {
		return JsonWriter.object().text("id", s.id()).text("tier", s.tier().json).raw("centre", JsonOut.pt(s.centre())).num("radius", s.radius())
				.raw("claimant", ints(s.claimant().cx(), s.claimant().cz())).raw("cells", JsonOut.cells(s.cells())).build();
	}

	private static String coarse(CoarseRaster c) {
		List<String> biomes = new ArrayList<>();
		for (String b : c.biomes()) biomes.add(quote(b));
		String in = "    ";
		return "{\n" + in + "\"x0\": " + c.x0() + ", \"z0\": " + c.z0() + ", \"step\": " + c.step() + ", \"nx\": " + c.nx() + ", \"nz\": " + c.nz() + ",\n"
				+ in + "\"floorY\": " + ints(c.floorY()) + ",\n"
				+ in + "\"water\": " + ints(c.water()) + ",\n"
				+ in + "\"biome\": " + ints(c.biome()) + ",\n"
				+ in + "\"biomes\": " + array(biomes) + ",\n"
				+ in + "\"mask\": " + ints(c.mask()) + ",\n"
				+ in + "\"density\": " + ints(c.density()) + ",\n"
				+ in + "\"quarter\": " + ints(c.quarter()) + "\n  }";
	}

	private static String district(DistrictSummary d) {
		List<String> styles = new ArrayList<>();
		for (StyleWeight w : d.styles()) styles.add(JsonWriter.object().text("style", w.style()).num("weight", w.weight()).build());
		return JsonWriter.object().text("id", d.id()).num("ix", d.ix()).num("iz", d.iz()).num("x", d.x()).num("z", d.z()).num("w", d.w()).num("d", d.d())
				.raw("origin", ints(d.originChunkX(), d.originChunkZ())).num("quarter", d.quarter()).num("density", d.density())
				.raw("styles", array(styles)).num("lots", d.lots()).textOrNull("detail", d.detail()).build();
	}

	private static String road(Road r) {
		List<String> br = new ArrayList<>();
		for (int[] b : r.bridges()) br.add(ints(b[0], b[1]));
		return JsonWriter.object().text("id", r.id()).text("kind", r.kind().json).num("width", r.width()).raw("points", JsonOut.pts(r.points()))
				.raw("bridges", array(br)).build();
	}

	private static String gate(Gate g) {
		return JsonWriter.object().text("id", g.id()).text("kind", g.kind().json).num("x", g.x()).num("z", g.z())
				.raw("cells", g.cells().isEmpty() ? "null" : JsonOut.cells(g.cells())).textOrNull("road", g.road()).build();
	}

	// ---- reader ----

	public static SettlementPlan read(String json) {
		Map<String, Object> o = Rd.root(json);
		String format = Rd.string(o, "$", "format");
		if (!SettlementPlan.FORMAT.equals(format)) {
			throw new IllegalArgumentException("$.format: unsupported format '" + format + "', expected '" + SettlementPlan.FORMAT + "'");
		}
		int pv = Rd.integer(o, "$", "planVersion");
		if (pv != SettlementPlan.PLAN_VERSION) throw new IllegalArgumentException("$.planVersion: unsupported " + pv + ", expected " + SettlementPlan.PLAN_VERSION);
		SettlementSite site = site(Rd.obj(Rd.req(o, "$", "site"), "$.site"), "$.site");
		Map<String, Object> bo = Rd.obj(Rd.req(o, "$", "bounds"), "$.bounds");
		Rect bounds = Rd.guard("$.bounds", () -> new Rect(Rd.integer(bo, "$.bounds", "x"), Rd.integer(bo, "$.bounds", "z"),
				Rd.integer(bo, "$.bounds", "w"), Rd.integer(bo, "$.bounds", "d")));
		CoarseRaster coarse = coarse(Rd.obj(Rd.req(o, "$", "coarse"), "$.coarse"), "$.coarse");
		return Rd.guard("$", () -> new SettlementPlan(Rd.integer(o, "$", "genVersion"), Rd.seed(o, "$", "seed"), site, bounds, coarse,
				Rd.list(o, "$", "quarters", (q, p) -> Rd.guard(p, () -> new Quarter(Rd.integer(q, p, "id"), Rd.enumOf(q, p, "kind", QuarterKind::of)))),
				Rd.list(o, "$", "cells", (c, p) -> Rd.guard(p, () -> {
					int[] s = Rd.ints(c, p, "site", 2);
					return new CellOutline(Rd.integer(c, p, "cx"), Rd.integer(c, p, "cz"), new Pt(s[0], s[1]), Rd.points(c, p, "polygon"));
				})),
				Rd.list(o, "$", "districts", SettlementJson::district),
				Rd.list(o, "$", "roads", SettlementJson::road),
				Rd.list(o, "$", "gates", SettlementJson::gate),
				Rd.list(o, "$", "landmarks", (l, p) -> Rd.guard(p, () -> new Landmark(Rd.string(l, p, "id"), Rd.enumOf(l, p, "kind", LandmarkKind::of),
						Rd.integer(l, p, "x"), Rd.integer(l, p, "z"), Rd.integer(l, p, "w"), Rd.integer(l, p, "d"), Rd.string(l, p, "district")))),
				Rd.list(o, "$", "walls", (w, p) -> Rd.guard(p, () -> new Wall(Rd.string(w, p, "id"), Rd.points(w, p, "points"), Rd.bool(w, p, "closed")))),
				Rd.list(o, "$", "violations", Rd::violation)));
	}

	private static SettlementSite site(Map<String, Object> o, String p) {
		int[] c = Rd.ints(o, p, "centre", 2), cl = Rd.ints(o, p, "claimant", 2);
		return Rd.guard(p, () -> new SettlementSite(Rd.string(o, p, "id"), Rd.enumOf(o, p, "tier", Tier::of), new Pt(c[0], c[1]), Rd.integer(o, p, "radius"),
				new Cell(cl[0], cl[1]), Rd.items(o, p, "cells", Rd::cell)));
	}

	private static CoarseRaster coarse(Map<String, Object> o, String p) {
		return Rd.guard(p, () -> new CoarseRaster(Rd.integer(o, p, "x0"), Rd.integer(o, p, "z0"), Rd.integer(o, p, "step"), Rd.integer(o, p, "nx"),
				Rd.integer(o, p, "nz"), Rd.ints(o, p, "floorY", -1), Rd.ints(o, p, "water", -1), Rd.ints(o, p, "biome", -1), Rd.strings(o, p, "biomes"),
				Rd.ints(o, p, "mask", -1), Rd.ints(o, p, "density", -1), Rd.ints(o, p, "quarter", -1)));
	}

	private static DistrictSummary district(Map<String, Object> o, String p) {
		int[] origin = Rd.ints(o, p, "origin", 2);
		return Rd.guard(p, () -> new DistrictSummary(Rd.string(o, p, "id"), Rd.integer(o, p, "ix"), Rd.integer(o, p, "iz"), Rd.integer(o, p, "x"),
				Rd.integer(o, p, "z"), Rd.integer(o, p, "w"), Rd.integer(o, p, "d"), origin[0], origin[1], Rd.integer(o, p, "quarter"),
				Rd.integer(o, p, "density"), Rd.list(o, p, "styles", (s, q) -> Rd.guard(q, () -> new StyleWeight(Rd.string(s, q, "style"), Rd.integer(s, q, "weight")))),
				Rd.integer(o, p, "lots"), Rd.stringOrNull(o, p, "detail")));
	}

	private static Road road(Map<String, Object> o, String p) {
		return Rd.guard(p, () -> new Road(Rd.string(o, p, "id"), Rd.enumOf(o, p, "kind", RoadKind::of), Rd.integer(o, p, "width"), Rd.points(o, p, "points"),
				Rd.items(o, p, "bridges", (v, q) -> Rd.intsOf(v, q, 2))));
	}

	private static Gate gate(Map<String, Object> o, String p) {
		Object c = Rd.req(o, p, "cells");
		List<Cell> cells = c == null ? List.of() : Rd.items(o, p, "cells", Rd::cell);
		return Rd.guard(p, () -> new Gate(Rd.string(o, p, "id"), Rd.enumOf(o, p, "kind", GateKind::of), Rd.integer(o, p, "x"), Rd.integer(o, p, "z"), cells,
				Rd.stringOrNull(o, p, "road")));
	}
}
