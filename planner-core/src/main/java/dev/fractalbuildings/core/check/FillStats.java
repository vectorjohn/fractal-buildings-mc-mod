package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.json.JsonWriter;
import dev.fractalbuildings.core.settlement.CityPlanner;
import dev.fractalbuildings.core.settlement.CoarseRaster;
import dev.fractalbuildings.core.settlement.DistrictInfo;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.settlement.Plaza;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.Rect;
import dev.fractalbuildings.core.settlement.RoadKind;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Street;
import dev.fractalbuildings.core.settlement.Tier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * How full a planned settlement is (METROFILL M0, DS-47 "Dense cores are filled"): one raster of the site disk (road, built lot,
 * unbuilt lot, plaza) measured over dry columns, by density band and by ring; how much of the street edge has a lot or plaza behind
 * it; street length by kind (each street clipped to the tile that lists it, since neighbouring tiles list the streets of their margin
 * too); empty slivers between near-parallel roads; dead ends. Report only: {@link #checks(Tier)} are listed as warnings until the
 * last METROFILL step makes them violations. Port of the architect's prototype
 * ({@code docs/review/reports/metro-sparse-architect/fill-metrics.js}); deviations: every row and column is scanned (the prototype
 * samples every 4th), a plaza between two roads is filled, not a gap, and the street length is clipped per tile.
 *
 * <p>Pure function of the plans, the site and the coarse raster (water and density come from it, as in the prototype).
 */
public final class FillStats {
	public static final String CORE_FILL = "core-fill", ROAD_SLIVER = "road-sliver", ROAD_HIERARCHY = "road-hierarchy";
	public static final List<String> IDS = List.of(CORE_FILL, ROAD_SLIVER, ROAD_HIERARCHY);

	/** Density bands (DS-47: "density 70 and up" is the core). */
	public static final int CORE_DENSITY = 70, MIDDLE_DENSITY = 50;
	/** A core with less land than this (columns) is too small to judge. */
	public static final int MIN_CORE_LAND = 20_000;
	/** DS-47 fill targets: the core is at most this share empty (%), and at least this share of its street edge has a lot or plaza behind it. */
	public static final double MAX_CORE_EMPTY_PCT = 15, MIN_CORE_FRONTAGE_PCT = 80;
	/** DS-47: arterial plus main streets are at most this share of street length (%). */
	public static final double MAX_ARTERIAL_MAIN_PCT = 30;
	/** A gap between two roads this short or shorter with nothing built in it is a sliver; a connected run of them at least {@link #SLIVER_LENGTH} long is one sliver. */
	public static final int SLIVER_GAP = 12, SLIVER_LENGTH = 32;
	/** Rings, as shares of the site radius. */
	static final double[] RINGS = {0, 0.25, 0.5, 0.75, 1.0000001};
	public static final String[] RING_NAMES = {"0-0.25", "0.25-0.5", "0.5-0.75", "0.75-1"};
	public static final String[] BAND_NAMES = {"core", "middle", "edge"};
	static final String[] KINDS = {"arterial", "main", "street", "quay", "lane", "path"};

	private static final byte NONE = 0, ROAD = 1, LOT = 2, PLAZA = 3, UNBUILT = 4;

	/** Dry columns in the site disk by what covers them; {@code edge} is the non-road columns next to a road, {@code edgeFilled} those with a lot or plaza. */
	public record Zone(String name, long land, long road, long lot, long plaza, long unbuilt, long edge, long edgeFilled) {
		public long empty() { return land - road - lot - plaza - unbuilt; }
		public double pct(long n) { return land == 0 ? 0 : 100.0 * n / land; }
		public double roadPct() { return pct(road); }
		public double lotPct() { return pct(lot); }
		public double plazaPct() { return pct(plaza); }
		public double unbuiltPct() { return pct(unbuilt); }
		public double emptyPct() { return pct(empty()); }
		/** Share of the street edge with a lot or plaza behind it; 100 when there is no edge. */
		public double frontagePct() { return edge == 0 ? 100 : 100.0 * edgeFilled / edge; }
	}

	/** One sliver: a connected run of empty gaps between roads, with its bounding box and the density at its middle. */
	public record Sliver(Rect box, long area, int density) {}

	public final String site;
	public final Tier tier;
	public final int radius;
	public final Zone all;
	public final SortedMap<String, Zone> bands, rings;
	/** Street edge by the kind of the road beside it: {filled, total}. */
	public final SortedMap<String, long[]> frontageByKind;
	/** Centre-line length by kind, clipped to each listing tile. */
	public final SortedMap<String, Double> lengthByKind;
	public final List<Sliver> slivers;
	public final long sliverArea;
	/** Sliver columns by the kinds of the two roads either side ("arterial/main", kinds in alphabetical order), over the columns of the slivers. */
	public final SortedMap<String, Long> sliverPairs;
	/** Built lots and density by district id ({built, density}); the lot-loss guard compares two runs. */
	public final SortedMap<String, int[]> districtLots;
	/** All road-to-road gaps in rows and columns, and those that are empty and at most {@link #SLIVER_GAP} long. */
	public final long gaps, emptyShortGaps;
	public final int deadEnds;
	public final SortedMap<String, Integer> deadEndsByKind;
	public final int blockLots;
	/** Median lot depth (blocks from the front line) by type, over built lots whose middle lies in the core. */
	public final SortedMap<String, Integer> coreLotDepth;
	/** Built lots by type in the core. */
	public final SortedMap<String, Integer> coreLotCount;

	private FillStats(String site, Tier tier, int radius, Zone all, SortedMap<String, Zone> bands, SortedMap<String, Zone> rings, SortedMap<String, long[]> frontageByKind,
			SortedMap<String, Double> lengthByKind, List<Sliver> slivers, long sliverArea, SortedMap<String, Long> sliverPairs, SortedMap<String, int[]> districtLots, long gaps, long emptyShortGaps, int deadEnds, SortedMap<String, Integer> deadEndsByKind,
			int blockLots, SortedMap<String, Integer> coreLotDepth, SortedMap<String, Integer> coreLotCount) {
		this.site = site;
		this.tier = tier;
		this.radius = radius;
		this.all = all;
		this.bands = bands;
		this.rings = rings;
		this.frontageByKind = frontageByKind;
		this.lengthByKind = lengthByKind;
		this.slivers = slivers;
		this.sliverArea = sliverArea;
		this.sliverPairs = sliverPairs;
		this.districtLots = districtLots;
		this.gaps = gaps;
		this.emptyShortGaps = emptyShortGaps;
		this.deadEnds = deadEnds;
		this.deadEndsByKind = deadEndsByKind;
		this.blockLots = blockLots;
		this.coreLotDepth = coreLotDepth;
		this.coreLotCount = coreLotCount;
	}

	public double streetLength() { double t = 0; for (double v : lengthByKind.values()) t += v; return t; }

	/** Arterial plus main as a share of street length (%). */
	public double arterialMainPct() {
		double t = streetLength();
		return t == 0 ? 0 : 100.0 * (lengthByKind.getOrDefault("arterial", 0.0) + lengthByKind.getOrDefault("main", 0.0)) / t;
	}

	/** Empty gaps of at most {@link #SLIVER_GAP} among all road-to-road gaps (%). */
	public double emptyShortGapPct() { return gaps == 0 ? 0 : 100.0 * emptyShortGaps / gaps; }

	public Zone core() { return bands.get("core"); }

	public static FillStats of(CityPlanner.City city) {
		return of(city.districts().values(), city.plan().site(), city.plan().coarse());
	}

	public static FillStats of(Collection<DistrictPlan> districts, SettlementSite site, CoarseRaster coarse) {
		return new Builder(districts, site, coarse).run();
	}

	/**
	 * The report-only checks. Nothing below a city is checked. {@code core-fill}: the core's empty share or frontage misses the
	 * DS-47 targets. {@code road-sliver}: one per sliver whose middle lies in the core. {@code road-hierarchy}: arterial plus main is
	 * over {@link #MAX_ARTERIAL_MAIN_PCT} of the street length.
	 */
	public List<Violation> checks(Tier t) {
		List<Violation> out = new ArrayList<>();
		if (!t.atLeast(Tier.CITY)) return out;
		Zone c = core();
		if (c != null && c.land() >= MIN_CORE_LAND && (c.emptyPct() > MAX_CORE_EMPTY_PCT || c.frontagePct() < MIN_CORE_FRONTAGE_PCT)) {
			out.add(new Violation(CORE_FILL, null, String.format(Locale.ROOT, "core (density %d+) is %.1f%% empty (limit %.0f) and %.1f%% of its street edge is built (at least %.0f)",
					CORE_DENSITY, c.emptyPct(), MAX_CORE_EMPTY_PCT, c.frontagePct(), MIN_CORE_FRONTAGE_PCT), List.of()));
		}
		for (Sliver s : slivers) {
			if (s.density() < CORE_DENSITY) continue;
			Rect b = s.box();
			out.add(new Violation(ROAD_SLIVER, null, "empty strip of " + s.area() + " columns between parallel roads at " + b.x() + "," + b.z() + " " + b.w() + "x" + b.d(),
					List.of(new Box3(b.x(), 0, b.z(), b.w(), 1, b.d()))));
		}
		if (arterialMainPct() > MAX_ARTERIAL_MAIN_PCT) {
			out.add(new Violation(ROAD_HIERARCHY, null, String.format(Locale.ROOT, "arterial plus main is %.1f%% of street length (limit %.0f)", arterialMainPct(), MAX_ARTERIAL_MAIN_PCT), List.of()));
		}
		return out;
	}

	/** The stats as one JSON object with sorted keys (counts, plus the derived percentages). */
	public String toJson() {
		JsonWriter w = JsonWriter.object();
		w.num("blockLots", blockLots);
		w.raw("bands", zonesJson(bands));
		w.raw("coreLotCount", intMap(coreLotCount));
		w.raw("coreLotDepth", intMap(coreLotDepth));
		w.raw("deadEnds", Integer.toString(deadEnds));
		w.raw("deadEndsByKind", intMap(deadEndsByKind));
		w.raw("districtLots", districtLotsJson());
		w.raw("emptyShortGapPct", f1(emptyShortGapPct()));
		w.raw("frontageByKind", frontageJson());
		w.num("gaps", gaps);
		w.raw("lengthByKind", lengthJson());
		w.raw("arterialMainPct", f1(arterialMainPct()));
		w.num("radius", radius);
		w.raw("rings", zonesJson(rings));
		w.text("site", site);
		w.raw("sliverArea", Long.toString(sliverArea));
		w.raw("sliverPairs", pairsJson());
		w.raw("slivers", Integer.toString(slivers.size()));
		w.text("tier", tier.json);
		w.raw("total", zoneJson(all));
		return w.build();
	}

	private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }

	private static String intMap(SortedMap<String, Integer> m) {
		List<String> parts = new ArrayList<>();
		m.forEach((k, v) -> parts.add(JsonWriter.quote(k) + ": " + v));
		return "{" + String.join(", ", parts) + "}";
	}

	private String pairsJson() {
		List<String> parts = new ArrayList<>();
		sliverPairs.forEach((k, v) -> parts.add(JsonWriter.quote(k) + ": " + v));
		return "{" + String.join(", ", parts) + "}";
	}

	private String districtLotsJson() {
		List<String> parts = new ArrayList<>();
		districtLots.forEach((k, v) -> parts.add(JsonWriter.quote(k) + ": [" + v[0] + ", " + v[1] + "]"));
		return "{" + String.join(", ", parts) + "}";
	}

	/** Districts of a site JSON object (as {@link #toJson} wrote it) by id: {built lots, density}; empty when the file has none. */
	@SuppressWarnings("unchecked")
	public static SortedMap<String, int[]> parseDistrictLots(Object siteJson) {
		TreeMap<String, int[]> out = new TreeMap<>();
		Object m = siteJson instanceof java.util.Map<?, ?> s ? s.get("districtLots") : null;
		if (m instanceof java.util.Map<?, ?> dm) {
			for (java.util.Map.Entry<?, ?> e : dm.entrySet()) {
				List<Object> a = (List<Object>) e.getValue();
				out.put((String) e.getKey(), new int[] {((Number) a.get(0)).intValue(), ((Number) a.get(1)).intValue()});
			}
		}
		return out;
	}

	/** DS-47 guard: districts of density {@value #LOSS_DENSITY} and up (either run) whose built lot count fell, as "id before -> after (density)". A road removal must never cause one. */
	public static List<String> lotLosses(SortedMap<String, int[]> before, SortedMap<String, int[]> after) {
		List<String> out = new ArrayList<>();
		before.forEach((id, b) -> {
			int[] a = after.get(id);
			if (a == null) return;
			if (Math.max(a[1], b[1]) >= LOSS_DENSITY && a[0] < b[0]) out.add(id + " " + b[0] + " -> " + a[0] + " (density " + b[1] + ")");
		});
		return out;
	}

	/** Density from which a lower lot count in a district counts as a loss. */
	public static final int LOSS_DENSITY = 50;

	private String lengthJson() {
		List<String> parts = new ArrayList<>();
		lengthByKind.forEach((k, v) -> parts.add(JsonWriter.quote(k) + ": " + Math.round(v)));
		return "{" + String.join(", ", parts) + "}";
	}

	private String frontageJson() {
		List<String> parts = new ArrayList<>();
		frontageByKind.forEach((k, v) -> parts.add(JsonWriter.quote(k) + ": [" + v[0] + ", " + v[1] + "]"));
		return "{" + String.join(", ", parts) + "}";
	}

	private static String zonesJson(SortedMap<String, Zone> zs) {
		List<String> parts = new ArrayList<>();
		zs.forEach((k, z) -> parts.add(JsonWriter.quote(k) + ": " + zoneJson(z)));
		return "{" + String.join(", ", parts) + "}";
	}

	private static String zoneJson(Zone z) {
		return JsonWriter.compactObject().num("edge", z.edge()).num("edgeFilled", z.edgeFilled()).num("land", z.land()).num("lot", z.lot()).num("plaza", z.plaza())
				.num("road", z.road()).num("unbuilt", z.unbuilt()).raw("emptyPct", f1(z.emptyPct())).raw("frontagePct", f1(z.frontagePct())).raw("lotPct", f1(z.lotPct()))
				.raw("plazaPct", f1(z.plazaPct())).raw("roadPct", f1(z.roadPct())).build();
	}

	// ---------------------------------------------------------------------------------------------------------------------

	private static final class Acc {
		long land, road, lot, plaza, unbuilt, edge, edgeFilled;
		Zone zone(String name) { return new Zone(name, land, road, lot, plaza, unbuilt, edge, edgeFilled); }
	}

	private static final class Builder {
		final Collection<DistrictPlan> districts;
		final SettlementSite site;
		final CoarseRaster coarse;
		final int[] density;
		final int cx, cz, r, x0, z0, w, h;
		final byte[] g;
		final byte[] kind; // road kind index + 1 of the first street to claim a column
		boolean[] land;

		Builder(Collection<DistrictPlan> districts, SettlementSite site, CoarseRaster coarse) {
			this.districts = districts;
			this.site = site;
			this.coarse = coarse;
			this.density = coarse.density();
			cx = site.centre().x();
			cz = site.centre().z();
			r = site.radius();
			x0 = cx - r;
			z0 = cz - r;
			w = 2 * r + 1;
			h = 2 * r + 1;
			g = new byte[w * h];
			kind = new byte[w * h];
		}

		int kindIndex(RoadKind k) {
			String j = k.json;
			for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(j)) return i + 1;
			return 0;
		}

		void paint(Rect q, byte v, int kindIdx) {
			int xa = Math.max(q.x(), x0), xb = Math.min(q.x() + q.w(), x0 + w), za = Math.max(q.z(), z0), zb = Math.min(q.z() + q.d(), z0 + h);
			for (int z = za; z < zb; z++) {
				int row = (z - z0) * w - x0;
				for (int x = xa; x < xb; x++) {
					int k = row + x;
					if (v == ROAD) {
						if (kind[k] == 0) kind[k] = (byte) kindIdx;
						g[k] = ROAD;
					} else if (g[k] == NONE) g[k] = v;
				}
			}
		}

		/** Water as the coarse raster has it; outside the raster counts as water. */
		boolean water(int x, int z) {
			int i = Math.floorDiv(x - coarse.x0(), coarse.step()), j = Math.floorDiv(z - coarse.z0(), coarse.step());
			if (i < 0 || j < 0 || i >= coarse.nx() || j >= coarse.nz()) return true;
			return coarseWater[j * coarse.nx() + i] > 0;
		}

		int densityAt(int x, int z) {
			int i = Math.floorDiv(x - coarse.x0(), coarse.step()), j = Math.floorDiv(z - coarse.z0(), coarse.step());
			if (i < 0 || j < 0 || i >= coarse.nx() || j >= coarse.nz()) return 0;
			return density[j * coarse.nx() + i];
		}

		int[] coarseWater;

		static int band(int den) { return den >= CORE_DENSITY ? 0 : den >= MIDDLE_DENSITY ? 1 : 2; }

		static int ring(double rr) {
			int k = 0;
			while (rr >= RINGS[k + 1]) k++;
			return k;
		}

		FillStats run() {
			coarseWater = coarse.water();
			List<Street> streets = new ArrayList<>();
			for (DistrictPlan d : districts) {
				for (Street s : d.streets()) {
					streets.add(s);
					int ki = kindIndex(s.kind());
					for (Rect q : SettlementChecks.footprint(s)) paint(q, ROAD, ki);
				}
			}
			for (DistrictPlan d : districts) for (LotBrief l : d.lots()) paint(l.rect(), l.builds() ? LOT : UNBUILT, 0);
			for (DistrictPlan d : districts) for (Plaza p : d.plazas()) paint(new Rect(p.x(), p.z(), p.w(), p.d()), PLAZA, 0);

			land = new boolean[w * h];
			Acc[] bandAcc = {new Acc(), new Acc(), new Acc()}, ringAcc = {new Acc(), new Acc(), new Acc(), new Acc()};
			Acc total = new Acc();
			long rr2 = (long) r * r;
			for (int j = 0; j < h; j++) {
				for (int i = 0; i < w; i++) {
					int x = x0 + i, z = z0 + j;
					long dx = x - cx, dz = z - cz;
					if (dx * dx + dz * dz > rr2 || water(x, z)) continue;
					land[j * w + i] = true;
					Acc[] zs = {bandAcc[band(densityAt(x, z))], ringAcc[ring(Math.sqrt((double) (dx * dx + dz * dz)) / r)], total};
					byte v = g[j * w + i];
					for (Acc a : zs) {
						a.land++;
						switch (v) {
							case ROAD -> a.road++;
							case LOT -> a.lot++;
							case PLAZA -> a.plaza++;
							case UNBUILT -> a.unbuilt++;
							default -> {}
						}
					}
				}
			}
			// frontage: non-road dry columns beside a road
			TreeMap<String, long[]> byKind = new TreeMap<>();
			for (int j = 1; j < h - 1; j++) {
				for (int i = 1; i < w - 1; i++) {
					int k = j * w + i;
					if (!land[k] || g[k] == ROAD) continue;
					int kk = -1;
					for (int d : new int[] {1, -1, w, -w}) if (g[k + d] == ROAD) kk = kind[k + d];
					if (kk < 0) continue;
					int x = x0 + i, z = z0 + j;
					double rr = Math.sqrt((double) ((long) (x - cx) * (x - cx) + (long) (z - cz) * (z - cz))) / r;
					boolean filled = g[k] == LOT || g[k] == PLAZA;
					for (Acc a : new Acc[] {bandAcc[band(densityAt(x, z))], ringAcc[ring(rr)], total}) {
						a.edge++;
						if (filled) a.edgeFilled++;
					}
					long[] c = byKind.computeIfAbsent(kk == 0 ? "road" : KINDS[kk - 1], q -> new long[2]);
					c[1]++;
					if (filled) c[0]++;
				}
			}
			TreeMap<String, Zone> bands = new TreeMap<>(), rings = new TreeMap<>();
			for (int i = 0; i < 3; i++) bands.put(BAND_NAMES[i], bandAcc[i].zone(BAND_NAMES[i]));
			for (int i = 0; i < 4; i++) rings.put(RING_NAMES[i], ringAcc[i].zone(RING_NAMES[i]));

			TreeMap<String, Double> length = new TreeMap<>();
			for (DistrictPlan d : districts) {
				DistrictInfo di = d.district();
				for (Street s : d.streets()) {
					double len = 0;
					List<Pt> ps = s.points();
					for (int i = 1; i < ps.size(); i++) len += clipped(ps.get(i - 1), ps.get(i), di.x(), di.z(), di.x() + di.w(), di.z() + di.d());
					if (len > 0) length.merge(s.kind().json, len, Double::sum);
				}
			}

			long[] gapCount = new long[2];
			List<Sliver> slivers = new ArrayList<>();
			TreeMap<String, Long> pairs = new TreeMap<>();
			long sliverArea = sliverScan(gapCount, slivers, pairs);
			TreeMap<String, int[]> dl = new TreeMap<>();
			for (DistrictPlan d : districts) {
				int n = 0;
				for (LotBrief l : d.lots()) if (l.builds()) n++;
				dl.put(d.district().id(), new int[] {n, d.district().density()});
			}

			// dead ends
			TreeMap<String, Integer> deadByKind = new TreeMap<>();
			int dead = deadEnds(streets, deadByKind);

			int blocks = 0;
			TreeMap<String, List<Integer>> depths = new TreeMap<>();
			for (DistrictPlan d : districts) {
				for (LotBrief l : d.lots()) {
					if (!l.builds()) continue;
					if (l.type().equals("block")) blocks++;
					if (densityAt(l.x() + l.w() / 2, l.z() + l.d() / 2) >= CORE_DENSITY) {
						int depth = l.front() == Facing.NORTH || l.front() == Facing.SOUTH ? l.d() : l.w();
						depths.computeIfAbsent(l.type(), q -> new ArrayList<>()).add(depth);
					}
				}
			}
			TreeMap<String, Integer> med = new TreeMap<>(), cnt = new TreeMap<>();
			depths.forEach((t, v) -> {
				v.sort(null);
				med.put(t, v.get((v.size() - 1) / 2));
				cnt.put(t, v.size());
			});
			return new FillStats(site.id(), site.tier(), r, total.zone("all"), bands, rings, byKind, length, slivers, sliverArea, pairs, dl, gapCount[0], gapCount[1], dead, deadByKind, blocks, med, cnt);
		}

		/** Length of the part of segment a-b inside [xa, xb) x [za, zb). */
		static double clipped(Pt a, Pt b, int xa, int za, int xb, int zb) {
			double t0 = 0, t1 = 1, dx = b.x() - a.x(), dz = b.z() - a.z();
			double[] p = {-dx, dx, -dz, dz};
			double eps = 1e-9;
			double[] q = {a.x() - xa, xb - eps - a.x(), a.z() - za, zb - eps - a.z()};
			for (int i = 0; i < 4; i++) {
				if (p[i] == 0) {
					if (q[i] < 0) return 0;
				} else {
					double t = q[i] / p[i];
					if (p[i] < 0) t0 = Math.max(t0, t);
					else t1 = Math.min(t1, t);
				}
			}
			return t1 <= t0 ? 0 : (t1 - t0) * Math.sqrt(dx * dx + dz * dz);
		}

		/**
		 * Row and column scan for road-to-road gaps; marks the cells of empty gaps of at most {@link #SLIVER_GAP}, then collects
		 * connected marked components whose bounding box is at least {@link #SLIVER_LENGTH} on its long side.
		 */
		static String pairName(int p) {
			int lo = p / 8, hi = p % 8;
			String a = lo == 0 ? "road" : KINDS[lo - 1], b = hi == 0 ? "road" : KINDS[hi - 1];
			return a.compareTo(b) <= 0 ? a + "/" + b : b + "/" + a;
		}

		long sliverScan(long[] gapCount, List<Sliver> out, TreeMap<String, Long> pairs) {
			boolean[] mark = new boolean[w * h];
			byte[] pair = new byte[w * h];
			for (int dir = 0; dir < 2; dir++) {
				int na = dir == 0 ? h : w, nb = dir == 0 ? w : h;
				int sa = dir == 0 ? w : 1, sb = dir == 0 ? 1 : w;
				for (int a = 0; a < na; a++) {
					int last = -1;
					boolean filled = false;
					for (int b = 0; b < nb; b++) {
						int k = a * sa + b * sb;
						if (!land[k]) { last = -1; continue; }
						if (g[k] == ROAD) {
							if (last >= 0 && b - last > 1) {
								gapCount[0]++;
								int len = b - last - 1;
								if (!filled && len <= SLIVER_GAP) {
									gapCount[1]++;
									for (int q = last + 1; q < b; q++) { mark[a * sa + q * sb] = true; pair[a * sa + q * sb] = (byte) (Math.min(kind[a * sa + last * sb], kind[k]) * 8 + Math.max(kind[a * sa + last * sb], kind[k])); }
								}
							}
							last = b;
							filled = false;
						} else if (g[k] == LOT || g[k] == PLAZA) filled = true;
					}
				}
			}
			long area = 0;
			boolean[] seen = new boolean[w * h];
			ArrayDeque<Integer> stack = new ArrayDeque<>();
			for (int k0 = 0; k0 < mark.length; k0++) {
				if (!mark[k0] || seen[k0]) continue;
				stack.push(k0);
				seen[k0] = true;
				int minI = w, maxI = -1, minJ = h, maxJ = -1;
				long n = 0;
				TreeMap<String, Long> comp = new TreeMap<>();
				while (!stack.isEmpty()) {
					int k = stack.pop();
					n++;
					comp.merge(pairName(pair[k]), 1L, Long::sum);
					int i = k % w, j = k / w;
					minI = Math.min(minI, i);
					maxI = Math.max(maxI, i);
					minJ = Math.min(minJ, j);
					maxJ = Math.max(maxJ, j);
					if (i > 0 && mark[k - 1] && !seen[k - 1]) { seen[k - 1] = true; stack.push(k - 1); }
					if (i < w - 1 && mark[k + 1] && !seen[k + 1]) { seen[k + 1] = true; stack.push(k + 1); }
					if (j > 0 && mark[k - w] && !seen[k - w]) { seen[k - w] = true; stack.push(k - w); }
					if (j < h - 1 && mark[k + w] && !seen[k + w]) { seen[k + w] = true; stack.push(k + w); }
				}
				int bw = maxI - minI + 1, bd = maxJ - minJ + 1;
				if (Math.max(bw, bd) >= SLIVER_LENGTH) {
					Rect box = new Rect(x0 + minI, z0 + minJ, bw, bd);
					out.add(new Sliver(box, n, densityAt(box.x() + bw / 2, box.z() + bd / 2)));
					area += n;
					comp.forEach((p, c) -> pairs.merge(p, c, Long::sum));
				}
			}
			return area;
		}

		/** A street end (in the dry disk) with no other street's centre line within width/2 + 2. Streets with the same geometry in two tiles count once. */
		int deadEnds(List<Street> all, TreeMap<String, Integer> byKind) {
			List<Street> ss = new ArrayList<>();
			Set<String> seen = new java.util.TreeSet<>();
			for (Street s : all) if (seen.add(s.kind() + "/" + s.width() + "/" + s.points())) ss.add(s);
			int cell = 64;
			java.util.TreeMap<Long, List<Integer>> buckets = new java.util.TreeMap<>();
			for (int n = 0; n < ss.size(); n++) {
				Street s = ss.get(n);
				int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
				for (Pt p : s.points()) { minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x()); minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z()); }
				int m = s.width() + 4;
				for (int bz = Math.floorDiv(minZ - m, cell); bz <= Math.floorDiv(maxZ + m, cell); bz++) {
					for (int bx = Math.floorDiv(minX - m, cell); bx <= Math.floorDiv(maxX + m, cell); bx++) buckets.computeIfAbsent(((long) bz << 32) ^ (bx & 0xffffffffL), q -> new ArrayList<>()).add(n);
				}
			}
			int dead = 0;
			for (int n = 0; n < ss.size(); n++) {
				Street s = ss.get(n);
				for (Pt p : List.of(s.points().get(0), s.points().get(s.points().size() - 1))) {
					int i = p.x() - x0, j = p.z() - z0;
					if (i < 0 || j < 0 || i >= w || j >= h || !land[j * w + i]) continue;
					boolean near = false;
					List<Integer> cand = buckets.getOrDefault(((long) Math.floorDiv(p.z(), cell) << 32) ^ (Math.floorDiv(p.x(), cell) & 0xffffffffL), List.of());
					for (int o : cand) {
						if (o == n) continue;
						Street t = ss.get(o);
						List<Pt> ps = t.points();
						for (int q = 1; q < ps.size() && !near; q++) near = segDist(p, ps.get(q - 1), ps.get(q)) <= t.width() / 2.0 + 2;
						if (near) break;
					}
					if (!near) {
						dead++;
						byKind.merge(s.kind().json, 1, Integer::sum);
					}
				}
			}
			return dead;
		}

		static double segDist(Pt p, Pt a, Pt b) {
			double vx = b.x() - a.x(), vz = b.z() - a.z(), l2 = vx * vx + vz * vz;
			double t = l2 == 0 ? 0 : ((p.x() - a.x()) * vx + (p.z() - a.z()) * vz) / l2;
			t = Math.max(0, Math.min(1, t));
			double ex = p.x() - (a.x() + t * vx), ez = p.z() - (a.z() + t * vz);
			return Math.sqrt(ex * ex + ez * ez);
		}
	}
}
