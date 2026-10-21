package dev.fractalbuildings.core.settlement.tiers;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.settlement.Arterials;
import dev.fractalbuildings.core.settlement.CellGeometry;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Ids;
import dev.fractalbuildings.core.settlement.Landmark;
import dev.fractalbuildings.core.settlement.LandmarkKind;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.QuarterKind;
import dev.fractalbuildings.core.settlement.SkeletonContext;
import dev.fractalbuildings.core.settlement.StreetLayout;
import dev.fractalbuildings.core.settlement.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The city (D3): arterials on tile borders (the {@link Arterials} rule: every border in the dense core, about half further out),
 * 3 or 4 avenues from the hub, on three cities in four 45-degree diagonals cutting through the grid (district detail packs the cut
 * blocks), squares at about one arterial crossing in four, grid blocks where the density is high, an old-town wall on
 * {@value dev.fractalbuildings.core.settlement.OldTown#WALL_PCT} per cent of cities (its ring comes from
 * {@link dev.fractalbuildings.core.settlement.OldTown}: no arterial enters it, and each avenue passes a gate), and the hub square
 * with its hall and temple (the default landmarks).
 *
 * <p>Avenues are straight lattice rays or octilinear two-leg paths (a diagonal and an axis run); a leg that meets water, a steep
 * slope or the edge of the cell's ground falls back to the slope-aware A* of {@link TownProgram}.
 */
public class CityProgram extends TownProgram {
	/** The eight compass directions, 45 degrees apart; odd indexes are the diagonals. */
	static final int[][] OCT = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
	/** Shortest avenue ray that is kept, in lattice steps. */
	static final int MIN_RAY = 5;
	/** Cities in {@value} whose avenues are diagonals (else the four axis directions). */
	static final int DIAGONAL_PCT = 75;
	/** About one arterial crossing in this many gets a square. */
	public static final int SQUARE_ONE_IN = 4;

	@Override protected int exitCount(SkeletonContext c) {
		if (!c.holdsCentre()) return 1;
		return 3 + Hash64.below(Hash64.hash(c.seed, "exit-count", c.cell.cx(), c.cell.cz()), 2);
	}

	/** The avenue directions: every second compass point from a hashed start, diagonals on {@value #DIAGONAL_PCT} per cent of cities. */
	protected List<int[]> directions(SkeletonContext c) {
		long h = Hash64.hash(c.seed, "avenue-dirs", c.site.centre().x(), c.site.centre().z());
		boolean diagonal = Hash64.below(h, 100) < DIAGONAL_PCT;
		int start = Hash64.below(Hash64.hash(h, "start"), 4) * 2 + (diagonal ? 1 : 0), n = exitCount(c);
		List<int[]> out = new ArrayList<>();
		for (int k = 0; k < n; k++) out.add(OCT[(start + 2 * k) % 8]);
		return out;
	}

	@Override public List<Pt> exits(SkeletonContext c, Pt hub) {
		if (hub == null || !c.holdsCentre()) return super.exits(c, hub);
		List<Pt> out = new ArrayList<>();
		for (int[] d : directions(c)) {
			Pt p = ray(c, hub, d[0], d[1]);
			if (p != null && !out.contains(p)) out.add(p);
		}
		return out.size() >= 2 ? out : super.exits(c, hub);
	}

	/** The furthest lattice column along (dx, dz) from the hub that keeps to owned, urban, gentle ground; null when shorter than {@value #MIN_RAY} steps. */
	Pt ray(SkeletonContext c, Pt hub, int dx, int dz) {
		Pt p = hub;
		int n = 0, max = c.site.radius() * 13 / 10 / c.step;
		while (n < max) {
			Pt q = new Pt(p.x() + dx * c.step, p.z() + dz * c.step);
			if (!stepOk(c, p, q, null)) break;
			p = q;
			n++;
		}
		return n >= MIN_RAY ? p : null;
	}

	/** May a road run from lattice column p to the neighbour q: q owned (or the target), urban, dry, and a grade of at most 1 in 3. */
	static boolean stepOk(SkeletonContext c, Pt p, Pt q, Pt target) {
		boolean isTarget = target != null && q.equals(target);
		if (!isTarget && (!c.owned(q.x(), q.z()) || !c.urbanCandidate(q.x(), q.z()))) return false;
		if (c.terrain.water(q.x(), q.z())) return false;
		int len = p.x() != q.x() && p.z() != q.z() ? c.step * 14 / 10 : c.step;
		return Math.abs(c.terrain.surfaceY(q.x(), q.z()) - c.terrain.surfaceY(p.x(), p.z())) * 3 <= len;
	}

