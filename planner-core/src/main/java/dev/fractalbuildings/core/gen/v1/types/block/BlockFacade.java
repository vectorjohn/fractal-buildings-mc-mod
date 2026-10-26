package dev.fractalbuildings.core.gen.v1.types.block;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.ir.Facing;
import java.util.ArrayList;
import java.util.List;

/**
 * Facade of a city block: the shared facade pass, then every unit's door moved to its own street side
 * (the shared pass may pick any side of the unit, and the courtyard side is not a street).
 */
final class BlockFacade {
	private BlockFacade() {}

	static void run(BuildContext c) {
		FacadePass.run(c);
		FacadePlan plan = c.facadePlan();
		Massing m = c.massing();
		List<FacadePlan.Layout> layouts = new ArrayList<>(plan.layouts());
		List<FacadePlan.Entrance> ents = new ArrayList<>(plan.entrances());
		boolean changed = false;
		for (int i = 0; i < m.entrances().size(); i++) {
			Mass u = m.mass(m.entrances().get(i));
			Facing want = BlockMassing.street(u, c.width(), c.depth());
			FacadePlan.Entrance old = ents.get(i);
			if (old.face().side() == want) continue;
			// the old door bay becomes a window bay again
			for (int li = 0; li < layouts.size(); li++) {
				FacadePlan.Layout l = layouts.get(li);
				if (!l.face().equals(old.face())) continue;
				List<String> kinds = new ArrayList<>(l.kinds());
				for (int b = 0; b < kinds.size(); b++) if (FacadePlan.DOOR.equals(kinds.get(b)) && l.centre(b) == old.pos()) kinds.set(b, FacadePlan.WINDOW);
				layouts.set(li, l.withKinds(kinds));
			}
			ents.set(i, pick(c, m, layouts, u.id(), want, i));
			changed = true;
		}
		if (changed) c.setFacadePlan(new FacadePlan(layouts, ents.get(0), ents.subList(1, ents.size())));
	}

	/** A door position on the unit's street face of storey 0, in a window bay if there is one, with a free cell inside. */
	private static FacadePlan.Entrance pick(BuildContext c, Massing m, List<FacadePlan.Layout> layouts, String id, Facing side, int index) {
		List<int[]> bays = new ArrayList<>();
		for (int li = 0; li < layouts.size(); li++) {
			FacadePlan.Layout l = layouts.get(li);
			Face f = l.face();
			if (!f.mass().equals(id) || f.storey() != 0 || f.side() != side) continue;
			for (int b = 0; b < l.bays().length; b++) {
				int p = l.centre(b);
				if (FacadePlan.WINDOW.equals(l.kinds().get(b)) && !m.inCore(0, f.insideX(p), f.insideZ(p))) bays.add(new int[] {li, b});
			}
		}
		if (!bays.isEmpty()) {
			int[] pick = bays.get(c.draws("block/entrance", index).below(bays.size()));
			FacadePlan.Layout l = layouts.get(pick[0]);
			List<String> kinds = new ArrayList<>(l.kinds());
			kinds.set(pick[1], FacadePlan.DOOR);
			layouts.set(pick[0], l.withKinds(kinds));
			return new FacadePlan.Entrance(l.face(), l.centre(pick[1]));
		}
		List<FacadePlan.Entrance> loose = new ArrayList<>();
		for (Face f : c.faces()) {
			if (!f.mass().equals(id) || f.storey() != 0 || f.side() != side) continue;
			for (int p = f.a() + 1; p < f.b() - 1; p++) if (!m.inCore(0, f.insideX(p), f.insideZ(p))) loose.add(new FacadePlan.Entrance(f, p));
		}
		if (loose.isEmpty()) throw new IllegalStateException("no street door position for " + id);
		return loose.get(c.draws("block/entrance-loose", index).below(loose.size()));
	}
}
