package dev.fractalbuildings.cli;

import dev.fractalbuildings.cli.Main.UsageException;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.FloorPlanText;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * {@code floorplan}: a building's storeys as ASCII grids with legend, room table, openings, furniture and
 * circulation ({@link FloorPlanText}), from a scene file ({@code --in}) or straight from generation flags.
 */
final class FloorplanCommand {
	private FloorplanCommand() {}

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("in", "building", "seed", "lot", "at", "front", "style", "terrain", "out", "type", "gen", "air"), Set.of("wide"));
		SceneDoc doc;
		if (o.has("in")) {
			String json = Files.readString(Path.of(o.get("in", null)), StandardCharsets.UTF_8);
			try {
				doc = IrJson.read(json);
			} catch (IllegalArgumentException e) {
				throw new UsageException("cannot read scene: " + e.getMessage());
			}
		} else {
			String gen = Scenes.gen(o);
			long seed = o.longValue("seed");
			int[] lot = Opts.lot(o.require("lot"));
			Facing front = Opts.front(o.get("front", "north"));
			String type = Scenes.type(o.get("type", "house"), gen);
			String style = Scenes.style(o.get("style", "plain"), gen);
			ProbeSpecs.Probes p = Scenes.probes(o.get("terrain", "flat:64"));
			int[] at = Scenes.origin(o.get("at", null), p, lot[0], lot[1]);
			doc = Scenes.generate(Scenes.request(seed, lot, at, front, type, style, p, o.intValue("air", 0)), false, gen);
		}
		String id = o.get("building", null);
		if (id == null && doc.buildings().size() > 1) {
			System.err.println("the scene has " + doc.buildings().size() + " buildings; pick one with --building ID:");
			System.err.print(FloorPlanText.index(doc));
			return 2;
		}
		String text;
		try {
			text = FloorPlanText.render(doc, id, o.flag("wide"));
		} catch (IllegalArgumentException e) {
			throw new UsageException(e.getMessage());
		}
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		if (o.has("out")) {
			GenCommand.write(Path.of(o.get("out", null)), bytes);
		} else {
			System.out.write(bytes);
			System.out.flush();
		}
		return 0;
	}
}
