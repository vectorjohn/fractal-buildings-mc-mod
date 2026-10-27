package dev.fractalbuildings.core.gen.v1.types.tower;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.facade.OpeningPass;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.mass.MassingOps;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.style.MassingStyle;
import dev.fractalbuildings.core.gen.v1.structure.Stilts;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.rand.Draws;
import java.util.List;

/**
 * A pre-modern tower (watchtower, bell tower, keep): a narrow square-ish shaft of 4 to 10 storeys,
 * always at least twice as tall as it is wide, with one stair core through every storey. Variants by
 * draw: a plain shaft, a wider base of one or two storeys (a plinth), or a set-back top storey (the
 * bell chamber or lookout). Heights come from the style's storey heights, so the tower reads as the
 * style's building scaled up; roofs follow the style (a tall one gets a flat roof with a parapet).
 *
 * <p>On a floor this small (interior 5 x 5 with a 5 x 2 core) a floor is one room beside the stair;
 * that is intended: a tower storey is a single chamber.
 */
public final class TowerType implements BuildingType {
	public static final String ID = "tower";

	/** Footprint range of the shaft's widest part. */
	static final int MIN_SIDE = 7, MAX_SIDE = 11;

	private static final TypeLimits LIMITS = new TypeLimits(7, 7, 4, 10, 1, 1, 1, false,
			List.of(new int[] {9, 9}, new int[] {11, 11}, new int[] {13, 13}, new int[] {15, 15}), 250, 2);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	/** Chance in 100 of the stilted lookout and of the top-heavy tower (the rest are the classic shafts). */
	static final int STILTED = 18, TOP_HEAVY = 18;
	/** Kind of the raised room of a stilted lookout. */
	public static final String LOOKOUT = "lookout";

	/** The watchtower variant this seed draws; the massing falls back to a classic shaft if it does not fit. */
	static int variant(BuildContext c) { return c.draws("tower/variant").below(100); }

	@Override
	public Massing mass(BuildContext c) {
		BuildingSpec sp = c.spec();
		MassingStyle st = c.style().massing();
		if (variant(c) < STILTED) {
			Massing m = stilted(c, st);
			if (m != null) return m;
		}
		for (int n = sp.storeys(); n >= 2; n--) {
			Massing m = attempt(c, st, n);
			if (m != null) return m;
		}
		// last resort: one plain storey that always fits the minimum envelope
		StoreyGrid g = StoreyGrid.of(1, st.ground(), st.typical());
		return new Massing(g, List.of(Mass.ground("main0", "main", 0, 0, Math.min(sp.width(), 7), Math.min(sp.depth(), 7), 1)), List.of());
	}

	// ---- stilted lookout: one small room on four legs, a stair up the front ----

	/** Leg height for a lookout whose room is {@code g} high, or 0 if the envelope has no room for legs (see {@link Stilts}). */
	static int legHeight(BuildContext c, StoreyGrid g) {
		BuildingSpec sp = c.spec();
		// the stair is one step per block of height along the front wall, so the legs are at most about half the envelope width
		int cap = Math.min(8, Math.min((sp.width() + 1) / 2, sp.maxHeight() - 2 - g.y(1)));
		return cap < Stilts.TOWER_MIN_LIFT ? 0 : c.draws("tower/legs").range(Stilts.TOWER_MIN_LIFT, cap);
	}

	private static Massing stilted(BuildContext c, MassingStyle st) {
		BuildingSpec sp = c.spec();
		Draws r = c.draws("massing/lookout");
		StoreyGrid g = StoreyGrid.of(1, Math.max(4, st.ground()), st.typical());
		int lift = legHeight(c, g);
		if (lift == 0 || sp.depth() < 7) return null;
		// slender: the raised room is at most two thirds as wide as legs plus room are high
		int side = Math.min(7, (lift + g.y(1)) * 2 / 3);
		int w = Math.min(sp.width(), r.range(5, side)), d = Math.min(sp.depth() - 1, r.range(5, side));
		if (r.chance(1, 2)) w = d = Math.min(w, d);
		List<Integer> xs = new java.util.ArrayList<>();
		for (int x = 0; x <= sp.width() - w; x++) xs.add(x);
		if (xs.isEmpty()) return null;
		int x = xs.get(r.below(xs.size())), z = r.range(1, Math.min(2, sp.depth() - d));
		Massing m = new Massing(g, List.of(new Mass("main0", LOOKOUT, x, z, w, d, 0, 1, null, false)), List.of());
		return MassingCheck.check(m, sp.width(), sp.depth(), sp.maxHeight() - lift).isEmpty() ? m : null;
	}

	@Override
	public Stilts stilts(BuildContext c) {
		Mass main = c.massing().main();
		if (!main.kind().equals(LOOKOUT)) return null;
		return new Stilts(legHeight(c, c.massing().grid()), c.draws("tower/braced").chance(3, 4));
	}

	// ---- top-heavy: a shaft with a distinctly larger room on top ----

