package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.cache.Fanout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Predicate;

/**
 * The quays of one settlement, traced lazily (QUAYLAZY, docs/plans/quaylazy.md). A tile asks {@link #near} for the quays within
 * reach of it and pays only for the chains it touches and the clusters those chains belong to; {@link #all} is today's whole trace.
 * Both give every part exactly as the one global computation would (contour of the whole window, every run, every run graded in
 * canonical order against the earlier ones), because:
 * <ul>
 * <li>a chain's runs depend on the chain alone (the {@code ok} predicate is pointwise);</li>
 * <li>grading a run reads earlier parts only at their columns within {@value #MEET_REACH} blocks of its own ({@code meetPins}: footprint
 * {@code c-2..c+1} and ring {@code c-3..c+2} round each column), and the per-column accumulation ignores layer order, so a run depends on
 * another only through such a column pair. The unit of work is therefore the <b>cluster</b>: chains connected through run columns
 * that close. A cluster is graded alone, in canonical order of its own runs, which is a subsequence of the global order;</li>
 * <li>every result carries an order key ({@link #orderKey}), and outputs are sorted by it, never by discovery order.</li>
 * </ul>
 * State is concurrent maps that are never iterated for output, so one trace may serve several threads at once, each with its own
 * {@link Bank}. Chains come from {@link Quays#seeds} and {@link Quays#chainThrough} (a walk that reads only the cells of its chain) through
 * the caller's bank and a node memo every caller shares; {@link #withSource} swaps in another {@link ChainSource} for a test.
 */
final class QuayTrace {
	/** Two runs interact when some column of one is within this many blocks (Chebyshev) of a column of the other (the {@code meetPins} arithmetic above). */
	static final int MEET_REACH = 4;
	/** How far round a run column the cluster scan looks for other chains' contour cells, blocks. */
	static final int CLUSTER_REACH = Quays.STEP;
	/** How far round a contour point its columns' first quay tests read, blocks (as {@code Districts.Build.QUAY_CHAIN_READ}). */
	static final int QUAY_CHAIN_READ = 8;
	/** How far round a lattice node the field reads the exact terrain, blocks (as {@code Districts.Build.QUAY_READ}). */
	static final int QUAY_READ = Quays.OFFSET + 4;

	static {
		// a column of another chain within MEET_REACH lies on a segment inside a cell within MEET_REACH of ours; segments sit within half a step of the cell edge
		if (CLUSTER_REACH < MEET_REACH + Quays.STEP / 2) throw new AssertionError("cluster reach too short");
	}

	/** What the trace needs from its caller (Districts supplies the terrain-reading implementation). A bank serves one thread. */
	interface Bank {
		/** The raw "water within OFFSET" value at a lattice node, false outside the area; no memo (the trace memoises). */
		boolean near(int x, int z);

		/** Whether a column can carry a quay (pointwise). */
		boolean ok(int[] col);

		/**
		 * Grades one cluster's runs: orients and sorts them canonically, grades each against the earlier parts, sets {@code order}
		 * ({@link #orderKey}). It must not call back into this trace: a cluster is graded on the thread that asks first, and a request
		 * from inside that grading for the same cluster would wait on itself (the trace throws instead).
		 */
		List<QuayWhole> grade(List<List<int[]>> runs);

		/** A hint that the exact terrain in these boxes {@code [minX, minZ, maxX, maxZ]} is about to be read. */
		void prefetch(List<int[]> boxes);

		/**
		 * A bank for another thread (a bank's memos are not thread-safe). The default is this bank itself, right only for a bank whose
		 * {@link #fanout} is serial.
		 */
		default Bank fork() { return this; }

		/** Runs independent pieces of this request, maybe on several threads (each piece asks {@link #fork} for its own bank); serial by default. */
		default Fanout fanout() { return Fanout.SERIAL; }
	}

	/** How far ahead of a chain walk the exact terrain is prefetched when the walk leaves the box prefetched so far, blocks (a box of about 5 x 5 chunks). */
	static final int WALK_AHEAD = 32; // the default of QuayTuning.walkAhead

	/** The traced area: the waterfront tile keys (kept for the caller) and the contour's cell window, inclusive. */
	record Area(TreeSet<Long> wf, int i0, int j0, int i1, int j1) {}

