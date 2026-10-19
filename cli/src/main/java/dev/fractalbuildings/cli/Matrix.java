package dev.fractalbuildings.cli;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.Types;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The types x lots x styles x terrains x fronts cross product shared by check and bench. Without
 * {@code --lots}, v1 uses each type's own test lots (TypeLimits.testLots); v0 uses the Phase 3 four.
 */
final class Matrix {
	static final Set<String> OPTIONS = Set.of("lots", "styles", "terrains", "fronts", "types", "gen");
	static final String LOTS = "7x7,12x10,20x20,31x17";
	static final String TERRAINS = "flat:64,plane:64:1:0:4,hill:64:20:24,noise:7:64:6:32";
	/** Default terrains for types whose maxGrade is above 3 (city block: front steps cannot absorb a steep drop around a whole block). */
	static final String GENTLE_TERRAINS = "flat:64,plane:64:1:0:24,hill:64:4:120,noise:7:64:1:48";
	static final String FRONTS = "north,east,south,west";

	record Cell(String label, String type, int[] lot, String style, ProbeSpecs.Probes probes, Facing front) {
		GenRequest request(long seed) {
			int[] at = Scenes.origin(null, probes, lot[0], lot[1]);
			return Scenes.request(seed, lot, at, front, type, style, probes);
		}
	}

	final String gen;
	final List<Cell> cells = new ArrayList<>();

	Matrix(Opts o) {
		gen = Scenes.gen(o);
		List<String> types = o.has("types") ? Opts.list(o.get("types", null)) : Scenes.types(gen);
		List<String> styles = o.has("styles") ? Opts.list(o.get("styles", null)) : Scenes.styles(gen);
		for (String type : types) {
			Scenes.type(type, gen);
			List<String> lots = new ArrayList<>();
			boolean own = !(o.has("lots") || gen.equals(Scenes.V0));
			if (o.has("lots") || gen.equals(Scenes.V0)) lots.addAll(Opts.list(o.get("lots", LOTS)));
			else for (int[] l : Types.get(type).limits().testLots()) lots.add(l[0] + "x" + l[1]);
			for (String lot : lots)
				for (String style : styles)
					for (String terrain : Opts.list(o.get("terrains", gen.equals(Scenes.V0) || Types.get(type).limits().maxGrade() <= 3 ? TERRAINS : GENTLE_TERRAINS)))
						for (String front : Opts.list(o.get("fronts", FRONTS))) {
							Scenes.style(style, gen);
							cells.add(new Cell(type + " " + lot + " " + style + " " + terrain + " " + front, type, own ? Types.get(type).limits().oriented(Opts.lot(lot), Opts.front(front)) : Opts.lot(lot), style,
									Scenes.probes(terrain), Opts.front(front)));
						}
		}
	}
}
