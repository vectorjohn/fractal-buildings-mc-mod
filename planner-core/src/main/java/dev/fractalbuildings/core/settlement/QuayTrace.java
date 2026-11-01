package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.cache.Fanout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Predicate;

/**
 * The quays of one settlement, traced lazily (QUAYLAZY, docs/plans/quaylazy.md; run by run since QUAYFAST, docs/plans/quayfast.md). A tile
 * asks {@link #near} for the quays within reach of it and pays only for the <b>runs</b> it touches (the stretches of contour where every
 * column can carry a quay) and the runs those meet; {@link #all} is the whole trace. Both give every part exactly as the one global
 * computation would (contour of the whole window, every chain cut into runs, every run graded in canonical order against the earlier
 * ones), because:
 * <ul>
 * <li>a run is a maximal stretch of {@code ok} columns along its chain, and {@code ok} is pointwise: a closed ring is cut by
 * {@link #breakRing} just after a column that is no quay, so its runs do not depend on where the ring starts. The run holding a column is
 * found by walking the contour from that column both ways until {@code ok} fails ({@link #walk}); only a ring that is quay all the way
 * round depends on its start, and that one is taken from the whole chain ({@link Quays#chainThrough});</li>
 * <li>grading a run reads earlier parts only at their columns within {@value #MEET_REACH} blocks of its own ({@code meetPins}: footprint
 * {@code c-2..c+1} and ring {@code c-3..c+2} round each column), and the per-column accumulation ignores layer order, so a run depends on
 * another only through such a column pair. The unit of work is therefore the <b>cluster</b>: runs connected through columns that close.
 * A cluster is graded alone, in canonical order of its own runs, which is a subsequence of the global order;</li>
 * <li>every result carries an order key ({@link #orderKey}), and outputs are sorted by it, never by discovery order.</li>
 * </ul>
 * State is concurrent maps that are never iterated for output, so one trace may serve several threads at once, each with its own
 * {@link Bank}.
 */
final class QuayTrace {
	/** Two runs interact when some column of one is within this many blocks (Chebyshev) of a column of the other (the {@code meetPins} arithmetic above). */
	static final int MEET_REACH = 4;
	/** How far round a run column the cluster scan looks for other runs' contour cells, blocks. */
	static final int CLUSTER_REACH = Quays.STEP;
	/** How far round a contour point its columns' first quay tests read, blocks (as {@code Districts.Build.QUAY_CHAIN_READ}). */
	static final int QUAY_CHAIN_READ = 8;
	/** How far round a lattice node the field reads the exact terrain, blocks (as {@code Districts.Build.QUAY_READ}). */
	static final int QUAY_READ = Quays.OFFSET + 4;

	static {
		// a column of another run within MEET_REACH lies on a segment inside a (closed) cell within MEET_REACH of ours
		if (CLUSTER_REACH < MEET_REACH + Quays.STEP / 2) throw new AssertionError("cluster reach too short");
	}

	/** What the trace needs from its caller (Districts supplies the terrain-reading implementation). A bank serves one thread. */
	interface Bank {
		/** The raw "water within OFFSET" value at a lattice node, false outside the area; no memo (the trace memoises). */
		boolean near(int x, int z);

		/** Whether a column can carry a quay (pointwise). */
		boolean ok(int[] col);

		/**
		 * A cheap screen for {@link #ok}: true whenever {@code ok(new int[] {x, z})} is true, so false means no quay can stand on the column.
		 * Reads no exact terrain (density and quarter fields only). Lets a scan skip columns the full check would reject.
		 */
		default boolean maybeOk(int x, int z) { return true; }

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

	/** How far ahead of a contour walk the exact terrain is prefetched when the walk leaves the box prefetched so far, blocks (a box of about 5 x 5 chunks). */
	static final int WALK_AHEAD = 32; // the default of QuayTuning.walkAhead
	/** How many prefetched boxes a walk remembers (the default of QuayTuning.walkBoxes): a walk goes both ways from where it starts, and with one box every read at the far end missed it and paid a parallel round (CASCADE 2fix: 3,760 rounds for one chain of perf tile 16,-35, 505 with 4). */
	static final int WALK_BOXES = 4;

