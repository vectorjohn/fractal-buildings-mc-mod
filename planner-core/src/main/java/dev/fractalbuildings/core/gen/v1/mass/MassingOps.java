package dev.fractalbuildings.core.gen.v1.mass;

import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.style.MassingStyle;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure massing operations (plan step M): small building blocks that type programs compose. Every op
 * returns {@code null} when the result would not fit (never an invalid mass); the composed
 * {@link Massing} is still checked by {@code MassingCheck} before anything is built.
 *
 * <p>Conventions: local frame, front = north (z = 0); wings stand on the east, west or south side of
 * the main block and share one wall strip with it (the strip is the wing's width along that side).
 * Side vectors ({@code over}, {@code inset}) are {@code {north, east, south, west}} in blocks.
 */
public final class MassingOps {
	private MassingOps() {}

	public static final int N = 0, E = 1, S = 2, W = 3;

	/** Smallest side of a mass that gets a floor plan (interior 3 across). */
	public static final int MIN_ROOM = 5;

	/** Where a wing sits along the side it is attached to. */
	public enum Align { START, CENTRE, END }

	/** Storey grid of {@code storeys}: the ground storey is {@code groundExtra} taller by the style's chance. */
	public static StoreyGrid grid(MassingStyle st, int storeys, Draws r) {
		int extra = st.groundExtra() > 0 && r.below(100) < st.groundExtraChance() ? st.groundExtra() : 0;
		return StoreyGrid.of(storeys, st.ground() + extra, st.typical());
	}

	/**
	 * The main block {@code w x d}, placed in the {@code envW x envD} envelope: x centred or random
	 * ({@code align}: -1 left, 0 centred, 1 right), z front-aligned (0 to 2 from the front edge) or
	 * centred. Null if it doesn't fit.
	 */
	public static Mass main(int envW, int envD, int w, int d, int x, boolean frontAligned, int storeys, Draws r) {
		if (w < MIN_ROOM || d < MIN_ROOM || w > envW || d > envD) return null;
		int z = frontAligned ? r.range(0, Math.min(2, envD - d)) : (envD - d) / 2;
		return Mass.ground("main0", "main", Math.max(0, Math.min(x, envW - w)), z, w, d, storeys);
	}

	/**
	 * A wing of {@code width} along {@code side} of the main block and {@code depth} away from it
	 * (including the shared wall), sharing a strip of {@code width} blocks with it. Null if the wing
	 * leaves the envelope, is narrower than {@link #MIN_ROOM}, or is as wide as the side it stands
	 * on (that would be a longer box, not a wing).
	 */
	public static Mass wing(String id, Mass main, int side, Align align, int width, int depth, int storeys, int envW, int envD) {
		if (width < MIN_ROOM || depth < MIN_ROOM) return null;
		boolean alongX = side == S;
		int span = alongX ? main.sx() : main.sz();
		if (side == N || width > span - 2) return null;
		int off = switch (align) {
			case START -> 0;
			case CENTRE -> (span - width) / 2;
			case END -> span - width;
		};
		int x, z, sx, sz;
		if (alongX) {
			x = main.x() + off; z = main.maxZ() - 1; sx = width; sz = depth;
		} else {
			z = main.z() + off; sz = width; sx = depth;
			x = side == E ? main.maxX() - 1 : main.x() - depth + 1;
		}
		if (x < 0 || z < 0 || x + sx > envW || z + sz > envD) return null;
		return Mass.ground(id, "wing", x, z, sx, sz, storeys);
	}

	/**
	 * Upper storeys [parent.to, to) wider than the parent by {@code over} per side, each 0 to
	 * {@code max}, at least one side nonzero. Null if it leaves the envelope or an overhang is out of range.
	 */
	public static Mass jetty(String id, Mass parent, int[] over, int to, int max, int envW, int envD) {
		boolean any = false;
		for (int o : over) {
			if (o < 0 || o > max || o > MassingCheck.JETTY_MAX) return null;
			any |= o > 0;
		}
		if (!any || to <= parent.to()) return null;
		int x = parent.x() - over[W], z = parent.z() - over[N];
		int sx = parent.sx() + over[W] + over[E], sz = parent.sz() + over[N] + over[S];
		if (x < 0 || z < 0 || x + sx > envW || z + sz > envD) return null;
		return new Mass(id, "jetty", x, z, sx, sz, parent.to(), to, parent.id(), false);
	}

	/**
	 * Upper storeys [parent.to, to) narrower than the parent by {@code inset} per side, at least one
	 * side nonzero; null if a side would shrink below {@link #MIN_ROOM}.
	 */
	public static Mass setback(String id, Mass parent, int[] inset, int to) {
		boolean any = false;
		for (int o : inset) {
			if (o < 0) return null;
			any |= o > 0;
		}
		if (!any || to <= parent.to()) return null;
		int sx = parent.sx() - inset[W] - inset[E], sz = parent.sz() - inset[N] - inset[S];
		if (sx < MIN_ROOM || sz < MIN_ROOM) return null;
		return new Mass(id, "setback", parent.x() + inset[W], parent.z() + inset[N], sx, sz, parent.to(), to, parent.id(), false);
	}

	/**
	 * A stack for towers and high-rises: a podium on the ground, then a shaft, then a crown, each
	 * inset by {@code insets[i]} on every side from the one below (0 = same footprint, no new mass
	 * is made for an equal footprint). {@code bands} are the storey counts of podium, shaft, crown.
	 * Null if a footprint shrinks below {@link #MIN_ROOM}.
	 */
	public static List<Mass> stack(Mass podium, int[] bands, int[] insets) {
		if (bands.length == 0 || bands[0] != podium.to() - podium.from()) return null;
		List<Mass> out = new ArrayList<>();
		out.add(podium);
		Mass cur = podium;
		int top = podium.to();
		for (int i = 1; i < bands.length; i++) {
			int in = insets[i - 1];
			int sx = cur.sx() - 2 * in, sz = cur.sz() - 2 * in;
			if (in < 0 || sx < MIN_ROOM || sz < MIN_ROOM) return null;
			if (in == 0) {
				Mass m = new Mass(cur.id(), cur.kind(), cur.x(), cur.z(), cur.sx(), cur.sz(), cur.from(), top + bands[i], cur.parent(), cur.open());
				out.set(out.size() - 1, m);
				cur = m;
			} else {
				String kind = i == bands.length - 1 ? "crown" : "shaft";
				Mass m = new Mass(kind + out.size(), kind, cur.x() + in, cur.z() + in, sx, sz, top, top + bands[i], cur.id(), false);
				out.add(m);
				cur = m;
			}
			top += bands[i];
		}
		return out;
	}

	/**
	 * A core serving storeys [from, to) inside the common interior of {@code hosts} (masses that
	 * together span those storeys one on top of the other), off the front inside row and not adjacent
	 * to a wall strip any of {@code others} shares with a host. Candidates are the back corners and
	 * centre, long side along x and along z; the first drawn one that fits is returned, else null.
	 */
	public static Core core(StoreyGrid g, List<Mass> hosts, List<Mass> others, int from, int to, Draws r) {
		return core(g, hosts, others, from, to, r, true);
	}

	/** As {@link #core(StoreyGrid, List, List, int, int, Draws)}; with {@code centre} false the core always stands in a corner or against a side wall, never mid-wall (no stair-only room beside it). */
	public static Core core(StoreyGrid g, List<Mass> hosts, List<Mass> others, int from, int to, Draws r, boolean centre) {
		if (to < from + 2) return null;
		int len = Core.length(g, from, to);
		int ix0 = Integer.MIN_VALUE, iz0 = Integer.MIN_VALUE, ix1 = Integer.MAX_VALUE, iz1 = Integer.MAX_VALUE;
		for (Mass h : hosts) {
			ix0 = Math.max(ix0, h.x() + 1);
			iz0 = Math.max(iz0, h.z() + 1);
			ix1 = Math.min(ix1, h.maxX() - 1);
			iz1 = Math.min(iz1, h.maxZ() - 1);
		}
		List<Core> cand = new ArrayList<>();
		// long side along x on the back row
		if (ix1 - ix0 >= len && iz1 - iz0 >= 3) {
			int z = iz1 - 2;
			cand.add(new Core(ix0, z, len, 2, from, to));
			cand.add(new Core(ix1 - len, z, len, 2, from, to));
			if (centre) cand.add(new Core(ix0 + (ix1 - ix0 - len) / 2, z, len, 2, from, to));
		}
		// long side along z against a side wall
		if (iz1 - iz0 >= len + 1 && ix1 - ix0 >= 3) {
			int z = iz1 - len;
			cand.add(new Core(ix0, z, 2, len, from, to));
			cand.add(new Core(ix1 - 2, z, 2, len, from, to));
		}
		List<Core> ok = new ArrayList<>();
		for (Core k : cand) if (clear(k, hosts, others, from, to)) ok.add(k);
		if (ok.isEmpty()) return null;
		return ok.get(r.below(ok.size()));
	}

	/**
	 * Like {@link #core}, but with candidates at all four corners and the middle of the hosts' common
	 * interior, so a mass whose back wall is a shared strip (a tower in front of a hall) still finds a
	 * place for its stair. Returns null if none is clear of every wall strip.
	 */
	public static Core coreAnywhere(StoreyGrid g, List<Mass> hosts, List<Mass> others, int from, int to, Draws r) {
		if (to < from + 2) return null;
		int len = Core.length(g, from, to);
		int ix0 = Integer.MIN_VALUE, iz0 = Integer.MIN_VALUE, ix1 = Integer.MAX_VALUE, iz1 = Integer.MAX_VALUE;
		for (Mass h : hosts) {
			ix0 = Math.max(ix0, h.x() + 1);
			iz0 = Math.max(iz0, h.z() + 1);
			ix1 = Math.min(ix1, h.maxX() - 1);
			iz1 = Math.min(iz1, h.maxZ() - 1);
		}
		List<Core> ok = new ArrayList<>();
		for (int along = 0; along < 2; along++) {
			int sx = along == 0 ? len : 2, sz = along == 0 ? 2 : len;
			if (ix1 - ix0 < sx || iz1 - iz0 - 1 < sz) continue;
			int[] xs = {ix0, ix1 - sx, ix0 + (ix1 - ix0 - sx) / 2};
			int[] zs = {iz1 - sz, iz0 + 1 + (iz1 - iz0 - 1 - sz) / 2}; // never the front inside row (the entrance)
			for (int x : xs)
				for (int z : zs) {
					Core k = new Core(x, z, sx, sz, from, to);
					boolean dup = false;
					for (Core o : ok) dup |= o.x() == k.x() && o.z() == k.z() && o.sx() == k.sx();
					if (!dup && clear(k, hosts, others, from, to)) ok.add(k);
				}
		}
		if (ok.isEmpty()) return null;
		return ok.get(r.below(ok.size()));
	}

	/** True if no shared wall strip between a host and another mass (on a served storey) touches {@code k}'s one-block neighbourhood. */
	private static boolean clear(Core k, List<Mass> hosts, List<Mass> others, int from, int to) {
		for (Mass h : hosts) {
			for (Mass o : others) {
				if (o == h) continue;
				int[] st = Massing.strip(h, o);
				if (st == null) continue;
				if (h.from() >= to || h.to() <= from || o.from() >= to || o.to() <= from) continue;
				if (st[0] < k.maxX() + 1 && k.x() - 1 < st[0] + st[2] && st[1] < k.maxZ() + 1 && k.z() - 1 < st[1] + st[3]) return false;
			}
		}
		return true;
	}
}
