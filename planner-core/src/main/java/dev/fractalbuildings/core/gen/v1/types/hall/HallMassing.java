package dev.fractalbuildings.core.gen.v1.types.hall;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.mass.MassingOps;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.style.MassingStyle;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * Hall / civic massing: four sub-kinds drawn per building, each built from open (one tall space)
 * masses so a wide span is one room, with stairs only in small non-open tower or annex masses.
 *
 * <ul>
 *   <li>{@code market}: an open arcade storey under an open upper hall of the same footprint, and a
 *       stair annex on one side that joins both floors.
 *   <li>{@code town}: a tower at the front (the main mass, with the entrance and the stair core) and
 *       a tall open hall behind it, joined along the hall's front wall.
 *   <li>{@code cathedral}: a nave flanked by lower aisles, a transept across the end of the nave, a
 *       narrower choir behind it, and one or two bell towers (with cores) beside the aisles at the
 *       front. No crossing tower: a core cannot stand in an open mass (rule M8), see the log.
 *   <li>{@code bath}: open ranges round a courtyard (front range, two side ranges, a back range).
 * </ul>
 *
 * A draw that doesn't fit the lot is retried with other sizes, then the next sub-kind is tried, and
 * the last resort is a plain open hall; the result always passes {@link MassingCheck}.
 */
public final class HallMassing {
	private HallMassing() {}

	public enum Kind { MARKET, TOWN, CATHEDRAL, BATH }

	/** Kind weights: market, town, cathedral, bath. */
	private static final int[] WEIGHT = {3, 3, 2, 2};
	private static final int TRIES = 6;

	public static Massing build(BuildContext c) {
		Kind first = pick(c.draws("massing/hall-kind"));
		List<Kind> order = new ArrayList<>();
		order.add(first);
		for (Kind k : Kind.values()) if (k != first) order.add(k);
		for (Kind k : order) {
			for (int t = 0; t < TRIES; t++) {
				Massing m = attempt(c, k, c.draws("massing/hall", k.ordinal(), t));
				if (m != null) return m;
			}
		}
		return fallback(c);
	}

	/** The sub-kind a building is drawn as, from its mass kinds (for tests and galleries). */
	public static Kind kindOf(Massing m) {
		for (Mass a : m.masses()) {
			switch (a.kind()) {
				case "nave": return Kind.CATHEDRAL;
				case "range": return Kind.BATH;
				case "tower": return Kind.TOWN;
				case "arcade": return Kind.MARKET;
				default: break;
			}
		}
		return null; // the plain fallback hall
	}

	static Kind pick(Draws r) {
		int total = 0;
		for (int w : WEIGHT) total += w;
		int x = r.below(total);
		for (int i = 0; i < WEIGHT.length; i++) {
			x -= WEIGHT[i];
			if (x < 0) return Kind.values()[i];
		}
		return Kind.MARKET;
	}

	static Massing attempt(BuildContext c, Kind k, Draws r) {
		return switch (k) {
			case MARKET -> market(c, r);
			case TOWN -> town(c, r);
			case CATHEDRAL -> cathedral(c, r);
			case BATH -> bath(c, r);
		};
	}

	// ---- helpers ----

	/** Uniform int in [lo, hi], or -1 if the range is empty. */
	private static int rng(Draws r, int lo, int hi) { return hi < lo ? -1 : r.range(lo, hi); }

	private static StoreyGrid grid(BuildContext c, int count, Draws r) {
		MassingStyle st = c.style().massing();
		int ground = Math.max(5, st.ground()) + (r.chance(1, 3) ? 1 : 0);
		return StoreyGrid.of(count, ground, Math.max(5, st.typical()));
	}

	/** Offset {dx, dz} of a {@code bw x bd} plan in the envelope: x centred or random, z near the front. */
	private static int[] place(Draws r, int envW, int envD, int bw, int bd) {
		if (bw > envW || bd > envD) return null;
		int dx = r.chance(2, 3) ? (envW - bw) / 2 : r.range(0, envW - bw);
		return new int[] {dx, r.range(0, Math.min(2, envD - bd))};
	}

