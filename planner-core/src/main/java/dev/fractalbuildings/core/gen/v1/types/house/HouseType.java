package dev.fractalbuildings.core.gen.v1.types.house;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.style.MassingStyle;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.rand.Draws;
import java.util.List;

/**
 * A house. Foundation default: v0's box (one mass, 5..15 wide, 5..11 deep, centred in the envelope,
 * {@code spec.storeys()} storeys, a core along the back wall when there is more than one). The
 * massing step (4.2) replaces {@link #mass} with wings, jetties and setbacks; the house step (4.6)
 * owns the type afterwards.
 */
public final class HouseType implements BuildingType {
	public static final String ID = "house";

	private static final TypeLimits LIMITS = new TypeLimits(5, 5, 1, 3, 1, 1, 1, false,
			List.of(new int[] {7, 7}, new int[] {12, 10}, new int[] {20, 20}, new int[] {31, 17}), 500, 1);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	@Override
	public Massing mass(BuildContext c) {
		BuildingSpec sp = c.spec();
		MassingStyle st = c.style().massing();
		Draws r = c.draws("massing/box");
		int w = r.range(Math.min(5, sp.width()), Math.min(sp.width(), 15));
		int d = r.range(Math.min(5, sp.depth()), Math.min(sp.depth(), 11));
		int n = sp.storeys();
		StoreyGrid g = StoreyGrid.of(n, st.ground(), st.typical());
		while (n > 1 && (g.y(n) + 2 > sp.maxHeight() || w - 2 < Core.length(g, 0, n))) {
			n--;
			g = StoreyGrid.of(n, st.ground(), st.typical());
		}
		int x = (sp.width() - w) / 2, z = (sp.depth() - d) / 2;
		Mass main = Mass.ground("main0", "main", x, z, w, d, n);
		List<Core> cores = n > 1 ? List.of(new Core(x + 1, z + d - 3, Core.length(g, 0, n), 2, 0, n)) : List.of();
		return new Massing(g, List.of(main), cores);
	}
}
