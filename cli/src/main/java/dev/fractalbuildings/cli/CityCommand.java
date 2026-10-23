package dev.fractalbuildings.cli;

import dev.fractalbuildings.cli.Main.UsageException;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionPlanner;
import dev.fractalbuildings.core.region.RegionProbe;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.settlement.CityPlanner;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.DistrictScenes;
import dev.fractalbuildings.core.settlement.DistrictSummary;
import dev.fractalbuildings.core.settlement.Districts;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.settlement.QuarterKind;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import dev.fractalbuildings.core.settlement.StyleWeight;
import dev.fractalbuildings.core.style.StyleField;
import java.util.Optional;
import dev.fractalbuildings.core.settlement.CellGeometry;
import dev.fractalbuildings.core.settlement.FieldSiteView;
import dev.fractalbuildings.core.settlement.FixedSiteView;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.SettlementJson;
import dev.fractalbuildings.core.settlement.RegionView;
import dev.fractalbuildings.core.settlement.SettlementPlan;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Skeletons;
import dev.fractalbuildings.core.settlement.TerrainCrop;
import dev.fractalbuildings.core.settlement.Tier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * {@code city}: plans a settlement skeleton (SK; later steps add districts and scenes) and writes {@code settlement.json}
 * ({@code fb-settlement/1}, every member cell merged) and {@code terrain.json.gz} (the plan window's coarse terrain, for the
 * plan view's background). The site is either the region planner's decision for {@code --cell}, or given with {@code --site}.
 */
final class CityCommand {
	private CityCommand() {}

	/** Cells a {@code --site} may span (the join rule bounds real ones; this is a safety net for a mistyped radius). */
	/** A typo guard, not a resource limit (plan memory is bounded by the skeleton and region BoundedCaches): 64 cells is a maxRadius-4096 metropolis at S=1024 (plan Decision 3). */
	static final int MAX_CELLS = 64;

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("seed", "terrain", "cell", "site", "cell-size", "out-dir", "scene", "scenes"), Set.of("districts", "exterior", "interiors", "check", "roads"));
		long seed = o.longValue("seed");
		Path dir = Path.of(o.require("out-dir"));
		if (o.has("cell") == o.has("site")) throw new UsageException("give exactly one of --cell CX,CZ and --site X,Z,TIER,RADIUS");
		int cellSize = 1024;
		if (o.has("cell-size")) {
			try { cellSize = Integer.parseInt(o.get("cell-size", null)); } catch (NumberFormatException e) { throw new UsageException("bad --cell-size"); }
			if (cellSize < 256 || cellSize > 65536) throw new UsageException("--cell-size must be 256..65536");
		}
		ProbeSpecs.Probes p = Scenes.probes(o.get("terrain", "flat:64"));
		RegionField field = new RegionField(seed, cellSize);
		SettlementSite site;
		RegionView view;
		if (o.has("cell")) {
			int[] a = ints(o.get("cell", null), 2, "--cell CX,CZ");
			FieldRegionView fv = fieldView(field, p);
			view = new FieldSiteView(fv, field, o.flag("roads") ? roadNet(field, fv, p) : null);
			if (!fv.decidable(a[0], a[1])) throw new UsageException("cell " + a[0] + "," + a[1] + " cannot be decided from this terrain window (its probe reach leaves the recorded area); " + WINDOW_HINT);
			site = view.siteOf(new Cell(a[0], a[1])).orElseThrow(() -> new UsageException("cell " + a[0] + "," + a[1] + " belongs to no settlement"
					+ (fv.covers(a[0], a[1]) ? "" : " (the terrain window is smaller than the join rule reads around the cell, so a neighbour that would claim it may be out of view; " + WINDOW_HINT + ")")));
			if (!fv.covers(a[0], a[1]) && (site.cells().size() > 1 || site.tier().atLeast(Tier.CITY))) System.err.println("city: note: a large settlement just outside the recorded terrain cannot be seen from cell " + a[0] + "," + a[1] + ", so its join with the neighbours is only as exact as the window (" + WINDOW_HINT + ")");
		} else {
			site = fromSite(field, o.get("site", null));
			view = new FixedSiteView(site, field);
		}
		CoarseProbe coarse = CoarseProbe.of(p.terrain(), p.biome(), 32);

		boolean wantDistricts = o.flag("districts") || o.has("scene") || o.has("scenes") || o.flag("check");
		long t0 = System.nanoTime();
		SettlementPlan plan;
		CityPlanner.City city = null;
		try {
			if (wantDistricts) {
				city = CityPlanner.plan(new SkeletonSource.Direct(seed, view, site, coarse), coarse, p.terrain(), p.biome(), GeneratorV1.GEN_VERSION);
				plan = city.plan();
			} else {
				plan = Skeletons.plan(seed, view, site, coarse, GeneratorV1.GEN_VERSION);
			}
		} catch (IllegalArgumentException e) {
			throw new UsageException("cannot plan " + site.id() + ": " + e.getMessage() + " (does the terrain window cover the settlement?)");
		}
		double ms = (System.nanoTime() - t0) / 1e6;
		List<Violation> all = new ArrayList<>();
		if (city != null) {
			for (String n : city.notes()) System.err.println("city: note: " + n);
			String sel = o.has("scenes") ? o.get("scenes", null) : o.get("scene", "none");
			List<String> sceneIds = sceneIds(sel, city);
			for (String id : sceneIds) {
				DistrictScenes.Built b = CityPlanner.scene(city, id, p.terrain(), p.biome(), !o.flag("interiors"));
				String scenePath = "scenes/" + id + ".json";
				DistrictPlan old = b.plan();
				city.districts().put(id, new DistrictPlan(old.genVersion(), old.seed(), old.district(), old.streets(), old.plazas(), old.lots(), scenePath, old.violations()));
				GenCommand.write(dir.resolve(scenePath), IrJson.write(b.scene()).getBytes(StandardCharsets.UTF_8));
				for (Violation v : b.scene().violations()) all.add(new Violation(v.invariant(), v.building(), id + " scene: " + v.message(), v.cells()));
			}
			int lots = 0;
			for (var e : city.districts().entrySet()) {
				GenCommand.write(dir.resolve("districts/" + e.getKey() + ".json"), DistrictJson.write(e.getValue()).getBytes(StandardCharsets.UTF_8));
				lots += e.getValue().lots().size();
			}
			all.addAll(CityPlanner.check(city));
			System.err.printf(java.util.Locale.ROOT, "districts: %d planned, %d lots, %d scenes%n", city.districts().size(), lots, sceneIds.size());
			plan = city.plan();
		} else {
			all.addAll(plan.violations());
		}
		for (Violation v : all) {
			String line = v.invariant() + ": " + v.message();
			System.err.println("city: " + line);
			if (o.flag("check")) System.out.println(line);
		}
		GenCommand.write(dir.resolve("settlement.json"), SettlementJson.write(plan).getBytes(StandardCharsets.UTF_8));
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
			gz.write(TerrainCrop.toJson(seed, plan.coarse()).getBytes(StandardCharsets.UTF_8));
		}
		GenCommand.write(dir.resolve("terrain.json.gz"), bos.toByteArray());
		System.err.printf(java.util.Locale.ROOT, "city: %s %s r%d at %d,%d, %d cells, %d districts, %d roads, %d gates, %d landmarks, %d violations, %.0f ms%n",
				site.id(), site.tier().json, site.radius(), site.centre().x(), site.centre().z(), site.cells().size(), plan.districts().size(),
				plan.roads().size(), plan.gates().size(), plan.landmarks().size(), all.size(), ms);
		return all.isEmpty() ? 0 : 1;
	}

	/** Resolves {@code --scenes}: none, all, auto:N, or district ids. */
	private static List<String> sceneIds(String sel, CityPlanner.City city) {
		if (sel.equals("none") || sel.isEmpty()) return List.of();
		if (sel.equals("all")) return new ArrayList<>(city.districts().keySet());
		if (sel.startsWith("auto:")) {
			try { return CityPlanner.autoScenes(city, Integer.parseInt(sel.substring(5))); } catch (NumberFormatException e) { throw new UsageException("bad --scenes " + sel); }
		}
		List<String> ids = new ArrayList<>();
		for (String id : sel.split(",")) {
			if (!city.districts().containsKey(id)) throw new UsageException("no planned district " + id + " (have " + city.districts().size() + " from " + city.districts().firstKey() + ")");
			ids.add(id);
		}
		return ids;
	}

	static final String WINDOW_HINT = "give --site X,Z,TIER,RADIUS for a fine window, or --cell on a coarse world dump or the live world";

	/** The region field with its join rule, limited to the recorded window when the terrain is a dump. */
	static RegionView cellView(RegionField field, ProbeSpecs.Probes p) {
		return new FieldSiteView(fieldView(field, p), field);
	}

	/** The road graph over the same field and window (ADR 0004): ports for required exits and approaches ({@code --roads}). */
	static dev.fractalbuildings.core.road.RoadNet roadNet(RegionField field, FieldRegionView fv, ProbeSpecs.Probes p) {
		RegionMap.Window w = null;
		if (p.terrain() instanceof RecordedProbe r) {
			int s = RegionProbe.STRIDE, x0 = Math.floorDiv(r.x0() + s - 1, s) * s, z0 = Math.floorDiv(r.z0() + s - 1, s) * s;
			int x1 = Math.floorDiv(r.x0() + (r.nx() - 1) * r.step(), s) * s, z1 = Math.floorDiv(r.z0() + (r.nz() - 1) * r.step(), s) * s;
			w = new RegionMap.Window(x0, z0, (x1 - x0) / s + 1, (z1 - z0) / s + 1);
		}
		return new dev.fractalbuildings.core.road.RoadNet(field, fv, w, RegionProbe.of(p.terrain(), p.biome()), p.terrain());
	}

	static FieldRegionView fieldView(RegionField field, ProbeSpecs.Probes p) {
		return fieldView(field, p, FieldRegionView.DEFAULT_CACHE);
	}

	static FieldRegionView fieldView(RegionField field, ProbeSpecs.Probes p, int cacheCapacity) {
		RegionPlanner planner = new RegionPlanner(field, RegionProbe.of(p.terrain(), p.biome()));
		RegionMap.Window w = null;
		if (p.terrain() instanceof RecordedProbe r) {
			int s = RegionProbe.STRIDE, x0 = Math.floorDiv(r.x0() + s - 1, s) * s, z0 = Math.floorDiv(r.z0() + s - 1, s) * s;
			int x1 = Math.floorDiv(r.x0() + (r.nx() - 1) * r.step(), s) * s, z1 = Math.floorDiv(r.z0() + (r.nz() - 1) * r.step(), s) * s;
			if (x1 < x0 || z1 < z0) throw new UsageException("recorded terrain is smaller than one region lattice step");
			w = new RegionMap.Window(x0, z0, (x1 - x0) / s + 1, (z1 - z0) / s + 1);
		}
		return new FieldRegionView(planner, w, cacheCapacity);
	}

	static SettlementSite fromSite(RegionField field, String arg) {
		String[] f = arg.split(",", -1);
		if (f.length != 4) throw new UsageException("expected --site X,Z,TIER,RADIUS");
		int x, z, r;
		Tier tier;
		try {
			x = Integer.parseInt(f[0]);
			z = Integer.parseInt(f[1]);
			r = Integer.parseInt(f[3]);
			tier = Tier.of(f[2]);
		} catch (IllegalArgumentException e) {
			throw new UsageException("bad --site '" + arg + "' (X,Z,TIER,RADIUS; tier hamlet|village|town|city|metropolis)");
		}
		if (r <= 0) throw new UsageException("--site radius must be positive");
		Cell claimant = CellGeometry.owner(field, x, z);
		List<Cell> cells = new ArrayList<>();
		for (int dz = -8; dz <= 8; dz++) {
			for (int dx = -8; dx <= 8; dx++) {
				Cell c = new Cell(claimant.cx() + dx, claimant.cz() + dz);
				long ex = field.siteX(c.cx(), c.cz()) - x, ez = field.siteZ(c.cx(), c.cz()) - z;
				if (c.equals(claimant) || ex * ex + ez * ez <= (long) r * r) cells.add(c);
			}
		}
		cells.sort(CellGeometry::compare);
		if (cells.size() > MAX_CELLS) throw new UsageException("--site spans " + cells.size() + " cells (max " + MAX_CELLS + "); use a smaller radius or a larger --cell-size");
		return new SettlementSite(dev.fractalbuildings.core.settlement.Ids.site(claimant.cx(), claimant.cz()), tier, new Pt(x, z), r, claimant, cells);
	}

	private static int[] ints(String s, int n, String form) {
		String[] p = s.split(",", -1);
		if (p.length != n) throw new UsageException("expected " + form);
		int[] out = new int[n];
		try {
			for (int i = 0; i < n; i++) out[i] = Integer.parseInt(p[i]);
		} catch (NumberFormatException e) {
			throw new UsageException("expected " + form);
		}
		return out;
	}
}
