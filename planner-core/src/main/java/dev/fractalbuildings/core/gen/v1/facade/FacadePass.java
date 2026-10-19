package dev.fractalbuildings.core.gen.v1.facade;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.style.FacadeStyle;
import dev.fractalbuildings.core.ir.Facing;
import java.util.ArrayList;
import java.util.List;

/**
 * Facade layout (foundation default, owned by the facades step 4.4 afterwards). Every face's
 * positions between its two end cells are split into equal bays of the style's width, one window per
 * bay centre. Each entrance mass ({@code Massing.entrances()}) gets one door on storey 0: the main
 * mass on its front (north) face, the others on a street side if they have one (spec.streets), else
 * any side; at a bay centre whose inside cell is not in a core, else at any such position. No decor.
 */
public final class FacadePass {
	private FacadePass() {}

	public static void run(BuildContext c) {
		FacadeStyle st = c.style().facade();
		Massing m = c.massing();
		List<FacadePlan.Layout> layouts = new ArrayList<>();
		for (Face f : c.faces()) {
			int[] bays = bays(f.length() - 2, st.bayMin(), st.bayMax());
			if (bays == null) continue;
			List<String> kinds = new ArrayList<>();
			for (int i = 0; i < bays.length; i++) kinds.add(FacadePlan.WINDOW);
			layouts.add(new FacadePlan.Layout(f, f.a() + 1, bays, kinds));
		}
		List<FacadePlan.Entrance> entrances = new ArrayList<>();
		for (int e = 0; e < m.entrances().size(); e++) entrances.add(entrance(c, m, layouts, m.entrances().get(e), e));
		c.setFacadePlan(new FacadePlan(layouts, entrances.get(0), entrances.subList(1, entrances.size())));
	}

	/** Picks the door for one entrance mass and marks its bay as a door bay. */
	static FacadePlan.Entrance entrance(BuildContext c, Massing m, List<FacadePlan.Layout> layouts, String massId, int index) {
		boolean main = index == 0;
		for (int pass = 0; pass < 2; pass++) { // pass 0: street sides only (main: north only); pass 1: any side
			List<int[]> cand = new ArrayList<>(); // {layout index, bay index}
			List<FacadePlan.Entrance> loose = new ArrayList<>();
			for (Facing side : Facing.values()) {
				if (main ? side != Facing.NORTH : pass == 0 && !c.spec().street(side)) continue;
				for (int li = 0; li < layouts.size(); li++) {
					FacadePlan.Layout l = layouts.get(li);
					Face f = l.face();
					if (!f.mass().equals(massId) || f.storey() != 0 || f.side() != side) continue;
					for (int b = 0; b < l.bays().length; b++) {
						int p = l.centre(b);
						if (FacadePlan.WINDOW.equals(l.kinds().get(b)) && !m.inCore(0, f.insideX(p), f.insideZ(p))) cand.add(new int[] {li, b});
					}
				}
				for (Face f : c.faces()) {
					if (!f.mass().equals(massId) || f.storey() != 0 || f.side() != side) continue;
					for (int p = f.a() + 1; p < f.b() - 1; p++) if (!m.inCore(0, f.insideX(p), f.insideZ(p))) loose.add(new FacadePlan.Entrance(f, p));
				}
			}
			if (!cand.isEmpty()) {
				int[] pick = cand.get(c.draws("facade/entrance", index).below(cand.size()));
				FacadePlan.Layout l = layouts.get(pick[0]);
				List<String> kinds = new ArrayList<>(l.kinds());
				kinds.set(pick[1], FacadePlan.DOOR);
				layouts.set(pick[0], new FacadePlan.Layout(l.face(), l.start(), l.bays(), kinds));
				return new FacadePlan.Entrance(l.face(), l.centre(pick[1]));
			}
			if (!loose.isEmpty()) return loose.get(c.draws("facade/entrance-loose", index).below(loose.size()));
			if (main) break;
		}
		throw new IllegalStateException("no entrance position for " + massId);
	}

	/**
	 * Equal bays (sizes differ by at most 1, larger first) covering {@code len} exactly, each in
	 * [min, max]; the most bays that fit. Null if none fit.
	 */
	public static int[] bays(int len, int min, int max) {
		if (len < min) return null;
		for (int n = len / min; n >= 1; n--) {
			int base = len / n, extra = len % n;
			if (base < min || base + (extra > 0 ? 1 : 0) > max) continue;
			int[] out = new int[n];
			for (int i = 0; i < n; i++) out[i] = base + (i < extra ? 1 : 0);
			return out;
		}
		return null;
	}
}
