package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.settlement.ExitTrim;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * CASCADE A1: the bounded exit-cut walk ({@link ExitTrim#cutBeyondWalk}) only proves "the cut lies at or beyond column m" when the whole
 * road's cut ({@link ExitTrim#fullCut}) says so, for every exit road and every m; and the trimmed plan of every owned tile is the one the
 * old path (the whole cut of every road, {@code -Dfb.exitcut.reference=true}) gives, replayed here from the public ExitTrim API.
 */
class ExitCutWalkTest {
	record Case(String label, boolean heavy, Supplier<WorldPlanner> planner, java.util.function.Function<WorldPlanner, SettlementSite> site) {}

	static List<Case> cases() {
		List<Case> out = new ArrayList<>();
		for (QuaySites s : QuaySites.all()) out.add(new Case(s.label(), s.heavy(), s.planner(), s.siteOf()));
		for (GateWorld g : GateWorld.all()) out.add(new Case("gate-" + g.site().id(), g.site().radius() >= 900, () -> g.sharedPlanner(), w -> g.site()));
		return out;
	}

	static ExitTrim.Plans lookup(WorldPlanner w, SettlementSite site) {
		return (x, z) -> w.tile(x, z).filter(i -> i.site().id().equals(site.id())).isPresent() ? w.rawPlan(x, z).plan() : null;
	}

	static int[] walk(Case c) {
		WorldPlanner w = c.planner().get();
		SettlementSite site = c.site().apply(w);
		SkeletonSource src = w.source(site);
		ExitTrim.Plans plans = lookup(w, site);
		int proved = 0, provable = 0;
		for (String id : ExitTrim.exitRoadIds(src)) {
			ExitTrim.Cut full = ExitTrim.fullCut(src, id, plans);
			int n = ExitTrim.columns(roadOf(src, id)).size();
			for (int m = 0; m <= n + 1; m++) {
				boolean beyond = full == null || full.index() >= m;
				boolean walk = ExitTrim.cutBeyondWalk(src, id, m, plans);
				if (beyond) provable++;
				if (walk) proved++;
				assertTrue(!walk || beyond, c.label() + " road " + id + " m " + m + ": walk says beyond, full cut index " + (full == null ? "none" : full.index()));
			}
		}
		return new int[] {proved, provable};
	}

	static dev.fractalbuildings.core.settlement.Road roadOf(SkeletonSource src, String id) {
		for (var cell : src.site().cells()) for (var r : src.skeleton(cell).roads()) if (r.id().equals(id)) return r;
		throw new IllegalArgumentException(id);
	}

	private void sweep(boolean heavy) {
		int proved = 0, provable = 0;
		for (Case c : cases()) {
			if (c.heavy() != heavy) continue;
			int[] r = walk(c);
			proved += r[0];
			provable += r[1];
		}
		assertTrue(provable > 0);
		assertTrue(proved * 4 >= provable, "the walk proves a useful share: " + proved + " of " + provable); // not vacuous: a walk that never proves passes the safety check
	}

	@Test
	void aBoundedWalkAgreesWithTheFullCut() { sweep(false); }

	@Tag("heavy")
	@Test
	void aBoundedWalkAgreesWithTheFullCutOnMetropolises() { sweep(true); }

	/** The old trimmedPlan, from the public API: every exit road crossing the tile through the whole road's cut. */
	static String reference(WorldPlanner w, int ix, int iz) {
		TilePlan raw = w.rawPlan(ix, iz);
		SettlementSite site = raw.info().site();
		SkeletonSource src = w.source(site);
		var rect = raw.plan().district().rect();
		ExitTrim.Plans plans = lookup(w, site);
		TreeMap<String, ExitTrim.Cut> cuts = new TreeMap<>();
		if (!ExitTrim.inCore(src, rect)) {
			for (String id : ExitTrim.exitRoadIds(src)) {
				if (!ExitTrim.isExitRoad(src, id)) continue;
				int m = ExitTrim.maxIndexIn(src, id, rect);
				if (m < 0 || ExitTrim.cutBeyond(src, id, m, raw.plan(), rect)) continue;
				ExitTrim.Cut c = ExitTrim.fullCut(src, id, plans);
				if (c == null || c.index() >= m || ExitTrim.gateServed(src, id)) continue;
				cuts.put(id, c);
			}
		}
		return DistrictJson.write(ExitTrim.apply(raw.plan(), cuts));
	}

	private void trimmed(boolean heavy) {
		int tiles = 0;
		for (Case c : cases()) {
			if (c.heavy() != heavy) continue;
			WorldPlanner w = c.planner().get();
			SettlementSite site = c.site().apply(w);
			int pad = site.radius() + 300;
			DistrictGrid.Tile a = w.tileAt(site.centre().x() - pad, site.centre().z() - pad), b = w.tileAt(site.centre().x() + pad, site.centre().z() + pad);
			for (int iz = a.iz(); iz <= b.iz(); iz++) {
				for (int ix = a.ix(); ix <= b.ix(); ix++) {
					if (w.tile(ix, iz).filter(i -> i.site().id().equals(site.id())).isEmpty()) continue;
					assertEquals(reference(w, ix, iz), DistrictJson.write(w.plan(ix, iz).plan()), c.label() + " tile " + ix + "," + iz);
					tiles++;
				}
			}
		}
		assertTrue(tiles > 0);
	}

	@Test
	void trimmedPlansUnchanged() { trimmed(false); }

	@Tag("heavy")
	@Test
	void trimmedPlansUnchangedOnMetropolises() { trimmed(true); }
}
