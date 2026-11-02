package dev.fractalbuildings.mod.perf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.cache.Fanout;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.ExitTrim;
import dev.fractalbuildings.core.settlement.QuayTuning;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.TerrainDumpTool;
import dev.fractalbuildings.mod.probe.ChunkFill;
import dev.fractalbuildings.mod.probe.LiveProbes;
import dev.fractalbuildings.mod.worldgen.Workers;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * CASCADE 2fix: the cold district rows of {@link PerfPlannerTest} as counts, stage by stage (region warm, the quay tile, the centre tile, its east
 * neighbour, then the 7 sample tiles of {@code district.cold.p95.ms}). Per stage: chunk fills (by planner part, {@link PerfReachTest#purpose}),
 * quay walk-ahead rounds ({@link QuayTuning#walkPrefetches}: each a parallel round a chain walk waits for), new raw plans, and the whole-road exit
 * cuts and bounded walks computed. HEAVY12's p95 FAIL was read off this (docs/log/2026-11-01-p6-cascade-2fix.md).
 * <p>
 * By default one thread, no reads pool: every count is deterministic and needs no quiet host, and two guards hold the window (the neighbour plus
 * the 7 samples) to 110 % of its recorded chunk fills and walk rounds, so work added to the path the p95 row times fails here without a timing.
 * {@code -Dfb.perf.coldrow.pool=N} runs the reads on N threads as the game does (fills then include prefetch overreach and depend on timing:
 * printed, not checked). Run:
 * {@code scripts/dev ./gradlew :mod:test -Dfb.perf.coldrow=1 --tests '*ColdRowCountTest*' -i} (about a minute; {@code -Dfb.exitcut.reference=true}
 * gives the pre-CASCADE-2 exit cut). Never in the normal run.
 */
class ColdRowCountTest {
	/** Serial chunk fills of the neighbour plus the 7 cold samples after QUAYFAST 4 (was 4,145 at 21fc8570; 4,281 with -Dfb.exitcut.reference=true); prefetch only hints, so walk boxes do not change it. */
	static final long WINDOW_FILLS = 1066;
	/** Sample 16,-35 (the slowest of the old window) after QUAYFAST: 144 serial fills; the guard allows 400 (the plan's step-3 probe bound was 166). */
	static final long SAMPLE_16_35_MAX = 400;
	/** Serial quay walk-ahead rounds of the same window with 4 walk boxes (after QUAYFAST 4; was 648, one box 4,238 at CASCADE 2fix). */
	static final long WINDOW_ROUNDS = 5;

	private final Map<String, Long> parts = new ConcurrentHashMap<>();
	private final TreeSet<Object> seenRaw = new TreeSet<>(), seenCuts = new TreeSet<>(), seenWalk = new TreeSet<>();
	private long fills0, rounds0, s1635 = -1;

	@Test
	@EnabledIfSystemProperty(named = "fb.perf.coldrow", matches = ".+")
	void counts() throws Exception {
		NoiseBasedChunkGenerator gen = TerrainDumpTool.generator();
		RandomState rs = TerrainDumpTool.randomState(PerfPlannerTest.SEED);
		if (Boolean.getBoolean("fb.perf.coldrow.nomemo")) dev.fractalbuildings.core.settlement.CoarseTerrain.memoise = false; // QUAYFAST 4b: the before count
		int threads = Integer.getInteger("fb.perf.coldrow.pool", 0);
		ThreadPoolExecutor pool = threads > 1 ? Workers.create(threads, Workers.READS_PREFIX) : null;
		try {
			ChunkFill fill = (g, r, cx, cz) -> {
				parts.merge(PerfReachTest.purpose(), 1L, Long::sum);
				return ChunkFill.DEFAULT.fill(g, r, cx, cz);
			};
			LiveProbes lp = pool == null ? new LiveProbes(gen, rs, fill, LiveProbes.DEFAULT_CHUNKS)
				: new LiveProbes(gen, rs, fill, LiveProbes.DEFAULT_CHUNKS, Fanout.over(() -> pool, threads));
			WorldPlanner cold = new WorldPlanner(PerfPlannerTest.SEED, WorldConfig.defaults(), lp.world(lp.exact()), null, WorldPlanner.Caches.DEFAULT);
			QuayTuning.walkPrefetches.set(0);
			if (System.getProperty("fb.perf.coldrow.walkAhead") != null) QuayTuning.walkAhead = Integer.getInteger("fb.perf.coldrow.walkAhead"); // knob for the pooled runs: printed, not checked
			int sx = PerfPlannerTest.MX, sz = PerfPlannerTest.MZ; // the centre of s2_m6, as PerfPlannerTest's nearest() finds it
			cold.warm(sx, sz, 2 * cold.config().cellSize());
			stage("warm", cold, lp);
			DistrictGrid.Tile bt = cold.grid().tile(15, -37); // region.cold.quays.tile of the perf run
			cold.warmQuaysAt((bt.minX() + bt.maxX()) / 2, (bt.minZ() + bt.maxZ()) / 2);
			stage("quay tile 15,-37", cold, lp);
			DistrictGrid.Tile tile = cold.grid().tileAt(sx, sz);
			cold.tile(tile.ix(), tile.iz());
			cold.buildings(tile.ix(), tile.iz());
			cold.ground(tile.ix(), tile.iz());
			stage("centre " + tile.ix() + "," + tile.iz(), cold, lp);
			long[] window = new long[2];
			if (cold.tile(tile.ix() + 1, tile.iz()).isPresent()) cold.buildings(tile.ix() + 1, tile.iz());
			add(window, stage("neighbour " + (tile.ix() + 1) + "," + tile.iz(), cold, lp));
			for (int d = -3; d <= 3; d++) {
				int ix = tile.ix() + d, iz = tile.iz() + 1;
				if (cold.tile(ix, iz).isPresent()) { cold.buildings(ix, iz); cold.ground(ix, iz); }
				long[] st = stage("sample " + ix + "," + iz, cold, lp);
				add(window, st);
				if (ix == 16 && iz == -35) s1635 = st[0];
			}
			System.out.println("[COLDROW] neighbour + samples (" + (pool == null ? "serial" : threads + " read threads") + "): fills " + window[0] + " (recorded " + WINDOW_FILLS + "), walk rounds " + window[1] + " (recorded " + WINDOW_ROUNDS + ")");
			if (pool != null) return;
			assertTrue(window[0] <= WINDOW_FILLS * 11 / 10, "the cold district window fills " + window[0] + " chunks, over 110 % of the " + WINDOW_FILLS + " recorded: work was added to the path the p95 row times");
			assertTrue(s1635 >= 0 && s1635 <= SAMPLE_16_35_MAX, "sample 16,-35 fills " + s1635 + " chunks, over " + SAMPLE_16_35_MAX + ": the quay run walk got expensive again (QUAYFAST)");
			assertTrue(window[1] <= Math.max(WINDOW_ROUNDS * 11 / 10, WINDOW_ROUNDS + 3), "the cold district window's quay walks wait for " + window[1] + " prefetch rounds, over 110 % of the " + WINDOW_ROUNDS + " recorded (QuayTrace.WALK_BOXES)");
		} finally {
			QuayTuning.walkAhead = 32;
			if (pool != null) Workers.shutdown(pool);
		}
	}

	private static void add(long[] sum, long[] v) { sum[0] += v[0]; sum[1] += v[1]; }

	/** Prints one stage's counts and returns its chunk fills and walk rounds. */
	private long[] stage(String name, WorldPlanner p, LiveProbes lp) throws Exception {
		Map<Object, Object> raw = PerfReachTest.entries(PerfReachTest.get(p, "rawPlans"));
		Map<Object, Object> cuts = PerfReachTest.entries(PerfReachTest.get(p, "fullCuts"));
		Map<Object, Object> walk = PerfReachTest.entries(PerfReachTest.get(p, "walkBeyond"));
		long fills = lp.chunkFills() - fills0, rounds = QuayTuning.walkPrefetches.get() - rounds0;
		long probeReads = dev.fractalbuildings.core.settlement.CoarseTerrain.PROBE_READS.getAndSet(0);
		if (name.startsWith("sample") && dev.fractalbuildings.core.settlement.CoarseTerrain.memoise) assertTrue(probeReads <= 20_000, name + ": " + probeReads + " coarse water/peak probe reads (61 with the lattice memo, over a million without: QUAYFAST 4b, the quay screen asks for the same lattice columns over and over)");
		StringBuilder sb = new StringBuilder("[COLDROW] " + name + ": fills " + fills + ", walk rounds " + rounds + ", coarse water/peak probe reads " + probeReads + ", new raw plans");
		for (Object k : raw.keySet()) if (seenRaw.add(k)) { long l = (Long) k; sb.append(' ').append((int) (l >> 32)).append(',').append((int) l); }
		for (Object k : cuts.keySet()) if (seenCuts.add(k)) sb.append(", fullCut ").append(k).append(" = ").append(((java.util.Optional<?>) cuts.get(k)).map(c -> "index " + ((ExitTrim.Cut) c).index()).orElse("none"));
		for (Object k : walk.keySet()) if (seenWalk.add(k)) sb.append(", walk ").append(k).append(" = ").append(walk.get(k));
		for (var e : new TreeMap<>(parts).entrySet()) sb.append("\n    ").append(e.getValue()).append(" fills: ").append(e.getKey());
		parts.clear();
		System.out.println(sb);
		fills0 = lp.chunkFills();
		rounds0 = QuayTuning.walkPrefetches.get();
		return new long[] {fills, rounds};
	}
}
