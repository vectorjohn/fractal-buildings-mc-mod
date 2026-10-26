package dev.fractalbuildings.core.gen.v1.types.highrise;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import java.util.ArrayList;
import java.util.List;

/**
 * The facade of a high-rise by programme (plan docs/plans/highrises.md, B3b): the bands come from
 * {@link TowerProgram#band}, so every storey of a tier, special floors included, draws the same bays and
 * window columns (DS-25: windows stack); the lobby and the special floors keep those columns but get taller
 * windows. Piers ({@link TowerPiers}) follow.
 */
final class HighriseFacade {
	private HighriseFacade() {}

	static void run(BuildContext c) {
		TowerProgram p = TowerProgram.read(c);
		if (p == null) { // the fallback box has no programme: the generic facade
			FacadePass.run(c);
			TowerPiers.apply(c);
			return;
		}
		FacadePass.run(c, (m, s) -> p.band(s), layouts -> taller(p, layouts));
		TowerPiers.apply(c);
	}

	/** Lobby and special storeys: the tier's window columns and sill, but 2 less than the storey is high. */
	static List<FacadePlan.Layout> taller(TowerProgram p, List<FacadePlan.Layout> layouts) {
		List<FacadePlan.Layout> out = new ArrayList<>();
		for (FacadePlan.Layout l : layouts) {
			int s = l.face().storey();
			FacadePlan.Win w = l.win();
			if (w == null || s >= p.habitable() || !(s == 0 || p.special(s))) { out.add(l); continue; }
			int h = Math.max(w.height(), Math.min(p.height(s) - 2, l.face().height() - w.sill() - (s == 0 ? 0 : 1))); // the lobby glass runs to the slab, a special floor keeps a wall row for the head
			out.add(new FacadePlan.Layout(l.face(), l.start(), l.bays(), l.kinds(), new FacadePlan.Win(w.width(), h, w.sill(), w.piered())));
		}
		return out;
	}
}
