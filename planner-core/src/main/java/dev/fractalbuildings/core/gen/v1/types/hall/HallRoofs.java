package dev.fractalbuildings.core.gen.v1.types.hall;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.roof.RoofPass;
import dev.fractalbuildings.core.gen.v1.style.RoofStyle;
import java.util.Map;
import java.util.TreeMap;

/**
 * Roofs over wide spans: the shared roof pass with the style's pitch lowered so that no roof rises
 * more than {@link #MAX_RISE} blocks (a 1/1 gable over a 40-wide hall would be 20 high), and without
 * mansards (a steep fold over a span this wide is a barn). The pass's own fallbacks (lower pitch,
 * then flat, under {@code maxHeight}) still apply on top.
 */
final class HallRoofs {
	private HallRoofs() {}

	static final int MAX_RISE = 7;

	static void run(BuildContext c) { RoofPass.run(c, adapt(c.style().roof(), widestSpan(c))); }

	/** The largest smaller side over the open masses (the nave or hall width), or over all masses without one. */
	static int widestSpan(BuildContext c) {
		int open = 0, any = 0;
		for (Mass m : c.massing().masses()) {
			int s = Math.min(m.sx(), m.sz());
			any = Math.max(any, s);
			if (m.open()) open = Math.max(open, s);
		}
		return open > 0 ? open : any;
	}

	static RoofStyle adapt(RoofStyle rs, int span) {
		// half the span rises rise/run per block: (span / 2) * rise / run <= MAX_RISE
		int run = Math.max(rs.run(), (span * rs.rise() + 2 * MAX_RISE - 1) / (2 * MAX_RISE));
		Map<String, Integer> forms = new TreeMap<>(rs.forms());
		forms.remove("mansard");
		if (forms.values().stream().noneMatch(w -> w > 0)) forms = Map.of("gable", 1);
		return rs.withForms(forms).withPitch(rs.rise(), run);
	}
}
