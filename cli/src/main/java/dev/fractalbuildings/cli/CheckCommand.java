package dev.fractalbuildings.cli;

import dev.fractalbuildings.core.check.InvariantReport;
import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/** {@code check}: invariants over seeds x matrix; exit 1 on any violation. Parallel, but reported in a fixed order. */
final class CheckCommand {
	private CheckCommand() {}

	static int run(String[] args) throws IOException {
		Set<String> names = new HashSet<>(Matrix.OPTIONS);
		names.addAll(Set.of("seeds", "out"));
		Opts o = new Opts(args, names, Set.of());
		long[] seeds = Opts.seedRange(o.require("seeds"));
		Matrix m = new Matrix(o);
		InvariantReport report = new InvariantReport();
		long t0 = System.nanoTime();
		for (long seed = seeds[0]; seed <= seeds[1]; seed++) {
			final long s = seed;
			@SuppressWarnings("unchecked")
			List<Violation>[] results = new List[m.cells.size()];
			IntStream.range(0, results.length).parallel().forEach(i -> {
				var req = m.cells.get(i).request(s);
				SceneDoc scene = Scenes.generate(req, false, m.gen);
				results[i] = Invariants.check(scene, req.terrain());
			});
			for (int i = 0; i < results.length; i++) report.add("seed " + s + " " + m.cells.get(i).label(), results[i]);
		}
		String md = report.toMarkdown();
		double secs = (System.nanoTime() - t0) / 1e9;
		System.err.printf("checked %d cases in %.1f s%n", report.cases(), secs);
		if (o.has("out")) GenCommand.write(Path.of(o.get("out", null)), md.getBytes(StandardCharsets.UTF_8));
		else System.out.print(md);
		return report.failingCases() == 0 ? 0 : 1;
	}
}
