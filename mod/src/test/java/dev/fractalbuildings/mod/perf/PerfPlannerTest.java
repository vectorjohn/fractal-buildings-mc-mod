package dev.fractalbuildings.mod.perf;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.mod.TerrainDumpTool;
import dev.fractalbuildings.mod.probe.LiveProbes;
import dev.fractalbuildings.mod.worldgen.Workers;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * B8, the planner half of the performance budget (6.7): live vanilla terrain (no server), a cold planner, numbers for the district plan, the
 * region layer, multi-player cache pressure and the vetoes' footprint query. Not part of the normal run:
 * {@code scripts/perf.sh} (or {@code -Dfb.perf=1 :mod:test --tests '*PerfPlannerTest*'}), under the heavy lock. Writes {@code mod/build/perf/planner.json}.
 */
class PerfPlannerTest {
	static final long SEED = 987654321L;
	/** gate-metropolis-temperate (viewer/city-presets): the centre of a metropolis on this seed's vanilla terrain. */
	static final int MX = 2432, MZ = -5696;

	/** Counts the exact-probe calls (the planner's dearest input: every call is a vanilla noise column). */
	static final class Counting implements TerrainProbe {
		final TerrainProbe in;
		final AtomicLong calls = new AtomicLong();

		Counting(TerrainProbe in) { this.in = in; }

		@Override public int floorY(int x, int z) { calls.incrementAndGet(); return in.floorY(x, z); }
		@Override public int waterDepth(int x, int z) { calls.incrementAndGet(); return in.waterDepth(x, z); }
		@Override public int surfaceY(int x, int z) { calls.incrementAndGet(); return in.surfaceY(x, z); }
	}

	private static double ms(long t0) { return (System.nanoTime() - t0) / 1e6; }

	private static WorldPlanner planner(NoiseBasedChunkGenerator gen, RandomState rs, ThreadPoolExecutor pool, Counting[] counter, WorldPlanner.Caches caps) {
		LiveProbes lp = new LiveProbes(gen, rs);
		Counting c = new Counting(lp.exact());
		if (counter != null) counter[0] = c;
		return new WorldPlanner(SEED, WorldConfig.defaults(), new WorldProbes(lp.region(), lp.coarse(32), c, lp.biome()), pool, caps);
	}

	private static long usedMb() {
		for (int i = 0; i < 3; i++) System.gc();
		Runtime r = Runtime.getRuntime();
		return (r.totalMemory() - r.freeMemory()) >> 20;
	}

	@Test
	@EnabledIfSystemProperty(named = "fb.perf", matches = ".+")
	void planner() throws Exception {
		NoiseBasedChunkGenerator gen = TerrainDumpTool.generator();
		RandomState rs = TerrainDumpTool.randomState(SEED);
		PerfOut out = new PerfOut();
		if (System.getenv("FB_PERF_FAIL") != null) throw new IllegalStateException("FB_PERF_FAIL set: deliberate failure to show perf.sh exits non-zero");
		out.put("planner.load.before", PerfOut.load());
		out.put("planner.cpus", Runtime.getRuntime().availableProcessors());

		// --- Q2 and Q1: the metropolis, cold, on the calling thread (what the lane pays with no lead) -------------------------------------
		Counting[] cnt = new Counting[1];
		WorldPlanner cold = planner(gen, rs, null, cnt, WorldPlanner.Caches.DEFAULT);
		Optional<SettlementInfo> metro = cold.nearest(MX, MZ, Tier.METROPOLIS, 1);
		if (metro.isEmpty()) throw new IllegalStateException("no metropolis near " + MX + "," + MZ + " (the config or generator changed the gate site)");
		int sx = metro.get().site().centre().x(), sz = metro.get().site().centre().z();
		out.put("planner.metropolis.site", metro.get().site().id() + " at " + sx + "," + sz + " radius " + metro.get().site().radius());
		// a fresh planner for the timed sequence: nearest() above already resolved the region cells around the site
		cold = planner(gen, rs, null, cnt, WorldPlanner.Caches.DEFAULT);
		long t0 = System.nanoTime();
		cold.warm(sx, sz, 2 * cold.config().cellSize());
		out.put("region.cold.warm.ms", ms(t0));
		DistrictGrid.Tile tile = cold.grid().tileAt(sx, sz);
		long probes0 = cnt[0].calls.get();
		t0 = System.nanoTime();
		boolean planned = cold.tile(tile.ix(), tile.iz()).isPresent();
		out.put("district.cold.tile.ms", ms(t0));
		t0 = System.nanoTime();
		int n = cold.buildings(tile.ix(), tile.iz()).size();
		cold.ground(tile.ix(), tile.iz());
		double distMs = ms(t0);
		out.put("district.cold.plan.ms", distMs);
		out.put("district.cold.buildings", n);
		out.put("district.cold.planned", planned ? 1 : 0);
		out.put("district.cold.probes", cnt[0].calls.get() - probes0);
		out.put("district.cold.firstTileProbes.incl.region", cnt[0].calls.get());
		// a second cold plan: a neighbour with the skeleton warm
		int nix = tile.ix() + 1, niz = tile.iz();
		t0 = System.nanoTime();
		if (cold.tile(nix, niz).isPresent()) cold.buildings(nix, niz);
		out.put("district.cold.neighbour.ms", ms(t0));
		// the median of several cold district plans (fresh tile each, skeleton warm): the budget is a median
		List<Double> plans = new ArrayList<>();
		for (int d = -3; d <= 3; d++) {
			int ix = tile.ix() + d, iz = tile.iz() + 1;
			t0 = System.nanoTime();
			if (cold.tile(ix, iz).isPresent()) { cold.buildings(ix, iz); cold.ground(ix, iz); plans.add(ms(t0)); }
		}
		plans.sort(Double::compare);
		out.put("district.cold.median.ms", plans.isEmpty() ? 0 : plans.get(plans.size() / 2));
		out.put("district.cold.p95.ms", plans.isEmpty() ? 0 : plans.get(Math.min(plans.size() - 1, (int) Math.ceil(plans.size() * 0.95) - 1)));
		out.put("district.cold.samples", plans.size());

		// --- the same on workers, then what the lane pays ------------------------------------------------------------------------------------
		ThreadPoolExecutor pool = Workers.create(Workers.defaultSize());
		try {
			WorldPlanner w = planner(gen, rs, pool, null, WorldPlanner.Caches.DEFAULT);
			t0 = System.nanoTime();
			pool.execute(() -> w.warm(sx, sz, 2 * w.config().cellSize()));
			Workers.awaitIdle(pool, 600_000);
			out.put("region.warmed.worker.ms", ms(t0));
			t0 = System.nanoTime();
			w.prefetch(tile.ix(), tile.iz());
			Workers.awaitIdle(pool, 600_000);
			out.put("district.warmed.prefetch.ms", ms(t0));
			t0 = System.nanoTime();
			w.tile(tile.ix(), tile.iz());
			w.buildings(tile.ix(), tile.iz());
			w.ground(tile.ix(), tile.iz());
			out.put("district.warmed.lane.ms", ms(t0));
		} finally {
			Workers.shutdown(pool);
		}

		// --- Q4: players in different settlements, with the default (heap-scaled) caches ------------------------------------------------------
		for (int players : new int[] {3, 8}) multi(out, gen, rs, players, 8);

		// --- Q3 (planner part): the veto's footprint query, if B7 is merged ---------------------------------------------------------------
		try {
			var m = WorldPlanner.class.getMethod("footprintNear", int.class, int.class, int.class);
			WorldPlanner v = planner(gen, rs, null, null, WorldPlanner.Caches.DEFAULT);
			t0 = System.nanoTime();
			m.invoke(v, 20000, 20000, 128);
			out.put("veto.footprint.first.ms", ms(t0));
			t0 = System.nanoTime();
			int calls = 0;
			for (int x = -6000; x <= 6000; x += 64, calls++) m.invoke(v, x, 3000, 128);
			out.put("veto.footprint.walk.mean.ms", ms(t0) / calls);
			out.put("veto.footprint.walk.total.ms", ms(t0));
		} catch (NoSuchMethodException e) {
			out.put("veto.footprint.first.ms", "absent: B7 not merged");
		}
		out.put("planner.load.after", PerfOut.load());
		out.write(Path.of("build/perf/planner.json"));
	}

