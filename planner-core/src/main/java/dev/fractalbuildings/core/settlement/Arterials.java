package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.Tiers;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Arterial roads of a city or metropolis: tile-border segments (plan decision 2). A segment is arterial when
 * both its end samples are urban, it crosses no water run over 40 blocks and
 * {@code hash(seed, "arterial", segment key) < p(density at its midpoint)}. Every input is a pointwise function
 * of the segment, so the two tiles (and the two cells) sharing a border agree without talking.
 */
public final class Arterials {
	/** Longest water run an arterial may bridge, blocks. */
	public static final int MAX_BRIDGE = 40;
	/** Density bonus within {@link #REACH} blocks of an arterial. */
	public static final int BONUS = 8;
	public static final int REACH = 24;

	private final long seed;
	private final Tier tier;
	private final DensityField density;
	private final CoarseTerrain terrain;
	private final DistrictGrid grid;
	private final dev.fractalbuildings.core.cache.BoundedCache<String, Boolean> memo = new dev.fractalbuildings.core.cache.BoundedCache<>(4096); // pure memo, shared by threads

	Arterials(long seed, Tier tier, DensityField density, CoarseTerrain terrain, DistrictGrid grid) {
		this.seed = seed;
		this.tier = tier;
		this.density = density;
		this.terrain = terrain;
		this.grid = grid;
	}

	/** Carriageway width. */
	public int width() { return tier == Tier.METROPOLIS ? 11 : 9; }

	/** Probability that a segment of the given base density is arterial (1 at 80, 0.5 at 50, 0 at 30). */
	static double probability(int d) {
		if (d >= 80) return 1;
		if (d >= 50) return 0.5 + (d - 50) / 60.0;
		if (d > 30) return (d - 30) / 40.0;
		return 0;
	}

	static int ax(DistrictGrid.Segment s, int along) { return s.axis() == DistrictGrid.AXIS_X ? s.fixed() : along; }

	static int az(DistrictGrid.Segment s, int along) { return s.axis() == DistrictGrid.AXIS_X ? along : s.fixed(); }

	/**
	 * Is the segment an arterial? The pointwise rule ({@link #pointwise}), then the settlement's network pass ({@link #network}):
	 * arterial pieces cut off from the rest by the parity rule get a link back or are demoted.
	 */
	public boolean is(DistrictGrid.Segment s) {
		if (!tier.atLeast(Tier.CITY)) return false;
		return network().contains(key(s));
	}

	static String key(DistrictGrid.Segment s) { return s.axis() + ":" + s.line() + ":" + s.index(); }

	/** The pointwise arterial rule alone (before the network pass). */
	boolean pointwise(DistrictGrid.Segment s) {
		if (!tier.atLeast(Tier.CITY)) return false;
		return memo.get(key(s), k -> compute(s, true));
	}

	/** Longest link (in tile-border segments) the network pass restores to join an arterial piece to the main network. */
	public static final int MAX_LINK = 2;

	private volatile java.util.TreeSet<String> network;

	/**
	 * The settlement's arterials after the network pass (METROFILL R1, architect: "arterials form one network"). The pointwise rule's
	 * arterials split into connected pieces (segments meeting at tile corners); the piece with the most segments is the main network.
	 * Each other piece is joined back by the shortest chain of at most {@link #MAX_LINK} segments that pass every ground test of
	 * the rule (urban ends, density over 30, water, grade; not the line parity or the random draw), the piece with the shortest link first; a piece that cannot be joined is demoted to plain streets.
	 * A pure function of the seed and the site (every segment of the footprint's box, in key order), so every tile and cell agrees.
	 */
	java.util.TreeSet<String> network() {
		java.util.TreeSet<String> n = network;
		if (n == null) {
			synchronized (this) {
				n = network;
				if (n == null) network = n = buildNetwork();
			}
		}
		return n;
	}

	static long node(int kx, int kz) { return ((long) kx << 32) ^ (kz & 0xffffffffL); }

