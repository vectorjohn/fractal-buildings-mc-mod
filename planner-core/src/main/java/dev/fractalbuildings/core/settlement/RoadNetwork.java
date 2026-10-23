package dev.fractalbuildings.core.settlement;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The settlement's road network as district detail will draw it (ROADS, ROADS2): every main road and path of every member cell
 * and, in a city or metropolis, the <i>street lattice</i> on the tile borders, kept to the columns some planned tile draws (its
 * tile plus {@link Districts#MARGIN}), as centre lines on a block raster, and the part of it that reaches the hub (8-connected,
 * from the hub column of the cell that holds the site centre).
 *
 * <p>The lattice (ROADS2). A tile-border segment is <i>eligible</i> when it is an arterial candidate, or when both its ends are
 * urban, it crosses no water run over 40 blocks, it is no steeper than an arterial may be and it does not cut the old-town wall.
 * A segment is <i>present</i> (drawn: an arterial when the arterial rule picked it, else a plain street) when a chain of eligible
 * segments meeting at tile corners links it to a live road (a main road, a path or a present segment that reaches the hub). So every
 * arterial and every border street is attached to the settlement's network, a chain of arterials that never met a main road is
 * linked to it by plain streets along the lattice (or, when the lattice offers no way, not drawn), and the built-up area beyond the
 * arterials has streets to front. Eligible segments no chain reaches are absent.
 *
 * <p>District detail lets lots front a street only when the street's piece in its tile holds a column of a live root. Deciding it
 * from the whole network rather than per tile is what keeps a fragment that looks fine inside its tile from carrying buildings that
 * cannot be reached. A pure function of the source: every tile of the settlement reads the same network, in any order.
 *
 * <p>Cost: all member skeletons (a handful of cells), the arterial rule over the planned area, and two bit rasters over the
 * planned box (a metropolis box of 3000 blocks is about 1.1 MB each), flooded a few times.
 */
public final class RoadNetwork {
	/** Planned tiles, keyed {@code ix << 32 | iz}. */
	private final TreeSet<Long> planned;
	private final int x0, z0, w, d;
	private final BitSet live;
	private final Pt hub;
	/** Present border segments by key ({@code axis:line:index}): true when the arterial rule picked it (an arterial), false for a plain street. */
	private final TreeMap<String, Boolean> present;

	private RoadNetwork(TreeSet<Long> planned, int x0, int z0, int w, int d, BitSet live, Pt hub, TreeMap<String, Boolean> present) {
		this.planned = planned;
		this.x0 = x0;
		this.z0 = z0;
		this.w = w;
		this.d = d;
		this.live = live;
		this.hub = hub;
		this.present = present;
	}

	/** Tiers whose tile borders form the street lattice (ROADS2). Hamlets and villages lay a street and no tile grid. */
	public static boolean latticeTier(Tier t) { return t.atLeast(Tier.TOWN); }

	static long key(int ix, int iz) { return ((long) ix << 32) ^ (iz & 0xffffffffL); }

	static String key(DistrictGrid.Segment s) { return s.axis() + ":" + s.line() + ":" + s.index(); }

	/** The tile is one of the settlement's planned districts. */
	public boolean planned(int ix, int iz) { return planned.contains(key(ix, iz)); }

	/** The column lies on a skeleton road or lattice centre line that reaches the hub through drawn roads. */
	public boolean live(int x, int z) {
		int i = x - x0, j = z - z0;
		return i >= 0 && j >= 0 && i < w && j < d && live.get(j * w + i);
	}

	/** Does some column of the road lie on the live network (a road with none reaches no hub and is not drawn)? */
	public boolean reaches(Road r) {
		for (int[] c : StreetCols.columns(r.points())) if (live(c[0], c[1])) return true;
		return false;
	}

	/** The box the network covers (the planned tiles plus {@link Districts#MARGIN} + 2): {x0, z0, w, d}, all zero without planned tiles or a hub. */
	int[] box() { return new int[] {x0, z0, w, d}; }

	/** The hub column the network is flooded from (null when the centre cell has no hub). */
	public Pt hub() { return hub; }

	/** True when the network has a street lattice at all (a city or metropolis with a hub and planned tiles). */
	public boolean hasLattice() { return lattice; }

	private boolean lattice;

	/** What the tile-border segment is drawn as: {@link RoadKind#ARTERIAL}, {@link RoadKind#STREET}, or null for none. */
	public RoadKind kind(DistrictGrid.Segment s) {
		Boolean a = present.get(key(s));
		return a == null ? null : a ? RoadKind.ARTERIAL : RoadKind.STREET;
	}

	/** Is the arterial segment with this id ({@link Ids#arterial}) part of the network (an arterial that is attached)? */
	public boolean arterialPresent(String id) { return arterialIds.contains(id); }

	private TreeSet<String> arterialIds = new TreeSet<>();

	/**
	 * Where the centre cell's roads start: the first point of its first main road or path (a village lays its street beside the hub
	 * column, so that column may be far from any road), else its hub.
	 */
	public static Pt roadHub(CellSkeleton sk) {
		for (Road r : sk.roads()) if (r.kind() == RoadKind.MAIN || r.kind() == RoadKind.PATH) return r.points().get(0);
		return sk.hub();
	}

	/** An eligible lattice segment: its key, tile corners, centre-line columns and whether it is an arterial. */
	private record Seg(DistrictGrid.Segment s, String key, long n0, long n1, List<int[]> cols, boolean arterial) {}

	/** Builds the network of a settlement from its skeletons and the arterial rule. */
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
		if (planned.isEmpty() || hub == null) return new RoadNetwork(planned, 0, 0, 0, 0, new BitSet(), hub, new TreeMap<>());
		int pad = Districts.MARGIN + 2;
		int x0 = minX - pad, z0 = minZ - pad, w = maxX - minX + 2 * pad, d = maxZ - minZ + 2 * pad;
		BitSet mains = new BitSet(w * d);
		Painter p = new Painter(grid, planned, x0, z0, w, d);
		for (CellSkeleton s : sk) {
			for (Road r : s.roads()) {
				if (r.kind() == RoadKind.MAIN || r.kind() == RoadKind.PATH) p.paint(mains, StreetCols.columns(r.points()));
			}
		}
		TreeMap<String, Boolean> present = new TreeMap<>();
		TreeSet<String> arterialIds = new TreeSet<>();
		boolean lattice = latticeTier(src.site().tier());
		BitSet live;
		if (!lattice) {
			live = flood(mains, hub, x0, z0, w, d);
		} else {
			List<Seg> segs = eligible(src, planned, x0, z0, w, d);
			TreeMap<Long, List<Integer>> atNode = new TreeMap<>();
			for (int i = 0; i < segs.size(); i++) {
				atNode.computeIfAbsent(segs.get(i).n0(), k -> new ArrayList<>()).add(i);
				atNode.computeIfAbsent(segs.get(i).n1(), k -> new ArrayList<>()).add(i);
			}
			boolean[] in = new boolean[segs.size()];
			while (true) {
				BitSet on = (BitSet) mains.clone();
				for (int i = 0; i < segs.size(); i++) if (in[i]) p.paint(on, segs.get(i).cols());
				live = flood(on, hub, x0, z0, w, d);
				List<Integer> stack = new ArrayList<>();
				for (int i = 0; i < segs.size(); i++) if (!in[i] && touches(live, segs.get(i).cols(), x0, z0, w, d)) { in[i] = true; stack.add(i); }
				if (stack.isEmpty()) break;
				while (!stack.isEmpty()) {
					Seg s = segs.get(stack.remove(stack.size() - 1));
					for (long n : new long[] {s.n0(), s.n1()}) {
						for (int j : atNode.get(n)) if (!in[j]) { in[j] = true; stack.add(j); }
					}
				}
			}
			for (int i = 0; i < segs.size(); i++) {
				if (!in[i]) continue;
				Seg s = segs.get(i);
				present.put(s.key(), s.arterial());
				if (s.arterial()) arterialIds.add(Ids.arterial(s.s().axis(), s.s().line(), s.s().from()));
			}
		}
		RoadNetwork n = new RoadNetwork(planned, x0, z0, w, d, live, hub, present);
		n.lattice = lattice;
		n.arterialIds = arterialIds;
		return n;
	}

	/** Largest rise over 32 blocks along a plain lattice street (1 in 4; arterials keep the arterial rule's 3 in 4). */
	public static final int STREET_STEEP_PER_32 = 8;

	/** A plain lattice street needs this many of its three sample points (both ends and the middle) urban. */
	public static final int URBAN_POINTS = 2;

	/** Longest distance between a lattice segment's centre line and a live column for the segment to count as touching it (the two footprints meet). */
	private static final int TOUCH = 3;

	private static boolean touches(BitSet live, List<int[]> cols, int x0, int z0, int w, int d) {
		for (int k = 0; k < cols.size(); k += 2) {
			int[] c = cols.get(k);
			for (int dz = -TOUCH; dz <= TOUCH; dz++) {
				for (int dx = -TOUCH; dx <= TOUCH; dx++) {
					int i = c[0] + dx - x0, j = c[1] + dz - z0;
					if (i >= 0 && j >= 0 && i < w && j < d && live.get(j * w + i)) return true;
				}
			}
		}
		return false;
	}

	/** The eligible border segments of the planned area, in (axis, line, index) order, with the columns the drawn windows keep. */
	private static List<Seg> eligible(SkeletonSource src, TreeSet<Long> planned, int x0, int z0, int w, int d) {
		Arterials art = src.density().arterials();
		DensityField den = src.density();
		boolean city = src.site().tier() == Tier.CITY;
		List<Seg> out = new ArrayList<>();
		List<Rect> walls = wallBlocks(src);
		Painter p = new Painter(src.grid(), planned, x0, z0, w, d);
		for (DistrictGrid.Segment s : art.segmentsIn(x0, z0, x0 + w - 1, z0 + d - 1)) {
			boolean xAxis = s.axis() == DistrictGrid.AXIS_X;
			int kx = xAxis ? s.line() : s.index(), kz = xAxis ? s.index() : s.line();
			boolean adj = xAxis ? planned.contains(key(kx - 1, kz)) || planned.contains(key(kx, kz)) : planned.contains(key(kx, kz - 1)) || planned.contains(key(kx, kz));
			if (!adj) continue;
			boolean arterial = art.is(s);
			if (!arterial) {
				int mid = Math.floorDiv(s.from() + s.to(), 2), urban = 0;
				for (int q : new int[] {s.from(), mid, s.to()}) if (den.urban(Arterials.ax(s, q), Arterials.az(s, q))) urban++;
				if (urban < URBAN_POINTS) continue;
				if (city && den.oldTown().cuts(s)) continue;
				if (!art.waterOk(s) || !art.gradeOk(s, STREET_STEEP_PER_32)) continue;
			}
			long n0 = key(kx, kz), n1 = xAxis ? key(kx, kz + 1) : key(kx + 1, kz);
			List<int[]> all = StreetCols.columns(art.points(s, s.from(), s.to()));
			if (!arterial && hitsWall(all, StreetRules.BORDER_WIDTH, walls)) continue;
			List<int[]> cols = new ArrayList<>();
			for (int[] c : all) if (p.drawn(c[0], c[1])) cols.add(c);
			out.add(new Seg(s, key(s), n0, n1, cols, arterial));
		}
		return out;
	}

	/** The rectangles the wall ring reserves (wall pieces and their gate-less stretches), as district detail reserves them: no street crosses them. */
	private static List<Rect> wallBlocks(SkeletonSource src) {
		List<Rect> out = new ArrayList<>();
		Cell centreCell = CellGeometry.owner(src.view().field(), src.site().centre().x(), src.site().centre().z());
		if (!src.site().cells().contains(centreCell)) return out;
		CellSkeleton sk = src.skeleton(centreCell);
		for (Wall w : sk.walls()) {
			List<Pt> gates = new ArrayList<>();
			for (Gate g : sk.gates()) if (g.kind() == GateKind.WALL) gates.add(new Pt(g.x(), g.z()));
			for (dev.fractalbuildings.core.settlement.tiers.WallRing.Piece pc : dev.fractalbuildings.core.settlement.tiers.WallRing.pieces(w, gates, src.grid(), src.seed())) {
				out.addAll(dev.fractalbuildings.core.settlement.tiers.WallRing.blocked(pc));
			}
		}
		return out;
	}

	private static boolean hitsWall(List<int[]> cols, int width, List<Rect> walls) {
		if (walls.isEmpty()) return false;
		for (int[] c : cols) for (Rect r : walls) if (StreetCols.footprintOverlaps(c, width, r)) return true;
		return false;
	}

	/** 8-connected flood of the bit raster from the set column nearest the hub within 24 blocks. */
	private static BitSet flood(BitSet on, Pt hub, int x0, int z0, int w, int d) {
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
		if (start < 0) return live;
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
		return live;
	}

	/** Paints centre-line columns that some planned tile draws (its tile widened by {@link Districts#MARGIN}, {@code maxX} exclusive so the window ends at {@code maxX + MARGIN}). */
	private static final class Painter {
		final BitSet drawn;
		final int x0, z0, w, d;

		Painter(DistrictGrid grid, TreeSet<Long> planned, int x0, int z0, int w, int d) {
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

		boolean drawn(int x, int z) {
			int i = x - x0, j = z - z0;
			return i >= 0 && j >= 0 && i < w && j < d && drawn.get(j * w + i);
		}

		void paint(BitSet on, List<int[]> cols) {
			for (int[] c : cols) {
				int i = c[0] - x0, j = c[1] - z0;
				if (i >= 0 && j >= 0 && i < w && j < d && drawn.get(j * w + i)) on.set(j * w + i);
			}
		}
	}
}