	/** The traced area: the waterfront tile keys (kept for the caller) and the contour's cell window, inclusive. */
	record Area(TreeSet<Long> wf, int i0, int j0, int i1, int j1) {}

	/** A run: its columns in contour order (either way; grading orients it), id = its smallest column key, kept = at least {@link Quays#MIN_LEN} long. */
	private record Run(long id, List<int[]> cols, boolean kept) {}

	/** {@link #runOfCol} value of an ok column that lies on no run of 2 or more columns. */
	private static final long NO_RUN = Long.MIN_VALUE;

	private final Area area;
	private final ConcurrentHashMap<Long, Boolean> nodes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Boolean> okMemo = new ConcurrentHashMap<>();
	/** Chains of the whole trace ({@link #all}), by the key of their smallest point. */
	private final ConcurrentHashMap<Long, Long> pointIndex = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, List<Pt>> chains = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Run> runs = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Long> runOfCol = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, List<Long>> partners = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Long> clusterOf = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, List<Long>> clusterMembers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, FutureTask<List<QuayWhole>>> clusters = new ConcurrentHashMap<>();
	/** The thread grading each cluster right now (so a re-entrant request throws instead of waiting on itself). */
	private final ConcurrentHashMap<Long, Thread> gradingBy = new ConcurrentHashMap<>();

	QuayTrace(Area area) { this.area = area; }

	Area area() { return area; }

	/** The quays with a column within {@code m} blocks (Chebyshev) of the window {@code [minX, maxX] x [minZ, maxZ]}, in canonical order. */
	List<QuayWhole> near(Bank bank, int minX, int minZ, int maxX, int maxZ, int m) {
		int r = m + Quays.STEP;
		int ci0 = Math.max(area.i0(), Math.floorDiv(minX - r, Quays.STEP)), ci1 = Math.min(area.i1(), Math.floorDiv(maxX + r, Quays.STEP));
		int cj0 = Math.max(area.j0(), Math.floorDiv(minZ - r, Quays.STEP)), cj1 = Math.min(area.j1(), Math.floorDiv(maxZ + r, Quays.STEP));
		if (ci0 > ci1 || cj0 > cj1) return List.of();
		// PARREADS: the scan reads every node of these cells, each within QUAY_READ; one call, so a live probe fills the chunks in parallel
		int[] scan = {ci0 * Quays.STEP, cj0 * Quays.STEP, (ci1 + 1) * Quays.STEP, (cj1 + 1) * Quays.STEP}; // the nodes the scan reads
		if (QuayTuning.scanBox) bank.prefetch(List.of(new int[] {scan[0] - QUAY_READ, scan[1] - QUAY_READ, scan[2] + QUAY_READ, scan[3] + QUAY_READ}));
		Quays.Near near = memoised(bank, scan);
		Predicate<int[]> ok = okOf(bank);
		int x0 = minX - m, z0 = minZ - m, x1 = maxX + m, z1 = maxZ + m;
		// every contour column within m of the window lies on a segment of a scan cell; each ok one is on at most one run. Walks are short
		// (a run, not its chain), so they run here on the caller, serially; only the cluster gradings fan out
		TreeSet<Long> wanted = new TreeSet<>();
		for (Pt[] sg : segmentsIn(near, ci0, cj0, ci1, cj1)) {
			List<int[]> cols = StreetCols.columns(List.of(sg[0], sg[1]));
			for (int t = 0; t < cols.size(); t++) {
				int[] c = cols.get(t);
				if (c[0] < x0 || c[0] > x1 || c[1] < z0 || c[1] > z1) continue;
				Run run = runAt(near, ok, sg, t);
				if (run != null && run.kept()) wanted.add(clusterId(near, ok, run));
			}
		}
		List<QuayWhole> out = new ArrayList<>();
		for (List<QuayWhole> parts : gradeAll(new Forks(bank), new ArrayList<>(wanted))) {
			for (QuayWhole q : parts) if (touches(q.cols(), x0, z0, x1, z1)) out.add(q);
		}
		sort(out);
		return out;
	}

