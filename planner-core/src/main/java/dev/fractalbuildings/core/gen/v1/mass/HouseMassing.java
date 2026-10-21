package dev.fractalbuildings.core.gen.v1.mass;

import static dev.fractalbuildings.core.gen.v1.mass.MassingOps.E;
import static dev.fractalbuildings.core.gen.v1.mass.MassingOps.N;
import static dev.fractalbuildings.core.gen.v1.mass.MassingOps.S;
import static dev.fractalbuildings.core.gen.v1.mass.MassingOps.W;

import dev.fractalbuildings.core.gen.v1.BuildContext;
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
 * House massing from {@link MassingOps}: a footprint plan (box, L, T, U, courtyard) with wings that
 * differ from the main block in width and height, then, on the main block, upper storeys that
 * overhang (jetty) or step back (setback) so that storeys have different footprints, each differing
 * storey its own volume with the one below as its parent. Plan, jetty and setback chances come from
 * {@link MassingStyle}. If a draw doesn't fit the lot it falls back to a simpler plan, then to fewer
 * storeys; the result always passes {@link MassingCheck}.
 */
public final class HouseMassing {
	private HouseMassing() {}

	public enum Plan { BOX, L, T, U, COURTYARD }

	private static final int BIG = 1000, ORIGIN = 100;

	/** Wall-side interior cells a mass must keep clear of its stair core and its margin. */
	static final int MIN_FREE = 5;

	/** Chance in 100 of a jetty on a house whose lot has air rights over the street (when its style has jetties at all). */
	public static final int AIR_JETTY_PCT = 75;

	/** The massing for the context's spec: its storeys unless the lot or height cap can't hold them. */
	public static Massing build(BuildContext c, int maxStoreys) {
		BuildingSpec sp = c.spec();
		MassingStyle st = c.style().massing();
		Plan pick = pick(st, c.draws("massing/plan"));
		for (int n = sp.storeys(); n >= 1; n--) {
			Massing m = attempt(c, pick, n, maxStoreys);
			if (m != null) return m;
			for (Plan p : new Plan[] {Plan.COURTYARD, Plan.U, Plan.T, Plan.L, Plan.BOX}) {
				if (p == pick) continue;
				m = attempt(c, p, n, maxStoreys);
				if (m != null) return m;
			}
		}
		// last resort: one plain storey (always fits a type's minimum envelope)
		Mass main = Mass.ground("main0", "main", 0, 0, Math.min(sp.width(), 5), Math.min(sp.depth(), 5), 1);
		return new Massing(new StoreyGrid(st.ground()), List.of(main), List.of());
	}

	static Plan pick(MassingStyle st, Draws r) {
		String[] names = {"box", "L", "T", "U", "courtyard"};
		int total = 0;
		for (String n : names) total += st.plan(n);
		if (total == 0) return Plan.BOX;
		int x = r.below(total);
		for (int i = 0; i < names.length; i++) {
			x -= st.plan(names[i]);
			if (x < 0) return Plan.values()[i];
		}
		return Plan.BOX;
	}

	/** One try at a plan with {@code n} main storeys; null if it doesn't fit. */
	static Massing attempt(BuildContext c, Plan plan, int n, int maxStoreys) {
		BuildingSpec sp = c.spec();
		MassingStyle st = c.style().massing();
		int lead = sp.lead(), W = sp.width(), D = sp.depth() - lead; // lead: envelope rows in front of the usual one (air rights); the ground masses keep the usual envelope
		Draws r = c.draws("massing/house", plan.ordinal(), n);
		List<Mass> lay = layout(plan, r, W, D, n, maxStoreys);
		if (lay == null) return null;
		// place the plan in the envelope: x anywhere, z near the front
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = 0, z1 = 0, top = n;
		for (Mass m : lay) {
			x0 = Math.min(x0, m.x());
			z0 = Math.min(z0, m.z());
			x1 = Math.max(x1, m.maxX());
			z1 = Math.max(z1, m.maxZ());
			top = Math.max(top, m.to());
		}
		if (x1 - x0 > W || z1 - z0 > D) return null;
		int dx = r.range(0, W - (x1 - x0)) - x0, dz = (lead > 0 ? lead : r.range(Math.min(1, D - (z1 - z0)), Math.min(2, D - (z1 - z0)))) - z0; // with air rights the front wall stands at the usual front edge, so the jetty can reach past the lot
		List<Mass> ground = new ArrayList<>();
		for (Mass m : lay) ground.add(new Mass(m.id(), m.kind(), m.x() + dx, m.z() + dz, m.sx(), m.sz(), m.from(), m.to(), m.parent(), m.open()));
		StoreyGrid g = MassingOps.grid(st, top, r);
		if (g.y(top) + 2 > sp.maxHeight()) return null;
		Build b = new Build(c, ground, g, n, r);
		return b.best();
	}

