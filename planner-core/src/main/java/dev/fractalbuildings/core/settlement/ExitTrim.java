package dev.fractalbuildings.core.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 6.25 MAINSTUB: a main road that leaves the settlement at a gate with nothing beyond it ends where the town does. An exit gate whose
 * road no approach continues (a hashed exit, or the exit of a link the road layer dropped), and a cell gate whose neighbour cell gives
 * no road, keep their road only up to the last place a lot or another street meets it, plus {@link #KEEP} columns (one lot depth) so the
 * last frontage still has its road; a bridge that would be the stub's last stretch goes too. The cut is a pure function of the raw tile
 * plans (lots and streets) and of which exits are served, so it does not depend on which tile or chunk is asked first; it only shortens
 * the street pieces and the settlement road, never a lot, and never touches the raw plans the road layer's approaches read.
 */
public final class ExitTrim {
	private ExitTrim() {}

	/** Columns kept past the last lot or street the road meets (about one lot depth). */
	public static final int KEEP = 8;
	/** The longest stretch of a road end without a lot or street beside it that the check ({@code exit-stub}) accepts, columns. */
	public static final int MAX_STUB = 16;
	/** How far from the centre line a lot or plaza counts as beside the road, beyond half the road width. */
	static final int LOT_REACH = 3;
	/** How far a street's centre line may lie from the road's (beyond the half widths) and still be a junction. */
	static final int JOIN_REACH = 2;

	/** The raw district plan of a tile, or null when no settlement plans it. */
	public interface Plans { DistrictPlan raw(int ix, int iz); }

	/** A road cut after column {@code index} of {@code cols} (the road's columns from the hub to the gate). */
	public record Cut(String road, List<int[]> cols, int index, int firstPoint, java.util.Set<String> dropped) {
		/** The new last point of the road when its points are kept up to {@code firstPoint - 1}: the cut column. */
		public Pt end() { return new Pt(cols.get(index)[0], cols.get(index)[1]); }
	}

	/** An exit road with nothing beyond its gate: the road and the gate it ends at. */
	record Candidate(Road road, Gate gate, List<Gate> walls) {}

	/**
	 * The main roads that end at a gate with nothing beyond: an EXIT gate no drawn approach leaves from, or a CELL gate whose neighbour
	 * cell has no road at it. In cell and gate order.
	 */
	static List<Candidate> candidates(SkeletonSource src) { return candidates(src, true); }

	/** {@code checkServed} false lists every exit road as a candidate, without asking whether a drawn approach continues it (the link rule). */
	static List<Candidate> candidates(SkeletonSource src, boolean checkServed) {
		List<Candidate> out = new ArrayList<>();
		for (Cell cell : src.site().cells()) {
			CellSkeleton sk = src.skeleton(cell);
			for (Gate g : sk.gates()) {
				if (g.road() == null) continue;
				if (g.kind() == GateKind.EXIT) {
					if (checkServed && src.exitServed(g.x(), g.z())) continue;
				} else if (g.kind() == GateKind.CELL) {
					if (!deadCellGate(src, cell, g)) continue;
				} else continue;
				Road r = null;
				for (Road o : sk.roads()) if (o.id().equals(g.road())) r = o;
				if (r == null || (r.kind() != RoadKind.MAIN && r.kind() != RoadKind.PATH)) continue;
				Pt end = r.points().get(r.points().size() - 1);
				if (end.x() != g.x() || end.z() != g.z()) continue;
				List<Gate> walls = new ArrayList<>();
				for (Gate w : sk.gates()) if (w.kind() == GateKind.WALL && r.id().equals(w.road())) walls.add(w);
				out.add(new Candidate(r, g, walls));
			}
		}
		return out;
	}

	/** The cuts of the settlement's gateless exit roads, by road id; pure in the raw plans and the served exits. */
	public static TreeMap<String, Cut> cuts(SkeletonSource src, Plans plans) {
		TreeMap<String, Cut> out = new TreeMap<>();
		Plans cached = memo(plans);
		for (Candidate c : candidates(src)) {
			Cut k = cut(src, c.road(), c.walls(), cached);
			if (k != null) out.put(c.road().id(), k);
		}
		return out;
	}

	/** The cuts of the roads with a column in or beside the tile (what one tile's plan needs); each equals the whole settlement's {@link #cuts} for that road. */
	public static TreeMap<String, Cut> cutsThrough(SkeletonSource src, Plans plans, int ix, int iz) {
		TreeMap<String, Cut> out = new TreeMap<>();
		Plans cached = memo(plans);
		DistrictGrid.Tile t = src.grid().tile(ix, iz);
		for (Candidate c : candidates(src)) {
			boolean through = false;
			for (int[] col : StreetCols.columns(c.road().points())) through |= col[0] >= t.minX() - 16 && col[0] < t.maxX() + 16 && col[1] >= t.minZ() - 16 && col[1] < t.maxZ() + 16;
			if (!through) continue;
			Cut k = cut(src, c.road(), c.walls(), cached);
			if (k != null) out.put(c.road().id(), k);
		}
		return out;
	}

	/** The core tile: the district tile that holds the settlement's centre. No road is cut inside it, so that tile needs no cut work at all (the first tile of a metropolis). */
	static Rect coreTile(SkeletonSource src) {
		DistrictGrid.Tile t = src.grid().tileAt(src.site().centre().x(), src.site().centre().z());
		return new Rect(t.minX(), t.minZ(), t.widthX(), t.widthZ());
	}

	/** Only a city or metropolis has a core tile: a village's single tile is most of the town, and its stubs are real. */
	static boolean hasCore(SkeletonSource src) { return src.site().radius() >= 400; }

	/** The last column of the road (hub to gate) inside the core tile, or 0. */
	static int coreIndex(SkeletonSource src, List<int[]> cols) {
		if (!hasCore(src)) return 0;
		Rect r = coreTile(src);
		int best = 0;
		for (int i = 0; i < cols.size(); i++) if (r.contains(cols.get(i)[0], cols.get(i)[1])) best = i;
		return best;
	}

	/** The largest column index any street piece of the road in the core tile's raw plan reaches (a piece can overhang the tile border), or 0. */
	static int coreReach(SkeletonSource src, String roadId, List<int[]> cols, Plans plans) {
		if (!hasCore(src)) return 0;
		DistrictGrid.Tile t = src.grid().tileAt(src.site().centre().x(), src.site().centre().z());
		DistrictPlan core = plans.raw(t.ix(), t.iz());
		if (core == null) return 0;
		Map<Long, Integer> at = new TreeMap<>();
		for (int i = 0; i < cols.size(); i++) at.putIfAbsent(((long) cols.get(i)[0] << 32) ^ (cols.get(i)[1] & 0xffffffffL), i);
		int best = 0;
		for (Street s : core.streets()) {
			if (!baseId(s.id()).equals(roadId)) continue;
			for (Pt p : s.points()) {
				Integer i = at.get(((long) p.x() << 32) ^ (p.z() & 0xffffffffL));
				if (i != null) best = Math.max(best, i);
			}
		}
		return best;
	}

	/** Whether district tile (ix, iz) is the core tile. */
	public static boolean inCore(SkeletonSource src, int ix, int iz) {
		DistrictGrid.Tile t = src.grid().tile(ix, iz);
		return inCore(src, new Rect(t.minX(), t.minZ(), t.widthX(), t.widthZ()));
	}

	/** Whether the tile is the core tile (no cut can touch it). */
	public static boolean inCore(SkeletonSource src, Rect tile) {
		if (!hasCore(src)) return false;
		Rect r = coreTile(src);
		return r.x() == tile.x() && r.z() == tile.z();
	}

	// ---- per road, for the world planner's lazy path (the first tile of a settlement must not pay for the whole settlement) ----

	private static Candidate candidateOf(SkeletonSource src, String roadId) {
		for (Candidate c : candidates(src, false)) if (c.road().id().equals(roadId)) return c;
		return null;
	}

	/** The ids of the settlement's exit roads (geometry only). */
	public static List<String> exitRoadIds(SkeletonSource src) {
		List<String> out = new ArrayList<>();
		for (Candidate c : candidates(src, false)) out.add(c.road().id());
		return out;
	}

	/** Whether the road is an exit road that ends at a gate with a possible nothing beyond (geometry only: no link rule). */
	public static boolean isExitRoad(SkeletonSource src, String roadId) { return candidateOf(src, roadId) != null; }

	/** Whether a drawn approach continues the road beyond its gate (asks the link rule: only after the cut is known to reach the tile). */
	public static boolean gateServed(SkeletonSource src, String roadId) {
		Candidate c = candidateOf(src, roadId);
		return c != null && c.gate().kind() == GateKind.EXIT && src.exitServed(c.gate().x(), c.gate().z());
	}

	/** The cut of the road as the plans say, ignoring whether its gate is served; null for none or a non-exit road. */
	public static Cut fullCut(SkeletonSource src, String roadId, Plans plans) {
		Candidate c = candidateOf(src, roadId);
		return c == null ? null : cut(src, c.road(), c.walls(), memo(plans));
	}

	/** The largest column index of the road inside the tile rectangle, or -1. */
	public static int maxIndexIn(SkeletonSource src, String roadId, Rect tile) {
		Candidate c = candidateOf(src, roadId);
		if (c == null) return -1;
		tile = new Rect(tile.x() - 24, tile.z() - 24, tile.w() + 48, tile.d() + 48); // lots dropped beyond a cut can lie in the tile beside a column that is not
		List<int[]> cols = StreetCols.columns(c.road().points());
		int best = -1;
		for (int i = 0; i < cols.size(); i++) if (tile.contains(cols.get(i)[0], cols.get(i)[1])) best = i;
		return best;
	}

	/**
	 * Cheap proof, from the tile's own plan, that the road's cut lies at or beyond column {@code m} (its last column in the tile), so this tile is
	 * untouched: the road's first m + 1 columns all lie in the tile, it has built frontage through to within one lot depth of m, and no bridge or
	 * lot-free run over {@link #GAP} columns on the way. False means "not proven" (ask {@link #fullCut}).
	 */
	public static boolean cutBeyond(SkeletonSource src, String roadId, int m, DistrictPlan own, Rect tile) {
		Candidate c = candidateOf(src, roadId);
		if (c == null || !c.walls().isEmpty() || m < 1) return false;
		Road r = c.road();
		List<int[]> cols = StreetCols.columns(r.points());
		if (m >= cols.size()) return false;
		for (int i = 0; i <= m; i++) if (!tile.contains(cols.get(i)[0], cols.get(i)[1])) return false;
		int[] pointCol = pointColumns(r.points());
		boolean[] enter = new boolean[cols.size()];
		for (int[] b : r.bridges()) enter[pointCol[b[0]]] = true;
		int last = 0, lastKind = 0;
		for (int i = 1; i <= m; i++) {
			int k = beside(own, r.id(), r.width(), cols.get(i)[0], cols.get(i)[1], null);
			if (k > 0) { last = i; lastKind = k; continue; }
			if (last > 0 && (enter[i] || i - last > GAP)) return false;
		}
		if (last == 0) return false;
		return last + (lastKind == 2 ? KEEP : r.width() / 2 + 1) >= m;
	}

	private static Plans memo(Plans plans) {
		Map<Long, DistrictPlan> memo = new TreeMap<>();
		return (ix, iz) -> {
			long k = ((long) ix << 32) ^ (iz & 0xffffffffL);
			if (!memo.containsKey(k)) memo.put(k, plans.raw(ix, iz));
			return memo.get(k);
		};
	}

	/** One gateless exit road as it ends: {@code columns} from its last frontage (lot, plaza or street beside it) to its last column. */
	public record Stub(String road, int x, int z, int columns, boolean cut, boolean unserved) {}

	/** The stub of every gateless exit road once {@code cuts} are applied (none: the roads as planned). */
	public static List<Stub> stubs(SkeletonSource src, Plans plans, Map<String, Cut> cuts) {
		List<Stub> out = new ArrayList<>();
		Plans cached = memo(plans);
		for (Candidate c : candidates(src)) {
			List<int[]> cols = StreetCols.columns(c.road().points());
			Cut k = cuts.get(c.road().id());
			int top = k == null ? cols.size() - 1 : k.index();
			int last = Math.max(lastServed(src, c.road().id(), c.road().width(), cols.subList(0, top + 1), cached, deckColumns(c.road(), cols.size())), wallColumn(cols.subList(0, top + 1), c.walls()));
			// a road that ends inside the core tile (the settlement centre; never cut, ExitTrim.coreTile) is the town itself, not a stub. Last 0: nothing at all stands beside the road (a hamlet's only street, or a road through tiles no one builds): it is kept whole and not counted
			out.add(new Stub(c.road().id(), cols.get(top)[0], cols.get(top)[1], last <= 0 || top <= Math.max(coreIndex(src, cols), coreReach(src, c.road().id(), cols, plans)) ? 0 : top - last, k != null, last <= 0));
		}
		return out;
	}

	/** A cell gate where the neighbouring cell's skeleton has no road: the road of this cell stops at the border. */
	private static boolean deadCellGate(SkeletonSource src, Cell cell, Gate g) {
		if (g.cells().size() != 2) return false;
		Cell other = g.cells().get(0).equals(cell) ? g.cells().get(1) : g.cells().get(0);
		if (!src.site().cells().contains(other)) return true; // the neighbour belongs to no cell of this settlement: nothing continues
		for (Gate og : src.skeleton(other).gates()) if (og.kind() == GateKind.CELL && og.x() == g.x() && og.z() == g.z()) return og.road() == null;
		return false;
	}

	/** A lot-free run of the road longer than this, outward from the town, ends the road's continuous frontage (two lot depths each side and slack). */
	static final int GAP = 32;
	/** An outlying cluster this big (built lots) or with a plaza is a quarter of its own: the road and its bridge stay. */
	static final int QUARTER_LOTS = 10;
	/** How far from the far frontage a lot or plaza still belongs to what lies beyond the stop. */
	static final int QUARTER_REACH = 100;

	/**
	 * The cut of one road, or null when none is needed. Walks outward from the town (column 0, the hub) and keeps the road through continuous
	 * frontage: it stops at the first bridge (the road ends at the near bank) or the first lot-free run over {@link #GAP} columns, unless what lies beyond
	 * is a quarter (a plaza or {@link #QUARTER_LOTS} built lots), in which case the whole road to the last frontage stays. Past the last built lot it keeps
	 * {@link #KEEP} columns, past a junction one road width; the lots beside the removed stretch are dropped (left unbuilt); {@link #apply} also unbuilds any built lot that loses its street frontage to the cut. A street with a built lot only holds a stop when it is a road or arterial or the beyond is a quarter.
	 * A wall gate counts as frontage.
	 */
	static Cut cut(SkeletonSource src, Road r, List<Gate> walls, Plans plans) {
		List<int[]> cols = StreetCols.columns(r.points());
		int n = cols.size();
		DistrictGrid grid = src.grid();
		int reach = r.width() / 2 + LOT_REACH;
		int[] kind = new int[n];
		List<java.util.Set<String>> lotsAt = new ArrayList<>();
		for (int i = 0; i < n; i++) lotsAt.add(new java.util.TreeSet<>());
		for (int i = 1; i < n; i++) {
			int x = cols.get(i)[0], z = cols.get(i)[1];
			DistrictGrid.Tile t0 = grid.tileAt(x - reach - 1, z - reach - 1), t1 = grid.tileAt(x + reach + 1, z + reach + 1);
			for (int iz = t0.iz(); iz <= t1.iz(); iz++) {
				for (int ix = t0.ix(); ix <= t1.ix(); ix++) {
					DistrictPlan p = plans.raw(ix, iz);
					if (p != null) kind[i] |= beside(p, r.id(), r.width(), x, z, lotsAt.get(i));
				}
			}
		}
		int wall = wallColumn(cols, walls);
		if (wall > 0) kind[wall] |= 2; // a wall gate is a destination too: the road runs through it
		int[] pointCol = pointColumns(r.points());
		boolean[] enter = new boolean[n]; // the column where a bridge starts
		for (int[] b : r.bridges()) enter[pointCol[b[0]]] = true;
		for (int[] b : r.bridges()) for (int i = pointCol[b[0]] + 1; i < pointCol[b[1]]; i++) { kind[i] = 0; lotsAt.get(i).clear(); } // a deck has no frontage: lots on the banks beside it are not its street
		boolean stops = src.site().radius() >= 250; // a hamlet or village has no outlying suburb to drop: its roads only lose their lot-free tail
		int last = 0, lastLot = 0, stop = -1;
		for (int i = 1; i < n; i++) {
			boolean bridge = stops && enter[i] && last > 0, gap = stops && kind[i] == 0 && i - last > GAP && last > 0;
			if (kind[i] > 0 && !bridge) { last = i; if ((kind[i] & 2) != 0) lastLot = i; continue; }
			if (!bridge && !gap) continue;
			if (junctionBeyond(kind, i) || quarterBeyond(src, cols, i, plans, lotsAt, kind)) continue; // a real quarter lies beyond: keep going to its frontage
			stop = bridge ? i : last + GAP;
			break;
		}
		int top = stop < 0 ? n - 1 : stop;
		int lastAll = 0;
		for (int i = 1; i <= top; i++) if (kind[i] > 0) lastAll = i;
		if (lastAll <= 0) return null; // nothing beside the road at all: it is the settlement's only street, keep it whole
		boolean byLot = false;
		for (int i = Math.max(0, lastAll - 0); i <= lastAll; i++) byLot |= (kind[i] & 2) != 0;
		int keep = Math.min(n - 1, lastAll + (byLot ? KEEP : r.width() / 2 + 1));
		if (stop >= 0 && enter[stop]) keep = Math.min(keep, stop); // the road reaches the near bank and stops
		for (int[] b : r.bridges()) {
			int s = pointCol[b[0]];
			if (s > lastAll && s <= keep) keep = s;
		}
		keep = Math.max(keep, coreIndex(src, cols)); // the town's core is never cut
		keep = Math.max(keep, coreReach(src, r.id(), cols, plans)); // ... nor the pieces of its streets that overhang the tile border (the core tile is not cut, so the neighbour must agree with it)
		if (keep >= n - 1) return null;
		java.util.Set<String> dropped = new java.util.TreeSet<>();
		if (stop >= 0) {
			java.util.Set<String> near = new java.util.TreeSet<>();
			for (int i = 0; i <= keep; i++) near.addAll(lotsAt.get(i));
			for (int i = keep + 1; i < n; i++) for (String k : lotsAt.get(i)) if (!near.contains(k) && !k.contains("/plaza ")) dropped.add(k);
		}
		int fp = 0;
		while (fp < r.points().size() && pointCol[fp] <= keep) fp++;
		return new Cut(r.id(), cols, keep, fp, dropped);
	}

	/** A street that goes somewhere (another road, or a street with a built lot) meets the road beyond the stop: removing the road would cut it off. */
	private static boolean junctionBeyond(int[] kind, int from) {
		for (int i = from; i < kind.length; i++) if ((kind[i] & 4) != 0) return true;
		return false;
	}

		/**
	 * Whether what lies beyond column {@code from} is a quarter: a plaza, or at least {@link #QUARTER_LOTS} distinct built lots within
	 * .
	 */
	private static boolean quarterBeyond(SkeletonSource src, List<int[]> cols, int from, Plans plans, List<java.util.Set<String>> lotsAt, int[] kind) {
		java.util.Set<String> near = new java.util.TreeSet<>();
		for (int i = 0; i < from; i++) near.addAll(lotsAt.get(i)); // frontage already kept does not make the far side a quarter
		int start = from;
		while (start < cols.size() && (kind[start] & 2) == 0) start++; // the far side begins at its first frontage
		int[] n0 = cols.get(from); // a lot or plaza belongs to the far side only when it is nearer to a far sample than to the stop
		java.util.Set<String> all = new java.util.TreeSet<>();
		DistrictGrid grid = src.grid();
		for (int i = start; i < cols.size(); i += 8) {
			int x = cols.get(i)[0], z = cols.get(i)[1];
			DistrictGrid.Tile t0 = grid.tileAt(x - QUARTER_REACH, z - QUARTER_REACH), t1 = grid.tileAt(x + QUARTER_REACH, z + QUARTER_REACH);
			for (int iz = t0.iz(); iz <= t1.iz(); iz++) {
				for (int ix = t0.ix(); ix <= t1.ix(); ix++) {
					DistrictPlan p = plans.raw(ix, iz);
					if (p == null) continue;
					for (Plaza q : p.plazas()) {
						int qx = q.x() + q.w() / 2, qz = q.z() + q.d() / 2, d = Math.max(Math.abs(qx - x), Math.abs(qz - z));
						if (d <= QUARTER_REACH && d < Math.max(Math.abs(qx - n0[0]), Math.abs(qz - n0[1]))) return true;
					}
					for (LotBrief l : p.lots()) {
						int lx = l.x() + l.w() / 2, lz = l.z() + l.d() / 2, d = Math.max(Math.abs(lx - x), Math.abs(lz - z));
						if (!l.builds() || d > QUARTER_REACH || d >= Math.max(Math.abs(lx - n0[0]), Math.abs(lz - n0[1]))) continue;
						String key = p.district().id() + "/" + l.id();
						if (!near.contains(key)) all.add(key);
					}
				}
			}
			if (all.size() >= QUARTER_LOTS) return true;
		}
		return false;
	}

	/** The road's centre-line columns from its first point to its last, one per block step. */
	public static List<int[]> columns(Road r) { return StreetCols.columns(r.points()); }

	/** The last column of the road at a wall gate of it (the wall opening is where the road leaves the town proper); -1 when it has none. */
	static int wallColumn(List<int[]> cols, List<Gate> walls) {
		int best = -1;
		for (Gate g : walls) {
			for (int i = 0; i < cols.size(); i++) if (Math.abs(cols.get(i)[0] - g.x()) <= 2 && Math.abs(cols.get(i)[1] - g.z()) <= 2) best = Math.max(best, i);
		}
		return best;
	}

	/** The columns strictly inside a bridge of the road. */
	static boolean[] deckColumns(Road r, int n) {
		int[] pc = pointColumns(r.points());
		boolean[] d = new boolean[n];
		for (int[] b : r.bridges()) for (int i = pc[b[0]] + 1; i < pc[b[1]] && i < n; i++) d[i] = true;
		return d;
	}

	/** Column index of each point of a road polyline. */
	static int[] pointColumns(List<Pt> pts) {
		int[] idx = new int[pts.size()];
		int c = 0;
		for (int p = 1; p < pts.size(); p++) {
			Pt a = pts.get(p - 1), b = pts.get(p);
			c += Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z()));
			idx[p] = c;
		}
		return idx;
	}

	/**
	 * The index of the road's last column that has a built lot, a plaza or another main road or arterial beside it (0, the hub end, when none has). Raw plans only.
	 * Walks from the gate inwards and stops at the first.
	 */
	public static int lastServed(SkeletonSource src, String roadId, int width, List<int[]> cols, Plans plans) { return lastServed(src, roadId, width, cols, plans, null); }

	/** As above, skipping the columns {@code deck} marks (a bridge deck has no frontage). */
	public static int lastServed(SkeletonSource src, String roadId, int width, List<int[]> cols, Plans plans, boolean[] deck) {
		DistrictGrid grid = src.grid();
		int reach = width / 2 + LOT_REACH;
		for (int i = cols.size() - 1; i > 0; i--) {
			if (deck != null && deck[i]) continue;
			int x = cols.get(i)[0], z = cols.get(i)[1];
			// the tiles whose pieces can lie within reach of the column
			DistrictGrid.Tile t0 = grid.tileAt(x - reach - 1, z - reach - 1), t1 = grid.tileAt(x + reach + 1, z + reach + 1);
			for (int iz = t0.iz(); iz <= t1.iz(); iz++) {
				for (int ix = t0.ix(); ix <= t1.ix(); ix++) {
					DistrictPlan p = plans.raw(ix, iz);
					if (p != null && beside(p, roadId, width, x, z, null) > 0) return i;
				}
			}
		}
		return 0;
	}

	/** Flags: 2 a built lot or plaza beside the column ({@code lots} collects the lot keys; null: stop at the first hit), 1 a street with a built lot meets it, 4 another road meets it; 0 nothing. */
	private static int beside(DistrictPlan p, String roadId, int width, int x, int z, java.util.Set<String> lots) {
		int reach = width / 2 + LOT_REACH;
		int kind = 0;
		for (LotBrief l : p.lots()) {
			if (l.builds() && x >= l.x() - reach && x < l.x() + l.w() + reach && z >= l.z() - reach && z < l.z() + l.d() + reach) {
				if (lots == null) return 2;
				lots.add(p.district().id() + "/" + l.id());
				kind = 2;
			}
		}
		for (Plaza q : p.plazas()) if (x >= q.x() - reach && x < q.x() + q.w() + reach && z >= q.z() - reach && z < q.z() + q.d() + reach) {
			if (lots == null) return 2;
			lots.add(p.district().id() + "/plaza " + q.id());
			kind = 2;
		}
		if (kind == 2 && lots == null) return 2;
		int flags = kind; // 2 lot or plaza, 1 a street with a built lot meets it, 4 another road or arterial meets it
		for (Street s : p.streets()) {
			if (baseId(s.id()).equals(roadId) || s.nearest(x, z)[0] > width / 2 + s.width() / 2 + JOIN_REACH) continue;
			if (leadsSomewhere(p, s)) flags |= (s.kind() == RoadKind.MAIN || s.kind() == RoadKind.PATH || length(s) >= THROUGH) ? 4 : 1; // only a street that runs on (not a lane of the far cluster) holds a stop
			if (lots == null && flags != 0) return flags;
		}
		return flags;
	}

	/** A street another road meets counts as a junction when it goes somewhere: a skeleton road or arterial, or a street with a built lot beside it (cutting the road would cut that lot off). */
	/** A road or arterial piece at least this long (columns) runs on beyond the cluster it passes; a shorter one belongs to it. */
	static final int THROUGH = 96;

	private static int length(Street s) {
		int n = 0;
		for (int i = 1; i < s.points().size(); i++) n += Math.max(Math.abs(s.points().get(i).x() - s.points().get(i - 1).x()), Math.abs(s.points().get(i).z() - s.points().get(i - 1).z()));
		return n;
	}

	private static boolean leadsSomewhere(DistrictPlan p, Street s) {
		if (s.kind() == RoadKind.ARTERIAL || s.kind() == RoadKind.MAIN || s.kind() == RoadKind.PATH) return true;
		int reach = s.width() / 2 + LOT_REACH;
		for (LotBrief l : p.lots()) {
			if (!l.builds()) continue;
			int cx = l.x() + l.w() / 2, cz = l.z() + l.d() / 2;
			if (s.nearest(cx, cz)[0] <= reach + Math.max(l.w(), l.d()) / 2) return true;
		}
		return false;
	}

	public static String baseIdOf(String id) { return baseId(id); }

	static String baseId(String id) {
		int dot = id.indexOf('.');
		return dot < 0 ? id : id.substring(0, dot);
	}

