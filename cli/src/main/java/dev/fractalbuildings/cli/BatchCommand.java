package dev.fractalbuildings.cli;

import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

/** {@code batch}: {@code DIR/seed-N.json} for each seed plus {@code DIR/index.json}, which the viewer's gen/ source loads. */
final class BatchCommand {
	private BatchCommand() {}

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("seeds", "lot", "at", "front", "style", "terrain", "out-dir", "type", "gen", "air"), Set.of("no-check"));
		String gen = Scenes.gen(o);
		String type = Scenes.type(o.get("type", "house"), gen);
		long[] seeds = Opts.seedRange(o.require("seeds"));
		String lotSpec = o.require("lot");
		int[] lot = Opts.lot(lotSpec);
		Facing front = Opts.front(o.get("front", "north"));
		String style = Scenes.style(o.get("style", "plain"), gen);
		String terrain = o.get("terrain", "flat:64");
		ProbeSpecs.Probes p = Scenes.probes(terrain);
		int[] at = Scenes.origin(o.get("at", null), p, lot[0], lot[1]);
		Path dir = Path.of(o.require("out-dir"));
		boolean check = !o.flag("no-check");
		int bad = 0;
		StringBuilder list = new StringBuilder();
		for (long s = seeds[0]; s <= seeds[1]; s++) {
			var scene = Scenes.generate(Scenes.request(s, lot, at, front, type, style, p, o.intValue("air", 0)), check, gen);
			bad += scene.violations().isEmpty() ? 0 : 1;
			GenCommand.write(dir.resolve("seed-" + s + ".json"), IrJson.write(scene).getBytes(StandardCharsets.UTF_8));
			if (!list.isEmpty()) list.append(", ");
			list.append(s);
		}
		String index = "{\"type\": " + quote(type) + ", \"style\": " + quote(style) + ", \"lot\": " + quote(lotSpec) + ", \"terrain\": " + quote(terrain)
				+ ", \"seeds\": [" + list + "]}\n";
		GenCommand.write(dir.resolve("index.json"), index.getBytes(StandardCharsets.UTF_8));
		System.err.println("wrote " + (seeds[1] - seeds[0] + 1) + " scenes to " + dir + (check ? ", " + bad + " with violations" : ""));
		return 0;
	}

	private static String quote(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
