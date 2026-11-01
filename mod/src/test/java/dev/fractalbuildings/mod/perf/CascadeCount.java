package dev.fractalbuildings.mod.perf;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import dev.fractalbuildings.core.world.TilePlan;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.TerrainDumpTool;
import dev.fractalbuildings.mod.probe.ChunkFill;
import dev.fractalbuildings.mod.probe.LiveProbes;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * CASCADE step 1 (docs/plans/jflazy.md): what the neighbours of the slowest cold chunk cost, as counts. A fresh planner over the game's live
 * probes with no eviction (one thread, no reads pool, so every count is deterministic and needs no quiet host), the tile of chunk (148,-360)
 * of the metropolis of {@link PerfPlannerTest} planned, then {@code ground} of each of its 8 neighbour tiles, as {@code SpillClear} reads them.
 * Counts are read from the planner's caches by reflection (a measurement; nothing depends on it): with no eviction an entry is one computation.
 * Chunk fills are counted by the part of the planner on the stack ({@link PerfReachTest#purpose}), per step.
 */
final class CascadeCount {
	/** Chunk (148,-360): the slowest cold chunk of the perf run (block 2376,-5752). */
	static final int BLOCK_X = 2376, BLOCK_Z = -5752;

	record Step(String name, Map<String, Long> counts, Map<String, Long> fillsByPart, Map<String, Long> rawPlansBySettlement) { }

	record Result(List<Step> steps, String chunkTile) {
		Step step(String name) { return steps.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow(); }
		long total(String key) { return step("2 neighbours ground").counts().get(key) - step("1 plan").counts().get(key); }
	}

	static Result run() throws Exception {
		NoiseBasedChunkGenerator gen = TerrainDumpTool.generator();
		RandomState rs = TerrainDumpTool.randomState(PerfPlannerTest.SEED);
		Map<String, LongAdder> fills = new ConcurrentHashMap<>();
		ChunkFill fill = (g, r, cx, cz) -> {
			fills.computeIfAbsent(PerfReachTest.purpose(), k -> new LongAdder()).increment();
			return ChunkFill.DEFAULT.fill(g, r, cx, cz);
		};
		LiveProbes lp = new LiveProbes(gen, rs, fill, 1 << 16); // no eviction: every fill is a first fill
		WorldPlanner p = new WorldPlanner(PerfPlannerTest.SEED, WorldConfig.defaults(), lp.world(lp.exact()), null, WorldPlanner.Caches.all(1 << 16));
		DistrictGrid.Tile t = p.grid().tileAt(BLOCK_X, BLOCK_Z);
		List<Step> steps = new java.util.ArrayList<>();
		p.plan(t.ix(), t.iz());
		steps.add(snapshot("1 plan", p, lp, fills));
		for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
			if (dx == 0 && dz == 0) continue;
			if (p.tile(t.ix() + dx, t.iz() + dz).isPresent()) p.ground(t.ix() + dx, t.iz() + dz);
		}
		steps.add(snapshot("2 neighbours ground", p, lp, fills));
		return new Result(steps, t.ix() + "," + t.iz());
	}

	private static Step snapshot(String name, WorldPlanner p, LiveProbes lp, Map<String, LongAdder> fills) throws Exception {
		Map<String, Long> c = new java.util.LinkedHashMap<>();
		Map<String, Long> perSettlement = new TreeMap<>();
		Map<Long, TilePlan> raw = PerfReachTest.entries(PerfReachTest.get(p, "rawPlans"));
		for (TilePlan tp : raw.values()) perSettlement.merge(tp.info().site().id(), 1L, Long::sum);
		c.put("chunkFills", lp.chunkFills());
		c.put("rawPlans", (long) raw.size());
		c.put("rawPlanSettlements", (long) perSettlement.size());
		c.put("plans", (long) PerfReachTest.entries(PerfReachTest.get(p, "plans")).size());
		Map<String, List<?>> approaches = PerfReachTest.entries(PerfReachTest.get(p, "rawApproaches"));
		c.put("approachSettlements", (long) approaches.size());
		c.put("approachesDrawn", approaches.values().stream().mapToLong(List::size).sum());
		c.put("computeServed", (long) PerfReachTest.entries(PerfReachTest.get(p, "servedLinks")).size());
		c.put("linkStates", (long) PerfReachTest.entries(PerfReachTest.get(p, "linkStates")).size());
		c.put("fullCuts", (long) PerfReachTest.entries(PerfReachTest.get(p, "fullCuts")).size());
		Map<String, Object> sources = PerfReachTest.entries(PerfReachTest.get(p, "sources"));
		c.put("skeletons", (long) sources.size());
		long junctions = 0;
		for (Object s : sources.values()) {
			try { if (PerfReachTest.<Object>get(s, "junctions") != null) junctions++; } catch (NoSuchFieldException e) { /* a source type without the memo */ }
		}
		c.put("junctionFields", junctions);
		Map<String, Long> byPart = new TreeMap<>();
		fills.forEach((k, v) -> byPart.put(k, v.sum()));
		return new Step(name, c, byPart, perSettlement);
	}

	static String markdown(Result r) {
		StringBuilder md = new StringBuilder("# Cascade counts (CascadeCount, CASCADE step 1)\n\nTile ").append(r.chunkTile())
			.append(" (chunk 148,-360), then `ground` of its 8 neighbours. Fresh planner, no eviction, one thread: deterministic.\n\n| count | after plan | after neighbours | neighbours alone |\n|---|---|---|---|\n");
		Step a = r.step("1 plan"), b = r.step("2 neighbours ground");
		for (var e : b.counts().entrySet()) {
			long x = a.counts().get(e.getKey());
			md.append(String.format(Locale.ROOT, "| %s | %d | %d | %d |%n", e.getKey(), x, e.getValue(), e.getValue() - x));
		}
		md.append("\n## Chunk fills by part\n\n| part | after plan | after neighbours | neighbours alone |\n|---|---|---|---|\n");
		java.util.TreeSet<String> parts = new java.util.TreeSet<>(b.fillsByPart().keySet());
		for (String k : parts) {
			long x = a.fillsByPart().getOrDefault(k, 0L), y = b.fillsByPart().get(k);
			md.append(String.format(Locale.ROOT, "| %s | %d | %d | %d |%n", k, x, y, y - x));
		}
		md.append("\n## Raw plans per settlement\n\n| settlement | after plan | after neighbours |\n|---|---|---|\n");
		for (var e : b.rawPlansBySettlement().entrySet()) md.append(String.format(Locale.ROOT, "| %s | %d | %d |%n", e.getKey(), a.rawPlansBySettlement().getOrDefault(e.getKey(), 0L), e.getValue()));
		return md.toString();
	}
}
