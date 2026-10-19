package dev.fractalbuildings.core.gen.v1.types.temple;

import static dev.fractalbuildings.core.gen.v1.mass.MassingOps.E;
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
 * Temple massing, all on one axis (the nave's centre line, every width odd so the axis is a cell
 * column): front block (a porch, or a bell tower with a stair core) first and carrying the main
 * entrance, then the nave (one open space 2 to 3 storeys tall, at least 7 wide inside), optionally
 * two side chapels (transepts) at the middle of the nave, and a narrower apse at the back whose
 * walls start in the back third of the building, so the altar hook (placed in the apse room) is
 * in the back third. If a draw doesn't fit the lot or the height cap, it backs off: no tower, no
 * chapels, a lower nave; the last resort (one 4-high storey) fits every legal envelope.
 */
final class TempleMassing {
	private TempleMassing() {}

	static final String PORCH = "porch", TOWER = "tower", NAVE = "nave", APSE = "apse", TRANSEPT = "transept";

	/** Smallest nave depth (outer) that still reads as a hall. */
	private static final int NAVE_MIN_DEPTH = 8;
	private static final int TOWER_STOREY = 4;

	/** What to try: nave storeys, tower, chapels. */
	record Variant(int storeys, boolean tower, boolean chapels) {}

	static Massing build(BuildContext c) {
		BuildingSpec sp = c.spec();
		Draws r = c.draws("massing/temple");
		int n = sp.storeys();
		boolean tower = sp.depth() >= 28 && r.chance(2, 5);
		boolean chapels = sp.width() >= 17 && r.chance(1, 2);
		List<Variant> tries = new ArrayList<>();
		for (int s = n; s >= 1; s--) {
			tries.add(new Variant(s, tower, chapels));
			if (tower) tries.add(new Variant(s, false, chapels));
			if (chapels) tries.add(new Variant(s, false, false));
			if (tower && chapels) tries.add(new Variant(s, tower, false));
		}
		int i = 0;
		for (Variant v : tries) {
			Massing m = attempt(c, v, i++);
			if (m != null) return m;
		}
		throw new IllegalStateException("temple fits no massing in " + sp.width() + "x" + sp.depth() + " under " + sp.maxHeight());
	}

	/** One try; null if it doesn't fit or breaks the contract. */
	static Massing attempt(BuildContext c, Variant v, int index) {
		BuildingSpec sp = c.spec();
		int W = sp.width(), D = sp.depth(), n = v.storeys();
		Draws r = c.draws("massing/temple-shape", index, v.storeys(), v.tower() ? 1 : 0, v.chapels() ? 1 : 0);
		int h = n == 1 ? 4 + r.range(0, 1) : r.range(4, n == 2 ? 6 : 5);
		h = Math.min(h, (sp.maxHeight() - 2) / n); // the nave's top slab plus a roof layer stay under the cap
		if (h < 4) return null;
		// nave: odd width, centred (floor) in the envelope; chapels need 4 more blocks on each side
		int wmax = Math.min(17, v.chapels() ? W - 8 : W);
		int nw = odd(r, 9, wmax);
		if (nw < 0) return null;
		int nx = (W - nw) / 2;
		int tTotal = D - r.range(0, D / 8);
		int apseZ = (2 * tTotal + 2) / 3 - 1; // apse interior starts at 2/3 of the building depth
		int fdMax = apseZ + 2 - NAVE_MIN_DEPTH;
		// front block
		Mass front;
		int fd;
		int towerStoreys = n;
		int apexRise = (nw + 1) / 2;
		if (v.tower()) {
			int tw = nw >= 11 && r.chance(1, 2) ? 11 : 9;
			fd = h + 7;
			towerStoreys = n + Math.max(1, Math.min(3, (apexRise + 1 + TOWER_STOREY - 1) / TOWER_STOREY));
			front = Mass.ground("tower0", TOWER, nx + (nw - tw) / 2, 0, tw, fd, towerStoreys);
		} else {
			int pw = r.chance(1, 2) && fdMax >= 8 ? 7 : 5;
			fd = Math.min(fdMax, pw + 1 + (pw == 5 ? r.range(0, 1) : 1));
			if (fd < 6) return null;
			Mass p = Mass.ground("porch0", PORCH, nx + (nw - pw) / 2, 0, pw, fd, 1);
			front = new Mass(p.id(), p.kind(), p.x(), p.z(), p.sx(), p.sz(), 0, 1, null, true);
		}
		if (fd > fdMax) return null;
		int nz = fd - 1;
		int nd = apseZ + 1 - nz;
		if (nd < NAVE_MIN_DEPTH) return null;
		Mass nave = new Mass("nave0", NAVE, nx, nz, nw, nd, 0, n, null, true);
		// apse
		int aw = odd(r, 5, nw - 2);
		if (aw < 0) return null;
		int ad = tTotal - apseZ;
		if (ad < 5) return null;
		int apseTo = n >= 2 && r.chance(3, 5) ? n - 1 : n;
		Mass apse = new Mass("apse0", APSE, nx + (nw - aw) / 2, apseZ, aw, ad, 0, apseTo, null, true);
		List<Mass> ms = new ArrayList<>(List.of(front, nave));
		if (v.chapels()) {
			int cw = odd(r, 5, Math.min(7, nd - 4));
			int cd = r.range(5, 6);
			if (cw < 0) return null;
			int to = n >= 2 ? n - 1 : 1;
			Mass e = MassingOps.wing("transept1", nave, E, MassingOps.Align.CENTRE, cw, cd, to, W, D);
			Mass w = MassingOps.wing("transept2", nave, W, MassingOps.Align.CENTRE, cw, cd, to, W, D);
			if (e == null || w == null) return null;
			ms.add(open(e));
			ms.add(open(w));
		}
		ms.add(apse);
		int[] heights = new int[towerStoreys];
		for (int s = 0; s < heights.length; s++) heights[s] = s < n ? h : TOWER_STOREY;
		StoreyGrid g = new StoreyGrid(heights);
		List<Core> cores = new ArrayList<>();
		if (v.tower()) {
			Core k = new Core(front.x() + 1, front.z() + 3, 2, Core.length(g, 0, towerStoreys), 0, towerStoreys);
			cores.add(k);
		}
		Massing m = new Massing(g, ms, cores);
		return MassingCheck.check(m, W, D, sp.maxHeight()).isEmpty() ? m : null;
	}

	private static Mass open(Mass m) {
		return new Mass(m.id(), TRANSEPT, m.x(), m.z(), m.sx(), m.sz(), m.from(), m.to(), m.parent(), true);
	}

	/** A uniform odd int in [lo, hi] (lo odd), or -1 if there is none. */
	private static int odd(Draws r, int lo, int hi) {
		if (hi % 2 == 0) hi--;
		return hi < lo ? -1 : lo + 2 * r.range(0, (hi - lo) / 2);
	}
}
