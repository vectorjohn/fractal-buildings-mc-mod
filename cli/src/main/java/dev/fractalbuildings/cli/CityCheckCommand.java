package dev.fractalbuildings.cli;

import dev.fractalbuildings.cli.Main.UsageException;
import dev.fractalbuildings.core.check.DoorRise;
import dev.fractalbuildings.core.check.Glazing;
import dev.fractalbuildings.core.check.HighriseChecks;
import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CityPlanner;
import dev.fractalbuildings.core.settlement.DistrictScenes;
import dev.fractalbuildings.core.settlement.FixedSiteView;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.settlement.RegionView;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import dev.fractalbuildings.core.settlement.TerrainStats;
import dev.fractalbuildings.core.settlement.Tier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;

/**
 * {@code city-check}: plans every district of each settlement (D0 pipeline), builds scenes for the districts chosen by
 * {@code --scenes auto:N}, runs every settlement and building rule, and writes a markdown report. Exit 1 on any violation or
 * planning error. Sites come from {@code --sites cells} (the region planner's settlements in the terrain), {@code random:N} (N
 * hash-drawn sites per seed and terrain) or {@code gates:FILE} (rows of {@code fixtures/terrain/gate-sites.tsv}, each on its own
 * recorded window and seed; {@code --seeds} and {@code --terrain} are ignored), or {@code at:X,Z,TIER,RADIUS} (one exact site, as `city --site`, per seed and terrain).
 */
final class CityCheckCommand {
	private CityCheckCommand() {}

	private record Job(String label, long seed, String terrain, int cellSize, String site, int cellCx, int cellCz) {}

