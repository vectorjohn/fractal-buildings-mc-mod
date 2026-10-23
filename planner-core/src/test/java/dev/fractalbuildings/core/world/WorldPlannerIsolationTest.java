package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.settlement.LotBrief;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** One planner per world: planners share nothing, and the config's style list reaches the briefs. */
class WorldPlannerIsolationTest {
	static final GateWorld A = GateWorld.load("gate-joined-city");


	/** The same settlement (same site id) under another world seed. */
	static WorldPlanner otherWorld(GateWorld g, long seed, WorldPlanner.Caches caps) {
		return WorldPlanner.forSite(seed, WorldConfig.defaults(), g.probes(), null, caps, g.site());
	}

	static Map<String, String> plans(WorldPlanner w, GateWorld g) {
		Map<String, String> out = new TreeMap<>();
		for (var e : WorldPlannerParityTest.planned(w, g).entrySet()) {
			var plan = w.plan(e.getValue().ix(), e.getValue().iz()).plan();
			assertEquals(w.seed(), plan.seed(), "a plan carries its own world's seed: " + e.getKey());
			out.put(e.getKey(), DistrictJson.write(plan));
		}
		return out;
	}

	@Test
	void twoWorldsOnOnePoolNeverShareACacheEntry() throws Exception {
		GateWorld b = A; // plans enumerates by window, so the same GateWorld serves both seeds
		long seedB = A.seed() + 1;
		Map<String, String> wantA = plans(A.planner(WorldPlanner.Caches.DEFAULT, null), A);
		Map<String, String> wantB = plans(otherWorld(A, seedB, WorldPlanner.Caches.DEFAULT), A);
		assertNotEquals(wantA, wantB, "the two seeds plan different cities");
		// interleaved on 8 threads over two live planners
		WorldPlanner pa = A.planner(WorldPlanner.Caches.all(2), null), pb = otherWorld(A, seedB, WorldPlanner.Caches.all(2));
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Future<Map<String, String>>> fa = new ArrayList<>(), fb = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				fa.add(pool.submit(() -> plans(pa, A)));
				fb.add(pool.submit(() -> plans(pb, b)));
			}
			for (var f : fa) assertEquals(wantA, f.get(5, TimeUnit.MINUTES));
			for (var f : fb) assertEquals(wantB, f.get(5, TimeUnit.MINUTES));
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void aDisabledStyleBecomesPlain() {
		GateWorld g = GateWorld.load("gate-border-desert-jungle");
		WorldPlanner all = g.planner(WorldPlanner.Caches.DEFAULT, null);
		TreeSet<String> used = new TreeSet<>();
		for (TileInfo t : WorldPlannerParityTest.planned(all, g).values()) for (LotBrief l : all.plan(t.ix(), t.iz()).plan().lots()) used.add(l.style());
		assertTrue(used.size() >= 2, "the window mixes styles: " + used);
		String keep = used.first().equals("plain") ? used.last() : used.first();
		WorldConfig cfg = new WorldConfig(1024, 1.0, dev.fractalbuildings.core.region.Tiers.DEFAULT_MAX_RADIUS, Set.of(keep), WorldConfig.defaults().genVersion());
		WorldPlanner only = WorldPlanner.forSite(g.seed(), cfg, g.probes(), null, WorldPlanner.Caches.DEFAULT, g.site());
		int changed = 0, same = 0;
		for (TileInfo t : WorldPlannerParityTest.planned(all, g).values()) {
			List<LotBrief> a = all.plan(t.ix(), t.iz()).plan().lots(), b = only.plan(t.ix(), t.iz()).plan().lots();
			assertEquals(a.size(), b.size());
			for (int i = 0; i < a.size(); i++) {
				LotBrief x = a.get(i), y = b.get(i);
				assertEquals(x.id(), y.id());
				if (x.style().equals(keep) || x.style().equals("plain")) { assertEquals(x, y); same++; }
				else { assertEquals("plain", y.style()); assertEquals(x.type(), y.type()); assertEquals(x.status(), y.status()); changed++; }
			}
			for (BuildingPiece p : only.buildings(t.ix(), t.iz())) assertTrue(p.brief().style().equals(keep) || p.brief().style().equals("plain"));
		}
		assertTrue(changed > 0 && same > 0, "changed " + changed + " same " + same);
	}
}
