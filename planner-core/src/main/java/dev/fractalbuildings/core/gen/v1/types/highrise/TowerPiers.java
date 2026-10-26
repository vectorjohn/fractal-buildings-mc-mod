package dev.fractalbuildings.core.gen.v1.types.highrise;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import java.util.ArrayList;
import java.util.List;

/**
 * Piers where the core block's walls meet the facade (plan docs/plans/highrises.md, B3a). The plates ({@link TowerPlate})
 * draw the block's walls and the hall lines from the massing, before any window is known, so a window bay whose cells
 * would sit where such a wall ends on the facade becomes a blank bay (a pier) instead. Runs after the facade pass has
 * set the plan; the stair house gets no windows at all (its faces are blank bar the roof doors).
 *
 * <p>Wall ends on the facade of a tier (plate frame: x along the street, z from the street): both side facades at the
 * hall's two partition rows and the block's back row (when a room stands behind it); the back facade at the block's
 * two end columns (the middle one too when the block is flush with it), because they run on to the back wall past a back room.
 */
public final class TowerPiers {
	private TowerPiers() {}

	/** Replaces window bays that meet a block wall by blank bays; keeps entrances and everything else. */
	public static void apply(BuildContext c) {
		Massing m = c.massing();
		TowerBlock b = TowerBlock.of(m, TowerBlock.SD);
		if (b == null) return;
		FacadePlan fp = c.facadePlan();
		List<FacadePlan.Layout> out = new ArrayList<>();
		for (FacadePlan.Layout l : fp.layouts()) {
			Mass a = m.mass(l.face().mass());
			boolean house = a.kind().equals("stairhouse");
			List<String> kinds = new ArrayList<>(l.kinds());
			boolean changed = false;
			for (int i = 0; i < kinds.size(); i++) {
				if (!FacadePlan.WINDOW.equals(kinds.get(i))) continue;
				if (house || meets(l, i, a, b)) {
					kinds.set(i, FacadePlan.BLANK);
					changed = true;
				}
			}
			out.add(changed ? l.withKinds(kinds) : l);
		}
		c.setFacadePlan(new FacadePlan(out, fp.entrance(), fp.others()));
	}

	private static boolean meets(FacadePlan.Layout l, int bay, Mass a, TowerBlock b) {
		Face f = l.face();
		int ox = a.x() + 1, oz = a.z() + 1, w = a.sx() - 2, d = a.sz() - 2;
		int hallZ0 = b.hallZ0() - oz, hallZ1 = b.hallZ1() - oz, sz1 = b.stairwell()[1] + b.stairwell()[3] - oz;
		int mid = (b.shaftEast() ? b.stairwell()[0] + b.stairwell()[2] : b.shaft()[0] + b.shaft()[2]) - ox;
		int left = b.x0() - ox, right = b.x1() - 1 - ox;
		for (int k = 0; k < l.windowWidth(bay); k++) {
			int p = l.windowStart(bay) + k;
			int ix = f.insideX(p), iz = f.insideZ(p);
			int[] sw = b.stairwell();
			if (ix >= sw[0] && ix < sw[0] + sw[2] && iz >= sw[1] && iz < sw[1] + sw[3]) return true; // the stairwell has no windows: its flights alternate sides, so a window there would show on every other storey only (floor repeat, DS-25)
			int x = f.insideX(p) - ox, z = f.insideZ(p) - oz;
			if ((x == 0 || x == w - 1) && (z == hallZ0 - 1 || z == hallZ1 || sz1 < d && z == sz1)) return true;
			if (z == d - 1 && (x == left || x == right || sz1 >= d && x == mid)) return true; // the end columns run on to the back wall past a back room
		}
		return false;
	}
}
