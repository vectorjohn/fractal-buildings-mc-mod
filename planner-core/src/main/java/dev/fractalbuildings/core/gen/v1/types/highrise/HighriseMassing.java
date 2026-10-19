package dev.fractalbuildings.core.gen.v1.types.highrise;

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
 * High-rise massing: a podium on the street (1 to 3 storeys, 5 high on the ground), a shaft in bands
 * that step back every 8 to 20 storeys (insets 1 to 4 per side, drawn per side), a crown (1 to 3
 * storeys) and one core through all storeys. The storey count is the spec's, capped so the top slab
 * plus a parapet layer stays under {@code maxHeight} (which the spec policy derives from the y 319
 * ceiling). If a draw doesn't fit it backs off to fewer setbacks, then to a plain box.
 */
final class HighriseMassing {
	private HighriseMassing() {}

	static final int GROUND = 5, TYPICAL = 4, MIN_BAND = 8, MAX_BAND = 20, MAX_INSET = 4;
	/** Smallest footprint side of any tier (a 2 x 6 core and its approaches fit). */
	static final int MIN_SIDE = 10;
	/** Blocks above the top slab kept free (flat roof layer plus a parapet). */
	static final int ROOF_ROOM = 3;

	/** Most storeys that fit under {@code maxHeight}. */
	static int capacity(int maxHeight) {
		int n = 1;
		while (n < 200 && 5 + TYPICAL * n + ROOF_ROOM <= maxHeight) n++;
		return n;
	}

	static Massing build(BuildContext c) {
		BuildingSpec sp = c.spec();
		int n = Math.max(1, Math.min(sp.storeys(), capacity(sp.maxHeight())));
		for (; n >= 1; n--) {
			for (int setbacks = 3; setbacks >= 0; setbacks--) {
				Massing m = attempt(c, n, setbacks);
				if (m != null) return m;
			}
		}
		int ground = Math.max(StoreyGrid.MIN_HEIGHT, Math.min(GROUND, sp.maxHeight() - 2));
		Mass main = Mass.ground("main0", "main", 0, 0, Math.min(sp.width(), 5), Math.min(sp.depth(), 5), 1);
		return new Massing(new StoreyGrid(ground), List.of(main), List.of());
	}

	/** One try: {@code n} storeys with up to {@code maxSteps} setbacks; null if it doesn't pass the massing rules. */
	static Massing attempt(BuildContext c, int n, int maxSteps) {
		BuildingSpec sp = c.spec();
		int W = sp.width(), D = sp.depth();
		Draws r = c.draws("massing/highrise", n, maxSteps);
		StoreyGrid g = StoreyGrid.of(n, GROUND, TYPICAL);
		if (g.y(n) + 2 > sp.maxHeight()) return null;
		if (n == 1) {
			Mass m = Mass.ground("main0", "main", 0, 0, Math.min(W, 8), Math.min(D, 8), 1);
			return check(c, new Massing(g, List.of(m), List.of()));
		}
		// podium on the street: nearly the whole envelope, trimmed a little on the sides and back
		int tx0 = r.range(0, Math.min(2, (W - MIN_SIDE) / 2)), tx1 = r.range(0, Math.min(2, (W - MIN_SIDE) / 2));
		int tz1 = r.range(0, Math.min(3, D - MIN_SIDE));
		Mass base = Mass.ground("main0", "main", tx0, 0, W - tx0 - tx1, D - tz1, 1);
		if (base.sx() < MIN_SIDE || base.sz() < MIN_SIDE) return null;
		int podium = n >= 6 ? r.range(1, 3) : 1;
		int crown = n >= 8 ? r.range(1, 3) : 0;
		int shaft = n - podium - crown;
		if (shaft < 1) return null;
		List<Integer> bands = bands(r, shaft, maxSteps);
		List<Mass> masses = new ArrayList<>();
		Mass cur = new Mass("main0", "main", base.x(), base.z(), base.sx(), base.sz(), 0, podium, null, false);
		masses.add(cur);
		int top = podium;
		for (int i = 0; i <= bands.size(); i++) {
			boolean isCrown = i == bands.size();
			int len = isCrown ? crown : bands.get(i);
			if (len == 0) break;
			int[] inset = insets(r, cur);
			if (inset == null) return null;
			Mass next = MassingOps.setback((isCrown ? "crown" : "shaft") + masses.size(), cur, inset, top + len);
			if (next == null) return null;
			next = new Mass(next.id(), isCrown ? "crown" : "shaft", next.x(), next.z(), next.sx(), next.sz(), next.from(), next.to(), next.parent(), false);
			if (Math.min(next.sx(), next.sz()) < MIN_SIDE) return null;
			masses.add(next);
			cur = next;
			top += len;
		}
		Core k = MassingOps.core(g, masses, List.of(), 0, n, c.draws("massing/highrise-core", n, maxSteps));
		if (k == null) return null;
		return check(c, new Massing(g, masses, List.of(k)));
	}

	/** Shaft band lengths: setbacks every 8 to 20 storeys, at most {@code maxSteps} bands after the first. */
	static List<Integer> bands(Draws r, int shaft, int maxSteps) {
		List<Integer> out = new ArrayList<>();
		int left = shaft;
		while (left > 0) {
			int len = out.size() > maxSteps || left < 2 * MIN_BAND ? left : r.range(MIN_BAND, Math.min(MAX_BAND, left - MIN_BAND));
			if (out.size() == maxSteps) len = left;
			out.add(len);
			left -= len;
		}
		return out;
	}

	/** Per-side insets, each 0 to 4, at least one side stepping in, every tier staying {@link #MIN_SIDE} across; null if none can. */
	static int[] insets(Draws r, Mass cur) {
		int k = r.range(1, MAX_INSET);
		int[] v = new int[4];
		for (int i = 0; i < 4; i++) v[i] = r.chance(2, 3) ? r.range(1, k) : 0;
		if (v[0] + v[1] + v[2] + v[3] == 0) v[r.below(4)] = k;
		while (cur.sx() - v[1] - v[3] < MIN_SIDE && v[1] + v[3] > 0) { v[1] = Math.max(0, v[1] - 1); v[3] = Math.max(0, v[3] - 1); }
		while (cur.sz() - v[0] - v[2] < MIN_SIDE && v[0] + v[2] > 0) { v[0] = Math.max(0, v[0] - 1); v[2] = Math.max(0, v[2] - 1); }
		return v[0] + v[1] + v[2] + v[3] == 0 ? null : v;
	}

	private static Massing check(BuildContext c, Massing m) {
		return MassingCheck.check(m, c.spec().width(), c.spec().depth(), c.spec().maxHeight()).isEmpty() ? m : null;
	}
}
