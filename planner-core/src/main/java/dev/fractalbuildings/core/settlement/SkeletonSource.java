package dev.fractalbuildings.core.settlement;

/**
 * What district detail may read about one settlement (plan step DL): its site, the pointwise fields, the tier program and the
 * cell skeletons. Skeletons are pure, so implementations may build them on demand and keep them; {@link Direct} does. A source
 * serves one settlement; the same source and the same exact terrain always give the same district plan.
 */
public interface SkeletonSource {
	long seed();

	RegionView view();

	SettlementSite site();

	DistrictGrid grid();

	TierProgram program();

	/** Pointwise density of the settlement (shares its arterial memo). */
	DensityField density();

	QuarterField quarters();

	/** The skeleton of a member cell; throws for a cell the site does not span. */
	CellSkeleton skeleton(Cell cell);

	/**
	 * The settlement's skeleton road network ({@link RoadNetwork}): which planned tiles there are and which road columns reach the hub.
	 * Built from {@link #skeleton} of every member cell; this default builds it on every call, so sources that serve many tiles memoise it.
	 */
	default RoadNetwork network() { return RoadNetwork.build(this); }

	/**
	 * The heights at which the settlement's whole streets meet ({@link JunctionField}), over the given exact terrain. This default builds
	 * it on every call; sources that serve many tiles memoise it (per probe).
	 */
	default JunctionField junctions(dev.fractalbuildings.core.probe.TerrainProbe exact) { return JunctionField.build(this, exact); }

	/** One approach's drawn centre line (corner nodes, as {@code Curve.stations} takes them) and its road width. */
	record ApproachLine(java.util.List<int[]> nodes, int width) {}

	/**
	 * The settlement's approaches, geometry only, for the district planner to keep lots off (ROADJOIN step 4). Empty when the source knows
	 * none (the CLI without a road graph); {@code WorldPlanner} supplies the curves the road layer draws.
	 */
	default java.util.List<ApproachLine> approaches() { return java.util.List.of(); }

	/**
	 * Whether a road continues beyond the exit gate at (x, z): a drawn approach leaves from it (6.25 MAINSTUB). This default knows the skeletons'
	 * approaches (the CLI with a road graph); {@code WorldPlanner} supplies the approaches its link rule keeps. A gate that is not served keeps its
	 * road only as far as lots and streets use it ({@link ExitTrim}).
	 */
	default boolean exitServed(int x, int z) {
		for (Cell c : site().cells()) {
			for (Road r : skeleton(c).approaches()) {
				Pt p = r.points().get(0);
				if (Math.abs(p.x() - x) <= 2 && Math.abs(p.z() - z) <= 2) return true;
			}
		}
		return false;
	}

	/** How this settlement's builds may fill the exact probe's memo ahead of their reads (PARREADS); never changes an answer. */
	default dev.fractalbuildings.core.probe.ParallelReads reads() { return dev.fractalbuildings.core.probe.ParallelReads.NONE; }

	/**
	 * A value worked out once from the whole settlement over the given exact terrain (the settlement's quay runs), by name. It must be a
	 * pure function of the source and the probe, whichever tile asks first. This default builds it on every call; sources that serve many
	 * tiles memoise it (per probe).
	 */
	default <T> T shared(String key, dev.fractalbuildings.core.probe.TerrainProbe exact, java.util.function.Supplier<T> build) { return build.get(); }

	/** QUAYLAZY: whether the settlement's quay runs come from the reference whole trace ({@code Districts.Build.settlementQuaysReference}), kept as the oracle of the lazy trace's tests. Never changes an answer. */
	default boolean quayReference() { return false; }

	/** Builds skeletons directly from a coarse probe, once per cell. */
	final class Direct implements SkeletonSource {
		private final long seed;
		private final RegionView view;
		private final SettlementSite site;
		private final dev.fractalbuildings.core.probe.CoarseProbe probe;
		private final DistrictGrid grid;
		private final TierProgram program;
		private final DensityField density;
		private final QuarterField quarters;
		private final dev.fractalbuildings.core.cache.BoundedCache<Cell, CellSkeleton> memo;

		public Direct(long seed, RegionView view, SettlementSite site, dev.fractalbuildings.core.probe.CoarseProbe probe) {
			this(seed, view, site, probe, TierPrograms.of(site.tier()));
		}

		/** Skeletons kept per source (LRU): a site spans a handful of cells, so this keeps them all; pass less to bound memory or to test eviction. */
		public static final int DEFAULT_CACHE = 64;

		public Direct(long seed, RegionView view, SettlementSite site, dev.fractalbuildings.core.probe.CoarseProbe probe, TierProgram program) {
			this(seed, view, site, probe, program, DEFAULT_CACHE);
		}