	/** {@code players} players at different settlements, each loading a (2r+1)^2 chunk block round-robin: every chunk asks for its tile's buildings and ground. */
	private static void multi(PerfOut out, NoiseBasedChunkGenerator gen, RandomState rs, int players, int radius) {
		Counting[] cnt = new Counting[1];
		long heap0 = usedMb();
		WorldPlanner p = planner(gen, rs, null, cnt, WorldPlanner.Caches.forHeap(Runtime.getRuntime().maxMemory()));
		AtomicLong done = new AtomicLong(), inflight = new AtomicLong(), inline = new AtomicLong();
		p.observeBuildings(l -> { switch (l) { case DONE -> done.incrementAndGet(); case INFLIGHT -> inflight.incrementAndGet(); case INLINE -> inline.incrementAndGet(); } });
		List<SettlementInfo> sites = new ArrayList<>();
		for (int i = 0; sites.size() < players && i < 200; i++) {
			int x = (int) (Math.cos(i * 2.4) * (3000 + i * 700)), z = (int) (Math.sin(i * 2.4) * (3000 + i * 700));
			Optional<SettlementInfo> s = p.nearest(x, z, Tier.CITY, 3);
			if (s.isPresent() && sites.stream().noneMatch(o -> o.site().id().equals(s.get().site().id()))) sites.add(s.get());
		}
		if (sites.size() < players) throw new IllegalStateException("only " + sites.size() + " city+ settlements found");
		List<int[]> perPlayer = new ArrayList<>();
		java.util.TreeSet<Long> tiles = new java.util.TreeSet<>();
		int chunks = 0, maxChunks = (2 * radius + 1) * (2 * radius + 1);
		for (int c = 0; c < maxChunks; c++) {
			for (SettlementInfo s : sites) {
				int cx = (s.site().centre().x() >> 4) + (c % (2 * radius + 1)) - radius, cz = (s.site().centre().z() >> 4) + (c / (2 * radius + 1)) - radius;
				DistrictGrid.Tile t = p.grid().tileAt(cx * 16 + 8, cz * 16 + 8);
				if (p.tile(t.ix(), t.iz()).isEmpty()) continue;
				tiles.add(((long) t.ix() << 32) | (t.iz() & 0xffffffffL));
				p.buildings(t.ix(), t.iz());
				p.ground(t.ix(), t.iz());
				chunks++;
			}
		}
		long lookups = done.get() + inflight.get() + inline.get();
		String k = "multi" + players + ".";
		out.put(k + "chunks", chunks);
		out.put(k + "tiles", tiles.size());
		out.put(k + "lookups", lookups);
		out.put(k + "inline", inline.get());
		// every tile computes once; anything beyond is a recompute caused by eviction
		out.put(k + "recomputes", Math.max(0, inline.get() - tiles.size()));
		out.put(k + "heapMb", usedMb() - heap0);
	}
}
