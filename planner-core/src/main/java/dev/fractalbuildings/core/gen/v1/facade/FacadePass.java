package dev.fractalbuildings.core.gen.v1.facade;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.style.FacadeStyle;
import dev.fractalbuildings.core.grammar.Choice;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Facade layout and decor (owned by the facades step 4.4).
 *
 * <p>Layout. Each face (one mass, storey and side) belongs to a band: the ground storey, a group of
 * {@code bandSize} typical storeys, or the mass's top storey. A band draws one {@link Variant} per
 * (mass, side) (target bay width, window width, where the extra blocks go), never the same as the
 * band below, so a tall facade changes its rhythm every few storeys; storeys of one band share the
 * variant, so windows line up inside it. {@link #tile} cuts a face's positions between its end cells
 * into bays: mirror-symmetric for symmetric styles, otherwise perturbed per face by a draw.
 *
 * <p>Entrances. Each entrance mass gets one door on storey 0 (main mass: the front, north, face; the
 * others: a street side if they have one), centred for symmetric styles, drawn otherwise, never in
 * front of a core.
 *
 * <p>Decor ({@link #decorate}): style-keyed boxes in the wall plane (corner posts or quoins, plinth,
 * half-timbering posts and slab-line rails) and one cell outside it (string courses, cornice), plus a
 * beam along a jetty's slab edge. The opening pass adds the window and door decor. Decor never
 * replaces anything but plain wall, and never takes a cell in front of a door or window.
 */
public final class FacadePass {
	private FacadePass() {}

	/** Band key of the top storey of a mass (sorts last). */
	static final int TOP = 1_000_000;

	public static void run(BuildContext c) {
		List<FacadePlan.Layout> layouts = layout(c);
		Massing m = c.massing();
		List<FacadePlan.Entrance> entrances = new ArrayList<>();
		for (int e = 0; e < m.entrances().size(); e++) entrances.add(entrance(c, m, layouts, m.entrances().get(e), e));
		FacadePlan plan = new FacadePlan(layouts, entrances.get(0), entrances.subList(1, entrances.size()));
		c.setFacadePlan(plan);
		decorate(c, plan);
	}

	/** One band's rhythm: {@code target} bay width, window {@code width}, and whether extra blocks go to the centre bays. */
	record Variant(int target, int width, boolean centreWide) {
		String key() { return target + "," + width + "," + centreWide; }
	}

	/** The band of a face: 0 ground storey, 1 + k the k-th group of {@code bandSize} typical storeys, {@link #TOP} the mass's top storey. */
	public static int band(Mass m, int storey, int bandSize) {
		if (storey == 0) return 0;
		if (storey == m.to() - 1) return TOP;
		return 1 + (storey - Math.max(m.from(), 1)) / bandSize;
	}

	/** Bay layouts for every face of the massing (all bays {@code window}; no entrance yet). Types may call this and change kinds. */
	public static List<FacadePlan.Layout> layout(BuildContext c) {
		FacadeStyle st = c.style().facade();
		Massing m = c.massing();
		List<Face> faces = c.faces();
		// (mass index, side, band) -> variant, drawn band by band so each differs from the ones below
		Map<String, Variant> variants = new TreeMap<>();
		TreeSet<String> done = new TreeSet<>();
		List<Variant> all = variants(st, glassRich(c));
		for (int fi = 0; fi < faces.size(); fi++) {
			Face f = faces.get(fi);
			Mass mass = m.mass(f.mass());
			int mi = m.masses().indexOf(mass);
			String group = mi + "/" + f.side().ordinal();
			if (!done.add(group)) continue;
			TreeSet<Integer> bands = new TreeSet<>();
			for (Face g : faces) if (g.mass().equals(f.mass()) && g.side() == f.side()) bands.add(band(mass, g.storey(), st.bandSize()));
			Variant p1 = null, p2 = null;
			Variant front = houseFront(c, st, mi, f);
			for (int b : bands) {
				Variant v = front != null ? front : pick(c, all, mi, f.side(), b, p1, p2, wideWeights(c, mi, f));
				variants.put(group + "/" + b, v);
				p2 = p1;
				p1 = v;
			}
		}
		List<FacadePlan.Layout> out = new ArrayList<>();
		for (Face f : faces) {
			Mass mass = m.mass(f.mass());
			int mi = m.masses().indexOf(mass);
			int b = band(mass, f.storey(), st.bandSize());
			Variant v = variants.get(mi + "/" + f.side().ordinal() + "/" + b);
			boolean mainFront = mi == 0 && f.side() == Facing.NORTH && f.storey() == 0;
			Draws r = c.draws("facade/tile", mi, f.side().ordinal(), b, f.a(), f.b());
			int[] bays = tile(f.length() - 2, v.target(), st.symmetric(), v.centreWide(), mainFront && st.symmetric(), st.bayMin(), st.bayMax(),
					st.narrowCorners() && f.convexA(), st.narrowCorners() && f.convexB(), r);
			if (bays == null && st.bayMin() > 2) bays = tile(f.length() - 2, v.target(), st.symmetric(), v.centreWide(), mainFront && st.symmetric(), 2, st.bayMax(), st.narrowCorners() && f.convexA(), st.narrowCorners() && f.convexB(), r); // a face the style's bays cannot tile (length 5 with 3..4) still gets windows
			if (bays == null) continue;
			int shift = 0;
			boolean houseFront = c.type().id().equals("house") && mi == 0 && f.side() == Facing.NORTH && houseFront(c, st, mi, f) != null;
			if (houseFront && v.width() >= 2) { // one 2x2 variant for the whole front: bays of exactly 4, the spare cells (0-3) as wider end piers
				int len = f.length() - 2, extra = len % 4;
				if (len >= 4) {
					bays = new int[len / 4];
					java.util.Arrays.fill(bays, 4);
					shift = extra / 2 + (extra % 2 == 1 ? c.draws("facade/shift", mi, f.side().ordinal(), f.a(), f.b()).below(2) : 0);
				}
			} else if (c.type().id().equals("house") && v.width() >= 2 && !hasFour(bays)) { // a house's 2x2 windows need a bay of 4: give up one or two cells of pier (one at a drawn end, or one each end) to get one
				for (int extra = 1; extra <= 2; extra++) {
					int[] alt = tile(f.length() - 2 - extra, v.target(), st.symmetric(), v.centreWide(), false, st.bayMin(), st.bayMax(),
							st.narrowCorners() && f.convexA(), st.narrowCorners() && f.convexB(), r);
					if (alt == null || !hasFour(alt)) continue;
					bays = alt;
					shift = extra == 2 ? 1 : c.draws("facade/shift", mi, f.side().ordinal(), b, f.a(), f.b()).below(2);
					break;
				}
			}
			List<String> kinds = new ArrayList<>();
			for (int i = 0; i < bays.length; i++) kinds.add(FacadePlan.WINDOW);
			if (st.blankCorners() && bays.length >= 4) {
				if (f.convexA()) kinds.set(0, FacadePlan.BLANK);
				if (f.convexB()) kinds.set(bays.length - 1, FacadePlan.BLANK);
			}
			boolean tall = glassRich(c) && st.tallGlass() && f.storey() >= 1 && TALL_TYPES.contains(c.type().id()); // floor to ceiling: halls and high-rises only, in styles that allow it
			int sill = tall ? 0 : st.sill();
			boolean headAtCeiling = glassRich(c) && f.storey() >= 1 && !tall && (c.type().id().equals("shop") || c.type().id().equals("block")); // shops and blocks above the shopfront: sill +1, head at the ceiling
			int h = Math.min(tall ? 3 : headAtCeiling ? Math.max(st.windowHeight(), Math.min(3, f.height() - sill)) : st.windowHeight(), f.height() - sill);
			if (h < 1) continue; // no room for a window on this face
			boolean piered = false;
			int len = f.length() - 2;
			if (headAtCeiling && st.tallGlass() && len >= 4) { // shop and block upper floors: 2-wide windows between 1-cell piers, P (WW P) x n, the same on every storey so they stack (DS-33, FACADE2 round 2)
				int n = (len - 1) / 3, rem = (len - 1) % 3;
				bays = new int[n];
				java.util.Arrays.fill(bays, 3);
				kinds = new ArrayList<>();
				for (int i = 0; i < n; i++) kinds.add(FacadePlan.WINDOW);
				shift = rem == 2 ? 1 : rem == 1 ? c.draws("facade/shift", mi, f.side().ordinal(), f.a(), f.b()).below(2) : 0;
				piered = true;
			}
			out.add(new FacadePlan.Layout(f, f.a() + 1 + shift, bays, kinds, new FacadePlan.Win(piered ? 2 : v.width(), h, sill, piered)));
		}
		return out;
	}

	/**
	 * The one variant of a house's street front, for every band (null: not a house front, or a style without 2-wide windows). 2x2 (bay 4, window 2) when the inner length is 12+, so the
	 * ground floor holds the door and a 2x2 each side; otherwise 1x2. Medieval fronts that long draw one or the other once (the tier is not known).
	 */
	static Variant houseFront(BuildContext c, FacadeStyle st, int mi, Face f) {
		if (!c.type().id().equals("house") || mi != 0 || f.side() != Facing.NORTH || st.windowWidth() < 2 || st.bayMax() < 4) return null;
		boolean wide = f.length() - 2 >= 12;
		if (wide && c.style().id().equals("medieval")) wide = c.draws("facade/front-wide").below(2) == 0;
		return wide ? new Variant(4, 2, false) : new Variant(2, 1, false);
	}

	private static boolean hasFour(int[] bays) { for (int w : bays) if (w == 4) return true; return false; }

	/** Types that may have sill-0, 3-tall upper-floor glass (in styles with {@code tallGlass}); shops and blocks use sill +1 (DS-39, architect decision 3). */
	private static final java.util.Set<String> TALL_TYPES = java.util.Set.of("hall", "highrise");

	/**
	 * Weights {wide, narrow[, 1 if bands may repeat the variant below]} of the 2-wide-window variant (bay 4) against the 1-wide one (bay 2) for a face: a house's street front (mass 0, north, 8+ wide) draws 2x2
	 * nine times in ten (weight 9 per variant against 1; the architect said 3:1, which gave 48-57 % of fronts with a 2x2 where 60 % was wanted, since the ground door takes a bay), its sides, backs and narrow faces one in four (DS-33 "2x2 common in town houses", architect decision 2). Medieval would take the front weighting
	 * only in town tier and up, but the tier is not in the spec, so medieval fronts are 1:1. Other types 1:1.
	 */
	static int[] wideWeights(BuildContext c, int mi, Face f) {
		if (!c.type().id().equals("house")) return new int[] {1, 1};
		if (!(mi == 0 && f.side() == Facing.NORTH && f.length() >= 8)) return new int[] {1, 3};
		return c.style().id().equals("medieval") ? new int[] {1, 1} : new int[] {9, 1, 1};
	}

	private static int weight(Variant v, int[] w) { return v.width() >= 2 ? w[0] : w[1]; }

	/** Types whose facades are glass-rich (DS-33: town houses, shops' upper floors, civic, commercial and high-rise): bays of 2 with a 1-wide window or of 4 with a 2-wide one, in styles that allow 2-wide windows. */
	private static final java.util.Set<String> RICH = java.util.Set.of("shop", "block", "hall", "highrise");

	static boolean glassRich(BuildContext c) { return RICH.contains(c.type().id()) && c.style().facade().windowWidth() >= 2 && c.style().facade().bayMax() >= 4; }

	private static List<Variant> variants(FacadeStyle st, boolean rich) {
		// a 2-wide window needs an even bay of 4+: in styles that have them, a band draws bays of 2 with 1x2 windows or bays of 4 with 2x2 windows, so 2x2 is common
		boolean wide = st.windowWidth() >= 2 && st.bayMax() >= 4;
		List<Variant> out = new ArrayList<>();
		for (int t = st.bayMin(); t <= st.bayMax(); t++)
			for (int w = 1; w <= st.windowWidth(); w++)
				if (wide ? ((t == 2 && w == 1) || (t == 4 && w == 2)) : true)
					for (int cw = 0; cw < (st.symmetric() ? 2 : 1); cw++) out.add(new Variant(t, w, cw == 1));
		return out;
	}

	private static Variant pick(BuildContext c, List<Variant> all, int mi, Facing side, int band, Variant p1, Variant p2, int[] wideWeights) {
		List<Choice.Option> opts = new ArrayList<>();
		if (wideWeights.length > 2) { p1 = null; p2 = null; } // a house front keeps its wide windows band after band (stacked, DS-25)
		for (Variant v : all) if (!v.equals(p1) && !v.equals(p2)) opts.add(Choice.opt(v.key(), weight(v, wideWeights)));
		if (opts.isEmpty()) for (Variant v : all) if (!v.equals(p1)) opts.add(Choice.opt(v.key(), weight(v, wideWeights)));
		if (opts.isEmpty()) return all.get(0);
		String key = Choice.pick(c.hash("facade/band", mi, side.ordinal(), band), "variant", opts);
		for (Variant v : all) if (v.key().equals(key)) return v;
		throw new IllegalStateException(key);
	}

	/**
	 * Cuts {@code len} positions into bays of {@code min..max}: the count nearest {@code len / target},
	 * widths equal (differing by at most 1). Symmetric: the extra blocks go in mirrored pairs (to the
	 * centre bays if {@code centreWide}, else to the ends), only counts that allow it are used, and
	 * {@code preferOdd} takes an odd count when there is one (a bay on the centre line). Not symmetric:
	 * the widths are then perturbed by {@code r}. Narrow corners take a block from the end bay.
	 * Null if no count fits.
	 */
	public static int[] tile(int len, int target, boolean symmetric, boolean centreWide, boolean preferOdd, int min, int max,
			boolean narrowA, boolean narrowB, Draws r) {
		if (len < min) return null;
		int nMin = (len + max - 1) / max, nMax = len / min;
		if (nMin > nMax) return null;
		int ideal = Math.max(nMin, Math.min(nMax, (2 * len + target) / (2 * target)));
		List<Integer> order = new ArrayList<>();
		for (int d = 0; d <= nMax - nMin; d++) {
			if (ideal + d <= nMax) order.add(ideal + d);
			if (d > 0 && ideal - d >= nMin) order.add(ideal - d);
		}
		int n = -1;
		boolean sym = symmetric;
		if (symmetric) {
			boolean odd = false;
			if (preferOdd) for (int k : order) if (k % 2 == 1) { odd = true; break; }
			for (int k : order) if ((!odd || k % 2 == 1) && (k % 2 == 1 || (len % k) % 2 == 0)) { n = k; break; }
			if (n < 0) sym = false;
		}
		if (n < 0) n = order.get(0);
		int[] w = new int[n];
		int base = len / n, extra = len % n;
		java.util.Arrays.fill(w, base);
		if (sym) {
			int e = extra;
			if (e % 2 == 1) { w[n / 2]++; e--; }
			List<int[]> pairs = new ArrayList<>();
			for (int i = 0; i < n / 2; i++) pairs.add(new int[] {i, n - 1 - i});
			if (centreWide) java.util.Collections.reverse(pairs);
			for (int[] p : pairs) {
				if (e < 2) break;
				w[p[0]]++;
				w[p[1]]++;
				e -= 2;
			}
		} else {
			for (int i = 0; i < extra; i++) w[i]++;
			if (n > 1) {
				for (int k = 0; k < n; k++) {
					int i = r.below(n), j = r.below(n);
					if (i != j && w[i] < max && w[j] > min) { w[i]++; w[j]--; }
				}
			}
		}
		if (n >= 3) {
			if (narrowA && w[0] > min && w[1] < max) { w[0]--; w[1]++; }
			if (narrowB && w[n - 1] > min && w[n - 2] < max) { w[n - 1]--; w[n - 2]++; }
		}
		return w;
	}

	/** Equal bays (sizes differ by at most 1, larger first) covering {@code len} exactly, each in [min, max]; the most bays that fit. Null if none fit. */
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

	/**
	 * Picks the door for one entrance mass and marks its bay as a door bay (replacing the layout in
	 * {@code layouts}). Symmetric styles take the window bay nearest the face centre, others draw; a
	 * double door needs an even bay of 4+ whose two inside cells are out of the cores.
	 */
	public static FacadePlan.Entrance entrance(BuildContext c, Massing m, List<FacadePlan.Layout> layouts, String massId, int index) {
		FacadeStyle st = c.style().facade();
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
				int[] pick = st.symmetric() ? nearestCentre(layouts, cand) : cand.get(c.draws("facade/entrance", index).below(cand.size()));
				FacadePlan.Layout l = layouts.get(pick[0]);
				Face f = l.face();
				int b = pick[1];
				List<String> kinds = new ArrayList<>(l.kinds());
				kinds.set(b, FacadePlan.DOOR);
				layouts.set(pick[0], l.withKinds(kinds));
				int width = 1, pos = l.centre(b);
				if (st.doorWidth() == 2 && l.bays()[b] >= 4 && l.bays()[b] % 2 == 0) {
					int p0 = l.bayStart(b) + (l.bays()[b] - 2) / 2;
					if (!m.inCore(0, f.insideX(p0), f.insideZ(p0)) && !m.inCore(0, f.insideX(p0 + 1), f.insideZ(p0 + 1))) {
						width = 2;
						pos = p0;
					}
				}
				return new FacadePlan.Entrance(f, pos, width);
			}
			if (!loose.isEmpty()) return loose.get(c.draws("facade/entrance-loose", index).below(loose.size()));
			if (main) break;
		}
		throw new IllegalStateException("no entrance position for " + massId);
	}

	private static int[] nearestCentre(List<FacadePlan.Layout> layouts, List<int[]> cand) {
		int[] best = null;
		long bestD = Long.MAX_VALUE;
		for (int[] k : cand) {
			FacadePlan.Layout l = layouts.get(k[0]);
			Face f = l.face();
			long d = Math.abs(2L * l.centre(k[1]) - (f.a() + f.b() - 1)) * 1000 + k[0]; // nearest the face centre, then first layout
			if (d < bestD) { bestD = d; best = k; }
		}
		return best;
	}

	// ---- decor ----

	/** Lays the facade decor the style asks for (see the class comment). Safe to call from a type's facade program after it set the plan. */
	public static void decorate(BuildContext c, FacadePlan plan) {
		FacadeStyle st = c.style().facade();
		Decor d = new Decor(c, plan);
		Massing m = c.massing();
		for (Face f : c.faces()) {
			Mass mass = m.mass(f.mass());
			if (!st.corners().equals("none")) corners(d, f);
			if (st.plinth() && f.storey() == 0) plinth(d, f);
			if (!st.timber().equals("none") && (st.timber().equals("all") || f.storey() >= 1)) timber(d, plan, f);
			if (st.bressumer() && mass.parent() != null && f.storey() == mass.from() && overhangs(mass, m.mass(mass.parent()), f.side())) bressumer(d, f, m);
		}
		for (Face f : c.faces()) {
			Mass mass = m.mass(f.mass());
			boolean top = f.storey() == mass.to() - 1;
			if (top ? st.cornice() : st.stringCourse()) ring(d, f);
		}
	}

	/** Corner posts (beam column) or quoins (trim corner cell plus alternating neighbours) at the face's outside corners. */
	private static void corners(Decor d, Face f) {
		boolean quoins = d.st.corners().equals("quoins");
		boolean[][] mk = Decor.mask(f);
		boolean any = false;
		for (int end = 0; end < 2; end++) {
			if (end == 0 ? !f.convexA() : !f.convexB()) continue;
			int p = end == 0 ? f.a() : f.b() - 1, dir = end == 0 ? 1 : -1;
			for (int y = f.y0(); y < f.y0() + f.height(); y++) {
				if (d.wallFree(f, p, y)) { mk[p - Decor.pa(f)][y - Decor.ya(f)] = true; any = true; }
				int q = p + dir;
				if (quoins && (y - f.y0() + f.side().ordinal()) % 2 == 0 && q > f.a() && q < f.b() - 1 && d.wallFree(f, q, y)) mk[q - Decor.pa(f)][y - Decor.ya(f)] = true;
			}
		}
		if (any) d.emit(f, quoins ? Role.TRIM : Role.BEAM, 0, mk, Decor.pa(f), Decor.ya(f));
	}

	/** Foundation-role bottom row of storey 0. */
	private static void plinth(Decor d, Face f) {
		boolean[][] mk = Decor.mask(f);
		boolean any = false;
		for (int p = f.a(); p < f.b(); p++) if (d.wallFree(f, p, f.y0())) { mk[p - Decor.pa(f)][f.y0() - Decor.ya(f)] = true; any = true; }
		if (any) d.emit(f, Role.FOUNDATION, 0, mk, Decor.pa(f), Decor.ya(f));
	}

	/**
	 * Half-timbering in the wall plane: a beam post on a pier at every bay boundary (the left cell if
	 * free, else the right one), and the slab line below the storey as a beam (the slab edge of storeys
	 * above the ground).
	 */
	private static void timber(Decor d, FacadePlan plan, Face f) {
		boolean[][] posts = Decor.mask(f);
		boolean any = false;
		for (FacadePlan.Layout l : plan.layouts()) {
			if (!l.face().equals(f)) continue;
			for (int i = 1; i < l.bays().length; i++) {
				int s = l.bayStart(i);
				for (int p : new int[] {s - 1, s}) {
					boolean free = true;
					for (int y = f.y0(); y < f.y0() + f.height(); y++) free &= d.wallFree(f, p, y);
					if (!free) continue;
					for (int y = f.y0(); y < f.y0() + f.height(); y++) posts[p - Decor.pa(f)][y - Decor.ya(f)] = true;
					any = true;
					break;
				}
			}
		}
		if (any) d.emit(f, Role.BEAM, 0, posts, Decor.pa(f), Decor.ya(f));
		if (f.storey() >= 1) {
			boolean[][] rail = Decor.mask(f);
			boolean anyRail = false;
			int y = f.y0() - 1;
			for (int p = f.a(); p < f.b(); p++) {
				if (d.c.role(f.x(p), y, f.z(p)) == Role.FLOOR) { rail[p - Decor.pa(f)][y - Decor.ya(f)] = true; anyRail = true; }
			}
			if (anyRail) d.emit(f, Role.BEAM, 0, rail, Decor.pa(f), Decor.ya(f));
		}
	}

	private static boolean overhangs(Mass m, Mass parent, Facing side) {
		return switch (side) {
			case NORTH -> m.z() < parent.z();
			case SOUTH -> m.maxZ() > parent.maxZ();
			case WEST -> m.x() < parent.x();
			case EAST -> m.maxX() > parent.maxX();
		};
	}

	/** A jetty's bressumer: the slab edge along the overhanging face as a beam. */
	private static void bressumer(Decor d, Face f, Massing m) {
		boolean[][] mk = Decor.mask(f);
		int y = m.grid().y(f.storey());
		boolean any = false;
		for (int p = f.a(); p < f.b(); p++) {
			if (d.c.role(f.x(p), y, f.z(p)) == Role.FLOOR) { mk[p - Decor.pa(f)][y - Decor.ya(f)] = true; any = true; }
		}
		if (any) d.emit(f, Role.BEAM, 0, mk, Decor.pa(f), Decor.ya(f));
	}

	/** A trim ring one cell out at the top of the face (the slab line above it): string course, or cornice under the eaves. */
	private static void ring(Decor d, Face f) {
		int y = f.y0() + f.height();
		boolean[][] mk = Decor.mask(f);
		boolean any = false;
		for (int p = f.a(); p < f.b(); p++) if (d.outFree(f, p, y)) { mk[p - Decor.pa(f) ][y - Decor.ya(f)] = true; any = true; }
		if (!any) return;
		d.emit(f, Role.TRIM, 1, mk, Decor.pa(f), Decor.ya(f));
		// the corner cell, diagonal to the wall corner, joins the two rings that meet there
		boolean[][] cm = Decor.mask(f);
		boolean anyC = false;
		for (int end = 0; end < 2; end++) {
			if (end == 0 ? !f.convexA() : !f.convexB()) continue;
			int edge = end == 0 ? f.a() : f.b() - 1, p = end == 0 ? f.a() - 1 : f.b();
			if (d.c.role(f.outsideX(edge), y, f.outsideZ(edge)) == Role.TRIM && d.outFree(f, p, y)) { cm[p - Decor.pa(f)][y - Decor.ya(f)] = true; anyC = true; }
		}
		if (anyC) d.emit(f, Role.TRIM, 1, cm, Decor.pa(f), Decor.ya(f));
	}
}
