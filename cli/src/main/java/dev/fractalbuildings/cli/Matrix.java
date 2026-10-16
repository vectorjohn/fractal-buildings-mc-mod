package dev.fractalbuildings.cli;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.Styles;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The lots x styles x terrains x fronts cross product shared by check and bench. */
final class Matrix {
	static final Set<String> OPTIONS = Set.of("lots", "styles", "terrains", "fronts");
	static final String LOTS = "7x7,12x10,20x20,31x17";
	static final String TERRAINS = "flat:64,plane:64:1:0:4,hill:64:20:24,noise:7:64:6:32";
	static final String FRONTS = "north,east,south,west";

	record Cell(String label, int[] lot, String style, ProbeSpecs.Probes probes, Facing front) {
		GenRequest request(long seed) {
			int[] at = Scenes.origin(null, probes, lot[0], lot[1]);
			return Scenes.request(seed, lot, at, front, style, probes);
		}
	}

	final List<Cell> cells = new ArrayList<>();

	Matrix(Opts o) {
		List<String> styles = o.has("styles") ? Opts.list(o.get("styles", null)) : Styles.ALL;
		for (String lot : Opts.list(o.get("lots", LOTS)))
			for (String style : styles)
				for (String terrain : Opts.list(o.get("terrains", TERRAINS)))
					for (String front : Opts.list(o.get("fronts", FRONTS))) {
						Scenes.style(style);
						cells.add(new Cell(lot + " " + style + " " + terrain + " " + front, Opts.lot(lot), style,
								Scenes.probes(terrain), Opts.front(front)));
					}
	}
}
