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
	}
}
