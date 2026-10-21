package dev.fractalbuildings.core.gen.v1.types.shop;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.mass.HouseMassing;
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
 * Shop massing: a main block spanning the envelope's width (so the front room spans the front),
 * optionally a narrower rear wing (a back house) and, in styles with jetties, a front-only jetty on
 * the upper storeys. The ground storey is {@link ShopType#GROUND} high. Falls back to fewer storeys,
 * then to a plain block; always passes {@link MassingCheck}.
 */
final class ShopMassing {
	private ShopMassing() {}

	static Massing build(BuildContext c, int maxStoreys) {
		BuildingSpec sp = c.spec();
		int mode = ShopType.groundMode(c);
		if (mode == ShopType.MARKET) {
			Massing m = market(c, maxStoreys);
			if (m != null) return m;
		}
		for (int n = Math.min(sp.storeys(), maxStoreys); n >= 1; n--) {
			for (int variant = 0; variant < 3; variant++) {
				Massing m = attempt(c, n, variant, mode == ShopType.ARCADE);
				if (m != null) return m;
			}
		}
		Mass main = Mass.ground("main0", "main", 0, sp.lead(), sp.width(), Math.min(sp.depth() - sp.lead(), 11), 1);
		// under a low ceiling (maxHeight 6) the ground storey is 4 high
		return new Massing(StoreyGrid.of(1, sp.maxHeight() >= ShopType.GROUND + 2 ? ShopType.GROUND : 4, 4), List.of(main), List.of());
	}

	/** The market hall: one mass on a row of posts ({@link ShopType#stilts}), storeys up a stair from the entrance flight; null if it does not fit. */
	private static Massing market(BuildContext c, int maxStoreys) {
		BuildingSpec sp = c.spec();
		int lift = ShopType.legHeight(c);
		int W = sp.width(), D = sp.depth() - sp.lead();
		if (lift == 0 || D < 9) return null;
		int deck = D >= 11 ? 2 : D >= 10 ? 1 : 0; // rows left in front of the hall for the landing the front steps rise to (see InstancePassV1 stoops)
		D -= deck;
		for (int n = Math.min(Math.min(sp.storeys(), maxStoreys), 3); n >= 1; n--) {
			Draws r = c.draws("massing/market", n);
			StoreyGrid g = StoreyGrid.of(n, ShopType.GROUND, Math.max(4, c.style().massing().typical()));
			if (g.y(n) + 2 > sp.maxHeight() - lift) continue;
			Mass main = new Mass("main0", ShopType.MARKET_KIND, 0, 0, W, r.range(Math.min(D, 10), Math.min(D, 14)), 0, n, null, false);
			List<Core> cores = new ArrayList<>();
			if (n >= 2) {
				Core k = MassingOps.core(g, List.of(main), List.of(), 0, n, r);
				if (k == null) k = sideCore(g, List.of(main), List.of(), n, r);
				if (k == null) continue;
				cores.add(k);
			}
			Massing m = new Massing(g, List.of(main), cores).shiftedZ(sp.lead() + deck);
			if (MassingCheck.check(m, W, sp.depth(), sp.maxHeight() - lift).isEmpty()) return m;
		}
		return null;
	}

	/** variant 0: as drawn (rear wing, jetty or arcade); 1: no jetty; 2: plain block. */
	static Massing attempt(BuildContext c, int n, int variant, boolean arcade) {
		BuildingSpec sp = c.spec();
		MassingStyle st = c.style().massing();
		int W = sp.width(), D = sp.depth() - sp.lead(); // lead: rows in front of the usual envelope (air rights), masses are shifted back by it at the end
		Draws r = c.draws("massing/shop", n);
		StoreyGrid g = StoreyGrid.of(n, ShopType.GROUND, Math.max(4, st.typical()));
		if (g.y(n) + 2 > sp.maxHeight()) return null;

		boolean arcadeHere = arcade && variant == 0 && n >= 2 && D >= 12;
		int over = arcadeHere ? r.range(2, MassingCheck.ARCADE_MAX) : 0; // the walkway's depth, in front of the shop
		int air = sp.lead() == 0 ? 0 : sp.lead() - c.type().limits().front(); // blocks of air rights over the street
		int jc = air > 0 ? Math.max(st.jettyChance(), HouseMassing.AIR_JETTY_PCT) : st.jettyChance(); // a street with air rights invites jetties
		boolean jetty = !arcadeHere && variant == 0 && n >= 2 && st.jetty() > 0 && D >= 9 && r.below(100) < jc;
		boolean airJetty = jetty && air > 0; // the front wall stands at the usual front edge and the upper storeys reach out past the lot
		int z0 = arcadeHere ? over : jetty && !airJetty ? 1 : 0;
		boolean rear = variant < 2 && D >= 15 && D - z0 >= 14 && W >= 7 && r.chance(2, 5);
		int md, wingW = 0, wingD = 0;
		if (rear) {
			md = r.range(10, Math.min(13, D - z0 - 4));
			wingW = r.range(5, Math.min(W - 2, 9));
			wingD = r.range(5, Math.min(D - z0 - md + 1, 12));
		} else {
			md = r.range(Math.min(D - z0, 11), D - z0);
		}
		Mass base = Mass.ground("main0", "main", 0, z0, W, md, n);
		List<Mass> stack = new ArrayList<>();
		List<Mass> wings = new ArrayList<>();
		if (arcadeHere) { // the upper storeys carry on over the walkway; the posts come with the facade
			Mass m0 = new Mass("main0", "main", 0, z0, W, md, 0, 1, null, false);
			stack.add(m0);
			stack.add(new Mass("jetty1", "jetty", 0, 0, W, md + over, 1, n, "main0", false));
		} else if (jetty) {
			Mass m0 = new Mass("main0", "main", 0, z0, W, md, 0, 1, null, false);
			if (airJetty) { // front overhang from the wall: the yard plus 1..air past the lot edge
				int out = c.type().limits().front() + r.range(1, air);
				stack.add(m0);
				stack.add(new Mass("jetty1", "jetty", 0, z0 - out, W, md + out, 1, n, "main0", false));
			} else {
				Mass j = MassingOps.jetty("jetty1", m0, new int[] {r.range(1, Math.min(st.jetty(), MassingCheck.JETTY_MAX)), 0, 0, 0}, n, st.jetty(), W, D);
				if (j == null) return null;
				stack.add(m0);
				stack.add(j);
			}
		} else {
			stack.add(base);
		}
		if (rear) {
			int ws = Math.max(1, Math.min(n, r.chance(1, 2) ? n - 1 : n));
			MassingOps.Align al = MassingOps.Align.values()[r.below(3)];
			Mass w = MassingOps.wing("wing1", stack.get(0), MassingOps.S, al, wingW, wingD, ws, W, D);
			if (w == null) return null;
			wings.add(w);
		}
		List<Mass> ordered = new ArrayList<>();
		ordered.add(stack.get(0));
		ordered.addAll(wings);
		ordered.addAll(stack.subList(1, stack.size()));
		List<Core> cores = new ArrayList<>();
		if (n >= 2) {
			Core k = MassingOps.core(g, stack, wings, 0, n, r);
			if (k == null) k = sideCore(g, stack, wings, n, r);
			if (k == null) return null;
			cores.add(k);
		}
		Massing m = new Massing(g, ordered, cores).shiftedZ(sp.lead());
		return MassingCheck.check(m, W, sp.depth(), sp.maxHeight()).isEmpty() ? m : null;
	}

	/**
	 * A core along a side wall of the main block, behind the two front rows (counter space), clear of
	 * the wall strips to the wings: for rear wings that cover the back row where the shared core
	 * candidates sit.
	 */
	private static Core sideCore(StoreyGrid g, List<Mass> stack, List<Mass> wings, int n, Draws r) {
		Mass host = stack.get(0);
		int len = Core.length(g, 0, n);
		List<Core> ok = new ArrayList<>();
		int ix0 = host.x() + 1, ix1 = host.maxX() - 1, iz0 = host.z() + 3, iz1 = host.maxZ() - 1;
		for (Mass s : stack) {
			ix0 = Math.max(ix0, s.x() + 1);
			ix1 = Math.min(ix1, s.maxX() - 1);
			iz1 = Math.min(iz1, s.maxZ() - 1);
		}
		for (int z = iz0; z + len <= iz1; z++) {
			for (int x : new int[] {ix0, ix1 - 2}) {
				Core k = new Core(x, z, 2, len, 0, n);
				boolean clear = true;
				for (Mass w : wings) {
					int[] st = Massing.strip(stack.get(0), w);
					if (st != null && st[0] < k.maxX() + 1 && k.x() - 1 < st[0] + st[2] && st[1] < k.maxZ() + 1 && k.z() - 1 < st[1] + st[3]) clear = false;
				}
				if (clear) ok.add(k);
			}
		}
		return ok.isEmpty() ? null : ok.get(r.below(ok.size()));
	}
}