	/** The two tile corners a segment joins. */
	private static long[] nodes(DistrictGrid.Segment s) {
		return s.axis() == DistrictGrid.AXIS_X ? new long[] {node(s.line(), s.index()), node(s.line(), s.index() + 1)} : new long[] {node(s.index(), s.line()), node(s.index() + 1, s.line())};
	}

	private java.util.TreeSet<String> buildNetwork() {
		int r = density.maxRadius() + 64, cx = density.site().centre().x(), cz = density.site().centre().z();
		TreeMap<String, long[]> art = new TreeMap<>(), cand = new TreeMap<>();
		for (DistrictGrid.Segment s : segmentsIn(cx - r, cz - r, cx + r, cz + r)) {
			if (pointwise(s)) art.put(key(s), nodes(s));
			else if (compute(s, false)) cand.put(key(s), nodes(s));
		}
		return join(art, cand);
	}

	/**
	 * The network pass on a graph: {@code art} and {@code cand} map segment keys to the two corners they join (arterials, and segments a
	 * link may restore). Returns the arterials kept and the links added; see {@link #network}.
	 */
	static java.util.TreeSet<String> join(TreeMap<String, long[]> art, TreeMap<String, long[]> cand) {
		java.util.TreeSet<String> out = new java.util.TreeSet<>();
		// connected pieces of the arterials, by union of their corners
		TreeMap<Long, Long> parent = new TreeMap<>();
		for (long[] e : art.values()) union(parent, e[0], e[1]);
		TreeMap<Long, List<String>> byRoot = new TreeMap<>();
		for (var e : art.entrySet()) byRoot.computeIfAbsent(find(parent, e.getValue()[0]), k -> new ArrayList<>()).add(e.getKey());
		List<List<String>> pieces = new ArrayList<>(byRoot.values());
		pieces.sort(java.util.Comparator.<List<String>>comparingInt(p -> -p.size()).thenComparing(p -> p.get(0)));
		if (pieces.isEmpty()) return out;
		java.util.TreeSet<Long> main = new java.util.TreeSet<>();
		for (String k : pieces.get(0)) { out.add(k); for (long v : art.get(k)) main.add(v); }
		TreeMap<Long, List<String>> candAt = new TreeMap<>();
		for (var e : cand.entrySet()) for (long v : e.getValue()) candAt.computeIfAbsent(v, k -> new ArrayList<>()).add(e.getKey());
		List<List<String>> rest = new ArrayList<>(pieces.subList(1, pieces.size()));
		while (!rest.isEmpty()) {
			int bi = -1;
			List<String> bestLink = null;
			for (int i = 0; i < rest.size(); i++) {
				java.util.TreeSet<Long> from = new java.util.TreeSet<>();
				for (String k : rest.get(i)) for (long v : art.get(k)) from.add(v);
				List<String> link = link(from, main, cand, candAt);
				if (link != null && (bestLink == null || link.size() < bestLink.size())) { bi = i; bestLink = link; }
			}
			if (bi < 0) break; // the rest cannot be joined: demoted
			for (String k : rest.get(bi)) { out.add(k); for (long v : art.get(k)) main.add(v); }
			for (String k : bestLink) { out.add(k); for (long v : cand.get(k)) main.add(v); }
			rest.remove(bi);
		}
		return out;
	}

	/** The shortest chain (at most {@link #MAX_LINK}) of candidate segments from a corner of {@code from} to one of {@code to}; null when none. */
	private static List<String> link(java.util.TreeSet<Long> from, java.util.TreeSet<Long> to, TreeMap<String, long[]> cand, TreeMap<Long, List<String>> candAt) {
		TreeMap<Long, String> via = new TreeMap<>(); // corner -> segment it was reached by ("" for a start)
		List<Long> frontier = new ArrayList<>(from);
		for (long v : from) via.put(v, "");
		for (int depth = 1; depth <= MAX_LINK; depth++) {
			List<Long> next = new ArrayList<>();
			for (long v : frontier) {
				for (String k : candAt.getOrDefault(v, List.of())) {
					long[] e = cand.get(k);
					long w = e[0] == v ? e[1] : e[0];
					if (via.containsKey(w)) continue;
					via.put(w, k);
					if (to.contains(w)) {
						List<String> chain = new ArrayList<>();
						for (long c = w; !via.get(c).isEmpty(); ) { String sk = via.get(c); chain.add(sk); long[] ce = cand.get(sk); c = ce[0] == c ? ce[1] : ce[0]; }
						return chain;
					}
					next.add(w);
				}
			}
			frontier = next;
		}
		return null;
	}

