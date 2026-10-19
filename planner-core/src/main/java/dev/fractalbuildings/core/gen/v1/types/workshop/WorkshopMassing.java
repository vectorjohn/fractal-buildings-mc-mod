package dev.fractalbuildings.core.gen.v1.types.workshop;

import static dev.fractalbuildings.core.gen.v1.mass.MassingOps.E;
import static dev.fractalbuildings.core.gen.v1.mass.MassingOps.S;
import static dev.fractalbuildings.core.gen.v1.mass.MassingOps.W;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.mass.MassingOps;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * Workshop massing: one tall open work hall (a single room, 5 or 6 high) at the front, a living
 * wing (one or two storeys) behind it or beside it, and sometimes a low lean-to on the free side of
 * the hall. Built from {@link MassingOps}; backs off to a shorter wing, then to a hall alone, so the
 * result always passes {@link MassingCheck}.
 */
final class WorkshopMassing {
	private WorkshopMassing() {}

	private static final int BIG = 1000, ORIGIN = 100;

	static Massing build(BuildContext c) {
		BuildingSpec sp = c.spec();
		Draws r = c.draws("massing/workshop");
		int hallH = Math.max(4, Math.min(5 + r.below(2), sp.maxHeight() - 2)); // 5 or 6, less under a low ceiling
		int plan = r.below(99); // < 50 wing behind, else wing beside; 99 (hall + lean-to only) is the last fallback
		boolean leanTo = r.chance(1, 3);
		for (int n = sp.storeys(); n >= 1; n--) {
			for (int variant = 0; variant < 2; variant++) {
				int p = variant == 0 ? plan : plan < 50 ? 60 : 10;
				for (int t = 0; t < 4; t++) {
					Massing m = attempt(c, p, n, hallH, leanTo && variant < 2, t);
					if (m != null) return m;
					m = attempt(c, p, n, hallH, false, t);
					if (m != null) return m;
				}
			}
		}
		for (int t = 0; t < 4; t++) {
			Massing m = attempt(c, 99, 1, hallH, true, t);
			if (m != null) return m;
		}
		Mass hall = hall(ORIGIN, ORIGIN, Math.min(sp.width(), 8), Math.min(sp.depth(), 6));
		return new Massing(new StoreyGrid(hallH), List.of(shift(hall, -ORIGIN, -ORIGIN)), List.of());
	}

	private static Mass hall(int x, int z, int w, int d) { return new Mass("main0", "main", x, z, w, d, 0, 1, null, true); }

	private static Mass shift(Mass m, int dx, int dz) {
		return new Mass(m.id(), m.kind(), m.x() + dx, m.z() + dz, m.sx(), m.sz(), m.from(), m.to(), m.parent(), m.open());
	}

	private static int rng(Draws r, int lo, int hi) { return hi < lo ? -1 : r.range(lo, hi); }

	/** One try: {@code plan} picks wing behind / beside / none; the wing has {@code n} storeys. Null if it doesn't fit. */
	static Massing attempt(BuildContext c, int plan, int n, int hallH, boolean leanTo, int t) {
		BuildingSpec sp = c.spec();
		int W = sp.width(), D = sp.depth();
		Draws r = c.draws("massing/workshop/try", plan, n, leanTo ? 1 : 0, t);
		List<Mass> lay = new ArrayList<>();
		Mass hall;
		Mass wing = null;
		if (plan < 50) { // wing behind (south)
			int hw = rng(r, 8, Math.min(W - (leanTo ? 5 : 0), 16)), hd = rng(r, leanTo ? 7 : 6, Math.min(D - 5, 10));
			if (hw < 0 || hd < 0) return null;
			hall = hall(ORIGIN, ORIGIN, hw, hd);
			int ww = rng(r, 5, Math.min(hw - 2, 10)), wd = rng(r, 5, Math.min(D - hd + 1, 12));
			if (ww < 0 || wd < 0) return null;
			wing = MassingOps.wing("wing1", hall, S, align(r), ww, wd, n, BIG, BIG);
		} else if (plan < 99) { // wing beside
			int hw = rng(r, 8, Math.min(W - (leanTo ? 9 : 4), 14)), hd = rng(r, 7, Math.min(D, 11));
			if (hw < 0 || hd < 0) return null;
			hall = hall(ORIGIN, ORIGIN, hw, hd);
			int ww = rng(r, 5, Math.min(hd - 2, 10)), wd = rng(r, 5, Math.min(W - hw + 1, 12));
			if (ww < 0 || wd < 0) return null;
			wing = MassingOps.wing("wing1", hall, r.chance(1, 2) ? E : W, align(r), ww, wd, n, BIG, BIG);
		} else { // hall alone (with a lean-to)
			int hw = rng(r, 8, Math.min(W - 4, 16)), hd = rng(r, 7, Math.min(D, 10));
			if (hw < 0 || hd < 0) return null;
			hall = hall(ORIGIN, ORIGIN, hw, hd);
			leanTo = true;
		}
		lay.add(hall);
		if (plan < 99) {
			if (wing == null) return null;
			lay.add(wing);
		}
		if (leanTo) {
			// on a side of the hall the wing doesn't use; one storey, narrow
			int side = r.chance(1, 2) ? E : W;
			if (wing != null && wing.z() < hall.maxZ() - 1) side = wing.x() >= hall.maxX() - 1 ? W : E; // wing beside: the other side
			int lw = rng(r, 5, Math.min(hall.sz() - 2, 7)), ld = rng(r, 5, 6);
			if (lw >= 0) {
				Mass lt = MassingOps.wing("lean2", hall, side, MassingOps.Align.START, lw, ld, 1, BIG, BIG);
				if (lt != null) lay.add(new Mass("lean2", "lean-to", lt.x(), lt.z(), lt.sx(), lt.sz(), 0, 1, null, false));
			}
		}
		// place in the envelope: x anywhere, z at the front edge
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = 0, z1 = 0;
		for (Mass m : lay) {
			x0 = Math.min(x0, m.x());
			z0 = Math.min(z0, m.z());
			x1 = Math.max(x1, m.maxX());
			z1 = Math.max(z1, m.maxZ());
		}
		if (x1 - x0 > W || z1 - z0 > D) return null;
		// the hall stays at z 0 (its front on the lot's front margin); only x moves
		int dx = r.range(0, W - (x1 - x0)) - x0, dz = -z0;
		List<Mass> placed = new ArrayList<>();
		for (Mass m : lay) placed.add(shift(m, dx, dz));
		int top = 1;
		for (Mass m : placed) top = Math.max(top, m.to());
		StoreyGrid g = StoreyGrid.of(top, hallH, c.style().massing().typical());
		if (g.y(top) + 2 > sp.maxHeight()) return null;
		List<Core> cores = new ArrayList<>();
		Mass w = placed.size() > 1 && placed.get(1).id().equals("wing1") ? placed.get(1) : null;
		if (w != null && w.to() >= 2) {
			List<Mass> others = new ArrayList<>(placed);
			others.remove(w);
			Core k = MassingOps.core(g, List.of(w), others, 0, w.to(), r);
			if (k == null) return null;
			cores.add(k);
		}
		Massing m = new Massing(g, placed, cores);
		return MassingCheck.check(m, W, D, sp.maxHeight()).isEmpty() ? m : null;
	}

	private static MassingOps.Align align(Draws r) {
		return switch (r.below(3)) {
			case 0 -> MassingOps.Align.START;
			case 1 -> MassingOps.Align.CENTRE;
			default -> MassingOps.Align.END;
		};
	}
}
