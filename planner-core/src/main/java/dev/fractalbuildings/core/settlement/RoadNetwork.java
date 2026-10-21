package dev.fractalbuildings.core.settlement;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.TreeSet;

/**
 * The settlement's skeleton road network as district detail will draw it (ROADS): every main road and path of every member
 * cell, and every arterial segment, kept to the columns some planned tile draws (its tile plus {@link Districts#MARGIN}), as
 * centre lines on a block raster, and the part of it that reaches the hub (8-connected, from the hub column of the cell that
 * holds the site centre).
 *
 * <p>District detail lets lots front a street only when the street's piece in its tile holds a column of a live root (a main
 * road, path or arterial column of this network that reaches the hub). Deciding it from the whole network rather than per
 * tile is what keeps a fragment that looks fine inside its tile (an arterial chain that never meets a main road, a main road
 * of a cell whose own routes failed) from carrying buildings that cannot be reached. A pure function of the source: every
 * tile of the settlement reads the same network, in any order.
 *
 * <p>Cost: all member skeletons (a handful of cells), the arterial rule over the planned area, and two bit rasters over the
 * planned box (a metropolis box of 3000 blocks is about 1.1 MB each).
 */
public final class RoadNetwork {
	/** Planned tiles, keyed {@code ix << 32 | iz}. */
	private final TreeSet<Long> planned;
	private final int x0, z0, w, d;
	private final BitSet live;
	private final Pt hub;

	private RoadNetwork(TreeSet<Long> planned, int x0, int z0, int w, int d, BitSet live, Pt hub) {
		this.planned = planned;
		this.x0 = x0;
		this.z0 = z0;
		this.w = w;
		this.d = d;
		this.live = live;
		this.hub = hub;
	}

	static long key(int ix, int iz) { return ((long) ix << 32) ^ (iz & 0xffffffffL); }

	/** The tile is one of the settlement's planned districts. */
	public boolean planned(int ix, int iz) { return planned.contains(key(ix, iz)); }

	/** The column lies on a skeleton road or arterial centre line that reaches the hub through drawn roads. */
	public boolean live(int x, int z) {
		int i = x - x0, j = z - z0;
		return i >= 0 && j >= 0 && i < w && j < d && live.get(j * w + i);
	}

	/** The hub column the network is flooded from (null when the centre cell has no hub). */
	public Pt hub() { return hub; }

	/**
	 * Where the centre cell's roads start: the first point of its first main road or path (a village lays its street beside the hub
	 * column, so that column may be far from any road), else its hub.
	 */
	public static Pt roadHub(CellSkeleton sk) {
		for (Road r : sk.roads()) if (r.kind() == RoadKind.MAIN || r.kind() == RoadKind.PATH) return r.points().get(0);
		return sk.hub();
	}

	/** Builds the network of a settlement from its skeletons and arterial rule. */
	public static RoadNetwork build(SkeletonSource src) {
		DistrictGrid grid = src.grid();
		TreeSet<Long> planned = new TreeSet<>();
		List<Cell> cells = new ArrayList<>(src.site().cells());
		cells.sort(CellGeometry::compare);
		List<CellSkeleton> sk = new ArrayList<>();
		for (Cell c : cells) sk.add(src.skeleton(c));
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (CellSkeleton s : sk) {
			for (DistrictSummary ds : s.districts()) {
				planned.add(key(ds.ix(), ds.iz()));
				minX = Math.min(minX, ds.x());
				minZ = Math.min(minZ, ds.z());
				maxX = Math.max(maxX, ds.x() + ds.w());
				maxZ = Math.max(maxZ, ds.z() + ds.d());
			}
		}
		Cell centreCell = CellGeometry.owner(src.view().field(), src.site().centre().x(), src.site().centre().z());
		Pt hub = src.site().cells().contains(centreCell) ? roadHub(src.skeleton(centreCell)) : null;
		if (planned.isEmpty() || hub == null) return new RoadNetwork(planned, 0, 0, 0, 0, new BitSet(), hub);
		int pad = Districts.MARGIN + 2;
		int x0 = minX - pad, z0 = minZ - pad, w = maxX - minX + 2 * pad, d = maxZ - minZ + 2 * pad;
		BitSet on = new BitSet(w * d);
		Painter p = new Painter(grid, planned, on, x0, z0, w, d);
		for (CellSkeleton s : sk) {
			for (Road r : s.roads()) {
				if (r.kind() == RoadKind.MAIN || r.kind() == RoadKind.PATH) p.paint(StreetCols.columns(r.points()));
			}
		}
		if (src.site().tier().atLeast(Tier.CITY)) {
			Arterials art = src.density().arterials();
			for (DistrictGrid.Segment s : art.segmentsIn(x0, z0, x0 + w - 1, z0 + d - 1)) {
				if (art.is(s)) p.paint(StreetCols.columns(art.points(s, s.from(), s.to())));
			}
		}
		BitSet live = new BitSet(w * d);
		int start = -1;
		long best = Long.MAX_VALUE;
		for (int dz = -24; dz <= 24; dz++) {
			for (int dx = -24; dx <= 24; dx++) {
				int i = hub.x() + dx - x0, j = hub.z() + dz - z0;
				if (i < 0 || j < 0 || i >= w || j >= d || !on.get(j * w + i)) continue;
				long dd = (long) dx * dx + (long) dz * dz;
				if (dd < best) { best = dd; start = j * w + i; }
			}
		}
		if (start >= 0) {
			int[] stack = new int[1024];
			int sp = 0;
			stack[sp++] = start;
			live.set(start);
			while (sp > 0) {
				int c = stack[--sp], i = c % w, j = c / w;
				for (int dj = -1; dj <= 1; dj++) {
					for (int di = -1; di <= 1; di++) {
						int ni = i + di, nj = j + dj;
						if ((di == 0 && dj == 0) || ni < 0 || nj < 0 || ni >= w || nj >= d) continue;
						int k = nj * w + ni;
						if (!on.get(k) || live.get(k)) continue;
						live.set(k);
						if (sp == stack.length) stack = java.util.Arrays.copyOf(stack, sp * 2);
						stack[sp++] = k;
					}
				}
			}
		}
		return new RoadNetwork(planned, x0, z0, w, d, live, hub);
	}

	/** Paints centre-line columns that some planned tile draws (its tile widened by {@link Districts#MARGIN}, {@code maxX} exclusive so the window ends at {@code maxX + MARGIN}). */
	private static final class Painter {
		final BitSet on, drawn;
		final int x0, z0, w, d;

		Painter(DistrictGrid grid, TreeSet<Long> planned, BitSet on, int x0, int z0, int w, int d) {
			this.on = on;
			this.x0 = x0;
			this.z0 = z0;
			this.w = w;
			this.d = d;
			this.drawn = new BitSet(w * d);
			int m = Districts.MARGIN;
			for (long k : planned) {
				DistrictGrid.Tile t = grid.tile((int) (k >> 32), (int) k);
				int ia = Math.max(0, t.minX() - m - x0), ib = Math.min(w - 1, t.maxX() + m - x0);
				for (int j = Math.max(0, t.minZ() - m - z0); j <= Math.min(d - 1, t.maxZ() + m - z0); j++) drawn.set(j * w + ia, j * w + ib + 1);
			}
		}

		void paint(List<int[]> cols) {
			for (int[] c : cols) {
				int i = c[0] - x0, j = c[1] - z0;
				if (i >= 0 && j >= 0 && i < w && j < d && drawn.get(j * w + i)) on.set(j * w + i);
			}
		}
	}
}