	private static long find(TreeMap<Long, Long> parent, long v) {
		Long p = parent.get(v);
		if (p == null) { parent.put(v, v); return v; }
		if (p == v) return v;
		long root = find(parent, p);
		parent.put(v, root);
		return root;
	}

	private static void union(TreeMap<Long, Long> parent, long a, long b) {
		long ra = find(parent, a), rb = find(parent, b);
		if (ra != rb) parent.put(Math.max(ra, rb), Math.min(ra, rb));
	}

	/** The pointwise rule; {@code parity} false (a segment the network pass may restore as a link) skips the METROFILL R1 line test and the random draw, keeping every ground test. */
	private boolean compute(DistrictGrid.Segment s, boolean parity) {
		if (!density.urban(ax(s, s.from()), az(s, s.from())) || !density.urban(ax(s, s.to()), az(s, s.to()))) return false;
		if (tier == Tier.CITY && density.oldTown().cuts(s)) return false; // the old-town wall takes no arterial through its gates
		if (tier == Tier.METROPOLIS && outerRing(s)) return waterOk(s);
		if (parity && !parityLine(s)) return false; // METROFILL R1 / DS-47: arterials only on every second tile line
		int mid = Math.floorDiv(s.from() + s.to(), 2);
		double p = probability(density.fabric(ax(s, mid), az(s, mid))); // ROADS3: the fabric density, not the skyline one
		if (p <= 0 || (parity && Tiers.unit(s.hash(seed, "arterial")) >= p)) return false; // a link (parity false) skips the draw too, not the density floor
		return waterOk(s) && gradeOk(s);
	}

	/**
	 * METROFILL R1: only every second tile line (per axis) may carry an arterial, so two parallel arterials are never one tile apart.
	 * The phase is a world-wide hash of (seed, axis), so every tile and settlement agrees. Pointwise.
	 */
	boolean parityLine(DistrictGrid.Segment s) {
		int phase = Hash64.below(Hash64.hash(seed, "arterial-phase", s.axis()), 2);
		return Math.floorMod(s.line() + phase, 2) == 0;
	}

	/**
	 * Terrain response (TR 5.3): an arterial runs only where a road can be graded along it, so no two coarse samples 32 blocks apart
	 * differ by more than {@link CoarseTerrain#ROAD_STEEP_PER_32} (24 per 32, 3 in 4; the road grade limit is 1 in 3, but the cut and fill beside a
	 * nine-wide road on steeper ground would shear the hill). Bridged spans are exempt. Pointwise.
	 */
	boolean gradeOk(DistrictGrid.Segment s) { return gradeOk(s, CoarseTerrain.ROAD_STEEP_PER_32); }

	/** {@link #gradeOk(DistrictGrid.Segment)} with the largest rise per 32 blocks given (the street lattice's plain streets keep to 1 in 4). */
	boolean gradeOk(DistrictGrid.Segment s, int per32) {
		int limit = per32 * terrain.step / 32;
		int prev = Integer.MIN_VALUE;
		for (int q = terrain.snap(s.from()); q <= terrain.snap(s.to()); q += terrain.step) {
			if (terrain.water(ax(s, q), az(s, q))) { prev = Integer.MIN_VALUE; continue; }
			int h = terrain.floorY(ax(s, q), az(s, q));
			if (prev != Integer.MIN_VALUE && Math.abs(h - prev) > limit) return false;
			prev = h;
		}
		return true;
	}

