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
