package dev.fractalbuildings.cli;

import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.metrics.Diversity;
import dev.fractalbuildings.core.metrics.DiversityReport;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** {@code diversity}: the diversity report over one building per seed; markdown (or JSON with {@code --json}). */
final class DiversityCommand {
	private DiversityCommand() {}

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("seeds", "lot", "at", "front", "style", "terrain", "out", "type", "gen"), Set.of("json"));
		String gen = Scenes.gen(o);
		String type = Scenes.type(o.get("type", "house"), gen);
		long[] seeds = Opts.seedRange(o.require("seeds"));
		int[] lot = Opts.lot(o.get("lot", "20x20"));
		Facing front = Opts.front(o.get("front", "north"));
		String style = Scenes.style(o.get("style", "plain"), gen);
		ProbeSpecs.Probes p = Scenes.probes(o.get("terrain", "flat:64"));
		int[] at = Scenes.origin(o.get("at", null), p, lot[0], lot[1]);
		List<PlacedBuilding> bs = new ArrayList<>();
		for (long s = seeds[0]; s <= seeds[1]; s++) {
			bs.add(Scenes.generate(Scenes.request(s, lot, at, front, type, style, p), false, gen).buildings().get(0));
		}
		DiversityReport r = Diversity.of(bs);
		String text = o.flag("json") ? r.toJson() : r.toMarkdown();
		if (o.has("out")) GenCommand.write(Path.of(o.get("out", null)), text.getBytes(StandardCharsets.UTF_8));
		else System.out.print(text);
		return 0;
	}
}
