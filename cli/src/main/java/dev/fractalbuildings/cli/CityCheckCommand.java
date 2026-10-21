package dev.fractalbuildings.cli;

import dev.fractalbuildings.cli.Main.UsageException;
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
 * recorded window and seed; {@code --seeds} and {@code --terrain} are ignored).
 */
final class CityCheckCommand {
	private CityCheckCommand() {}

	private record Job(String label, long seed, String terrain, int cellSize, String site, int cellCx, int cellCz) {}

	private record Result(Job job, String siteId, String tier, int radius, int cells, int districts, int lots, int scenes, int hubPct, int strays, List<String> problems, String error, double ms) {}

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("seeds", "terrain", "sites", "scenes", "cell-size", "out", "ignore"), Set.of());
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
					else throw new UsageException("bad --sites " + sites + " (cells | random:N | gates:FILE)");
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
		try {
			ProbeSpecs.Probes p = Scenes.probes(j.terrain());
			RegionField field = new RegionField(j.seed(), j.cellSize());
			SettlementSite site = CityCommand.fromSite(field, j.site());
			RegionView view = new FixedSiteView(site, field);
			CoarseProbe coarse = CoarseProbe.of(p.terrain(), p.biome(), 32);
			CityPlanner.City city = CityPlanner.plan(new SkeletonSource.Direct(j.seed(), view, site, coarse), coarse, p.terrain(), p.biome(), GeneratorV1.GEN_VERSION);
			for (Violation v : CityPlanner.check(city)) problems.add(v.invariant() + ": " + v.message());
			int built = 0;
			List<String> ids = CityPlanner.autoScenes(city, sceneCount);
			for (String id : ids) {
				DistrictScenes.Built b = CityPlanner.scene(city, id, p.terrain(), p.biome(), true);
				for (Violation v : b.scene().violations()) problems.add(v.invariant() + ": " + id + " " + v.message());
				for (Violation v : b.plan().violations()) problems.add(v.invariant() + ": " + id + " scene " + v.message());
				built++;
			}
			int lots = 0;
			for (var d : city.districts().values()) lots += (int) d.lots().stream().filter(LotBrief::builds).count();
			return new Result(j, site.id(), site.tier().json, site.radius(), site.cells().size(), city.districts().size(), lots, built, city.streetColumns() == 0 ? 100 : (int) (100L * city.hubPiece() / city.streetColumns()), city.strays(), problems, null, (System.nanoTime() - t0) / 1e6);
		} catch (RuntimeException e) {
			return new Result(j, "?", "?", 0, 0, 0, 0, 0, 0, 0, problems, e.getClass().getSimpleName() + ": " + e.getMessage(), (System.nanoTime() - t0) / 1e6);
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
		sb.append("| site | id | tier | radius | cells | districts | lots | scenes | hub piece % | strays | violations | ms |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n");
		for (Result r : rs) {
			sb.append(String.format(Locale.ROOT, "| %s | %s | %s | %d | %d | %d | %d | %d | %d | %d | %s | %.0f |%n", r.job().label(), r.siteId(), r.tier(), r.radius(), r.cells(), r.districts(), r.lots(), r.scenes(), r.hubPct(), r.strays(),
					r.error() != null ? "error" : Integer.toString(r.problems().size()), r.ms()));
		}
		for (Result r : rs) {
			if (r.error() == null && r.problems().isEmpty()) continue;
			sb.append("\n## ").append(r.job().label()).append(" (--site ").append(r.job().site()).append(")\n\n");
			if (r.error() != null) sb.append("- error: ").append(r.error()).append('\n');
			for (int i = 0; i < Math.min(r.problems().size(), 20); i++) sb.append("- ").append(r.problems().get(i)).append('\n');
			if (r.problems().size() > 20) sb.append("- ... ").append(r.problems().size() - 20).append(" more\n");
		}
		return sb.toString();
	}
}