	/** Mass layout in a plan frame (main block at ORIGIN): main first, then wings; null if the plan can't be drawn. */
	static List<Mass> layout(Plan plan, Draws r, int W, int D, int n, int maxS) {
		List<Mass> out = new ArrayList<>();
		switch (plan) {
			case BOX -> {
				int mw = rng(r, n >= 2 && Math.min(W, 15) >= 7 ? 7 : 5, Math.min(W, 15)), md = rng(r, 5, Math.min(D, 11));
				if (mw < 0 || md < 0) return null;
				out.add(Mass.ground("main0", "main", ORIGIN, ORIGIN, mw, md, n));
			}
			case L -> {
				boolean south = Math.min(W, 15) >= 7 && Math.min(D - 4, 9) >= 5;
				boolean side = Math.min(W - 4, 9) >= 5 && Math.min(D, 11) >= 7;
				if (!south && !side) return null;
				if (south && side ? r.chance(1, 2) : south) {
					int mw = rng(r, 7, Math.min(W, 15)), md = rng(r, 5, Math.min(D - 4, 9));
					Mass main = Mass.ground("main0", "main", ORIGIN, ORIGIN, mw, md, n);
					int ww = rng(r, 5, mw - 2), wd = rng(r, 5, Math.min(D - md + 1, 12));
					Mass w = MassingOps.wing("wing1", main, S, r.chance(1, 2) ? MassingOps.Align.START : MassingOps.Align.END, ww, wd, wingStoreys(r, n, maxS), BIG, BIG);
					if (w == null) return null;
					out.add(main);
					out.add(w);
				} else {
					int mw = rng(r, 5, Math.min(W - 4, 9)), md = rng(r, 7, Math.min(D, 11));
					Mass main = Mass.ground("main0", "main", ORIGIN, ORIGIN, mw, md, n);
					int ww = rng(r, 5, md - 2), wd = rng(r, 5, Math.min(W - mw + 1, 12));
					int sd = r.chance(1, 2) ? E : W;
					Mass w = MassingOps.wing("wing1", main, sd, r.chance(1, 2) ? MassingOps.Align.START : MassingOps.Align.END, ww, wd, wingStoreys(r, n, maxS), BIG, BIG);
					if (w == null) return null;
					out.add(main);
					out.add(w);
				}
			}
			case T -> {
				int mw = rng(r, 9, Math.min(W, 17)), md = rng(r, 5, Math.min(D - 4, 9));
				if (mw < 0 || md < 0) return null;
				Mass main = Mass.ground("main0", "main", ORIGIN, ORIGIN, mw, md, n);
				int ww = rng(r, 5, Math.min(mw - 4, 9)), wd = rng(r, 5, Math.min(D - md + 1, 12));
				Mass w = MassingOps.wing("wing1", main, S, MassingOps.Align.CENTRE, ww, wd, wingStoreys(r, n, maxS), BIG, BIG);
				if (w == null) return null;
				out.add(main);
				out.add(w);
			}
			case U, COURTYARD -> {
				boolean court = plan == Plan.COURTYARD;
				int mw = rng(r, 14, Math.min(W, 22)), md = rng(r, 5, Math.min(D - (court ? 6 : 4), 7));
				if (mw < 0 || md < 0) return null;
				Mass main = Mass.ground("main0", "main", ORIGIN, ORIGIN, mw, md, n);
				int w1 = rng(r, 5, Math.min(9, mw - 9));
				if (w1 < 0) return null;
				int w2 = rng(r, 5, Math.min(9, mw - 4 - w1));
				int wlo = court ? 7 : 5, whi = Math.min(D - md + 1, 12);
				int d1 = rng(r, wlo, whi), d2 = court ? d1 : rng(r, wlo, whi);
				if (w2 < 0 || d1 < 0 || d2 < 0) return null;
				Mass a = MassingOps.wing("wing1", main, S, MassingOps.Align.START, w1, d1, wingStoreys(r, n, maxS), BIG, BIG);
				Mass b = MassingOps.wing("wing2", main, S, MassingOps.Align.END, w2, d2, wingStoreys(r, n, maxS), BIG, BIG);
				if (a == null || b == null) return null;
				out.add(main);
				out.add(a);
				out.add(b);
				if (court) {
					int rd = rng(r, 5, d1 - 2);
					int rx = a.maxX() - 1, rs = b.x() + 1 - rx;
					int rs0 = r.chance(1, 2) ? a.to() : b.to();
					if (rd < 0) return null;
					out.add(Mass.ground("range3", "range", rx, a.maxZ() - rd, rs, rd, rs0));
				}
			}
		}
		return out;
	}

