package dev.fractalbuildings.cli;

import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** {@code gen}: one scene to stdout or {@code --out}. */
final class GenCommand {
	private GenCommand() {}

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("seed", "lot", "at", "front", "style", "terrain", "out", "type", "gen", "air"), Set.of("check"));
		String gen = Scenes.gen(o);
		long seed = o.longValue("seed");
		int[] lot = Opts.lot(o.require("lot"));
		Facing front = Opts.front(o.get("front", "north"));
		String type = Scenes.type(o.get("type", "house"), gen);
		String style = Scenes.style(o.get("style", "plain"), gen);
		ProbeSpecs.Probes p = Scenes.probes(o.get("terrain", "flat:64"));
		int[] at = Scenes.origin(o.get("at", null), p, lot[0], lot[1]);
		byte[] json = IrJson.write(Scenes.generate(Scenes.request(seed, lot, at, front, type, style, p, o.intValue("air", 0)), o.flag("check"), gen))
				.getBytes(StandardCharsets.UTF_8);
		if (o.has("out")) {
			write(Path.of(o.get("out", null)), json);
		} else {
			System.out.write(json);
			System.out.flush();
		}
		return 0;
	}

	static void write(Path p, byte[] data) throws IOException {
		if (p.getParent() != null) Files.createDirectories(p.getParent());
		Files.write(p, data);
	}
}
