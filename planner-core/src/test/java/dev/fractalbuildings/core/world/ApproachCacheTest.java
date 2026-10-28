package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.fractalbuildings.core.road.RoadNet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 6.15 ROADJOIN: the approach columns of a chunk do not depend on the cache sizes (the planner's answers never do).
 *
 * <p>TESTSPEED: with every cache at 1 (strict LRU) the metropolis chunks (200,106 and 204,110) replan the settlement for each of its nine
 * neighbours and takes about 40 minutes alone, so that one case is {@code @Tag("heavy")} (run by {@code scripts/test-all.sh --full}
 * or {@code -PfbTests=heavy}). The default run shrinks one cache layer at a time over all four chunks (each evicts and recomputes
 * its layer on every access, seconds), and all caches at 1 over the chunks that are cheap that way.
 */
class ApproachCacheTest {
	static List<String> columns(WorldPlanner w, int cx, int cz) {
		List<String> out = new ArrayList<>();
		for (List<RoadNet.Col> l : w.roadColumnsAround(cx, cz)) for (RoadNet.Col c : l) out.add(c.x() + "," + c.z() + "," + c.y() + "," + c.crossing());
		return out;
	}

	/** The hamlet gate (3616,1440) and the metropolis exits. */
	private static final int[][] CHUNKS = {{226, 90}, {227, 90}, {200, 106}, {204, 110}};

	private static void sameAs(WorldPlanner.Caches tiny, int[][] chunks) {
		WorldPlanner big = JoinWorld.playtest(WorldPlanner.Caches.DEFAULT), small = JoinWorld.playtest(tiny);
		int total = 0;
		for (int[] c : chunks) {
			var a = columns(big, c[0], c[1]);
			total += a.size();
			assertEquals(a, columns(small, c[0], c[1]), "chunk " + c[0] + "," + c[1] + " with " + tiny);
		}
		assertFalse(total == 0, "the chunks hold road columns");
	}

	/** One layer at a time at 1, the rest at the defaults: every layer's eviction path is exercised on all four chunks. */
	@Test
	void approachChunksAreTheSameWithOneTinyCacheLayer() {
		var d = WorldPlanner.Caches.DEFAULT;
		var layers = List.of(
				new WorldPlanner.Caches(1, d.sources(), d.skeletons(), d.region(), d.plans(), d.buildings(), d.ground()),
				new WorldPlanner.Caches(d.tiles(), 1, d.skeletons(), d.region(), d.plans(), d.buildings(), d.ground()),
				new WorldPlanner.Caches(d.tiles(), d.sources(), 1, d.region(), d.plans(), d.buildings(), d.ground()),
				new WorldPlanner.Caches(d.tiles(), d.sources(), d.skeletons(), 1, d.plans(), d.buildings(), d.ground()),
				new WorldPlanner.Caches(d.tiles(), d.sources(), d.skeletons(), d.region(), 1, 1, 1));
		for (var c : layers) sameAs(c.strict(), CHUNKS);
	}

	/** Everything at 1 over the hamlet gate chunks (about 35 s). */
	@Test
	void approachChunksAreTheSameWithEveryCacheAtOneOnTheHamletGate() {
		sameAs(WorldPlanner.Caches.all(1), new int[][] {{226, 90}, {227, 90}});
	}

	/** The original case: everything at 1 on the metropolis chunks (about 40 minutes each). */
	@Tag("heavy")
	@Test
	void approachChunksAreTheSameWithEveryCacheAtOneOnTheMetropolisChunks() {
		sameAs(WorldPlanner.Caches.all(1), new int[][] {{200, 106}, {204, 110}});
	}
}