	private static Mass open(String id, String kind, int x, int z, int sx, int sz, int from, int to, String parent) {
		return new Mass(id, kind, x, z, sx, sz, from, to, parent, true);
	}

	private static Massing done(BuildContext c, StoreyGrid g, List<Mass> ms, List<Core> cores) {
		Massing m = new Massing(g, ms, cores);
		BuildingSpec sp = c.spec();
		return MassingCheck.check(m, sp.width(), sp.depth(), sp.maxHeight()).isEmpty() ? m : null;
	}

	// ---- market hall ----

	static Massing market(BuildContext c, Draws r) {
		BuildingSpec sp = c.spec();
		int upper = sp.storeys() >= 2 && r.chance(1, 2) ? 2 : 1;
		int ax = rng(r, 8, 10), ad = rng(r, 10, 14);
		int w = rng(r, 16, Math.min(36, sp.width() - ax + 1)), d = rng(r, Math.max(20, ad), Math.min(sp.depth(), 120));
		if (w < 0 || d < 0) return null;
		int bw = w + ax - 1;
		int[] o = place(r, sp.width(), sp.depth(), bw, d);
		if (o == null) return null;
		boolean east = r.chance(1, 2);
		int mx = o[0] + (east ? 0 : ax - 1), ax0 = east ? mx + w - 1 : o[0];
		int az = o[1] + r.range(0, d - ad);
		StoreyGrid g = grid(c, 1 + upper, r);
		Mass arcade = open("main0", "arcade", mx, o[1], w, d, 0, 1, null);
		Mass hall = open("hall1", "hall", mx, o[1], w, d, 1, 1 + upper, arcade.id());
		Mass annex = Mass.ground("annex2", "annex", ax0, az, ax, ad, 1 + upper);
		Core k = MassingOps.core(g, List.of(annex), List.of(arcade, hall), 0, 1 + upper, r);
		if (k == null) return null;
		return done(c, g, List.of(arcade, hall, annex), List.of(k));
	}

	// ---- town hall ----

	static Massing town(BuildContext c, Draws r) {
		BuildingSpec sp = c.spec();
		int tw = rng(r, 9, 13), tz = rng(r, 9, 13);
		int hw = rng(r, 20, Math.min(sp.width(), 44));
		if (hw < 0) return null;
		int hd = rng(r, 15, Math.min(sp.depth() - tz + 1, 70));
		if (hd < 0) return null;
		int hs = sp.storeys() >= 3 ? 3 : 2;
		int tn = Math.max(hs + 1, Math.min(7, 3 + sp.storeys() + r.range(0, 1)));
		int bd = tz - 1 + hd;
		int[] o = place(r, sp.width(), sp.depth(), hw, bd);
		if (o == null) return null;
		int off = r.chance(2, 3) ? (hw - tw) / 2 : r.range(0, hw - tw);
		StoreyGrid g = grid(c, Math.max(tn, hs), r);
		Mass tower = Mass.ground("tower0", "tower", o[0] + off, o[1], tw, tz, tn);
		Mass hall = open("hall1", "hall", o[0], o[1] + tz - 1, hw, hd, 0, hs, null);
		Core k = MassingOps.coreAnywhere(g, List.of(tower), List.of(hall), 0, tn, r);
		if (k == null) return null;
		return done(c, g, List.of(tower, hall), List.of(k));
	}

	// ---- cathedral ----