	/** Wing storeys: usually one lower than the main block, sometimes the same, sometimes one more. */
	static int wingStoreys(Draws r, int n, int maxS) {
		int x = r.below(100);
		int w = x < 40 ? n - 1 : x < 75 ? n : n + 1;
		return Math.max(1, Math.min(w, Math.max(maxS, 1)));
	}

	/** Uniform int in [lo, hi], or -1 if the range is empty. */
	private static int rng(Draws r, int lo, int hi) { return hi < lo ? -1 : r.range(lo, hi); }

	/** The main stack and cores for one placed layout. */
	private static final class Build {
		final BuildContext c;
		final BuildingSpec sp;
		final MassingStyle st;
		final List<Mass> ground;
		final StoreyGrid g;
		final int n;
		final Draws r;

		Build(BuildContext c, List<Mass> ground, StoreyGrid g, int n, Draws r) {
			this.c = c;
			this.sp = c.spec();
			this.st = c.style().massing();
			this.ground = ground;
			this.g = g;
			this.n = n;
			this.r = r;
		}

		/** Blocks of air rights over the street in front of the lot (0: none). */
		int air() { return sp.lead() == 0 ? 0 : sp.lead() - yard(); }

		/** The front margin: blocks between the lot edge and the usual envelope. */
		int yard() { return c.type().limits().front(); }

		/** Jetty or setback or neither, per style chances, backing off to simpler shapes when a draw doesn't fit. */
		Massing best() {
			if (n >= 2) {
				int x = r.below(100);
				int jc = air() > 0 ? Math.max(st.jettyChance(), AIR_JETTY_PCT) : st.jettyChance(); // a street with air rights invites jetties
				if (st.jetty() > 0 && x < jc) {
					int mx = Math.min(st.jetty(), MassingCheck.JETTY_MAX);
					int[] v = {r.chance(4, 5) ? r.range(1, mx) : 0, r.chance(1, 2) ? r.range(1, mx) : 0, r.chance(1, 3) ? r.range(1, mx) : 0, r.chance(1, 2) ? r.range(1, mx) : 0};
					if (v[N] + v[E] + v[S] + v[W] == 0) v[N] = 1;
					boolean step2 = n == 3 && r.chance(1, 2);
					if (air() > 0) { // over the street: front overhang measured from the wall (yard plus 1..air past the lot edge); a single jetty storey run
						step2 = false;
						int b = r.range(1, air());
						v[N] = r.chance(3, 4) ? yard() + b : v[N];
					}
					Massing m = backOff(v, step2, true);
					if (m != null) return m;
				} else if (x < jc + st.setbackChance()) {
					int k = r.range(1, 2);
					int[] v = r.chance(1, 2) ? new int[] {k, k, k, k}
							: new int[] {r.range(0, k), r.range(0, k), r.range(0, k), r.range(0, k)};
					if (v[N] + v[E] + v[S] + v[W] == 0) v[E] = v[W] = 1;
					Massing m = backOff(v, false, false);
					if (m != null) return m;
				}
			}
			return assemble(null, false, false);
		}

		/** Try the vector, then without the second step, then with sides zeroed one by one (back, east, west, front). */
		Massing backOff(int[] v, boolean step2, boolean jetty) {
			Massing m = assemble(v, step2, jetty);
			if (m == null && step2) m = assemble(v, false, jetty);
			for (int side : new int[] {S, E, W, N}) {
				if (m != null) return m;
				v[side] = 0;
				if (v[N] + v[E] + v[S] + v[W] == 0) return null;
				m = assemble(v, false, jetty);
			}
			return m;
		}

