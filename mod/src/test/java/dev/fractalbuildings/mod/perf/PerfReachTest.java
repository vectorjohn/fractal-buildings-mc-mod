package dev.fractalbuildings.mod.perf;

import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionProbe;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.mod.TerrainDumpTool;
import dev.fractalbuildings.mod.probe.ChunkFill;
import dev.fractalbuildings.mod.probe.LiveProbes;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * PROFILE (2026-10-28, measurement only, never in the normal run): how far planning one cold metropolis reaches, and what each part costs
 * in vanilla noise. The metropolis of {@link PerfPlannerTest} (s2_m6 at 2432,-5696, seed 987654321), a fresh planner over the game's live
 * probes, three steps as the game takes them: {@code warm} (region cells, road graph, every found settlement's network and junction field),
 * {@code warmQuays}, then the centre tile's plan. Every probe read is counted by kind (region lattice, coarse lattice, road station, exact
 * district read) and by purpose (a stack walk: quays, junction field, intercity roads, region decisions, streets, tile plan), and every
 * newly computed noise unit (a whole chunk for exact reads, a single column for the others) is recorded with its position and purpose, so
 * {@code scripts/profile-overlap.sh} can join them with the chunks a server run generated. Afterwards the planner's caches are read by
 * reflection (a measurement, nothing depends on it): region cells decided, settlements found, skeletons built, intercity routes built.
 * Run: {@code scripts/dev ./gradlew :mod:test -Dfb.perf.reach=1 --tests '*PerfReachTest*'}; writes {@code mod/build/perf/reach.md}
 * and {@code mod/build/perf/reach-units.tsv}.
 */
class PerfReachTest {
	static final long SEED = PerfPlannerTest.SEED;

	/** Purpose of a read from its stack: the first frame from the top that names a known part. */
	static String purpose() {
		return StackWalker.getInstance().walk(s -> {
			// every frame of the planner core on the stack; the most specific part wins (quays > junction field > roads > region > streets > tile)
			boolean quays = false, junction = false, road = false, region = false, regionForRoads = false, regionAround = false, streets = false, tile = false;
			for (var it = s.iterator(); it.hasNext();) {
				var f = it.next();
				String c = f.getClassName(), m = f.getMethodName();
				if (!c.startsWith("dev.fractalbuildings.core.")) continue;
				if (c.endsWith(".Quays") || c.endsWith(".QuayTrace") || m.toLowerCase(Locale.ROOT).contains("quay")) quays = true;
				else if (c.endsWith(".JunctionField")) junction = true;
				else if (c.endsWith(".RegionPlanner") || c.endsWith(".FieldRegionView") || c.endsWith(".RegionField")) region = true;
				else if (c.contains(".road.")) { if (region) regionForRoads = true; else road = true; }
				else if (c.endsWith(".WorldPlanner") && (m.equals("warm") || m.equals("nearest"))) { if (region && !regionForRoads) regionAround = true; }
				else if (c.contains("Street") || c.contains("Skeleton") || c.contains("Network")) streets = true;
				else if (c.contains(".settlement.")) tile = true;
			}
			if (quays) return "quays";
			if (junction) return "junction field";
			if (region) return regionForRoads ? "region decisions (for the road graph)" : regionAround ? "region decisions (cells around the point)" : "region decisions (other)";
			if (road) return "intercity roads (routes, stations)";
			if (streets) return "streets and skeleton";
			if (tile) return "tile plan (other)";
			return "other";
		});
	}

	/** Counts per (step, kind, purpose); new noise units with their position. */
	static final class Rec {
		volatile String step = "setup";
		final Map<String, LongAdder> calls = new ConcurrentHashMap<>();
		final Map<String, LongAdder> units = new ConcurrentHashMap<>();
		/** unit key -> "step\tkind\tpurpose\tchunkX\tchunkZ" (first computation only). */
		final Map<String, String> first = new ConcurrentHashMap<>();

		void call(String kind) {
			calls.computeIfAbsent(step + "\t" + kind + "\t" + purpose(), k -> new LongAdder()).increment();
		}

