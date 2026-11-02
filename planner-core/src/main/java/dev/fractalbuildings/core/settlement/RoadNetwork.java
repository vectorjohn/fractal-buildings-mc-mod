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
 * <p>The lattice (ROADS2). A tile-border segment is <i>eligible</i> when it is an arterial candidate, or when two of its three sample points
 * are inside the footprint, dry and not a peak, it crosses no water run over 40 blocks, it rises at most 1 in 2 and it does not cut the old-town wall.
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

	/**
	 * THINDECK: the border segment lies between a planned tile and a tile the settlement does not plan, so only one tile builds it (half the
	 * street, and none of a deck's centre-line piers).
	 */
	public boolean oneSided(DistrictGrid.Segment s) {
		boolean xAxis = s.axis() == DistrictGrid.AXIS_X;
		int kx = xAxis ? s.line() : s.index(), kz = xAxis ? s.index() : s.line();
		return !(xAxis ? planned.contains(key(kx - 1, kz)) && planned.contains(key(kx, kz)) : planned.contains(key(kx, kz - 1)) && planned.contains(key(kx, kz)));
	}

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

	/** Grid lines yield to a skeleton main road this near (centre to centre) running the same way (METROFILL R2). 0 = off: at 24 it saved no sliver (28 -> 29 on the 9 gate windows) and exposed a latent tile-border disagreement (DistrictsTest case 8: a skeleton square within 8 of the border clips a main road in one tile only). */
	static final int GRID_YIELD = 0;
	/**
	 * Plain border streets yield to a parallel main road this near (METROFILL R2). 0 = off, byte-neutral: at 16 the street's lots and the
	 * streets that branched off it were lost (city-check lot-loss guard: 12 districts of density 50+ lose lots on the gate windows, coast-city
	 * dm46_m22 28 -> 0 before the cascade test); the frontage hand-over to the main road (re-rooting, re-fronting lots) is not built. See
	 * docs/review/metrofill/fill-r2-yield16.md.
	 */
	static final int LATTICE_YIELD = 0;
	static final int ARTERIAL_YIELD = 0;

	/** Test hook: a network holding only the main-road rasters. */
	RoadNetwork(Mains m) {
		this(new TreeSet<>(), 0, 0, 0, 0, new BitSet(), null, new TreeMap<>());
		this.mains = m;
	}

	/**
	 * Do at least half the {@link StreetRules#NODE}-block nodes of the stretch {@code from..to} of the line of constant coordinate {@code pos}
	 * ({@code axis} as in {@link DistrictGrid}: AXIS_X is a line of constant x) have a skeleton main road running the same way within
	 * {@code reach} across it? A main road crossing the line at right angles does not count. Pointwise (a raster lookup).
	 */
	public boolean parallelMain(int axis, int pos, int from, int to, int reach) {
		return mains != null && reach > 0 && mains.parallel(axis, pos, from, to, reach);
	}

	/** Skeleton main-road centre lines by run direction (R2), null without a lattice. */
	private Mains mains;

	/** Main-road centre lines split by run direction: {@code alongZ} columns belong to roads that run along z (parallel to lines of constant x). */
	static final class Mains {
		final BitSet alongX, alongZ;
		final int x0, z0, w, d;

		Mains(int x0, int z0, int w, int d) {
			this.x0 = x0;
			this.z0 = z0;
			this.w = w;
			this.d = d;
			alongX = new BitSet(w * d);
			alongZ = new BitSet(w * d);
		}

		void add(List<int[]> cols) {
			for (int k = 0; k < cols.size(); k++) {
				int[] a = cols.get(Math.max(0, k - 2)), b = cols.get(Math.min(cols.size() - 1, k + 2)), c = cols.get(k);
				int dx = Math.abs(b[0] - a[0]), dz = Math.abs(b[1] - a[1]);
				int i = c[0] - x0, j = c[1] - z0;
				if (i < 0 || j < 0 || i >= w || j >= d) continue;
				if (dx > 2 * dz) alongX.set(j * w + i);
				else if (dz > 2 * dx) alongZ.set(j * w + i);
			}
		}

		boolean parallel(int axis, int pos, int from, int to, int reach) {
			boolean vertical = axis == DistrictGrid.AXIS_X;
			BitSet runs = vertical ? alongZ : alongX;
			int nodes = 0, hit = 0;
			for (int t = Math.floorDiv(from + StreetRules.NODE - 1, StreetRules.NODE) * StreetRules.NODE; t <= to; t += StreetRules.NODE) {
				nodes++;
				boolean found = false;
				for (int o = -reach; o <= reach && !found; o++) {
					int i = (vertical ? pos + o : t) - x0, j = (vertical ? t : pos + o) - z0;
					if (i >= 0 && j >= 0 && i < w && j < d && runs.get(j * w + i)) found = true;
				}
				if (found) hit++;
			}
			return nodes > 0 && 2 * hit >= nodes;
		}
	}

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
	private record Seg(DistrictGrid.Segment s, String key, long n0, long n1, List<int[]> cols, boolean arterial, boolean yields) {}

	/** The segments linked to the hub through the mains and each other, leaving out those in {@code off}; {@code liveOut[0]} gets the flooded raster. */
	private static boolean[] fixpoint(List<Seg> segs, TreeMap<Long, List<Integer>> atNode, boolean[] off, BitSet mains, Painter p, Pt hub, int x0, int z0, int w, int d, BitSet[] liveOut) {
		boolean[] in = new boolean[segs.size()];
		while (true) {
			BitSet on = (BitSet) mains.clone();
			for (int i = 0; i < segs.size(); i++) if (in[i]) p.paint(on, segs.get(i).cols());
			BitSet live = flood(on, hub, x0, z0, w, d);
			if (liveOut != null) liveOut[0] = live;
			List<Integer> stack = new ArrayList<>();
			for (int i = 0; i < segs.size(); i++) if (!off[i] && !in[i] && touches(live, segs.get(i).cols(), x0, z0, w, d)) { in[i] = true; stack.add(i); }
			if (stack.isEmpty()) return in;
			while (!stack.isEmpty()) {
				Seg s = segs.get(stack.remove(stack.size() - 1));
				for (long n : new long[] {s.n0(), s.n1()}) {
					for (int j : atNode.get(n)) if (!off[j] && !in[j]) { in[j] = true; stack.add(j); }
				}
			}
		}
	}

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
		Mains dirs = new Mains(x0, z0, w, d);
		Painter p = new Painter(grid, planned, x0, z0, w, d);
		for (CellSkeleton s : sk) {
			for (Road r : s.roads()) {
				if (r.kind() == RoadKind.MAIN || r.kind() == RoadKind.PATH) {
					List<int[]> mc = StreetCols.columns(r.points());
					p.paint(mains, mc);
					if (r.kind() == RoadKind.MAIN) dirs.add(mc);
				}
			}
		}
		TreeMap<String, Boolean> present = new TreeMap<>();
		TreeSet<String> arterialIds = new TreeSet<>();
		boolean lattice = latticeTier(src.site().tier());
		BitSet live;
		State state = null;
		if (!lattice) {
			live = flood(mains, hub, x0, z0, w, d);
		} else {
			List<Seg> segs = eligible(src, planned, x0, z0, w, d, dirs);
			TreeMap<Long, List<Integer>> atNode = new TreeMap<>();
			for (int i = 0; i < segs.size(); i++) {
				atNode.computeIfAbsent(segs.get(i).n0(), k -> new ArrayList<>()).add(i);
				atNode.computeIfAbsent(segs.get(i).n1(), k -> new ArrayList<>()).add(i);
			}
			// METROFILL R2: a segment beside a parallel main road yields only when that costs no other segment its link to the hub
			// (the cascade that emptied whole tiles): candidates in key order, each tried on top of the ones already accepted
			boolean[] off = new boolean[segs.size()];
			boolean[] all = fixpoint(segs, atNode, off, mains, p, hub, x0, z0, w, d, null);
			for (int c = 0; c < segs.size(); c++) {
				if (!segs.get(c).yields()) continue;
				off[c] = true;
				boolean[] t = fixpoint(segs, atNode, off, mains, p, hub, x0, z0, w, d, null);
				for (int i = 0; i < segs.size() && off[c]; i++) if (all[i] && !t[i] && !off[i]) off[c] = false;
			}
			BitSet[] lv = new BitSet[1];
			boolean[] in = fixpoint(segs, atNode, off, mains, p, hub, x0, z0, w, d, lv);
			live = lv[0];
			collect(segs, in, present, arterialIds);
			state = new State(segs, atNode, off, mains, p, src.density().arterials().width());
		}
		RoadNetwork n = new RoadNetwork(planned, x0, z0, w, d, live, hub, present);
		n.lattice = lattice;
		n.mains = dirs;
		n.arterialIds = arterialIds;
		n.state = state;
		return n;
	}

	private static void collect(List<Seg> segs, boolean[] in, TreeMap<String, Boolean> present, TreeSet<String> arterialIds) {
		for (int i = 0; i < segs.size(); i++) {
			if (!in[i]) continue;
			Seg s = segs.get(i);
			present.put(s.key(), s.arterial());
			if (s.arterial()) arterialIds.add(Ids.arterial(s.s().axis(), s.s().line(), s.s().from()));
		}
	}

	/** What {@link #withWater} needs from the build: the eligible segments, their corner index, the yields decided, the main-road raster, the painter and the arterial width. */
	private record State(List<Seg> segs, TreeMap<Long, List<Integer>> atNode, boolean[] off, BitSet mains, Painter painter, int arterialWidth) {}

	/** Null without a lattice, and on a network {@link #withWater} made. */
	private State state;

	/** THINDECK: present one-sided segments drawn inside their planned tile, by key: the shift of the centre line across it. */
	private final TreeMap<String, Integer> insets = new TreeMap<>();
	/** The same shifts for the inset arterials, by arterial id ({@link Ids#arterial}). */
	private final TreeMap<String, Integer> arterialInsets = new TreeMap<>();

	/**
	 * THINDECK: the network over the exact terrain. A lattice segment on the edge of the planned area ({@link #oneSided}) is built by its
	 * planned tile alone, which clips it at the tile border: centred on the border it would be half a street, and over water half a deck
	 * whose centre-line piers nobody builds. So a one-sided segment whose centre line has water (the exact probe, every column) is never
	 * drawn on its line. One that crosses more than {@link #SHORT_CROSSING} wet columns in a row (a lake, as on the THINDECK playtest
	 * spot) is dropped, unless dropping it costs another segment its link to the hub (the test METROFILL R2 uses for yields: candidates
	 * in key order, each tried on top of those already dropped; round 1 dropped them all and cut a dry quarter of the coast city off).
	 * Every other wet one-sided segment, a short crossing or one something hangs on, is drawn whole inside its planned tile
	 * ({@link #inset}): the full width, its centre line and its piers, a real bridge. Dry one-sided segments are unchanged.
	 *
	 * <p>A pure function of the network and the exact terrain (the coarse lattice samples every 32 blocks and misses a pond between
	 * them). Returns this network when no one-sided segment is wet.
	 */
	public RoadNetwork withWater(dev.fractalbuildings.core.probe.TerrainProbe exact) {
		State st = state;
		if (!lattice || st == null) return this;
		List<Seg> segs = new ArrayList<>(st.segs());
		List<Integer> wet = new ArrayList<>();
		int[] shift = new int[segs.size()];
		boolean[] long_ = new boolean[segs.size()];
		for (int i = 0; i < segs.size(); i++) {
			Seg s = segs.get(i);
			int run = oneSided(s.s()) ? wetRun(s.cols(), exact) : 0;
			if (run == 0) continue;
			long_[i] = run > SHORT_CROSSING;
			shift[i] = insetShift(s.s(), s.arterial() ? st.arterialWidth() : StreetRules.BORDER_WIDTH);
			segs.set(i, new Seg(s.s(), s.key(), s.n0(), s.n1(), band(s.cols(), s.s().axis(), shift[i]), s.arterial(), s.yields()));
			wet.add(i);
		}
		if (wet.isEmpty()) return this;
		boolean[] off = st.off().clone();
		boolean[] all = fixpoint(segs, st.atNode(), off, st.mains(), st.painter(), hub, x0, z0, w, d, null);
		for (int c : wet) {
			if (off[c] || !long_[c]) continue; // a short crossing stays: an ordinary small bridge, whole
			off[c] = true;
			boolean[] t = fixpoint(segs, st.atNode(), off, st.mains(), st.painter(), hub, x0, z0, w, d, null);
			for (int i = 0; i < segs.size() && off[c]; i++) if (all[i] && !t[i] && !off[i]) off[c] = false;
		}
		BitSet[] lv = new BitSet[1];
		boolean[] in = fixpoint(segs, st.atNode(), off, st.mains(), st.painter(), hub, x0, z0, w, d, lv);
		TreeMap<String, Boolean> pres = new TreeMap<>();
		TreeSet<String> arts = new TreeSet<>();
		collect(segs, in, pres, arts);
		RoadNetwork n = new RoadNetwork(planned, x0, z0, w, d, lv[0], hub, pres);
		n.lattice = true;
		n.mains = mains;
		n.arterialIds = arts;
		for (int c : wet) {
			if (!in[c]) continue;
			Seg s = segs.get(c);
			n.insets.put(s.key(), shift[c]);
			if (s.arterial()) n.arterialInsets.put(Ids.arterial(s.s().axis(), s.s().line(), s.s().from()), shift[c]);
		}
		return n;
	}

	/** Wet edge streets whose longest run of wet centre-line columns is at most this (DS-19's longest crossing) are kept, drawn whole as a small bridge: dropping them would cost their lots for nothing. */
	static final int SHORT_CROSSING = 20;

	/** The longest run of consecutive centre-line columns over water (exact), 0 when the line is dry. */
	private static int wetRun(List<int[]> cols, dev.fractalbuildings.core.probe.TerrainProbe exact) {
		int run = 0, max = 0;
		for (int[] c : cols) {
			run = exact.waterDepth(c[0], c[1]) > 0 ? run + 1 : 0;
			max = Math.max(max, run);
		}
		return max;
	}

	/**
	 * The shift across its line that puts a street of this width on a one-sided segment wholly inside its planned tile. A tile owns
	 * {@code [min, max)}, so the line belongs to the tile on its high side; the footprint of centre {@code c} is {@code c - width/2} to
	 * {@code c - width/2 + width - 1}.
	 */
	int insetShift(DistrictGrid.Segment s, int width) {
		boolean xAxis = s.axis() == DistrictGrid.AXIS_X;
		int kx = xAxis ? s.line() : s.index(), kz = xAxis ? s.index() : s.line();
		return planned.contains(key(kx, kz)) ? width / 2 : -(width - width / 2);
	}

	/** The centre-line columns and every column between them and the shifted line (the raster link from the border to the drawn street). */
	private static List<int[]> band(List<int[]> cols, int axis, int shift) {
		List<int[]> out = new ArrayList<>();
		int step = shift > 0 ? 1 : -1;
		for (int[] c : cols) {
			for (int o = 0; o != shift + step; o += step) out.add(axis == DistrictGrid.AXIS_X ? new int[] {c[0] + o, c[1]} : new int[] {c[0], c[1] + o});
		}
		return out;
	}


	/**
	 * A skeleton arterial road ({@code Skeletons}, id {@link Ids#arterial}, points on its tile border) as district detail draws it: moved
	 * across its line by the segment's {@link #inset}, or unchanged.
	 */
	public Road drawn(Road r) {
		Integer o = r.kind() == RoadKind.ARTERIAL ? arterialInsets.get(r.id()) : null;
		if (o == null || r.points().isEmpty()) return r;
		boolean alongZ = true;
		for (Pt p : r.points()) alongZ &= p.x() == r.points().get(0).x();
		List<Pt> pts = new ArrayList<>();
		for (Pt p : r.points()) pts.add(alongZ ? new Pt(p.x() + o, p.z()) : new Pt(p.x(), p.z() + o));
		return new Road(r.id(), r.kind(), r.width(), pts, r.bridges());
	}
	/** THINDECK: how far across its line a present border segment is drawn ({@link #withWater}); 0 for every segment drawn on its line. */
	public int inset(DistrictGrid.Segment s) {
		Integer o = insets.get(key(s));
		return o == null ? 0 : o;
	}

	/** The centre line a present border segment is drawn on: {@link Arterials#points} moved across by {@link #inset}. */
	public List<Pt> points(Arterials art, DistrictGrid.Segment s) {
		List<Pt> p = art.points(s, s.from(), s.to());
		int o = inset(s);
		if (o == 0) return p;
		List<Pt> out = new ArrayList<>();
		for (Pt q : p) out.add(s.axis() == DistrictGrid.AXIS_X ? new Pt(q.x() + o, q.z()) : new Pt(q.x(), q.z() + o));
		return out;
	}

	/** Largest rise over 32 blocks along a plain lattice street (1 in 2; it was 1 in 4 before CITYQ, which cut hilly tiles off the hub; arterials keep the arterial rule's 3 in 4). */
	public static final int STREET_STEEP_PER_32 = 16;

	/** A plain lattice street needs this many of its three sample points (both ends and the middle) to be {@link #streetPoint}s. */
	public static final int URBAN_POINTS = 2;

	/** Longest distance between a lattice segment's centre line and a live column for the segment to count as touching it (the two footprints meet). */
	private static final int TOUCH = 3;

	/** A lattice sample point a plain street may count (CITYQ): inside the footprint, dry, not a peak. Unlike the urban test it ignores the 4-neighbour steepness: the grade test along the street ({@link #STREET_STEEP_PER_32}) judges slope, and counting it twice left urban tiles on rolling ground with no street. */
	static boolean streetPoint(DensityField den, int x, int z) {
		CoarseTerrain t = den.terrain();
		return den.inside(x, z) && !t.water(x, z) && !t.peak(x, z);
	}

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
	private static List<Seg> eligible(SkeletonSource src, TreeSet<Long> planned, int x0, int z0, int w, int d, Mains dirs) {
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
			// R2: a main road within reach running the same way takes the segment's place (arterial or plain)
			int reach = arterial ? ARTERIAL_YIELD : LATTICE_YIELD;
			boolean yields = reach > 0 && src.site().tier().atLeast(Tier.CITY) && dirs.parallel(s.axis(), s.fixed(), s.from(), s.to(), reach);
			if (!arterial) {
				int mid = Math.floorDiv(s.from() + s.to(), 2), urban = 0;
				for (int q : new int[] {s.from(), mid, s.to()}) if (streetPoint(den, Arterials.ax(s, q), Arterials.az(s, q))) urban++;
				if (urban < URBAN_POINTS) continue;
				if (city && den.oldTown().cuts(s)) continue;
				if (!art.waterOk(s) || !art.gradeOk(s, STREET_STEEP_PER_32)) continue;
			}
			long n0 = key(kx, kz), n1 = xAxis ? key(kx, kz + 1) : key(kx + 1, kz);
			List<int[]> all = StreetCols.columns(art.points(s, s.from(), s.to()));
			if (!arterial && hitsWall(all, StreetRules.BORDER_WIDTH, walls)) continue;
			List<int[]> cols = new ArrayList<>();
			for (int[] c : all) if (p.drawn(c[0], c[1])) cols.add(c);
			out.add(new Seg(s, key(s), n0, n1, cols, arterial, yields));
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
