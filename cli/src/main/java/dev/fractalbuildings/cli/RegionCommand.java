package dev.fractalbuildings.cli;

import dev.fractalbuildings.cli.Main.UsageException;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.CellDecision;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionPlanner;
import dev.fractalbuildings.core.region.RegionProbe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * {@code region}: runs the region planner over a terrain window and writes the region map
 * ({@code fb-region-dump/1} with {@code layers}); a one-line summary with timing goes to stderr.
 */
final class RegionCommand {
	private RegionCommand() {}

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("seed", "terrain", "cell-size", "window", "max-radius", "out"), Set.of());
		long seed = o.longValue("seed");
		String spec = o.get("terrain", "flat:64");
		int cell = intOpt(o, "cell-size", 1024);
		if (cell < 256 || cell > 65536) throw new UsageException("--cell-size must be 256..65536");
		ProbeSpecs.Probes p = Scenes.probes(spec);
		RegionProbe probe = RegionProbe.of(p.terrain(), p.biome());

		RegionMap.Window w;
		if (o.has("window")) {
			int[] a = ints(o.get("window", null), 4, "--window X0,Z0,WIDTH,DEPTH (blocks)");
			int x0 = Math.floorDiv(a[0], RegionProbe.STRIDE) * RegionProbe.STRIDE, z0 = Math.floorDiv(a[1], RegionProbe.STRIDE) * RegionProbe.STRIDE;
			int nx = Math.floorDiv(a[0] + a[2] - x0 + RegionProbe.STRIDE - 1, RegionProbe.STRIDE) + 1;
			int nz = Math.floorDiv(a[1] + a[3] - z0 + RegionProbe.STRIDE - 1, RegionProbe.STRIDE) + 1;
			w = new RegionMap.Window(x0, z0, nx, nz);
		} else if (p.terrain() instanceof RecordedProbe r) {
			if (r.step() != RegionProbe.STRIDE) throw new UsageException("recorded terrain must be a step-" + RegionProbe.STRIDE + " dump, not " + r.step());
			w = new RegionMap.Window(r.x0(), r.z0(), r.nx(), r.nz());
		} else {
			w = new RegionMap.Window(-3072, -3072, 97, 97);
		}

		RegionPlanner planner = new RegionPlanner(new RegionField(seed, cell), probe);
		if (o.has("max-radius")) planner.maxRadius = intOpt(o, "max-radius", planner.maxRadius);
		long t0 = System.nanoTime();
		RegionMap.Layer layer = RegionMap.layer(planner, w);
		double ms = (System.nanoTime() - t0) / 1e6;
		String json = RegionMap.toJson(seed, "cli", probe, w, List.of(layer));

		int settled = 0;
		for (CellDecision c : layer.cells()) if (c.settled()) settled++;
		System.err.printf(java.util.Locale.ROOT, "region: seed %d, window %d,%d %dx%d columns, %d cells decided, %d settled, %.1f ms (%.2f ms/cell)%n",
			seed, w.x0(), w.z0(), w.nx(), w.nz(), layer.cells().size(), settled, ms, layer.cells().isEmpty() ? 0 : ms / layer.cells().size());
		if (o.has("out")) GenCommand.write(Path.of(o.get("out", null)), json.getBytes(StandardCharsets.UTF_8));
		else System.out.print(json);
		return 0;
	}

	private static int intOpt(Opts o, String n, int def) {
		String v = o.get(n, null);
		if (v == null) return def;
		try { return Integer.parseInt(v); } catch (NumberFormatException e) { throw new UsageException("bad --" + n + ": " + v); }
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
		if (out[2] <= 0 || out[3] <= 0) throw new UsageException("window size must be positive");
		return out;
	}
}
