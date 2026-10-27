package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.terrain.LatticePath;
import java.util.List;

/**
 * What a {@link TierProgram} may read while a cell skeleton is built: the site, the cell and its polygon, the
 * pointwise fields, and the cell's window of the lattice with which columns it owns and which are urban. All
 * queries are on lattice columns.
 */
public final class SkeletonContext {
	public final long seed;
	public final SettlementSite site;
	public final Cell cell;
	public final RegionField field;
	public final CoarseTerrain terrain;
	public final DistrictGrid grid;
	public final DensityField density;
	public final QuarterField quarters;
	public final List<Pt> polygon;
	/** The window of lattice columns: ({@code x0 + i step}, {@code z0 + j step}), row-major with z outer. */
	public final int x0, z0, nx, nz, step;
	final boolean[] owned, candidate;
	int[] mask;
	/** For {@link #tileOwned}: set by the builder once the hub is known (6.15 ROADJOIN step 3). */
	RegionView view;
	DistrictGrid.TileId siteHubTile;
	private final java.util.TreeMap<Long, Boolean> tileOwned = new java.util.TreeMap<>();

	SkeletonContext(long seed, SettlementSite site, Cell cell, RegionField field, CoarseTerrain terrain, DistrictGrid grid, DensityField density,
			QuarterField quarters, List<Pt> polygon, int x0, int z0, int nx, int nz) {
		this.seed = seed;
		this.site = site;
		this.cell = cell;
		this.field = field;
		this.terrain = terrain;
		this.grid = grid;
		this.density = density;
		this.quarters = quarters;
		this.polygon = polygon;
		this.x0 = x0;
		this.z0 = z0;
		this.nx = nx;
		this.nz = nz;
		this.step = terrain.step;
		this.owned = new boolean[nx * nz];
		this.candidate = new boolean[nx * nz];
		this.mask = new int[nx * nz];
	}

	private int index(int x, int z) {
		int i = Math.floorDiv(x - x0, step), j = Math.floorDiv(z - z0, step);
		return i < 0 || j < 0 || i >= nx || j >= nz ? -1 : j * nx + i;
	}

	/** Does this cell own the column (exact; outside the window too)? */
	public boolean owned(int x, int z) {
		int k = index(x, z);
		return k >= 0 ? owned[k] : CellGeometry.owns(field, cell, x, z);
	}

	/** Pre-reachability urban test: inside the footprint and buildable. */
	public boolean urbanCandidate(int x, int z) {
		int k = index(x, z);
		return k >= 0 ? candidate[k] : density.urban(x, z);
	}

	/**
	 * Does this settlement plan the district tile of the column ({@link Districts#owns})? Exits are chosen by cell ownership but districts
	 * are planned by tile ownership, so an exit can sit in a tile nobody plans and its road never reaches a street (6.15 ROADJOIN step 3).
	 * True when the context was built without a view (tests).
	 */
	public boolean tileOwned(int x, int z) {
		if (view == null) return true;
		DistrictGrid.Tile t = grid.tileAt(x, z);
		return tileOwned.computeIfAbsent(((long) t.ix() << 32) ^ (t.iz() & 0xffffffffL), k -> Districts.owns(view, site, grid, siteHubTile, t.ix(), t.iz()));
	}

	/** An exit column that road traffic can leave from: the column and its eight lattice neighbours are all dry, so no bridge ends on it (6.16 DEADMAIN). */
	public boolean solid(int x, int z) {
		return terrain.dryAround(x, z);
	}

	/** The final mask value at a window column (see {@link CoarseRaster}); -1 outside the window. */
	public int mask(int x, int z) {
		int k = index(x, z);
		return k >= 0 ? mask[k] : -1;
	}

	/** Search bounds for lattice paths: the window. */
	public LatticePath.Bounds bounds() { return new LatticePath.Bounds(x0, z0, x0 + (nx - 1) * step, z0 + (nz - 1) * step); }

	/** Does the site's centre lie in this cell? */
	public boolean holdsCentre() { return CellGeometry.owns(field, cell, site.centre().x(), site.centre().z()); }
}
