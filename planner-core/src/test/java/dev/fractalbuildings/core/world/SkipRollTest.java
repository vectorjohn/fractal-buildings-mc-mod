package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.BiomeClass;
import dev.fractalbuildings.core.region.CellDecision;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionPlanner;
import dev.fractalbuildings.core.road.RoadNet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SKIPROLL (docs/review/regionperf-decision.md option D): a region cell whose roll can never settle is decided with no terrain
 * read. On the recorded worlds (pt1573, w12345, w987) every cell's settled flag, claim and site, and the road graph (settlements
 * and links), are identical with and without the skip; the skipped cells are counted and the skip reads fewer columns.
 */
class SkipRollTest {
	private record Run(List<String> cells, List<String> graph, int skipped, long reads, int settled) {}

	private static Run run(long seed, WorldProbes probes, boolean skip) {
		WorldConfig cfg = WorldConfig.defaults();
		long[] reads = {0};
		var region = probes.region();
		dev.fractalbuildings.core.region.RegionProbe counting = new dev.fractalbuildings.core.region.RegionProbe() {
			@Override public int floorY(int x, int z) { reads[0]++; return region.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { return region.waterDepth(x, z); }
			@Override public String biome(int x, int z) { reads[0]++; return region.biome(x, z); }
		};
		RegionPlanner rp = new RegionPlanner(new RegionField(seed, cfg.cellSize()), counting);
		rp.maxRadius = cfg.maxRadius();
		rp.baseChance *= cfg.frequency();
		rp.skipDeadRolls = skip;
		FieldRegionView view = new FieldRegionView(rp, probes.window(), FieldRegionView.DEFAULT_CACHE);
		RoadNet net = new RoadNet(rp.field, view, probes.window(), probes.region(), probes.roadExact(), FieldRegionView.DEFAULT_CACHE);
		List<String> cells = new ArrayList<>();
		int skipped = 0, settled = 0;
		int S = cfg.cellSize();
		var w = probes.window();
		for (var r : view.resolveWindow(w.x0(), w.z0(), w.x1(), w.z1())) {
			CellDecision d = r.decision();
			if (d.settled()) settled++;
			if (d.reason().equals("roll") && d.suitability() == 0 && d.chance() == 0) skipped++;
			cells.add(d.cx() + "," + d.cz() + " " + d.settled() + " " + d.tier() + " " + r.claimedBy() + " " + r.site());
		}
		List<String> graph = new ArrayList<>();
		for (var s : net.allSettlements()) graph.add(s.toString());
		for (var e : net.allEdges()) graph.add(e.toString());
		return new Run(cells, graph, skipped, reads[0], settled);
	}

	private static void check(String name, long seed, WorldProbes probes) {
		Run full = run(seed, probes, false), skip = run(seed, probes, true);
		assertTrue(full.cells.size() > 100 && full.settled > 20, name + ": cells " + full.cells.size() + " settled " + full.settled);
		assertEquals(0, full.skipped, name + ": the reference run skips nothing");
		assertEquals(full.cells, skip.cells, name + ": settled/claim/site differ");
		assertEquals(full.graph, skip.graph, name + ": road graph differs");
		assertTrue(full.graph.size() > 50, name + ": graph " + full.graph.size());
		double share = skip.skipped / (double) full.cells.size();
		assertTrue(share > 0.2 && share < 0.5, name + ": skipped share " + share);
		assertTrue(skip.reads < full.reads, name + ": reads " + skip.reads + " vs " + full.reads);
		System.out.printf("SKIPROLL %s: cells %d, skipped %d (%.1f%%), reads %d -> %d (%.1f%%), maxMultiplier %.2f%n", name, full.cells.size(),
			skip.skipped, 100 * share, full.reads, skip.reads, 100.0 * skip.reads / full.reads, BiomeClass.maxMultiplier());
	}

	private static WorldProbes world(String dump) {
		return JoinWorld.probes(JoinWorld.read(GateWorld.FIXTURES, dump));
	}

	@Test
	void pt1573() { check("pt1573", JoinWorld.PT1573_SEED, world("pt1573-world-s64")); }

	@Test
	void w12345() { check("w12345", 12345, world("world-12345-s64")); }

	@Test
	void w987() { check("w987", 987654321L, world("world-987654321-s64")); }

	@Test
	void bound() {
		assertEquals(1.0, BiomeClass.maxMultiplier(), 1e-12);
		assertEquals(0.65, new RegionPlanner(new RegionField(1, 1024), null).baseChance * BiomeClass.maxMultiplier(), 1e-12);
	}
}
