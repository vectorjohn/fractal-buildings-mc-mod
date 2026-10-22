package dev.fractalbuildings.core.settlement;

import static dev.fractalbuildings.core.json.JsonWriter.array;
import static dev.fractalbuildings.core.json.JsonWriter.ints;
import static dev.fractalbuildings.core.json.JsonWriter.quote;
import static dev.fractalbuildings.core.json.JsonWriter.str;

import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.json.JsonWriter;
import java.util.List;
import java.util.Map;

/** Writes and reads {@code fb-district/1} (docs/IR.md "Settlement plans"). Same plan gives byte-identical text. */
public final class DistrictJson {
	private DistrictJson() {}

	public static String write(DistrictPlan d) {
		StringBuilder sb = new StringBuilder("{\n");
		sb.append("  \"format\": ").append(quote(DistrictPlan.FORMAT)).append(",\n");
		sb.append("  \"planVersion\": ").append(SettlementPlan.PLAN_VERSION).append(",\n");
		sb.append("  \"genVersion\": ").append(d.genVersion()).append(",\n");
		sb.append("  \"seed\": ").append(str(d.seed())).append(",\n");
		DistrictInfo i = d.district();
		sb.append("  \"district\": ").append(JsonWriter.object().text("id", i.id()).num("ix", i.ix()).num("iz", i.iz()).num("x", i.x()).num("z", i.z())
				.num("w", i.w()).num("d", i.d()).num("quarter", i.quarter()).num("density", i.density()).build()).append(",\n");
		sb.append("  \"streets\": ").append(JsonOut.lines(d.streets(), s -> JsonWriter.object().text("id", s.id()).text("kind", s.kind().json)
				.num("width", s.width()).raw("points", JsonOut.pts(s.points())).raw("y", ints(s.y())).build(), 2)).append(",\n");
		sb.append("  \"plazas\": ").append(JsonOut.lines(d.plazas(), p -> JsonWriter.object().text("id", p.id()).text("kind", p.kind()).num("x", p.x())
				.num("z", p.z()).num("w", p.w()).num("d", p.d()).num("y", p.y()).build(), 2)).append(",\n");
		sb.append("  \"lots\": ").append(JsonOut.lines(d.lots(), DistrictJson::lot, 2)).append(",\n");
		sb.append("  \"scene\": ").append(d.scene() == null ? "null" : quote(d.scene())).append(",\n");
		sb.append("  \"violations\": ").append(JsonOut.lines(d.violations(), JsonOut::violation, 2)).append("\n}\n");
		return sb.toString();
	}

	private static String lot(LotBrief l) {
		JsonWriter w = JsonWriter.object().text("id", l.id()).num("x", l.x()).num("z", l.z()).num("w", l.w()).num("d", l.d()).text("front", l.front().json())
				.text("type", l.type()).text("style", l.style()).raw("storeys", ints(l.minStoreys(), l.maxStoreys())).num("streets", l.streets())
				.num("airRights", l.airRights()).num("frontY", l.frontY()).num("density", l.density()).text("status", l.status());
		if (l.walk() != null) w.raw("walk", ints(l.walk().base(), l.walk().prev(), l.walk().next())); // only the ring lots of a walled town have one
		return w.build();
	}

	/** Parses {@code fb-district/1}; also rejects a lot that lies outside its tile. */
	public static DistrictPlan read(String json) {
		Map<String, Object> o = Rd.root(json);
		String format = Rd.string(o, "$", "format");
		if (!DistrictPlan.FORMAT.equals(format)) {
			throw new IllegalArgumentException("$.format: unsupported format '" + format + "', expected '" + DistrictPlan.FORMAT + "'");
		}
		int pv = Rd.integer(o, "$", "planVersion");
		if (pv != SettlementPlan.PLAN_VERSION) throw new IllegalArgumentException("$.planVersion: unsupported " + pv + ", expected " + SettlementPlan.PLAN_VERSION);
		Map<String, Object> d = Rd.obj(Rd.req(o, "$", "district"), "$.district");
		String dp = "$.district";
		DistrictInfo info = Rd.guard(dp, () -> new DistrictInfo(Rd.string(d, dp, "id"), Rd.integer(d, dp, "ix"), Rd.integer(d, dp, "iz"), Rd.integer(d, dp, "x"),
				Rd.integer(d, dp, "z"), Rd.integer(d, dp, "w"), Rd.integer(d, dp, "d"), Rd.integer(d, dp, "quarter"), Rd.integer(d, dp, "density")));
		List<LotBrief> lots = Rd.list(o, "$", "lots", DistrictJson::lot);
		for (int k = 0; k < lots.size(); k++) {
			LotBrief l = lots.get(k);
			if (!info.rect().containsRect(l.x(), l.z(), l.w(), l.d())) {
				throw new IllegalArgumentException("$.lots[" + k + "]: lot " + l.id() + " lies outside its district tile " + info.id());
			}
		}
		return Rd.guard("$", () -> new DistrictPlan(Rd.integer(o, "$", "genVersion"), Rd.seed(o, "$", "seed"), info,
				Rd.list(o, "$", "streets", (s, p) -> Rd.guard(p, () -> new Street(Rd.string(s, p, "id"), Rd.enumOf(s, p, "kind", RoadKind::of),
						Rd.integer(s, p, "width"), Rd.points(s, p, "points"), Rd.ints(s, p, "y", -1)))),
				Rd.list(o, "$", "plazas", (s, p) -> Rd.guard(p, () -> new Plaza(Rd.string(s, p, "id"), Rd.string(s, p, "kind"), Rd.integer(s, p, "x"),
						Rd.integer(s, p, "z"), Rd.integer(s, p, "w"), Rd.integer(s, p, "d"), Rd.integer(s, p, "y")))),
				lots, Rd.stringOrNull(o, "$", "scene"), Rd.list(o, "$", "violations", Rd::violation)));
	}

	private static LotBrief lot(Map<String, Object> o, String p) {
		int[] st = Rd.ints(o, p, "storeys", 2);
		int[] wk = o.containsKey("walk") ? Rd.ints(o, p, "walk", 3) : null;
		dev.fractalbuildings.core.gen.Brief.Walk walk = wk == null ? null : Rd.guard(p, () -> new dev.fractalbuildings.core.gen.Brief.Walk(wk[0], wk[1], wk[2]));
		return Rd.guard(p, () -> new LotBrief(Rd.string(o, p, "id"), Rd.integer(o, p, "x"), Rd.integer(o, p, "z"), Rd.integer(o, p, "w"), Rd.integer(o, p, "d"),
				Rd.enumOf(o, p, "front", s -> {
					for (Facing f : Facing.values()) if (f.json().equals(s)) return f;
					throw new IllegalArgumentException("unknown front '" + s + "'");
				}), Rd.string(o, p, "type"), Rd.string(o, p, "style"), st[0], st[1], Rd.integer(o, p, "streets"), Rd.integer(o, p, "airRights"),
				Rd.integer(o, p, "frontY"), Rd.integer(o, p, "density"), Rd.string(o, p, "status"), walk));
	}
}