	/** Every quay of the area in canonical order: the whole trace (every node of the window, every chain, every cluster graded). */
	List<QuayWhole> all(Bank bank) {
		Quays.Near near = memoised(bank, new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}); // the bank's row prefetch covers the scan: no walk-ahead
		Predicate<int[]> ok = okOf(bank);
		TreeSet<Long> kept = new TreeSet<>(), done = new TreeSet<>();
		for (Pt seed : Quays.seeds(near, area.i0(), area.j0(), area.i1(), area.j1())) {
			Long known = pointIndex.get(key(seed));
			if (known != null && done.contains(known)) continue; // this call has cut that chain already
			List<Pt> pts = chainOf(bank, near, seed);
			if (!done.add(pointIndex.get(key(seed)))) continue;
			for (List<int[]> run : allRunsOf(pts, ok)) {
				Run rn = register(run);
				if (rn.kept()) kept.add(rn.id());
			}
		}
		TreeSet<Long> ids = new TreeSet<>();
		for (long id : kept) ids.add(clusterId(near, ok, runs.get(id)));
		List<QuayWhole> out = new ArrayList<>();
		for (List<QuayWhole> parts : gradeAll(new Forks(bank), new ArrayList<>(ids))) out.addAll(parts);
		sort(out);
		return out;
	}

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

