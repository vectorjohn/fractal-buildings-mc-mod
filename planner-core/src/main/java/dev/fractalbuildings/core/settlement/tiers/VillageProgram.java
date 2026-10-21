package dev.fractalbuildings.core.settlement.tiers;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.settlement.Landmark;
import dev.fractalbuildings.core.settlement.LandmarkKind;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.SkeletonContext;
import java.util.List;

/**
 * Village (D1): a main street through the hub across the radius, a cross street on half of the villages meeting it 40 to 90
 * blocks from the hub, a green at the hub with a temple (70 %) or a hall facing it from the side away from the street, and the
 * lanes that district detail branches off the streets.
 */
public final class VillageProgram extends StraightProgram {
	@Override protected int landmarkMargin() { return 14; }

	@Override boolean flatSquare() { return true; }

	@Override int minLength() { return 64; }

	@Override public boolean ports() { return false; }

	@Override int borderClear() { return 28; }

	@Override int halfLength(SkeletonContext c) { return Math.min(Math.max(48, c.site.radius()), 50 + c.site.radius() / 5); }

	@Override boolean crossStreet(SkeletonContext c) {
		return Hash64.below(Hash64.hash(c.seed, "village-cross", c.site.centre().x(), c.site.centre().z()), 2) == 0;
	}

	@Override int crossReach(SkeletonContext c) { return Math.max(32, c.site.radius() / 3); }

	@Override public List<Landmark> landmarks(SkeletonContext c, Pt hub) {
		Layout l = layout(c, hub);
		return super.landmarks(c, l == null ? hub : l.start());
	}

	/** A temple on 70 % of villages, a hall on 30 %. */
	@Override protected LandmarkKind villageFace(long h) { return Hash64.below(h, 10) < 7 ? LandmarkKind.TEMPLE : LandmarkKind.HALL; }

	/** Sides off (the original hub is recomputed: {@code hub} here is the shifted anchor)  the street's axis first (so the facing buildings never sit on the main street), the axis sides last. */
	@Override protected int sideAt(SkeletonContext c, Pt hub, int side0, int k, int q) {
		Layout l = layout(c, hub(c));
		if (l == null) return super.sideAt(c, hub, side0, k, q);
		int[] perp = l.alongX() ? new int[] {0, 2} : new int[] {1, 3}, axis = l.alongX() ? new int[] {1, 3} : new int[] {0, 2};
		int flip = side0 & 1;
		return switch (q) {
			case 0 -> perp[flip];
			case 1 -> perp[1 - flip];
			case 2 -> axis[flip];
			default -> axis[1 - flip];
		};
	}
}
