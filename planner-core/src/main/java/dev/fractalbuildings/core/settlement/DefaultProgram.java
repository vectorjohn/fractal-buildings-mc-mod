package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.Tiers;
import dev.fractalbuildings.core.terrain.LatticePath;
import java.util.ArrayList;
import java.util.List;

/** The program every tier uses until D1 to D3 add their own: hub, exits, A* main roads, hub square and hall. */
public class DefaultProgram implements TierProgram {
	/** Tile margin kept free around a landmark (road half-widths). */
	public static final int TILE_MARGIN = 8;
	/** Gap between the hub square and the building facing it. */
	public static final int FACING_GAP = 6;
	/** Exits closer than this to the hub are dropped. */
	public static final int MIN_EXIT = 96;

	@Override public Pt hub(SkeletonContext c) {
		Pt target = c.holdsCentre() ? c.site.centre() : new Pt(c.field.siteX(c.cell.cx(), c.cell.cz()), c.field.siteZ(c.cell.cx(), c.cell.cz()));
		long best = Long.MAX_VALUE;
		Pt hub = null;
		for (int j = 0; j < c.nz; j++) {
			for (int i = 0; i < c.nx; i++) {
				int x = c.x0 + i * c.step, z = c.z0 + j * c.step;
				if (!c.owned[j * c.nx + i] || !c.candidate[j * c.nx + i]) continue;
				long dx = x - target.x(), dz = z - target.z(), d = dx * dx + dz * dz;
				if (d < best) {
					best = d;
					hub = new Pt(x, z);
				}
			}
		}
		return hub;
	}

	/** How many exits the cell gets. */
	protected int exitCount(SkeletonContext c) {
		Tier t = c.site.tier();
		long h = Hash64.hash(c.seed, "exit-count", c.cell.cx(), c.cell.cz());
		if (!c.holdsCentre()) return t.atLeast(Tier.TOWN) ? 1 : 0;
		return switch (t) {
			case HAMLET -> 1;
			case VILLAGE -> 2;
			case TOWN -> 2 + Hash64.below(h, 3);
			case CITY -> 3 + Hash64.below(h, 2);
			case METROPOLIS -> 4;
		};
	}

	@Override public List<Pt> exits(SkeletonContext c, Pt hub) {
		List<Pt> out = new ArrayList<>();
		if (hub == null) return out;
		int n = exitCount(c);
		double base = Tiers.unit(Hash64.hash(c.seed, "exit-angle", c.cell.cx(), c.cell.cz(), 0)) * 2 * Math.PI;
		int minExit = Math.max(c.step, Math.min(MIN_EXIT, c.site.radius() / 2));
		for (int k = 0; k < n; k++) {
			double jitter = (Tiers.unit(Hash64.hash(c.seed, "exit-angle", c.cell.cx(), c.cell.cz(), k + 1)) - 0.5) * Math.PI / (2.0 * n);
			double theta = base + k * 2 * Math.PI / n + jitter;
			double ux = StrictMath.cos(theta), uz = StrictMath.sin(theta);
			Pt best = pick(c, hub, ux, uz, minExit);
			if (best != null && !out.contains(best)) out.add(best);
		}
		return out;
	}

	/**
	 * The urban column that goes furthest along the direction (u, v) without straying sideways: the first pass wants at least
	 * {@code minExit} from the hub; a tiny hamlet may only have nearer columns, so the second takes any column ahead.
	 */
	private static Pt pick(SkeletonContext c, Pt hub, double ux, double uz, int minExit) {
		for (int pass = 0; pass < 2; pass++) {
			double bestScore = -1e18;
			Pt best = null;
			for (int j = 0; j < c.nz; j++) {
				for (int i = 0; i < c.nx; i++) {
					if (c.mask[j * c.nx + i] != CoarseRaster.URBAN || !c.owned[j * c.nx + i]) continue;
					int x = c.x0 + i * c.step, z = c.z0 + j * c.step;
					double dx = x - hub.x(), dz = z - hub.z();
					double fwd = dx * ux + dz * uz, perp = StrictMath.abs(-dx * uz + dz * ux);
					double score = fwd - 0.5 * perp;
					if ((pass == 0 ? fwd >= minExit : fwd > 0) && score > bestScore) {
						bestScore = score;
						best = new Pt(x, z);
					}
				}
			}
			if (best != null) return best;
		}
		return null;
	}

