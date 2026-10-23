package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.tiers.WallRing;
import dev.fractalbuildings.core.terrain.Grading;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * The heights at which a settlement's whole streets meet (JUNCTION). A <i>whole</i> street has the same columns in every tile that
 * draws it: the skeleton main roads and paths, the lattice's arterials and border streets, and the grid lines' runs. Each is graded
 * once over its whole length ({@code Districts}), so before this field no street knew another's height and two streets that met
 * could do so 20 blocks apart.
 *
 * <p>Wherever the footprints of two whole streets overlap (a crossing, a street ending on another, two lattice segments meeting at
 * a corner) the overlapping stretch of each centre line is a <i>pad</i>: the columns whose footprint touches the other street's.
 * Pads that touch (through shared overlaps) form one <i>junction</i>, and every pad of a junction is pinned flat at the junction's
 * one level ({@code Grading.profile(.., pins)}), so every cell the streets share has the same surface on both. The level is the
 * median exact ground over the pads, made grade-feasible along every street between neighbouring junctions: the rise a street
 * can make between two pads (1 in {@value Grading#GRADE_RUN} per anchor gap, as the grader allows) bounds the difference of their
 * levels, and the levels are the midpoint of the largest feasible field below the ground and the smallest above it (two
 * multi-source shortest-path sweeps over the junction graph), which is feasible and equals the ground where the ground allows.
 *
 * <p>A pure function of the source and the exact terrain, built for the whole settlement at once: every tile reads the same pins,
 * so a street's profile, its pads and its junction levels do not depend on which tile asks or in which order. Pins are looked up by
 * street key and column, so a tile's grid run cut by its window (it follows a line {@code Districts.GRID_REACH} beyond the tile)
 * gets the same pins as the whole run.
 *
 * <p>Cost: the street lattice's runs over the planned box (a few tens of thousands of edge tests in a metropolis), one pass of a
 * bucket raster over the whole streets' columns, and a handful of exact probes per junction.
 */
public final class JunctionField {
	/** A whole street: pin key, width and centre-line columns (in the order {@code StreetCols.columns} gives). */
	record Whole(String key, int width, List<int[]> cols, TreeMap<Integer, Integer> gate) {
		Whole(String key, int width, List<int[]> cols) { this(key, width, cols, null); }
	}

	/** A junction as the field decided it: its level, the ground median it started from, and its pads as {street key, first column x, z, last column x, z}. */
	public record Junction(int level, int ground, List<String> pads) {}

	private static final JunctionField EMPTY = new JunctionField(new TreeMap<>(), List.of(), List.of(), new int[1], new TreeMap<>(), 0, new int[0][], new boolean[0][]);

	private final TreeMap<String, TreeMap<Long, Integer>> pins;
	private final List<Junction> junctions;
	/** The whole streets, the first global column index of each, the column buckets ({@link #B} blocks) and the largest half width. */
	private final List<Whole> ws;
	private final int[] off;
	private final TreeMap<Long, int[]> buckets;
	private final int maxH;
	/** Each whole street's graded profile ({@code Grading.profile} with its pins) and its columns over the cut and fill limit. */
	private final int[][] ys;
	private final boolean[][] steep;
	private final TreeMap<String, List<Integer>> byKey = new TreeMap<>();

	static final int B = 16;

	private JunctionField(TreeMap<String, TreeMap<Long, Integer>> pins, List<Junction> junctions, List<Whole> ws, int[] off, TreeMap<Long, int[]> buckets, int maxH, int[][] ys, boolean[][] steep) {
		this.pins = pins;
		this.junctions = junctions;
		this.ws = ws;
		this.off = off;
		this.buckets = buckets;
		this.maxH = maxH;
		this.ys = ys;
		this.steep = steep;
		for (int a = 0; a < ws.size(); a++) byKey.computeIfAbsent(ws.get(a).key(), k -> new ArrayList<>()).add(a);
	}

	/** The pin key of a lattice line (tile border or grid line): a line of constant x for {@code axis} {@link DistrictGrid#AXIS_X}. */
	public static String lineKey(int axis, int fixed) { return "L" + axis + ":" + fixed; }

	/** The pin key of a skeleton road. */
	public static String roadKey(String roadId) { return "R" + roadId; }

	static long pack(int x, int z) { return ((long) z << 32) ^ (x & 0xffffffffL); }

	/** The junctions in the order they were found (for tests and the CLI). */
	public List<Junction> junctions() { return junctions; }

	/**
	 * The pins of a street's columns: column index to level for each column in a pad of the street with this key, or null when none
	 * is. The columns may be any stretch of the whole street (a tile's piece, a grid run cut by the tile's window).
	 */
	public TreeMap<Integer, Integer> pins(String key, List<int[]> cols) {
		TreeMap<Long, Integer> m = key == null ? null : pins.get(key);
		if (m == null) return null;
		TreeMap<Integer, Integer> out = null;
		for (int i = 0; i < cols.size(); i++) {
			Integer y = m.get(pack(cols.get(i)[0], cols.get(i)[1]));
			if (y == null) continue;
			if (out == null) out = new TreeMap<>();
			out.put(i, y);
		}
		return out;
	}

	/** A stretch of a whole street's graded profile: the height of each column and whether it needs more cut or fill than allowed. */
	public record Slice(int[] y, boolean[] steep) {}

	/**
	 * The graded profile of these columns of the whole street with this key (a tile's piece of it, or a grid run cut by the tile's
	 * window), or null when no whole street of that key holds them all, in order.
	 */
	public Slice profile(String key, List<int[]> cols) {
		List<Integer> cand = key == null || cols.isEmpty() ? null : byKey.get(key);
		if (cand == null) return null;
		int[] c0 = cols.get(0);
		for (int a : cand) {
			List<int[]> wc = ws.get(a).cols();
			for (int s = 0; s + cols.size() <= wc.size(); s++) {
				if (wc.get(s)[0] != c0[0] || wc.get(s)[1] != c0[1]) continue;
				boolean same = true;
				for (int i = 1; i < cols.size() && same; i++) same = wc.get(s + i)[0] == cols.get(i)[0] && wc.get(s + i)[1] == cols.get(i)[1];
				if (same) return new Slice(java.util.Arrays.copyOfRange(ys[a], s, s + cols.size()), java.util.Arrays.copyOfRange(steep[a], s, s + cols.size()));
			}
		}
		return null;
	}

	/**
	 * The lowest and highest surface the whole streets have on a cell ({@code {lo, hi, end}}; for each street the first of its columns whose
	 * footprint covers the cell gives the level, as the grader builds it; end 1 when that column is one of a street's last {@code width / 2 + 1}
	 * at either end, so the cell is on its end face), or null when no whole street covers it.
	 */
	public int[] levelsAt(int x, int z) {
		TreeMap<Integer, Integer> first = firstClaims(x, z);
		if (first == null) return null;
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, end = 0;
		for (var e : first.entrySet()) {
			int y = ys[e.getKey()][e.getValue()], h = ws.get(e.getKey()).width() / 2;
			lo = Math.min(lo, y);
			hi = Math.max(hi, y);
			if (e.getValue() <= h || e.getValue() >= ys[e.getKey()].length - 1 - h) end = 1;
		}
		return new int[] {lo, hi, end};
	}

	/**
	 * The footprint of a column of a street of this width (centre {@code (x, z)}) overlaps two whole streets that no one level is within a
	 * block of (two streets side by side at different heights): a street there could meet neither.
	 */
	public boolean splits(int x, int z, int width) {
		int h = width / 2;
		TreeMap<Integer, int[]> range = null; // street -> {lo, hi} over the footprint's cells
		for (int cz = z - h; cz < z - h + width; cz++) {
			for (int cx = x - h; cx < x - h + width; cx++) {
				TreeMap<Integer, Integer> first = firstClaims(cx, cz);
				if (first == null) continue;
				if (range == null) range = new TreeMap<>();
				for (var e : first.entrySet()) {
					int y = ys[e.getKey()][e.getValue()];
					int[] r = range.computeIfAbsent(e.getKey(), k -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
					r[0] = Math.min(r[0], y);
					r[1] = Math.max(r[1], y);
				}
			}
		}
		if (range == null || range.size() < 2) return false;
		for (var a : range.entrySet()) {
			for (var b : range.entrySet()) {
				if (a.getKey() >= b.getKey()) continue;
				if (Math.max(a.getValue()[1], b.getValue()[1]) - Math.min(a.getValue()[0], b.getValue()[0]) > 2) return true;
			}
		}
		return false;
	}

	/** For each whole street covering the cell, the index of its first column that does (the one that builds the cell); null when none. */
	private TreeMap<Integer, Integer> firstClaims(int x, int z) {
		TreeMap<Integer, Integer> first = null;
		for (int bz = Math.floorDiv(z - maxH, B); bz <= Math.floorDiv(z + maxH, B); bz++) {
			for (int bx = Math.floorDiv(x - maxH, B); bx <= Math.floorDiv(x + maxH, B); bx++) {
				int[] here = buckets.get(pack(bx, bz));
				if (here == null) continue;
				for (int g : here) {
					int a = streetOf(g);
					Whole w = ws.get(a);
					int[] c = w.cols().get(g - off[a]);
					int h = w.width() / 2;
					if (x < c[0] - h || x > c[0] - h + w.width() - 1 || z < c[1] - h || z > c[1] - h + w.width() - 1) continue;
					if (first == null) first = new TreeMap<>();
					first.merge(a, g - off[a], Math::min);
				}
			}
		}
		return first;
	}

	private int streetOf(int g) {
		int l = 0, r = ws.size() - 1;
		while (l < r) {
			int m = (l + r + 1) >>> 1;
			if (off[m] <= g) l = m; else r = m - 1;
		}
		return l;
	}

	static List<dev.fractalbuildings.core.terrain.LatticePath.Pt> lattice(List<int[]> cols) {
		List<dev.fractalbuildings.core.terrain.LatticePath.Pt> out = new ArrayList<>();
		for (Pt p : StreetCols.vertices(cols)) out.add(new dev.fractalbuildings.core.terrain.LatticePath.Pt(p.x(), p.z()));
		return out;
	}

	/** The whole streets of the settlement, as district detail draws them (any tile, any window), in a fixed order. */
	static List<Whole> wholeStreets(SkeletonSource src, TerrainProbe exact) {
		RoadNetwork net = src.network();
		int[] box = net.box();
		List<Whole> out = new ArrayList<>();
		if (box[2] == 0 || box[3] == 0) return out;
		StreetRules rules = new StreetRules(src);
		boolean lattice = rules.lattice();
		// skeleton main roads and paths (Districts.nearbyRoads, every member cell)
		List<Cell> cells = new ArrayList<>(src.site().cells());
		cells.sort(CellGeometry::compare);
		for (Cell c : cells) {
			for (Road r : src.skeleton(c).roads()) {
				if (r.kind() == RoadKind.ARTERIAL || (lattice && !net.reaches(r))) continue;
				out.add(new Whole(roadKey(r.id()), r.width(), StreetCols.columns(r.points())));
			}
		}
		int x0 = box[0], z0 = box[1], x1 = box[0] + box[2] - 1, z1 = box[1] + box[3] - 1;
		// lattice segments (Districts.borderStreets)
		Arterials art = rules.arterials();
		for (DistrictGrid.Segment s : art.segmentsIn(x0, z0, x1, z1)) {
			RoadKind k = rules.borderKind(s);
			if (k == null) continue;
			String key = lineKey(s.axis(), s.fixed());
			if (k == RoadKind.ARTERIAL) { out.add(new Whole(key, art.width(), StreetCols.columns(art.points(s, s.from(), s.to())))); continue; }
			if (lattice) { out.add(new Whole(key, StreetRules.BORDER_WIDTH, StreetCols.columns(art.points(s, s.from(), s.to())))); continue; }
			for (int[] run : runs(s.from(), s.to(), (a, b) -> StreetRules.borderEdgeOk(exact, Arterials.ax(s, a), Arterials.az(s, a), Arterials.ax(s, b), Arterials.az(s, b)))) {
				out.add(new Whole(key, StreetRules.BORDER_WIDTH, StreetCols.columns(List.of(new Pt(Arterials.ax(s, run[0]), Arterials.az(s, run[0])), new Pt(Arterials.ax(s, run[1]), Arterials.az(s, run[1]))))));
			}
		}
		// grid lines (Districts.gridStreets): every line of every tile interval over the box, followed as far as any tile's window reaches
		DistrictGrid grid = src.grid();
		int reach = Districts.GRID_REACH + 2 * StreetRules.NODE;
		for (int axis = 0; axis < 2; axis++) {
			boolean vertical = axis == DistrictGrid.AXIS_X;
			int kLo = grid.indexOf(axis, vertical ? x0 : z0), kHi = grid.indexOf(axis, vertical ? x1 : z1);
			int lo = Math.floorDiv((vertical ? z0 : x0) - reach, StreetRules.NODE) * StreetRules.NODE, hi = Math.floorDiv((vertical ? z1 : x1) + reach, StreetRules.NODE) * StreetRules.NODE;
			for (int k = kLo; k <= kHi; k++) {
				for (int pos : rules.gridLines(axis, k)) {
					DistrictGrid.Segment s = new DistrictGrid.Segment(axis, 0, 0, pos, lo, hi);
					final int ax = axis;
					for (int[] run : runs(lo, hi, (a, b) -> rules.gridSideEdgeOk(exact, ax, pos, a, b) && !rules.nearAbsentBorder(Arterials.ax(s, a), Arterials.az(s, a), 0) && !rules.nearAbsentBorder(Arterials.ax(s, b), Arterials.az(s, b), 0))) {
						out.add(new Whole(lineKey(axis, pos), StreetRules.GRID_WIDTH, StreetCols.columns(List.of(new Pt(Arterials.ax(s, run[0]), Arterials.az(s, run[0])), new Pt(Arterials.ax(s, run[1]), Arterials.az(s, run[1]))))));
					}
				}
			}
		}
		return out;
	}

	interface Edge { boolean ok(int a, int b); }

	/** Runs {from, to} of consecutive ok edges between the {@value StreetRules#NODE}-block nodes (as {@code Districts.nodeRuns}). */
	static List<int[]> runs(int from, int to, Edge test) {
		List<int[]> out = new ArrayList<>();
		int none = Integer.MIN_VALUE, start = none;
		for (int a = from; a < to; a += StreetRules.NODE) {
			int b = Math.min(to, a + StreetRules.NODE);
			if (test.ok(a, b)) {
				if (start == none) start = a;
				if (b >= to) { out.add(new int[] {start, b}); start = none; }
			} else if (start != none) {
				out.add(new int[] {start, a});
				start = none;
			}
		}
		return out;
	}

	/** Builds the field of a settlement. */
	public static JunctionField build(SkeletonSource src, TerrainProbe probe) {
		TerrainProbe exact = new dev.fractalbuildings.core.probe.MemoProbe(probe); // the build reads most columns more than once
		List<Whole> ws = wholeStreets(src, exact);
		List<List<WallRing.Piece>> rings = Districts.wallRings(src);
		if (rings.isEmpty()) return of(ws, exact);
		int[][] levels = new int[rings.size()][];
		List<Whole> gated = new ArrayList<>();
		for (Whole w : ws) {
			TreeMap<Integer, Integer> g = null;
			for (int k = 0; k < rings.size(); k++) {
				final int kk = k;
				g = Districts.ringGatePins(rings.get(k), () -> {
					if (levels[kk] == null) levels[kk] = WallRing.walkLevels(rings.get(kk), q -> groundTop(exact, q.rect()));
					return levels[kk];
				}, w.cols(), g);
			}
			gated.add(g == null ? w : new Whole(w.key(), w.width(), w.cols(), g));
		}
		return of(gated, exact);
	}

	/** Highest exact ground over a rectangle (what district detail's {@code groundTop} gives a ring lot). */
	static int groundTop(TerrainProbe exact, Rect r) {
		int hi = Integer.MIN_VALUE;
		for (int z = r.z(); z < r.z() + r.d(); z++) for (int x = r.x(); x < r.x() + r.w(); x++) hi = Math.max(hi, exact.floorY(x, z));
		return hi;
	}

	/** The field of a given set of whole streets (tests build small ones by hand). */
	static JunctionField of(List<Whole> ws, TerrainProbe exact) {
		int n = ws.size();
		if (n == 0) return EMPTY;
		int[] off = new int[n + 1];
		int maxH = 0;
		for (int a = 0; a < n; a++) {
			off[a + 1] = off[a] + ws.get(a).cols().size();
			maxH = Math.max(maxH, ws.get(a).width() / 2);
		}
		int total = off[n];
		// bucket raster of every centre-line column
		TreeMap<Long, List<Integer>> buckets = new TreeMap<>();
		for (int a = 0; a < n; a++) {
			List<int[]> cols = ws.get(a).cols();
			for (int i = 0; i < cols.size(); i++) buckets.computeIfAbsent(pack(Math.floorDiv(cols.get(i)[0], B), Math.floorDiv(cols.get(i)[1], B)), k -> new ArrayList<>()).add(off[a] + i);
		}
		int[] streetOf = new int[total];
		for (int a = 0; a < n; a++) for (int g = off[a]; g < off[a + 1]; g++) streetOf[g] = a;
		// contacts: a column of one street whose footprint overlaps a footprint of another street
		int[] parent = new int[total];
		for (int g = 0; g < total; g++) parent[g] = g;
		boolean[] contact = new boolean[total];
		for (int a = 0; a < n; a++) {
			Whole wa = ws.get(a);
			int ha = wa.width() / 2, r = ha + maxH + 1, na = wa.cols().size();
			for (int i = 0; i < wa.cols().size(); i++) {
				int[] c = wa.cols().get(i);
				for (long bz = Math.floorDiv(c[1] - r, B); bz <= Math.floorDiv(c[1] + r, B); bz++) {
					for (long bx = Math.floorDiv(c[0] - r, B); bx <= Math.floorDiv(c[0] + r, B); bx++) {
						List<Integer> here = buckets.get(pack((int) bx, (int) bz));
						if (here == null) continue;
						for (int g : here) {
							int b = streetOf[g];
							if (b <= a) continue;
							int[] o = ws.get(b).cols().get(g - off[b]);
							int hb = ws.get(b).width() / 2, reachAB = ha + hb, ax = Math.abs(o[0] - c[0]), az = Math.abs(o[1] - c[1]);
							if (ax > reachAB || az > reachAB) {
								// footprints that touch across a cell edge meet only where one street ends against the other (its end face)
								boolean touch = (ax <= reachAB + 1 && az <= reachAB) || (ax <= reachAB && az <= reachAB + 1);
								int j = g - off[b], nb = ws.get(b).cols().size();
								if (!touch || !(i <= ha || i >= na - 1 - ha || j <= hb || j >= nb - 1 - hb)) continue;
							}
							contact[off[a] + i] = true;
							contact[g] = true;
							union(parent, off[a] + i, g);
						}
					}
				}
			}
		}
		// gate passages are pads too, at the passage floor, which no junction moves
		int[] hard = new int[total];
		java.util.Arrays.fill(hard, Integer.MIN_VALUE);
		for (int a = 0; a < n; a++) {
			if (ws.get(a).gate() == null) continue;
			for (var e : ws.get(a).gate().entrySet()) {
				contact[off[a] + e.getKey()] = true;
				hard[off[a] + e.getKey()] = e.getValue();
			}
		}
		// pads: maximal runs of contact columns along a street (one junction each, joined through their contacts)
		List<int[]> pads = new ArrayList<>(); // {street, first index, last index}
		for (int a = 0; a < n; a++) {
			int len = ws.get(a).cols().size();
			for (int i = 0; i < len; ) {
				if (!contact[off[a] + i]) { i++; continue; }
				int j = i;
				while (j + 1 < len && contact[off[a] + j + 1]) { union(parent, off[a] + j, off[a] + j + 1); j++; }
				pads.add(new int[] {a, i, j});
				i = j + 1;
			}
		}
		// junction ids in order of first appearance
		TreeMap<Integer, Integer> idOf = new TreeMap<>();
		int[] padJ = new int[pads.size()];
		for (int p = 0; p < pads.size(); p++) {
			int root = find(parent, off[pads.get(p)[0]] + pads.get(p)[1]);
			Integer id = idOf.get(root);
			if (id == null) { id = idOf.size(); idOf.put(root, id); }
			padJ[p] = id;
		}
		int m = idOf.size();
		// ground: median exact ground (deck height over water) of every pad column
		List<List<Integer>> samples = new ArrayList<>();
		for (int j = 0; j < m; j++) samples.add(new ArrayList<>());
		for (int p = 0; p < pads.size(); p++) {
			int[] pd = pads.get(p);
			List<int[]> cols = ws.get(pd[0]).cols();
			for (int i = pd[1]; i <= pd[2]; i++) {
				int[] c = cols.get(i);
				samples.get(padJ[p]).add(exact.waterDepth(c[0], c[1]) > 0 ? exact.surfaceY(c[0], c[1]) + Grading.BRIDGE_CLEARANCE : exact.floorY(c[0], c[1]));
			}
		}
		// bridge decks: the grader keeps every anchor at or above the decks of the street's water spans less the grade between them,
		// pins included, so a junction stands at least that high on every street through it
		int[] deckFloor = new int[m];
		java.util.Arrays.fill(deckFloor, Integer.MIN_VALUE / 2);
		boolean[][] anchors = new boolean[n][];
		for (int a = 0, p0 = 0; a < n; a++) {
			int p1 = p0;
			while (p1 < pads.size() && pads.get(p1)[0] == a) p1++;
			if (p1 > p0) {
				anchors[a] = new boolean[ws.get(a).cols().size()];
				int[] floor = anchorsAndDecks(ws.get(a).cols(), contact, off[a], exact, anchors[a]);
				if (floor != null) {
					for (int p = p0; p < p1; p++) for (int i = pads.get(p)[1]; i <= pads.get(p)[2]; i++) deckFloor[padJ[p]] = Math.max(deckFloor[padJ[p]], floor[i]);
				}
			}
			p0 = p1;
		}
		int[] gate = new int[m];
		java.util.Arrays.fill(gate, Integer.MIN_VALUE);
		for (int p = 0; p < pads.size(); p++) {
			int[] pd = pads.get(p);
			for (int i = pd[1]; i <= pd[2]; i++) gate[padJ[p]] = Math.max(gate[padJ[p]], hard[off[pd[0]] + i]);
		}
		int[] ground = new int[m];
		for (int j = 0; j < m; j++) {
			List<Integer> s = samples.get(j);
			s.sort(null);
			ground[j] = gate[j] != Integer.MIN_VALUE ? gate[j] : s.get(s.size() / 2);
		}
		// edges: neighbouring pads of different junctions along a street, with the rise the grader allows between them
		TreeMap<Long, Integer> cap = new TreeMap<>();
		int prev = -1;
		for (int p = 0; p < pads.size(); p++) {
			if (prev >= 0 && pads.get(prev)[0] == pads.get(p)[0] && padJ[prev] != padJ[p]) {
				int u = Math.min(padJ[prev], padJ[p]), v = Math.max(padJ[prev], padJ[p]);
				int c = rise(anchors[pads.get(p)[0]], pads.get(prev)[2], pads.get(p)[1]);
				cap.merge(((long) u << 32) | v, c, Math::min);
			}
			prev = p;
		}
		List<List<int[]>> adj = new ArrayList<>();
		for (int j = 0; j < m; j++) adj.add(new ArrayList<>());
		for (var e : cap.entrySet()) {
			int u = (int) (e.getKey() >> 32), v = (int) (long) e.getKey();
			adj.get(u).add(new int[] {v, e.getValue()});
			adj.get(v).add(new int[] {u, e.getValue()});
		}
		int[] below = envelope(ground, adj, 1), above = envelope(ground, adj, -1);
		int[] level = new int[m];
		for (int j = 0; j < m; j++) level[j] = Math.floorDiv(below[j] + above[j], 2);
		// a junction over water stands at least at the deck over it (the grader never lowers a deck): the smallest feasible field above those floors
		int[] decks = envelope(deckFloor, adj, -1);
		for (int j = 0; j < m; j++) level[j] = Math.max(level[j], decks[j]);
		// a gate passage keeps the wall's level: clamp between the largest feasible field under the gates and the smallest over them
		int[] gateLo = new int[m], gateHi = new int[m];
		boolean gated = false;
		for (int j = 0; j < m; j++) {
			boolean g = gate[j] != Integer.MIN_VALUE;
			gated |= g;
			gateLo[j] = g ? gate[j] : Integer.MIN_VALUE / 2;
			gateHi[j] = g ? gate[j] : Integer.MAX_VALUE / 2;
		}
		if (gated) {
			int[] lo = envelope(gateLo, adj, -1), hi = envelope(gateHi, adj, 1);
			for (int j = 0; j < m; j++) level[j] = Math.max(lo[j], Math.min(hi[j], level[j]));
		}
		// pins, by street key and column
		TreeMap<String, TreeMap<Long, Integer>> pins = new TreeMap<>();
		List<List<String>> padText = new ArrayList<>();
		for (int j = 0; j < m; j++) padText.add(new ArrayList<>());
		for (int p = 0; p < pads.size(); p++) {
			int[] pd = pads.get(p);
			Whole w = ws.get(pd[0]);
			TreeMap<Long, Integer> mp = pins.computeIfAbsent(w.key(), k -> new TreeMap<>());
			for (int i = pd[1]; i <= pd[2]; i++) mp.put(pack(w.cols().get(i)[0], w.cols().get(i)[1]), level[padJ[p]]);
			int[] f = w.cols().get(pd[1]), l = w.cols().get(pd[2]);
			padText.get(padJ[p]).add(w.key() + " " + f[0] + "," + f[1] + ".." + l[0] + "," + l[1]);
		}
		List<Junction> js = new ArrayList<>();
		for (int j = 0; j < m; j++) js.add(new Junction(level[j], ground[j], List.copyOf(padText.get(j))));
		// every whole street's profile, graded once with its pads and gate passages pinned (what a tile would grade from the same columns)
		int[][] ys = new int[n][];
		boolean[][] steep = new boolean[n][];
		int[] padLevel = new int[total];
		boolean[] inPad = new boolean[total];
		for (int p = 0; p < pads.size(); p++) for (int i = pads.get(p)[1]; i <= pads.get(p)[2]; i++) { inPad[off[pads.get(p)[0]] + i] = true; padLevel[off[pads.get(p)[0]] + i] = level[padJ[p]]; }
		for (int a = 0; a < n; a++) {
			Whole w = ws.get(a);
			TreeMap<Integer, Integer> pn = null;
			for (int i = 0; i < w.cols().size(); i++) {
				if (!inPad[off[a] + i]) continue;
				if (pn == null) pn = new TreeMap<>();
				pn.put(i, padLevel[off[a] + i]);
			}
			if (w.gate() != null) { if (pn == null) pn = new TreeMap<>(); pn.putAll(w.gate()); }
			Grading.RoadGrade g = Grading.profile(lattice(w.cols()), exact, pn);
			ys[a] = g.profile().y();
			steep[a] = new boolean[w.cols().size()];
			for (Grading.Steep st : g.steep()) for (int i = st.from(); i <= st.to(); i++) steep[a][i] = true;
		}
		TreeMap<Long, int[]> packed = new TreeMap<>();
		for (var e : buckets.entrySet()) packed.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
		return new JunctionField(pins, js, ws, off, packed, maxH, ys, steep);
	}

	/**
	 * The street's anchors as the grader will have them ({@code anchor}, filled: every {@value Grading#SAMPLE_EVERY}th column, the ends,
	 * the ends of water spans and every pinned column), and the lower bound the grader puts on each anchor from its bridge decks (a
	 * water span's deck is its highest water surface plus the clearance, and every anchor keeps at least a deck less the grade between
	 * them); null when the street crosses no water.
	 */
	static int[] anchorsAndDecks(List<int[]> cols, boolean[] pinned, int off, TerrainProbe exact, boolean[] anchor) {
		int n = cols.size();
		boolean[] wet = new boolean[n];
		boolean any = false;
		for (int i = 0; i < n; i++) any |= wet[i] = exact.waterDepth(cols.get(i)[0], cols.get(i)[1]) > 0;
		int[] floor = new int[n];
		java.util.Arrays.fill(floor, Integer.MIN_VALUE / 2);
		for (int i = 0; i < n; ) {
			if (!wet[i]) { anchor[i] |= i % Grading.SAMPLE_EVERY == 0 || i == n - 1 || pinned[off + i]; i++; continue; }
			int j = i, top = Integer.MIN_VALUE;
			while (j < n && wet[j]) { top = Math.max(top, exact.surfaceY(cols.get(j)[0], cols.get(j)[1])); j++; }
			for (int k = i; k < j; k++) {
				floor[k] = top + Grading.BRIDGE_CLEARANCE;
				anchor[k] = k == i || k == j - 1 || k % Grading.SAMPLE_EVERY == 0 || k == n - 1 || pinned[off + k];
			}
			i = j;
		}
		if (!any) return null;
		int last = -1;
		for (int i = 0; i < n; i++) {
			if (!anchor[i]) continue;
			if (last >= 0) floor[i] = Math.max(floor[i], floor[last] - (i - last) / Grading.GRADE_RUN);
			last = i;
		}
		last = -1;
		for (int i = n - 1; i >= 0; i--) {
			if (!anchor[i]) continue;
			if (last >= 0) floor[i] = Math.max(floor[i], floor[last] - (last - i) / Grading.GRADE_RUN);
			last = i;
		}
		return floor;
	}

	/**
	 * The most a profile can rise between the pinned columns {@code from} and {@code to} of a street whose grader anchors are
	 * {@code anchor}: it moves at most gap / {@value Grading#GRADE_RUN} between neighbouring anchors.
	 */
	static int rise(boolean[] anchor, int from, int to) {
		int sum = 0, last = from;
		for (int i = from + 1; i <= to; i++) {
			if (!anchor[i] && i != to) continue;
			sum += (i - last) / Grading.GRADE_RUN;
			last = i;
		}
		return sum;
	}

	/**
	 * The most a profile can rise between the pinned columns {@code from} and {@code to} of a dry street: the grader's anchors there are
	 * the two pins and every column index divisible by {@value Grading#SAMPLE_EVERY}, and it moves at most gap / {@value Grading#GRADE_RUN}
	 * between neighbouring anchors.
	 */
	static int rise(int from, int to) {
		int sum = 0, last = from;
		for (int i = Math.floorDiv(from, Grading.SAMPLE_EVERY) * Grading.SAMPLE_EVERY + Grading.SAMPLE_EVERY; i < to; i += Grading.SAMPLE_EVERY) {
			sum += (i - last) / Grading.GRADE_RUN;
			last = i;
		}
		return sum + (to - last) / Grading.GRADE_RUN;
	}

	/**
	 * {@code sign} 1: the largest field below {@code ground} whose neighbours differ by at most their edge's cap (the lowest of
	 * {@code ground[k] + distance(j, k)} over all k); -1: the smallest field above it. Multi-source shortest paths, ties by index.
	 */
	static int[] envelope(int[] ground, List<List<int[]>> adj, int sign) {
		int m = ground.length;
		int[] d = new int[m];
		for (int j = 0; j < m; j++) d[j] = sign * ground[j];
		PriorityQueue<int[]> q = new PriorityQueue<>(Comparator.<int[]>comparingInt(e -> e[0]).thenComparingInt(e -> e[1]));
		for (int j = 0; j < m; j++) q.add(new int[] {d[j], j});
		while (!q.isEmpty()) {
			int[] e = q.poll();
			if (e[0] != d[e[1]]) continue;
			for (int[] nb : adj.get(e[1])) {
				int v = e[0] + nb[1];
				if (v < d[nb[0]]) { d[nb[0]] = v; q.add(new int[] {v, nb[0]}); }
			}
		}
		for (int j = 0; j < m; j++) d[j] *= sign;
		return d;
	}

	private static int find(int[] p, int a) {
		while (p[a] != a) { p[a] = p[p[a]]; a = p[a]; }
		return a;
	}

	private static void union(int[] p, int a, int b) {
		int ra = find(p, a), rb = find(p, b);
		if (ra == rb) return;
		if (ra < rb) p[rb] = ra; else p[ra] = rb;
	}
}