	/** Where chains come from. {@code seeds} lists contour points on the cell edges of a cell range; {@code chainThrough} the whole chain holding a point. */
	interface ChainSource {
		List<Pt> seeds(int ci0, int cj0, int ci1, int cj1);

		List<Pt> chainThrough(Pt seed);

		/** One seed per group of segments joined inside the cell range ({@link Quays#distinctSeeds}); every chain of the range has one. */
		default List<Pt> distinctSeeds(int ci0, int cj0, int ci1, int cj1) { return seeds(ci0, cj0, ci1, cj1); }
	}

	private record Chain(long id, List<Pt> points, List<List<int[]>> runs) {}

	private final Area area;
	private final ConcurrentHashMap<Long, Boolean> nodes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Long> pointIndex = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Chain> chains = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, List<Long>> partners = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Long> clusterOf = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, List<Long>> clusterMembers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, FutureTask<List<QuayWhole>>> clusters = new ConcurrentHashMap<>();
	/** The thread grading each cluster right now (so a re-entrant request throws instead of waiting on itself). */
	private final ConcurrentHashMap<Long, Thread> gradingBy = new ConcurrentHashMap<>();
	private volatile ChainSource source;

	QuayTrace(Area area) { this.area = area; }

	/** For a test: a chain source other than the walker over the caller's bank. */
	QuayTrace withSource(ChainSource s) { source = s; return this; }

	Area area() { return area; }

	/** The quays with a column within {@code m} blocks (Chebyshev) of the window {@code [minX, maxX] x [minZ, maxZ]}, in canonical order. */
	List<QuayWhole> near(Bank bank, int minX, int minZ, int maxX, int maxZ, int m) {
		int r = m + Quays.STEP;
		int ci0 = Math.max(area.i0(), Math.floorDiv(minX - r, Quays.STEP)), ci1 = Math.min(area.i1(), Math.floorDiv(maxX + r, Quays.STEP));
		int cj0 = Math.max(area.j0(), Math.floorDiv(minZ - r, Quays.STEP)), cj1 = Math.min(area.j1(), Math.floorDiv(maxZ + r, Quays.STEP));
		if (ci0 > ci1 || cj0 > cj1) return List.of();
		// PARREADS: the seed scan reads every node of these cells, each within QUAY_READ; one call, so a live probe fills the chunks in parallel
		int[] scan = {ci0 * Quays.STEP, cj0 * Quays.STEP, (ci1 + 1) * Quays.STEP, (cj1 + 1) * Quays.STEP}; // the nodes the seed scan reads
		if (QuayTuning.scanBox) bank.prefetch(List.of(new int[] {scan[0] - QUAY_READ, scan[1] - QUAY_READ, scan[2] + QUAY_READ, scan[3] + QUAY_READ}));
		Forks forks = new Forks(bank);
		// the seed scan reads every node of the box, serially (one memo pass, the box is prefetched); then one walk per group of joined segments,
		// and each walk's cluster, run on the fanout: each piece writes only its own slot and the id-keyed memos
		List<Pt> seeds = source(bank, scan).distinctSeeds(ci0, cj0, ci1, cj1);
		long[] ids = new long[seeds.size()];
		bank.fanout().each(seeds.size(), i -> {
			Bank b = forks.get();
			ChainSource src = source(b, scan);
			Chain c = chainOf(b, src, seeds.get(i));
			ids[i] = touches(c, minX - m, minZ - m, maxX + m, maxZ + m) ? clusterId(b, src, c) : NONE;
		});
		TreeSet<Long> wanted = new TreeSet<>();
		for (long id : ids) if (id != NONE) wanted.add(id);
		List<QuayWhole> out = new ArrayList<>();
		for (List<QuayWhole> parts : gradeAll(forks, new ArrayList<>(wanted))) {
			for (QuayWhole q : parts) if (touches(q.cols(), minX - m, minZ - m, maxX + m, maxZ + m)) out.add(q);
		}
		sort(out);
		return out;
	}

	private static final long NONE = Long.MIN_VALUE;

	/** Grades the clusters on the fanout, each wholly on one thread (the re-entry guard is per thread), and returns the results in the order of {@code ids}. */
	private List<List<QuayWhole>> gradeAll(Forks forks, List<Long> ids) {
		@SuppressWarnings("unchecked") List<QuayWhole>[] res = new List[ids.size()];
		forks.root.fanout().each(ids.size(), i -> res[i] = graded(forks.get(), ids.get(i)));
		return Arrays.asList(res);
	}

