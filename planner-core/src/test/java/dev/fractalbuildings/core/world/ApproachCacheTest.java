package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.fractalbuildings.core.road.RoadNet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 6.15 ROADJOIN: the approach columns of a chunk do not depend on the cache sizes (the planner's answers never do). */
class ApproachCacheTest {
	static List<String> columns(WorldPlanner w, int cx, int cz) {
		List<String> out = new ArrayList<>();
		for (List<RoadNet.Col> l : w.roadColumnsAround(cx, cz)) for (RoadNet.Col c : l) out.add(c.x() + "," + c.z() + "," + c.y() + "," + c.crossing());
		return out;
	}

	@Test
	void approachChunksAreTheSameWithTinyCaches() {
		WorldPlanner big = JoinWorld.playtest(WorldPlanner.Caches.DEFAULT), tiny = JoinWorld.playtest(WorldPlanner.Caches.all(1));
		int[][] chunks = {{226, 90}, {227, 90}, {200, 106}, {204, 110}}; // the hamlet gate (3616,1440) and the metropolis exits
		int total = 0;
		for (int[] c : chunks) {
			var a = columns(big, c[0], c[1]);
			total += a.size();
			assertEquals(a, columns(tiny, c[0], c[1]), "chunk " + c[0] + "," + c[1]);
		}
		assertFalse(total == 0, "the chunks hold road columns");
	}
}