	@Override public List<Route> mainRoads(SkeletonContext c, Pt hub, List<Pt> targets) {
		List<Route> out = new ArrayList<>();
		for (Pt t : targets) out.add(route(c, hub, t));
		return out;
	}

	/** Slope-aware A* on the lattice from the hub to a target inside the cell (or on its gate). */
	protected Route route(SkeletonContext c, Pt hub, Pt target) {
		if (hub == null || hub.equals(target)) return new Route(target, List.of(), List.of());
		int step = c.step;
		LatticePath.Cost cost = new LatticePath.Cost() {
			@Override public long step(int x, int z, int nx, int nz) {
				if (!(c.owned(nx, nz) || (nx == target.x() && nz == target.z()))) return FORBIDDEN;
				boolean diag = x != nx && z != nz;
				int dy = Math.abs(c.terrain.surfaceY(nx, nz) - c.terrain.surfaceY(x, z));
				int len = diag ? step * 14 / 10 : step;
				if (dy * 3 > len) return FORBIDDEN; // grade over 1 in 3
				long cost = (diag ? 14L : 10L) * step + 60L * dy;
				if (c.terrain.water(nx, nz)) return cost + 400;
				if (c.terrain.steep(nx, nz)) cost += 300;
				if (!c.urbanCandidate(nx, nz)) cost = cost * 16 / 10;
				return cost;
			}

			@Override public boolean water(int x, int z) { return c.terrain.water(x, z); }
		};
		List<LatticePath.Pt> path = LatticePath.find(new LatticePath.Pt(hub.x(), hub.z()), new LatticePath.Pt(target.x(), target.z()), cost,
				c.bounds(), step, Arterials.MAX_BRIDGE, LatticePath.DEFAULT_MAX_EXPANSIONS, false);
		if (path.size() < 2) return new Route(target, List.of(), List.of());
		return simplify(c, path, target);
	}