	private record Result(Job job, String siteId, String tier, int radius, int cells, int districts, int lots, int scenes, int hubPct, int strays, List<String> problems, List<String> warnings, String error, double ms, TerrainStats terrain) {}

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("seeds", "terrain", "sites", "scenes", "cell-size", "out", "ignore", "list", "junctions-near"), Set.of("junction", "door-rise"));
		doorRiseOn = o.flag("door-rise");
		o.flag("junction"); // junction-height is part of every check now (JUNCTION); the flag that turned it on is accepted and does nothing
		listMax = o.intValue("list", 20);
		if (o.has("junctions-near")) {
			String[] f = o.get("junctions-near", null).split(",");
			near = new int[] {Integer.parseInt(f[0]), Integer.parseInt(f[1]), f.length > 2 ? Integer.parseInt(f[2]) : 24};
		}
		String sites = o.get("sites", "cells");
		int scenes = 0;
		String sc = o.get("scenes", "auto:0");
		if (!sc.startsWith("auto:")) throw new UsageException("--scenes must be auto:N");
		try { scenes = Integer.parseInt(sc.substring(5)); } catch (NumberFormatException e) { throw new UsageException("bad --scenes " + sc); }
		int cellSize = 1024;
		if (o.has("cell-size")) {
			try { cellSize = Integer.parseInt(o.get("cell-size", null)); } catch (NumberFormatException e) { throw new UsageException("bad --cell-size"); }
		}
		Set<String> ignore = new java.util.TreeSet<>(o.has("ignore") ? Opts.list(o.get("ignore", null)) : List.of());
		List<Job> jobs = new ArrayList<>();
		if (sites.startsWith("gates:")) {
			Path tsv = Path.of(sites.substring(6));
			List<String> lines = Files.readAllLines(tsv);
			for (String l : lines.subList(1, lines.size())) {
				String[] f = l.split("\t");
				if (f.length < 6) continue;
				String terrain = "recorded:" + tsv.resolveSibling(f[0] + ".json.gz");
				jobs.add(new Job(f[0], Long.parseLong(f[1]), terrain, cellSize, f[2] + "," + f[3] + "," + f[4] + "," + f[5], 0, 0));
			}
		} else {
			long[] seeds = Opts.seedRange(o.require("seeds"));
			List<String> terrains = Opts.list(o.get("terrain", "noise:3:70:10:300"));
			for (long seed = seeds[0]; seed <= seeds[1]; seed++) {
				for (String t : terrains) {
					if (sites.equals("cells")) addCells(jobs, seed, t, cellSize);
					else if (sites.startsWith("random:")) addRandom(jobs, seed, t, cellSize, parse(sites.substring(7)));
					else if (sites.startsWith("at:")) jobs.add(new Job("at-" + seed, seed, t, cellSize, sites.substring(3), 0, 0));
					else throw new UsageException("bad --sites " + sites + " (cells | random:N | at:X,Z,TIER,RADIUS | gates:FILE)");
				}
			}
		}
		if (jobs.isEmpty()) throw new UsageException("no sites to check");
		long t0 = System.nanoTime();
		Result[] results = new Result[jobs.size()];
		final int sceneCount = scenes;
		IntStream.range(0, results.length).parallel().forEach(i -> results[i] = check(jobs.get(i), sceneCount));
		String md = toMarkdown(results, scenes, ignore);
		double secs = (System.nanoTime() - t0) / 1e9;
		int bad = 0;
		for (Result r : results) if (r.error() != null || r.problems().stream().anyMatch(p -> !ignore.contains(rule(p)))) bad++;
		System.err.printf(Locale.ROOT, "city-check: %d sites, %d failing, %.1f s%n", results.length, bad, secs);
		if (o.has("out")) GenCommand.write(Path.of(o.get("out", null)), md.getBytes(StandardCharsets.UTF_8));
		else System.out.print(md);
		return bad == 0 ? 0 : 1;
	}

	/** Violations listed per site in the report ({@code --list}). */
	private static volatile int listMax = 20;
	/** Glazing of every building in the scenes built (DS-33 report, 7.12). */
	private static final Glazing.Report GLAZING = new Glazing.Report();
	/** Entrance door rise of every building in the scenes built (DS-45 report, SHOPLOW S0). */
	private static final DoorRise.Report DOOR_RISE = new DoorRise.Report();
	/** {@code --door-rise}: also build every scene with interiors and measure door rise (about 2.5x the run time). */
	private static boolean doorRiseOn;
	/** {@code --junctions-near X,Z[,R]}: print the junction field's junctions with a pad end within R blocks of (X, Z) (debugging JUNCTION). */
	private static volatile int[] near;

	private static int parse(String s) {
		try { return Integer.parseInt(s); } catch (NumberFormatException e) { throw new UsageException("bad number '" + s + "'"); }
	}

	/** The settlements the region planner finds in the terrain (its recorded window, else cells -4..4), once each. */
	private static void addCells(List<Job> jobs, long seed, String terrain, int cellSize) {
		ProbeSpecs.Probes p = Scenes.probes(terrain);
		RegionField field = new RegionField(seed, cellSize);
		dev.fractalbuildings.core.region.FieldRegionView fv = CityCommand.fieldView(field, p);
		RegionView view = new dev.fractalbuildings.core.settlement.FieldSiteView(fv, field);
		int c0x = -4, c0z = -4, c1x = 4, c1z = 4;
		if (p.terrain() instanceof RecordedProbe r) {
			c0x = Math.floorDiv(r.x0(), cellSize);
			c0z = Math.floorDiv(r.z0(), cellSize);
			c1x = Math.floorDiv(r.x0() + (r.nx() - 1) * r.step(), cellSize);
			c1z = Math.floorDiv(r.z0() + (r.nz() - 1) * r.step(), cellSize);
		}
		Set<String> seen = new java.util.TreeSet<>();
		for (int cz = c0z; cz <= c1z; cz++) {
			for (int cx = c0x; cx <= c1x; cx++) {
				if (!fv.decidable(cx, cz)) continue; // the window cannot answer for this cell
				try {
					var s = view.siteOf(new Cell(cx, cz));
					if (s.isPresent() && seen.add(s.get().id())) {
						SettlementSite st = s.get();
						jobs.add(new Job("seed " + seed + " " + terrain + " " + st.id(), seed, terrain, cellSize, st.centre().x() + "," + st.centre().z() + "," + st.tier().json + "," + st.radius(), cx, cz));
					}
				} catch (RuntimeException e) {
					// a cell the window cannot resolve
				}
			}
		}
	}

	/** {@code n} sites per (seed, terrain): tier by the ladder weights, a centre within 2500 of the origin, a radius within the tier band. */
	private static void addRandom(List<Job> jobs, long seed, String terrain, int cellSize, int n) {
		for (int i = 0; i < n; i++) {
			double u = (Hash64.hash(seed, "city-check/tier", i, 0) >>> 11) * 0x1.0p-53;
			Tier tier = Tier.HAMLET;
			double acc = 0;
			for (Tier t : Tier.values()) {
				acc += t.weight;
				tier = t;
				if (u < acc) break;
			}
			int hi = Math.min(tier.maxRadius, 1400);
			int r = tier.minRadius + Hash64.below(Hash64.hash(seed, "city-check/radius", i, 0), hi - tier.minRadius + 1);
			int x = Hash64.below(Hash64.hash(seed, "city-check/x", i, 0), 5001) - 2500, z = Hash64.below(Hash64.hash(seed, "city-check/z", i, 0), 5001) - 2500;
			jobs.add(new Job("seed " + seed + " " + terrain + " #" + i, seed, terrain, cellSize, x + "," + z + "," + tier.json + "," + r, 0, 0));
		}
	}

	private static Result check(Job j, int sceneCount) {
		long t0 = System.nanoTime();
		List<String> problems = new ArrayList<>();
		List<String> warnings = new ArrayList<>(); // design warnings (HighriseChecks): listed, never failing
		try {
			ProbeSpecs.Probes p = Scenes.probes(j.terrain());
			RegionField field = new RegionField(j.seed(), j.cellSize());
			SettlementSite site = CityCommand.fromSite(field, j.site());
			RegionView view = new FixedSiteView(site, field);
			CoarseProbe coarse = CoarseProbe.of(p.terrain(), p.biome(), 32);
			SkeletonSource.Direct src = new SkeletonSource.Direct(j.seed(), view, site, coarse);
			CityPlanner.City city = CityPlanner.plan(src, coarse, p.terrain(), p.biome(), GeneratorV1.GEN_VERSION);
			if (near != null) {
				for (var jn : src.junctions(p.terrain()).junctions()) {
					boolean hit = false;
					for (String pad : jn.pads()) for (String xz : pad.substring(pad.indexOf(' ') + 1).split("\\.\\.")) { String[] q = xz.split(","); if (Math.abs(Integer.parseInt(q[0]) - near[0]) <= near[2] && Math.abs(Integer.parseInt(q[1]) - near[1]) <= near[2]) hit = true; }
					if (hit) System.out.println(j.label() + " junction level " + jn.level() + " ground " + jn.ground() + " pads " + jn.pads());
				}
			}
			for (Violation v : CityPlanner.check(city)) problems.add(v.invariant() + ": " + v.message());
			// DS-18 edge taper (CITYQ r4): a high-rise with no built lot within 120 blocks outward. A warning, since the per-tile cap (BuiltEdge) cannot see
			// other tiles' lots and leaves a few; EdgeTaperTest holds the count down.
			for (Violation v : dev.fractalbuildings.core.check.SettlementChecks.cityEdgeTaper(city.districts().values(), site.centre(), new dev.fractalbuildings.core.settlement.BuiltEdge(src)::depth)) warnings.add(v.invariant() + ": " + v.message());
			// proposed rule "arterials form one network" (METROFILL R1): a warning until John accepts it
			for (Violation v : dev.fractalbuildings.core.check.SettlementChecks.cityArterialsConnected(city.districts().values())) warnings.add(v.invariant() + ": " + v.message());
			int built = 0;
			List<String> ids = CityPlanner.autoScenes(city, sceneCount);
			for (String id : ids) {
				DistrictScenes.Built b = CityPlanner.scene(city, id, p.terrain(), p.biome(), true);
				for (Violation v : b.scene().violations()) problems.add(v.invariant() + ": " + id + " " + v.message());
				for (Violation v : b.plan().violations()) problems.add(v.invariant() + ": " + id + " scene " + v.message());
				for (var pb : b.scene().buildings()) GLAZING.add(pb.shell().spec().type(), Glazing.measure(pb));
				// doors exist only in scenes built with interiors; the exterior scene above has none (violations of this one are not listed: the interior rules are check's)
				if (doorRiseOn) DOOR_RISE.add(DoorRise.measure(CityPlanner.scene(city, id, p.terrain(), p.biome(), false).scene(), p.terrain()));
				for (Violation v : HighriseChecks.check(b.scene())) warnings.add(v.invariant() + ": " + id + " " + v.message());
				for (Violation v : b.warnings()) warnings.add(v.invariant() + ": " + id + " " + v.message());
				built++;
			}
			int lots = 0;
			for (var d : city.districts().values()) lots += (int) d.lots().stream().filter(LotBrief::builds).count();
			return new Result(j, site.id(), site.tier().json, site.radius(), site.cells().size(), city.districts().size(), lots, built, city.streetColumns() == 0 ? 100 : (int) (100L * city.hubPiece() / city.streetColumns()), city.strays(), problems, warnings, null, (System.nanoTime() - t0) / 1e6, TerrainStats.of(city, p.terrain()));
		} catch (RuntimeException e) {
			return new Result(j, "?", "?", 0, 0, 0, 0, 0, 0, 0, problems, warnings, e.getClass().getSimpleName() + ": " + e.getMessage(), (System.nanoTime() - t0) / 1e6, null);
		}
	}

	private static String rule(String problem) { return problem.substring(0, problem.indexOf(':')); }

	private static String toMarkdown(Result[] rs, int scenes, Set<String> ignore) {
		StringBuilder sb = new StringBuilder("# city-check\n\n");
		int bad = 0, problems = 0;
		TreeMap<String, Integer> byInvariant = new TreeMap<>();
		for (Result r : rs) {
			if (r.error() != null || r.problems().stream().anyMatch(p -> !ignore.contains(rule(p)))) bad++;
			problems += r.problems().size();
			for (String p : r.problems()) byInvariant.merge(p.substring(0, p.indexOf(':')), 1, Integer::sum);
			if (r.error() != null) byInvariant.merge("error", 1, Integer::sum);
		}
		sb.append(rs.length).append(" sites (scenes: auto:").append(scenes).append("), ").append(bad).append(" failing, ").append(problems).append(" violations");
		if (!ignore.isEmpty()) sb.append(" (ignored for the exit code: ").append(String.join(", ", ignore)).append(")");
		sb.append("\n\n");
		if (!byInvariant.isEmpty()) {
			sb.append("| rule | count |\n|---|---|\n");
			for (var e : byInvariant.entrySet()) sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
			sb.append('\n');
		}
		TreeMap<String, Integer> design = new TreeMap<>();
		int designSites = 0;
		for (Result r : rs) {
			if (!r.warnings().isEmpty()) designSites++;
			for (String w : r.warnings()) design.merge(rule(w), 1, Integer::sum);
		}
		if (!design.isEmpty()) {
			sb.append("Design warnings (high-rise, door-rise beyond the planner's limit, flight-landings and proposed checks, not violations; ").append(designSites).append(" sites): ");
			design.forEach((k, n) -> sb.append(k).append(" ").append(n).append("; "));
			sb.append("\n\n");
		}
		sb.append("| site | id | tier | radius | cells | districts | lots | scenes | hub piece % | strays | violations | ms |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n");
		for (Result r : rs) {
			sb.append(String.format(Locale.ROOT, "| %s | %s | %s | %d | %d | %d | %d | %d | %d | %d | %s | %.0f |%n", r.job().label(), r.siteId(), r.tier(), r.radius(), r.cells(), r.districts(), r.lots(), r.scenes(), r.hubPct(), r.strays(),
					r.error() != null ? "error" : Integer.toString(r.problems().size()), r.ms()));
		}
		sb.append("\nTerrain response (urban % = columns the mask kept of those inside the footprint that are dry; cut and fill in thousands of blocks):\n\n| site | urban % | lots | empty:steep | cut k | fill k | steep roads | bridges | quays | quay lots |\n|---|---|---|---|---|---|---|---|---|---|\n");
		for (Result r : rs) {
			TerrainStats t = r.terrain();
			if (t == null) continue;
			sb.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d | %d | %d | %d | %d | %d |%n", r.job().label(), t.urbanPct(), t.lots(), t.emptySteep(), t.cutBlocks() / 1000, t.fillBlocks() / 1000, t.steepRoads(), t.bridges(), t.quays(), t.quayLots()));
		}
		for (Result r : rs) {
			if (r.error() == null && r.problems().isEmpty()) continue;
			sb.append("\n## ").append(r.job().label()).append(" (--site ").append(r.job().site()).append(")\n\n");
			if (r.error() != null) sb.append("- error: ").append(r.error()).append('\n');
			for (int i = 0; i < Math.min(r.problems().size(), listMax); i++) sb.append("- ").append(r.problems().get(i)).append('\n');
			if (r.problems().size() > listMax) sb.append("- ... ").append(r.problems().size() - listMax).append(" more\n");
		}
		if (scenes > 0) sb.append("\n## Glazing (DS-33, report only)\n\nBuildings in the district scenes built; glass cells over exterior wall-plane cells.\n\n").append(GLAZING.toMarkdown("type"));
		if (scenes > 0 && doorRiseOn) sb.append("\n## Door rise (DS-45, report only)\n\nEntrance doors of the buildings in the district scenes built; rise = door level minus the street surface in front (limits: shops -2..2, houses and workshops at most 3 or 4-5 with a landing, no run over 3 risers).\n\n").append(DOOR_RISE.toMarkdown());
		return sb.toString();
	}
}