		void unit(String kind, int x, int z, boolean chunk) {
			int cx = chunk ? x : x >> 4, cz = chunk ? z : z >> 4;
			String key = kind + ":" + x + "," + z;
			String p = purpose();
			units.computeIfAbsent(step + "\t" + kind + "\t" + p, k -> new LongAdder()).increment();
			first.putIfAbsent(key, step + "\t" + kind + "\t" + p + "\t" + cx + "\t" + cz);
		}
	}

	static final Rec REC = new Rec();
	/** Columns seen per lattice kind (a first sight is one noise column: the live memos are far larger than this test needs). */
	static final Set<Long> SPARSE_SEEN = ConcurrentHashMap.newKeySet(), ROAD_SEEN = ConcurrentHashMap.newKeySet();

	static long k(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

	static void sparse(String kind, int x, int z) {
		REC.call(kind);
		if (SPARSE_SEEN.add(k(x, z))) REC.unit("lattice column", x, z, false);
	}

	@Test
	@EnabledIfSystemProperty(named = "fb.perf.reach", matches = ".+")
	void reach() throws Exception {
		NoiseBasedChunkGenerator gen = TerrainDumpTool.generator();
		RandomState rs = TerrainDumpTool.randomState(SEED);
		ChunkFill fill = (g, r, cx, cz) -> {
			REC.unit("exact chunk", cx, cz, true);
			return ChunkFill.DEFAULT.fill(g, r, cx, cz);
		};
		LiveProbes lp = new LiveProbes(gen, rs, fill, 1 << 16); // no eviction: every fill is a first fill
		RegionProbe region0 = lp.region();
		RegionProbe region = new RegionProbe() {
			@Override public int floorY(int x, int z) { sparse("region", x, z); return region0.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { sparse("region", x, z); return region0.waterDepth(x, z); }
			@Override public String biome(int x, int z) { sparse("region", x, z); return region0.biome(x, z); }
		};
		CoarseProbe coarse0 = lp.coarse(32);
		CoarseProbe coarse = wrapCoarse(coarse0);
		TerrainProbe exact0 = lp.exact();
		TerrainProbe exact = new TerrainProbe() {
			@Override public int floorY(int x, int z) { REC.call("exact"); return exact0.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { REC.call("exact"); return exact0.waterDepth(x, z); }
			@Override public int surfaceY(int x, int z) { REC.call("exact"); return exact0.surfaceY(x, z); }
		};
		BiomeProbe biome0 = lp.biome();
		BiomeProbe biome = (x, z) -> { REC.call("biome (exact surface)"); return biome0.biome(x, z); };
		TerrainProbe road0 = lp.roads();
		TerrainProbe road = new TerrainProbe() {
			void r(int x, int z) { REC.call("road station"); if (ROAD_SEEN.add(k(x, z))) REC.unit("road column", x, z, false); }
			@Override public int floorY(int x, int z) { r(x, z); return road0.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { r(x, z); return road0.waterDepth(x, z); }
			@Override public int surfaceY(int x, int z) { r(x, z); return road0.surfaceY(x, z); }
		};
		WorldProbes probes = new WorldProbes(region, coarse, exact, biome, null, road);

		// the site, on a throwaway planner (as PerfPlannerTest: nearest() resolves cells, the timed planner starts cold)
		WorldPlanner find = new WorldPlanner(SEED, WorldConfig.defaults(), new LiveProbes(gen, rs).world(new LiveProbes(gen, rs).exact()), null, WorldPlanner.Caches.DEFAULT);
		Optional<SettlementInfo> metro = find.nearest(PerfPlannerTest.MX, PerfPlannerTest.MZ, Tier.METROPOLIS, 1);
		if (metro.isEmpty()) throw new IllegalStateException("no metropolis");
		var site = metro.get().site();
		int sx = site.centre().x(), sz = site.centre().z();
		WorldPlanner p = new WorldPlanner(SEED, WorldConfig.defaults(), probes, null, WorldPlanner.Caches.DEFAULT);
		int reach = 2 * p.config().cellSize();
		Map<String, Double> ms = new LinkedHashMap<>();
		long t0 = System.nanoTime();
		REC.step = "1 warm";
		p.warm(sx, sz, reach);
		ms.put("1 warm", (System.nanoTime() - t0) / 1e6);
		Map<String, Object> afterWarm = reachRows(p, sx, sz, site.id());
		t0 = System.nanoTime();
		REC.step = "2 warmQuays";
		p.warmQuays(sx, sz, reach);
		ms.put("2 warmQuays", (System.nanoTime() - t0) / 1e6);
		t0 = System.nanoTime();
		REC.step = "3 centre tile";
		DistrictGrid.Tile tile = p.grid().tileAt(sx, sz);
		p.tile(tile.ix(), tile.iz());
		p.buildings(tile.ix(), tile.iz());
		p.ground(tile.ix(), tile.iz());
		ms.put("3 centre tile", (System.nanoTime() - t0) / 1e6);

		StringBuilder md = new StringBuilder("# Reach of one cold metropolis plan (PerfReachTest)\n\n");
		md.append(String.format(Locale.ROOT, "Site %s (%s) at %d,%d radius %d; warm radius %d blocks (%d region cells of %d).%n%n", site.id(), site.tier(), sx, sz, site.radius(), reach, 2, p.config().cellSize()));
		md.append("| step | ms (single thread, includes stack walks) |\n|---|---|\n");
		ms.forEach((k, v) -> md.append(String.format(Locale.ROOT, "| %s | %.0f |%n", k, v)));
		md.append("\n## What warm resolved\n\n| row | value |\n|---|---|\n");
		afterWarm.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v).append(" |\n"));
		md.append("\n## Noise units computed (exact chunk = one 16x16 fill; lattice / road column = one column)\n\n| step | kind | purpose | units |\n|---|---|---|---|\n");
		new TreeMap<>(REC.units).forEach((k, v) -> md.append("| ").append(k.replace("\t", " | ")).append(" | ").append(v.sum()).append(" |\n"));
		md.append("\n## Probe calls (memo hits included)\n\n| step | kind | purpose | calls |\n|---|---|---|---|\n");
		new TreeMap<>(REC.calls).forEach((k, v) -> md.append("| ").append(k.replace("\t", " | ")).append(" | ").append(v.sum()).append(" |\n"));
		Path dir = Path.of(System.getProperty("fb.perf.out", "build/perf"));
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("reach.md"), md.toString());
		StringBuilder tsv = new StringBuilder("step\tkind\tpurpose\tchunkX\tchunkZ\n");
		for (String v : new TreeMap<>(REC.first).values()) tsv.append(v).append('\n');
		Files.writeString(dir.resolve("reach-units.tsv"), tsv.toString());
		System.out.println(md);
	}

	static CoarseProbe wrapCoarse(CoarseProbe in) {
		return new CoarseProbe() {
			@Override public int stride() { return in.stride(); }
			@Override public int floorY(int x, int z) { sparse("coarse", x, z); return in.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { sparse("coarse", x, z); return in.waterDepth(x, z); }
			@Override public String biome(int x, int z) { sparse("coarse", x, z); return in.biome(x, z); }
		};
	}

	@SuppressWarnings("unchecked")
	static <T> T get(Object o, String field) throws Exception {
		Class<?> c = o.getClass();
		while (c != null) {
			try {
				Field f = c.getDeclaredField(field);
				f.setAccessible(true);
				return (T) f.get(o);
			} catch (NoSuchFieldException e) {
				c = c.getSuperclass();
			}
		}
		throw new NoSuchFieldException(field);
	}

	/** The values of a BoundedCache (or FlightCache), by reflection. */
	@SuppressWarnings("unchecked")
	static <K, V> Map<K, V> entries(Object cache) throws Exception {
		Object inner = cache;
		try { inner = get(cache, "cache"); } catch (NoSuchFieldException ignored) { /* a BoundedCache */ }
		Map<K, Object> map = get(inner, "map");
		Map<K, V> out = new LinkedHashMap<>();
		synchronized (map) {
			for (var e : map.entrySet()) out.put(e.getKey(), (V) get(e.getValue(), "value"));
		}
		return out;
	}

	static Map<String, Object> reachRows(WorldPlanner p, int sx, int sz, String metroId) throws Exception {
		Map<String, Object> r = new LinkedHashMap<>();
		int s = p.config().cellSize();
		int mcx = Math.floorDiv(sx, s), mcz = Math.floorDiv(sz, s);
		Object view = get(p, "fieldView");
		Map<Object, Object> decisions = entries(get(view, "decisions"));
		int maxRing = 0;
		TreeMap<Integer, Integer> byRing = new TreeMap<>();
		int settled = 0;
		for (var e : decisions.entrySet()) {
			var key = (dev.fractalbuildings.core.region.CellKey) e.getKey();
			int ring = Math.max(Math.abs(key.cx() - mcx), Math.abs(key.cz() - mcz));
			byRing.merge(ring, 1, Integer::sum);
			maxRing = Math.max(maxRing, ring);
			if (((dev.fractalbuildings.core.region.CellDecision) e.getValue()).settled()) settled++;
		}
		r.put("region cells decided", decisions.size() + " (settled " + settled + "), farthest " + maxRing + " cells (" + maxRing * s + " blocks) from the metropolis cell");
		StringBuilder rings = new StringBuilder();
		byRing.forEach((k, v) -> rings.append(rings.length() > 0 ? ", " : "").append("ring ").append(k).append(": ").append(v));
		r.put("cells decided by ring (Chebyshev, cells)", rings.toString());
		Map<String, Object> sources = entries(get(p, "sources"));
		StringBuilder sk = new StringBuilder();
		for (Object src : sources.values()) {
			var st = ((SkeletonSource) src).site();
			double d = Math.hypot(st.centre().x() - sx, st.centre().z() - sz);
			sk.append(sk.length() > 0 ? "; " : "").append(String.format(Locale.ROOT, "%s %s r%d at %.0f blocks", st.id(), st.tier(), st.radius(), d));
		}
		r.put("settlements whose streets and junction field warm built (" + sources.size() + ")", sk.toString());
		RoadNet net = p.roads();
		if (net != null) {
			Map<Object, Object> settlements = entries(get(net, "settlements"));
			long known = settlements.values().stream().filter(v -> ((Optional<?>) v).isPresent()).count();
			r.put("road graph: cells asked for a settlement", settlements.size() + " (settlements found " + known + ")");
			Map<Object, Object> routes = entries(get(net, "routes"));
			Map<String, Set<String>> adj = new TreeMap<>();
			String metroOwner = null;
			long stations = 0, exactReads = 0, ok = 0;
			for (Object v : routes.values()) {
				RoadNet.Route rt = (RoadNet.Route) v;
				if (rt == null || rt.a == null || rt.b == null) continue;
				String a = rt.a.owner().toString(), b = rt.b.owner().toString();
				adj.computeIfAbsent(a, x -> new TreeSet<>()).add(b);
				adj.computeIfAbsent(b, x -> new TreeSet<>()).add(a);
				if (Math.hypot(rt.a.x() - sx, rt.a.z() - sz) < 64) metroOwner = a;
				if (Math.hypot(rt.b.x() - sx, rt.b.z() - sz) < 64) metroOwner = b;
				stations += rt.stations.length;
				exactReads += rt.exactReads;
				if (rt.ok) ok++;
			}
			r.put("road graph: routes built", routes.size() + " (ok " + ok + "), stations " + stations + ", exact station reads " + exactReads + ", endpoints " + adj.size() + " settlements");
			// graph distance from the metropolis over the built routes
			TreeMap<Integer, Integer> hops = new TreeMap<>();
			if (metroOwner != null) {
				Map<String, Integer> dist = new LinkedHashMap<>();
				ArrayDeque<String> q = new ArrayDeque<>(List.of(metroOwner));
				dist.put(metroOwner, 0);
				while (!q.isEmpty()) {
					String c = q.poll();
					for (String n : adj.getOrDefault(c, Set.of())) if (!dist.containsKey(n)) { dist.put(n, dist.get(c) + 1); q.add(n); }
				}
				for (String n : adj.keySet()) hops.merge(dist.getOrDefault(n, -1), 1, Integer::sum);
				int routesAtMetro = adj.getOrDefault(metroOwner, Set.of()).size();
				r.put("road graph: routes touching the metropolis", routesAtMetro);
			}
			StringBuilder h = new StringBuilder();
			hops.forEach((k, v) -> h.append(h.length() > 0 ? ", " : "").append(k < 0 ? "not connected to it" : k + " hops").append(": ").append(v));
			r.put("route endpoints by hops from the metropolis over built routes", metroOwner == null ? "metropolis not an endpoint" : h.toString());
		}
		return r;
	}
}