	private static long key(int[] c) { return key(c[0], c[1]); }

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
		List<List<int[]>> out = new ArrayList<>();
		for (List<int[]> whole : allRunsOf(chain, ok)) if (whole.size() >= Quays.MIN_LEN) out.add(whole);
		return out;
	}

	/** {@link #runsOf} before the length filter: every maximal stretch of ok columns of 2 or more. */
	static List<List<int[]>> allRunsOf(List<Pt> chain, Predicate<int[]> ok) {
		List<int[]> cols = StreetCols.columns(chain);
		if (chain.get(0).equals(chain.get(chain.size() - 1))) cols = breakRing(cols, ok);
		return StreetCols.runs(cols, ok);
	}

	private static boolean touches(List<int[]> cols, int x0, int z0, int x1, int z1) {
		for (int[] c : cols) if (c[0] >= x0 && c[0] <= x1 && c[1] >= z0 && c[1] <= z1) return true;
		return false;
	}

	// ---- reads

	/**
	 * The node memo over one bank. {@code fetched} is the node box {@code [minX, minZ, maxX, maxZ]} whose terrain the caller has prefetched; a read
	 * that misses the memo outside it and the last {@link #WALK_BOXES} boxes prefetched prefetches {@link #WALK_AHEAD} blocks round the node first (a live probe fills a whole chunk per first read,
	 * so a walk reading one node at a time would fill chunks one by one). Only a hint: the answers are the same. Serves one thread.
	 */
	private Quays.Near memoised(Bank bank, int[] fetched) {
		java.util.ArrayDeque<int[]> boxes = new java.util.ArrayDeque<>();
		boxes.add(fetched.clone());
		return (x, z) -> {
			long k = key(x, z);
			Boolean v = nodes.get(k);
			if (v == null) {
				boolean in = false;
				for (int[] f : boxes) in |= x >= f[0] && z >= f[1] && x <= f[2] && z <= f[3];
				if (!in) {
					int a = QuayTuning.walkAhead;
					if (boxes.size() >= Math.max(1, QuayTuning.walkBoxes)) boxes.poll();
					boxes.add(new int[] {x - a, z - a, x + a, z + a});
					QuayTuning.walkPrefetches.incrementAndGet();
					bank.prefetch(List.of(new int[] {x - a - QUAY_READ, z - a - QUAY_READ, x + a + QUAY_READ, z + a + QUAY_READ}));
				}
				v = bank.near(x, z);
				nodes.put(k, v);
			}
			return v;
		};
	}

	/** The quay test over one bank, memoised in the trace (pure: a race computes the same value twice). */
	private Predicate<int[]> okOf(Bank bank) {
		return c -> {
			long k = key(c);
			Boolean v = okMemo.get(k);
			if (v == null) {
				v = bank.ok(c);
				okMemo.put(k, v);
			}
			return v;
		};
	}

	// ---- runs

	private static List<Pt[]> segmentsIn(Quays.Near near, int ci0, int cj0, int ci1, int cj1) { return Quays.segmentsIn(near, ci0, cj0, ci1, cj1); }

	/** The run holding column {@code t} of segment {@code a -> b} (an ok column): {@link Quays#runThrough}, over the area's cell window. */
	private Quays.RunWalk walk(Quays.Near near, Predicate<int[]> ok, Pt a, Pt b, int t) {
		return Quays.runThrough(near, ok, area.i0(), area.j0(), area.i1(), area.j1(), a, b, t);
	}

	/** The run through column {@code t} of segment {@code sg}, or null when that column is no quay or lies on no run of 2 or more columns. */
	private Run runAt(Quays.Near near, Predicate<int[]> ok, Pt[] sg, int t) {
		int[] c = StreetCols.columns(List.of(sg[0], sg[1])).get(t);
		long ck = key(c);
		Long id = runOfCol.get(ck);
		if (id != null) return id == NO_RUN ? null : runs.get(id);
		if (!ok.test(c)) return null;
		Quays.RunWalk w = walk(near, ok, sg[0], sg[1], t);
		List<int[]> cols = w.cols();
		if (w.wholeRing()) { // the ring's one run starts where the ring does: take it from the whole chain, as the whole trace does
			cols = null;
			for (List<int[]> run : allRunsOf(Quays.chainThrough(near, area.i0(), area.j0(), area.i1(), area.j1(), sg[0]), ok)) {
				for (int[] e : run) if (e[0] == c[0] && e[1] == c[1]) { cols = run; break; }
				if (cols != null) break;
			}
			if (cols == null) throw new IllegalStateException("column " + Arrays.toString(c) + " is on no run of its all-quay ring");
		}
		if (cols.size() < 2) {
			runOfCol.putIfAbsent(ck, NO_RUN);
			return null;
		}
		return register(cols);
	}

	/** Records a run (and the run of each of its columns); the first registration wins, and every registration of one run is the same run. */
	private Run register(List<int[]> cols) {
		long id = smallest(cols);
		Run run = runs.get(id);
		if (run == null) {
			Run mine = new Run(id, List.copyOf(cols), cols.size() >= Quays.MIN_LEN);
			run = runs.putIfAbsent(id, mine);
			if (run == null) run = mine;
		}
		for (int[] e : run.cols()) runOfCol.put(key(e), id); // after the run: a thread that finds the id here finds the run
		return run;
	}

	/** The whole chain through a contour point (the whole trace), memoised by the key of its smallest point. */
	private List<Pt> chainOf(Bank bank, Quays.Near near, Pt seed) {
		Long known = pointIndex.get(key(seed));
		if (known != null) return chains.get(known);
		List<Pt> pts = Quays.chainThrough(near, area.i0(), area.j0(), area.i1(), area.j1(), seed);
		long id = Long.MAX_VALUE;
		for (Pt p : pts) id = Math.min(id, key(p));
		List<int[]> boxes = new ArrayList<>(); // what the chain's runs read next (PARREADS)
		int h = QUAY_CHAIN_READ;
		for (Pt p : pts) boxes.add(new int[] {p.x() - h, p.z() - h, p.x() + h, p.z() + h});
		bank.prefetch(boxes);
		List<Pt> c = List.copyOf(pts);
		List<Pt> prev = chains.putIfAbsent(id, c);
		if (prev != null) c = prev;
		for (Pt p : pts) pointIndex.put(key(p), id);
		return c;
	}

	// ---- clusters

	/** The kept runs other than {@code run} with a column within {@value #MEET_REACH} blocks (Chebyshev) of one of its columns, by id. */
	private List<Long> partnersOf(Quays.Near near, Predicate<int[]> ok, Run run) {
		List<Long> have = partners.get(run.id());
		if (have != null) return have;
		TreeSet<Long> mine = new TreeSet<>();
		for (int[] c : run.cols()) mine.add(key(c));
		TreeSet<Long> scanned = new TreeSet<>();
		TreeMap<Long, Object[]> candidates = new TreeMap<>(); // column key -> {segment, index}, first seen
		for (int[] col : run.cols()) {
			int ci0 = Math.max(area.i0(), Math.floorDiv(col[0] - CLUSTER_REACH, Quays.STEP)), ci1 = Math.min(area.i1(), Math.floorDiv(col[0] + CLUSTER_REACH, Quays.STEP));
			int cj0 = Math.max(area.j0(), Math.floorDiv(col[1] - CLUSTER_REACH, Quays.STEP)), cj1 = Math.min(area.j1(), Math.floorDiv(col[1] + CLUSTER_REACH, Quays.STEP));
			for (int j = cj0; j <= cj1; j++) {
				for (int i = ci0; i <= ci1; i++) {
					if (!scanned.add(key(i, j))) continue;
					for (Pt[] sg : segmentsIn(near, i, j, i, j)) {
						List<int[]> cols = StreetCols.columns(List.of(sg[0], sg[1]));
						for (int t = 0; t < cols.size(); t++) {
							long ek = key(cols.get(t));
							if (!mine.contains(ek) && !candidates.containsKey(ek) && meets(mine, cols.get(t))) candidates.put(ek, new Object[] {sg, t});
						}
					}
				}
			}
		}
		TreeSet<Long> out = new TreeSet<>();
		for (Object[] e : candidates.values()) {
			Run d = runAt(near, ok, (Pt[]) e[0], (Integer) e[1]);
			if (d != null && d.kept() && d.id() != run.id()) out.add(d.id());
		}
		List<Long> list = List.copyOf(out);
		partners.putIfAbsent(run.id(), list);
		return list;
	}

	/** Whether column {@code e} is within MEET_REACH of one of the columns {@code mine}. */
	private static boolean meets(TreeSet<Long> mine, int[] e) {
		for (int dz = -MEET_REACH; dz <= MEET_REACH; dz++) for (int dx = -MEET_REACH; dx <= MEET_REACH; dx++) if (mine.contains(key(e[0] + dx, e[1] + dz))) return true;
		return false;
	}

	/** The smallest run id of the connected component of kept runs that meet. */
	private long clusterId(Quays.Near near, Predicate<int[]> ok, Run start) {
		Long known = clusterOf.get(start.id());
		if (known != null) return known;
		TreeSet<Long> seen = new TreeSet<>();
		List<Long> todo = new ArrayList<>(List.of(start.id()));
		seen.add(start.id());
		while (!todo.isEmpty()) {
			Run r = runs.get(todo.remove(todo.size() - 1));
			for (long p : partnersOf(near, ok, r)) if (seen.add(p)) todo.add(p);
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
				List<List<int[]>> cols = new ArrayList<>();
				for (long id : clusterMembers.get(clusterId)) cols.add(runs.get(id).cols());
				return List.copyOf(bank.grade(cols));
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

	/** Test hook: the most runs any traced cluster holds so far. */
	int maxClusterRuns() {
		int n = 0;
		for (List<Long> l : clusterMembers.values()) n = Math.max(n, l.size());
		return n;
	}

	/** Test hook (after {@link #all}): whether two kept runs of one chain fell in different clusters (run clusters refine chain clusters). */
	boolean splitsAChain() {
		for (List<Pt> pts : new TreeMap<>(chains).values()) {
			TreeSet<Long> ids = new TreeSet<>();
			for (int[] c : StreetCols.columns(pts)) {
				Long r = runOfCol.get(key(c));
				if (r != null && r != NO_RUN && runs.get(r).kept()) {
					Long cl = clusterOf.get(r);
					if (cl != null) ids.add(cl);
				}
			}
			if (ids.size() > 1) return true;
		}
		return false;
	}
}