		/** {@code cacheCapacity} cell skeletons are kept; an evicted one is rebuilt (skeletons are pure), so it changes speed only. */
		public Direct(long seed, RegionView view, SettlementSite site, dev.fractalbuildings.core.probe.CoarseProbe probe, TierProgram program, int cacheCapacity) {
			this.memo = new dev.fractalbuildings.core.cache.BoundedCache<>(cacheCapacity);
			this.seed = seed;
			this.view = view;
			this.site = site;
			this.probe = probe;
			this.grid = new DistrictGrid(seed);
			this.program = program;
			CoarseTerrain terrain = new CoarseTerrain(probe);
			this.density = new DensityField(seed, site, terrain, grid);
			this.quarters = new QuarterField(seed, site, terrain, density);
		}

		@Override public long seed() { return seed; }

		@Override public RegionView view() { return view; }

		@Override public SettlementSite site() { return site; }

		@Override public DistrictGrid grid() { return grid; }

		@Override public TierProgram program() { return program; }

		@Override public DensityField density() { return density; }

		@Override public QuarterField quarters() { return quarters; }

		@Override public CellSkeleton skeleton(Cell cell) {
			if (!site.cells().contains(cell)) throw new IllegalArgumentException("cell " + cell + " is not part of " + site.id());
			return memo.get(cell, c -> Skeletons.build(seed, view, c, probe, grid, program));
		}

		private volatile dev.fractalbuildings.core.probe.ParallelReads reads = dev.fractalbuildings.core.probe.ParallelReads.NONE;

		/** The world planner's parallel reads (PARREADS). */
		public Direct withReads(dev.fractalbuildings.core.probe.ParallelReads r) { reads = r == null ? dev.fractalbuildings.core.probe.ParallelReads.NONE : r; return this; }

		@Override public dev.fractalbuildings.core.probe.ParallelReads reads() { return reads; }

		private volatile boolean quayReference;

		/** Selects the reference whole quay trace over the default one (tests: the two must agree byte for byte). */
		public Direct withQuayReference(boolean on) { quayReference = on; return this; }

		@Override public boolean quayReference() { return quayReference; }

		private volatile java.util.function.Supplier<java.util.List<ApproachLine>> approachSource = java.util.List::of;
		private volatile java.util.List<ApproachLine> approachMemo;

		/** Where the approaches come from (the world planner, which owns the road graph); read lazily, by the first tile that plans lots. */
		public Direct withApproaches(java.util.function.Supplier<java.util.List<ApproachLine>> s) { approachSource = s; approachMemo = null; return this; }

		@Override public java.util.List<ApproachLine> approaches() {
			java.util.List<ApproachLine> a = approachMemo;
			if (a == null) approachMemo = a = approachSource.get();
			return a;
		}

		private volatile java.util.function.BiPredicate<Integer, Integer> exitSource;

		/** Where the served exits come from (the world planner's link rule); without it the skeletons' approaches decide. */
		public Direct withExitServed(java.util.function.BiPredicate<Integer, Integer> s) { exitSource = s; return this; }

		@Override public boolean exitServed(int x, int z) {
			java.util.function.BiPredicate<Integer, Integer> s = exitSource;
			return s != null ? s.test(x, z) : SkeletonSource.super.exitServed(x, z);
		}

		private volatile RoadNetwork network;

		/** Built once, by the first thread that asks (the others wait: a metropolis network costs seconds, so duplicating it on every worker would multiply the cost). */
		@Override public RoadNetwork network() {
			RoadNetwork n = network;
			if (n == null) {
				synchronized (this) {
					n = network;
					if (n == null) network = n = RoadNetwork.build(this);
				}
			}
			return n;
		}

		private volatile Object[] junctions;

		/** Built once per exact probe, by the first thread that asks (as {@link #network}). */
		@Override public JunctionField junctions(dev.fractalbuildings.core.probe.TerrainProbe exact) {
			Object[] j = junctions;
			if (j == null || j[0] != exact) {
				synchronized (this) {
					j = junctions;
					if (j == null || j[0] != exact) junctions = j = new Object[] {exact, JunctionField.build(this, exact)};
				}
			}
			return (JunctionField) j[1];
		}

		/** Copy on write: a published map is never changed, so a cache hit reads it without the lock. */
		private volatile java.util.TreeMap<String, Object[]> shared = new java.util.TreeMap<>();

		/** Built once per name and exact probe, by the first thread that asks (as {@link #network}); a hit takes no lock. */
		@SuppressWarnings("unchecked")
		@Override public <T> T shared(String key, dev.fractalbuildings.core.probe.TerrainProbe exact, java.util.function.Supplier<T> build) {
			Object[] v = shared.get(key);
			if (v != null && v[0] == exact) return (T) v[1];
			synchronized (this) {
				v = shared.get(key);
				if (v == null || v[0] != exact) {
					java.util.TreeMap<String, Object[]> next = new java.util.TreeMap<>(shared);
					next.put(key, v = new Object[] {exact, build.get()});
					shared = next;
				}
				return (T) v[1];
			}
		}
	}
}