	private static Massing topHeavy(BuildContext c, MassingStyle st, int n) {
		BuildingSpec sp = c.spec();
		Draws r = c.draws("massing/topheavy", n);
		if (n < 4) return null;
		StoreyGrid g = StoreyGrid.of(n, st.ground(), st.typical());
		int len = Core.length(g, 0, n);
		int lo = Math.max(MIN_SIDE, len + 2);
		int over = r.chance(7, 10) ? 2 : 1;
		int rooms = r.chance(1, 3) ? 2 : 1;
		// the room (shaft + 2 over) is the widest part: at least twice as tall as that
		int hi = Math.min(MAX_SIDE, g.y(n) / 2 - 2 * over);
		int hiW = Math.min(hi, sp.width() - 2 * over), hiD = Math.min(hi, sp.depth() - 2 * over);
		if (hiW < lo || hiD < lo) return null;
		int w = r.range(lo, hiW), d = r.range(lo, hiD);
		if (r.chance(1, 2)) d = w = Math.min(w, d);
		int x = r.range(over, sp.width() - w - over), z = r.range(Math.min(over, sp.depth() - d - over), Math.min(over + 1, sp.depth() - d - over));
		Mass shaft = Mass.ground("main0", "main", x, z, w, d, n - rooms);
		Mass room = MassingOps.jetty("jetty1", shaft, new int[] {over, over, over, over}, n, over, sp.width(), sp.depth());
		if (room == null) return null;
		List<Mass> hosts = List.of(shaft, room);
		Core k = MassingOps.core(g, hosts, List.of(), 0, n, r);
		if (k == null) return null;
		Massing m = new Massing(g, hosts, List.of(k));
		return MassingCheck.check(m, sp.width(), sp.depth(), sp.maxHeight()).isEmpty() ? m : null;
	}

	private static Massing attempt(BuildContext c, MassingStyle st, int n) {
		int v = variant(c);
		if (v >= STILTED && v < STILTED + TOP_HEAVY) {
			Massing m = topHeavy(c, st, n);
			if (m != null) return m;
		}
		return classic(c, st, n);
	}

	private static Massing classic(BuildContext c, MassingStyle st, int n) {
		BuildingSpec sp = c.spec();
		Draws r = c.draws("massing/tower", n);
		StoreyGrid g = StoreyGrid.of(n, st.ground(), st.typical());
		int len = Core.length(g, 0, n);
		int lo = Math.max(MIN_SIDE, len + 2);
		// height >= 2 x footprint side
		int hi = Math.min(MAX_SIDE, g.y(n) / 2);
		int hiW = Math.min(hi, sp.width()), hiD = Math.min(hi, sp.depth());
		if (hiW < lo || hiD < lo) return null;
		int base = r.below(100);
		boolean plinth = base < 30 && n >= 4;
		boolean crown = !plinth && base < 55 && n >= 4;
		// the shaft above a plinth is 2 narrower; it must still hold the core
		int loW = plinth || crown ? lo + 2 : lo, loD = loW;
		if (hiW < loW || hiD < loD) { plinth = false; crown = false; loW = lo; loD = lo; }
		int w = r.range(loW, hiW), d = r.range(loD, hiD);
		if (r.chance(1, 2)) d = w = Math.min(w, d);
		int x = r.range(0, sp.width() - w), z = r.range(Math.min(1, sp.depth() - d), Math.min(2, sp.depth() - d));
		List<Mass> hosts;
		if (plinth) {
			int pn = r.range(1, Math.min(2, n - 2));
			Mass podium = Mass.ground("main0", "podium", x, z, w, d, pn);
			hosts = MassingOps.stack(podium, new int[] {pn, n - pn}, new int[] {1});
		} else if (crown) {
			Mass shaft = Mass.ground("main0", "main", x, z, w, d, n - 1);
			hosts = MassingOps.stack(shaft, new int[] {n - 1, 1}, new int[] {1});
		} else {
			hosts = List.of(Mass.ground("main0", "main", x, z, w, d, n));
		}
		if (hosts == null) return null;
		// with a plinth the core must sit inside the narrower shaft: MassingOps.core uses the common interior
		Core k = MassingOps.core(g, hosts, List.of(), 0, n, r);
		if (k == null) return null;
		Massing m = new Massing(g, hosts, List.of(k));
		return MassingCheck.check(m, sp.width(), sp.depth(), sp.maxHeight()).isEmpty() ? m : null;
	}


	/** Watchtower variants (a stilted lookout, a top-heavy tower) have window holes more often than the classic shafts. */
	@Override
	public Program openings() {
		return c -> {
			boolean watch = c.stilts() != null || c.massing().masses().stream().anyMatch(m -> m.kind().equals("jetty"));
			boolean holes = c.draws("tower/holes").chance(watch ? 3 : 1, watch ? 5 : 4);
			OpeningPass.run(c, e -> OpeningPass.door(c, e), holes);
		};
	}
	@Override
	public RoomProgram rooms() { return ROOMS; }

	/** Guard room below, chambers above with a chest and a bed, a lookout at the top is just the last chamber. */
	private static final RoomProgram ROOMS = new RoomProgram() {
		@Override
		public String use(Role role, int area, Draws d) { return RoomProgram.DEFAULT.use(role, area, d); }

		@Override
		public List<String> hooks(String use, int area, Draws d) {
			return switch (use) {
				case "entry" -> List.of("chest", "table");
				case "room", "hall" -> List.of("chest", "bed", "table").subList(0, Math.min(3, 1 + area / 8));
				default -> RoomProgram.DEFAULT.hooks(use, area, d);
			};
		}
	};
}