	/** Drop points in the middle of straight runs, except around water so the bridge ranges stay exact. */
	private static Route simplify(SkeletonContext c, List<LatticePath.Pt> path, Pt target) {
		int n = path.size();
		boolean[] wet = new boolean[n], keep = new boolean[n];
		for (int i = 0; i < n; i++) wet[i] = c.terrain.water(path.get(i).x(), path.get(i).z());
		for (int i = 0; i < n; i++) {
			boolean nearWet = wet[i] || (i > 0 && wet[i - 1]) || (i < n - 1 && wet[i + 1]);
			boolean corner = i == 0 || i == n - 1 || !sameDir(path.get(i - 1), path.get(i), path.get(i + 1));
			keep[i] = nearWet || corner;
		}
		List<Pt> pts = new ArrayList<>();
		List<Boolean> w = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (!keep[i]) continue;
			pts.add(new Pt(path.get(i).x(), path.get(i).z()));
			w.add(wet[i]);
		}
		return new Route(target, pts, bridgeRanges(w));
	}

	private static boolean sameDir(LatticePath.Pt a, LatticePath.Pt b, LatticePath.Pt d) {
		return Integer.signum(b.x() - a.x()) == Integer.signum(d.x() - b.x()) && Integer.signum(b.z() - a.z()) == Integer.signum(d.z() - b.z());
	}

	/** Index ranges [first land point before, first land point after] of each run of wet points. */
	static List<int[]> bridgeRanges(List<Boolean> wet) {
		List<int[]> out = new ArrayList<>();
		int n = wet.size();
		for (int i = 0; i < n; ) {
			if (!wet.get(i)) { i++; continue; }
			int j = i;
			while (j + 1 < n && wet.get(j + 1)) j++;
			int a = Math.max(0, i - 1), b = Math.min(n - 1, j + 1);
			if (!out.isEmpty() && out.get(out.size() - 1)[1] >= a) out.get(out.size() - 1)[1] = b;
			else out.add(new int[] {a, b});
			i = j + 1;
		}
		return out;
	}

	@Override public RoadKind mainKind(Tier tier) { return tier == Tier.HAMLET ? RoadKind.PATH : RoadKind.MAIN; }

	@Override public int mainWidth(Tier tier) {
		return switch (tier) {
			case HAMLET -> 3;
			case VILLAGE -> 4;
			case TOWN -> 5;
			case CITY -> 7;
			case METROPOLIS -> 9;
		};
	}

	@Override public List<Landmark> landmarks(SkeletonContext c, Pt hub) {
		Tier tier = c.site.tier();
		List<Landmark> out = new ArrayList<>();
		if (hub == null || tier == Tier.HAMLET) return out;
		long hs = Hash64.hash(c.seed, "hub-square", c.site.centre().x(), c.site.centre().z());
		LandmarkKind kind = switch (tier) {
			case VILLAGE -> LandmarkKind.GREEN;
			case TOWN -> LandmarkKind.MARKET;
			default -> LandmarkKind.SQUARE;
		};
		int lo = kind == LandmarkKind.GREEN ? 16 : kind == LandmarkKind.SQUARE ? 24 : 32, hi = kind == LandmarkKind.GREEN ? 28 : kind == LandmarkKind.SQUARE ? 40 : 48;
		int w = Hash64.range(Hash64.hash(hs, "w"), lo, hi), d = Hash64.range(Hash64.hash(hs, "d"), lo, hi);
		DistrictGrid.Tile tile = c.grid.tileAt(hub.x(), hub.z());
		Rect r = new Rect(clamp(hub.x() - w / 2, tile.minX() + TILE_MARGIN, tile.maxX() - TILE_MARGIN - w),
				clamp(hub.z() - d / 2, tile.minZ() + TILE_MARGIN, tile.maxZ() - TILE_MARGIN - d), w, d);
		String district = Ids.district(tile.ix(), tile.iz());
		List<Rect> taken = new ArrayList<>(List.of(r));
		out.add(new Landmark("l?", kind, r.x(), r.z(), r.w(), r.d(), district));
		// buildings facing the square: village a temple or a hall, town a hall, city and up a hall and a temple
		List<LandmarkKind> faces = new ArrayList<>();
		if (tier == Tier.VILLAGE) faces.add(Hash64.below(Hash64.hash(hs, "face"), 2) == 0 ? LandmarkKind.TEMPLE : LandmarkKind.HALL);
		else if (tier == Tier.TOWN) faces.add(LandmarkKind.HALL);
		else { faces.add(LandmarkKind.HALL); faces.add(LandmarkKind.TEMPLE); }
		int side0 = Hash64.below(Hash64.hash(hs, "side"), 4);
		int k = 0;
		for (LandmarkKind fk : faces) {
			boolean hall = fk == LandmarkKind.HALL;
			for (int q = 0; q < 4; q++) {
				int side = (side0 + k + q) % 4; // 0 north (-z), 1 east, 2 south, 3 west
				int bw = hall ? 28 : 20, bd = hall ? 18 : 20;
				if (side == 1 || side == 3) { int t = bw; bw = bd; bd = t; }
				int bx, bz;
				if (side == 0 || side == 2) {
					bx = r.x() + r.w() / 2 - bw / 2;
					bz = side == 0 ? r.z() - FACING_GAP - bd : r.z() + r.d() + FACING_GAP;
				} else {
					bz = r.z() + r.d() / 2 - bd / 2;
					bx = side == 3 ? r.x() - FACING_GAP - bw : r.x() + r.w() + FACING_GAP;
				}
				boolean fits = bx >= tile.minX() + TILE_MARGIN && bz >= tile.minZ() + TILE_MARGIN && bx + bw <= tile.maxX() - TILE_MARGIN && bz + bd <= tile.maxZ() - TILE_MARGIN;
				for (Rect o : taken) if (Rect.overlap(bx, bz, bw, bd, o.x(), o.z(), o.w(), o.d())) fits = false;
				if (!fits) continue;
				taken.add(new Rect(bx, bz, bw, bd));
				out.add(new Landmark("l?", fk, bx, bz, bw, bd, district));
				break;
			}
			k++;
		}
		return out;
	}

	private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

	@Override public StreetLayout streetLayout(Tier tier, QuarterKind quarter, int density) {
		boolean dense = tier.atLeast(Tier.CITY) && quarter != QuarterKind.OLD_TOWN && (density >= 50 || quarter == QuarterKind.DOWNTOWN);
		return dense ? StreetLayout.GRID : StreetLayout.FRONTAGE;
	}
}