	/** Are all lattice steps of the straight (horizontal, vertical or 45-degree) leg from a to b usable? Only the route's final {@code target} may lie outside the cell's ground. */
	static boolean legOk(SkeletonContext c, Pt a, Pt b, Pt target) {
		int dx = b.x() - a.x(), dz = b.z() - a.z();
		if (dx != 0 && dz != 0 && Math.abs(dx) != Math.abs(dz)) return false;
		if (dx % c.step != 0 || dz % c.step != 0) return false;
		int sx = Integer.signum(dx), sz = Integer.signum(dz), n = Math.max(Math.abs(dx), Math.abs(dz)) / c.step;
		Pt p = a;
		for (int k = 1; k <= n; k++) {
			Pt q = new Pt(a.x() + sx * k * c.step, a.z() + sz * k * c.step);
			if (!stepOk(c, p, q, target)) return false;
			p = q;
		}
		return true;
	}

	/** The straight or octilinear (diagonal then axis, or axis then diagonal) route from a to b, null if no such route is usable. */
	Route octilinear(SkeletonContext c, Pt a, Pt b) {
		int dx = b.x() - a.x(), dz = b.z() - a.z();
		if (dx == 0 && dz == 0) return null;
		List<List<Pt>> variants = new ArrayList<>();
		int m = Math.min(Math.abs(dx), Math.abs(dz)), sx = Integer.signum(dx), sz = Integer.signum(dz);
		if (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz)) variants.add(List.of(a, b));
		else {
			variants.add(List.of(a, new Pt(a.x() + sx * m, a.z() + sz * m), b)); // the diagonal first, from the hub end
			variants.add(List.of(a, new Pt(b.x() - sx * m, b.z() - sz * m), b));
		}
		for (List<Pt> v : variants) {
			boolean ok = true;
			for (int i = 1; i < v.size() && ok; i++) ok = legOk(c, v.get(i - 1), v.get(i), b);
			if (ok) return new Route(b, v, List.of());
		}
		return null;
	}

	@Override protected Route route(SkeletonContext c, Pt hub, Pt target) {
		if (hub == null || hub.equals(target)) return super.route(c, hub, target);
		Route r = octilinear(c, hub, target);
		return r != null ? r : super.route(c, hub, target);
	}

	// ---- the old-town wall

	/** Avenues are 5 wide in a walled city (the wall type's passage is 5 wide, and a road wider than its gate would be cut), else the tier's 7 or 9. */
	@Override public int mainWidth(SkeletonContext c) { return c.density.oldTown().walled() ? WallRing.BAND * 2 + 1 : super.mainWidth(c); }

	@Override protected boolean walled(SkeletonContext c) { return c.holdsCentre() && c.density.oldTown().walled(); }

	/**
	 * The old town's ring (from {@link dev.fractalbuildings.core.settlement.OldTown}) when this cell owns all of it and the hub
	 * lies well inside; else none, and the city has no wall.
	 */
	@Override protected Optional<WallRing.Ring> ring(SkeletonContext c, Pt hub) {
		if (!walled(c) || hub == null) return Optional.empty();
		Optional<WallRing.Ring> r = c.density.oldTown().ring();
		if (r.isEmpty()) return r;
		WallRing.Ring q = r.get();
		if (hub.x() < q.xl() + 30 || hub.x() > q.xr() - 30 || hub.z() < q.zt() + 30 || hub.z() > q.zb() - 30) return Optional.empty();
		return WallRing.fits(q, c.step, ok(c), (x, z) -> c.density.urban(x, z)) ? r : Optional.empty();
	}

	// ---- squares at arterial crossings

	/**
	 * Squares: 24 to 36 blocks, in the corner of a tile next to an arterial crossing, touching its arterials. Each cell lists the
	 * squares of the tiles whose centre it owns (the cell that plans the tile's district), so every district finds its square in one skeleton.
	 */
	@Override public List<Landmark> cellLandmarks(SkeletonContext c) {
		List<Landmark> out = new ArrayList<>();
		if (!c.site.tier().atLeast(Tier.CITY)) return out;
		DistrictGrid g = c.grid;
		int x1 = c.x0 + (c.nx - 1) * c.step, z1 = c.z0 + (c.nz - 1) * c.step;
		DistrictGrid.TileId a = g.tileOf(c.x0, c.z0), b = g.tileOf(x1, z1);
		DistrictGrid.TileId centreTile = g.tileOf(c.site.centre().x(), c.site.centre().z());
		for (int iz = a.iz() - 1; iz <= b.iz() + 1; iz++) {
			for (int ix = a.ix() - 1; ix <= b.ix() + 1; ix++) {
				DistrictGrid.Tile t = g.tile(ix, iz);
				if (!CellGeometry.owns(c.field, c.cell, (t.minX() + t.maxX()) / 2, (t.minZ() + t.maxZ()) / 2)) continue;
				if (ix == centreTile.ix() && iz == centreTile.iz()) continue; // the hub square owns its tile
				for (int quadrant = 0; quadrant < 4; quadrant++) {
					int sx = quadrant % 2 == 0 ? 1 : -1, sz = quadrant < 2 ? 1 : -1; // the crossing is the tile's corner at (sx > 0 ? min : max) in each axis
					int kx = ix + (sx < 0 ? 1 : 0), kz = iz + (sz < 0 ? 1 : 0);
					if (quadrantOf(c, kx, kz) != quadrant) continue;
					squareAt(c, out, t, kx, kz, sx, sz);
					break;
				}
			}
		}
		return out;
	}

	/** The quadrant (0 to 3: +x+z, -x+z, +x-z, -x-z of the crossing) that holds the crossing's square, -1 for none. */
	private static int quadrantOf(SkeletonContext c, int kx, int kz) {
		DistrictGrid g = c.grid;
		long h = Hash64.hash(c.seed, "cross-square", kx, kz);
		if (Hash64.below(h, SQUARE_ONE_IN) != 0) return -1;
		Arterials art = c.density.arterials();
		boolean below = art.is(g.segments(kx, kz)[0]), above = art.is(g.segments(kx, kz - 1)[0]);
		boolean right = art.is(g.segments(kx, kz)[2]), left = art.is(g.segments(kx - 1, kz)[2]);
		if (!(above || below) || !(left || right)) return -1;
		// the first of four hashed picks whose two border segments are both arterial
		int pick = Hash64.below(Hash64.hash(h, "quadrant"), 4);
		for (int k = 0; k < 4; k++) {
			int q = (pick + k) % 4;
			boolean horiz = q % 2 == 0 ? right : left, vert = q < 2 ? below : above;
			if (vert && horiz) return q;
		}
		return -1;
	}

	private static void squareAt(SkeletonContext c, List<Landmark> out, DistrictGrid.Tile t, int kx, int kz, int sx, int sz) {
		DistrictGrid g = c.grid;
		long h = Hash64.hash(c.seed, "cross-square", kx, kz);
		int lx = g.lineBlock(DistrictGrid.AXIS_X, kx), lz = g.lineBlock(DistrictGrid.AXIS_Z, kz);
		int w = Hash64.range(Hash64.hash(h, "w"), 24, 36), d = Hash64.range(Hash64.hash(h, "d"), 24, 36), inset = c.density.arterials().width() / 2 + 1;
		int px = sx > 0 ? lx + inset : lx - inset - w, pz = sz > 0 ? lz + inset : lz - inset - d;
		int cx = px + w / 2, cz = pz + d / 2;
		if (!c.density.urban(cx, cz) || c.terrain.waterWithin(cx, cz, 24) || c.density.at(cx, cz) < 50) return;
		if (c.quarters.at(cx, cz, c.density.at(cx, cz)) == QuarterKind.PARK) return; // a park tile stays open ground
		if (nearRing(c, px, pz, w, d)) return;
		String district = Ids.district(t.ix(), t.iz());
		out.add(new Landmark("l?", LandmarkKind.SQUARE, px, pz, w, d, district));
		if (Hash64.below(Hash64.hash(h, "hall"), 2) == 0) hallOn(c, out, t, px, pz, w, d, sx, district);
	}

	/** Is the square within 40 blocks of the old-town wall (whose lots reserve the ground first)? */
	private static boolean nearRing(SkeletonContext c, int x, int z, int w, int d) {
		Optional<WallRing.Ring> r = c.density.oldTown().ring();
		if (r.isEmpty()) return false;
		WallRing.Ring q = r.get();
		return x < q.xr() + 40 && x + w > q.xl() - 40 && z < q.zb() + 40 && z + d > q.zt() - 40;
	}

	/** A hall facing the square from the side away from the crossing (district detail grows its lot flush against the square). */
	private static void hallOn(SkeletonContext c, List<Landmark> out, DistrictGrid.Tile t, int px, int pz, int w, int d, int sx, String district) {
		int bw = 18, bd = 28; // facing east or west
		int bx = sx > 0 ? px + w + FACING_GAP : px - FACING_GAP - bw, bz = pz + d / 2 - bd / 2;
		int m = TILE_MARGIN;
		if (bx < t.minX() + m || bx + bw > t.maxX() - m || bz < t.minZ() + m || bz + bd > t.maxZ() - m) return;
		out.add(new Landmark("l?", LandmarkKind.HALL, bx, bz, bw, bd, district));
	}

	// ---- street layout

	/** Parks (a metropolis's open downtown tiles) get no grid streets; elsewhere as the default. */
	@Override public StreetLayout streetLayout(Tier tier, QuarterKind quarter, int density) {
		if (quarter == QuarterKind.PARK) return StreetLayout.FRONTAGE;
		return super.streetLayout(tier, quarter, density);
	}
}