		/** The whole massing for a stack variant ({@code v == null}: a plain stack), or null if any rule fails. */
		Massing assemble(int[] v, boolean step2, boolean jetty) {
			Mass base = ground.get(0);
			List<Mass> stack = new ArrayList<>();
			if (v == null) {
				stack.add(base);
			} else if (jetty) {
				Mass m0 = new Mass(base.id(), base.kind(), base.x(), base.z(), base.sx(), base.sz(), 0, 1, null, false);
				stack.add(m0);
				Mass j1 = MassingOps.jetty("jetty1", m0, v, step2 ? 2 : n, st.jetty(), sp.width(), sp.depth(), air() > 0 ? sp.lead() : 0);
				if (j1 == null) return null;
				stack.add(j1);
				if (step2) {
					Mass j2 = MassingOps.jetty("jetty2", j1, v, 3, st.jetty(), sp.width(), sp.depth());
					if (j2 == null) return null;
					stack.add(j2);
				}
			} else {
				Mass m0 = new Mass(base.id(), base.kind(), base.x(), base.z(), base.sx(), base.sz(), 0, n - 1, null, false);
				stack.add(m0);
				Mass s1 = MassingOps.setback("setback1", m0, v, n);
				if (s1 == null) return null;
				stack.add(s1);
			}
			// wings and ranges keep layout order; stack children come after them (parents first)
			List<Mass> wings = new ArrayList<>(ground.subList(1, ground.size()));
			List<Mass> ordered = new ArrayList<>();
			ordered.add(stack.get(0));
			ordered.addAll(wings);
			ordered.addAll(stack.subList(1, stack.size()));
			List<Core> cores = new ArrayList<>();
			if (n >= 2) {
				Core k = MassingOps.core(g, stack, wings, 0, n, r, false);
				if (k == null) return null;
				cores.add(k);
			}
			for (Mass w : wings) {
				int u = firstUnlinked(w, stack, wings);
				if (u < 0) continue;
				Core k = MassingOps.core(g, List.of(w), others(w, stack, wings), u - 1, w.to(), r, false);
				if (k == null) return null;
				cores.add(k);
			}
			for (Core k : cores) for (Mass h : ordered) if (!roomy(h, k)) return null;
			Massing m = new Massing(g, ordered, cores);
			return MassingCheck.check(m, sp.width(), sp.depth(), sp.maxHeight()).isEmpty() ? m : null;
		}

		/** True unless mass {@code h} has a floor served by core {@code k} where fewer than {@link #MIN_FREE} interior cells against its walls lie outside the core and the two-cell margin the interior pass keeps hook-free around it (no room for a bed beside the stairs). */
		static boolean roomy(Mass h, Core k) {
			if (h.to() <= k.from() || h.from() >= k.to() || h.open() || !(h.x() < k.maxX() && k.x() < h.maxX() && h.z() < k.maxZ() && k.z() < h.maxZ())) return true;
			int free = 0;
			for (int x = h.x() + 1; x < h.maxX() - 1; x++)
				for (int z = h.z() + 1; z < h.maxZ() - 1; z++)
					if ((x == h.x() + 1 || z == h.z() + 1 || x == h.maxX() - 2 || z == h.maxZ() - 2) && (x < k.x() - 2 || x > k.maxX() + 1 || z < k.z() - 2 || z > k.maxZ() + 1)) free++;
			return free >= MIN_FREE;
		}

		static List<Mass> others(Mass self, List<Mass> stack, List<Mass> wings) {
			List<Mass> o = new ArrayList<>(stack);
			for (Mass w : wings) if (w != self) o.add(w);
			return o;
		}

		/** First storey >= 1 of wing {@code w} with no strip to another mass on that storey (needs its own core), or -1. */
		static int firstUnlinked(Mass w, List<Mass> stack, List<Mass> wings) {
			for (int s = 1; s < w.to(); s++) {
				boolean linked = false;
				for (Mass o : others(w, stack, wings)) {
					if (!o.spans(s) || !o.floorAt(s)) continue;
					int[] st = Massing.strip(w, o);
					if (st != null && Math.max(st[2], st[3]) >= 3) linked = true;
				}
				if (!linked) return s;
			}
			return -1;
		}
	}
}
