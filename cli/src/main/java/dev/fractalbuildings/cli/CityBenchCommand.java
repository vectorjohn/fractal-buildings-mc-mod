package dev.fractalbuildings.cli;

import dev.fractalbuildings.cli.Main.UsageException;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.settlement.Briefs;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CellGeometry;
import dev.fractalbuildings.core.settlement.CellSkeleton;
import dev.fractalbuildings.core.settlement.CityPlanner;
import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.DistrictScenes;
import dev.fractalbuildings.core.settlement.DistrictSummary;
import dev.fractalbuildings.core.settlement.Districts;
import dev.fractalbuildings.core.settlement.FieldSiteView;
import dev.fractalbuildings.core.settlement.FixedSiteView;
import dev.fractalbuildings.core.settlement.RegionView;
import dev.fractalbuildings.core.settlement.SettlementJson;
import dev.fractalbuildings.core.settlement.SettlementPlan;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import dev.fractalbuildings.core.settlement.Skeletons;
import dev.fractalbuildings.core.style.StyleField;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code city-bench}: single-threaded scale measurements of one settlement (phase 5 step SC; not part of any test run). Per repetition
 * it times the region decision, every cell skeleton, the assembly, and per district {@code Districts.plan} and {@code Briefs.fill}
 * with exact/coarse/biome probe counts, then a whole {@link CityPlanner#plan} and a sample of scenes (probes counted apart), and
 * the heap retained by the plan. Repetitions before {@code --warm} are discarded. Peak RSS is process-wide, so run one
 * configuration per JVM. Output is a key=value block plus one {@code SUMMARY} line per configuration.
 */
final class CityBenchCommand {
	private CityBenchCommand() {}

	private static final class Counter {
		final AtomicLong exact = new AtomicLong(), biome = new AtomicLong(), coarse = new AtomicLong();
	}

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("seed", "terrain", "cell", "site", "cell-size", "reps", "warm", "scenes", "cache", "region-cache", "label"), Set.of());
		long seed = o.longValue("seed");
		int reps = intOpt(o, "reps", 3), warm = intOpt(o, "warm", 1), nScenes = intOpt(o, "scenes", 4);
		int cache = intOpt(o, "cache", SkeletonSource.Direct.DEFAULT_CACHE), regionCache = intOpt(o, "region-cache", FieldRegionView.DEFAULT_CACHE);
		int cellSize = intOpt(o, "cell-size", 1024);
		if (o.has("cell") == o.has("site")) throw new UsageException("give exactly one of --cell CX,CZ and --site X,Z,TIER,RADIUS");
		if (reps <= warm) throw new UsageException("--reps must exceed --warm");
		ProbeSpecs.Probes p = Scenes.probes(o.get("terrain", "flat:64"));
		RegionField field = new RegionField(seed, cellSize);
		String label = o.get("label", o.has("cell") ? "cell " + o.get("cell", null) : "site " + o.get("site", null));

		List<Double> regionMs = new ArrayList<>(), skelMs = new ArrayList<>(), assembleMs = new ArrayList<>(), planMs = new ArrayList<>(),
				briefMs = new ArrayList<>(), distMs = new ArrayList<>(), totalMs = new ArrayList<>(), sceneMs = new ArrayList<>();
		List<Double> exactPer = new ArrayList<>(), coarsePer = new ArrayList<>(), biomePer = new ArrayList<>(), lotsPer = new ArrayList<>(),
				sceneExact = new ArrayList<>(), sceneBiome = new ArrayList<>();
		long allocPlan = 0, retained = 0, jsonDistricts = 0, jsonScenes = 0, jsonSettlement = 0;
		int nDistricts = 0, nCells = 0, nLots = 0;
		SettlementSite site = null;

		for (int rep = 0; rep < reps; rep++) {
			boolean keep = rep >= warm;
			Counter cnt = new Counter(), sceneCnt = new Counter();
			TerrainProbe exact = counting(p.terrain(), cnt.exact);
			BiomeProbe biome = (x, z) -> { cnt.biome.incrementAndGet(); return p.biome().biome(x, z); };
			CoarseProbe coarse = counting(CoarseProbe.of(p.terrain(), p.biome(), 32), cnt.coarse);

			// region decision, cold caches
			long t = System.nanoTime();
			FieldRegionView fv = CityCommand.fieldView(field, p, regionCache);
			RegionView view;
			if (o.has("cell")) {
				int[] a = ints(o.get("cell", null));
				view = new FieldSiteView(fv, field);
				Optional<SettlementSite> s = view.siteOf(new Cell(a[0], a[1]));
				if (s.isEmpty()) throw new UsageException("cell " + o.get("cell", null) + " belongs to no settlement");
				site = s.get();
			} else {
				site = CityCommand.fromSite(field, o.get("site", null));
				view = new FixedSiteView(site, field);
				try { new FieldSiteView(fv, field).siteOf(CellGeometry.owner(field, site.centre().x(), site.centre().z())); } catch (RuntimeException e) { /* window cannot answer; time what it did */ }
			}
			double region = ms(t);

			SkeletonSource.Direct src = new SkeletonSource.Direct(seed, view, site, coarse, dev.fractalbuildings.core.settlement.TierPrograms.of(site.tier()), cache);
			List<CellSkeleton> sk = new ArrayList<>();
			List<Double> perCell = new ArrayList<>();
			for (Cell c : site.cells()) {
				t = System.nanoTime();
				sk.add(src.skeleton(c));
				perCell.add(ms(t));
			}
			t = System.nanoTime();
			SettlementPlan plan = Skeletons.assemble(seed, view, site, coarse, GeneratorV1.GEN_VERSION, sk);
			double asm = ms(t);

			StyleField styles = new StyleField(biome, seed);
			long exact0, coarse0, biome0;
			List<Double> dPlan = new ArrayList<>(), dBrief = new ArrayList<>(), dTot = new ArrayList<>(), dEx = new ArrayList<>(), dCo = new ArrayList<>(), dBi = new ArrayList<>(), dLots = new ArrayList<>();
			for (DistrictSummary d : plan.districts()) {
				exact0 = cnt.exact.get(); coarse0 = cnt.coarse.get(); biome0 = cnt.biome.get();
				t = System.nanoTime();
				Optional<Districts.Detail> det = Districts.plan(src, d.ix(), d.iz(), exact);
				double a1 = ms(t);
				if (det.isEmpty()) continue;
				t = System.nanoTime();
				DistrictPlan dp = Briefs.fill(det.get().plan(), src.quarters()::at, styles, exact);
				double a2 = ms(t);
				dPlan.add(a1); dBrief.add(a2); dTot.add(a1 + a2); dLots.add((double) dp.lots().size());
				dEx.add((double) (cnt.exact.get() - exact0)); dCo.add((double) (cnt.coarse.get() - coarse0)); dBi.add((double) (cnt.biome.get() - biome0));
			}

			// whole-city run for retained heap, total time and the scene sample (fresh source: cold skeleton cache)
			Counter c2 = new Counter();
			TerrainProbe ex2 = counting(p.terrain(), c2.exact);
			BiomeProbe bi2 = (x, z) -> { c2.biome.incrementAndGet(); return p.biome().biome(x, z); };
			CoarseProbe co2 = counting(CoarseProbe.of(p.terrain(), p.biome(), 32), c2.coarse);
			long before = used();
			long alloc0 = allocated();
			t = System.nanoTime();
			CityPlanner.City city = CityPlanner.plan(new SkeletonSource.Direct(seed, view, site, co2, dev.fractalbuildings.core.settlement.TierPrograms.of(site.tier()), cache), co2, ex2, bi2, GeneratorV1.GEN_VERSION);
			long alloc1 = allocated();
			double total = ms(t);
			long after = used();
			java.lang.ref.Reference.reachabilityFence(city);

			List<Double> sc = new ArrayList<>(), scEx = new ArrayList<>(), scBi = new ArrayList<>();
			long scJson = 0;
			TerrainProbe ex3 = counting(p.terrain(), sceneCnt.exact);
			BiomeProbe bi3 = (x, z) -> { sceneCnt.biome.incrementAndGet(); return p.biome().biome(x, z); };
			for (String id : nScenes > 0 ? CityPlanner.autoScenes(city, nScenes) : List.<String>of()) {
				long e0 = sceneCnt.exact.get(), b0 = sceneCnt.biome.get();
				t = System.nanoTime();
				DistrictScenes.Built b = CityPlanner.scene(city, id, ex3, bi3, true);
				sc.add(ms(t));
				scEx.add((double) (sceneCnt.exact.get() - e0)); scBi.add((double) (sceneCnt.biome.get() - b0));
				if (keep) scJson += IrJson.write(b.scene()).getBytes(StandardCharsets.UTF_8).length;
			}
			if (keep) {
				regionMs.add(region); skelMs.addAll(perCell); assembleMs.add(asm);
				planMs.addAll(dPlan); briefMs.addAll(dBrief); distMs.addAll(dTot); totalMs.add(total); sceneMs.addAll(sc);
				exactPer.addAll(dEx); coarsePer.addAll(dCo); biomePer.addAll(dBi); lotsPer.addAll(dLots); sceneExact.addAll(scEx); sceneBiome.addAll(scBi);
				retained = after - before; allocPlan = alloc1 - alloc0;
				nDistricts = city.districts().size(); nCells = site.cells().size();
				nLots = 0; jsonDistricts = 0;
				for (DistrictPlan dp : city.districts().values()) { nLots += dp.lots().size(); jsonDistricts += DistrictJson.write(dp).getBytes(StandardCharsets.UTF_8).length; }
				jsonSettlement = SettlementJson.write(city.plan()).getBytes(StandardCharsets.UTF_8).length;
				jsonScenes = sc.isEmpty() ? 0 : scJson / sc.size();
			}
			if (city.districts().size() < 0) System.out.println(); // keep the city reachable until here
		}

		String load = loadavg();
		Locale l = Locale.ROOT;
		System.out.printf(l, "city-bench %s: %s %s r%d, %d cells, %d districts planned (%d lots), %d timed reps after %d warm-up, cache skeleton=%d region=%d%n",
				label, site.id(), site.tier().json, site.radius(), nCells, nDistricts, nLots, reps - warm, warm, cache, regionCache);
		row("region decision (cold, one cell)", regionMs);
		row("skeleton per cell", skelMs);
		row("assemble", assembleMs);
		row("Districts.plan per district", planMs);
		row("Briefs.fill per district", briefMs);
		row("district plan+brief", distMs);
		row("CityPlanner.plan total", totalMs);
		row("scene per district (sampled)", sceneMs);
		row("lots per district", lotsPer);
		row("exact probes per district plan", exactPer);
		row("coarse probes per district plan", coarsePer);
		row("biome probes per district plan", biomePer);
		row("exact probes per scene", sceneExact);
		row("biome probes per scene", sceneBiome);
		System.out.printf(l, "allocated by CityPlanner.plan: %.1f MiB (%.0f KiB per district); retained heap of City (noisy, GC dependent): %.1f MiB; json: settlement %d KiB, districts %d KiB total, scene %d KiB avg%n",
				allocPlan / 1048576.0, nDistricts == 0 ? 0 : allocPlan / 1024.0 / nDistricts, retained / 1048576.0, jsonSettlement / 1024, jsonDistricts / 1024, jsonScenes / 1024);
		System.out.printf(l, "peak RSS %s MiB, heap max %d MiB, load %s%n", status("VmHWM"), Runtime.getRuntime().maxMemory() >> 20, load);
		System.out.printf(l, "SUMMARY\t%s\t%s\t%d\t%d\t%d\t%.2f\t%.2f\t%.2f\t%.1f\t%.0f\t%.1f\t%.1f\t%.1f\t%s\t%s%n", label, site.tier().json, site.radius(), nCells, nDistricts,
				med(distMs), p95(distMs), med(skelMs), med(totalMs), med(exactPer), med(sceneMs), allocPlan / 1048576.0, med(regionMs), status("VmHWM"), load);
		return 0;
	}

	private static void row(String name, List<Double> v) {
		if (v.isEmpty()) { System.out.printf(Locale.ROOT, "%-34s n=0%n", name); return; }
		double[] a = v.stream().mapToDouble(Double::doubleValue).sorted().toArray();
		double sum = Arrays.stream(a).sum();
		System.out.printf(Locale.ROOT, "%-34s n=%-5d median %9.2f  mean %9.2f  p95 %9.2f  max %9.2f%n", name, a.length, med(v), sum / a.length, p95(v), a[a.length - 1]);
	}

	private static double med(List<Double> v) {
		if (v.isEmpty()) return Double.NaN;
		double[] a = v.stream().mapToDouble(Double::doubleValue).sorted().toArray();
		return a.length % 2 == 1 ? a[a.length / 2] : (a[a.length / 2 - 1] + a[a.length / 2]) / 2;
	}

	private static double p95(List<Double> v) {
		if (v.isEmpty()) return Double.NaN;
		double[] a = v.stream().mapToDouble(Double::doubleValue).sorted().toArray();
		return a[(int) Math.min(a.length - 1, Math.ceil(a.length * 0.95) - 1)];
	}

	private static double ms(long t0) { return (System.nanoTime() - t0) / 1e6; }

	private static long used() {
		Runtime r = Runtime.getRuntime();
		for (int i = 0; i < 3; i++) { System.gc(); try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
		return r.totalMemory() - r.freeMemory();
	}

	private static long allocated() {
		return ((com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().threadId());
	}

	private static String status(String key) {
		try {
			for (String line : Files.readAllLines(Path.of("/proc/self/status"))) if (line.startsWith(key + ":")) return String.valueOf(Long.parseLong(line.replaceAll("[^0-9]", "")) / 1024);
		} catch (IOException | RuntimeException e) { /* not Linux */ }
		return "?";
	}

	private static String loadavg() {
		try { return Files.readString(Path.of("/proc/loadavg")).trim(); } catch (IOException e) { return "?"; }
	}

	private static TerrainProbe counting(TerrainProbe t, AtomicLong n) {
		return new TerrainProbe() {
			@Override public int floorY(int x, int z) { n.incrementAndGet(); return t.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { return t.waterDepth(x, z); }
		};
	}

	private static CoarseProbe counting(CoarseProbe c, AtomicLong n) {
		return new CoarseProbe() {
			@Override public int stride() { return c.stride(); }
			@Override public int floorY(int x, int z) { n.incrementAndGet(); return c.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { return c.waterDepth(x, z); }
			@Override public String biome(int x, int z) { return c.biome(x, z); }
		};
	}

	private static int intOpt(Opts o, String name, int def) {
		if (!o.has(name)) return def;
		try { return Integer.parseInt(o.get(name, null)); } catch (NumberFormatException e) { throw new UsageException("bad --" + name); }
	}

	private static int[] ints(String s) {
		String[] f = s.split(",", -1);
		if (f.length != 2) throw new UsageException("expected --cell CX,CZ");
		try { return new int[] {Integer.parseInt(f[0]), Integer.parseInt(f[1])}; } catch (NumberFormatException e) { throw new UsageException("expected --cell CX,CZ"); }
	}
}