/** The lots a cut leaves beyond its end (an outlying cluster smaller than a quarter) stay as lots but are not built. */
static DistrictPlan unbuild(DistrictPlan p, Map<String, Cut> cuts) {
	java.util.Set<String> drop = new java.util.TreeSet<>();
	for (Cut c : cuts.values()) drop.addAll(c.dropped());
	if (drop.isEmpty()) return p;
	List<LotBrief> lots = new ArrayList<>();
	boolean changed = false;
	for (LotBrief l : p.lots()) {
		if (l.builds() && !dev.fractalbuildings.core.settlement.tiers.WallRing.isRingLotId(l.id()) && drop.contains(p.district().id() + "/" + l.id())) {
			lots.add(new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), l.type(), l.style(), l.minStoreys(), l.maxStoreys(), l.streets(), l.airRights(), l.frontY(), l.density(), "empty:stub", l.walk(), l.square()));
			changed = true;
		} else lots.add(l);
	}
	return changed ? new DistrictPlan(p.genVersion(), p.seed(), p.district(), p.streets(), p.plazas(), lots, p.scene(), p.violations()) : p;
}

/** The plan with the street pieces of the cut roads shortened; the same object when no piece is touched. */
	public static DistrictPlan apply(DistrictPlan p, Map<String, Cut> cuts) {
		if (cuts.isEmpty()) return p;
		List<Street> out = new ArrayList<>();
		boolean changed = false;
		p = unbuild(p, cuts);
		for (Street s : p.streets()) {
			Cut c = cuts.get(baseId(s.id()));
			if (c == null) { out.add(s); continue; }
			Street t = shorten(s, c);
			if (t != s) changed = true;
			if (t != null) out.add(t);
		}
		if (!changed) return p;
		DistrictPlan q = new DistrictPlan(p.genVersion(), p.seed(), p.district(), dropOrphans(p.district().rect(), p.streets(), out), p.plazas(), p.lots(), p.scene(), p.violations());
		return unbuildLostFrontage(p, q);
	}

	/** A built lot that had street frontage before the cut and has none after it (its lane was dropped with the removed stretch) is left unbuilt. */
	static DistrictPlan unbuildLostFrontage(DistrictPlan before, DistrictPlan after) {
		java.util.Set<String> bad = new java.util.TreeSet<>();
		for (var v : dev.fractalbuildings.core.check.SettlementChecks.lotFrontage(before)) bad.add(v.message().split(" ")[1]);
		List<LotBrief> lots = new ArrayList<>();
		boolean changed = false;
		for (var v : dev.fractalbuildings.core.check.SettlementChecks.lotFrontage(after)) {
			String id = v.message().split(" ")[1];
			if (bad.contains(id)) continue;
			for (LotBrief l : after.lots()) if (l.id().equals(id) && l.builds()) bad.add("!" + id);
		}
		for (LotBrief l : after.lots()) {
			if (bad.contains("!" + l.id())) {
				lots.add(new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), l.type(), l.style(), l.minStoreys(), l.maxStoreys(), l.streets(), l.airRights(), l.frontY(), l.density(), "empty:stub", l.walk(), l.square()));
				changed = true;
			} else lots.add(l);
		}
		return changed ? new DistrictPlan(after.genVersion(), after.seed(), after.district(), after.streets(), after.plazas(), lots, after.scene(), after.violations()) : after;
	}

	/**
	 * The streets that reached a road (a skeleton road or arterial) or the tile border before the cut and no longer do: a lane that hung on the
	 * removed end of an exit road. They are dropped (the same ground rule district planning keeps: no street reaches nothing).
	 */
	static List<Street> dropOrphans(Rect tile, List<Street> before, List<Street> after) {
		boolean[] was = rooted(tile, before), now = rooted(tile, after);
		java.util.Set<String> wasRooted = new java.util.TreeSet<>();
		for (int i = 0; i < before.size(); i++) if (was[i]) wasRooted.add(before.get(i).id());
		List<Street> out = new ArrayList<>();
		for (int i = 0; i < after.size(); i++) if (now[i] || !wasRooted.contains(after.get(i).id())) out.add(after.get(i));
		return out;
	}

	private static boolean isRoad(Street s) { return s.kind() == RoadKind.MAIN || s.kind() == RoadKind.PATH || s.kind() == RoadKind.ARTERIAL; }

	/** Which of the streets touch (through streets that touch) a road or the tile's border. */
	private static boolean[] rooted(Rect tile, List<Street> streets) {
		int n = streets.size();
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) parent[i] = i;
		boolean[] root = new boolean[n];
		int x0 = tile.x() - 8, z0 = tile.z() - 8, w = tile.w() + 16, d = tile.d() + 16;
		int[] owner = new int[w * d];
		for (int i = 0; i < n; i++) {
			root[i] = isRoad(streets.get(i));
			for (Rect r : dev.fractalbuildings.core.check.SettlementChecks.footprint(streets.get(i))) {
				if (streets.get(i).kind() == RoadKind.STREET && (r.x() <= tile.x() || r.z() <= tile.z() || r.x() + r.w() >= tile.x() + tile.w() || r.z() + r.d() >= tile.z() + tile.d())) root[i] = true; // only lattice streets run on across a tile border; a quay or lane that ends at it hangs on a road
				for (int z = Math.max(r.z(), z0); z < Math.min(r.z() + r.d(), z0 + d); z++) {
					for (int x = Math.max(r.x(), x0); x < Math.min(r.x() + r.w(), x0 + w); x++) {
						int k = (z - z0) * w + (x - x0);
						if (owner[k] == 0) owner[k] = i + 1; else union(parent, owner[k] - 1, i);
					}
				}
			}
		}
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				int a = owner[z * w + x];
				if (a == 0) continue;
				if (x + 1 < w && owner[z * w + x + 1] != 0) union(parent, a - 1, owner[z * w + x + 1] - 1);
				if (z + 1 < d && owner[(z + 1) * w + x] != 0) union(parent, a - 1, owner[(z + 1) * w + x] - 1);
			}
		}
		boolean[] rootedComp = new boolean[n];
		for (int i = 0; i < n; i++) if (root[i]) rootedComp[find(parent, i)] = true;
		boolean[] out = new boolean[n];
		for (int i = 0; i < n; i++) out[i] = rootedComp[find(parent, i)];
		return out;
	}

	private static int find(int[] p, int a) {
		while (p[a] != a) { p[a] = p[p[a]]; a = p[a]; }
		return a;
	}

	private static void union(int[] p, int a, int b) { p[find(p, a)] = find(p, b); }

	/** The piece up to the cut: itself when it lies before it, null when it lies after. */
	static Street shorten(Street s, Cut c) {
		Map<Long, Integer> at = new TreeMap<>();
		for (int i = 0; i < c.cols().size(); i++) at.putIfAbsent(((long) c.cols().get(i)[0] << 32) ^ (c.cols().get(i)[1] & 0xffffffffL), i);
		List<Pt> pts = s.points();
		int[] ys = s.y();
		int[] idx = new int[pts.size()];
		for (int k = 0; k < pts.size(); k++) {
			Integer i = at.get(((long) pts.get(k).x() << 32) ^ (pts.get(k).z() & 0xffffffffL));
			if (i == null) return s; // not a piece of this road's columns: leave it alone
			idx[k] = i;
		}
		if (idx[idx.length - 1] <= c.index()) return s;
		if (idx[0] > c.index()) return null;
		int k = 0;
		while (idx[k + 1] <= c.index()) k++; // points 0..k are kept; the cut lies on the segment k..k+1 (or on k)
		List<Pt> np = new ArrayList<>(pts.subList(0, k + 1));
		int[] ny;
		if (idx[k] == c.index()) {
			ny = java.util.Arrays.copyOf(ys, k + 1);
		} else {
			np.add(c.end());
			ny = java.util.Arrays.copyOf(ys, k + 2);
			ny[k + 1] = ys[k]; // between stored points the height is constant or steps once at the second
		}
		if (np.size() < 2) return null;
		return new Street(s.id(), s.kind(), s.width(), np, ny);
	}

	/** The settlement road cut to the same column (points up to the cut, the cut column last); bridges that start before it are kept whole. */
	public static Road shorten(Road r, Cut c) {
		List<Pt> pts = new ArrayList<>(r.points().subList(0, c.firstPoint()));
		Pt end = c.end();
		if (!pts.get(pts.size() - 1).equals(end)) pts.add(end);
		List<int[]> bridges = new ArrayList<>();
		for (int[] b : r.bridges()) if (b[1] < pts.size()) bridges.add(b);
		return new Road(r.id(), r.kind(), r.width(), pts, bridges);
	}

}