	/** The caller's bank for the caller's thread, and one fork per other thread that runs a piece of the request. */
	private static final class Forks {
		final Bank root;
		private final Thread owner = Thread.currentThread();
		private final ConcurrentHashMap<Thread, Bank> others = new ConcurrentHashMap<>();

		Forks(Bank root) { this.root = root; }

		Bank get() {
			Thread t = Thread.currentThread();
			if (t == owner) return root;
			return others.computeIfAbsent(t, k -> {
				java.util.function.LongSupplier clk = QuayTuning.clock;
				long t0 = clk == null ? 0 : clk.getAsLong();
				Bank b = root.fork();
				if (clk != null) QuayTuning.forkNanos.addAndGet(clk.getAsLong() - t0);
				QuayTuning.forks.incrementAndGet();
				return b;
			});
		}
	}

	/** Every quay of the area in canonical order: today's whole trace (every node of the window, every cluster graded). */
	List<QuayWhole> all(Bank bank) {
		int[] whole = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}; // the bank's row prefetch covers the scan: no walk-ahead
		ChainSource src = source(bank, whole);
		TreeSet<Long> ids = new TreeSet<>();
		for (Pt seed : src.seeds(area.i0(), area.j0(), area.i1(), area.j1())) {
			Chain c = chainOf(bank, src, seed);
			if (!c.runs().isEmpty()) ids.add(clusterId(bank, src, c));
		}
		List<QuayWhole> out = new ArrayList<>();
		for (List<QuayWhole> parts : gradeAll(new Forks(bank), new ArrayList<>(ids))) out.addAll(parts);
		sort(out);
		return out;
	}

	/**
	 * The key that sorts parts into the global canonical order. For run {@code r} of a list in {@code Districts.canonicalQuays} order: its
	 * smallest column key, first column key and size; its rank among the runs just before it with those same three (canonicalQuays breaks
	 * such a tie by the column sequence); then the part's index in the run's {@code quayParts} recursion order. The key is total. Two runs
	 * that tie share their smallest column, so they meet (distance 0) and always sit in the same cluster: a run's rank in its cluster's
	 * canonical list is its rank in the settlement's.
	 */
	static long[] orderKey(List<List<int[]>> canonical, int r, int part) {
		List<int[]> run = canonical.get(r);
		long s = smallest(run), f = key(run.get(0)[0], run.get(0)[1]);
		int tie = 0;
		for (int k = r - 1; k >= 0; k--) {
			List<int[]> o = canonical.get(k);
			if (smallest(o) != s || key(o.get(0)[0], o.get(0)[1]) != f || o.size() != run.size()) break;
			tie++;
		}
		return new long[] {s, f, run.size(), tie, part};
	}

	static final Comparator<QuayWhole> BY_ORDER = (a, b) -> Arrays.compare(a.order(), b.order());

	private static void sort(List<QuayWhole> parts) { parts.sort(BY_ORDER); }

	static long key(int x, int z) { return ((long) z << 32) ^ (x & 0xffffffffL); }

	private static long key(Pt p) { return key(p.x(), p.z()); }

	static long smallest(List<int[]> cols) {
		long best = Long.MAX_VALUE;
		for (int[] c : cols) best = Math.min(best, key(c[0], c[1]));
		return best;
	}

	/** A closed ring of columns, rotated to start just after a column that is no quay, so a run through the ring's start is not split (the start depends on where the contour was traced from). */
	static List<int[]> breakRing(List<int[]> ring, Predicate<int[]> ok) {
		List<int[]> cols = new ArrayList<>(ring.subList(0, ring.size() - 1));
		for (int k = 0; k < cols.size(); k++) {
			if (ok.test(cols.get(k))) continue;
			List<int[]> out = new ArrayList<>(cols.subList(k + 1, cols.size()));
			out.addAll(cols.subList(0, k));
			return out;
		}
		return ring; // every column is a quay: one closed promenade, cut where the ring starts
	}

	/** The runs of one contour chain, as the whole trace cuts them. */
	static List<List<int[]>> runsOf(List<Pt> chain, Predicate<int[]> ok) {
		List<int[]> cols = StreetCols.columns(chain);
		if (chain.get(0).equals(chain.get(chain.size() - 1))) cols = breakRing(cols, ok);
		List<List<int[]>> out = new ArrayList<>();
		for (List<int[]> whole : StreetCols.runs(cols, ok)) if (whole.size() >= Quays.MIN_LEN) out.add(whole);
		return out;
	}

	private static boolean touches(Chain c, int x0, int z0, int x1, int z1) {
		for (List<int[]> run : c.runs()) if (touches(run, x0, z0, x1, z1)) return true;
		return false;
	}

	private static boolean touches(List<int[]> cols, int x0, int z0, int x1, int z1) {
		for (int[] c : cols) if (c[0] >= x0 && c[0] <= x1 && c[1] >= z0 && c[1] <= z1) return true;
		return false;
	}

	// ---- chains

	/** The chain source of one request: the walker over the cell window, reading through the caller's bank (never kept: a bank serves one thread). */
	private ChainSource source(Bank bank, int[] fetched) {
		ChainSource s = source;
		if (s != null) return s;
		Quays.Near near = memoised(bank, fetched.clone());
		return new ChainSource() {
			@Override public List<Pt> seeds(int ci0, int cj0, int ci1, int cj1) { return Quays.seeds(near, ci0, cj0, ci1, cj1); }

			@Override public List<Pt> distinctSeeds(int ci0, int cj0, int ci1, int cj1) { return Quays.distinctSeeds(near, ci0, cj0, ci1, cj1); }

			@Override public List<Pt> chainThrough(Pt seed) { return Quays.chainThrough(near, area.i0(), area.j0(), area.i1(), area.j1(), seed); }
		};
	}

	/**
	 * The node memo over one bank. {@code fetched} is the node box {@code [minX, minZ, maxX, maxZ]} whose terrain the caller has prefetched; a read
	 * that misses the memo outside it prefetches {@link #WALK_AHEAD} blocks round the node first (a live probe fills a whole chunk per first read,
	 * so a walk reading one node at a time would fill chunks one by one). Only a hint: the answers are the same.
	 */
	private Quays.Near memoised(Bank bank, int[] fetched) {
		return (x, z) -> {
			long k = key(x, z);
			Boolean v = nodes.get(k);
			if (v == null) {
				if (x < fetched[0] || z < fetched[1] || x > fetched[2] || z > fetched[3]) {
					int a = QuayTuning.walkAhead;
					fetched[0] = x - a; fetched[1] = z - a; fetched[2] = x + a; fetched[3] = z + a;
					bank.prefetch(List.of(new int[] {x - a - QUAY_READ, z - a - QUAY_READ, x + a + QUAY_READ, z + a + QUAY_READ}));
				}
				v = bank.near(x, z);
				nodes.put(k, v);
			}
			return v;
		};
	}

	private Chain chainOf(Bank bank, ChainSource src, Pt seed) {
		Long known = pointIndex.get(key(seed));
		if (known != null) return chains.get(known);
		List<Pt> pts = src.chainThrough(seed);
		long id = Long.MAX_VALUE;
		for (Pt p : pts) id = Math.min(id, key(p));
		Chain have = chains.get(id);
		if (have != null) return have;
		List<int[]> boxes = new ArrayList<>(); // what the chain's runs read next (PARREADS, as the whole trace does)
		int h = QUAY_CHAIN_READ;
		for (Pt p : pts) boxes.add(new int[] {p.x() - h, p.z() - h, p.x() + h, p.z() + h});
		bank.prefetch(boxes);
		Chain c = new Chain(id, List.copyOf(pts), runsOf(pts, bank::ok));
		Chain prev = chains.putIfAbsent(id, c);
		if (prev != null) c = prev;
		for (Pt p : pts) pointIndex.put(key(p), id); // after the chain: a thread that finds the id here finds the chain
		return c;
	}

	/** The chains whose runs meet this chain's runs, by id. */
	private List<Long> partnersOf(Bank bank, ChainSource src, Chain c) {
		List<Long> have = partners.get(c.id());
		if (have != null) return have;
		Set<Long> scanned = new TreeSet<>();
		TreeMap<Long, Chain> candidates = new TreeMap<>();
		for (List<int[]> run : c.runs()) {
			for (int[] col : run) {
				int ci0 = Math.max(area.i0(), Math.floorDiv(col[0] - CLUSTER_REACH, Quays.STEP)), ci1 = Math.min(area.i1(), Math.floorDiv(col[0] + CLUSTER_REACH, Quays.STEP));
				int cj0 = Math.max(area.j0(), Math.floorDiv(col[1] - CLUSTER_REACH, Quays.STEP)), cj1 = Math.min(area.j1(), Math.floorDiv(col[1] + CLUSTER_REACH, Quays.STEP));
				if (ci0 > ci1 || cj0 > cj1 || !scanned.add(((ci0 & 0xffffL) << 48) | ((cj0 & 0xffffL) << 32) | ((ci1 & 0xffffL) << 16) | (cj1 & 0xffffL))) continue; // the same cells give the same chains
				for (Pt seed : src.seeds(ci0, cj0, ci1, cj1)) {
					Chain d = chainOf(bank, src, seed);
					if (d.id() != c.id() && !d.runs().isEmpty()) candidates.putIfAbsent(d.id(), d);
				}
			}
		}
		Set<Long> mine = new TreeSet<>();
		for (List<int[]> run : c.runs()) for (int[] col : run) mine.add(key(col[0], col[1]));
		TreeSet<Long> out = new TreeSet<>();
		for (Chain d : candidates.values()) if (meets(mine, d)) out.add(d.id());
		List<Long> list = List.copyOf(out);
		partners.putIfAbsent(c.id(), list);
		return list;
	}

	/** Whether some run column of {@code d} is within MEET_REACH of one of the columns {@code mine}. */
	private static boolean meets(Set<Long> mine, Chain d) {
		for (List<int[]> run : d.runs()) {
			for (int[] e : run) {
				for (int dz = -MEET_REACH; dz <= MEET_REACH; dz++) for (int dx = -MEET_REACH; dx <= MEET_REACH; dx++) if (mine.contains(key(e[0] + dx, e[1] + dz))) return true;
			}
		}
		return false;
	}

	/** The smallest chain id of the connected component of chains whose runs meet. */
	private long clusterId(Bank bank, ChainSource src, Chain start) {
		Long known = clusterOf.get(start.id());
		if (known != null) return known;
		TreeSet<Long> seen = new TreeSet<>();
		List<Long> todo = new ArrayList<>(List.of(start.id()));
		seen.add(start.id());
		while (!todo.isEmpty()) {
			Chain c = chains.get(todo.remove(todo.size() - 1));
			for (long p : partnersOf(bank, src, c)) if (seen.add(p)) todo.add(p);
		}
		long id = seen.first();
		clusterMembers.put(id, List.copyOf(seen)); // before any clusterOf entry: a thread that reads the id there goes on to read the members
		for (long k : seen) clusterOf.put(k, id);
		return id;
	}

	private List<QuayWhole> graded(Bank bank, long clusterId) {
		FutureTask<List<QuayWhole>> task = clusters.get(clusterId);
		if (task == null) {
			FutureTask<List<QuayWhole>> mine = new FutureTask<>(() -> {
				List<List<int[]>> runs = new ArrayList<>();
				for (long id : clusterMembers.get(clusterId)) runs.addAll(chains.get(id).runs());
				return List.copyOf(bank.grade(runs));
			});
			task = clusters.putIfAbsent(clusterId, mine);
			if (task == null) {
				task = mine;
				gradingBy.put(clusterId, Thread.currentThread());
				try {
					mine.run();
				} finally {
					gradingBy.remove(clusterId);
				}
			}
		}
		if (!task.isDone() && gradingBy.get(clusterId) == Thread.currentThread()) {
			throw new IllegalStateException("cluster " + clusterId + " asked for while this thread grades it: Bank.grade must not call back into the trace");
		}
		try {
			return task.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof RuntimeException re) throw re;
			if (e.getCause() instanceof Error er) throw er;
			throw new IllegalStateException(e.getCause());
		}
	}

	/** Test hook: the most chains any traced cluster holds so far. */
	int maxClusterChains() {
		int n = 0;
		for (List<Long> l : clusterMembers.values()) n = Math.max(n, l.size());
		return n;
	}
}
