package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionProbe;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** DECKDIAG F3 on a synthetic plane: a lake strip on lattice row z=32 runs along the straight exit-port line z=0. */
class ShoreRerouteTest {
	record Plane() implements TerrainProbe, dev.fractalbuildings.core.probe.BiomeProbe {
		@Override public int floorY(int x, int z) { return 64; }
		/** The lake: z 28..36, only 1 deep: not "water" to the plain rule, wet to the shore rule. */
		@Override public int waterDepth(int x, int z) { return z >= 28 && z <= 36 ? 1 : 0; }
		@Override public String biome(int x, int z) { return "minecraft:plains"; }
	}

	static WorldPlanner plane() {
		Plane t = new Plane();
		int s = RegionProbe.STRIDE;
		RegionMap.Window w = new RegionMap.Window(-4 * s, -4 * s, 8, 8);
		var probes = new WorldProbes(RegionProbe.of(t, t), CoarseProbe.of(t, t, 32), t, t, w, t);
		return new WorldPlanner(1L, WorldConfig.defaults(), probes, null, WorldPlanner.Caches.DEFAULT);
	}

	static int maxAbsZ(List<int[]> pts) {
		int m = 0;
		for (int[] p : pts) m = Math.max(m, Math.abs(p[1]));
		return m;
	}

	@Test
	void shoreRerouteMovesOffTheLakeLine() {
		WorldPlanner w = plane();
		int[] from = {0, 0}, to = {320, 0};
		List<int[]> plain = w.rerouteApproach(from, to, Set.of(), List.of(), null, false);
		List<int[]> shore = w.rerouteApproach(from, to, Set.of(), List.of(), null, true);
		assertEquals(0, maxAbsZ(plain), "the plain reroute keeps the straight line beside the lake");
		assertTrue(shore.stream().anyMatch(p -> p[1] <= -32), "the shore reroute moves at least one lattice column away");
		assertTrue(WorldPlanner.shoreDetourOk(plain, shore), "a 32-block step out is within the detour limit");
	}
}
