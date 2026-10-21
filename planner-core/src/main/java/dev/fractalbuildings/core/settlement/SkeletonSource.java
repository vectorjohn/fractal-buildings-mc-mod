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
		private final java.util.TreeMap<Cell, CellSkeleton> memo = new java.util.TreeMap<>(CellGeometry::compare);

		public Direct(long seed, RegionView view, SettlementSite site, dev.fractalbuildings.core.probe.CoarseProbe probe) {
			this(seed, view, site, probe, new DefaultProgram());
		}

		public Direct(long seed, RegionView view, SettlementSite site, dev.fractalbuildings.core.probe.CoarseProbe probe, TierProgram program) {
			this.seed = seed;
			this.view = view;
			this.site = site;
			this.probe = probe;
			this.grid = new DistrictGrid(seed);
			this.program = program;
			CoarseTerrain terrain = new CoarseTerrain(probe);
			this.density = new DensityField(seed, site, terrain, grid);
			this.quarters = new QuarterField(seed, site, terrain);
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
			return memo.computeIfAbsent(cell, c -> Skeletons.build(seed, view, c, probe, grid, program));
		}
	}
}
