package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A hand-laid settlement for the viewer steps and the format tests (docs/plans/phase5.md "SampleSettlement"):
 * {@link #sample()} is a town of 2 x 2 district tiles on synthetic terrain (a river west of the middle, plains west
 * and desert east of x = 160) with a bridged main road, a north-south street, a lane, a market square and 40 lots;
 * the first district also has a district scene (buildings from generator v1 with their briefs, road boxes, water).
 * {@link #big(int)} is a plain tile grid with many lots for viewer performance tests. Not a planner: the real
 * skeleton and district steps replace it, but the files keep the formats honest.
 */
public final class SampleSettlement {
	private SampleSettlement() {}

	public static final long SEED = 4242;
	public static final String SAMPLE_TERRAIN = "noise:7:64:3:64+river:128:12:4+biomes:split:160:plains:desert";

	/** A settlement, its districts by id (sorted) and the scenes by relative path. */
	public record Sample(SettlementPlan settlement, Map<String, DistrictPlan> districts, Map<String, SceneDoc> scenes) {
		public Sample {
			districts = new TreeMap<>(districts);
			scenes = new TreeMap<>(scenes);
		}

		/** Every file as relative path to text (settlement.json, districts/*.json, scenes/*.json). */
		public Map<String, String> files() {
			Map<String, String> out = new TreeMap<>();
			out.put("settlement.json", SettlementJson.write(settlement));
			for (DistrictPlan d : districts.values()) out.put("districts/" + d.district().id() + ".json", DistrictJson.write(d));
			for (Map.Entry<String, SceneDoc> e : scenes.entrySet()) out.put(e.getKey(), IrJson.write(e.getValue()));
			return out;
		}

		public void write(Path dir) throws IOException {
			for (Map.Entry<String, String> e : files().entrySet()) {
				Path p = dir.resolve(e.getKey());
				Files.createDirectories(p.getParent());
				Files.writeString(p, e.getValue(), StandardCharsets.UTF_8);
			}
		}
	}

	private static final int[] TILE_X = {0, 144, 272}, TILE_Z = {0, 160, 288};
	private static final int MAIN_Z = 140, STREET_X = 200, LANE_Z = 210;

	public static Sample sample() {
		ProbeSpecs.Probes pr = ProbeSpecs.parse(SAMPLE_TERRAIN);
		TerrainProbe terrain = pr.terrain();
		Pt centre = new Pt(136, 144);
		int radius = 200;
		List<Road> roads = List.of(
				new Road("r0_0_0", RoadKind.MAIN, 7, List.of(new Pt(0, MAIN_Z), new Pt(127, MAIN_Z), new Pt(140, MAIN_Z), new Pt(271, MAIN_Z)), List.of(new int[] {1, 2})),
				new Road("r0_0_1", RoadKind.STREET, 5, List.of(new Pt(STREET_X, 0), new Pt(STREET_X, 287)), List.of()),
				new Road("r0_0_2", RoadKind.LANE, 4, List.of(new Pt(4, LANE_Z), new Pt(196, LANE_Z)), List.of()));
		List<Quarter> quarters = List.of(new Quarter(0, QuarterKind.RESIDENTIAL), new Quarter(1, QuarterKind.MARKET), new Quarter(2, QuarterKind.WATERFRONT));
		int[][] quarterOf = {{2, 1}, {0, 0}}; // [iz][ix]
		Plaza plaza = new Plaza("p0", "market", 216, 90, 32, 28, terrain.surfaceY(232, 104));
		Landmark market = new Landmark("l0", LandmarkKind.MARKET, plaza.x(), plaza.z(), plaza.w(), plaza.d(), Ids.district(1, 0));

		// lots per tile: [ix][iz] -> list of {x, z, w, d, front ordinal}
		Map<String, List<int[]>> lotRects = new TreeMap<>();
		int n = Facing.NORTH.ordinal(), s = Facing.SOUTH.ordinal();
		for (int x : new int[] {8, 28, 48, 68, 88, 108}) {
			add(lotRects, 0, 0, x, 123, s);
			add(lotRects, 0, 0, x, 144, n);
		}
		for (int x : new int[] {152, 172, 208, 228, 248}) {
			add(lotRects, 1, 0, x, 123, s);
			add(lotRects, 1, 0, x, 144, n);
		}
		for (int x : new int[] {8, 28, 48, 68, 88, 108, 128}) {
			add(lotRects, 0, 1, x, 194, s);
			add(lotRects, 0, 1, x, 212, n);
		}
		for (int x : new int[] {148, 168}) {
			add(lotRects, 1, 1, x, 194, s);
			add(lotRects, 1, 1, x, 212, n);
		}

		Map<String, DistrictPlan> districts = new TreeMap<>();
		List<DistrictSummary> summaries = new ArrayList<>();
		Map<String, SceneDoc> scenes = new TreeMap<>();
		for (int iz = 0; iz < 2; iz++) {
			for (int ix = 0; ix < 2; ix++) {
				Rect tile = new Rect(TILE_X[ix], TILE_Z[iz], TILE_X[ix + 1] - TILE_X[ix], TILE_Z[iz + 1] - TILE_Z[iz]);
				String id = Ids.district(ix, iz);
				int q = quarterOf[iz][ix];
				int density = densityAt(tile.x() + tile.w() / 2, tile.z() + tile.d() / 2, centre, radius, 65);
				List<StyleWeight> styles = styleShares(pr, tile);
				List<LotBrief> lots = new ArrayList<>();
				int k = 0;
				for (int[] r : lotRects.getOrDefault(ix + "," + iz, List.of())) {
					lots.add(lot(id + ".l" + k++, r, pr, quarters.get(q).kind(), centre, radius));
				}
				List<Street> streets = new ArrayList<>();
				for (Road road : roads) streets.addAll(clip(road, tile, terrain));
				List<Plaza> plazas = plaza.x() >= tile.x() && plaza.x() < tile.x() + tile.w() && plaza.z() >= tile.z() && plaza.z() < tile.z() + tile.d()
						? List.of(plaza) : List.of();
				String scenePath = ix == 0 && iz == 0 ? "scenes/" + id + ".json" : null;
				DistrictPlan dp = new DistrictPlan(GeneratorV1.GEN_VERSION, SEED, new DistrictInfo(id, ix, iz, tile.x(), tile.z(), tile.w(), tile.d(), q, density),
						streets, plazas, lots, scenePath, List.of());
				districts.put(id, dp);
				if (scenePath != null) scenes.put(scenePath, scene(dp, pr));
				summaries.add(new DistrictSummary(id, ix, iz, tile.x(), tile.z(), tile.w(), tile.d(), tile.x() / 16, tile.z() / 16, q, density, styles, lots.size(),
						"districts/" + id + ".json"));
			}
		}
		Rect bounds = new Rect(0, 0, 272, 288);
		SettlementSite site = new SettlementSite("s0", Tier.TOWN, centre, radius, new Cell(0, 0), List.of(new Cell(0, 0)));
		CoarseRaster coarse = coarse(bounds, 32, pr, centre, radius, 65, quarters.size(), summaries);
		List<Gate> gates = List.of(new Gate("g0", GateKind.EXIT, 0, MAIN_Z, List.of(), "r0_0_0"), new Gate("g1", GateKind.EXIT, 271, MAIN_Z, List.of(), "r0_0_0"));
		List<Pt> poly = List.of(new Pt(0, 0), new Pt(272, 0), new Pt(272, 288), new Pt(0, 288));
		SettlementPlan plan = new SettlementPlan(GeneratorV1.GEN_VERSION, SEED, site, bounds, coarse, quarters, List.of(new CellOutline(0, 0, centre, poly)),
				summaries, roads, gates, List.of(market), List.of(), List.of());
		return new Sample(plan, districts, scenes);
	}

	/** A plain grid of {@code sizeBlocks} x {@code sizeBlocks} columns (rounded down to whole 128 tiles): 36 lots per tile, flat ground. */
	public static Sample big(int sizeBlocks) {
		int tiles = Math.max(1, sizeBlocks / 128);
		int size = tiles * 128;
		ProbeSpecs.Probes pr = ProbeSpecs.parse("flat:64");
		Pt centre = new Pt(size / 2, size / 2);
		int radius = size / 2;
		List<Quarter> quarters = List.of(new Quarter(0, QuarterKind.RESIDENTIAL), new Quarter(1, QuarterKind.DOWNTOWN));
		Map<String, DistrictPlan> districts = new TreeMap<>();
		List<DistrictSummary> summaries = new ArrayList<>();
		List<Road> roads = new ArrayList<>();
		for (int iz = 0; iz < tiles; iz++) {
			for (int ix = 0; ix < tiles; ix++) {
				int x = ix * 128, z = iz * 128;
				String id = Ids.district(ix, iz);
				int density = densityAt(x + 64, z + 64, centre, radius, 100);
				int q = density >= 60 ? 1 : 0;
				List<Street> streets = new ArrayList<>();
				for (int line : new int[] {0, 4}) {
					int c = line * 16 + 8;
					streets.add(new Street(id + ".e" + line, RoadKind.STREET, 5, List.of(new Pt(x, z + c), new Pt(x + 127, z + c)), new int[] {64, 64}));
					streets.add(new Street(id + ".n" + line, RoadKind.STREET, 5, List.of(new Pt(x + c, z), new Pt(x + c, z + 127)), new int[] {64, 64}));
				}
				List<LotBrief> lots = new ArrayList<>();
				for (int a : new int[] {1, 2, 3, 5, 6, 7}) {
					for (int b : new int[] {1, 2, 3, 5, 6, 7}) {
						int lx = x + a * 16 + 1, lz = z + b * 16 + 1;
						var pick = ClassTable.pick(SEED, lx, lz, density, quarters.get(q).kind(), 0, 14, 14);
						String type = pick.map(ClassTable.Pick::type).orElse("house");
						int mn = pick.map(ClassTable.Pick::minStoreys).orElse(1), mx = pick.map(ClassTable.Pick::maxStoreys).orElse(1);
						lots.add(new LotBrief(id + ".l" + lots.size(), lx, lz, 14, 14, Facing.values()[(a + b) & 3], type, "plain", mn, mx, 1, 0, 64, density,
								pick.isPresent() ? LotBrief.BUILD : "empty:no-fit"));
					}
				}
				districts.put(id, new DistrictPlan(GeneratorV1.GEN_VERSION, SEED, new DistrictInfo(id, ix, iz, x, z, 128, 128, q, density), streets, List.of(), lots, null, List.of()));
				summaries.add(new DistrictSummary(id, ix, iz, x, z, 128, 128, x / 16, z / 16, q, density, List.of(new StyleWeight("plain", 100)), lots.size(),
						"districts/" + id + ".json"));
			}
		}
		int[][] ends = {{0, 8, size - 1, 8}, {8, 0, 8, size - 1}};
		for (int i = 0; i < ends.length; i++) {
			roads.add(new Road("r0_0_" + i, RoadKind.ARTERIAL, 9, List.of(new Pt(ends[i][0], ends[i][1]), new Pt(ends[i][2], ends[i][3])), List.of()));
		}
		Rect bounds = new Rect(0, 0, size, size);
		SettlementSite site = new SettlementSite("s0", tiles >= 4 ? Tier.CITY : Tier.TOWN, centre, radius, new Cell(0, 0), List.of(new Cell(0, 0)));
		CoarseRaster coarse = coarse(bounds, 64, pr, centre, radius, 100, quarters.size(), summaries);
		SettlementPlan plan = new SettlementPlan(GeneratorV1.GEN_VERSION, SEED, site, bounds, coarse, quarters,
				List.of(new CellOutline(0, 0, centre, List.of(new Pt(0, 0), new Pt(size, 0), new Pt(size, size), new Pt(0, size)))), summaries, roads, List.of(),
				List.of(), List.of(), List.of());
		return new Sample(plan, districts, Map.of());
	}

	private static void add(Map<String, List<int[]>> m, int ix, int iz, int x, int z, int front) {
		m.computeIfAbsent(ix + "," + iz, k -> new ArrayList<>()).add(new int[] {x, z, 16, 14, front});
	}

	/** Linear falloff from the centre: cap at the centre, 0 at the radius. */
	static int densityAt(int x, int z, Pt centre, int radius, int cap) {
		long dx = x - centre.x(), dz = z - centre.z();
		long dist = isqrt(dx * dx + dz * dz);
		return (int) Math.max(0, cap * (radius - dist) / radius);
	}

	private static long isqrt(long n) {
		long r = (long) StrictMath.sqrt((double) n);
		while (r * r > n) r--;
		while ((r + 1) * (r + 1) <= n) r++;
		return r;
	}

	private static List<StyleWeight> styleShares(ProbeSpecs.Probes pr, Rect tile) {
		TreeMap<String, Integer> count = new TreeMap<>();
		int total = 0;
		for (int z = tile.z() + 8; z < tile.z() + tile.d(); z += 16) {
			for (int x = tile.x() + 8; x < tile.x() + tile.w(); x += 16) {
				count.merge(StyleLibrary.forBiome(pr.biome().biome(x, z)).id(), 1, Integer::sum);
				total++;
			}
		}
		List<StyleWeight> out = new ArrayList<>();
		for (Map.Entry<String, Integer> e : count.entrySet()) out.add(new StyleWeight(e.getKey(), Math.max(1, e.getValue() * 100 / total)));
		return out;
	}

	private static LotBrief lot(String id, int[] r, ProbeSpecs.Probes pr, QuarterKind quarter, Pt centre, int radius) {
		int x = r[0], z = r[1], w = r[2], d = r[3];
		Facing front = Facing.values()[r[4]];
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		for (int px = x; px < x + w; px++) for (int pz = z; pz < z + d; pz++) {
			int fy = pr.terrain().floorY(px, pz);
			min = Math.min(min, fy);
			max = Math.max(max, fy);
		}
		int density = densityAt(x + w / 2, z + d / 2, centre, radius, 65);
		String style = StyleLibrary.forBiome(pr.biome().biome(x + w / 2, z + d / 2)).id();
		int fx = x + w / 2 + (front == Facing.EAST ? w / 2 : front == Facing.WEST ? -w / 2 - 1 : 0);
		int fz = z + d / 2 + (front == Facing.SOUTH ? d / 2 : front == Facing.NORTH ? -d / 2 - 1 : 0);
		int frontY = pr.terrain().surfaceY(fx, fz);
		// the lot's width runs along the front: these lots front north or south, so w x d is as the table wants it
		var pick = ClassTable.pick(SEED, x, z, density, quarter, max - min, w, d);
		if (pick.isEmpty()) return new LotBrief(id, x, z, w, d, front, "house", style, 1, 1, 1, 0, frontY, density, "empty:no-fit");
		var p = pick.get();
		return new LotBrief(id, x, z, w, d, front, p.type(), style, p.minStoreys(), p.maxStoreys(), 1, 0, frontY, density, LotBrief.BUILD);
	}

	/** The part of a road inside a tile (axis-aligned roads only): one street per contiguous piece. */
	static List<Street> clip(Road road, Rect tile, TerrainProbe terrain) {
		List<List<Pt>> pieces = new ArrayList<>();
		List<Pt> cur = null;
		for (int i = 1; i < road.points().size(); i++) {
			Pt a = road.points().get(i - 1), b = road.points().get(i);
			if (a.x() != b.x() && a.z() != b.z()) throw new IllegalArgumentException("clip: diagonal road " + road.id());
			int x0 = Math.max(Math.min(a.x(), b.x()), tile.x()), x1 = Math.min(Math.max(a.x(), b.x()), tile.x() + tile.w() - 1);
			int z0 = Math.max(Math.min(a.z(), b.z()), tile.z()), z1 = Math.min(Math.max(a.z(), b.z()), tile.z() + tile.d() - 1);
			if (x0 > x1 || z0 > z1) { cur = null; continue; }
			boolean forward = (b.x() - a.x()) + (b.z() - a.z()) > 0;
			Pt p = forward ? new Pt(x0, z0) : new Pt(x1, z1), q = forward ? new Pt(x1, z1) : new Pt(x0, z0);
			if (cur != null && cur.get(cur.size() - 1).equals(p)) {
				cur.add(q);
			} else {
				cur = new ArrayList<>(List.of(p, q));
				pieces.add(cur);
			}
		}
		List<Street> out = new ArrayList<>();
		for (int i = 0; i < pieces.size(); i++) {
			List<Pt> ps = pieces.get(i);
			int[] y = new int[ps.size()];
			for (int k = 0; k < y.length; k++) y[k] = terrain.surfaceY(ps.get(k).x(), ps.get(k).z());
			out.add(new Street(pieces.size() == 1 ? road.id() : road.id() + "_" + i, road.kind(), road.width(), ps, y));
		}
		return out;
	}

	/** A district scene: terrain with water, the buildings of the built lots (generator v1 with briefs), road boxes, invariants. */
	private static SceneDoc scene(DistrictPlan dp, ProbeSpecs.Probes pr) {
		Rect t = dp.district().rect();
		TerrainPatch patch = TerrainPatch.sampleWithWater(pr.terrain(), t.x(), t.z(), t.w(), t.d());
		List<Lot> lots = new ArrayList<>();
		List<PlacedBuilding> buildings = new ArrayList<>();
		GeneratorV1 gen = new GeneratorV1();
		for (LotBrief l : dp.lots()) {
			if (!l.builds()) continue;
			Lot lot = new Lot(l.id(), l.x(), l.z(), l.w(), l.d(), l.front());
			SceneDoc one = gen.generate(new GenRequest(SEED, lot, l.type(), l.style(), pr.terrain(), pr.biome(),
					new Brief(l.minStoreys(), l.maxStoreys(), l.streets(), l.airRights(), l.frontY())));
			PlacedBuilding b = one.buildings().get(0);
			lots.add(lot);
			buildings.add(new PlacedBuilding("b" + buildings.size(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), b.siteBoxes()));
		}
		List<Box> ground = new ArrayList<>();
		for (Street s : dp.streets()) roadBoxes(s, pr.terrain(), ground);
		SceneDoc draft = new SceneDoc(GeneratorV1.GEN_VERSION, SEED, patch, lots, buildings, ground, List.of());
		return new SceneDoc(GeneratorV1.GEN_VERSION, SEED, patch, lots, buildings, ground, Invariants.check(draft, pr.terrain()));
	}

	/** Road surface boxes of an axis-aligned street: runs of equal surface height along the centre line, {@code width} wide. */
	static void roadBoxes(Street s, TerrainProbe terrain, List<Box> out) {
		int w = s.width(), h = w / 2;
		for (int i = 1; i < s.points().size(); i++) {
			Pt a = s.points().get(i - 1), b = s.points().get(i);
			boolean alongX = a.z() == b.z();
			int from = alongX ? Math.min(a.x(), b.x()) : Math.min(a.z(), b.z()), to = alongX ? Math.max(a.x(), b.x()) : Math.max(a.z(), b.z());
			int runStart = from, runY = surface(terrain, alongX, from, a);
			for (int c = from + 1; c <= to + 1; c++) {
				int y = c <= to ? surface(terrain, alongX, c, a) : Integer.MIN_VALUE;
				if (y == runY) continue;
				int len = c - runStart;
				out.add(new Box(alongX ? new Box3(runStart, runY - 1, a.z() - h, len, 1, w) : new Box3(a.x() - h, runY - 1, runStart, w, 1, len),
						Role.ROAD, Layer.GROUND, Box.NO_STOREY));
				runStart = c;
				runY = y;
			}
		}
	}

	private static int surface(TerrainProbe t, boolean alongX, int c, Pt line) {
		return alongX ? t.surfaceY(c, line.z()) : t.surfaceY(line.x(), c);
	}

	static CoarseRaster coarse(Rect bounds, int step, ProbeSpecs.Probes pr, Pt centre, int radius, int cap, int quarters, List<DistrictSummary> districts) {
		int nx = (bounds.w() + step - 1) / step + 1, nz = (bounds.d() + step - 1) / step + 1;
		TreeSet<String> names = new TreeSet<>();
		for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) names.add(pr.biome().biome(bounds.x() + i * step, bounds.z() + j * step));
		List<String> biomes = new ArrayList<>(names);
		int n = nx * nz;
		int[] floorY = new int[n], water = new int[n], biome = new int[n], mask = new int[n], density = new int[n], quarter = new int[n];
		for (int j = 0; j < nz; j++) {
			for (int i = 0; i < nx; i++) {
				int x = bounds.x() + i * step, z = bounds.z() + j * step, k = j * nx + i;
				floorY[k] = pr.terrain().floorY(x, z);
				water[k] = pr.terrain().waterDepth(x, z);
				String b = pr.biome().biome(x, z);
				biome[k] = biomes.indexOf(b);
				density[k] = densityAt(x, z, centre, radius, cap);
				boolean wet = water[k] >= 2 || dev.fractalbuildings.core.probe.BiomeIds.isWater(b);
				mask[k] = wet ? CoarseRaster.WATER : density[k] > 0 ? CoarseRaster.URBAN : CoarseRaster.WILD;
				quarter[k] = -1;
				for (DistrictSummary d : districts) if (d.rect().contains(x, z)) quarter[k] = d.quarter();
			}
		}
		return new CoarseRaster(bounds.x(), bounds.z(), step, nx, nz, floorY, water, biome, biomes, mask, density, quarter);
	}
}