	static Massing cathedral(BuildContext c, Draws r) {
		BuildingSpec sp = c.spec();
		int ns = sp.storeys() >= 2 ? 3 : 2;
		int aw = rng(r, 6, 8), nw = rng(r, 15, 21);
		int towers = r.below(3); // 0, 1 or 2 bell towers
		int tw = rng(r, 8, 10), tn = Math.max(ns + 1, Math.min(7, 4 + sp.storeys() + r.range(0, 1)));
		int across = nw + 2 * (aw - 1);
		int ta = towers == 0 ? rng(r, 6, 10) : tw - 1 + r.range(1, 3);
		int bw = across + 2 * ta;
		int ts = rng(r, 13, 17);
		int cl = rng(r, 12, 22);
		if (bw > sp.width()) return null;
		int nl = rng(r, 18, Math.min(sp.depth() - ts - cl + 2, 70));
		if (nl < 0) return null;
		int cw = nw - 2 * r.range(1, 3);
		int bd = nl + ts + cl - 2;
		int[] o = place(r, sp.width(), sp.depth(), bw, bd);
		if (o == null) return null;
		int count = towers == 0 ? ns : Math.max(ns, tn);
		StoreyGrid g = grid(c, count, r);
		int x0 = o[0], z0 = o[1];
		int ax = x0 + ta, nx = ax + aw - 1;
		List<Mass> ms = new ArrayList<>();
		ms.add(open("nave0", "nave", nx, z0, nw, nl, 0, ns, null));
		ms.add(open("aisle1", "aisle", ax, z0, aw, nl, 0, 1, null));
		ms.add(open("aisle2", "aisle", nx + nw - 1, z0, aw, nl, 0, 1, null));
		ms.add(open("transept3", "transept", x0, z0 + nl - 1, bw, ts, 0, ns, null));
		ms.add(open("choir4", "choir", nx + (nw - cw) / 2, z0 + nl + ts - 2, cw, cl, 0, Math.max(1, ns - 1), null));
		List<Core> cores = new ArrayList<>();
		for (int t = 0; t < towers; t++) {
			boolean left = t == 0;
			Mass aisle = left ? ms.get(1) : ms.get(2);
			Mass tower = Mass.ground("tower" + (5 + t), "tower", left ? aisle.x() - tw + 1 : aisle.maxX() - 1, z0, tw, tw, tn);
			Core k = MassingOps.coreAnywhere(g, List.of(tower), List.of(aisle), 0, tn, r);
			if (k == null) return null;
			ms.add(tower);
			cores.add(k);
		}
		return done(c, g, ms, cores);
	}

	// ---- bath ----

	static Massing bath(BuildContext c, Draws r) {
		BuildingSpec sp = c.spec();
		int w = rng(r, 28, Math.min(sp.width(), 150)), d = rng(r, 28, Math.min(sp.depth(), 120));
		if (w < 0 || d < 0) return null;
		int md = rng(r, 10, Math.min(20, d - 18)); // front range depth
		int w1 = rng(r, 9, Math.min(20, (w - 6) / 2)), w2 = rng(r, 9, Math.min(20, w - 6 - w1));
		int rd = rng(r, 8, Math.min(20, d - md - 6));
		if (md < 0 || w1 < 0 || w2 < 0 || rd < 0) return null;
		int[] o = place(r, sp.width(), sp.depth(), w, d);
		if (o == null) return null;
		int front = sp.storeys() >= 2 && r.chance(1, 2) ? 2 : 1;
		StoreyGrid g = grid(c, front, r);
		int x0 = o[0], z0 = o[1];
		Mass main = open("range0", "range", x0, z0, w, md, 0, front, null);
		Mass a = open("range1", "range", x0, z0 + md - 1, w1, d - md + 1, 0, 1, null);
		Mass b = open("range2", "range", x0 + w - w2, z0 + md - 1, w2, d - md + 1, 0, 1, null);
		int rx = a.maxX() - 1;
		Mass back = open("range3", "range", rx, z0 + d - rd, b.x() + 1 - rx, rd, 0, 1, null);
		return done(c, g, List.of(main, a, b, back), List.of());
	}

	// ---- last resort ----

	/** One open hall, as big as the envelope allows up to 36 x 60: always fits a type's minimum envelope. */
	static Massing fallback(BuildContext c) {
		BuildingSpec sp = c.spec();
		int w = Math.min(sp.width(), 36), d = Math.min(sp.depth(), 60);
		StoreyGrid g = StoreyGrid.of(1, Math.max(4, Math.min(5, sp.maxHeight() - 2)), 5);
		Mass hall = open("main0", "hall", (sp.width() - w) / 2, 0, w, d, 0, 1, null);
		return new Massing(g, List.of(hall), List.of());
	}
}
