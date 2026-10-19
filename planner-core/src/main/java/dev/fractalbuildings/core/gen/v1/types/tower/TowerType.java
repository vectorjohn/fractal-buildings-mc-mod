package dev.fractalbuildings.core.gen.v1.types.tower;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.mass.MassingOps;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.style.MassingStyle;
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

	@Override
	public Massing mass(BuildContext c) {
		BuildingSpec sp = c.spec();
		MassingStyle st = c.style().massing();
		for (int n = sp.storeys(); n >= 2; n--) {
			Massing m = attempt(c, st, n);
			if (m != null) return m;
		}
		// last resort: one plain storey that always fits the minimum envelope
		StoreyGrid g = StoreyGrid.of(1, st.ground(), st.typical());
		return new Massing(g, List.of(Mass.ground("main0", "main", 0, 0, Math.min(sp.width(), 7), Math.min(sp.depth(), 7), 1)), List.of());
	}

	private static Massing attempt(BuildContext c, MassingStyle st, int n) {
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
