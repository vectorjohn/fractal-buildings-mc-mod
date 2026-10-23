package dev.fractalbuildings.core.road;

import dev.fractalbuildings.core.cache.BoundedCache;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.CellDecision;
import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionPlanner;
import dev.fractalbuildings.core.region.RegionProbe;
import dev.fractalbuildings.core.region.Tiers;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.terrain.LatticePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Roads between settlements (ADR 0004, ROADMAP 6.10 planner half): a region-scale graph over the resolved settlements, one
 * dry route per link, and a per-chunk query. Every answer is a pure function of (seed, region decisions, probes) (RD1); the
 * caches are {@link BoundedCache}s and only memoise (RD3), so the capacity never changes an answer.
 *
 * <ul>
 *   <li><b>Nodes</b> are the resolved settlements ({@link FieldRegionView#cell}: a joined city is one node, its owner). They are
 *       found through region-cell claims, never by a scan whose radius includes the largest settlement radius (RC).
 *   <li><b>Graph</b> (N1): a pair is linked when its rim gap is at most {@link #LGAP} and no <i>routable</i> witness exists:
 *       a third settlement whose rim gap to both is smaller, with both of those links routing. A touching pair (gap under
 *       {@link #TOUCH}) shares a meeting point instead of a road (N2), which counts as routed.
 *   <li><b>Route</b> (N4): A* on the 64-block lattice from port to port with wet nodes and other footprints forbidden, then
 *       string-pulled, shaped with a Catmull-Rom curve and read at exact heights every 8 blocks ({@link Profile}); a curve
 *       that is too wet is rerouted round the offending lattice cell, at most {@link #MAX_REROUTES} times, else the link is dropped.
 * </ul>
 */
public final class RoadNet {
	public static final int LATTICE = RegionProbe.STRIDE;
	/** Longest rim gap that links two settlements. */
	public static final int LGAP = 1536;
	/** Rim gap under which two settlements touch and share a meeting point. */
	public static final int TOUCH = 128;
	public static final int MAX_REROUTES = 3;
	/** A port sits this far outside the rim. */
	public static final int PORT_OFFSET = 64;
	/** A port may slide at most this far along the rim to avoid water, peaks and footprints. */
	public static final int MAX_SLIDE = 256;
	public static final int PORT_TRIES = 4;
	/** Other footprints are forbidden to routes within this margin of their rim. */
	public static final int FOOT_MARGIN = 32;
	/** No road column lies farther than this from the nearer port of its link (bounds a chunk's read, RC). */
	public static final int CORRIDOR = 3000;
	/** How far a road column may lie outside its route's search band box: the shaped curve bulges by up to 32 blocks and a column reaches half the width (at most 4) further. A cell keeps a link when this grown box meets it. */
	public static final int BAND_SLACK = 40;
	/** Largest height difference between lattice nodes the string-pull accepts per 64 blocks (1 in 4). */
	private static final int PULL_STEP = 16;

	/** A resolved settlement. {@code port} is the region field's water tag (N6). */
	public record Settlement(CellKey owner, int x, int z, int r, Tier tier, boolean port) {}

	/** A link between two settlements; {@code a} sorts before {@code b} and owns the route. */
	public record EdgeKey(CellKey a, CellKey b) implements Comparable<EdgeKey> {
		@Override public int compareTo(EdgeKey o) {
			int c = a.compareTo(o.a);
			return c != 0 ? c : b.compareTo(o.b);
		}
	}

	/** One road column: world x, z, the deck's y (the block the player stands on, the first free block above it), whether it lies on a crossing, and the road width. */
	public record Col(int x, int z, int y, boolean crossing, int width) {}

	/** The finished route of one link (or the reason it has none). Read-only after construction. */
	public static final class Route {
		public EdgeKey key;
		public Settlement a, b;
		public boolean ok, touching;
		public String failure = "";
		public int[] portA, portB;
		public int width;
		public List<LatticePath.Pt> lattice = List.of();
		public int pulledNodes;
		public double[][] stations = new double[0][];
		public Profile.Result profile;
		/** minX, minZ, maxX, maxZ of the search band. */
		public int[] bandBox;
		public int attempts;
		/** Lattice nodes the searches asked for, and the exact reads of the final profile. */
		public long nodeRequests, exactReads;
		/** Wall time of the build, for reports only. */
		public long nanos;
		public double length, maxGrade;
		public TreeMap<Long, List<Col>> byChunk = new TreeMap<>();
		public int columns;
	}

	private final FieldRegionView view;
	private final RegionMap.Window window;
	private final RegionField field;
	private final long seed;
	private final int S;
	private final RegionProbe coarse;
	private final TerrainProbe exact;
	/** Scan slack: a settlement whose rim is within d of a point has a claimed cell site within d + this of it (see {@link #settlementsNear}). */
	private final int slack;

	private final BoundedCache<CellKey, Optional<Settlement>> settlements;
	private final BoundedCache<CellKey, List<Settlement>> neighbours;
	private final BoundedCache<CellKey, EdgeSet> edgesOf;
	private final BoundedCache<EdgeKey, int[][]> ports;
	private final BoundedCache<EdgeKey, Route> routes;
	private final BoundedCache<CellKey, List<EdgeKey>> cellEdges;
	private final java.util.concurrent.ConcurrentHashMap<EdgeKey, java.util.concurrent.CompletableFuture<Route>> routeFlights = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentHashMap<CellKey, java.util.concurrent.CompletableFuture<List<EdgeKey>>> cellFlights = new java.util.concurrent.ConcurrentHashMap<>();
	private final BoundedCache<Long, int[]> nodes;
	private final AtomicLong nodeReads = new AtomicLong();

	private double scanRadius, scanRadiusNear;
	private int refX, refZ;
	private boolean logging, nearScan;
	private final TreeSet<CellKey> touched = new TreeSet<>();

	/** Test seam (RD3): when set, a deck height depends on how many routes this net has built, which the determinism tests must catch. */
	static volatile boolean MUTANT_CACHE_STATE;

	/**
	 * @param window the recorded lattice the coarse probe covers, or null for an unbounded world
	 * @param coarse reads at lattice columns only (multiples of 64)
	 * @param exact the exact height definition (the game's, through ColumnMemo); read at stations only
	 */
	public RoadNet(RegionField field, FieldRegionView view, RegionMap.Window window, RegionProbe coarse, TerrainProbe exact, int cacheCapacity) {
		this.view = view;
		this.window = window;
		this.field = field;
		this.seed = field.seed;
		this.S = field.cellSize;
		this.coarse = coarse;
		this.exact = exact;
		this.slack = (int) (2.5 * S) + 128;
		settlements = new BoundedCache<>(cacheCapacity);
		neighbours = new BoundedCache<>(cacheCapacity);
		edgesOf = new BoundedCache<>(cacheCapacity);
		ports = new BoundedCache<>(cacheCapacity);
		routes = new BoundedCache<>(cacheCapacity);
		cellEdges = new BoundedCache<>(cacheCapacity);
		nodes = new BoundedCache<>(cacheCapacity);
	}

	public RoadNet(RegionField field, FieldRegionView view, RegionMap.Window window, RegionProbe coarse, TerrainProbe exact) {
		this(field, view, window, coarse, exact, FieldRegionView.DEFAULT_CACHE);
	}

	/** Drops every memo (memory pressure, tests). Answers do not change. */
	public void evictAll() {
		settlements.clear();
		neighbours.clear();
		edgesOf.clear();
		ports.clear();
		routes.clear();
		cellEdges.clear();
		nodes.clear();
	}

	/** Lattice nodes read from the coarse probe so far (cache misses). */
	public long nodeReads() { return nodeReads.get(); }

	public long routesBuilt() { return routes.misses(); }

	/**
	 * Starts recording which region cells the settlement scans touch from (x, z) (a test hook; never changes an answer): the farthest
	 * cell site of any scan, the farthest of the chunk's own first scan (the read cap RC bounds this one), and the distinct cells.
	 */
	public void startLog(int x, int z) { refX = x; refZ = z; scanRadius = 0; scanRadiusNear = 0; touched.clear(); logging = true; }

	/** Stops the log; returns {farthest site of any scan, farthest site of the chunk's first scan, distinct cells touched}. */
	public double[] stopLog() { logging = false; return new double[] {scanRadius, scanRadiusNear, touched.size()}; }

	// ---- settlements (claims) ----

	private Optional<Settlement> settlementOf(CellKey k) {
		return settlements.get(k, kk -> {
			if (!view.decidable(kk.cx(), kk.cz())) return Optional.empty();
			FieldRegionView.Resolved r = view.cell(kk.cx(), kk.cz());
			CellDecision d = r.decision();
			if (!d.settled() || r.claimedBy() != null) return Optional.empty();
			return Optional.of(new Settlement(kk, d.centreX(), d.centreZ(), d.radius(), d.tier(), d.port()));
		});
	}

	/** The settlement a cell is the owner of (a settled cell that nobody claimed), if any. */
	public Optional<Settlement> settlement(CellKey owner) { return settlementOf(owner); }

	private static long d2(long ax, long az, long bx, long bz) { return (ax - bx) * (ax - bx) + (az - bz) * (az - bz); }

	private double rimDist(Settlement s, long px, long pz) {
		return StrictMath.sqrt((double) d2(s.x(), s.z(), px, pz)) - s.r();
	}

	/**
	 * Settlements whose rim is within {@code dist} of (px, pz), in owner order. The scan visits cells whose site lies within
	 * {@code dist + slack} (2.5 cells) of the point and takes each cell's owner from its claim: a settlement of radius at most
	 * 1.3 cells has its own cell's site within r + 0.45 cells of its centre, and a larger one has claimed a cell site within 1.2
	 * cells of its rim point nearest to the point. The radius does not grow with the largest settlement radius.
	 */
	public List<Settlement> settlementsNear(int px, int pz, int dist) {
		return scan(px, pz, dist, null);
	}

	private List<Settlement> scan(int px, int pz, int dist, java.util.Set<CellKey> skip) {
		long reach = (long) dist + slack;
		TreeMap<CellKey, Settlement> found = new TreeMap<>();
		for (int cz = Math.floorDiv((int) (pz - reach), S); cz <= Math.floorDiv((int) (pz + reach), S); cz++) {
			for (int cx = Math.floorDiv((int) (px - reach), S); cx <= Math.floorDiv((int) (px + reach), S); cx++) {
				CellKey k = new CellKey(cx, cz);
				if (skip != null && skip.contains(k)) continue;
				if (d2(field.siteX(cx, cz), field.siteZ(cx, cz), px, pz) > reach * reach) continue;
				owner(k).ifPresent(s -> {
					if (rimDist(s, px, pz) <= dist) found.put(s.owner(), s);
				});
			}
		}
		return new ArrayList<>(found.values());
	}

	/** The settlement a cell belongs to (as owner or as a claimed member), if any. */
	private Optional<Settlement> owner(CellKey k) {
		if (logging) {
			double d = StrictMath.sqrt((double) d2(field.siteX(k.cx(), k.cz()), field.siteZ(k.cx(), k.cz()), refX, refZ));
			if (d > scanRadius) scanRadius = d;
			if (nearScan && d > scanRadiusNear) scanRadiusNear = d;
			touched.add(k);
		}
		if (!view.decidable(k.cx(), k.cz())) return Optional.empty();
		FieldRegionView.Resolved r = view.cell(k.cx(), k.cz());
		if (r.claimedBy() != null) return settlementOf(r.claimedBy());
		return settlementOf(k);
	}

	/** Every settlement the window can answer for, in owner order (reports and tests). */
	public List<Settlement> allSettlements() {
		List<Settlement> out = new ArrayList<>();
		int cx0, cx1, cz0, cz1;
		if (window == null) throw new IllegalStateException("allSettlements needs a window");
		cx0 = Math.floorDiv(window.x0(), S); cx1 = Math.floorDiv(window.x1(), S);
		cz0 = Math.floorDiv(window.z0(), S); cz1 = Math.floorDiv(window.z1(), S);
		for (int cz = cz0; cz <= cz1; cz++) for (int cx = cx0; cx <= cx1; cx++) settlementOf(new CellKey(cx, cz)).ifPresent(out::add);
		return out;
	}

	// ---- graph ----

	/** The rim gap in 1/16 blocks (an integer, so comparisons are exact). */
	static long gapQ(Settlement a, Settlement b) {
		long dq = (long) StrictMath.floor(StrictMath.sqrt((double) d2(a.x(), a.z(), b.x(), b.z())) * 16.0);
		return dq - 16L * (a.r() + b.r());
	}

	public static double gap(Settlement a, Settlement b) { return gapQ(a, b) / 16.0; }

	static EdgeKey key(Settlement p, Settlement q) {
		return p.owner().compareTo(q.owner()) < 0 ? new EdgeKey(p.owner(), q.owner()) : new EdgeKey(q.owner(), p.owner());
	}

	/** Strict weight order of two pairs: gap first, then the edge key (N1 ties). */
	private static boolean lighter(long qa, EdgeKey ka, long qb, EdgeKey kb) {
		return qa < qb || (qa == qb && ka.compareTo(kb) < 0);
	}

	/** Settlements whose rim gap to {@code a} is at most {@link #LGAP}, in owner order. Symmetric: b is in a's list iff a is in b's. */
	public List<Settlement> neighbours(Settlement a) {
		return neighbours.get(a.owner(), k -> {
			FieldRegionView.Resolved self = view.cell(a.owner().cx(), a.owner().cz());
			java.util.Set<CellKey> own = new TreeSet<>();
			own.add(a.owner());
			if (self.site() != null) own.addAll(self.site().cells());
			List<Settlement> out = new ArrayList<>();
			for (Settlement c : scan(a.x(), a.z(), a.r() + LGAP, own)) {
				if (!c.owner().equals(a.owner()) && gapQ(a, c) <= 16L * LGAP) out.add(c);
			}
			return List.copyOf(out);
		});
	}

	/** Can the pair be joined: touching, or within the gap limit with a route that came out dry. */
	public boolean routable(Settlement a, Settlement b) {
		long g = gapQ(a, b);
		if (g > 16L * LGAP) return false;
		if (g < 16L * TOUCH) return true;
		return route(key(a, b)).ok;
	}

	/** The links of a settlement (N1), as edge keys in order. */
	public List<EdgeKey> edgesOf(Settlement a) { return edgeSet(a).linked(); }

	/** The pairs of {@code a} that no routable witness blocks but whose route failed the dry check or found no path (reports). */
	public List<EdgeKey> droppedOf(Settlement a) { return edgeSet(a).dropped(); }

	/** Linked and dropped pairs of one settlement. */
	public record EdgeSet(List<EdgeKey> linked, List<EdgeKey> dropped) {}

	private EdgeSet edgeSet(Settlement a) {
		return edgesOf.get(a.owner(), k -> {
			List<EdgeKey> out = new ArrayList<>(), lost = new ArrayList<>();
			List<Settlement> near = neighbours(a);
			for (Settlement b : near) {
				EdgeKey ab = key(a, b);
				long qab = gapQ(a, b);
				boolean blocked = false;
				for (Settlement c : near) {
					if (c.owner().equals(b.owner())) continue;
					if (!lighter(gapQ(a, c), key(a, c), qab, ab) || !lighter(gapQ(c, b), key(c, b), qab, ab)) continue;
					if (routable(a, c) && routable(c, b)) { blocked = true; break; }
				}
				if (blocked) continue;
				if (routable(a, b)) out.add(ab);
				else lost.add(ab);
			}
			return new EdgeSet(List.copyOf(out), List.copyOf(lost));
		});
	}

	/** Every link between settlements the window can answer for, in key order (reports and tests). */
	public List<EdgeKey> allEdges() {
		TreeSet<EdgeKey> s = new TreeSet<>();
		for (Settlement a : allSettlements()) s.addAll(edgesOf(a));
		return new ArrayList<>(s);
	}

	private Settlement settlementOrThrow(CellKey k) { return settlementOf(k).orElseThrow(); }

	public static boolean touching(Settlement a, Settlement b) { return gapQ(a, b) < 16L * TOUCH; }

	/** The shared meeting point of a touching pair: on the line between the centres, halfway through the gap. */
	public static int[] meeting(Settlement a, Settlement b) {
		double dx = b.x() - a.x(), dz = b.z() - a.z();
		double len = StrictMath.sqrt(dx * dx + dz * dz);
		if (len == 0) return new int[] {a.x(), a.z()};
		double t = (a.r() + gap(a, b) / 2.0) / len;
		return new int[] {(int) StrictMath.floor(a.x() + dx * t + 0.5), (int) StrictMath.floor(a.z() + dz * t + 0.5)};
	}

	/** The ports of a settlement for its links: where each road meets it (N3, N5). */
	public List<RoadPort> portsOf(Settlement a) {
		List<RoadPort> out = new ArrayList<>();
		for (EdgeKey e : edgesOf(a)) {
			boolean low = e.a().equals(a.owner());
			Settlement b = settlementOrThrow(low ? e.b() : e.a());
			int width = widthOf(a.tier(), b.tier());
			if (touching(a, b)) {
				int[] m = meeting(a, b);
				out.add(new RoadPort(b.owner(), m[0], m[1], width, true));
			} else {
				int[][] p = ports(e);
				int[] mine = low ? p[0] : p[1];
				out.add(new RoadPort(b.owner(), mine[0], mine[1], width, false));
			}
		}
		return out;
	}

	public static int widthOf(Tier x, Tier y) {
		Tier lo = x.ordinal() < y.ordinal() ? x : y;
		if (lo.atLeast(Tier.CITY)) return 7;
		return switch (lo) { case HAMLET -> 3; case VILLAGE -> 4; default -> 5; };
	}

	// ---- lattice ----

	private static long nodeKey(int x, int z) { return ((long) (z / LATTICE + (1 << 24)) << 32) | (long) (x / LATTICE + (1 << 24)); }

	private boolean inWindow(int x, int z) {
		return window == null || (x >= window.x0() && x <= window.x1() && z >= window.z0() && z <= window.z1());
	}

	/** {floor, wet, peak} at a lattice node; outside the window a node is wet. */
	private int[] node(int x, int z) {
		return nodes.get(nodeKey(x, z), k -> {
			nodeReads.incrementAndGet();
			if (!inWindow(x, z)) return new int[] {0, 1, 0};
			int f = coarse.floorY(x, z);
			boolean wet = coarse.water(x, z) || coarse.waterDepth(x, z) >= 1;
			return new int[] {f, wet ? 1 : 0, f >= RegionPlanner.PEAK_Y ? 1 : 0};
		});
	}

	private int snap(double v) { return (int) (Math.floorDiv((long) StrictMath.floor(v) + LATTICE / 2, LATTICE) * LATTICE); }

	/** Largest distance at which a wet lattice node is taken to reach a point: a pond sampled at one node spreads about half a lattice step. */
	private static final int SHORE = 36;

	/** Is a wet lattice node within {@code d} blocks of the point (the four nodes round it are tested)? */
	private boolean wetWithin(double px, double pz, int d, java.util.Set<Long> banned) {
		int gx = Math.floorDiv((int) StrictMath.floor(px), LATTICE) * LATTICE, gz = Math.floorDiv((int) StrictMath.floor(pz), LATTICE) * LATTICE;
		for (int dz = 0; dz <= LATTICE; dz += LATTICE) {
			for (int dx = 0; dx <= LATTICE; dx += LATTICE) {
				double ex = gx + dx - px, ez = gz + dz - pz;
				if (ex * ex + ez * ez < (double) d * d && (node(gx + dx, gz + dz)[1] == 1 || banned.contains(packNode(gx + dx, gz + dz)))) return true;
			}
		}
		return false;
	}

	private boolean nearWet(int x, int z) {
		for (int dz = -LATTICE; dz <= LATTICE; dz += LATTICE) {
			for (int dx = -LATTICE; dx <= LATTICE; dx += LATTICE) {
				if ((dx != 0 || dz != 0) && inWindow(x + dx, z + dz) && node(x + dx, z + dz)[1] == 1) return true;
			}
		}
		return false;
	}

	// ---- ports ----

	/** {portA, portB} of a (non-touching) link: lattice points, or null entries when no spot qualifies. */
	private int[][] ports(EdgeKey e) {
		return ports.get(e, k -> {
			Settlement a = settlementOrThrow(k.a()), b = settlementOrThrow(k.b());
			return new int[][] {port(a, b), port(b, a)};
		});
	}

	private int[] port(Settlement a, Settlement b) {
		double bearing = StrictMath.atan2((double) b.z() - a.z(), (double) b.x() - a.x());
		double rp = a.r() + PORT_OFFSET;
		double maxSlide = Math.min(Math.PI, MAX_SLIDE / rp);
		for (int k = 0; k <= PORT_TRIES; k++) {
			double ang = bearing;
			if (k > 0) ang += (Tiers.unit(Hash64.hash(seed, "road/port", a.owner().cx(), a.owner().cz(), b.owner().cx(), b.owner().cz(), k)) - 0.5) * 2 * maxSlide;
			double ux = StrictMath.cos(ang), uz = StrictMath.sin(ang);
			int px = snap(a.x() + rp * ux), pz = snap(a.z() + rp * uz);
			int rx = snap(a.x() + a.r() * ux), rz = snap(a.z() + a.r() * uz);
			if (!inWindow(px, pz) || !inWindow(rx, rz)) continue;
			int[] n = node(px, pz);
			if (n[1] == 1) continue;
			if (n[2] == 1) continue;
			if (node(rx, rz)[1] == 1) continue;
			boolean inside = false;
			for (Settlement o : settlementsNear(px, pz, FOOT_MARGIN)) {
				if (o.owner().equals(a.owner())) continue;
				long m = o.r() + FOOT_MARGIN;
				if (d2(px, pz, o.x(), o.z()) < m * m) { inside = true; break; }
			}
			if (!inside) return new int[] {px, pz};
		}
		return null;
	}

	// ---- routes ----

	public Route route(EdgeKey e) { return flight(routeFlights, routes, e, this::timedRoute); }

	/**
	 * {@code cache.get} where threads that miss on the same key while another computes it wait for that result instead of computing it
	 * again (a world generates its first chunks of a region on every worker at once, and a cold cell costs seconds). Answers are the same
	 * either way: the function is pure. The graph below is acyclic (a cell's links need routes, a route needs ports and settlements,
	 * never the other way), so a waiter cannot wait on itself. A failure reaches every waiter and leaves nothing cached.
	 */
	private static <K, V> V flight(java.util.concurrent.ConcurrentHashMap<K, java.util.concurrent.CompletableFuture<V>> inflight, BoundedCache<K, V> cache, K key, java.util.function.Function<K, V> fn) {
		return cache.get(key, k -> {
			java.util.concurrent.CompletableFuture<V> mine = new java.util.concurrent.CompletableFuture<>();
			java.util.concurrent.CompletableFuture<V> other = inflight.putIfAbsent(k, mine);
			if (other != null) return other.join();
			try {
				V v = fn.apply(k);
				mine.complete(v);
				return v;
			} catch (Throwable e) {
				mine.completeExceptionally(e);
				throw e;
			} finally {
				inflight.remove(k, mine);
			}
		});
	}

	private Route timedRoute(EdgeKey e) {
		long t0 = System.nanoTime(); // det-ok: report only, never an answer
		Route r = buildRoute(e);
		r.nanos = System.nanoTime() - t0; // det-ok: report only, never an answer
		return r;
	}

	private static long packNode(int x, int z) { return ((long) z << 32) ^ (x & 0xffffffffL); }

	private Route buildRoute(EdgeKey e) {
		Route rt = new Route();
		rt.key = e;
		Settlement A = settlementOrThrow(e.a()), B = settlementOrThrow(e.b());
		rt.a = A;
		rt.b = B;
		rt.width = widthOf(A.tier(), B.tier());
		long g = gapQ(A, B);
		if (g > 16L * LGAP) { rt.failure = "too far"; return rt; }
		if (g < 16L * TOUCH) {
			rt.touching = true;
			int[] m = meeting(A, B);
			rt.portA = m;
			rt.portB = m;
			rt.ok = true;
			return rt;
		}
		int[][] pp = ports(e);
		if (pp[0] == null || pp[1] == null) { rt.failure = "no port"; return rt; }
		rt.portA = pp[0];
		rt.portB = pp[1];
		double pd = StrictMath.sqrt((double) d2(pp[0][0], pp[0][1], pp[1][0], pp[1][1]));
		int band = (int) Math.max(256, 0.3 * pd);
		int x0 = window == null ? Integer.MIN_VALUE / 2 : window.x0(), x1 = window == null ? Integer.MAX_VALUE / 2 : window.x1();
		int z0 = window == null ? Integer.MIN_VALUE / 2 : window.z0(), z1 = window == null ? Integer.MAX_VALUE / 2 : window.z1();
		int minX = Math.max(x0, Math.min(pp[0][0], pp[1][0]) - band), maxX = Math.min(x1, Math.max(pp[0][0], pp[1][0]) + band);
		int minZ = Math.max(z0, Math.min(pp[0][1], pp[1][1]) - band), maxZ = Math.min(z1, Math.max(pp[0][1], pp[1][1]) + band);
		minX = Math.floorDiv(minX + LATTICE - 1, LATTICE) * LATTICE; minZ = Math.floorDiv(minZ + LATTICE - 1, LATTICE) * LATTICE;
		maxX = Math.floorDiv(maxX, LATTICE) * LATTICE; maxZ = Math.floorDiv(maxZ, LATTICE) * LATTICE;
		rt.bandBox = new int[] {minX, minZ, maxX, maxZ};
		int mx = (pp[0][0] + pp[1][0]) / 2, mz = (pp[0][1] + pp[1][1]) / 2;
		int reach = (int) (pd / 2 + band * 1.5) + FOOT_MARGIN + 64;
		List<Settlement> others = new ArrayList<>();
		for (Settlement o : settlementsNear(mx, mz, reach)) if (!o.owner().equals(A.owner()) && !o.owner().equals(B.owner())) others.add(o);

		TreeSet<Long> banned = new TreeSet<>();
		for (int attempt = 0; ; attempt++) {
			rt.attempts = attempt;
			long[] req = {0};
			LatticePath.Cost cost = new LatticePath.Cost() {
				@Override public long step(int x, int z, int nx, int nz) {
					req[0]++;
					if (banned.contains(packNode(nx, nz))) return FORBIDDEN;
					int[] n = node(nx, nz);
					if (n[1] == 1 || n[2] == 1 || blocked(nx, nz, 0, A, B, others)) return FORBIDDEN;
					boolean diag = x != nx && z != nz;
					if (diag && (node(x, nz)[1] == 1 || node(nx, z)[1] == 1)) return FORBIDDEN;
					int dy = Math.abs(n[0] - node(x, z)[0]);
										long c = (diag ? 14L : 10L) * LATTICE + 60L * dy;
					if (nearWet(nx, nz)) c += 200;
					return c;
				}
			};
			List<LatticePath.Pt> path;
			try {
				path = LatticePath.find(new LatticePath.Pt(pp[0][0], pp[0][1]), new LatticePath.Pt(pp[1][0], pp[1][1]), cost,
					new LatticePath.Bounds(minX, minZ, maxX, maxZ), LATTICE, -1, LatticePath.DEFAULT_MAX_EXPANSIONS, false);
			} catch (IllegalArgumentException ex) {
				path = List.of();
			}
			rt.nodeRequests += req[0];
			if (path.isEmpty()) {
				rt.failure = attempt == 0 ? "no path" : "no dry reroute";
				return rt;
			}
			rt.lattice = path;
			List<int[]> pulled = pull(path, A, B, others, banned);
			rt.pulledNodes = pulled.size();
			double[][] st = Curve.stations(pulled);
			rt.stations = st;
			String bad = footprintHit(st, A, B, others);
			Profile.Result pr = null;
			int failStation = -1;
			if (bad != null) {
				rt.failure = bad;
				failStation = footprintStation(st, A, B, others);
			} else {
				double[][] clamped = clampToWindow(st);
				pr = Profile.build(clamped, exact);
				rt.exactReads += pr.reads();
				rt.profile = pr;
				if (!pr.ok()) { rt.failure = pr.failure(); failStation = pr.failStation(); }
			}
			if (failStation < 0 && (bad == null) && pr != null && pr.ok()) {
				rt.failure = "";
				rt.ok = true;
				finish(rt);
				return rt;
			}
			if (attempt >= MAX_REROUTES) { rt.failure = "dry check failed after " + MAX_REROUTES + " reroutes: " + rt.failure; return rt; }
			int sx = (int) StrictMath.floor(st[Math.max(0, failStation)][0]), sz = (int) StrictMath.floor(st[Math.max(0, failStation)][1]);
			int gx = Math.floorDiv(sx, LATTICE) * LATTICE, gz = Math.floorDiv(sz, LATTICE) * LATTICE;
			boolean added = false;
			for (int[] n : new int[][] {{gx, gz}, {gx + LATTICE, gz}, {gx, gz + LATTICE}, {gx + LATTICE, gz + LATTICE}}) {
				if ((n[0] == pp[0][0] && n[1] == pp[0][1]) || (n[0] == pp[1][0] && n[1] == pp[1][1])) continue;
				added |= banned.add(packNode(n[0], n[1]));
			}
			if (!added) { rt.failure = "dry check failed, nothing left to ban: " + rt.failure; return rt; }
		}
	}

	private double[][] clampToWindow(double[][] st) {
		if (window == null) return st;
		double[][] out = new double[st.length][];
		for (int i = 0; i < st.length; i++) {
			out[i] = new double[] {Math.max(window.x0(), Math.min(window.x1(), st[i][0])), Math.max(window.z0(), Math.min(window.z1(), st[i][1]))};
		}
		return out;
	}

	/** Is the point inside either end's disc, or inside another settlement's disc grown by {@code margin} + the footprint margin? */
	private static boolean blocked(int x, int z, int margin, Settlement A, Settlement B, List<Settlement> others) {
		if (d2(x, z, A.x(), A.z()) < (long) A.r() * A.r() || d2(x, z, B.x(), B.z()) < (long) B.r() * B.r()) return true;
		for (Settlement o : others) {
			long m = o.r() + FOOT_MARGIN + margin;
			if (d2(x, z, o.x(), o.z()) < m * m) return true;
		}
		return false;
	}

	private String footprintHit(double[][] st, Settlement A, Settlement B, List<Settlement> others) {
		return footprintStation(st, A, B, others) >= 0 ? "footprint" : null;
	}

	/** The first station inside another settlement's disc (not the margin: the curve may bulge a little), or inside an end's disc beyond its port. */
	private int footprintStation(double[][] st, Settlement A, Settlement B, List<Settlement> others) {
		for (int k = 0; k < st.length; k++) {
			int x = Profile.round(st[k][0]), z = Profile.round(st[k][1]);
			for (Settlement o : others) {
				if (d2(x, z, o.x(), o.z()) < (long) o.r() * o.r()) return k;
			}
		}
		return -1;
	}

	/** String-pull: keep a node only where the straight chord to the next would cross water, a peak, a footprint or a steep step. */
	private List<int[]> pull(List<LatticePath.Pt> path, Settlement A, Settlement B, List<Settlement> others, java.util.Set<Long> banned) {
		List<int[]> out = new ArrayList<>();
		int i = 0, n = path.size() - 1;
		out.add(new int[] {path.get(0).x(), path.get(0).z()});
		while (i < n) {
			int j = n;
			while (j > i + 1 && !chordOk(path.get(i), path.get(j), A, B, others, banned)) j--;
			out.add(new int[] {path.get(j).x(), path.get(j).z()});
			i = j;
		}
		return out;
	}

	private boolean chordOk(LatticePath.Pt p, LatticePath.Pt q, Settlement A, Settlement B, List<Settlement> others, java.util.Set<Long> banned) {
		double dx = q.x() - p.x(), dz = q.z() - p.z();
		double len = StrictMath.sqrt(dx * dx + dz * dz);
		int n = (int) StrictMath.ceil(len / (LATTICE / 2.0));
		int prev = Integer.MIN_VALUE;
		int prevNx = Integer.MIN_VALUE, prevNz = 0;
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n;
			double sx = p.x() + t * dx, sz = p.z() + t * dz;
			int nx = snap(sx), nz = snap(sz);
			if (blocked(Profile.round(sx), Profile.round(sz), 0, A, B, others)) return false;
			if (nx == prevNx && nz == prevNz) continue;
			prevNx = nx;
			prevNz = nz;
			int[] nd = node(nx, nz);
			if (nd[1] == 1 || nd[2] == 1) return false;
			if (wetWithin(sx, sz, SHORE, banned)) return false;
			if (prev != Integer.MIN_VALUE && Math.abs(nd[0] - prev) > PULL_STEP) return false;
			prev = nd[0];
		}
		return true;
	}

	// ---- raster ----

	private static long colKey(int x, int z) { return ((long) (z + (1 << 30)) << 32) | (long) (x + (1 << 30)); }

	private static long chunkKey(int cx, int cz) { return colKey(cx, cz); }

	private void finish(Route rt) {
		Profile.Result pr = rt.profile;
		double[][] st = rt.stations;
		double len = 0, mg = 0;
		for (int k = 1; k < st.length; k++) {
			double dl = StrictMath.sqrt((st[k][0] - st[k - 1][0]) * (st[k][0] - st[k - 1][0]) + (st[k][1] - st[k - 1][1]) * (st[k][1] - st[k - 1][1]));
			len += dl;
			if (dl > 0) mg = Math.max(mg, Math.abs(pr.deck()[k] - pr.deck()[k - 1]) / Math.max(dl, 4.0));
		}
		rt.length = len;
		if (MUTANT_CACHE_STATE) for (int k = 0; k < pr.deck().length; k++) pr.deck()[k] += (int) (routes.misses() & 1);
		rt.maxGrade = mg;
		rt.byChunk = raster(st, pr.deck(), pr.span(), rt.width);
		for (List<Col> l : rt.byChunk.values()) rt.columns += l.size();
	}

	/**
	 * The columns of a road of {@code width} along the stations, by chunk: each column takes the nearest segment (the lower segment on a
	 * tie), its deck height interpolated along it; {@code span} marks the stations of a crossing. Pure.
	 */
	public static TreeMap<Long, List<Col>> raster(double[][] st, int[] deck, boolean[] span, int width) {
		TreeMap<Long, List<Col>> byChunk = new TreeMap<>();
		double hw = width / 2.0;
		TreeMap<Long, double[]> best = new TreeMap<>(); // column -> {dist, segment, y, crossing}
		for (int k = 0; k + 1 < st.length; k++) {
			double[] p = st[k], q = st[k + 1];
			int cx0 = (int) StrictMath.floor(Math.min(p[0], q[0]) - hw - 1), cx1 = (int) StrictMath.ceil(Math.max(p[0], q[0]) + hw + 1);
			int cz0 = (int) StrictMath.floor(Math.min(p[1], q[1]) - hw - 1), cz1 = (int) StrictMath.ceil(Math.max(p[1], q[1]) + hw + 1);
			double dx = q[0] - p[0], dz = q[1] - p[1], l2 = dx * dx + dz * dz;
			boolean cross = span[k] && span[k + 1];
			for (int z = cz0; z <= cz1; z++) {
				for (int x = cx0; x <= cx1; x++) {
					double ccx = x + 0.5, ccz = z + 0.5;
					double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((ccx - p[0]) * dx + (ccz - p[1]) * dz) / l2));
					double ex = ccx - (p[0] + t * dx), ez = ccz - (p[1] + t * dz);
					double dist = StrictMath.sqrt(ex * ex + ez * ez);
					if (dist > hw) continue;
					double y = deck[k] + t * (deck[k + 1] - deck[k]);
					double[] cur = best.get(colKey(x, z));
					if (cur == null || dist < cur[0] || (dist == cur[0] && k < cur[1])) {
						best.put(colKey(x, z), new double[] {dist, k, y, cross ? 1 : 0});
					}
				}
			}
		}
		for (var en : best.entrySet()) {
			int x = (int) ((en.getKey() & 0xffffffffL) - (1 << 30)), z = (int) ((en.getKey() >>> 32) - (1 << 30));
			double[] v = en.getValue();
			byChunk.computeIfAbsent(chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), kk -> new ArrayList<>())
				.add(new Col(x, z, (int) StrictMath.floor(v[2] + 0.5), v[3] == 1, width));
		}
		return byChunk;
	}

	// ---- chunk query ----

	/**
	 * The deck surface height of the road to {@code other} at {@code s}'s end of it, or empty for a touching pair (no road: both
	 * approaches meet at one point) or a dropped link. The approach from the settlement is pinned to it.
	 */
	public java.util.OptionalInt endDeck(Settlement s, CellKey other) {
		Optional<Settlement> o = settlementOf(other);
		if (o.isEmpty()) return java.util.OptionalInt.empty();
		EdgeKey e = key(s, o.get());
		Route r = route(e);
		if (!r.ok || r.touching) return java.util.OptionalInt.empty();
		int[] d = r.profile.deck();
		return java.util.OptionalInt.of(e.a().equals(s.owner()) ? d[0] : d[d.length - 1]);
	}


	/**
	 * Links whose road may reach the region cell: every link of a settlement whose rim is within the corridor bound of the cell
	 * (a road column is at most {@link #CORRIDOR} from the nearer port), kept when its search band meets the cell.
	 */
	public List<EdgeKey> edgesNearCell(CellKey cell) {
		return cellEdgesOf(cell);
	}

	/**
	 * The farthest a cell site may lie from a chunk for the chunk's own settlement scan (RC): the corridor bound, the port offset and
	 * lattice snap, the chunk-to-cell-centre offset and the scan slack of 2.5 cells. Independent of the largest settlement radius.
	 */
	public int chunkReadBound() { return CORRIDOR + PORT_OFFSET + LATTICE + (int) (0.71 * S) + 16 + slack + (int) (0.71 * S); }

	private List<EdgeKey> cellEdgesOf(CellKey cell) {
		return flight(cellFlights, cellEdges, cell, c -> {
			int x0 = c.cx() * S, z0 = c.cz() * S, x1 = x0 + S - 1, z1 = z0 + S - 1;
			int cxm = x0 + S / 2, czm = z0 + S / 2;
			TreeSet<EdgeKey> cand = new TreeSet<>();
			int reach = CORRIDOR + PORT_OFFSET + LATTICE + (int) (0.71 * S) + 16;
			nearScan = true;
			List<Settlement> first;
			try {
				first = settlementsNear(cxm, czm, reach);
			} finally {
				nearScan = false;
			}
			for (Settlement a : first) cand.addAll(edgesOf(a));
			List<EdgeKey> out = new ArrayList<>();
			for (EdgeKey e : cand) {
				Route r = route(e);
				if (!r.ok || r.touching) continue;
				int[] b = r.bandBox;
				if (b[2] + BAND_SLACK < x0 || b[0] - BAND_SLACK > x1 || b[3] + BAND_SLACK < z0 || b[1] - BAND_SLACK > z1) continue;
				out.add(e);
			}
			return List.copyOf(out);
		});
	}

	/** Road columns inside the chunk, sorted by (z, x); where two roads overlap the lower edge key wins (RD4). */
	public List<Col> query(int chunkX, int chunkZ) {
		int cx = Math.floorDiv(chunkX * 16 + 8, S), cz = Math.floorDiv(chunkZ * 16 + 8, S);
		TreeMap<Long, Col> merged = new TreeMap<>();
		long ck = chunkKey(chunkX, chunkZ);
		for (EdgeKey e : edgesNearCell(new CellKey(cx, cz))) {
			List<Col> cols = route(e).byChunk.get(ck);
			if (cols == null) continue;
			for (Col c : cols) merged.putIfAbsent(colKey(c.x(), c.z()), c);
		}
		return new ArrayList<>(merged.values());
	}
}
