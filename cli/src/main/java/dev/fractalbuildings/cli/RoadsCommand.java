package dev.fractalbuildings.cli;

import dev.fractalbuildings.cli.Main.UsageException;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionPlanner;
import dev.fractalbuildings.core.region.RegionProbe;
import dev.fractalbuildings.core.road.Profile;
import dev.fractalbuildings.core.road.RoadJson;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.road.RoadNet.EdgeKey;
import dev.fractalbuildings.core.road.RoadNet.Route;
import dev.fractalbuildings.core.road.RoadNet.Settlement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * {@code roads}: the region map with a {@code roads} layer (ADR 0004, ROADMAP 6.10): runs the region planner with the join rule over
 * a recorded terrain window, builds the road graph and every route in it, writes {@code fb-region-dump/1} with the roads, and prints a
 * report (linked share, edge lengths, crossings, per-edge cost) to stdout.
 */
final class RoadsCommand {
	private RoadsCommand() {}

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("seed", "terrain", "cell-size", "out"), Set.of());
		long seed = o.longValue("seed");
		int cell = o.intValue("cell-size", 1024);
		ProbeSpecs.Probes p = Scenes.probes(o.require("terrain"));
		if (!(p.terrain() instanceof RecordedProbe r) || r.step() != RegionProbe.STRIDE) throw new UsageException("roads needs --terrain recorded:<step-64 dump>");
		RegionProbe probe = RegionProbe.of(p.terrain(), p.biome());
		RegionMap.Window w = new RegionMap.Window(r.x0(), r.z0(), r.nx(), r.nz());
		RegionPlanner planner = new RegionPlanner(new RegionField(seed, cell), probe);
		FieldRegionView view = new FieldRegionView(planner, w);
		RegionMap.Layer layer = RegionMap.layer(planner, w);
		RoadNet net = new RoadNet(planner.field, view, w, probe, p.terrain());
		long t0 = System.nanoTime();
		String roads = RoadJson.write(net);
		double secs = (System.nanoTime() - t0) / 1e9;
		if (o.has("out")) {
			String json = RegionMap.toJson(seed, "cli", probe, w, List.of(layer), roads);
			GenCommand.write(Path.of(o.get("out", null)), json.getBytes(StandardCharsets.UTF_8));
		}
		System.out.print(report(seed, net, secs));
		return 0;
	}

	static String hist(List<Integer> v, int bin) {
		TreeMap<Integer, Integer> h = new TreeMap<>();
		for (int x : v) h.merge(x / bin * bin, 1, Integer::sum);
		StringBuilder sb = new StringBuilder();
		for (var e : h.entrySet()) sb.append(sb.length() == 0 ? "" : ", ").append(e.getKey()).append(": ").append(e.getValue());
		return sb.toString();
	}

	static String pct(List<? extends Number> v) {
		if (v.isEmpty()) return "-";
		List<Double> s = new ArrayList<>();
		for (Number n : v) s.add(n.doubleValue());
		s.sort(null);
		double sum = 0;
		for (double d : s) sum += d;
		return String.format(Locale.ROOT, "%.1f / %.1f / %.1f / %.1f", sum / s.size(), s.get(s.size() / 2), s.get(Math.min(s.size() - 1, (int) (s.size() * 0.95))), s.get(s.size() - 1));
	}

	static String report(long seed, RoadNet net, double secs) {
		List<Settlement> all = net.allSettlements();
		TreeSet<EdgeKey> edges = new TreeSet<>(), dropped = new TreeSet<>();
		TreeSet<RoadNet.EdgeKey> linkedPairs = new TreeSet<>();
		TreeMap<String, Integer> tiers = new TreeMap<>();
		int ports = 0, linked = 0, noNeighbour = 0, allFailed = 0, orphanRoutable = 0;
		for (Settlement s : all) {
			edges.addAll(net.edgesOf(s));
			dropped.addAll(net.droppedOf(s));
			tiers.merge(s.tier().json, 1, Integer::sum);
			if (s.port()) ports++;
		}
		List<Integer> centreLen = new ArrayList<>(), routeLen = new ArrayList<>(), wetRuns = new ArrayList<>();
		List<Long> reads = new ArrayList<>(), nodes = new ArrayList<>();
		List<Double> ms = new ArrayList<>(), grade = new ArrayList<>();
		int routes = 0, touching = 0, withCrossing = 0, crossings = 0, reroutes = 0, columns = 0;
		for (EdgeKey e : edges) {
			Route r = net.route(e);
			if (r.touching) { touching++; continue; }
			routes++;
			Settlement a = net.settlement(e.a()).orElseThrow(), b = net.settlement(e.b()).orElseThrow();
			centreLen.add((int) Math.hypot(a.x() - b.x(), a.z() - b.z()));
			routeLen.add((int) r.length);
			reads.add(r.exactReads);
			nodes.add(r.nodeRequests);
			ms.add(r.nanos / 1e6);
			grade.add(r.maxGrade);
			columns += r.columns;
			if (r.attempts > 0) reroutes++;
			if (!r.profile.crossings().isEmpty()) withCrossing++;
			for (Profile.Crossing c : r.profile.crossings()) { crossings++; wetRuns.add(c.length()); }
		}
		for (Settlement s : all) {
			boolean has = !net.edgesOf(s).isEmpty();
			if (has) { linked++; continue; }
			List<Settlement> near = net.neighbours(s);
			if (near.isEmpty()) noNeighbour++;
			else {
				boolean any = false;
				for (Settlement n : near) any |= net.routable(s, n);
				if (any) orphanRoutable++;
				else allFailed++;
			}
		}
		List<Long> dropReads = new ArrayList<>();
		TreeMap<String, Integer> why = new TreeMap<>();
		for (EdgeKey e : dropped) why.merge(net.route(e).failure.replaceAll("[0-9]+", "N"), 1, Integer::sum);
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT, "seed %d: %d settlements %s, %d tagged port%n", seed, all.size(), tiers, ports));
		sb.append(String.format(Locale.ROOT, "linked %d of %d = %.1f %%; unlinked: %d with no neighbour within %d, %d with every neighbour unroutable, %d with a routable neighbour (must be 0)%n",
			linked, all.size(), 100.0 * linked / Math.max(1, all.size()), noNeighbour, RoadNet.LGAP, allFailed, orphanRoutable));
		sb.append(String.format(Locale.ROOT, "links: %d routed + %d meeting points (touching), %d pairs dropped by the dry check or no path %s%n", routes, touching, dropped.size(), why));
		sb.append("centre-to-centre per 500: ").append(hist(centreLen, 500)).append('\n');
		sb.append("route length mean / p50 / p95 / max: ").append(pct(routeLen)).append('\n');
		sb.append("max deck grade per route mean / p50 / p95 / max: ").append(pct(grade)).append('\n');
		sb.append(String.format(Locale.ROOT, "crossings: %d on %d of %d routes; wet run length per 4: %s; %d routes rerouted; %d road columns%n", crossings, withCrossing, routes, hist(wetRuns, 4), reroutes, columns));
		sb.append("exact station reads per route mean / p50 / p95 / max: ").append(pct(reads)).append('\n');
		sb.append("lattice node requests per route mean / p50 / p95 / max: ").append(pct(nodes)).append('\n');
		sb.append("route time ms mean / p50 / p95 / max: ").append(pct(ms)).append('\n');
		sb.append(String.format(Locale.ROOT, "lattice nodes read from the coarse probe: %d; routes built: %d (incl. witnesses and dropped); all of it in %.1f s%n", net.nodeReads(), net.routesBuilt(), secs));
		return sb.toString();
	}
}