	/** Where a metropolis's outer ring of arterials runs, as a fraction of the footprint radius. */
	public static final double RING_AT = 0.62;

	/**
	 * Is the segment on the metropolis's outer ring: the border between the tiles whose centres lie inside the ring radius
	 * (at their angle, the footprint's radius times {@link #RING_AT}) and those outside it? The tile borders where the two sides differ
	 * form a closed staircase round the city; the test reads the two tiles' centres only, so both sides of a border agree.
	 */
	boolean outerRing(DistrictGrid.Segment s) {
		boolean alongZ = s.axis() == DistrictGrid.AXIS_X; // a line of constant x
		DistrictGrid.Tile a = alongZ ? grid.tile(s.line() - 1, s.index()) : grid.tile(s.index(), s.line() - 1);
		DistrictGrid.Tile b = alongZ ? grid.tile(s.line(), s.index()) : grid.tile(s.index(), s.line());
		return insideRing(a) != insideRing(b);
	}

	private boolean insideRing(DistrictGrid.Tile t) {
		int cx = (t.minX() + t.maxX()) / 2, cz = (t.minZ() + t.maxZ()) / 2;
		double dx = cx - density.site().centre().x(), dz = cz - density.site().centre().z();
		if (dx == 0 && dz == 0) return true;
		return StrictMath.sqrt(dx * dx + dz * dz) < RING_AT * density.radiusAt(StrictMath.atan2(dz, dx));
	}

	/** The segment crosses no run of water wider than {@link #MAX_BRIDGE} (coarse lattice samples along it). Pointwise. */
	boolean waterOk(DistrictGrid.Segment s) {
		int run = 0;
		for (int q = terrain.snap(s.from()); q <= s.to(); q += terrain.step) {
			if (q < s.from()) continue;
			run = terrain.water(ax(s, q), az(s, q)) ? run + 1 : 0;
			if (run * terrain.step > MAX_BRIDGE) return false;
		}
		return true;
	}

	/** Is the column within {@link #REACH} blocks of an arterial segment of its own tile? */
	public boolean near(int x, int z) {
		if (!tier.atLeast(Tier.CITY)) return false;
		DistrictGrid.Tile t = grid.tileAt(x, z);
		for (DistrictGrid.Segment s : grid.segments(t.ix(), t.iz())) {
			int across = s.axis() == DistrictGrid.AXIS_X ? x : z, along = s.axis() == DistrictGrid.AXIS_X ? z : x;
			if (Math.abs(across - s.fixed()) <= REACH && along >= s.from() && along <= s.to() && is(s)) return true;
		}
		return false;
	}

	/** The segments of every tile overlapping the box, each once, in (axis, line, index) order. */
	public List<DistrictGrid.Segment> segmentsIn(int minX, int minZ, int maxX, int maxZ) {
		TreeMap<String, DistrictGrid.Segment> out = new TreeMap<>();
		DistrictGrid.TileId a = grid.tileOf(minX, minZ), b = grid.tileOf(maxX, maxZ);
		for (int iz = a.iz(); iz <= b.iz(); iz++) {
			for (int ix = a.ix(); ix <= b.ix(); ix++) {
				for (DistrictGrid.Segment s : grid.segments(ix, iz)) {
					out.putIfAbsent(String.format("%d:%09d:%09d", s.axis(), s.line() + 100_000_000, s.index() + 100_000_000), s);
				}
			}
		}
		return new ArrayList<>(out.values());
	}

	/** Centre-line points of the part of a segment between {@code from} and {@code to}: both ends and every lattice multiple between. */
	public List<Pt> points(DistrictGrid.Segment s, int from, int to) {
		List<Pt> pts = new ArrayList<>();
		pts.add(new Pt(ax(s, from), az(s, from)));
		for (int q = Math.floorDiv(from, terrain.step) * terrain.step + terrain.step; q < to; q += terrain.step) pts.add(new Pt(ax(s, q), az(s, q)));
		if (to > from) pts.add(new Pt(ax(s, to), az(s, to)));
		return pts;
	}
}
