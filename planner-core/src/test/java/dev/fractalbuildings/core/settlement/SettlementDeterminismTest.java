package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.json.JsonReader;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.style.StyleField;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Phase 5 determinism suite (step DT): the settlement, every district plan and the chosen district scenes are byte-identical
 * however they are computed: (a) in order, (b) cells and districts shuffled, each skeleton from a fresh source, (c) on 8 threads
 * over a source whose skeleton cache holds 2 cells and evicts constantly, (d) every district (a sample of them unless
 * {@code -Dfb.det.full=true}) alone in a new source. The cases are the viewer's city presets ({@code viewer/city-presets/*.json}),
 * so new presets are picked up without editing this class. {@code -Dfb.det.presets=a,b} picks presets by name (default: {@link #DEFAULT}; the heavy-tagged test runs all of them). Mutant sources at the bottom prove the checks can fail.
 */
class SettlementDeterminismTest {
	static final Path PRESETS = Files.isDirectory(Path.of("../viewer/city-presets")) ? Path.of("../viewer/city-presets") : Path.of("viewer/city-presets");
	static final Path ROOT = PRESETS.getParent().getParent();
	/** Presets the suite must keep seeing (guards against the preset directory moving). */
	static final List<String> DEFAULT = List.of("synth-city", "river-town", "gate-joined-city");

	record Preset(String name, long seed, String terrain, int x, int z, Tier tier, int radius, int cellSize, String scenes) {}

	/** Everything a preset plans from; {@code source()} gives a fresh, empty source each time. */
	record Setup(Preset preset, ProbeSpecs.Probes p, SettlementSite site, RegionField field, CoarseProbe coarse) {
		SkeletonSource.Direct direct() {
			return new SkeletonSource.Direct(preset.seed(), new FixedSiteView(site, field), site, coarse);
		}

		StyleField styles() { return new StyleField(p.biome(), preset.seed()); }
	}

	@SuppressWarnings("unchecked")
	static List<Preset> allPresets() throws IOException {
		List<Preset> out = new ArrayList<>();
		try (Stream<Path> files = Files.list(PRESETS)) {
			for (Path f : files.filter(x -> x.toString().endsWith(".json")).sorted().toList()) {
				Map<String, Object> o = (Map<String, Object>) JsonReader.parse(Files.readString(f));
				Map<String, Object> s = (Map<String, Object>) o.get("site");
				if (!s.containsKey("at")) continue; // a --cell preset needs the region planner; the others cover the same code
				List<Object> at = (List<Object>) s.get("at");
				String terrain = (String) o.get("terrain");
				if (terrain.startsWith("recorded:")) terrain = "recorded:" + ROOT.resolve(terrain.substring(9));
				String name = f.getFileName().toString().replace(".json", "");
				out.add(new Preset(name, (Long) o.get("seed"), terrain, ((Long) at.get(0)).intValue(), ((Long) at.get(1)).intValue(), Tier.of((String) s.get("tier")),
						((Long) s.get("radius")).intValue(), o.get("cellSize") == null ? 1024 : ((Long) o.get("cellSize")).intValue(), String.valueOf(o.getOrDefault("scenes", "none"))));
			}
		}
		return out;
	}

	static List<Preset> selected() throws IOException {
		String want = System.getProperty("fb.det.presets", String.join(",", DEFAULT));
		List<Preset> all = allPresets(), out = new ArrayList<>();
		for (Preset p : all) if (want.equals("all") || List.of(want.split(",")).contains(p.name())) out.add(p);
		assertFalse(out.isEmpty(), "no preset matches " + want);
		return out;
	}

	/** The site the CLI's {@code --site X,Z,TIER,RADIUS} makes: the cells whose sites lie within the radius, plus the claimant. */
	static Setup setup(Preset pr) {
		RegionField field = new RegionField(pr.seed(), pr.cellSize());
		ProbeSpecs.Probes p = ProbeSpecs.parse(pr.terrain());
		Cell claimant = CellGeometry.owner(field, pr.x(), pr.z());
		List<Cell> cells = new ArrayList<>();
		for (int dz = -8; dz <= 8; dz++) {
			for (int dx = -8; dx <= 8; dx++) {
				Cell c = new Cell(claimant.cx() + dx, claimant.cz() + dz);
				long ex = field.siteX(c.cx(), c.cz()) - pr.x(), ez = field.siteZ(c.cx(), c.cz()) - pr.z();
				if (c.equals(claimant) || ex * ex + ez * ez <= (long) pr.radius() * pr.radius()) cells.add(c);
			}
		}
		cells.sort(CellGeometry::compare);
		SettlementSite site = new SettlementSite(Ids.site(claimant.cx(), claimant.cz()), pr.tier(), new Pt(pr.x(), pr.z()), pr.radius(), claimant, cells);
		return new Setup(pr, p, site, field, CoarseProbe.of(p.terrain(), p.biome(), 32));
	}

	/** A synthetic noise-terrain setup for the mutant checks (no file needed, a few seconds). */
	static Setup synthetic() {
		return setup(new Preset("mutant-bed", 11, "noise:3:70:10:300", 1100, 1100, Tier.CITY, 600, 512, "auto:2"));
	}

	// ---- the outputs that are compared

	/** What is compared for one district: its plan JSON. */
	static String districtBytes(DistrictPlan d) { return d == null ? "missing" : DistrictJson.write(d); }

	static DistrictPlan planDistrict(Setup s, SkeletonSource src, StyleField styles, DistrictSummary d) {
		var t = CityPlanner.planTileTrimmed(src, d.ix(), d.iz(), styles, s.p().terrain());
		return t.isEmpty() ? null : t.get().plan();
	}

	static String sceneBytes(Setup s, SkeletonSource src, StyleField styles, DistrictSummary d) {
		var t = CityPlanner.planTileTrimmed(src, d.ix(), d.iz(), styles, s.p().terrain()).orElseThrow();
		DistrictPlan dp = t.plan();
		return IrJson.write(DistrictScenes.build(dp, t.landmarks(), s.p().terrain(), s.p().biome(), true, src.site().tier()).scene());
	}

	/** Reference bytes from a plain in-order {@link CityPlanner#plan} on a fresh {@link SkeletonSource.Direct}. */
	record Reference(CityPlanner.City city, String skeleton, String settlement, TreeMap<String, String> districts, TreeMap<String, String> scenes) {}

	static Reference reference(Setup s) {
		SkeletonSource.Direct src = s.direct();
		CityPlanner.City city = CityPlanner.plan(src, s.coarse(), s.p().terrain(), s.p().biome(), GeneratorV1.GEN_VERSION);
		TreeMap<String, String> districts = new TreeMap<>(), scenes = new TreeMap<>();
		for (var e : city.districts().entrySet()) districts.put(e.getKey(), districtBytes(e.getValue()));
		for (String id : CityPlanner.autoScenes(city, 2)) scenes.put(id, IrJson.write(CityPlanner.scene(city, id, s.p().terrain(), s.p().biome(), true).scene()));
		String skeleton = SettlementJson.write(Skeletons.plan(s.preset().seed(), new FixedSiteView(s.site(), s.field()), s.site(), s.coarse(), GeneratorV1.GEN_VERSION));
		return new Reference(city, skeleton, SettlementJson.write(city.plan()), districts, scenes);
	}

	/** A factory of fresh sources over the setup; the real one is {@code Setup::direct}, the mutants wrap it. */
	interface Factory extends Function<Setup, SkeletonSource> {}

	/**
	 * Plans {@code s} every way the plan lists, with sources from {@code f}, and returns the differences from the reference
	 * (empty when it is deterministic). {@code sample} bounds how many districts are planned alone (d); 0 means all.
	 */
	static List<String> differences(Setup s, Reference ref, Factory f, int sample, int threads) throws Exception {
		List<String> bad = new ArrayList<>();
		StyleField styles = s.styles();
		List<DistrictSummary> sums = new ArrayList<>();
		for (DistrictSummary d : ref.city().plan().districts()) if (ref.districts().containsKey(d.id())) sums.add(d);
		List<String> sceneIds = new ArrayList<>(ref.scenes().keySet());
		Map<String, DistrictSummary> byId = new LinkedHashMap<>();
		for (DistrictSummary d : sums) byId.put(d.id(), d);

		// (b) cells shuffled, each skeleton from a source of its own; then the settlement assembled from them
		for (long shuffle = 0; shuffle < 2; shuffle++) {
			List<Cell> order = new ArrayList<>(s.site().cells());
			Collections.shuffle(order, new Random(7 + shuffle));
			List<CellSkeleton> sk = new ArrayList<>();
			for (Cell c : order) sk.add(f.apply(s).skeleton(c));
			String got = SettlementJson.write(Skeletons.assemble(s.preset().seed(), new FixedSiteView(s.site(), s.field()), s.site(), s.coarse(), GeneratorV1.GEN_VERSION, sk));
			if (!got.equals(ref.skeleton())) bad.add("skeleton assembled from cells built alone in shuffled order " + shuffle + " differs");
		}
		// (b) districts shuffled over one fresh source whose cells were warmed in shuffled order
		for (long shuffle = 0; shuffle < 2; shuffle++) {
			SkeletonSource src = f.apply(s);
			List<Cell> cells = new ArrayList<>(s.site().cells());
			Collections.shuffle(cells, new Random(100 + shuffle));
			for (Cell c : cells) src.skeleton(c);
			List<DistrictSummary> order = new ArrayList<>(sums);
			Collections.shuffle(order, new Random(200 + shuffle));
			for (DistrictSummary d : order) compare(bad, "shuffled " + shuffle, d.id(), ref.districts().get(d.id()), districtBytes(planDistrict(s, src, styles, d)));
		}
		// (a) the full pipeline through the given source, in order
		{
			CityPlanner.City city = CityPlanner.plan(f.apply(s), s.coarse(), s.p().terrain(), s.p().biome(), GeneratorV1.GEN_VERSION);
			compare(bad, "in order", "settlement", ref.settlement(), SettlementJson.write(city.plan()));
			for (var e : ref.districts().entrySet()) {
				DistrictPlan got = city.districts().get(e.getKey());
				compare(bad, "in order", e.getKey(), e.getValue(), got == null ? "missing" : districtBytes(got));
			}
			if (!city.districts().keySet().equals(ref.districts().keySet())) bad.add("in order: planned district set differs");
		}
		// (c) N threads over a source with a 2-cell cache that evicts constantly: districts, scenes, and the whole settlement
		{
			Evicting shared = new Evicting(f.apply(s), s.coarse(), 2);
			ExecutorService pool = Executors.newFixedThreadPool(threads);
			try {
				List<DistrictSummary> order = new ArrayList<>(sums);
				Collections.shuffle(order, new Random(300));
				AtomicInteger next = new AtomicInteger();
				List<Future<Map<String, String>>> futures = new ArrayList<>();
				for (int t = 0; t < threads; t++) {
					futures.add(pool.submit(() -> {
						Map<String, String> got = new TreeMap<>();
						for (int i; (i = next.getAndIncrement()) < order.size();) {
							DistrictSummary d = order.get(i);
							got.put(d.id(), districtBytes(planDistrict(s, shared, styles, d)));
							if (sceneIds.contains(d.id())) got.put("scene " + d.id(), sceneBytes(s, shared, styles, d));
						}
						return got;
					}));
				}
				Map<String, String> all = new TreeMap<>();
				for (var fu : futures) all.putAll(fu.get());
				for (var e : ref.districts().entrySet()) compare(bad, threads + " threads", e.getKey(), e.getValue(), all.get(e.getKey()));
				for (var e : ref.scenes().entrySet()) compare(bad, threads + " threads", "scene " + e.getKey(), e.getValue(), all.get("scene " + e.getKey()));
				if (s.site().cells().size() > 2 && shared.misses.get() <= s.site().cells().size()) bad.add(threads + " threads: the cache never evicted (" + shared.misses.get() + " builds)");
				// the settlement itself, assembled by threads that ask the evicting source for skeletons concurrently
				List<Future<CellSkeleton>> sk = new ArrayList<>();
				List<Cell> cells = new ArrayList<>(s.site().cells());
				Collections.reverse(cells);
				for (Cell c : cells) sk.add(pool.submit(() -> shared.skeleton(c)));
				List<CellSkeleton> built = new ArrayList<>();
				for (var fu : sk) built.add(fu.get());
				String got = SettlementJson.write(Skeletons.assemble(s.preset().seed(), new FixedSiteView(s.site(), s.field()), s.site(), s.coarse(), GeneratorV1.GEN_VERSION, built));
				compare(bad, threads + " threads", "skeleton", ref.skeleton(), got);
			} finally {
				pool.shutdownNow();
			}
		}
		// (d) each district alone in a new source (a sample, or all)
		{
			List<DistrictSummary> order = new ArrayList<>(sums);
			Collections.shuffle(order, new Random(400));
			int n = sample <= 0 ? order.size() : Math.min(sample, order.size());
			for (DistrictSummary d : order.subList(0, n)) compare(bad, "alone", d.id(), ref.districts().get(d.id()), districtBytes(planDistrict(s, f.apply(s), styles, d)));
			for (String id : sceneIds) compare(bad, "alone", "scene " + id, ref.scenes().get(id), sceneBytes(s, f.apply(s), styles, byId.get(id)));
		}
		return bad;
	}

	static void compare(List<String> bad, String way, String what, String expected, String actual) {
		if (!java.util.Objects.equals(expected, actual) && bad.size() < 30) bad.add(way + ": " + what + " differs");
	}

	/** A source whose skeleton cache holds {@code capacity} cells (least recently used out), shared by threads. */
	static final class Evicting implements SkeletonSource {
		private final SkeletonSource base;
		private final CoarseProbe coarse;
		private final int capacity;
		private final LinkedHashMap<Cell, CellSkeleton> cache = new LinkedHashMap<>();
		final AtomicInteger misses = new AtomicInteger();

		Evicting(SkeletonSource base, CoarseProbe coarse, int capacity) {
			this.base = base;
			this.coarse = coarse;
			this.capacity = capacity;
		}

		@Override public long seed() { return base.seed(); }
		@Override public RegionView view() { return base.view(); }
		@Override public SettlementSite site() { return base.site(); }
		@Override public DistrictGrid grid() { return base.grid(); }
		@Override public TierProgram program() { return base.program(); }
		@Override public DensityField density() { return base.density(); }
		@Override public QuarterField quarters() { return base.quarters(); }

		@Override public CellSkeleton skeleton(Cell cell) {
			synchronized (cache) {
				CellSkeleton hit = cache.remove(cell);
				if (hit != null) { cache.put(cell, hit); return hit; }
			}
			// built outside the lock: two threads may build the same cell, which must give the same skeleton
			misses.incrementAndGet();
			CellSkeleton built = Skeletons.build(base.seed(), base.view(), cell, coarse, base.grid(), base.program());
			synchronized (cache) {
				cache.put(cell, built);
				while (cache.size() > capacity) cache.remove(cache.keySet().iterator().next());
			}
			return built;
		}
	}

	// ---- the suite

	@Test
	void presetsPlanTheSameBytesEveryWay() throws Exception {
		checkPresets(selected());
	}

	/** TESTSPEED: every other city preset (about 25, 15 minutes); the default run keeps {@link #DEFAULT}. Run by `-PfbTests=heavy` and test-all.sh --planner-heavy. */
	@org.junit.jupiter.api.Tag("heavy")
	@Test
	void everyPresetPlansTheSameBytesEveryWay() throws Exception {
		checkPresets(allPresets());
	}

	private static void checkPresets(List<Preset> presets) throws Exception {
		boolean full = Boolean.getBoolean("fb.det.full");
		for (Preset pr : presets) {
			Setup s = setup(pr);
			Reference ref = reference(s);
			assertTrue(ref.districts().size() > 1, pr.name() + ": only " + ref.districts().size() + " districts");
			assertFalse(ref.scenes().isEmpty(), pr.name() + ": no scenes chosen");
			assertEquals(ref.settlement(), SettlementJson.write(reference(s).city().plan()), pr.name() + ": two runs");
			assertEquals(List.of(), differences(s, ref, Setup::direct, full ? 0 : 10, 8), pr.name());
		}
	}

	@Test
	void everyPresetHasTheFilesTheSuiteNeeds() throws Exception {
		List<Preset> all = allPresets();
		assertTrue(all.size() >= DEFAULT.size());
		for (String n : DEFAULT) assertTrue(all.stream().anyMatch(p -> p.name().equals(n)), "default preset " + n + " is gone; update DEFAULT");
	}

	// ---- mutants: the checks above must fail on each of these

	private static CellSkeleton dropGates(CellSkeleton k) {
		return new CellSkeleton(k.cell(), k.outline(), k.coarse(), k.hub(), k.roads(), List.of(), k.landmarks(), k.districts(), k.walls());
	}

	private static CellSkeleton withRoads(CellSkeleton k, List<Road> roads) {
		return new CellSkeleton(k.cell(), k.outline(), k.coarse(), k.hub(), roads, k.gates(), k.landmarks(), k.districts(), k.walls());
	}

	/** A source that delegates to a clean one and perturbs the skeletons it hands out. */
	abstract static class Mutant implements SkeletonSource {
		final SkeletonSource base;

		Mutant(SkeletonSource base) { this.base = base; }

		@Override public long seed() { return base.seed(); }
		@Override public RegionView view() { return base.view(); }
		@Override public SettlementSite site() { return base.site(); }
		@Override public DistrictGrid grid() { return base.grid(); }
		@Override public TierProgram program() { return base.program(); }
		@Override public DensityField density() { return base.density(); }
		@Override public QuarterField quarters() { return base.quarters(); }
	}

	private static List<String> run(Factory f) throws Exception {
		Setup s = synthetic();
		return differences(s, reference(s), f, 4, 4);
	}

	@Test
	void theCleanSourceOnTheMutantBedIsDeterministic() throws Exception {
		assertEquals(List.of(), run(Setup::direct));
	}

	/** A hidden counter (a stand-in for one in {@link Districts}): from the 40th skeleton asked for onward the gates are lost. */
	@Test
	void mutantHiddenCounterIsCaught() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		List<String> d = run(s -> new Mutant(s.direct()) {
			@Override public CellSkeleton skeleton(Cell c) {
				CellSkeleton k = base.skeleton(c);
				return calls.incrementAndGet() > 40 && !k.gates().isEmpty() ? dropGates(k) : k;
			}
		});
		assertFalse(d.isEmpty(), "a counter-dependent skeleton went unnoticed");
	}

	/** A skeleton that reads a neighbour's result: it loses its last road if a neighbouring cell was built before it in this source. */
	@Test
	void mutantSkeletonReadingANeighbourIsCaught() throws Exception {
		List<String> d = run(s -> new Mutant(s.direct()) {
			final java.util.Set<Cell> built = Collections.synchronizedSet(new java.util.TreeSet<>(CellGeometry::compare));

			@Override public CellSkeleton skeleton(Cell c) {
				CellSkeleton k = base.skeleton(c);
				boolean east = built.contains(new Cell(c.cx() + 1, c.cz())) || built.contains(new Cell(c.cx(), c.cz() + 1)) || built.contains(new Cell(c.cx() - 1, c.cz())) || built.contains(new Cell(c.cx(), c.cz() - 1));
				built.add(c);
				return east && k.roads().size() > 1 ? withRoads(k, k.roads().subList(0, k.roads().size() - 1)) : k;
			}
		});
		assertFalse(d.isEmpty(), "a neighbour-dependent skeleton went unnoticed");
	}

	/** Roads ordered by the iteration order of a hash set of identity-hashed keys (what a HashMap of objects without hashCode does). */
	@Test
	void mutantHashOrderDependenceIsCaught() throws Exception {
		List<String> d = run(s -> new Mutant(s.direct()) {
			@Override public CellSkeleton skeleton(Cell c) {
				CellSkeleton k = base.skeleton(c);
				Map<Object, Road> keyed = new java.util.IdentityHashMap<>();
				for (Road r : k.roads()) keyed.put(new Object(), r);
				HashSet<Object> order = new HashSet<>(keyed.keySet()); // det-ok: this is the mutant
				List<Road> roads = new ArrayList<>();
				for (Object o : order) roads.add(keyed.get(o));
				return withRoads(k, roads);
			}
		});
		assertFalse(d.isEmpty(), "a hash-order-dependent skeleton went unnoticed");
	}
}
