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
import dev.fractalbuildings.core.settlement.Briefs;
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
	static final int MAX_CELLS = 16;

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("seed", "terrain", "cell", "site", "cell-size", "out-dir", "scene"), Set.of("districts"));
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
			view = cellView(field, p);
			site = view.siteOf(new Cell(a[0], a[1])).orElseThrow(() -> new UsageException("cell " + a[0] + "," + a[1] + " belongs to no settlement"));
		} else {
			site = fromSite(field, o.get("site", null));
			view = new FixedSiteView(site, field);
		}
		CoarseProbe coarse = CoarseProbe.of(p.terrain(), p.biome(), 32);

		long t0 = System.nanoTime();
		SettlementPlan plan;
		try {
			plan = Skeletons.plan(seed, view, site, coarse, GeneratorV1.GEN_VERSION);
		} catch (IllegalArgumentException e) {
			throw new UsageException("cannot plan " + site.id() + ": " + e.getMessage() + " (does the terrain window cover the settlement?)");
		}
		double ms = (System.nanoTime() - t0) / 1e6;
		int districtViolations = 0;
		if (o.flag("districts") || o.has("scene")) {
			Set<String> scenes = new java.util.TreeSet<>();
			if (o.has("scene")) scenes.addAll(List.of(o.get("scene", null).split(",")));
			DistrictsResult r = planDistricts(seed, view, site, coarse, p, plan, scenes, dir);
			plan = r.plan();
			districtViolations = r.violations();
		}
		GenCommand.write(dir.resolve("settlement.json"), SettlementJson.write(plan).getBytes(StandardCharsets.UTF_8));
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
			gz.write(TerrainCrop.toJson(seed, plan.coarse()).getBytes(StandardCharsets.UTF_8));
		}
		GenCommand.write(dir.resolve("terrain.json.gz"), bos.toByteArray());
		System.err.printf(java.util.Locale.ROOT, "city: %s %s r%d at %d,%d, %d cells, %d districts, %d roads, %d gates, %d landmarks, %d violations, %.0f ms%n",
				site.id(), site.tier().json, site.radius(), site.centre().x(), site.centre().z(), site.cells().size(), plan.districts().size(),
				plan.roads().size(), plan.gates().size(), plan.landmarks().size(), plan.violations().size() + districtViolations, ms);
		return plan.violations().isEmpty() && districtViolations == 0 ? 0 : 1;
	}

	record DistrictsResult(SettlementPlan plan, int violations) {}

	/**
	 * District detail (DL) for every district the settlement lists that it owns: {@code districts/<id>.json}, filled by the brief step
	 * (style from the biome field, storeys, street mask, front height), with an optional scene ({@code scenes/<id>.json}, exterior) for the
	 * districts named in {@code --scene}. The settlement's district list gets the lot counts, style weights and detail paths.
	 */
	private static DistrictsResult planDistricts(long seed, RegionView view, SettlementSite site, CoarseProbe coarse, ProbeSpecs.Probes p, SettlementPlan plan,
			Set<String> sceneIds, Path dir) throws IOException {
		SkeletonSource src = new SkeletonSource.Direct(seed, view, site, coarse);
		StyleField styles = new StyleField(p.biome(), seed);
		List<DistrictSummary> out = new ArrayList<>();
		int violations = 0, planned = 0, lots = 0;
		long t0 = System.nanoTime();
		for (DistrictSummary d : plan.districts()) {
			Optional<Districts.Detail> det;
			try {
				det = Districts.plan(src, d.ix(), d.iz(), p.terrain());
			} catch (IllegalArgumentException e) {
				throw new UsageException("cannot plan district " + d.id() + ": " + e.getMessage() + " (does the terrain window cover it?)");
			}
			if (det.isEmpty()) { out.add(d); continue; }
			DistrictPlan dp = Briefs.fill(det.get().plan(), QuarterKind.values()[det.get().plan().district().quarter()], styles, p.terrain());
			String scenePath = null;
			if (sceneIds.contains(d.id())) {
				DistrictScenes.Built b = DistrictScenes.build(dp, det.get().landmarks(), p.terrain(), p.biome(), true);
				scenePath = "scenes/" + d.id() + ".json";
				dp = new DistrictPlan(b.plan().genVersion(), b.plan().seed(), b.plan().district(), b.plan().streets(), b.plan().plazas(), b.plan().lots(), scenePath, b.plan().violations());
				GenCommand.write(dir.resolve(scenePath), IrJson.write(b.scene()).getBytes(StandardCharsets.UTF_8));
				violations += b.scene().violations().size();
			}
			violations += dp.violations().size();
			for (Violation v : dp.violations()) System.err.println("city: " + d.id() + " " + v.invariant() + ": " + v.message());
			for (String n : det.get().notes()) System.err.println("city: " + d.id() + " note: " + n);
			GenCommand.write(dir.resolve("districts/" + d.id() + ".json"), DistrictJson.write(dp).getBytes(StandardCharsets.UTF_8));
			java.util.TreeMap<String, Integer> tally = new java.util.TreeMap<>();
			int built = 0;
			for (LotBrief l : dp.lots()) if (l.builds()) { tally.merge(l.style(), 1, Integer::sum); built++; }
			List<StyleWeight> sw = new ArrayList<>();
			for (var e : tally.entrySet()) sw.add(new StyleWeight(e.getKey(), e.getValue()));
			out.add(new DistrictSummary(d.id(), d.ix(), d.iz(), d.x(), d.z(), d.w(), d.d(), d.originChunkX(), d.originChunkZ(), d.quarter(), d.density(), sw, dp.lots().size(), "districts/" + d.id() + ".json"));
			planned++;
			lots += dp.lots().size();
		}
		System.err.printf(java.util.Locale.ROOT, "districts: %d planned, %d lots, %d violations, %.0f ms%n", planned, lots, violations, (System.nanoTime() - t0) / 1e6);
		return new DistrictsResult(plan.withDistricts(out), violations);
	}


	/** The region field with its join rule, limited to the recorded window when the terrain is a dump. */
	private static RegionView cellView(RegionField field, ProbeSpecs.Probes p) {
		RegionPlanner planner = new RegionPlanner(field, RegionProbe.of(p.terrain(), p.biome()));
		RegionMap.Window w = null;
		if (p.terrain() instanceof RecordedProbe r) {
			int s = RegionProbe.STRIDE, x0 = Math.floorDiv(r.x0() + s - 1, s) * s, z0 = Math.floorDiv(r.z0() + s - 1, s) * s;
			int x1 = Math.floorDiv(r.x0() + (r.nx() - 1) * r.step(), s) * s, z1 = Math.floorDiv(r.z0() + (r.nz() - 1) * r.step(), s) * s;
			if (x1 < x0 || z1 < z0) throw new UsageException("recorded terrain is smaller than one region lattice step");
			w = new RegionMap.Window(x0, z0, (x1 - x0) / s + 1, (z1 - z0) / s + 1);
		}
		return new FieldSiteView(new FieldRegionView(planner, w), field);
	}

	private static SettlementSite fromSite(RegionField field, String arg) {
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
		return new SettlementSite("s" + claimant.cx() + "_" + claimant.cz(), tier, new Pt(x, z), r, claimant, cells);
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
