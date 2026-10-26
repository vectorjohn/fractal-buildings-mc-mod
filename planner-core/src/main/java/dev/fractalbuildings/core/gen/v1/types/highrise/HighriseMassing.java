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
 * High-rise massing: a podium on the street (1 to 3 storeys), a shaft in bands that step back every 8 to
 * 20 storeys (insets 1 to 4 per side, drawn per side), a crown (1 to 3 storeys), the {@link TowerBlock}
 * (stairwell and lift shaft behind a hall) that every tier must hold ({@link TowerBlock#fits}), and a
 * stair house on top of the top tier over the whole core block. Heights come from the
 * {@link TowerProgram}: the lobby and the special floors are 6 high, the rest 4. The storey count is the
 * spec's, capped so the stair house's top slab plus a parapet layer stays under {@code maxHeight}. If a
 * draw doesn't fit it backs off to fewer setbacks, then fewer storeys, then to a plain box.
 */
final class HighriseMassing {
	private HighriseMassing() {}

	static final int TYPICAL = TowerProgram.TYPICAL_H, MIN_BAND = 8, MAX_BAND = 20, MAX_INSET = 4;
	/** Smallest footprint side of any tier ({@link TowerBlock#fits} is the real test). */
	static final int MIN_SIDE = 10;
	/** Blocks above the top slab kept free (flat roof layer plus a parapet). */
	static final int ROOF_ROOM = 3;
	/** Core length of every tower (the lobby is the tallest storey): lobby height + 1. */
	static final int CORE_LEN = TowerProgram.LOBBY_H + 1;

	/** Most storeys that fit under {@code maxHeight}, specials not counted (the attempt checks the real grid). */
	static int capacity(int maxHeight) {
		int n = 1;
		while (n < 200 && TowerProgram.LOBBY_H + TYPICAL * n + TowerProgram.STAIRHOUSE_H + ROOF_ROOM <= maxHeight) n++;
		return n;
	}

	static Massing build(BuildContext c) { return build(c, TowerBlock.SD); }

	/** As {@link #build(BuildContext)} with stairwell depth {@code sd} (3 or 4); tests run both. */
	static Massing build(BuildContext c, int sd) {
		BuildingSpec sp = c.spec();
		int n = Math.max(1, Math.min(sp.storeys(), capacity(sp.maxHeight())));
		for (; n >= 1; n--) {
			for (int setbacks = 3; setbacks >= 0; setbacks--) {
				Massing m = attempt(c, n, setbacks, sd);
				if (m != null) return m;
			}
		}
		int ground = Math.max(StoreyGrid.MIN_HEIGHT, Math.min(TowerProgram.LOBBY_H, sp.maxHeight() - 2));
		Mass main = Mass.ground("main0", "main", 0, 0, Math.min(sp.width(), 5), Math.min(sp.depth(), 5), 1);
		return new Massing(new StoreyGrid(ground), List.of(main), List.of());
	}

	/** One try: {@code n} storeys with up to {@code maxSteps} setbacks; null if it doesn't pass the massing rules. */
	static Massing attempt(BuildContext c, int n, int maxSteps, int sd) {
		BuildingSpec sp = c.spec();
		if (n == 1) {
			StoreyGrid g = new StoreyGrid(TowerProgram.LOBBY_H);
			if (g.y(1) + 2 > sp.maxHeight()) return null;
			Mass m = Mass.ground("main0", "main", 0, 0, Math.min(sp.width(), 8), Math.min(sp.depth(), 8), 1);
			return check(c, new Massing(g, List.of(m), List.of()));
		}
		if (TowerProgram.LOBBY_H + TYPICAL * (n - 1) + TowerProgram.STAIRHOUSE_H + ROOF_ROOM > sp.maxHeight()) return null;
		// a podium trimmed at random may leave the block no room at the sides; the full width has the most
		for (int full = 0; full < 2; full++) {
			Massing m = place(c, n, maxSteps, sd, full == 1);
			if (m != null) return m;
		}
		return null;
	}

	private static Massing place(BuildContext c, int n, int maxSteps, int sd, boolean fullWidth) {
		BuildingSpec sp = c.spec();
		int W = sp.width(), D = sp.depth();
		Draws r = c.draws("massing/highrise", n, maxSteps);
		// podium on the street: nearly the whole envelope, trimmed a little on the sides and back
		int tx0 = r.range(0, Math.min(2, (W - MIN_SIDE) / 2)), tx1 = r.range(0, Math.min(2, (W - MIN_SIDE) / 2));
		int tz1 = r.range(0, Math.min(3, D - MIN_SIDE));
		if (fullWidth) tx0 = tx1 = 0;
		Mass base = Mass.ground("main0", "main", tx0, 0, W - tx0 - tx1, D - tz1, 1);
		if (base.sx() < MIN_SIDE || base.sz() < MIN_SIDE) return null;
		int podium = n >= 6 ? r.range(1, 3) : 1;
		int crown = n >= 8 ? r.range(1, 3) : 0;
		int shaft = n - podium - crown;
		if (shaft < 1) return null;
		List<Integer> bands = bands(r, shaft, maxSteps);
		TowerBlock block = placeBlock(r, base, sd);
		if (block == null) return null;
		List<Mass> masses = new ArrayList<>();
		Mass cur = new Mass("main0", "main", base.x(), base.z(), base.sx(), base.sz(), 0, podium, null, false);
		masses.add(cur);
		int top = podium;
		for (int i = 0; i <= bands.size(); i++) {
			boolean isCrown = i == bands.size();
			int len = isCrown ? crown : bands.get(i);
			if (len == 0) break;
			Mass next = fitSetback(r, (isCrown ? "crown" : "shaft") + masses.size(), cur, block, top + len);
			if (next == null) return null;
			next = new Mass(next.id(), isCrown ? "crown" : "shaft", next.x(), next.z(), next.sx(), next.sz(), next.from(), next.to(), next.parent(), false);
			masses.add(next);
			cur = next;
			top += len;
		}
		// the programme sets the storey heights; the stair house is storey n
		int[] starts = masses.stream().filter(a -> a.kind().equals("shaft")).mapToInt(Mass::from).toArray();
		Mass last = masses.get(masses.size() - 1);
		TowerProgram.Kind[] kinds = TowerProgram.plan(n, podium, starts, last.kind().equals("crown") ? last.from() : -1, TowerProgram.drawsFor(c, n));
		int[] heights = new int[n + 1];
		for (int s = 0; s <= n; s++) heights[s] = TowerProgram.height(kinds[s]);
		StoreyGrid g = new StoreyGrid(heights);
		if (g.y(n + 1) + ROOF_ROOM > sp.maxHeight()) return null;
		masses.add(block.stairHouse("stair" + masses.size(), n, cur.id()));
		if (Core.length(g, 0, n + 1) != CORE_LEN) throw new IllegalStateException("core length " + Core.length(g, 0, n + 1));
		return check(c, new Massing(g, masses, List.of(new Core(block.kx(), block.kz(), CORE_LEN, 2, 0, n + 1))));
	}

	/**
	 * The core block in the podium: the hall centred in z (or, in a podium interior 23+ deep, the block
	 * centred in z), the block centred in x shifted by -2..2, the shaft on a drawn side. Snapped to the
	 * back wall when fewer than 3 rows would stay behind it; the first of the drawn, flush-left, flush-right
	 * and centred x positions that fits the podium. Null if none does.
	 */
	static TowerBlock placeBlock(Draws r, Mass base, int sd) {
		int hw = TowerBlock.hallWidth(base);
		int iz0 = base.z() + 1, depthI = base.sz() - 2;
		int blockZ0 = depthI >= 23 ? iz0 + (depthI - (sd + 2)) / 2 : iz0 + (depthI - hw) / 2 + hw;
		int kz = blockZ0 + (sd - 2) + 1;
		int behind = base.maxZ() - (kz + 3);
		if (behind != 0 && behind < 4) kz = base.maxZ() - 3;
		boolean east = r.chance(1, 2);
		int shift = r.range(-2, 2);
		int centred = base.x() + (base.sx() - (CORE_LEN + 6)) / 2;
		int[] xs = {centred + shift, base.x(), base.maxX() - (CORE_LEN + 6), centred};
		for (int x0 : xs) {
			TowerBlock b = new TowerBlock(east ? x0 + 1 : x0 + 5, kz, CORE_LEN, east, hw, sd);
			if (b.fits(base)) return b;
		}
		return null;
	}

	/** A setback of {@code cur} up to storey {@code to} that still holds the block, shrinking the offending insets; null if none does. */
	static Mass fitSetback(Draws r, String id, Mass cur, TowerBlock block, int to) {
		int[] v = insets(r, cur);
		if (v == null) return null;
		for (int guard = 0; guard < 12; guard++) {
			Mass cand = MassingOps.setback(id, cur, v, to);
			if (cand != null && Math.min(cand.sx(), cand.sz()) >= MIN_SIDE && block.fits(cand)) return cand;
			boolean changed = false;
			for (int i = 0; i < 4; i++) {
				if (v[i] == 0) continue;
				int[] only = new int[4];
				only[i] = v[i];
				Mass one = MassingOps.setback(id, cur, only, to);
				if (one == null || !block.fits(one)) {
					v[i]--;
					changed = true;
				}
			}
			if (!changed) for (int i = 0; i < 4; i++) if (v[i] > 0) v[i]--;
			if (v[0] + v[1] + v[2] + v[3] == 0) return null;
		}
		return null;
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
