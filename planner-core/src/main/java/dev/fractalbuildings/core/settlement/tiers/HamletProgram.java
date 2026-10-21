package dev.fractalbuildings.core.settlement.tiers;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.settlement.Landmark;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.SkeletonContext;
import java.util.List;

/**
 * Hamlet (D1): one short track through the hub along the gentlest axis, houses and a workshop along it, no square and no
 * cross street. The track is 22 to 40 blocks long so the frontage lots on its two sides come to 3 to 8 buildings.
 */
public final class HamletProgram extends StraightProgram {
	@Override int minLength() { return 12; }

	@Override public boolean lanes() { return false; }

	@Override public boolean ports() { return false; }

	@Override int borderClear() { return 12; }

	@Override int halfLength(SkeletonContext c) {
		return 11 + Hash64.below(Hash64.hash(c.seed, "hamlet-half", c.site.centre().x(), c.site.centre().z()), 10);
	}

	@Override boolean crossStreet(SkeletonContext c) { return false; }

	@Override public List<Landmark> landmarks(SkeletonContext c, Pt hub) { return List.of(); }
}
