package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.v1.SpecPolicyV1;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.Types;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.terrain.Grading;
import dev.fractalbuildings.core.terrain.LatticePath;
import dev.fractalbuildings.core.settlement.tiers.WallRing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * District detail (plan step DL, ADR 0003 P1 level two): the streets, plazas and lots of one district tile, a pure function of
 * (seed, tile, the settlement's skeletons, exact terrain). Tiles can be planned in any order and from fresh sources.
 *
 * <p>Streets of a tile, all as centre lines on block columns clipped to the tile plus {@value #MARGIN} blocks (so a street that
 * only reaches into the tile with its width is still there, and both tiles at a border list the same street):
 * <ul>
 * <li>skeleton main roads and paths, and tile-border arterials (the pointwise {@link Arterials} rule);</li>
 * <li>plain streets on non-arterial borders in grid areas and grid lines shared along a whole column of tiles
 * ({@link StreetRules}), so grid streets run straight across tile borders;</li>
 * <li>hashed <i>ports</i> on the other borders where a lane crosses into the neighbour, and frontage lanes branching from
 * the roads every 40 to 70 blocks.</li>
 * </ul>
 * Lots are axis-aligned rectangles packed along the straight runs of the streets (diagonal runs get none: a rectangle meets
 * a 45 degree band in a corner, so its frontage rule could not hold; lanes branch off diagonal roads instead) and whole
 * street blocks in dense grid areas; hall and temple lots sit flush against the hub square. A lot never touches water, never
 * overlaps anything and always has street or plaza along its front edge.
 */
public final class Districts {
	private Districts() {}

	/** Streets are kept this far outside the tile: the widest half-width (5) plus the 3 columns {@code tile-border-agree} compares, so neighbouring tiles agree near the border. */
	public static final int MARGIN = 8;
	/** Frontage lots stay shallower than the {@code block} type's smallest lot (52), so only whole street blocks take blocks. */
	public static final int MAX_DEPTH = 44;
	static final int PAD = 16, MIN_W = 7, MIN_DEPTH_ANY = 7;
	static final int STREET = 1, PLAZA = 2, LOT = 4, RESERVED = 8, WET = 16, SKEL = 32, APPROACH = 64;
	/** Whole-block lots from density 50 up (ROADS3; was 70): the mid-density grid quarters get perimeter blocks too, where the class table picks one. */
	static final int BLOCK_DENSITY = 50;
	/** How far beyond the tile a grid line's runs are followed (a multiple of {@link StreetRules#NODE}). */
	static final int GRID_REACH = 256;

	/**
	 * A planned district: the plan, the landmarks that still reserve ground (open squares, and hall or temple landmarks that
	 * got no lot; moved squares carry their new position), notes on what was dropped or moved, and whether the first plaza (the
	 * hub square in the hub tile) is joined to a road that reaches the hub.
	 */
	public record Detail(DistrictPlan plan, List<Landmark> landmarks, List<String> notes, boolean firstPlazaReached) {}

	/**
	 * Is the tile planned by this settlement? A tile belongs to the settlement of the cell that owns its centre; a tile holding
	 * the hub whose centre is owned by a cell of no settlement is the hub's. Exactly one settlement plans each tile.
	 */
	public static boolean owns(SkeletonSource src, int ix, int iz) {
		RegionField f = src.view().field();
		Cell centreCell = CellGeometry.owner(f, src.site().centre().x(), src.site().centre().z());
		DistrictGrid.TileId hubTile = null;
		if (src.site().cells().contains(centreCell)) {
			Pt hub = src.skeleton(centreCell).hub();
			if (hub != null) hubTile = src.grid().tileOf(hub.x(), hub.z());
		}
		return owns(src.view(), src.site(), src.grid(), hubTile, ix, iz);
	}

	/** Sample spacing of the tile-claim rule for tiles whose centre lies in a cell without a settlement. */
	static final int CLAIM_STEP = 32;

	/**
	 * Tile ownership from the view alone ({@code hubTile} is the tile of the centre cell's hub, or null): the settlement of the cell
	 * owning the tile's centre; else, when that cell has no settlement, the hub's own tile, or the settlement whose cells own the
	 * most of the tile's {@value #CLAIM_STEP}-block lattice columns (ties to the smaller site id). The last case lets a settlement
	 * plan the edge tiles its streets and main roads run through although their centre lies across a cell border (ROADS: a main
	 * road crossing an unplanned tile left a gap). Every settlement computes the same answer, so a tile is planned at most once.
	 */
	/** The id of the settlement a cell belongs to, "" for none (or a cell the view cannot decide, at the edge of a recorded window). */
	private static String siteId(RegionView view, Cell c) {
		try {
			return view.siteOf(c).map(SettlementSite::id).orElse("");
		} catch (RuntimeException e) {
			return "";
		}
	}

	public static boolean owns(RegionView view, SettlementSite site, DistrictGrid grid, DistrictGrid.TileId hubTile, int ix, int iz) {
		DistrictGrid.Tile t = grid.tile(ix, iz);
		RegionField f = view.field();
		Cell c = CellGeometry.owner(f, (t.minX() + t.maxX()) / 2, (t.minZ() + t.maxZ()) / 2);
		if (site.cells().contains(c)) return true;
		if (view.siteOf(c).isPresent()) return false;
		if (hubTile != null && hubTile.ix() == ix && hubTile.iz() == iz) return true;
		TreeMap<String, Integer> count = new TreeMap<>();
		TreeMap<Cell, String> seen = new TreeMap<>(CellGeometry::compare);
		for (int z = Math.floorDiv(t.minZ() + CLAIM_STEP - 1, CLAIM_STEP) * CLAIM_STEP; z < t.maxZ(); z += CLAIM_STEP) {
			for (int x = Math.floorDiv(t.minX() + CLAIM_STEP - 1, CLAIM_STEP) * CLAIM_STEP; x < t.maxX(); x += CLAIM_STEP) {
				Cell o = CellGeometry.owner(f, x, z);
				String s = seen.computeIfAbsent(o, k -> site.cells().contains(k) ? site.id() : siteId(view, k));
				if (!s.isEmpty()) count.merge(s, 1, Integer::sum);
			}
		}
		String best = null;
		for (var e : count.entrySet()) if (best == null || e.getValue() > count.get(best)) best = e.getKey();
		return site.id().equals(best);
	}

	/**
	 * The quay runs tile (ix, iz) grades ahead of its streets, one line each: every column as {@code x,z:y}, in order. For tests: every
	 * tile that sees a run must list it alike, whatever its window.
	 */
	static List<String> quayRuns(SkeletonSource src, int ix, int iz, TerrainProbe exact) {
		List<String> out = new ArrayList<>();
		for (Build.QuayWhole q : new Build(src, ix, iz, exact).quayWholes()) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < q.cols().size(); i++) sb.append(i == 0 ? "" : " ").append(q.cols().get(i)[0]).append(',').append(q.cols().get(i)[1]).append(':').append(q.y()[i]);
			out.add(sb.toString());
		}
		return out;
	}

	/**
	 * Builds the settlement's quay runs (a value shared by every tile, {@link SkeletonSource#shared}) ahead of the first tile that needs
	 * them, through the first waterfront tile the settlement plans; nothing when it has none. Never changes any answer.
	 */
	public static void warmQuays(SkeletonSource src, TerrainProbe exact) {
		if (!src.site().tier().atLeast(Tier.TOWN)) return;
		int r = src.density().maxRadius() + 2 * PAD, cx = src.site().centre().x(), cz = src.site().centre().z();
		DistrictGrid.TileId lo = src.grid().tileOf(cx - r, cz - r), hi = src.grid().tileOf(cx + r, cz + r);
		for (int iz = lo.iz(); iz <= hi.iz(); iz++) {
			for (int ix = lo.ix(); ix <= hi.ix(); ix++) {
				Build b = new Build(src, ix, iz, exact, false);
				if (!b.hasWaterfront() || !owns(src, ix, iz)) continue;
				b.quayWholes();
				return;
			}
		}
	}

	/** Plans one tile, or nothing if another settlement owns it. */
	public static Optional<Detail> plan(SkeletonSource src, int ix, int iz, TerrainProbe exact) {
		if (!owns(src, ix, iz)) return Optional.empty();
		return Optional.of(new Build(src, ix, iz, exact).run());
	}

	/** How far (blocks) a square at an arterial crossing may slide along one of its arterials (METROFILL R1 r4): about one lot depth. */
	static final int CROSS_SLIDE = 40;

	/** Spacing of the cut lanes along a 45-degree run, and the shortest run that gets any. */
	static final int AVE_GAP_MIN = 20, AVE_GAP_VAR = 17, AVE_MIN_RUN = 20;
	/** Longest cut lane, and the shortest that is kept (a lane needs a straight run of lot-width on each side). */
	static final int AVE_LANE_MAX = 56, AVE_LANE_MIN = 14;

	/**
	 * Quay runs as every tile that sees them grades them: each from its end with the smaller column key (a contour chain starts where
	 * the window cuts it, so the same run can come either way), in order of their smallest column (ties by first column), the order a
	 * later run meets an earlier one in.
	 */
	static List<List<int[]>> canonicalQuays(List<List<int[]>> runs) {
		List<List<int[]>> out = new ArrayList<>();
		for (List<int[]> r : runs) {
			List<int[]> o = new ArrayList<>(r);
			if (qkey(o.get(o.size() - 1)[0], o.get(o.size() - 1)[1]) < qkey(o.get(0)[0], o.get(0)[1])) java.util.Collections.reverse(o);
			out.add(o);
		}
		out.sort(Comparator.comparingLong((List<int[]> r) -> qsmallest(r)).thenComparingLong(r -> qkey(r.get(0)[0], r.get(0)[1])).thenComparingInt(List::size));
		return out;
	}

	private static long qkey(int x, int z) { return ((long) z << 32) ^ (x & 0xffffffffL); }

	private static long qsmallest(List<int[]> cols) {
		long best = Long.MAX_VALUE;
		for (int[] c : cols) best = Math.min(best, qkey(c[0], c[1]));
		return best;
	}

	/** The wall rings of the settlement (the centre cell's walls) as ring lots, the same in every tile. */
	static List<List<WallRing.Piece>> wallRings(SkeletonSource src) {
		List<List<WallRing.Piece>> out = new ArrayList<>();
		Cell centreCell = CellGeometry.owner(src.view().field(), src.site().centre().x(), src.site().centre().z());
		if (!src.site().cells().contains(centreCell)) return out;
		CellSkeleton sk = src.skeleton(centreCell);
		for (Wall w : sk.walls()) {
			List<Pt> gates = new ArrayList<>();
			for (Gate g : sk.gates()) if (g.kind() == GateKind.WALL) gates.add(new Pt(g.x(), g.z()));
			out.add(WallRing.pieces(w, gates, src.grid(), src.seed()));
		}
		return out;
	}

	/** Gate pins of a whole street (ROADS3; {@code Build.gatePins}) for one wall ring, added to {@code pins} (created when null and some column passes a gate). */
	static TreeMap<Integer, Integer> ringGatePins(List<WallRing.Piece> pieces, java.util.function.Supplier<int[]> levels, List<int[]> cols, TreeMap<Integer, Integer> pins) {
		for (int k = 0; k < pieces.size(); k++) {
			WallRing.Piece p = pieces.get(k);
			if (p.gate() == WallRing.NONE) continue;
			Rect b = WallRing.band(p);
			boolean alongX = p.front() == Facing.NORTH || p.front() == Facing.SOUTH; // the passage runs along z
			Rect e = alongX ? new Rect(b.x(), b.z() - 1, b.w(), b.d() + 2) : new Rect(b.x() - 1, b.z(), b.w() + 2, b.d());
			for (int i = 0; i < cols.size(); i++) {
				int[] c = cols.get(i);
				if (!e.contains(c[0], c[1])) continue;
				if (pins == null) pins = new TreeMap<>();
				pins.put(i, levels.get()[k] + 1);
			}
		}
		return pins;
	}

	/** Index ranges {first, last} of the maximal runs of a street's columns that step one block in x and one in z each time. */
	static List<int[]> diagonalRuns(List<int[]> cols) {
		List<int[]> out = new ArrayList<>();
		int i = 0;
		while (i + 1 < cols.size()) {
			int dx = cols.get(i + 1)[0] - cols.get(i)[0], dz = cols.get(i + 1)[1] - cols.get(i)[1];
			int j = i + 1;
			while (j + 1 < cols.size() && cols.get(j + 1)[0] - cols.get(j)[0] == dx && cols.get(j + 1)[1] - cols.get(j)[1] == dz) j++;
			if (dx != 0 && dz != 0) out.add(new int[] {i, j});
			i = j;
		}
		return out;
	}

	/** A street under construction: its centre-line columns, in order. {@code fixed} streets are never dropped. */
	static final class Raw {
		final String id;
		final RoadKind kind;
		final int width;
		final List<int[]> cols;
		final boolean fixed;
		Street street;
		/** Lots may front this street: its piece within the tile (plus margin) holds a skeleton road, so it is not a fragment that only reaches a tile border. */
		boolean lotOk = true;

		Raw(String id, RoadKind kind, int width, List<int[]> cols, boolean fixed) {
			this.id = id;
			this.kind = kind;
			this.width = width;
			this.cols = cols;
			this.fixed = fixed;
		}

		/** The graded height of every column (set by {@code grade}). */
		int[] y;
		/** {@code grade} found no profile that meets the streets this one touches within the grade limit (JUNCTION). */
		boolean unmet;

		/** For a piece of a whole street: its slice of the whole profile and which columns need more cut or fill than allowed (null otherwise). */
		int[] wholeY;
		boolean[] steep;

		Raw withWhole(Raw from) {
			wholeY = from.wholeY;
			steep = from.steep;
			return this;
		}

		boolean skeletonish() { return kind == RoadKind.ARTERIAL || kind == RoadKind.MAIN || kind == RoadKind.PATH; }

		/** A skeleton road, or a border street of the settlement's lattice: the streets everything else attaches to. */
		boolean macro() { return skeletonish() || (kind == RoadKind.STREET && id.length() > 1 && id.charAt(0) == 'b' && Character.isDigit(id.charAt(1))); }
	}

	private static final class Build {
		final SkeletonSource src;
		final StreetRules rules;
		final DistrictGrid grid;
		final DistrictGrid.Tile tile;
		final Rect tr;
		final int ix, iz;
		final String id;
		final TerrainProbe exact;
		final DensityField den;
		final BuiltEdge edge;
		final QuarterField qf;
		final long seed;
		final Tier tier;
		// raster over the tile and PAD blocks round it
		final int rx0, rz0, rw, rd;
		final byte[] flags;
		final short[] owner;
		final List<Raw> ownerList = new ArrayList<>();
		final TreeMap<Long, Integer> floorMemo = new TreeMap<>();
		final TreeMap<Long, Boolean> wetMemo = new TreeMap<>();

		final List<Raw> raws = new ArrayList<>();
		final List<Plaza> plazas = new ArrayList<>();
		final List<Landmark> landmarks = new ArrayList<>();
		final List<Rect> reserved = new ArrayList<>();
		/** The wall ring's lots in and near this tile, less gate passages: the one reservation both sides of a tile border share. */
		final List<Rect> wallBlocked = new ArrayList<>();
		final List<LotBrief> lots = new ArrayList<>();
		final List<Integer> lotAir = new ArrayList<>();
		final List<String> notes = new ArrayList<>();

		Build(SkeletonSource src, int ix, int iz, TerrainProbe exact) { this(src, ix, iz, exact, true); }

		/** {@code water} false skips the water raster (a tile only screened, or asked for the settlement's shared values). */
		Build(SkeletonSource src, int ix, int iz, TerrainProbe exact, boolean water) {
			this.src = src;
			this.ix = ix;
			this.iz = iz;
			this.exact = exact;
			this.rules = new StreetRules(src);
			this.grid = src.grid();
			this.tile = grid.tile(ix, iz);
			this.tr = tile.rect();
			this.id = Ids.district(ix, iz);
			this.den = src.density();
			this.edge = new BuiltEdge(src);
			this.qf = src.quarters();
			this.seed = src.seed();
			this.tier = src.site().tier();
			this.rx0 = tile.minX() - PAD;
			this.rz0 = tile.minZ() - PAD;
			this.rw = tile.widthX() + 2 * PAD;
			this.rd = tile.widthZ() + 2 * PAD;
			this.flags = new byte[rw * rd];
			this.owner = new short[rw * rd];
			if (water) markWater();
			markApproaches();
		}

		// ---- terrain access, memoised

		static long key(int x, int z) { return ((long) z << 32) ^ (x & 0xffffffffL); }

		int fl(int x, int z) {
			Integer v = floorMemo.get(key(x, z));
			if (v == null) { v = exact.floorY(x, z); floorMemo.put(key(x, z), v); }
			return v;
		}

		boolean wt(int x, int z) {
			Boolean v = wetMemo.get(key(x, z));
			if (v == null) { v = exact.waterDepth(x, z) > 0; wetMemo.put(key(x, z), v); }
			return v;
		}

		/** Flags a 7 x 7 block round every wet 4-lattice point, so lots keep off water the lattice might step over. */
		void markWater() {
			for (int pz = Math.floorDiv(rz0, 4) * 4; pz < rz0 + rd + 4; pz += 4) {
				for (int px = Math.floorDiv(rx0, 4) * 4; px < rx0 + rw + 4; px += 4) {
					if (!wt(px, pz)) continue;
					for (int z = pz - 3; z <= pz + 3; z++) for (int x = px - 3; x <= px + 3; x++) or(x, z, WET);
				}
			}
		}

		/** ROADJOIN step 4: the columns of the settlement's approaches (width + 2) stay free of lots, buildings and plazas; streets may cross them. */
		void markApproaches() {
			for (var a : src.approaches()) {
				double[][] st = dev.fractalbuildings.core.road.Curve.stations(a.nodes());
				if (st.length < 2) continue;
				for (var l : dev.fractalbuildings.core.road.RoadNet.raster(st, new int[st.length], new boolean[st.length], a.width() + 2).values())
					for (var c : l) or(c.x(), c.z(), APPROACH);
			}
		}

		boolean inR(int x, int z) { return x >= rx0 && z >= rz0 && x < rx0 + rw && z < rz0 + rd; }

		int fg(int x, int z) { return inR(x, z) ? flags[(z - rz0) * rw + (x - rx0)] : 0; }

		void or(int x, int z, int f) { if (inR(x, z)) flags[(z - rz0) * rw + (x - rx0)] |= (byte) f; }

		/** Free for a lot: inside the tile and nothing on it. */
		boolean free(int x, int z) { return tr.contains(x, z) && (fg(x, z) & (STREET | PLAZA | LOT | RESERVED | WET | APPROACH)) == 0; }

		Raw streetAt(int x, int z) {
			if (!inR(x, z)) return null;
			int o = owner[(z - rz0) * rw + (x - rx0)];
			return o == 0 ? null : ownerList.get(o - 1);
		}

		void paint(Raw r) {
			ownerList.add(r);
			short idx = (short) ownerList.size();
			int w = r.width, h = w / 2;
			for (int[] c : r.cols) {
				for (int dz = 0; dz < w; dz++) {
					for (int dx = 0; dx < w; dx++) {
						int x = c[0] - h + dx, z = c[1] - h + dz;
						if (!inR(x, z)) continue;
						int i = (z - rz0) * rw + (x - rx0);
						flags[i] |= STREET;
						if (owner[i] == 0) owner[i] = idx;
					}
				}
			}
		}

		void paintRect(Rect r, int f) {
			for (int z = r.z(); z < r.z() + r.d(); z++) for (int x = r.x(); x < r.x() + r.w(); x++) or(x, z, f);
		}

		/** Per plaza (index into {@link #plazas}): its piece reaches a live road, so a hall or temple facing it can be reached. */
		boolean[] plazaOk = new boolean[0];

		/**
		 * Which streets may carry lots (ROADS). Streets and plazas that overlap or touch, in this tile and its margin, form pieces;
		 * a piece may carry lots only when it holds a column of a live root: a main road, path or arterial column of the
		 * settlement's {@link RoadNetwork} that reaches the hub. A fragment kept because it reaches the tile border, an arterial
		 * chain that never meets a main road, or the main road of a cell whose own routes failed stay in the plan but get no lots.
		 * Every tile reads the same network, so the answer does not depend on which tiles were planned before.
		 */
		void markLotEligible(List<Raw> finals) {
			RoadNetwork net = src.network();
			int n = finals.size(), np = plazas.size();
			int[] parent = new int[n + np];
			for (int i = 0; i < n + np; i++) parent[i] = i;
			short[] tmp = new short[rw * rd];
			for (int i = 0; i < n; i++) {
				Raw r = finals.get(i);
				int h = r.width / 2;
				for (int[] c : r.cols) {
					for (int dz = 0; dz < r.width; dz++) {
						for (int dx = 0; dx < r.width; dx++) {
							int x = c[0] - h + dx, z = c[1] - h + dz;
							if (!inR(x, z)) continue;
							int p = (z - rz0) * rw + (x - rx0);
							if (tmp[p] == 0) tmp[p] = (short) (i + 1);
							else union(parent, tmp[p] - 1, i);
						}
					}
				}
			}
			for (int j = 0; j < np; j++) {
				Plaza pz = plazas.get(j);
				for (int z = pz.z(); z < pz.z() + pz.d(); z++) {
					for (int x = pz.x(); x < pz.x() + pz.w(); x++) {
						if (!inR(x, z)) continue;
						int p = (z - rz0) * rw + (x - rx0);
						if (tmp[p] == 0) tmp[p] = (short) (n + j + 1);
						else union(parent, tmp[p] - 1, n + j);
					}
				}
			}
			for (int z = 0; z < rd; z++) {
				for (int x = 0; x < rw; x++) {
					int a = tmp[z * rw + x];
					if (a == 0) continue;
					if (x + 1 < rw && tmp[z * rw + x + 1] != 0) union(parent, a - 1, tmp[z * rw + x + 1] - 1);
					if (z + 1 < rd && tmp[(z + 1) * rw + x] != 0) union(parent, a - 1, tmp[(z + 1) * rw + x] - 1);
				}
			}
			boolean[] good = new boolean[n + np];
			for (int i = 0; i < n; i++) {
				Raw r = finals.get(i);
				if (!r.macro()) continue;
				for (int[] c : r.cols) if (net.live(c[0], c[1])) { good[find(parent, i)] = true; break; }
			}
			for (int i = 0; i < n; i++) finals.get(i).lotOk = good[find(parent, i)];
			plazaOk = new boolean[np];
			for (int j = 0; j < np; j++) plazaOk[j] = good[find(parent, n + j)];
		}

		/**
		 * The last guard of JUNCTION: a street whose end face still touches another street's side more than a block away (a piece of a
		 * whole street cut by this tile's own reservation, which the junction field cannot know of) loses its end columns until it no
		 * longer does (a street running beside another to its end), one column at a time, keeping at least its width plus two columns, and only inside the tile clear of its border band (where
		 * the neighbouring tile must draw the same footprint). In id order; deterministic.
		 */
		void trimAbuttingEnds(List<Raw> finals) {
			for (Raw r : finals) {
				if (r.y == null || r.y.length != r.cols.size()) continue;
				for (int side = 0; side < 2; side++) {
					while (r.cols.size() > r.width + 2) {
						int[] end = side == 0 ? r.cols.get(0) : r.cols.get(r.cols.size() - 1);
						int m = r.width / 2 + 3;
						if (end[0] < tile.minX() + m || end[0] >= tile.maxX() - m || end[1] < tile.minZ() + m || end[1] >= tile.maxZ() - m) break;
						if (!abutsAtAnotherLevel(r, side, finals)) break;
						if (!stillJoined(r, side, finals)) { relevelEnd(r, side, finals); break; } // never cut a street off the streets it hangs from: bring its end to their level instead
						try {
							r.cols.remove(side == 0 ? 0 : r.cols.size() - 1);
						} catch (UnsupportedOperationException e) {
							break;
						}
						r.y = side == 0 ? java.util.Arrays.copyOfRange(r.y, 1, r.y.length) : java.util.Arrays.copyOf(r.y, r.y.length - 1);
						if (r.wholeY != null) {
							r.wholeY = r.y;
							r.steep = side == 0 ? java.util.Arrays.copyOfRange(r.steep, 1, r.steep.length) : java.util.Arrays.copyOf(r.steep, r.steep.length - 1);
						}
						r.street = street(r, r.y);
						notes.add("street " + r.id + " stops a column short of a street at another level");
					}
				}
			}
		}

		/**
		 * Brings the end face of {@code r} at {@code side} to the level of the streets it touches there, ramping back to its own profile at
		 * 1 in 3, when the result keeps the grade, stays inside the tile clear of its border band and still meets every street it overlaps
		 * within a block. False (and no change) otherwise.
		 */
		boolean relevelEnd(Raw r, int side, List<Raw> finals) {
			int h = r.width / 2, n = r.cols.size();
			int[] b = footprintBox(r.cols, r.width);
			Layer mine = cells(r.cols, r.width, r.y, b[0], b[1], b[2], b[3], side == 0, side == 1);
			List<Layer> others = new ArrayList<>();
			for (Raw s : finals) {
				if (s == r || s.y == null || SettlementChecks.wholeStreet(s.id).equals(SettlementChecks.wholeStreet(r.id))) continue;
				Layer l = cells(s.cols, s.width, s.y, b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, false, false);
				if (l != null) others.add(l);
			}
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			int e0 = side == 0 ? 0 : Math.max(0, n - 1 - h), e1 = side == 0 ? Math.min(n - 1, h) : n - 1;
			for (int i = e0; i <= e1; i++) {
				int[] c = r.cols.get(i);
				for (int dz = 0; dz < r.width; dz++) for (int dx = 0; dx < r.width; dx++) {
					int x = c[0] - h + dx, z = c[1] - h + dz;
					int[] v = mine.at(x, z);
					if (v == null || v[2] != 1) continue;
					for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						if (mine.at(x + d[0], z + d[1]) != null) continue;
						for (Layer o : others) {
							int[] w = o.at(x + d[0], z + d[1]);
							if (w == null || o.at(x, z) != null) continue;
							lo = Math.min(lo, w[0]);
							hi = Math.max(hi, w[1]);
						}
					}
				}
			}
			if (lo == Integer.MAX_VALUE || hi - lo > 2) return false;
			int t = Math.floorDiv(lo + hi, 2), edge = side == 0 ? e1 : e0;
			int[] y = r.y.clone();
			int m = r.width / 2 + 3;
			for (int k = 0; k < n; k++) {
				int dist = side == 0 ? Math.max(0, k - edge) : Math.max(0, edge - k);
				int v = Math.max(Math.min(y[k], t + dist / Grading.GRADE_RUN), t - dist / Grading.GRADE_RUN);
				if (v == y[k]) continue;
				int[] c = r.cols.get(k);
				if (c[0] < tile.minX() + m || c[0] >= tile.maxX() - m || c[1] < tile.minZ() + m || c[1] >= tile.maxZ() - m) return false;
				y[k] = v;
			}
			int[] xs = new int[n], zs = new int[n];
			for (int k = 0; k < n; k++) { xs[k] = r.cols.get(k)[0]; zs[k] = r.cols.get(k)[1]; }
			if (!SettlementChecks.roadGrade(r.id, xs, zs, y).isEmpty()) return false;
			Layer moved = cells(r.cols, r.width, y, b[0], b[1], b[2], b[3], false, false);
			for (int z = b[1]; z <= b[3]; z++) for (int x = b[0]; x <= b[2]; x++) {
				int[] v = moved.at(x, z);
				if (v == null) continue;
				for (Layer o : others) { int[] w = o.at(x, z); if (w != null && Math.abs(w[0] - v[0]) > 1) return false; }
			}
			r.y = y;
			if (r.wholeY != null) r.wholeY = y;
			r.street = street(r, y);
			notes.add("street " + r.id + " brought to the level of the street its end meets");
			return true;
		}

		/** Without its end column at {@code side}, the street's footprint still overlaps or touches (across a cell edge) another street. */
		boolean stillJoined(Raw r, int side, List<Raw> finals) {
			List<int[]> rest = side == 0 ? r.cols.subList(1, r.cols.size()) : r.cols.subList(0, r.cols.size() - 1);
			int h = r.width / 2;
			int[] b = footprintBox(rest, r.width);
			for (Raw s : finals) {
				if (s == r || s.y == null || SettlementChecks.wholeStreet(s.id).equals(SettlementChecks.wholeStreet(r.id))) continue;
				Layer l = cells(s.cols, s.width, s.y, b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, false, false);
				if (l == null) continue;
				for (int[] c : rest) {
					for (int dz = -1; dz <= r.width; dz++) {
						for (int dx = -1; dx <= r.width; dx++) {
							if ((dx == -1 || dx == r.width) && (dz == -1 || dz == r.width)) continue; // corners do not join
							if (l.at(c[0] - h + dx, c[1] - h + dz) != null) return true;
						}
					}
				}
			}
			return false;
		}

		/** The end face of {@code r} at one end (its first or last {@code width / 2 + 1} columns' cells) touches a cell of another street, across one cell edge and not shared, more than a block away. */
		boolean abutsAtAnotherLevel(Raw r, int side, List<Raw> finals) {
			int h = r.width / 2, n = r.cols.size();
			int[] b = footprintBox(r.cols, r.width);
			Layer mine = cells(r.cols, r.width, r.y, b[0], b[1], b[2], b[3], side == 0, side == 1);
			List<Layer> others = new ArrayList<>();
			for (Raw s : finals) {
				if (s == r || s.y == null || SettlementChecks.wholeStreet(s.id).equals(SettlementChecks.wholeStreet(r.id))) continue;
				Layer l = cells(s.cols, s.width, s.y, b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, false, false);
				if (l != null) others.add(l);
			}
			if (others.isEmpty()) return false;
			for (int i = side == 0 ? 0 : Math.max(0, n - 1 - h); i <= (side == 0 ? Math.min(n - 1, h) : n - 1); i++) {
				int[] c = r.cols.get(i);
				for (int dz = 0; dz < r.width; dz++) {
					for (int dx = 0; dx < r.width; dx++) {
						int x = c[0] - h + dx, z = c[1] - h + dz;
						int[] v = mine.at(x, z);
						if (v == null || v[2] != 1) continue; // a cell of this end face
						for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
							if (mine.at(x + d[0], z + d[1]) != null) continue;
							for (Layer o : others) {
								int[] w = o.at(x + d[0], z + d[1]);
								if (w == null || o.at(x, z) != null) continue;
								if (Math.abs(w[0] - v[0]) > 1) return true;
							}
						}
					}
				}
			}
			return false;
		}

		/** Hall and temple lots placed on a square, with the square they face. */
		final List<Object[]> onSquare = new ArrayList<>();

		/**
		 * A hall or temple lot faces its square only; when the square's piece reaches no live road (a square joined to no street, or to
		 * a cut-off fragment) the lot is taken back, so {@link #landmarkFallback} can put the building on a reachable street.
		 */
		void dropCutOffHallLots() {
			boolean dropped = false;
			for (Object[] o : onSquare) {
				LotBrief lot = (LotBrief) o[0];
				Rect sq = (Rect) o[1];
				int j = -1;
				for (int k = 0; k < plazas.size(); k++) if (plazas.get(k).x() == sq.x() && plazas.get(k).z() == sq.z() && plazas.get(k).w() == sq.w() && plazas.get(k).d() == sq.d()) j = k;
				if (j >= 0 && plazaOk[j]) continue;
				int idx = lots.indexOf(lot);
				lots.remove(idx);
				lotAir.remove(idx);
				reserved.remove(lot.rect());
				clearRect(lot.rect(), LOT | RESERVED);
				notes.add(lot.type() + " lot " + lot.id() + " taken back: its square reaches no road");
				unplaced.add((Landmark) o[2]);
				dropped = true;
			}
			if (!dropped) return;
			for (Rect r : reserved) paintRect(r, RESERVED);
			for (LotBrief l : lots) paintRect(l.rect(), LOT);
			renumberLots();
		}

		void clearRect(Rect r, int f) {
			for (int z = r.z(); z < r.z() + r.d(); z++) for (int x = r.x(); x < r.x() + r.w(); x++) if (inR(x, z)) flags[(z - rz0) * rw + (x - rx0)] &= (byte) ~f;
		}

		/** Lot ids follow the list order ({@code <district>.l<index>}). */
		void renumberLots() {
			for (int i = 0; i < lots.size(); i++) {
				LotBrief l = lots.get(i);
				String nid = id + ".l" + i;
				if (!l.id().contains(WallRing.MARK) && !l.id().equals(nid)) lots.set(i, new LotBrief(nid, l.x(), l.z(), l.w(), l.d(), l.front(), l.type(), l.style(), l.minStoreys(), l.maxStoreys(), l.streets(), l.airRights(), l.frontY(), l.density(), l.status(), l.walk()));
			}
		}

		/** Hall and temple landmarks of this tile that got no lot facing a square. */
		final List<Landmark> unplaced = new ArrayList<>();

		/**
		 * A hall or temple that found no room facing its square takes the street-front lot nearest the square (or its own spot when
		 * there is no square) that can hold the type: a town hall or temple a street away from the square rather than none. The
		 * landmark's reservation is dropped once its building has a lot.
		 */
		void landmarkFallback() {
			for (Landmark l : unplaced) {
				String type = l.kind() == LandmarkKind.HALL ? "hall" : "temple";
				int cx = l.x() + l.w() / 2, cz = l.z() + l.d() / 2;
				if (!squares.isEmpty()) {
					long best = Long.MAX_VALUE;
					for (Rect s : squares) {
						long dx = s.x() + s.w() / 2 - cx, dz = s.z() + s.d() / 2 - cz;
						if (dx * dx + dz * dz < best) { best = dx * dx + dz * dz; cx = s.x() + s.w() / 2; cz = s.z() + s.d() / 2; }
					}
				}
				int pick = -1;
				long best = Long.MAX_VALUE;
				for (int i = 0; i < lots.size(); i++) {
					LotBrief o = lots.get(i);
					if (!o.builds() || o.type().equals("hall") || o.type().equals("temple") || o.type().equals("block")) continue;
					long dx = o.x() + o.w() / 2 - cx, dz = o.z() + o.d() / 2 - cz, dd = dx * dx + dz * dz;
					if (dd >= best || !Briefs.buildable(type, o, exactRelief(o.rect()))) continue;
					best = dd;
					pick = i;
				}
				if (pick < 0) { notes.add(type + " got no lot anywhere in the tile"); continue; }
				LotBrief o = lots.get(pick);
				TypeLimits lim = Types.get(type).limits();
				lots.set(pick, new LotBrief(o.id(), o.x(), o.z(), o.w(), o.d(), o.front(), type, o.style(), lim.minStoreys(), lim.maxStoreys(), o.streets(), o.airRights(), o.frontY(), o.density(), o.status(), o.walk()));
				landmarks.removeIf(m -> m == l); // skeleton landmark ids are not unique yet ("l?"): remove this one only
				notes.add(type + " placed on lot " + o.id() + " (no room facing the square)");
			}
		}

		/**
		 * A hall or temple that found no room facing its square gets its own lot on a reachable street of the tile, before the
		 * frontage lots take the ground: the type's smallest lot (a few blocks deeper when there is room), on the straight run of a
		 * lot-eligible street whose front column is nearest the square (or the landmark's spot without one), on dry ground the type
		 * accepts. Candidates every 2 columns along every run, both sides.
		 */
		void landmarkLots(List<Raw> finals) {
			List<Landmark> placed = new ArrayList<>();
			unplaced.sort(Comparator.comparing((Landmark m) -> m.kind() == LandmarkKind.HALL ? 0 : 1)); // the hall first: it is the one a town cannot do without
			for (Landmark l : unplaced) {
				String type = l.kind() == LandmarkKind.HALL ? "hall" : "temple";
				TypeLimits lim = Types.get(type).limits();
				int cx = l.x() + l.w() / 2, cz = l.z() + l.d() / 2;
				long sb = Long.MAX_VALUE;
				for (Rect s : squares) {
					long dx = s.x() + s.w() / 2 - (l.x() + l.w() / 2), dz = s.z() + s.d() / 2 - (l.z() + l.d() / 2);
					if (dx * dx + dz * dz < sb) { sb = dx * dx + dz * dz; cx = s.x() + s.w() / 2; cz = s.z() + s.d() / 2; }
				}
				Rect best = null;
				Facing bestFront = null;
				Raw bestStreet = null;
				long bestD = Long.MAX_VALUE;
				for (int slack = 2; slack >= 0 && best == null; slack -= 2) {
				int along = lim.minLotWidth() + slack, deep = lim.minLotDepth() + slack;
				for (Raw r : finals) {
					if (!r.lotOk) continue;
					for (Frame f : frames(r)) {
						for (int u = f.uLo; u + along - 1 <= f.uHi; u += 2) {
							Rect c = f.rect(u, along, deep);
							long dx = c.x() + c.w() / 2 - cx, dz = c.z() + c.d() / 2 - cz, dd = dx * dx + dz * dz;
							if (dd >= bestD) continue;
							boolean ok = true;
							for (int k = u; k < u + along && ok; k++) if (f.depth(k) < deep) ok = false;
							if (!ok || !dry(c) || onKeptLandmark(c)) continue;
							LotBrief probe = new LotBrief("probe", c.x(), c.z(), c.w(), c.d(), f.front, type, StyleLibrary.AUTO, lim.minStoreys(), lim.maxStoreys(), 1, 0, Brief.NO_FRONT_Y, 0, LotBrief.BUILD);
							if (!Briefs.buildable(type, probe, exactRelief(c))) continue;
							bestD = dd;
							best = c;
							bestFront = f.front;
							bestStreet = r;
						}
					}
				}
				}
				if (best == null) continue;
				paintRect(best, LOT);
				LotBrief lot = addLot(best, bestFront, type, lim.minStoreys(), lim.maxStoreys(), den.at(best.x() + best.w() / 2, best.z() + best.d() / 2), bestStreet);
				landmarks.removeIf(m -> m == l); // skeleton landmark ids are not unique yet ("l?"): remove this one only
				notes.add(type + " got lot " + lot.id() + " on street " + bestStreet.id + " (no room facing the square)");
				placed.add(l);
			}
			unplaced.removeAll(placed);
		}

		/** The lot frames of a street's straight axis runs (both sides), as {@link #frontageLots} lays them. */
		List<Frame> frames(Raw r) {
			List<Frame> out = new ArrayList<>();
			List<int[]> cols = r.cols;
			int i = 0;
			while (i + 1 < cols.size()) {
				int dx = cols.get(i + 1)[0] - cols.get(i)[0], dz = cols.get(i + 1)[1] - cols.get(i)[1];
				int j = i + 1;
				while (j + 1 < cols.size() && cols.get(j + 1)[0] - cols.get(j)[0] == dx && cols.get(j + 1)[1] - cols.get(j)[1] == dz) j++;
				if ((dx == 0) != (dz == 0) && j - i >= MIN_W) {
					int[] a = cols.get(i), b = cols.get(j);
					int h = r.width / 2, w = r.width;
					if (dz == 0) {
						int z = a[1], x0 = Math.min(a[0], b[0]), x1 = Math.max(a[0], b[0]);
						out.add(new Frame(0, z - h - 1, 1, 0, 0, -1, x0 - h, x1 - h + w - 1, Facing.SOUTH));
						out.add(new Frame(0, z - h + w, 1, 0, 0, 1, x0 - h, x1 - h + w - 1, Facing.NORTH));
					} else {
						int x = a[0], z0 = Math.min(a[1], b[1]), z1 = Math.max(a[1], b[1]);
						out.add(new Frame(x - h - 1, 0, 0, 1, -1, 0, z0 - h, z1 - h + w - 1, Facing.EAST));
						out.add(new Frame(x - h + w, 0, 0, 1, 1, 0, z0 - h, z1 - h + w - 1, Facing.WEST));
					}
				}
				i = j;
			}
			return out;
		}

		/** Highest minus lowest exact ground over every column (what {@link Briefs#fill} measures). */
		int exactRelief(Rect r) {
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int z = r.z(); z < r.z() + r.d(); z++) for (int x = r.x(); x < r.x() + r.w(); x++) { int y = fl(x, z); lo = Math.min(lo, y); hi = Math.max(hi, y); }
			return hi - lo;
		}

		// ---- the plan

		Detail run() {
			landmarksAndPlazas();
			wallLots();
			skeletonStreets();
			borderStreets();
			gridStreets();
			finishBase();
			subdivide();
			ports();
			plazaLanes();
			dropUnrooted(true);
			hallLots();
			quays();
			dropUnrooted(false);
			frontageLanes();
			avenueLanes();
			dropUnrooted(false);
			List<Raw> finals = new ArrayList<>();
			for (Raw r : raws) if (r.street != null) finals.add(r);
			finals.sort(Comparator.comparing(r -> r.id));
			trimAbuttingEnds(finals);
			markLotEligible(finals);
			dropCutOffHallLots();
			ownerList.clear();
			java.util.Arrays.fill(owner, (short) 0);
			for (Raw r : finals) paint(r);
			landmarkLots(finals);
			blockLots();
			frontageLots(finals);
			landmarkFallback();
			avenueGreens(finals);
			return assemble(finals);
		}

		// ---- landmarks, plazas, hall and temple lots

		List<Landmark> found;
		List<Rect> all, squares;

		void landmarksAndPlazas() {
			paintSkeletonRoads();
			found = new ArrayList<>();
			// landmarks of this tile from every cell that reaches it (the hub's from the centre cell, squares at arterial crossings from the cell that owns the crossing)
			TreeMap<Cell, Boolean> nearCells = new TreeMap<>(CellGeometry::compare);
			RegionField rf = src.view().field();
			for (int z = tile.minZ() - 64; z <= tile.maxZ() + 64; z += 32) {
				for (int x = tile.minX() - 64; x <= tile.maxX() + 64; x += 32) {
					Cell oc = CellGeometry.owner(rf, x, z);
					if (src.site().cells().contains(oc)) nearCells.put(oc, true);
				}
			}
			for (Cell oc : nearCells.keySet()) for (Landmark l : src.skeleton(oc).landmarks()) if (l.district().equals(id)) found.add(l);
			all = new ArrayList<>();
			for (Landmark l : found) all.add(new Rect(l.x(), l.z(), l.w(), l.d()));
			squares = new ArrayList<>();
			int pk = 0;
			for (int i = 0; i < found.size(); i++) {
				Landmark l = found.get(i);
				if (!open(l.kind())) continue;
				Rect r = all.get(i);
				boolean crossing = l.kind() == LandmarkKind.SQUARE && crossingSquare(r);
				Optional<Grading.PlazaGrade> g = !dry(r) ? Optional.empty() : crossing ? meetingPlaza(r) : Grading.plaza(r, exact);
				if (g.isEmpty()) {
					// Exact terrain disagrees with the coarse probe the skeleton was placed on: search nearby for a spot that grades,
					// within NEAR_RINGS full size then shrunk (never below MIN_SQUARE), then the same out to SHIFT_RINGS; nearest shifts first. A first round wants dry ground HALL_ROOM blocks all round (room for the hall and temple facing it), a second round takes any grading spot.
					Rect from = r;
					search:
					for (int room = l.kind() == LandmarkKind.SQUARE ? HALL_ROOM : 0; room >= 0; room -= HALL_ROOM)
					if (crossing) {
						// METROFILL R1 (architect): a square at an arterial crossing slides only along one of its two arterials, away from the
						// crossing and at most CROSS_SLIDE blocks, so one side stays on the arterial; shrunk with its crossing corner kept
						for (Rect c : crossingSlides(from)) {
							boolean clash = false;
							for (int j = 0; j < all.size(); j++) if (j != i && open(found.get(j).kind()) && overlaps(c, all.get(j))) clash = true;
							if (clash || !dry(c) || (room > 0 && !dry(new Rect(c.x() - room, c.z() - room, c.w() + 2 * room, c.d() + 2 * room)))) continue;
							g = meetingPlaza(c);
							if (g.isPresent()) {
								notes.add("plaza " + l.kind().json + " moved from " + from.x() + "," + from.z() + " " + from.w() + "x" + from.d() + " to " + c.x() + "," + c.z() + " " + c.w() + "x" + c.d() + " along its arterial (rough or wet ground)");
								r = c;
								if (c.x() != from.x() || c.z() != from.z() || c.w() != from.w()) moved.add(c);
								break search;
							}
						}
					} else
					for (int reach = NEAR_RINGS; reach <= (l.kind() == LandmarkKind.SQUARE ? SHIFT_RINGS : NEAR_RINGS); reach += SHIFT_RINGS - NEAR_RINGS)
					for (int shrink = 0; shrink <= SQUARE_SHRINK_STEPS; shrink++) {
						Rect base = shrink == 0 ? from : new Rect(from.x() + 4 * shrink, from.z() + 4 * shrink, from.w() - 8 * shrink, from.d() - 8 * shrink);
						if (base.w() < MIN_SQUARE || base.d() < MIN_SQUARE) break;
						for (Rect c : roadSideFirst(shifts(base, shrink > 0, reach))) {
							boolean clash = false;
							for (int j = 0; j < all.size(); j++) if (j != i && open(found.get(j).kind()) && overlaps(c, all.get(j))) clash = true;
							if (clash || !dry(c) || (room > 0 && !dry(new Rect(c.x() - room, c.z() - room, c.w() + 2 * room, c.d() + 2 * room)))) continue;
							g = Grading.plaza(c, exact);
							if (g.isPresent()) {
								notes.add("plaza " + l.kind().json + " moved from " + from.x() + "," + from.z() + " " + from.w() + "x" + from.d() + " to " + c.x() + "," + c.z() + " " + c.w() + "x" + c.d() + " (rough or wet ground)");
								r = c;
								moved.add(c);
								break search;
							}
						}
					}
				}
				if (g.isEmpty()) {
					notes.add("plaza " + l.kind().json + " at " + r.x() + "," + r.z() + " dropped (rough or wet ground, no better spot within " + 16 * SHIFT_RINGS + " blocks)");
					all.set(i, r);
					continue;
				}
				// a square clear of every skeleton road (the skeleton clamped it into the tile away from the hub) is joined by lanes like a moved one
				if (!moved.contains(r) && !touchesSkeleton(r)) moved.add(r);
				all.set(i, r);
				plazas.add(new Plaza("p" + pk++, l.kind().json, r.x(), r.z(), r.w(), r.d(), g.get().level()));
				squares.add(r);
				landmarks.add(new Landmark(l.id(), l.kind(), r.x(), r.z(), r.w(), r.d(), l.district()));
				reserved.add(r);
				paintRect(r, PLAZA | RESERVED);
			}
		}

		/** Hall and temple lots beside the squares; after the lanes joining a moved square to the roads, so they do not cut them off. */
		void hallLots() {
			for (int i = 0; i < found.size(); i++) {
				Landmark l = found.get(i);
				if (open(l.kind())) continue;
				Rect r = all.get(i);
				LotBrief lot = l.kind() == LandmarkKind.HALL || l.kind() == LandmarkKind.TEMPLE ? hallLot(l, r, squares) : null;
				if (lot == null) {
					notes.add(l.kind().json + " at " + r.x() + "," + r.z() + " got no lot");
					if (l.kind() == LandmarkKind.HALL || l.kind() == LandmarkKind.TEMPLE) unplaced.add(l);
					boolean covered = false; // a lot or square placed meanwhile covers the landmark's spot: keep no reservation there
					for (LotBrief o : lots) if (overlaps(o.rect(), r)) covered = true;
					for (Rect q : squares) if (overlaps(q, r)) covered = true;
					if (covered) continue;
					landmarks.add(l);
					reserved.add(r);
					paintRect(r, RESERVED);
					continue;
				}
				lots.add(lot);
				lotAir.add(0);
				onSquare.add(new Object[] {lot, lastSquare, l});
				Rect lr = lot.rect();
				reserved.add(lr);
				paintRect(lr, LOT | RESERVED);
			}
		}

		// ---- the wall ring (D2): wall and corner-tower lots, reserved so that streets stop at them except through a gate's passage

		/** One wall ring of the centre cell: its pieces, and the walk profile of the whole ring ({@link WallRing#walkLevels}), worked out on first use. */
		final class RingInfo {
			final List<WallRing.Piece> pieces;
			int[] levels;

			RingInfo(List<WallRing.Piece> pieces) { this.pieces = pieces; }

			int[] levels() {
				if (levels == null) levels = WallRing.walkLevels(pieces, q -> groundTop(q.rect()));
				return levels;
			}
		}

		List<RingInfo> rings;

		/** The wall rings of the settlement (the centre cell's walls), the same in every tile. */
		List<RingInfo> rings() {
			if (rings != null) return rings;
			rings = new ArrayList<>();
			for (List<WallRing.Piece> pieces : wallRings(src)) rings.add(new RingInfo(pieces));
			return rings;
		}

		/** The settlement's junction field (JUNCTION), built once per source and probe. */
		JunctionField junctions() {
			if (junctionField == null) junctionField = src.junctions(exact);
			return junctionField;
		}

		JunctionField junctionField;

		/** The pins of a whole street: its junction pads ({@link JunctionField}), and gate passages over them (a gate's level is the wall's). Null when none. */
		TreeMap<Integer, Integer> pins(String key, List<int[]> cols) {
			TreeMap<Integer, Integer> j = key == null ? null : junctions().pins(key, cols), g = gatePins(cols);
			if (j == null) return g;
			if (g != null) j.putAll(g);
			return j;
		}

		/**
		 * Gate pins of a whole street (ROADS3): every centre column of it that lies in a gate's passage band, or one column beyond either
		 * face, must sit at the passage floor (the gate lot's ring level plus one, the first free y over its slab), so the road runs
		 * through the gate at the gate's height on both sides. Keyed by column index; null when the street passes no gate. A pure
		 * function of the ring and the exact ground, so every tile that grades the street pins it the same.
		 */
		TreeMap<Integer, Integer> gatePins(List<int[]> cols) {
			TreeMap<Integer, Integer> pins = null;
			for (RingInfo ri : rings()) pins = ringGatePins(ri.pieces, ri::levels, cols, pins);
			return pins;
		}

		void wallLots() {
			for (RingInfo ri : rings()) {
				List<WallRing.Piece> pieces = ri.pieces;
				int[] levels = null; List<String> types = pieces.stream().map(WallRing.Piece::type).toList(); // the walk profile of the whole ring, worked out once (by the first piece that lies in this tile)
				for (int k = 0; k < pieces.size(); k++) {
					WallRing.Piece p = pieces.get(k);
					Rect r = p.rect();
					if (!tr.containsRect(r.x(), r.z(), r.w(), r.d())) {
						// a neighbour tile's lot: keep streets and port stubs of this tile off it too, so both sides of the border agree
						if (Rect.overlap(r.x(), r.z(), r.w(), r.d(), tr.x() - 24, tr.z() - 24, tr.w() + 48, tr.d() + 48)) {
							reserved.addAll(WallRing.blocked(p));
							wallBlocked.addAll(WallRing.blocked(p));
							paintRect(r, RESERVED);
						}
						continue;
					}
					boolean taken = !free(r, STREET | PLAZA | LOT | RESERVED), wet = !taken && !dry(r); // a wall may stand beside water: only its own columns must be dry
					if (taken || wet) {
						notes.add("wall lot " + p.type() + " at " + r.x() + "," + r.z() + " dropped (" + (wet ? "wet ground" : "ground taken: " + whatIsOn(r)) + ")");
						reserved.addAll(WallRing.blocked(p)); // the neighbour tile reserves it too
						wallBlocked.addAll(WallRing.blocked(p));
						continue;
					}
					TypeLimits lim = Types.get(p.type()).limits();
					int dens = den.at(r.x() + r.w() / 2, r.z() + r.d() / 2);
					if (levels == null) levels = ri.levels();
					lots.add(new LotBrief(id + WallRing.MARK + k, r.x(), r.z(), r.w(), r.d(), p.front(), p.type(), StyleLibrary.AUTO, lim.minStoreys(), lim.maxStoreys(), 1, 0, Brief.NO_FRONT_Y, dens, LotBrief.BUILD, WallRing.walkOf(types, levels, k)));
					lotAir.add(0);
					paintRect(r, LOT | RESERVED);
					reserved.addAll(WallRing.blocked(p));
					wallBlocked.addAll(WallRing.blocked(p));
				}
			}
		}

		/** Highest exact ground over every column of the rectangle. */
		int groundTop(Rect r) {
			int hi = Integer.MIN_VALUE;
			for (int z = r.z(); z < r.z() + r.d(); z++) for (int x = r.x(); x < r.x() + r.w(); x++) hi = Math.max(hi, fl(x, z));
			return hi;
		}

		/** Which flags are set somewhere on the rectangle, for notes. */
		String whatIsOn(Rect r) {
			int f = 0;
			for (int z = r.z(); z < r.z() + r.d(); z++) for (int x = r.x(); x < r.x() + r.w(); x++) f |= fg(x, z);
			StringBuilder sb = new StringBuilder();
			if ((f & STREET) != 0) sb.append("street ");
			if ((f & PLAZA) != 0) sb.append("plaza ");
			if ((f & LOT) != 0) sb.append("lot ");
			if ((f & RESERVED) != 0) sb.append("reserved ");
			if ((f & WET) != 0) sb.append("water-margin ");
			return sb.toString().trim();
		}

		/** How far (in 16-block steps) a square that does not grade may move, and how often it may shrink (4 blocks a side per step) before it is dropped. */
		static final int SHIFT_RINGS = 10, NEAR_RINGS = 4, HALL_ROOM = 22, HALL_MAX_RELIEF = 3, SQUARE_SHRINK_STEPS = 3, MIN_SQUARE = 16;

		static boolean open(LandmarkKind k) { return k == LandmarkKind.SQUARE || k == LandmarkKind.MARKET || k == LandmarkKind.GREEN || k == LandmarkKind.PARK; }

		static boolean overlaps(Rect a, Rect b) { return Rect.overlap(a.x(), a.z(), a.w(), a.d(), b.x(), b.z(), b.w(), b.d()); }

		/**
		 * A square the skeleton placed at an arterial crossing ({@code CityProgram.squareAt}): its corner nearest a tile corner is flush with the
		 * arterial footprints along both border lines there.
		 */
		boolean crossingSquare(Rect r) {
			int in = rules.arterials().width() / 2 + 1;
			return (r.x() == tile.minX() + in || r.x() + r.w() == tile.maxX() - in + 1) && (r.z() == tile.minZ() + in || r.z() + r.d() == tile.maxZ() - in + 1);
		}

		/**
		 * A crossing square graded to meet the whole streets beside it (their levels, from the junction field, on the cells just outside
		 * its edges), so the paving and the arterial join without a step. Empty when those levels span more than 2 blocks, or when the
		 * meeting level needs a cut over the limit; a square with no whole street beside it grades as usual.
		 */
		Optional<Grading.PlazaGrade> meetingPlaza(Rect c) {
			JunctionField jf = junctions();
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			List<int[]> ring = new ArrayList<>();
			for (int x = c.x(); x < c.x() + c.w(); x++) { ring.add(new int[] {x, c.z() - 1}); ring.add(new int[] {x, c.z() + c.d()}); }
			for (int z = c.z(); z < c.z() + c.d(); z++) { ring.add(new int[] {c.x() - 1, z}); ring.add(new int[] {c.x() + c.w(), z}); }
			for (int[] q : ring) {
				int[] v = jf.levelsAt(q[0], q[1]);
				if (v == null) continue;
				lo = Math.min(lo, v[0]);
				hi = Math.max(hi, v[1]);
			}
			if (lo == Integer.MAX_VALUE) return Grading.plaza(c, exact);
			if (hi - lo > 2) return Optional.empty();
			return Grading.plaza(c, exact, lo, hi);
		}

		/**
		 * Where a crossing square may go when its own spot does not grade: shrunk (4 blocks a side per step, never below
		 * {@link #MIN_SQUARE}) with its crossing corner kept, then slid away from the crossing along either arterial in 4-block steps up to
		 * {@link #CROSS_SLIDE}, keeping its side on that arterial. Nearest first (slide, then shrink, then the z-running arterial first).
		 */
		List<Rect> crossingSlides(Rect r) {
			int in = rules.arterials().width() / 2 + 1;
			boolean west = r.x() == tile.minX() + in, north = r.z() == tile.minZ() + in; // the crossing corner's sides
			List<Rect> out = new ArrayList<>();
			for (int t = 0; t <= CROSS_SLIDE; t += 4) {
				for (int shrink = 0; shrink <= SQUARE_SHRINK_STEPS; shrink++) {
					int w = r.w() - 8 * shrink, d = r.d() - 8 * shrink;
					if (w < MIN_SQUARE || d < MIN_SQUARE) break;
					int x = west ? r.x() : r.x() + r.w() - w, z = north ? r.z() : r.z() + r.d() - d; // the corner kept
					for (int axis = 0; axis < (t == 0 ? 1 : 2); axis++) {
						int cx = x + (axis == 1 ? (west ? t : -t) : 0), cz = z + (axis == 0 ? (north ? t : -t) : 0);
						Rect c = new Rect(cx, cz, w, d);
						if (tr.containsRect(c.x(), c.z(), c.w(), c.d())) out.add(c);
					}
				}
			}
			return out;
		}

		/** Shifted copies of a rectangle on the 16-lattice, nearest first (ring order, then z, then x), inside the tile with a margin. */
		List<Rect> shifts(Rect r, boolean includeSelf, int reach) {
			List<int[]> offs = new ArrayList<>();
			for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) if (includeSelf || dx != 0 || dz != 0) offs.add(new int[] {dx, dz});
			offs.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1]).thenComparingInt(o -> o[1]).thenComparingInt(o -> o[0]));
			List<Rect> out = new ArrayList<>();
			int m = DefaultProgram.TILE_MARGIN;
			for (int[] o : offs) {
				Rect c = new Rect(r.x() + 16 * o[0], r.z() + 16 * o[1], r.w(), r.d());
				if (tr.containsRect(c.x() - m, c.z() - m, c.w() + 2 * m, c.d() + 2 * m)) out.add(c);
			}
			return out;
		}

		/** Overlaps a hall or temple landmark that got no lot and still reserves its spot. */
		boolean onKeptLandmark(Rect r) {
			for (Landmark m : landmarks) if (!open(m.kind()) && overlaps(r, new Rect(m.x(), m.z(), m.w(), m.d()))) return true;
			return false;
		}

		/** The square the last {@link #hallLot} faced. */
		Rect lastSquare;

		/** The lot of a hall or temple landmark: flush against the nearest square, grown backwards to the type's smallest lot. */
		LotBrief hallLot(Landmark l, Rect r, List<Rect> squares) {
			String type = l.kind() == LandmarkKind.HALL ? "hall" : "temple";
			TypeLimits lim = Types.get(type).limits();
			Rect sq = null;
			long best = Long.MAX_VALUE;
			int lcx = r.x() + r.w() / 2, lcz = r.z() + r.d() / 2;
			for (Rect s : squares) {
				long dx = s.x() + s.w() / 2 - lcx, dz = s.z() + s.d() / 2 - lcz, d = dx * dx + dz * dz;
				if (d < best) { best = d; sq = s; }
			}
			if (sq == null) return null;
			lastSquare = sq;
			int dx = sq.x() + sq.w() / 2 - lcx, dz = sq.z() + sq.d() / 2 - lcz;
			Facing first = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Facing.EAST : Facing.WEST) : (dz > 0 ? Facing.SOUTH : Facing.NORTH);
			// The preferred side first, then the others; on each side slide along the square (nearest the landmark first) until the lot fits.
			List<Facing> sides = new ArrayList<>(List.of(first));
			for (Facing f : new Facing[] {Facing.SOUTH, Facing.NORTH, Facing.EAST, Facing.WEST}) if (f != first) sides.add(f);
			for (Facing front : sides) {
				boolean alongX = front == Facing.NORTH || front == Facing.SOUTH;
				int along = Math.max(alongX ? r.w() : r.d(), lim.minLotWidth() + SpecPolicyV1.RAIL_ROOM_WIDTH), deep = Math.max(alongX ? r.d() : r.w(), lim.minLotDepth() + SpecPolicyV1.RAIL_ROOM_DEPTH); // with room for a setback on a slope (MINLOT)
				int w = alongX ? along : deep, d = alongX ? deep : along;
				int sqLo = alongX ? sq.x() : sq.z(), sqLen = alongX ? sq.w() : sq.d();
				int want = (alongX ? lcx : lcz) - along / 2;
				int minStart = sqLo - along + Math.min(8, sqLen), maxStart = sqLo + sqLen - Math.min(8, sqLen);
				int pref = Math.max(minStart, Math.min(maxStart, want));
				for (int k = 0; k <= maxStart - minStart; k += 4) {
					for (int sgn = (k == 0 ? 1 : -1); sgn <= 1; sgn += 2) {
						int start = pref + sgn * k;
						if (start < minStart || start > maxStart) continue;
						int x, z;
						switch (front) {
							case SOUTH -> { x = start; z = sq.z() - d; }
							case NORTH -> { x = start; z = sq.z() + sq.d(); }
							case EAST -> { x = sq.x() - w; z = start; }
							default -> { x = sq.x() + sq.w(); z = start; }
						}
						Rect lot = new Rect(x, z, w, d);
						if (!tr.containsRect(x - MARGIN, z - MARGIN, w + 2 * MARGIN, d + 2 * MARGIN)) continue; // off the border band, where its reservation would clip a street the neighbour tile keeps
						if (!rectFreeExactWet(lot) || !dry(lot) || onKeptLandmark(lot)) continue;
						if ((!moved.isEmpty() && relief(lot) > HALL_MAX_RELIEF) || !SpecPolicyV1.reliefOk(lim, relief(lot), alongX ? along : deep, alongX ? deep : along)) continue;
						int dens = den.at(x + w / 2, z + d / 2);
						return new LotBrief(id + ".l" + lots.size(), x, z, w, d, front, type, StyleLibrary.AUTO, lim.minStoreys(), lim.maxStoreys(), 1, 0, Brief.NO_FRONT_Y, dens, LotBrief.BUILD);
					}
				}
			}
			return null;
		}

		/** Nothing on any column (a landmark's own reservation does not count when {@code ignoreReserved}). */
		/** Every column of the rectangle is dry (exact probes, memoised): the 4-lattice flags are a conservative first screen only. */
		boolean dry(Rect r) {
			for (int z = r.z(); z < r.z() + r.d(); z++) for (int x = r.x(); x < r.x() + r.w(); x++) if (wt(x, z)) return false;
			return true;
		}

		boolean free(Rect r, int mask) {
			for (int z = r.z(); z < r.z() + r.d(); z++) for (int x = r.x(); x < r.x() + r.w(); x++) if ((fg(x, z) & mask) != 0) return false;
			return true;
		}

		/** Free of streets, plazas and lots; wet columns are judged by the exact probes (the caller checks {@link #dry}), not the conservative WET flags. */
		boolean rectFreeExactWet(Rect r) {
			for (int z = r.z(); z < r.z() + r.d(); z++) for (int x = r.x(); x < r.x() + r.w(); x++) if ((fg(x, z) & (STREET | PLAZA | LOT | APPROACH)) != 0) return false;
			return true;
		}

		boolean rectFree(Rect r, boolean ignoreReserved) {
			int mask = STREET | PLAZA | LOT | WET | APPROACH | (ignoreReserved ? 0 : RESERVED);
			for (int z = r.z(); z < r.z() + r.d(); z++) for (int x = r.x(); x < r.x() + r.w(); x++) if ((fg(x, z) & mask) != 0) return false;
			return true;
		}

		/** Highest minus lowest ground over the 4-lattice points of the rectangle and its four corners. */
		int relief(Rect r) {
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (int z = Math.floorDiv(r.z() + 3, 4) * 4; z < r.z() + r.d(); z += 4) {
				for (int x = Math.floorDiv(r.x() + 3, 4) * 4; x < r.x() + r.w(); x += 4) {
					int y = fl(x, z);
					lo = Math.min(lo, y);
					hi = Math.max(hi, y);
				}
			}
			for (int[] c : new int[][] {{r.x(), r.z()}, {r.x() + r.w() - 1, r.z()}, {r.x(), r.z() + r.d() - 1}, {r.x() + r.w() - 1, r.z() + r.d() - 1}}) {
				int y = fl(c[0], c[1]);
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
			}
			return hi - lo;
		}

		// ---- streets

		boolean keep(int[] c) { return c[0] >= tile.minX() - MARGIN && c[0] <= tile.maxX() + MARGIN && c[1] >= tile.minZ() - MARGIN && c[1] <= tile.maxZ() + MARGIN; }

		boolean hitsReserved(int[] c, int width) {
			for (Rect r : reserved) if (StreetCols.footprintOverlaps(c, width, r)) return true;
			return false;
		}

		/** Clips a street's columns to the tile and its margin and the reserved rectangles; each remaining run is a street. */
		void addPieces(String base, RoadKind kind, int width, List<int[]> cols, boolean fixed, String key) {
			int n = 0;
			for (Raw r : pieces(cols, width, kind, fixed, key)) {
				raws.add(new Raw(n == 0 ? base : base + "." + n, kind, width, r.cols, fixed).withWhole(r));
				n++;
			}
		}

		/**
		 * The pieces of a whole street (a skeleton road, an arterial or border segment, a grid line's run or a quay run: the same
		 * columns in every tile that draws it) kept by this tile: clipped to the tile and its margin and to the reserved squares. The
		 * whole street is graded once and each piece keeps its slice of that profile, so the tiles on both sides of a border build the
		 * road at the same height (ROADS; before, each tile graded its own clipped piece and the two disagreed by up to 14 blocks).
		 * Ids are the caller's; the returned Raws carry only columns and the slice.
		 */
		List<Raw> pieces(List<int[]> cols, int width, RoadKind kind, boolean fixed, String key) {
			return pieces(cols, width, kind, fixed, key, null, null);
		}

		/**
		 * {@link #pieces(List, int, RoadKind, boolean, String)}. A whole street of the junction field (a key) takes its profile from the
		 * field, which grades every whole street of the settlement once with its junction pads pinned; one graded already ({@code givenY},
		 * {@code givenSteep}: a quay run, {@link #quayWholes}) keeps that profile; any other is graded here with its gate pins.
		 */
		List<Raw> pieces(List<int[]> cols, int width, RoadKind kind, boolean fixed, String key, int[] givenY, boolean[] givenSteep) {
			List<Raw> out = new ArrayList<>();
			int[] wy = givenY;
			boolean[] wst = givenSteep;
			for (List<int[]> run0 : StreetCols.runs(cols, c -> keep(c) && !hitsReserved(c, width))) {
				if (wy == null) {
					JunctionField.Slice sl = key == null ? null : junctions().profile(key, cols);
					if (sl != null) { wy = sl.y(); wst = sl.steep(); }
					else {
						TreeMap<Integer, Integer> pn = pins(key, cols);
						Grading.RoadGrade wg = Grading.profile(lattice(StreetCols.vertices(cols)), memo, pn);
						wy = wg.profile().y();
						wst = new boolean[cols.size()];
						for (Grading.Steep st : wg.steep()) for (int i = st.from(); i <= st.to(); i++) wst[i] = true;
					}
				}
				int s = indexOf(cols, run0.get(0)), e = s + run0.size() - 1;
				List<int[]> run = new ArrayList<>(run0);
				int off = 0;
				if (s > 0) { int[] j = joinSquare(run.get(0), cols.get(s - 1), width); if (j != null) { run.add(0, j); off = 1; } }
				if (e + 1 < cols.size()) { int[] j = joinSquare(run.get(run.size() - 1), cols.get(e + 1), width); if (j != null) run.add(j); }
				Raw r = new Raw("piece", kind, width, run, fixed);
				r.wholeY = new int[run.size()];
				r.steep = new boolean[run.size()];
				for (int i = 0; i < run.size(); i++) {
					int w = Math.max(s, Math.min(e, s + i - off)); // a join step takes its neighbour's height
					r.wholeY[i] = wy[w];
					r.steep[i] = wst[w];
				}
				out.add(r);
			}
			return out;
		}

		static List<LatticePath.Pt> lattice(List<Pt> pts) {
			List<LatticePath.Pt> out = new ArrayList<>();
			for (Pt p : pts) out.add(new LatticePath.Pt(p.x(), p.z()));
			return out;
		}

		/** The exact probe through this tile's memo (whole streets are graded once per tile, often over the same columns). */
		final TerrainProbe memo = new TerrainProbe() {
			@Override public int floorY(int x, int z) { return fl(x, z); }
			@Override public int waterDepth(int x, int z) {
				Integer v = waterMemo.get(key(x, z));
				if (v == null) { v = exact.waterDepth(x, z); waterMemo.put(key(x, z), v); }
				return v;
			}
			@Override public int surfaceY(int x, int z) { return exact.surfaceY(x, z); }
		};
		final TreeMap<Long, Integer> waterMemo = new TreeMap<>();

		static int indexOf(List<int[]> cols, int[] c) {
			for (int i = 0; i < cols.size(); i++) if (cols.get(i) == c) return i;
			return -1;
		}

		/**
		 * A street cut where its footprint would run onto a square ends on a footprint that may touch the square only at a corner (a
		 * road leaving it at 45 degrees), which does not join it. Then one axis step from the end towards the cut column gives a
		 * footprint that shares an edge with the square without covering it; null when the end already shares an edge or no step does.
		 */
		int[] joinSquare(int[] endCol, int[] cut, int width) {
			if (!hitsReserved(cut, width) || !keep(cut)) return null;
			if (edgeTouchesReserved(endCol, width)) return null;
			int dx = cut[0] - endCol[0], dz = cut[1] - endCol[1];
			for (int[] c : new int[][] {{endCol[0] + dx, endCol[1]}, {endCol[0], endCol[1] + dz}}) {
				if ((c[0] == endCol[0] && c[1] == endCol[1]) || !keep(c) || hitsReserved(c, width)) continue;
				if (edgeTouchesReserved(c, width)) return c;
			}
			return null;
		}

		/** The column's footprint shares an edge (not just a corner) with a reserved rectangle. */
		boolean edgeTouchesReserved(int[] c, int width) {
			int h = width / 2, x0 = c[0] - h, z0 = c[1] - h, x1 = x0 + width, z1 = z0 + width;
			for (Rect r : reserved) {
				boolean zOver = z0 < r.z() + r.d() && r.z() < z1, xOver = x0 < r.x() + r.w() && r.x() < x1;
				if (zOver && (x1 == r.x() || r.x() + r.w() == x0)) return true;
				if (xOver && (z1 == r.z() || r.z() + r.d() == z0)) return true;
			}
			return false;
		}

		/** Main roads and paths of the member cells near the tile (arterials come from the pointwise rule instead). */
		List<Road> nearbyRoads() {
			TreeMap<Cell, Boolean> cells = new TreeMap<>(CellGeometry::compare);
			RegionField f = src.view().field();
			for (int z = Math.floorDiv(tile.minZ() - 64, 32) * 32; z <= tile.maxZ() + 64; z += 32) {
				for (int x = Math.floorDiv(tile.minX() - 64, 32) * 32; x <= tile.maxX() + 64; x += 32) {
					Cell c = CellGeometry.owner(f, x, z);
					if (src.site().cells().contains(c)) cells.put(c, true);
				}
			}
			List<Road> out = new ArrayList<>();
			RoadNetwork net = rules.lattice() ? src.network() : null;
			for (Cell c : cells.keySet()) for (Road r : src.skeleton(c).roads()) if (r.kind() != RoadKind.ARTERIAL && (net == null || net.reaches(r))) out.add(r);
			return out;
		}

		void skeletonStreets() {
			for (Road r : nearbyRoads()) addPieces(r.id(), r.kind(), r.width(), StreetCols.columns(r.points()), true, JunctionField.roadKey(r.id()));
		}

		/** Flags {@link #SKEL} under the skeleton roads and arterials of the tile (unclipped), so a moved square can prefer a spot beside one. */
		void paintSkeletonRoads() {
			List<Object[]> roads = new ArrayList<>();
			for (Road r : nearbyRoads()) roads.add(new Object[] {StreetCols.columns(r.points()), r.width()});
			Arterials art = rules.arterials();
			for (DistrictGrid.Segment s : art.segmentsIn(tile.minX() - MARGIN, tile.minZ() - MARGIN, tile.maxX() + MARGIN, tile.maxZ() + MARGIN)) {
				if (rules.borderKind(s) == RoadKind.ARTERIAL) roads.add(new Object[] {StreetCols.columns(art.points(s, s.from(), s.to())), art.width()});
			}
			for (Object[] o : roads) {
				@SuppressWarnings("unchecked") List<int[]> cols = (List<int[]>) o[0];
				int w = (Integer) o[1], h = w / 2;
				for (int[] c : cols) {
					if (!inR(c[0], c[1])) continue;
					for (int dz = 0; dz < w; dz++) for (int dx = 0; dx < w; dx++) or(c[0] - h + dx, c[1] - h + dz, SKEL);
				}
			}
		}

		/** The candidates beside or over a skeleton road first (a square there is on the street network without a lane), each group in its given order. */
		List<Rect> roadSideFirst(List<Rect> cands) {
			List<Rect> on = new ArrayList<>(), off = new ArrayList<>();
			for (Rect c : cands) (touchesSkeleton(c) ? on : off).add(c);
			on.addAll(off);
			return on;
		}

		/** A skeleton road's footprint covers a column of the ring just outside the rectangle (so it reaches the rectangle's edge or runs through it). */
		boolean touchesSkeleton(Rect r) {
			for (int x = r.x() - 1; x <= r.x() + r.w(); x++) if ((fg(x, r.z() - 1) & SKEL) != 0 || (fg(x, r.z() + r.d()) & SKEL) != 0) return true;
			for (int z = r.z(); z < r.z() + r.d(); z++) if ((fg(r.x() - 1, z) & SKEL) != 0 || (fg(r.x() + r.w(), z) & SKEL) != 0) return true;
			return false;
		}

		/** Arterials and plain border streets of the tile's four segments and of the neighbouring segments reaching into its margin. */
		void borderStreets() {
			Arterials art = rules.arterials();
			for (DistrictGrid.Segment s : art.segmentsIn(tile.minX() - MARGIN, tile.minZ() - MARGIN, tile.maxX() + MARGIN, tile.maxZ() + MARGIN)) {
				RoadKind k = rules.borderKind(s);
				if (k == null) continue;
				if (k == RoadKind.ARTERIAL) {
					addPieces(Ids.arterial(s.axis(), s.line(), s.from()), k, art.width(), StreetCols.columns(art.points(s, s.from(), s.to())), true, JunctionField.lineKey(s.axis(), s.fixed()));
					continue;
				}
				if (rules.lattice()) {
					addPieces("b" + s.axis() + Ids.n(s.line()) + "_" + Ids.n(s.from()), RoadKind.STREET, StreetRules.BORDER_WIDTH, StreetCols.columns(art.points(s, s.from(), s.to())), true, JunctionField.lineKey(s.axis(), s.fixed()));
					continue;
				}
				for (List<Pt> run : nodeRuns(s.from(), s.to(), (a, b) -> StreetRules.borderEdgeOk(exact, Arterials.ax(s, a), Arterials.az(s, a), Arterials.ax(s, b), Arterials.az(s, b)), s)) {
					addPieces("b" + s.axis() + Ids.n(s.line()) + "_" + Ids.n(axisCoord(s, run.get(0))), RoadKind.STREET, StreetRules.BORDER_WIDTH, StreetCols.columns(run), true, JunctionField.lineKey(s.axis(), s.fixed()));
				}
			}
		}

		/**
		 * The column lies within {@code r} blocks of a border of its tile where the street lattice has no street. Grid lines and quays stop
		 * short of such a border (neither tile may draw a piece that crosses it, and the piece would reach no street there).
		 */
		boolean nearAbsentBorder(int x, int z, int r) { return rules.nearAbsentBorder(x, z, r); }

		static int axisCoord(DistrictGrid.Segment s, Pt p) { return s.axis() == DistrictGrid.AXIS_X ? p.z() : p.x(); }

		interface EdgeTest { boolean ok(int a, int b); }

		/** Runs of consecutive ok edges between the {@value StreetRules#NODE}-block nodes from..to of a segment, as polylines (2 points each). */
		List<List<Pt>> nodeRuns(int from, int to, EdgeTest test, DistrictGrid.Segment s) {
			List<List<Pt>> out = new ArrayList<>();
			int none = Integer.MIN_VALUE, start = none;
			for (int a = from; a < to; a += StreetRules.NODE) {
				int b = Math.min(to, a + StreetRules.NODE);
				if (test.ok(a, b)) {
					if (start == none) start = a;
					if (b >= to) { out.add(line(s, start, b)); start = none; }
				} else if (start != none) {
					out.add(line(s, start, a));
					start = none;
				}
			}
			return out;
		}

		static List<Pt> line(DistrictGrid.Segment s, int a, int b) {
			return List.of(new Pt(Arterials.ax(s, a), Arterials.az(s, a)), new Pt(Arterials.ax(s, b), Arterials.az(s, b)));
		}

		/** Grid lines: the shared lines of this tile's row and column of tiles, as runs of ok edges over the tile and its margin. */
		void gridStreets() {
			int lo, hi;
			for (int axis = 0; axis < 2; axis++) {
				boolean vertical = axis == DistrictGrid.AXIS_X; // a line of constant x, running along z
				for (int pos : rules.gridLines(axis, vertical ? ix : iz)) {
					lo = Math.floorDiv(vertical ? tile.minZ() : tile.minX(), StreetRules.NODE) * StreetRules.NODE - StreetRules.NODE;
					hi = (vertical ? tile.maxZ() : tile.maxX()) + StreetRules.NODE;
					// the run is followed GRID_REACH beyond the tile, so it starts and ends where it does for every tile on the line (its id
					// and its profile are then the same on both sides of a border)
					lo -= GRID_REACH;
					hi += GRID_REACH;
					DistrictGrid.Segment s = new DistrictGrid.Segment(axis, 0, 0, pos, lo, hi);
					for (List<Pt> run : nodeRuns(lo, hi, (a, b) -> rules.gridSideEdgeOk(memo, s.axis(), pos, a, b) && !nearAbsentBorder(Arterials.ax(s, a), Arterials.az(s, a), 0) && !nearAbsentBorder(Arterials.ax(s, b), Arterials.az(s, b), 0), s)) {
						addPieces("s" + (vertical ? "x" : "z") + Ids.n(pos) + "_" + Ids.n(axisCoord(s, run.get(0))), RoadKind.STREET, StreetRules.GRID_WIDTH, StreetCols.columns(run), false, JunctionField.lineKey(s.axis(), pos));
					}
				}
			}
		}

		// ---- grading and connectivity of the base streets

		boolean touchesBorder(Raw r) {
			for (int[] c : r.cols) if (c[0] <= tile.minX() || c[0] >= tile.maxX() - 1 || c[1] <= tile.minZ() || c[1] >= tile.maxZ() - 1) return true;
			return false;
		}

		/**
		 * Grades a street (the profile the scene will compute from the same columns); false when it needs more cut or fill than allowed,
		 * or (JUNCTION) when it cannot meet the streets it touches within the grade limit. A whole street keeps its slice of the whole
		 * profile; any other street (a subdivision street, lane, link or port) is graded after the streets it meets and takes their
		 * level where its footprint overlaps theirs ({@link #meetPins}), so it joins them flat or within one block.
		 */
		boolean grade(Raw r) {
			int n = r.cols.size();
			int[] y;
			boolean ok = true;
			if (r.wholeY != null) {
				y = r.wholeY;
				for (int i = 0; i < n; i++) ok &= !r.steep[i];
			} else {
				TreeMap<Integer, Integer> pins = meetPins(r);
				Grading.RoadGrade g = Grading.profile(lattice(StreetCols.vertices(r.cols)), memo, pins);
				if (g.profile().size() != n) throw new IllegalStateException("street " + r.id + ": profile has " + g.profile().size() + " columns for " + n);
				y = g.profile().y();
				// the drop rule: a street that cannot meet the streets it touches (too steep between them, two of them too far apart at one
				// column, or a bridge deck it must keep lifting it off one) is not drawn
				r.unmet = pins != null && (meetConflict || !meetBad.isEmpty() || !SettlementChecks.roadGrade(r.id, g.profile().x(), g.profile().z(), y).isEmpty());
				if (pins != null) for (var e : pins.entrySet()) r.unmet |= y[e.getKey()] != e.getValue();
				ok = g.ok() && !r.unmet;
			}
			r.y = y;
			r.street = street(r, y);
			return ok;
		}

		/** The last {@link #meetPins} found a column that touches two streets more than two blocks apart. */
		boolean meetConflict;

		/** The columns the last {@link #meetPins} found no level for: they touch one street whose cells there span more than two blocks. */
		TreeSet<Integer> meetBad = new TreeSet<>();

		/**
		 * A street another may meet, over a box: the level it has on a cell ({@code {lo, hi, end}}: end 1 when the cell belongs to the
		 * street's end face), or null where it does not cover the cell.
		 */
		interface Layer { int[] at(int x, int z); }

		/**
		 * A street's cells over the box {@code [x0, x1] x [z0, z1]}: the first of its columns whose footprint covers a cell gives the cell's
		 * level, as the grader builds it. Null when the street misses the box.
		 */
		static Layer cells(List<int[]> cols, int width, int[] y, int x0, int z0, int x1, int z1, boolean headEnd, boolean tailEnd) {
			int w = x1 - x0 + 1, d = z1 - z0 + 1, sh = width / 2, n = cols.size();
			int[] lvl = new int[w * d];
			boolean[] set = new boolean[w * d], end = new boolean[w * d];
			boolean any = false;
			for (int i = 0; i < cols.size(); i++) {
				int[] c = cols.get(i);
				if (c[0] - sh > x1 || c[0] - sh + width - 1 < x0 || c[1] - sh > z1 || c[1] - sh + width - 1 < z0) continue;
				for (int dz = 0; dz < width; dz++) {
					int z = c[1] - sh + dz;
					if (z < z0 || z > z1) continue;
					for (int dx = 0; dx < width; dx++) {
						int x = c[0] - sh + dx;
						if (x < x0 || x > x1 || set[(z - z0) * w + (x - x0)]) continue;
						set[(z - z0) * w + (x - x0)] = true;
						lvl[(z - z0) * w + (x - x0)] = y[i];
						end[(z - z0) * w + (x - x0)] = (headEnd && i <= sh) || (tailEnd && i >= n - 1 - sh);
						any = true;
					}
				}
			}
			if (!any) return null;
			return (x, z) -> {
				if (x < x0 || x > x1 || z < z0 || z > z1 || !set[(z - z0) * w + (x - x0)]) return null;
				int v = lvl[(z - z0) * w + (x - x0)];
				return new int[] {v, v, end[(z - z0) * w + (x - x0)] ? 1 : 0};
			};
		}

		/** The box a street's footprint covers: {x0, z0, x1, z1}. */
		static int[] footprintBox(List<int[]> cols, int width) {
			int h = width / 2, x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
			for (int[] c : cols) { x0 = Math.min(x0, c[0] - h); z0 = Math.min(z0, c[1] - h); x1 = Math.max(x1, c[0] - h + width - 1); z1 = Math.max(z1, c[1] - h + width - 1); }
			return new int[] {x0, z0, x1, z1};
		}

		/**
		 * The levels a street must take where its footprint overlaps the graded streets of the tile (JUNCTION), and the settlement's
		 * quays (graded ahead, {@link #quayWholes}, so a street laid before the quays still meets them): see {@link #meetPins(List, int, List, boolean, boolean)}.
		 */
		TreeMap<Integer, Integer> meetPins(Raw r) {
			int[] b = footprintBox(r.cols, r.width);
			b = new int[] {b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1}; // and the cells beside it, where a street ending against it touches it
			List<Layer> layers = new ArrayList<>();
			for (Raw s : raws) {
				if (s == r || s.street == null || s.y == null) continue;
				Layer l = cells(s.cols, s.width, s.y, b[0], b[1], b[2], b[3], !windowEdge(s.cols.get(0)), !windowEdge(s.cols.get(s.cols.size() - 1)));
				if (l != null) layers.add(l);
			}
			for (QuayWhole q : quayWholes()) {
				Layer l = cells(q.cols(), Quays.WIDTH, q.y(), b[0], b[1], b[2], b[3], true, true);
				if (l != null) layers.add(l);
			}
			return meetPins(r.cols, r.width, layers, !windowEdge(r.cols.get(0)), !windowEdge(r.cols.get(r.cols.size() - 1)));
		}

		/** The column lies on the edge of the window the tile keeps its streets in (a piece cut there goes on in the neighbouring tile). */
		boolean windowEdge(int[] c) {
			return c[0] <= tile.minX() - MARGIN || c[0] >= tile.maxX() + MARGIN || c[1] <= tile.minZ() - MARGIN || c[1] >= tile.maxZ() + MARGIN;
		}

		/**
		 * Column index to level for every column of a street whose footprint shares a cell with one of the layers (the streets it meets),
		 * null when none does. For each street it touches, a run of touching columns gets one level within one block of every level that
		 * street has on those cells (the middle of their range), so the street meets it flat; when no single level fits (a street sloping
		 * across the whole overlap), each column gets the middle of its own cells. A column that touches two streets takes a level within a
		 * block of both, or sets {@link #meetConflict} when there is none.
		 */
		TreeMap<Integer, Integer> meetPins(List<int[]> cols, int width, List<Layer> layers, boolean headEnd, boolean tailEnd) {
			int h = width / 2, n = cols.size();
			meetConflict = false;
			meetBad = new TreeSet<>();
			TreeMap<Integer, int[]> acc = null; // column -> {sum of the streets' levels, count, lowest and highest cell level} over the streets it touches
			for (Layer l : layers) {
				int[] cLo = new int[n], cHi = new int[n];
				boolean[] touch = new boolean[n];
				for (int i = 0; i < n; i++) {
					int[] c = cols.get(i);
					cLo[i] = Integer.MAX_VALUE;
					cHi[i] = Integer.MIN_VALUE;
					for (int dz = 0; dz < width; dz++) {
						for (int dx = 0; dx < width; dx++) {
							int[] v = l.at(c[0] - h + dx, c[1] - h + dz);
							if (v == null) continue;
							touch[i] = true;
							cLo[i] = Math.min(cLo[i], v[0]);
							cHi[i] = Math.max(cHi[i], v[1]);
						}
					}
					// the cells beside the footprint, across one cell edge: a street that ends there (this one at its end column, or the
					// other at its end face) meets the other street as if they overlapped
					boolean mineEnd = (headEnd && i <= h) || (tailEnd && i >= n - 1 - h);
					for (int k = 0; k < width; k++) {
						int[][] ring = {{c[0] - h - 1, c[1] - h + k}, {c[0] - h + width, c[1] - h + k}, {c[0] - h + k, c[1] - h - 1}, {c[0] - h + k, c[1] - h + width}};
						for (int[] q : ring) {
							int[] v = l.at(q[0], q[1]);
							if (v == null || !(mineEnd || v[2] == 1)) continue;
							touch[i] = true;
							cLo[i] = Math.min(cLo[i], v[0]);
							cHi[i] = Math.max(cHi[i], v[1]);
						}
					}
				}
				TreeMap<Integer, Integer> p = runPins(touch, cLo, cHi, meetBad);
				if (p == null) continue;
				if (acc == null) acc = new TreeMap<>();
				for (var e : p.entrySet()) {
					int[] a = acc.computeIfAbsent(e.getKey(), k -> new int[] {0, 0, Integer.MAX_VALUE, Integer.MIN_VALUE});
					a[0] += e.getValue();
					a[1]++;
					a[2] = Math.min(a[2], cLo[e.getKey()]);
					a[3] = Math.max(a[3], cHi[e.getKey()]);
				}
			}
			if (acc == null) return null;
			TreeMap<Integer, Integer> pins = new TreeMap<>();
			for (var e : acc.entrySet()) {
				int[] a = e.getValue();
				int v = Math.floorDiv(a[0], a[1]);
				if (a[1] > 1) {
					if (a[3] - a[2] > 2) meetConflict = true; // no level is within a block of every street this column touches
					else v = Math.max(a[3] - 1, Math.min(a[2] + 1, v));
				}
				pins.put(e.getKey(), v);
			}
			return pins;
		}

		/**
		 * One level per run of touching columns within one block of every level on its cells when one fits, else each column's middle.
		 * Two runs on one street whose levels differ by more than the grade allows over the gap between them (a street crossing the
		 * chord of another's bend) count as one run.
		 */
		static TreeMap<Integer, Integer> runPins(boolean[] touch, int[] cLo, int[] cHi, java.util.Set<Integer> bad) {
			int n = touch.length;
			List<int[]> runs = new ArrayList<>(); // {first, last, lowest, highest}
			for (int i = 0; i < n; ) {
				if (!touch[i]) { i++; continue; }
				int j = i, rLo = Integer.MAX_VALUE, rHi = Integer.MIN_VALUE;
				while (j < n && touch[j]) { rLo = Math.min(rLo, cLo[j]); rHi = Math.max(rHi, cHi[j]); j++; }
				int[] r = {i, j - 1, rLo, rHi};
				int[] prev = runs.isEmpty() ? null : runs.get(runs.size() - 1);
				if (prev != null && Math.abs(Math.floorDiv(r[2] + r[3], 2) - Math.floorDiv(prev[2] + prev[3], 2)) > JunctionField.rise(prev[1], r[0])) {
					prev[1] = r[1];
					prev[2] = Math.min(prev[2], r[2]);
					prev[3] = Math.max(prev[3], r[3]);
				} else runs.add(r);
				i = j;
			}
			if (runs.isEmpty()) return null;
			TreeMap<Integer, Integer> pins = new TreeMap<>();
			for (int[] r : runs) {
				for (int k = r[0]; k <= r[1]; k++) {
					if (!touch[k]) continue;
					pins.put(k, r[3] - r[2] <= 2 ? Math.floorDiv(r[2] + r[3], 2) : Math.floorDiv(cLo[k] + cHi[k], 2));
					if (r[3] - r[2] > 2 && cHi[k] - cLo[k] > 2 && bad != null) bad.add(k); // no level is within a block of every cell of this one street
				}
			}
			return pins;
		}

		/**
		 * The street as stored: its vertices, both ends, and both columns of every change of height, with their heights. Between two
		 * stored points the height is then constant or steps once at the second, so the floor interpolation the scene and the brief
		 * filler use ({@code Grading.road(points, ys, ...)}, {@code Briefs.nearest}) gives back every column's height exactly.
		 */
		static Street street(Raw r, int[] y) {
			int n = r.cols.size();
			TreeSet<Integer> idx = new TreeSet<>(StreetCols.vertexIndices(r.cols));
			for (int i = 1; i < n; i++) if (y[i] != y[i - 1]) { idx.add(i - 1); idx.add(i); }
			List<Pt> pts = new ArrayList<>();
			int[] ys = new int[idx.size()];
			int k = 0;
			for (int i : idx) {
				pts.add(new Pt(r.cols.get(i)[0], r.cols.get(i)[1]));
				ys[k++] = y[i];
			}
			return new Street(r.id, r.kind, r.width, pts, ys);
		}

		/**
		 * Drops the streets that reach no road (ROADS2): components of the tile's streets (footprints that overlap or touch) that hold
		 * no root, where a root is a skeleton road or a border street of the lattice with a live column. Lanes, quays and grid
		 * runs are the only things dropped; skeleton roads and lattice streets never are. Pieces cut off by exact terrain (a grid run
		 * between two steep edges) or whose link lane failed (a quay with no street in reach) are not left floating. Neighbouring
		 * tiles agree: a piece that reaches the border does so on a lattice street (rooted in both tiles), and the slivers a tile sees of
		 * its neighbour's pieces within its margin carry no column near the border.
		 */
		/** The plaza is the hub square: the hub column lies within {@value #HUB_SQUARE_REACH} blocks of it (the skeleton joins that one itself). */
		boolean hubSquare(Plaza pz) {
			Pt h = src.network().hub();
			if (h == null) return false;
			int dx = Math.max(Math.max(pz.x() - h.x(), h.x() - (pz.x() + pz.w() - 1)), 0), dz = Math.max(Math.max(pz.z() - h.z(), h.z() - (pz.z() + pz.d() - 1)), 0);
			return dx <= HUB_SQUARE_REACH && dz <= HUB_SQUARE_REACH;
		}

		static final int HUB_SQUARE_REACH = 24;

		/** Squares and greens that touch no rooted street are dropped, with their landmarks: nothing could reach them. */
		void dropFloatingPlazas(short[] tmp, int[] parent, boolean[] rooted) {
			List<Plaza> keep = new ArrayList<>();
			for (Plaza pz : plazas) {
				boolean ok = false;
				for (int z = pz.z() - 1; z <= pz.z() + pz.d() && !ok; z++) {
					for (int x = pz.x() - 1; x <= pz.x() + pz.w() && !ok; x++) {
						// 4-adjacency, as the city's roads-attached rule floods: a street that meets the plaza only at a corner does not reach it (CITYQ)
						if ((x == pz.x() - 1 || x == pz.x() + pz.w()) && (z == pz.z() - 1 || z == pz.z() + pz.d())) continue;
						if (!inR(x, z)) continue;
						int a = tmp[(z - rz0) * rw + (x - rx0)];
						if (a != 0 && rooted[find(parent, a - 1)]) ok = true;
					}
				}
				if (ok || hubSquare(pz)) { keep.add(pz); continue; }
				Rect r = new Rect(pz.x(), pz.z(), pz.w(), pz.d());
				notes.add("plaza " + pz.kind() + " at " + pz.x() + "," + pz.z() + " dropped (reaches no road)");
				squares.remove(r);
				reserved.remove(r);
				moved.remove(r);
				landmarks.removeIf(l -> l.x() == r.x() && l.z() == r.z() && l.w() == r.w() && l.d() == r.d() && open(l.kind()));
				clearRect(r, PLAZA | RESERVED);
			}
			if (keep.size() == plazas.size()) return;
			plazas.clear();
			for (int i = 0; i < keep.size(); i++) {
				Plaza q = keep.get(i);
				plazas.add(new Plaza("p" + i, q.kind(), q.x(), q.z(), q.w(), q.d(), q.y()));
			}
		}

		void dropUnrooted(boolean plazasToo) {
			boolean latt = rules.lattice();
			if (!latt && !plazasToo) return;
			RoadNetwork net = src.network();
			int n = raws.size();
			int[] parent = new int[n];
			for (int i = 0; i < n; i++) parent[i] = i;
			short[] tmp = new short[rw * rd];
			for (int i = 0; i < n; i++) {
				Raw r = raws.get(i);
				int h = r.width / 2;
				for (int[] c : r.cols) {
					for (int dz = 0; dz < r.width; dz++) {
						for (int dx = 0; dx < r.width; dx++) {
							int x = c[0] - h + dx, z = c[1] - h + dz;
							if (!inR(x, z)) continue;
							int p = (z - rz0) * rw + (x - rx0);
							if (tmp[p] == 0) tmp[p] = (short) (i + 1);
							else union(parent, tmp[p] - 1, i);
						}
					}
				}
			}
			for (int z = 0; z < rd; z++) {
				for (int x = 0; x < rw; x++) {
					int a = tmp[z * rw + x];
					if (a == 0) continue;
					if (x + 1 < rw && tmp[z * rw + x + 1] != 0) union(parent, a - 1, tmp[z * rw + x + 1] - 1);
					if (z + 1 < rd && tmp[(z + 1) * rw + x] != 0) union(parent, a - 1, tmp[(z + 1) * rw + x] - 1);
				}
			}
			boolean[] rooted = new boolean[n];
			for (int i = 0; i < n; i++) {
				Raw r = raws.get(i);
				if (r.kind == RoadKind.MAIN || r.kind == RoadKind.PATH) rooted[find(parent, i)] = true;
				else if (r.macro()) for (int[] c : r.cols) if (net.live(c[0], c[1])) { rooted[find(parent, i)] = true; break; }
			}
			if (plazasToo) dropFloatingPlazas(tmp, parent, rooted);
			List<Raw> kept = new ArrayList<>();
			int dropped = 0;
			StringBuilder ids = new StringBuilder();
			for (int i = 0; i < n; i++) {
				if (!latt || rooted[find(parent, i)] || raws.get(i).macro() || raws.get(i).kind == RoadKind.MAIN) kept.add(raws.get(i));
				else { dropped++; ids.append(ids.length() == 0 ? "" : ", ").append(raws.get(i).id); }
			}
			if (dropped == 0) return;
			notes.add(dropped + " street pieces dropped (reach no road): " + ids);
			raws.clear();
			raws.addAll(kept);
			for (int i = 0; i < flags.length; i++) flags[i] &= (byte) ~STREET;
			ownerList.clear();
			java.util.Arrays.fill(owner, (short) 0);
			for (Raw r : raws) paint(r);
		}

		/** Grades the base streets (dropping grid streets that are too steep and do not touch the border) and keeps connected ones. */
		void finishBase() {
			for (Raw r : raws) {
				boolean ok = grade(r);
				if (!ok && r.fixed) notes.add("street " + r.id + " needs more than " + Grading.MAX_CUT_FILL + " blocks of cut or fill on a steep stretch");
				if (!ok && !r.fixed && !touchesBorder(r)) r.street = null;
			}
			raws.removeIf(r -> r.street == null);
			// components by footprint overlap and adjacency
			int n = raws.size();
			int[] parent = new int[n];
			for (int i = 0; i < n; i++) parent[i] = i;
			short[] tmp = new short[rw * rd];
			for (int i = 0; i < n; i++) {
				Raw r = raws.get(i);
				int h = r.width / 2;
				for (int[] c : r.cols) {
					for (int dz = 0; dz < r.width; dz++) {
						for (int dx = 0; dx < r.width; dx++) {
							int x = c[0] - h + dx, z = c[1] - h + dz;
							if (!inR(x, z)) continue;
							int p = (z - rz0) * rw + (x - rx0);
							if (tmp[p] == 0) tmp[p] = (short) (i + 1);
							else union(parent, tmp[p] - 1, i);
						}
					}
				}
			}
			for (int z = 0; z < rd; z++) {
				for (int x = 0; x < rw; x++) {
					int a = tmp[z * rw + x];
					if (a == 0) continue;
					if (x + 1 < rw && tmp[z * rw + x + 1] != 0) union(parent, a - 1, tmp[z * rw + x + 1] - 1);
					if (z + 1 < rd && tmp[(z + 1) * rw + x] != 0) union(parent, a - 1, tmp[(z + 1) * rw + x] - 1);
				}
			}
			boolean[] good = new boolean[n];
			boolean latt = rules.lattice();
			RoadNetwork net = src.network();
			for (int i = 0; i < n; i++) {
				Raw r = raws.get(i);
				if (!latt) { if (r.skeletonish() || touchesBorder(r)) good[find(parent, i)] = true; continue; }
				if (r.kind == RoadKind.MAIN || r.kind == RoadKind.PATH) good[find(parent, i)] = true; // skeleton roads are never dropped
				else if (r.macro()) for (int[] c : r.cols) if (net.live(c[0], c[1])) { good[find(parent, i)] = true; break; }
			}
			List<Raw> kept = new ArrayList<>();
			int dropped = 0;
			for (int i = 0; i < n; i++) {
				if (good[find(parent, i)]) kept.add(raws.get(i));
				else dropped++;
			}
			if (dropped > 0) notes.add(dropped + " street pieces dropped (cut off from the roads and the tile border)");
			raws.clear();
			raws.addAll(kept);
			ownerList.clear();
			for (Raw r : raws) paint(r);
		}

		static int find(int[] p, int a) {
			while (p[a] != a) { p[a] = p[p[a]]; a = p[a]; }
			return a;
		}

		static void union(int[] p, int a, int b) {
			a = find(p, a);
			b = find(p, b);
			if (a != b) p[Math.max(a, b)] = Math.min(a, b);
		}

		// ---- old-town subdivision (IRR1; the splitter itself is Subdivision)

		/** Distance the subdivided area keeps from the tile border (streets there are the neighbour's business; ports carry lanes across). */
		static final int SUB_INSET = 14;
		/** Keep subdivision streets this far from a square, so the hall and temple lots flush against it still fit. */
		static final int SUB_SQUARE_ROOM = 22;

		List<Rect> subLeaves = List.of();
		int subCount, subLink;

		/** A skeleton road or arterial footprint covers the column. */
		boolean skel(int x, int z) { return (fg(x, z) & SKEL) != 0; }

		boolean nearSquare(int x, int z) {
			for (Rect s : squares) if (x >= s.x() - SUB_SQUARE_ROOM && x < s.x() + s.w() + SUB_SQUARE_ROOM && z >= s.z() - SUB_SQUARE_ROOM && z < s.z() + s.d() + SUB_SQUARE_ROOM) return true;
			return false;
		}

		/**
		 * Cuts the tile's urban rectangle (inset from the border) into blocks with {@link Subdivision}, keyed by (seed, tile) only, so
		 * tile order does not matter. Each street run is kept only where it is old-town land, dry, clear of squares and gradeable, and
		 * only if it joins a street that is already there (else a lane is routed to the nearest one).
		 */
		void subdivide() {
			Rect inner = new Rect(tile.minX() + SUB_INSET, tile.minZ() + SUB_INSET, tile.widthX() - 2 * SUB_INSET, tile.widthZ() - 2 * SUB_INSET);
			int hits = 0;
			for (int z = inner.z(); z < inner.z() + inner.d(); z += 16) for (int x = inner.x(); x < inner.x() + inner.w(); x += 16) if (rules.subdivideAt(x, z)) hits++;
			if (hits < 6) return;
			Subdivision sd = Subdivision.of(inner, Hash64.hash(seed, "subdivide", ix, iz), this::skel);
			subLeaves = sd.leaves;
			for (Subdivision.Line l : sd.lines) addSubStreet(l.constX(), l.c(), l.from(), l.to(), l.width(), l.level());
			if (subCount > 0) notes.add("old-town subdivision: " + subCount + " streets, " + subLeaves.size() + " blocks");
			smallSquare();
		}

		/**
		 * How far past an end of a subdivision street (on the line of constant {@code c}, end at {@code at}, outward {@code sgn}) the
		 * street must run to reach a street that is there already (an arterial or border street on the tile border): 0 when its end is
		 * on one, else the distance to the first one within the inset and a little more; 0 again when none (the end is then a dead end).
		 */
		int reachOut(boolean lineOfConstantX, int c, int at, int sgn, int width) {
			int h = width / 2;
			for (int k = 0; k <= SUB_INSET + 4; k++) {
				int p = at + sgn * k, x = lineOfConstantX ? c : p, z = lineOfConstantX ? p : c;
				if ((fg(x, z) & (RESERVED | PLAZA | WET)) != 0) return 0;
				// a footprint cell in the 3 columns beside the tile border that is no street cell would be missing in the neighbour tile (tile-border-agree)
				for (int dz = 0; dz < width; dz++) for (int dx = 0; dx < width; dx++) {
					int fx = x - h + dx, fz = z - h + dz;
					if (inBorderBand(fx, fz) && (fg(fx, fz) & STREET) == 0) return 0;
				}
				if ((fg(x, z) & STREET) != 0) return k;
			}
			return 0;
		}

		/** The column lies in the 3 columns each side of a tile border that {@code tile-border-agree} compares. */
		boolean inBorderBand(int x, int z) { return x < tile.minX() + 3 || x >= tile.maxX() - 3 || z < tile.minZ() + 3 || z >= tile.maxZ() - 3; }

		/** One street of the subdivision: the line of constant {@code c} from {@code from} to {@code to} on the other axis, kept in its good runs. */
		void addSubStreet(boolean lineOfConstantX, int c, int from, int to, int width, int level) {
			from -= reachOut(lineOfConstantX, c, from, -1, width);
			to += reachOut(lineOfConstantX, c, to, 1, width);
			List<Pt> pts = lineOfConstantX ? List.of(new Pt(c, from), new Pt(c, to)) : List.of(new Pt(from, c), new Pt(to, c));
			for (List<int[]> run : StreetCols.runs(StreetCols.columns(pts), col -> keep(col) && !hitsReserved(col, width) && rules.subdivideAt(col[0], col[1]) && clearOfWater(col[0], col[1]) && !nearSquare(col[0], col[1]))) {
				Raw r = new Raw("sd" + subCount, level == 0 ? RoadKind.STREET : RoadKind.LANE, width, run, false);
				if (!grade(r)) continue; // too steep: this run is dropped (a child that then joins nothing is dropped too)
				if (!joinsStreet(r)) {
					List<Raw> base = new ArrayList<>(raws);
					raws.add(r); // so the link meets the street it starts from at the street's level (JUNCTION)
					Raw link = quayLink("sdk" + subLink, run, base);
					raws.remove(raws.size() - 1);
					if (link == null) continue;
					subLink++;
					raws.add(link);
					paint(link);
				}
				subCount++;
				raws.add(r);
				paint(r);
			}
		}

		/** Some column of the street's footprint lies on a street already there (so the street is joined to the network). */
		boolean joinsStreet(Raw r) {
			int h = r.width / 2;
			for (int[] c : r.cols) {
				for (int dz = 0; dz < r.width; dz++) for (int dx = 0; dx < r.width; dx++) if ((fg(c[0] - h + dx, c[1] - h + dz) & STREET) != 0) return true;
			}
			return false;
		}

		/** Half the subdivided tiles get a small square (16 to 24 across) in the corner of a block, flush with the streets round it. */
		void smallSquare() {
			if (subLeaves.isEmpty() || Hash64.below(Hash64.hash(seed, "sub-square", ix, iz), 100) >= 50) return;
			long h = Hash64.hash(seed, "sub-square-at", ix, iz);
			int n = subLeaves.size(), first = Hash64.below(h, n);
			for (int t = 0; t < n; t++) {
				Rect b = subLeaves.get((first + t) % n);
				int s = 16 + 4 * Hash64.below(Hash64.hash(h, "size", t), 3);
				if (b.w() < s + 12 || b.d() < s + 12) continue;
				int corner = Hash64.below(Hash64.hash(h, "corner", t), 4);
				for (int k = 0; k < 4; k++) {
					int q = (corner + k) % 4;
					Rect sq = new Rect((q & 1) == 0 ? b.x() : b.x() + b.w() - s, (q & 2) == 0 ? b.z() : b.z() + b.d() - s, s, s);
					if (!rectFree(sq, false) || !dry(sq) || streetsAround(sq) < 2 * s) continue;
					Optional<Grading.PlazaGrade> g = Grading.plaza(sq, exact);
					if (g.isEmpty()) continue;
					boolean green = Hash64.below(Hash64.hash(h, "green", t), 3) == 0;
					plazas.add(new Plaza("p" + plazas.size(), green ? "green" : "square", sq.x(), sq.z(), sq.w(), sq.d(), g.get().level()));
					squares.add(sq);
					reserved.add(sq);
					paintRect(sq, PLAZA | RESERVED);
					notes.add("old-town small " + (green ? "green" : "square") + " " + sq.w() + "x" + sq.d() + " at " + sq.x() + "," + sq.z());
					return;
				}
			}
		}

		/** Street columns on the ring just outside the rectangle (up to 4 sides). */
		int streetsAround(Rect r) {
			int n = 0;
			for (int x = r.x(); x < r.x() + r.w(); x++) { if ((fg(x, r.z() - 1) & STREET) != 0) n++; if ((fg(x, r.z() + r.d()) & STREET) != 0) n++; }
			for (int z = r.z(); z < r.z() + r.d(); z++) { if ((fg(r.x() - 1, z) & STREET) != 0) n++; if ((fg(r.x() + r.w(), z) & STREET) != 0) n++; }
			return n;
		}

		// ---- quays (TR 5.3)

		/** A wet 4-lattice point within {@link Quays#OFFSET} blocks of (x, z). */
		boolean nearWater(int x, int z) {
			int r = Quays.OFFSET;
			for (int pz = Math.floorDiv(z - r, 4) * 4; pz <= z + r; pz += 4) {
				for (int px = Math.floorDiv(x - r, 4) * 4; px <= x + r; px += 4) {
					long dx = px - x, dz = pz - z;
					if (dx * dx + dz * dz <= (long) r * r && wt(px, pz)) return true;
				}
			}
			return false;
		}

		/** Lowest water surface of the wet 4-lattice points within 12 blocks of (x, z), or MAX_VALUE when none. */
		int waterLevel(int x, int z) {
			int best = Integer.MAX_VALUE;
			for (int pz = Math.floorDiv(z - 12, 4) * 4; pz <= z + 12; pz += 4) {
				for (int px = Math.floorDiv(x - 12, 4) * 4; px <= x + 12; px += 4) if (wt(px, pz)) best = Math.min(best, exact.surfaceY(px, pz));
			}
			return best;
		}

		/** Some urban column of the tile lies in the waterfront quarter (a cheap screen before any exact probe). */
		boolean hasWaterfront() { return hasWaterfront(tile); }

		/** Some urban column of the given tile lies in the waterfront quarter (pointwise in the tile: the same answer in every tile that asks). */
		boolean hasWaterfront(DistrictGrid.Tile t) {
			for (int z = Math.floorDiv(t.minZ(), 32) * 32; z <= t.maxZ() + 32; z += 32) {
				for (int x = Math.floorDiv(t.minX(), 32) * 32; x <= t.maxX() + 32; x += 32) {
					if (den.urban(x, z) && qf.at(x, z, den.at(x, z)) == QuarterKind.WATERFRONT) return true;
				}
			}
			return false;
		}

		/** No wet 4-lattice point within 3 blocks of (x, z) (what the WET flag marks, but defined for any column, not only the raster). */
		boolean clearOfWater(int x, int z) {
			for (int pz = Math.floorDiv(z - 3, 4) * 4; pz <= z + 3; pz += 4) for (int px = Math.floorDiv(x - 3, 4) * 4; px <= x + 3; px += 4) if (wt(px, pz)) return false;
			return true;
		}

		/** A quay column: dry, clear of the water, in the waterfront quarter and on a low bank (pointwise: the same answer in every tile). */
		boolean quayOk(int[] c) {
			if (wt(c[0], c[1]) || !clearOfWater(c[0], c[1]) || nearAbsentBorder(c[0], c[1], 5)) return false;
			if (!den.urban(c[0], c[1])) return false;
			int dens = den.at(c[0], c[1]);
			if (dens < QuarterField.OUTSKIRTS_DENSITY || qf.at(c[0], c[1], dens) != QuarterKind.WATERFRONT) return false; // outskirts keep their bank
			int rise = fl(c[0], c[1]) - waterLevel(c[0], c[1]);
			if (rise < 0 || rise > Quays.MAX_RISE) return false;
			return !junctions().splits(c[0], c[1], Quays.WIDTH); // squeezed between two streets at different heights it could meet neither (JUNCTION)
		}

		/**
		 * Promenades along the water of the waterfront quarter: the marching-squares contour of "water within 8 blocks" on the street
		 * lattice, clipped to the dry columns on a low bank, each piece joined to the nearest road or street by a lane (so lots on it
		 * can be reached). Pointwise, so neighbouring tiles list the same promenade near their border.
		 */
		void quays() {
			if (quayWholes().isEmpty()) return;
			List<Raw> base = new ArrayList<>(raws);
			int cn = 0;
			List<Raw> qs = new ArrayList<>();
			for (QuayWhole qw : quayWholes()) {
				for (Raw piece : pieces(qw.cols(), Quays.WIDTH, RoadKind.QUAY, false, null, qw.y(), qw.steep())) {
					List<int[]> run = piece.cols;
					Raw q = new Raw("quay" + Ids.n(run.get(0)[0]) + "_" + Ids.n(run.get(0)[1]), RoadKind.QUAY, Quays.WIDTH, run, false).withWhole(piece);
					grade(q); // a steep stretch is the road's to bear (a promenade is never dropped: both tiles must list it)
					raws.add(q);
					paint(q);
					qs.add(q);
				}
			}
			// the links after every quay, so a link that crosses another quay meets it at its level (JUNCTION)
			for (Raw q : qs) {
				Raw link = quayLink("k" + cn, q.cols, base);
				if (link != null) { cn++; raws.add(link); paint(link); }
			}
		}

		/** A quay run as every tile that sees it draws it, graded once: its columns, heights and the columns over the cut and fill limit. */
		record QuayWhole(List<int[]> cols, int[] y, boolean[] steep) {}

		List<QuayWhole> quayWholes;

		/**
		 * The settlement's quay runs that come near the tile (their footprint within the reach of the window quays were once traced in): see
		 * {@link #settlementQuays}. Ahead of the tile's other streets, so a subdivision street or lane laid before the quays still meets
		 * them at their level.
		 */
		List<QuayWhole> quayWholes() {
			if (quayWholes != null) return quayWholes;
			if (!hasWaterfront()) return quayWholes = List.of();
			int m = Quays.MIN_LEN + MARGIN + 2 * Quays.STEP; // the reach of the window the runs were once traced in
			List<QuayWhole> near = new ArrayList<>();
			for (QuayWhole q : src.shared("quays", exact, this::settlementQuays)) {
				int[] b = footprintBox(q.cols(), Quays.WIDTH);
				if (b[2] >= tile.minX() - m && b[0] <= tile.maxX() + m && b[3] >= tile.minZ() - m && b[1] <= tile.maxZ() + m) near.add(q);
			}
			return quayWholes = List.copyOf(near);
		}

		/**
		 * Every promenade run of the settlement, each graded once (JUNCTION), in the canonical order: a pure function of the source and the
		 * probe, so every tile that sees a run grades it, and cuts it where it cannot meet a street, alike. (Traced and graded through each
		 * tile's own window, a run was clipped where the window ended; its profile, and so where {@link #quayParts} cut it, then depended on
		 * the window: METROFILL R1, {@code tile-border-agree} where a quay crossed a border street.)
		 *
		 * <p>Where a run overlaps a whole street it takes the street's level from the junction field (not from a tile's pieces), and where
		 * it overlaps another quay it takes the level of the one whose smallest column comes first. The contour is traced over the tiles the
		 * settlement plans that screen as waterfront ({@link #hasWaterfront(DistrictGrid.Tile)}), and {@value #PAD} blocks round them, and a
		 * quay column must lie in such a tile, so every quay column is inside the traced area by more than a contour cell, where the contour
		 * is the unclipped one. (A run that leaves those tiles ends at their border: no tile beyond would draw it.) Any tile may build it,
		 * through its own terrain memo: the values are the probe's, so the result does not depend on which tile asked.
		 */
		List<QuayWhole> settlementQuays() {
			int reach = den.maxRadius() + 2 * PAD, cx = src.site().centre().x(), cz = src.site().centre().z();
			DistrictGrid.TileId lo = grid.tileOf(cx - reach, cz - reach), hi = grid.tileOf(cx + reach, cz + reach);
			TreeSet<Long> wf = new TreeSet<>();
			int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
			for (int iz = lo.iz(); iz <= hi.iz(); iz++) {
				for (int ix = lo.ix(); ix <= hi.ix(); ix++) {
					DistrictGrid.Tile t = grid.tile(ix, iz);
					if (!hasWaterfront(t) || !owns(src, ix, iz)) continue;
					wf.add(key(ix, iz));
					x0 = Math.min(x0, t.minX() - PAD); z0 = Math.min(z0, t.minZ() - PAD); x1 = Math.max(x1, t.maxX() + PAD); z1 = Math.max(z1, t.maxZ() + PAD);
				}
			}
			if (wf.isEmpty()) return List.of();
			TreeMap<Long, Boolean> nearMemo = new TreeMap<>(); // the contour asks each lattice node up to four times
			Quays.Near traced = (x, z) -> {
				DistrictGrid.TileId c = grid.tileOf(x, z);
				boolean in = false;
				for (int dz = -1; dz <= 1 && !in; dz++) {
					for (int dx = -1; dx <= 1 && !in; dx++) {
						if (!wf.contains(key(c.ix() + dx, c.iz() + dz))) continue;
						DistrictGrid.Tile t = grid.tile(c.ix() + dx, c.iz() + dz);
						in = x >= t.minX() - PAD && x <= t.maxX() + PAD && z >= t.minZ() - PAD && z <= t.maxZ() + PAD;
					}
				}
				return in && nearMemo.computeIfAbsent(key(x, z), k -> nearWater(x, z));
			};
			java.util.function.Predicate<int[]> ok = c -> {
				DistrictGrid.TileId t = grid.tileOf(c[0], c[1]);
				return wf.contains(key(t.ix(), t.iz())) && quayOk(c);
			};
			List<List<int[]>> runs = new ArrayList<>();
			for (List<Pt> chain : Quays.contour(traced, x0, z0, x1, z1)) {
				List<int[]> cols = StreetCols.columns(chain);
				if (chain.get(0).equals(chain.get(chain.size() - 1))) cols = breakRing(cols, ok);
				for (List<int[]> whole : StreetCols.runs(cols, ok)) if (whole.size() >= Quays.MIN_LEN) runs.add(whole);
			}
			JunctionField jf = junctions();
			List<QuayWhole> out = new ArrayList<>();
			for (List<int[]> whole : canonicalQuays(runs)) {
				int[] b = footprintBox(whole, Quays.WIDTH);
				b = new int[] {b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1};
				List<Layer> layers = new ArrayList<>();
				layers.add(jf::levelsAt);
				for (QuayWhole q : out) {
					Layer l = cells(q.cols(), Quays.WIDTH, q.y(), b[0], b[1], b[2], b[3], true, true);
					if (l != null) layers.add(l);
				}
				quayParts(whole, layers, false, out);
			}
			return List.copyOf(out);
		}

		/**
		 * The parts of a quay run, graded with the levels of what they meet ({@code layers}: whole streets and earlier quays). A stretch where
		 * it touches streets it cannot follow within the grade limit (a street beside it that climbs faster along it than a promenade may)
		 * is no quay: the quay stops short of it, and each part left meets that street with its new end. A part still steeper than allowed
		 * between two meetings loses the meeting nearest its first steep column. Each decision is made from the levels near it, so a tile whose
		 * window holds only part of the run decides alike; once anything is cut, parts shorter than a whole run are dropped.
		 */
		void quayParts(List<int[]> cols, List<Layer> layers, boolean anyCut, List<QuayWhole> out) {
			if (anyCut && cols.size() < Quays.MIN_LEN) return;
			TreeMap<Integer, Integer> meet = meetPins(cols, Quays.WIDTH, layers, true, true);
			TreeSet<Integer> cut = new TreeSet<>();
			cut.addAll(meetBad); // where no level fits the street it crosses, the quay stops short of it
			if (meet != null) {
				for (int k : meet.keySet()) {
					Integer a = meet.get(k - 1), c = meet.get(k - 3);
					int v = meet.get(k);
					if ((a != null && Math.abs(v - a) > 1) || (c != null && meet.containsKey(k - 2) && a != null && Math.abs(v - c) > 1)) for (int j = k - 3; j <= k; j++) if (meet.containsKey(j)) cut.add(j);
				}
			}
			if (cut.isEmpty() && meet != null) {
				QuayWhole q = gradeQuay(cols, meet);
				int bad = firstSteep(q.y());
				if (bad < 0) { out.add(q); return; }
				// the run of pinned columns nearest the steep column
				int best = -1, bd = Integer.MAX_VALUE;
				for (int k : meet.keySet()) if (Math.abs(k - bad) < bd) { bd = Math.abs(k - bad); best = k; }
				for (int k = best; meet.containsKey(k); k--) cut.add(k);
				for (int k = best; meet.containsKey(k); k++) cut.add(k);
			}
			if (cut.isEmpty()) { out.add(gradeQuay(cols, null)); return; }
			for (List<int[]> part : StreetCols.runs(cols, c -> !cut.contains(indexOf(cols, c)))) quayParts(part, layers, true, out);
		}

		/** The first column where a profile breaks the street grade ({@code road-grade}), or -1. */
		static int firstSteep(int[] y) {
			for (int i = 1; i < y.length; i++) if (Math.abs(y[i] - y[i - 1]) > 1 || (i >= 3 && Math.abs(y[i] - y[i - 3]) > 1)) return i;
			return -1;
		}

		/** A quay run graded with the given meeting pins under its gate passages. */
		QuayWhole gradeQuay(List<int[]> cols, TreeMap<Integer, Integer> meet) {
			TreeMap<Integer, Integer> pn = meet, g = gatePins(cols);
			if (pn == null) pn = g;
			else if (g != null) { pn = new TreeMap<>(pn); pn.putAll(g); }
			Grading.RoadGrade gr = Grading.profile(lattice(StreetCols.vertices(cols)), memo, pn);
			boolean[] steep = new boolean[cols.size()];
			for (Grading.Steep st : gr.steep()) for (int k = st.from(); k <= st.to(); k++) steep[k] = true;
			return new QuayWhole(cols, gr.profile().y(), steep);
		}

		static int[] xs(List<int[]> cols) { int[] o = new int[cols.size()]; for (int i = 0; i < o.length; i++) o[i] = cols.get(i)[0]; return o; }

		static int[] zs(List<int[]> cols) { int[] o = new int[cols.size()]; for (int i = 0; i < o.length; i++) o[i] = cols.get(i)[1]; return o; }

		/** A closed ring of columns, rotated to start just after a column that is no quay, so a run through the ring's start is not split (the start depends on where the contour was traced from). */
		List<int[]> breakRing(List<int[]> ring, java.util.function.Predicate<int[]> ok) {
			List<int[]> cols = new ArrayList<>(ring.subList(0, ring.size() - 1));
			for (int k = 0; k < cols.size(); k++) {
				if (ok.test(cols.get(k))) continue;
				List<int[]> out = new ArrayList<>(cols.subList(k + 1, cols.size()));
				out.addAll(cols.subList(0, k));
				return out;
			}
			return ring; // every column is a quay: one closed promenade, cut where the ring starts
		}

		/** A lane from the column of a quay nearest to a road or street, to that street; null when none is within reach or the route is bad. */
		/**
		 * {@link #nearest}, and in a lattice tile also the tile's own border streets: for a column of one the target is the point
		 * {@value #EDGE_STEP} blocks inside the tile (a lane ending there shares an edge with the border street's footprint and stays
		 * clear of the columns {@code tile-border-agree} compares).
		 */
		int[] nearestAttach(List<Raw> base, int x, int z) {
			int[] best = nearest(base, x, z);
			if (!rules.lattice()) return best;
			long bd = best == null ? 200L * 200L : (long) (best[0] - x) * (best[0] - x) + (long) (best[1] - z) * (best[1] - z);
			for (Raw r : base) {
				if (!r.macro() || r.kind != RoadKind.STREET) continue;
				int[] a = r.cols.get(0), e = r.cols.get(r.cols.size() - 1);
				boolean horizontal = a[1] == e[1], vertical = a[0] == e[0];
				if (horizontal == vertical) continue;
				int line = horizontal ? a[1] : a[0], lo = horizontal ? tile.minZ() : tile.minX(), hi = horizontal ? tile.maxZ() : tile.maxX();
				int sgn = line == lo ? 1 : 0; // only the tile's low borders: a lane beside a high border would cover the column just inside the band both tiles compare
				if (sgn == 0) continue;
				for (int[] c : r.cols) {
					int along = horizontal ? c[0] : c[1];
					if (along < (horizontal ? tile.minX() : tile.minZ()) + EDGE_STEP || along > (horizontal ? tile.maxX() : tile.maxZ()) - EDGE_STEP) continue;
					int tx = horizontal ? c[0] : line + sgn * EDGE_STEP, tz = horizontal ? line + sgn * EDGE_STEP : c[1];
					long dx = tx - x, dz = tz - z, d = dx * dx + dz * dz;
					if (d < bd) { bd = d; best = new int[] {tx, tz}; }
				}
			}
			return best;
		}

		static final int EDGE_STEP = 4;

		/** Longest tries to join a quay to the streets: the nearest few columns of it and their nearest street targets, in order of distance. */
		static final int QUAY_TRIES = 6;

		Raw quayLink(String laneId, List<int[]> run, List<Raw> base) {
			List<long[]> cand = new ArrayList<>();
			List<int[]> froms = new ArrayList<>(), tos = new ArrayList<>();
			for (int i = 0; i < run.size(); i += 4) {
				int[] c = run.get(i);
				if (c[0] < tile.minX() + EDGE_STEP || c[0] > tile.maxX() - EDGE_STEP - 1 || c[1] < tile.minZ() + EDGE_STEP || c[1] > tile.maxZ() - EDGE_STEP - 1) continue;
				int[] near = nearestAttach(base, c[0], c[1]);
				if (near == null) continue;
				long dx = near[0] - c[0], dz = near[1] - c[1];
				cand.add(new long[] {dx * dx + dz * dz, froms.size()});
				froms.add(c);
				tos.add(near);
			}
			cand.sort((p, q) -> p[0] != q[0] ? Long.compare(p[0], q[0]) : Long.compare(p[1], q[1]));
			List<int[]> tried = new ArrayList<>();
			for (int k = 0, n = 0; k < cand.size() && n < QUAY_TRIES; k++) {
				int[] from = froms.get((int) cand.get(k)[1]), to = tos.get((int) cand.get(k)[1]);
				boolean dup = false;
				for (int[] t : tried) if (Math.abs(t[0] - from[0]) + Math.abs(t[1] - from[1]) < 16 && t[2] == to[0] && t[3] == to[1]) dup = true;
				if (dup) continue;
				tried.add(new int[] {from[0], from[1], to[0], to[1]});
				n++;
				Raw lane = quayLane(laneId, from, to);
				if (lane != null) return lane;
			}
			return null;
		}

		/** A lane from a quay column to a street target (null when the lattice route, or the lane it makes, is no good). */
		Raw quayLane(String laneId, int[] from, int[] to) {
			int m = StreetRules.NODE;
			int qx = from[0], qz = from[1];
			int gx = qx + Math.round((to[0] - qx) / (float) m) * m, gz = qz + Math.round((to[1] - qz) / (float) m) * m;
			LatticePath.Bounds b = new LatticePath.Bounds(tile.minX() + EDGE_STEP, tile.minZ() + EDGE_STEP, tile.maxX() - EDGE_STEP - 1, tile.maxZ() - EDGE_STEP - 1);
			if (!b.contains(gx, gz) || (gx == qx && gz == qz)) return null;
			List<LatticePath.Pt> path = LatticePath.find(new LatticePath.Pt(qx, qz), new LatticePath.Pt(gx, gz), laneCost(qx, qz, 8), b, m, -1, 20_000, false, src.program().diagonals());
			if (path.size() < 2) return null;
			List<Pt> routed = new ArrayList<>();
			for (LatticePath.Pt q : path) routed.add(new Pt(q.x(), q.z()));
			Pt last = routed.get(routed.size() - 1);
			if (last.x() != to[0] && last.z() != to[1]) routed.add(new Pt(to[0], last.z()));
			if (routed.get(routed.size() - 1).x() != to[0] || routed.get(routed.size() - 1).z() != to[1]) routed.add(new Pt(to[0], to[1]));
			Raw lane = laneRaw(laneId, routed);
			return lane != null && !hitsReservedAny(lane) && grade(lane) ? lane : null;
		}

		// ---- lanes

		/** Is a lattice node of a lane search usable, and what does stepping to it cost? */
		LatticePath.Cost laneCost(int sx, int sz, int freeRadius) {
			return new LatticePath.Cost() {
				@Override public long step(int x, int z, int nx, int nz) {
					if (!tr.contains(nx, nz)) return FORBIDDEN;
					int f = fg(nx, nz);
					if ((f & (PLAZA | RESERVED | LOT | WET)) != 0) return FORBIDDEN;
					if (!den.urban(nx, nz)) return FORBIDDEN;
					boolean diag = x != nx && z != nz;
					int dy = Math.abs(fl(nx, nz) - fl(x, z));
					int len = diag ? 11 : 8;
					if (dy * 3 > len) return FORBIDDEN;
					long c = (diag ? 14L : 10L) * StreetRules.NODE + 60L * dy;
					boolean near = Math.max(Math.abs(nx - sx), Math.abs(nz - sz)) <= freeRadius;
					if (!near) {
						if ((f & STREET) != 0) c += 400;
						else if (nearStreet(nx, nz)) c += 200;
					}
					return c;
				}
			};
		}

		boolean nearStreet(int x, int z) {
			for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) if ((dx != 0 || dz != 0) && (fg(x + 6 * dx, z + 6 * dz) & STREET) != 0) return true;
			return false;
		}

		static List<Pt> simplify(List<Pt> pts) {
			List<Pt> out = new ArrayList<>();
			for (Pt p : pts) if (out.isEmpty() || !out.get(out.size() - 1).equals(p)) out.add(p);
			for (int i = out.size() - 2; i >= 1; i--) {
				Pt a = out.get(i - 1), b = out.get(i), c = out.get(i + 1);
				if (Integer.signum(b.x() - a.x()) == Integer.signum(c.x() - b.x()) && Integer.signum(b.z() - a.z()) == Integer.signum(c.z() - b.z())) out.remove(i);
			}
			return out;
		}

		Raw laneRaw(String laneId, List<Pt> pts) {
			List<Pt> s = simplify(pts);
			if (s.size() < 2) return null;
			List<List<int[]>> runs = StreetCols.runs(StreetCols.columns(s), this::keep); // clipped to the tile and its margin
			return runs.isEmpty() ? null : new Raw(laneId, RoadKind.LANE, StreetRules.LANE_WIDTH, runs.get(0), false);
		}

		/** Hashed ports on the tile's non-street borders: a stub into the tile, then a lane to the nearest base street. */
		void ports() {
			if (!src.program().ports() || rules.lattice()) return;
			List<Raw> base = new ArrayList<>(raws);
			int n = 0;
			for (DistrictGrid.Segment s : grid.segments(ix, iz)) {
				for (int p : rules.ports(s)) {
					int px = Arterials.ax(s, p), pz = Arterials.az(s, p);
					int ix0 = s.axis() == DistrictGrid.AXIS_X ? (s.fixed() == tile.minX() ? 1 : -1) : 0, iz0 = s.axis() == DistrictGrid.AXIS_X ? 0 : (s.fixed() == tile.minZ() ? 1 : -1);
					int qx = px + ix0 * StreetRules.NODE, qz = pz + iz0 * StreetRules.NODE;
					int ox = px - ix0 * StreetRules.NODE, oz = pz - iz0 * StreetRules.NODE;
					// the same columns are read from both sides of the border, so both tiles decide alike
					if (StreetRules.wet(exact, px, pz) || StreetRules.wet(exact, qx, qz) || StreetRules.wet(exact, ox, oz)
							|| !StreetRules.slopeOk(exact, px, pz, qx, qz) || !StreetRules.slopeOk(exact, px, pz, ox, oz)) continue;
					List<Pt> pts = new ArrayList<>(List.of(new Pt(ox, oz), new Pt(px, pz), new Pt(qx, qz)));
					String laneId = "t" + n++;
					int[] near = nearest(base, qx, qz);
					if (near != null) {
						int gx = qx + Math.round((near[0] - qx) / (float) StreetRules.NODE) * StreetRules.NODE, gz = qz + Math.round((near[1] - qz) / (float) StreetRules.NODE) * StreetRules.NODE;
						List<LatticePath.Pt> path = List.of();
						int m = StreetRules.NODE;
						LatticePath.Bounds b = new LatticePath.Bounds(tile.minX() + m, tile.minZ() + m, tile.maxX() - m, tile.maxZ() - m);
						if (b.contains(gx, gz) && b.contains(qx, qz) && (gx != qx || gz != qz)) {
							path = LatticePath.find(new LatticePath.Pt(qx, qz), new LatticePath.Pt(gx, gz), laneCost(qx, qz, 0), b, StreetRules.NODE, -1, 20_000, false, src.program().diagonals());
						}
						List<Pt> routed = new ArrayList<>(pts);
						for (LatticePath.Pt q : path) routed.add(new Pt(q.x(), q.z()));
						Pt last = routed.get(routed.size() - 1);
						if (last.x() != near[0] && last.z() != near[1]) routed.add(new Pt(near[0], last.z()));
						if (routed.get(routed.size() - 1).x() != near[0] || routed.get(routed.size() - 1).z() != near[1]) routed.add(new Pt(near[0], near[1]));
						Raw full = laneRaw(laneId, routed);
						if (full != null && !hitsReservedAny(full) && grade(full)) { raws.add(full); paint(full); continue; }
					}
					Raw stub = laneRaw(laneId, pts);
					if (stub != null && !hitsWall(stub)) { grade(stub); if (!stub.unmet) { raws.add(stub); paint(stub); } }
				}
			}
		}

		/** Does the street cross the wall ring's lots (this tile's and its neighbours' within reach)? Only the ring: every other reservation is private to its tile. */
		boolean hitsWall(Raw r) {
			for (int[] c : r.cols) for (Rect w : wallBlocked) if (StreetCols.footprintOverlaps(c, r.width, w)) return true;
			return false;
		}

		boolean hitsReservedAny(Raw r) {
			for (int[] c : r.cols) if (hitsReserved(c, r.width)) return true;
			return false;
		}

		/** The centre-line column of a base street nearest to (x, z), or null when none lies within 200 blocks. */
		static int[] nearestIn(List<Raw> base, int x, int z) {
			long best = 200L * 200L;
			int[] out = null;
			for (Raw r : base) {
				for (int[] c : r.cols) {
					long dx = c[0] - x, dz = c[1] - z, d = dx * dx + dz * dz;
					if (d < best) { best = d; out = c; }
				}
			}
			return out;
		}

		int[] nearest(List<Raw> base, int x, int z) {
			long best = 200L * 200L;
			int[] out = null;
			int m = StreetRules.NODE;
			for (Raw r : base) {
				for (int[] c : r.cols) {
					if (c[0] < tile.minX() + m || c[0] > tile.maxX() - m || c[1] < tile.minZ() + m || c[1] > tile.maxZ() - m) continue; // keep the lane well inside the tile
					long dx = c[0] - x, dz = c[1] - z, d = dx * dx + dz * dz;
					if (d < best) { best = d; out = c; }
				}
			}
			return out;
		}

		/** Squares moved off the skeleton's spot (exact terrain would not grade it): the skeleton roads no longer reach them. */
		final List<Rect> moved = new ArrayList<>();

		/** A lane from each moved square to the nearest street, so the square is part of the road network. */
		/** Longest straight lane {@link #stubLane} lays from a square's edge to a street. */
		static final int STUB_REACH = 24;

		/**
		 * A short straight lane from the middle (then points 4 apart either side) of one of the square's sides to the first street
		 * column within {@value #STUB_REACH} blocks straight out, over free dry ground. Joins a square that sits just beside a road
		 * on the tile border, where the lattice lanes (kept a node inside the tile) cannot reach. Shortest stub wins; false if none.
		 */
		boolean stubLane(Rect sq, String laneId) {
			Raw best = null;
			int bestLen = Integer.MAX_VALUE;
			int h = StreetRules.LANE_WIDTH / 2;
			for (Facing side : new Facing[] {Facing.NORTH, Facing.SOUTH, Facing.WEST, Facing.EAST}) {
				boolean alongX = side == Facing.NORTH || side == Facing.SOUTH;
				int len = alongX ? sq.w() : sq.d(), lo = (alongX ? sq.x() : sq.z()) + h + 1, mid = (alongX ? sq.x() : sq.z()) + len / 2;
				for (int k = 0; k <= len / 2; k += 4) {
					for (int sgn = -1; sgn <= 1; sgn += 2) {
						if (k == 0 && sgn > 0) continue;
						int a = mid + sgn * k;
						if (a < lo || a > (alongX ? sq.x() + sq.w() : sq.z() + sq.d()) - h - 2) continue;
						int sx = alongX ? a : side == Facing.WEST ? sq.x() - 1 - h : sq.x() + sq.w() + h;
						int sz = alongX ? (side == Facing.NORTH ? sq.z() - 1 - h : sq.z() + sq.d() + h) : a; // the lane's footprint ends on the square's edge
						int ux = side.dx, uz = side.dz;
						int hit = -1;
						for (int s = 0; s < STUB_REACH && s < bestLen; s++) {
							int x = sx + ux * s, z = sz + uz * s;
							if (x - h < tile.minX() + 3 || x + h > tile.maxX() - 4 || z - h < tile.minZ() + 3 || z + h > tile.maxZ() - 4) break; // clear of the columns tile-border-agree compares
							if ((fg(x, z) & STREET) != 0) { hit = s; break; }
							boolean bad = false;
							for (int o = -h; o <= h; o++) {
								int fx = x + (alongX ? o : 0), fz = z + (alongX ? 0 : o);
								if ((fg(fx, fz) & (PLAZA | LOT | RESERVED | WET | APPROACH)) != 0 || !inR(fx, fz)) bad = true;
							}
							if (bad) break;
							// the tip's footprint shares an edge with a street (a border street that the guard above keeps the lane off)
							boolean adj = false;
							for (int o = -h; o <= h; o++) {
								int fx = x + ux * (h + 1) + (alongX ? o : 0), fz = z + uz * (h + 1) + (alongX ? 0 : o);
								if ((fg(fx, fz) & STREET) != 0) adj = true;
							}
							if (adj) { hit = s; break; }
						}
						if (hit < 0 || hit >= bestLen) continue;
						Raw lane = new Raw(laneId, RoadKind.LANE, StreetRules.LANE_WIDTH, StreetCols.columns(List.of(new Pt(sx, sz), new Pt(sx + ux * Math.max(1, hit), sz + uz * Math.max(1, hit)))), false);
						if (lane.cols.size() < 2 || !grade(lane)) continue;
						best = lane;
						bestLen = hit;
					}
				}
			}
			if (best == null) return false;
			raws.add(best);
			paint(best);
			return true;
		}

		void plazaLanes() {
			if (moved.isEmpty()) return;
			List<Raw> base = new ArrayList<>(raws);
			int m = StreetRules.NODE, n = 0;
			LatticePath.Bounds b = new LatticePath.Bounds(tile.minX() + m, tile.minZ() + m, tile.maxX() - m, tile.maxZ() - m);
			for (Rect pr : moved) {
				if (stubLane(pr, "q" + n)) { n++; continue; }
				int cx = pr.x() + pr.w() / 2, cz = pr.z() + pr.d() / 2;
				// the nearest column of each street run (a skeleton road the square cut in two has a piece on each side), up to 3 within 64 blocks of the nearest
				List<long[]> cand = new ArrayList<>();
				List<int[]> targets = new ArrayList<>();
				for (Raw r : base) {
					int[] best = null;
					long bd = 200L * 200L;
					for (int[] c : r.cols) {
						if (c[0] < tile.minX() + m || c[0] > tile.maxX() - m || c[1] < tile.minZ() + m || c[1] > tile.maxZ() - m) continue;
						if (c[0] >= pr.x() - 2 && c[0] < pr.x() + pr.w() + 2 && c[1] >= pr.z() - 2 && c[1] < pr.z() + pr.d() + 2) continue; // under or against the square: nothing joins it there
						long ddx = c[0] - cx, ddz = c[1] - cz, dd = ddx * ddx + ddz * ddz;
						if (dd < bd) { bd = dd; best = c; }
					}
					if (best != null) { cand.add(new long[] {bd, targets.size()}); targets.add(best); }
				}
				cand.sort((p, q) -> p[0] != q[0] ? Long.compare(p[0], q[0]) : Long.compare(p[1], q[1]));
				List<int[]> chosen = new ArrayList<>();
				for (long[] c : cand) {
					if (chosen.size() >= 3 || (!chosen.isEmpty() && c[0] > (long) 64 * 64)) break;
					int[] t = targets.get((int) c[1]);
					boolean dup = false;
					for (int[] o : chosen) if (Math.abs(o[0] - t[0]) + Math.abs(o[1] - t[1]) < 12) dup = true;
					if (!dup) chosen.add(t);
				}
				for (int[] near : chosen) {
				int dx = near[0] - cx, dz = near[1] - cz;
				int ex, ez, qx, qz;
				if (Math.abs(dx) * pr.d() > Math.abs(dz) * pr.w()) {
					int sgn = dx > 0 ? 1 : -1;
					ex = sgn > 0 ? pr.x() + pr.w() + 1 : pr.x() - 2;
					qx = ex + sgn * m;
					ez = qz = cz;
				} else {
					int sgn = dz > 0 ? 1 : -1;
					ez = sgn > 0 ? pr.z() + pr.d() + 1 : pr.z() - 2;
					qz = ez + sgn * m;
					ex = qx = cx;
				}
				int gx = qx + Math.round((near[0] - qx) / (float) m) * m, gz = qz + Math.round((near[1] - qz) / (float) m) * m;
				if (!b.contains(qx, qz) || !b.contains(gx, gz)) continue;
				List<List<Pt>> variants = new ArrayList<>();
				if (gx != qx || gz != qz) {
					List<LatticePath.Pt> path = LatticePath.find(new LatticePath.Pt(qx, qz), new LatticePath.Pt(gx, gz), laneCost(qx, qz, 0), b, m, -1, 40_000, false, src.program().diagonals());
					if (!path.isEmpty()) {
						List<Pt> v = new ArrayList<>();
						for (LatticePath.Pt q : path) v.add(new Pt(q.x(), q.z()));
						variants.add(v);
					}
				}
				variants.add(List.of(new Pt(near[0], qz))); // no lattice route: an L either way round, taken only if it hits nothing
				variants.add(List.of(new Pt(qx, near[1])));
				for (List<Pt> mid : variants) {
					List<Pt> routed = new ArrayList<>(List.of(new Pt(ex, ez), new Pt(qx, qz)));
					routed.addAll(mid);
					Pt last = routed.get(routed.size() - 1);
					if (last.x() != near[0] && last.z() != near[1]) routed.add(new Pt(near[0], last.z()));
					if (routed.get(routed.size() - 1).x() != near[0] || routed.get(routed.size() - 1).z() != near[1]) routed.add(new Pt(near[0], near[1]));
					Raw lane = laneRaw("q" + n++, routed);
					if (lane == null || hitsReservedAny(lane) || !grade(lane)) continue;
					raws.add(lane);
					paint(lane);
					break;
				}
				}
			}
		}

		/** Lanes branching from the roads and streets of organic areas every 40 to 70 blocks, 24 to 56 long. */
		void frontageLanes() {
			if (!src.program().lanes()) return;
			List<Raw> base = new ArrayList<>();
			for (Raw r : raws) if (r.kind == RoadKind.MAIN || r.kind == RoadKind.PATH || r.kind == RoadKind.QUAY || (r.kind == RoadKind.STREET && !r.id.startsWith("s"))) base.add(r);
			base.sort(Comparator.comparing(r -> r.id));
			int m = StreetRules.NODE;
			Rect inner = new Rect(tile.minX() + 14, tile.minZ() + 14, tile.widthX() - 28, tile.widthZ() - 28);
			LatticePath.Bounds b = new LatticePath.Bounds(tile.minX() + m, tile.minZ() + m, tile.maxX() - m, tile.maxZ() - m);
			int ln = 0;
			for (Raw r : base) {
				long h = Hash64.hash(seed, "lane-gap", ix, iz, Hash64.of(r.id));
				int i = 16 + Hash64.below(Hash64.hash(h, "k", 0), 41), k = 1;
				while (i < r.cols.size() - 1) {
					int[] c = r.cols.get(i);
					int idx = i;
					i += 40 + Hash64.below(Hash64.hash(h, "k", k), 31);
					long hk = Hash64.hash(h, "branch", k++);
					if (!inner.contains(c[0], c[1]) || !rules.frontageAt(c[0], c[1])) continue;
					int[] a = r.cols.get(idx - 1), z = r.cols.get(idx + 1);
					int dx = z[0] - a[0], dz = z[1] - a[1];
					int ux, uz;
					int side = Hash64.below(hk, 2) * 2 - 1;
					if (dz == 0) { ux = 0; uz = side; }
					else if (dx == 0) { ux = side; uz = 0; }
					else if (Hash64.below(Hash64.hash(hk, "axis"), 2) == 0) { ux = side; uz = 0; }
					else { ux = 0; uz = side; }
					int len = 24 + 8 * Hash64.below(Hash64.hash(hk, "len"), 5);
					int gx = 0, gz = 0;
					boolean got = false;
					for (; len >= 16; len -= 8) {
						gx = c[0] + ux * len;
						gz = c[1] + uz * len;
						if (b.contains(gx, gz) && b.contains(c[0], c[1]) && rules.frontageAt(gx, gz)) { got = true; break; }
					}
					if (!got) continue;
					List<LatticePath.Pt> path = LatticePath.find(new LatticePath.Pt(c[0], c[1]), new LatticePath.Pt(gx, gz), laneCost(c[0], c[1], 14), b, StreetRules.NODE, -1, 20_000, false, src.program().diagonals());
					if (path.size() < 2) continue;
					List<Pt> pts = new ArrayList<>();
					for (LatticePath.Pt q : path) pts.add(new Pt(q.x(), q.z()));
					Raw lane = laneRaw("n" + ln++, pts);
					if (lane == null || hitsReservedAny(lane)) continue;
					if (!grade(lane)) continue;
					raws.add(lane);
					paint(lane);
				}
			}
		}

		// ---- diagonal avenues (IRR1, the DL half of 5.13)

		/**
		 * Frontage for a diagonal avenue. A rectangle meets a 45-degree band in a corner, so no lot can front it; instead axis-aligned
		 * cut lanes leave it every 22 to 38 blocks, one way or the other, and run on until a street stops them (the grid street the avenue
		 * cuts across) or at most {@value #AVE_LANE_MAX} blocks. Lots then front the lane on both sides, which fills the triangles the avenue
		 * cuts out of its blocks. Organic frontage land is left to {@link #frontageLanes}, which already branches lanes off every road.
		 */
		void avenueLanes() {
			Rect inner = new Rect(tile.minX() + 14, tile.minZ() + 14, tile.widthX() - 28, tile.widthZ() - 28);
			List<Raw> base = new ArrayList<>();
			for (Raw r : raws) if ((r.kind == RoadKind.MAIN || r.kind == RoadKind.PATH) && r.street != null) base.add(r);
			base.sort(Comparator.comparing(r -> r.id));
			int n = 0;
			for (Raw r : base) {
				for (int[] run : diagonalRuns(r.cols)) {
					if (run[1] - run[0] < AVE_MIN_RUN) continue;
					long h = Hash64.hash(seed, "avenue-lane", ix, iz, Hash64.of(r.id), run[0]);
					int i = run[0] + 6 + Hash64.below(Hash64.hash(h, "k", 0), AVE_GAP_VAR), k = 1;
					while (i < run[1] - 4) {
						int[] c = r.cols.get(i);
						int[] a = r.cols.get(i - 1), z = r.cols.get(i + 1);
						int dx = Integer.signum(z[0] - a[0]), dz = Integer.signum(z[1] - a[1]);
						long hk = Hash64.hash(h, "lane", k);
						i += AVE_GAP_MIN + Hash64.below(Hash64.hash(h, "k", k++), AVE_GAP_VAR);
						if (!inner.contains(c[0], c[1]) || !den.urban(c[0], c[1]) || rules.frontageAt(c[0], c[1])) continue;
						int[] u = switch (Hash64.below(hk, 4)) { case 0 -> new int[] {dx, 0}; case 1 -> new int[] {0, dz}; case 2 -> new int[] {-dx, 0}; default -> new int[] {0, -dz}; };
						Raw lane = cutLane("av" + n, c, u[0], u[1], inner);
						if (lane == null) continue;
						n++;
						raws.add(lane);
						paint(lane);
					}
				}
			}
		}

		/**
		 * What is left of the triangles a diagonal avenue cuts out of its blocks once the lots are packed: a free dry square of 16 to 20
		 * blocks flush with a street, within 30 blocks of the avenue, becomes a small green (at most 2 per tile). Slivers under 16
		 * across stay empty.
		 */
		void avenueGreens(List<Raw> finals) {
			int placed = 0;
			for (Raw r : finals) {
				if (r.kind != RoadKind.MAIN && r.kind != RoadKind.PATH) continue;
				for (int[] run : diagonalRuns(r.cols)) {
					if (run[1] - run[0] < AVE_MIN_RUN) continue;
					for (int i = run[0] + 4; i < run[1] && placed < 2; i += 12) {
						int[] c = r.cols.get(i);
						Rect best = null;
						long bestD = Long.MAX_VALUE;
						for (int s = 20; s >= 16 && best == null; s -= 4) {
							for (int z0 = c[1] - 30 - s; z0 <= c[1] + 30; z0++) {
								for (int x0 = c[0] - 30 - s; x0 <= c[0] + 30; x0++) {
									long dx = x0 + s / 2 - c[0], dz = z0 + s / 2 - c[1], d = dx * dx + dz * dz;
									if (d >= bestD || !free(x0 + s / 2, z0 + s / 2) || streetsAround(new Rect(x0, z0, s, s)) < s) continue;
									Rect q = new Rect(x0, z0, s, s);
									if (!tr.containsRect(x0 - 14, z0 - 14, s + 28, s + 28) || !rectFree(q, false) || !dry(q) || streetsAround(q) < s || !den.urban(x0 + s / 2, z0 + s / 2)) continue;
									bestD = d;
									best = q;
								}
							}
						}
						if (best == null) continue;
						Optional<Grading.PlazaGrade> g = Grading.plaza(best, exact);
						if (g.isEmpty()) continue;
						plazas.add(new Plaza("p" + plazas.size(), "green", best.x(), best.z(), best.w(), best.d(), g.get().level()));
						squares.add(best);
						reserved.add(best);
						paintRect(best, PLAZA | RESERVED);
						notes.add("avenue green " + best.w() + "x" + best.d() + " at " + best.x() + "," + best.z());
						placed++;
					}
				}
			}
		}

		/** A lane from the avenue column {@code c} along (ux, uz): over the avenue's own band, then free dry urban ground up to the first street (kept: it joins it) or {@value #AVE_LANE_MAX} blocks. */
		Raw cutLane(String laneId, int[] c, int ux, int uz, Rect inner) {
			int h = StreetRules.LANE_WIDTH / 2;
			int k = 0;
			while (k < 16 && (fg(c[0] + ux * k, c[1] + uz * k) & STREET) != 0) k++; // out of the avenue's band
			int start = Math.max(0, k - 1), end = -1;
			for (; k <= AVE_LANE_MAX; k++) {
				int x = c[0] + ux * k, z = c[1] + uz * k;
				if (!inner.contains(x, z) || !den.urban(x, z)) break;
				if ((fg(x, z) & STREET) != 0) { end = k; break; }
				for (int o = -h; o <= h; o++) {
					int fx = x + (ux == 0 ? o : 0), fz = z + (uz == 0 ? o : 0);
					if ((fg(fx, fz) & (PLAZA | LOT | RESERVED | WET | APPROACH)) != 0 || !inR(fx, fz) || wt(fx, fz)) { k = AVE_LANE_MAX + 1; break; }
				}
				if (k > AVE_LANE_MAX) break;
			}
			if (end < 0) { // no street to run into: a dead-end lane of a hashed length that stopped at the limit or short of an obstacle
				int len = 0;
				for (int t = start + 1; t <= AVE_LANE_MAX; t++) {
					int x = c[0] + ux * t, z = c[1] + uz * t;
					boolean bad = !inner.contains(x, z) || !den.urban(x, z) || (fg(x, z) & (STREET | PLAZA | LOT | RESERVED | WET | APPROACH)) != 0 || wt(x, z);
					for (int o = -h; o <= h && !bad; o++) {
						int fx = x + (ux == 0 ? o : 0), fz = z + (uz == 0 ? o : 0);
						if ((fg(fx, fz) & (PLAZA | LOT | RESERVED | WET | APPROACH)) != 0 || !inR(fx, fz) || wt(fx, fz)) bad = true;
					}
					if (bad) break;
					len = t - start;
				}
				if (len < AVE_LANE_MIN) return null;
				end = start + len;
			}
			if (end - start < AVE_LANE_MIN) return null;
			List<Pt> pts = List.of(new Pt(c[0] + ux * start, c[1] + uz * start), new Pt(c[0] + ux * end, c[1] + uz * end));
			Raw lane = laneRaw(laneId, pts);
			if (lane == null || hitsReservedAny(lane) || !grade(lane)) return null;
			return lane;
		}

		// ---- lots

		/** A line of cells along a street run, and the cells away from it: {@code cell(u, v)} with v = -1 on the street. */
		final class Frame {
			final int bx, bz, ux, uz, vx, vz, uLo, uHi;
			final Facing front;
			int[] dep;

			Frame(int bx, int bz, int ux, int uz, int vx, int vz, int uLo, int uHi, Facing front) {
				this.bx = bx;
				this.bz = bz;
				this.ux = ux;
				this.uz = uz;
				this.vx = vx;
				this.vz = vz;
				this.uLo = uLo;
				this.uHi = uHi;
				this.front = front;
			}

			int x(int u, int v) { return bx + ux * u + vx * v; }

			int z(int u, int v) { return bz + uz * u + vz * v; }

			/** Free cells away from the street at column u (0 unless the cell next to the front is street), at most MAX_DEPTH. */
			int depth(int u) {
				if ((fg(x(u, -1), z(u, -1)) & STREET) == 0) return 0;
				int v = 0;
				while (v < MAX_DEPTH && free(x(u, v), z(u, v))) v++;
				return v;
			}

			Rect rect(int u0, int w, int d) {
				int xa = x(u0, 0), za = z(u0, 0), xb = x(u0 + w - 1, d - 1), zb = z(u0 + w - 1, d - 1);
				return new Rect(Math.min(xa, xb), Math.min(za, zb), Math.abs(xb - xa) + 1, Math.abs(zb - za) + 1);
			}
		}

		void frontageLots(List<Raw> finals) {
			for (Raw r : finals) {
				if (!r.lotOk) continue;
				List<int[]> cols = r.cols;
				int i = 0;
				while (i + 1 < cols.size()) {
					int dx = cols.get(i + 1)[0] - cols.get(i)[0], dz = cols.get(i + 1)[1] - cols.get(i)[1];
					int j = i + 1;
					while (j + 1 < cols.size() && cols.get(j + 1)[0] - cols.get(j)[0] == dx && cols.get(j + 1)[1] - cols.get(j)[1] == dz) j++;
					if ((dx == 0) != (dz == 0) && j - i >= MIN_W) {
						int[] a = cols.get(i), b = cols.get(j);
						int h = r.width / 2, w = r.width;
						if (dz == 0) {
							int z = a[1], x0 = Math.min(a[0], b[0]), x1 = Math.max(a[0], b[0]);
							placeAlong(new Frame(0, z - h - 1, 1, 0, 0, -1, x0 - h, x1 - h + w - 1, Facing.SOUTH), r);
							placeAlong(new Frame(0, z - h + w, 1, 0, 0, 1, x0 - h, x1 - h + w - 1, Facing.NORTH), r);
						} else {
							int x = a[0], z0 = Math.min(a[1], b[1]), z1 = Math.max(a[1], b[1]);
							placeAlong(new Frame(x - h - 1, 0, 0, 1, -1, 0, z0 - h, z1 - h + w - 1, Facing.EAST), r);
							placeAlong(new Frame(x - h + w, 0, 0, 1, 1, 0, z0 - h, z1 - h + w - 1, Facing.WEST), r);
						}
					}
					i = j;
				}
			}
		}

		void placeAlong(Frame f, Raw street) {
			int n = f.uHi - f.uLo + 1;
			int[] dep = new int[n];
			for (int k = 0; k < n; k++) dep[k] = f.depth(f.uLo + k);
			int u = f.uLo;
			while (u <= f.uHi - (MIN_W - 1)) {
				if (dep[u - f.uLo] < MIN_DEPTH_ANY) { u++; continue; }
				int wa = 0;
				while (u + wa <= f.uHi && wa < 100 && dep[u + wa - f.uLo] >= MIN_DEPTH_ANY) wa++;
				if (wa < MIN_W) { u += wa; continue; }
				LotBrief l = tryLot(f, dep, u, wa, street);
				if (l == null) { u += 4; continue; }
				int used = (f.front == Facing.NORTH || f.front == Facing.SOUTH) ? l.w() : l.d();
				for (int k = u; k < u + used; k++) dep[k - f.uLo] = 0;
				long h = Hash64.hash(seed, "lot-gap", l.x(), l.z());
				u += used + (Hash64.below(h, 4) == 0 ? 2 + Hash64.below(Hash64.hash(h, "k", 1), 3) : 0);
			}
		}

		LotBrief tryLot(Frame f, int[] dep, int u, int wa, Raw street) {
			int probe = Math.min(wa, 24);
			int dmaxFirst = MAX_DEPTH;
			for (int k = 0; k < probe; k++) dmaxFirst = Math.min(dmaxFirst, dep[u + k - f.uLo]);
			int sx = f.x(u + probe / 2, Math.min(6, dmaxFirst - 1)), sz = f.z(u + probe / 2, Math.min(6, dmaxFirst - 1));
			if (!den.urban(sx, sz)) return null;
			int dens = den.at(sx, sz);
			if (dens <= 0) return null;
			QuarterKind q = qf.at(sx, sz, dens);
			dens = edge.cap(dens, sx, sz); // DS-18: no tall class near the built edge (BuiltEdge); the quarter keeps the field density
			int lotX = f.x(u, 0), lotZ = f.z(u, 0);
			int relief = 0;
			java.util.Set<String> excluded = new java.util.TreeSet<>(); // types this frontage could not take once the lot had grown for its setback
			for (int attempt = 0; attempt < 6; attempt++) {
				Optional<ClassTable.Pick> p = ClassTable.pick(seed, lotX, lotZ, dens, q, relief, Math.min(wa, 100), dmaxFirst, excluded);
				if (p.isEmpty()) return null;
				String type = p.get().type();
				TypeLimits lim = Types.get(type).limits();
				if (lim.allStreets()) return null;
				int[] dims = dims(f, dep, u, wa, type, lim, relief, Hash64.hash(seed, "lot-size", lotX, lotZ));
				if (dims == null) {
					if (relief == 0) return null;
					excluded.add(type); // the grown lot does not fit this frontage: try a smaller type
					continue;
				}
				Rect r = f.rect(u, dims[0], dims[1]);
				if (!dry(r)) return null;
				int rel = Math.max(relief(r), exactRelief(r)); // the brief stage measures every column: plan with the same relief (MINLOT)
				boolean alongX = f.front == Facing.NORTH || f.front == Facing.SOUTH;
				if (!SpecPolicyV1.reliefOk(lim, rel, r.w(), r.d()) || !SpecPolicyV1.railRoomOk(type, lim, rel, alongX ? r.w() : r.d(), alongX ? r.d() : r.w())) {
					if (rel == relief) {
						if (relief == 0) return null;
						excluded.add(type); // still too steep or too small once grown: try another type
						continue;
					}
					relief = rel;
					continue;
				}
				return place(f, street, r, u, dims, type, p.get(), dens);
			}
			return null;
		}

		/** Width along the street and depth for a type: a test lot that fits, widened to absorb a leftover too small for a lot. */
		int[] dims(Frame f, int[] dep, int u, int wa, String type, TypeLimits lim, int relief, long h) {
			boolean room = !SpecPolicyV1.railRoomOk(type, lim, relief, lim.minLotWidth(), lim.minLotDepth()); // sloping ground: the lot grows to leave room for a setback (MINLOT)
			int minW = lim.minLotWidth() + (room ? SpecPolicyV1.RAIL_ROOM_WIDTH : 0), minD = lim.minLotDepth() + (room ? SpecPolicyV1.RAIL_ROOM_DEPTH : 0);
			List<int[]> cands = new ArrayList<>(); // a lot grown for its setback stays near the grown minimum: slope lots are small and many (MINLOT)
			for (int[] t : room ? List.<int[]>of() : lim.testLots()) {
				if (t[0] < minW || t[1] < minD || t[0] > wa || t[1] > MAX_DEPTH) continue;
				if (minDepth(f, dep, u, t[0]) >= t[1]) cands.add(t);
			}
			int w, d;
			if (cands.isEmpty()) {
				if (minW > wa || minDepth(f, dep, u, minW) < minD) return null;
				w = minW;
				d = minD;
			} else {
				int total = cands.size() * (cands.size() + 1) / 2, t = Hash64.below(Hash64.hash(h, "k", 0), total), k = 0;
				for (; k < cands.size() - 1; k++) {
					t -= cands.size() - k;
					if (t < 0) break;
				}
				w = cands.get(k)[0];
				d = cands.get(k)[1];
			}
			w += Hash64.below(Hash64.hash(h, "k", 1), Math.min(room ? 1 : 4, wa - w) + 1);
			if (wa - w < MIN_W) w = Math.min(wa, w + MIN_W);
			w = Math.min(w, wa);
			// a strip left beside the lot that is too narrow to take a lot grown for its setback, on sloping ground: this lot gives up width for it when it can, else takes it (MINLOT)
			int left = wa - w, grown = lim.minLotWidth() + SpecPolicyV1.RAIL_ROOM_WIDTH;
			if (left > 0 && left < grown && exactRelief(f.rect(u + w, left, Math.max(minD, 12))) >= SpecPolicyV1.RAIL_ROOM_RELIEF) w = wa - grown >= minW ? wa - grown : wa;
			int dm = minDepth(f, dep, u, w);
			d = Math.min(MAX_DEPTH, d + Hash64.below(Hash64.hash(h, "k", 2), Math.max(0, Math.min(4, dm - d)) + 1));
			if (dm < d) d = dm;
			if (w < minW || d < minD) return null;
			return new int[] {w, d};
		}

		int minDepth(Frame f, int[] dep, int u, int w) {
			int m = MAX_DEPTH;
			for (int k = u; k < u + w; k++) m = Math.min(m, dep[k - f.uLo]);
			return m;
		}

		LotBrief place(Frame f, Raw street, Rect r, int u, int[] dims, String type, ClassTable.Pick pick, int dens) {
			paintRect(r, LOT);
			Raw fs = streetAt(f.x(u + dims[0] / 2, -1), f.z(u + dims[0] / 2, -1)); // the street in front of the middle of the front edge
			return addLot(r, f.front, type, pick.minStoreys(), pick.maxStoreys(), dens, fs == null ? street : fs);
		}

		LotBrief addLot(Rect r, Facing front, String type, int minS, int maxS, int dens, Raw frontStreet) {
			int air = frontStreet == null ? 0 : airRights(frontStreet);
			LotBrief l = new LotBrief(id + ".l" + lots.size(), r.x(), r.z(), r.w(), r.d(), front, type, StyleLibrary.AUTO, minS, maxS, 1, 0, Brief.NO_FRONT_Y, dens, LotBrief.BUILD);
			lots.add(l);
			lotAir.add(air);
			return l;
		}

		/** Jetty reach (plan decision 7): 2 over streets of width 7 and more, else 1; none over lanes under 4; never reaching the centre line. */
		static int airRights(Raw s) {
			if (s.width < 4) return 0;
			return Math.min(s.width >= 7 ? 2 : 1, s.width / 2 - 1);
		}

		/** Whole street blocks of the grid: a {@code block} lot where all four sides are street, the density is high and the table picks it. */
		void blockLots() {
			DistrictGrid.Segment[] seg = grid.segments(ix, iz);
			List<Integer> xs = new ArrayList<>(), zs = new ArrayList<>();
			List<Integer> xl = new ArrayList<>(rules.gridLines(DistrictGrid.AXIS_X, ix)), zl = new ArrayList<>(rules.gridLines(DistrictGrid.AXIS_Z, iz));
			int w0 = rules.borderWidth(seg[0]), w1 = rules.borderWidth(seg[1]), n0 = rules.borderWidth(seg[2]), n1 = rules.borderWidth(seg[3]);
			// free interval [a, b) of each block along an axis: between the street edges
			List<int[]> xi = intervals(tile.minX(), tile.maxX(), w0, w1, xl), zi = intervals(tile.minZ(), tile.maxZ(), n0, n1, zl);
			for (int[] zr : zi) {
				for (int[] xr : xi) {
					int w = xr[1] - xr[0], d = zr[1] - zr[0];
					if (w < 52 || d < 52 || w > 100 || d > 100) continue;
					Rect r = new Rect(xr[0], zr[0], w, d);
					if (!tr.containsRect(r.x(), r.z(), r.w(), r.d()) || !ringIsStreet(r) || !rectFree(r, false) || !dry(r)) continue;
					int cx = r.x() + r.w() / 2, cz = r.z() + r.d() / 2;
					int dens = den.at(cx, cz);
					if (dens < BLOCK_DENSITY || !den.urban(cx, cz)) continue;
					QuarterKind q = qf.at(cx, cz, dens);
					dens = edge.cap(dens, cx, cz); // DS-18: no tall class near the built edge (BuiltEdge); the quarter keeps the field density
					Optional<ClassTable.Pick> p = ClassTable.pick(seed, r.x(), r.z(), dens, q, relief(r), w, d);
					if (p.isEmpty() || !p.get().type().equals("block")) continue;
					Facing front = Facing.values()[Hash64.below(Hash64.hash(seed, "block-front", r.x(), r.z()), 4)];
					int mid = Math.max(1, ((front.dx != 0 ? r.d() : r.w()) - 1) / 2);
					int fx = front == Facing.EAST ? r.x() + r.w() : front == Facing.WEST ? r.x() - 1 : r.x() + mid;
					int fz = front == Facing.SOUTH ? r.z() + r.d() : front == Facing.NORTH ? r.z() - 1 : r.z() + mid;
					Raw fs0 = streetAt(fx, fz);
					if (fs0 != null && !fs0.lotOk) continue;
					paintRect(r, LOT);
					Raw fs = streetAt(fx, fz);
					addLot(r, front, "block", p.get().minStoreys(), p.get().maxStoreys(), dens, fs);
				}
			}
		}

		/** Free intervals between the street edges along one axis: the borders (street width 0 = none) and the inner lines (width 5). */
		static List<int[]> intervals(int lo, int hi, int wLo, int wHi, List<Integer> lines) {
			List<int[]> out = new ArrayList<>();
			int a = wLo > 0 ? lo - wLo / 2 + wLo : lo;
			for (int p : lines) {
				int h = StreetRules.GRID_WIDTH / 2;
				out.add(new int[] {a, p - h});
				a = p - h + StreetRules.GRID_WIDTH;
			}
			out.add(new int[] {a, wHi > 0 ? hi - wHi / 2 : hi});
			return out;
		}

		boolean ringIsStreet(Rect r) {
			for (int x = r.x() - 1; x <= r.x() + r.w(); x++) if ((fg(x, r.z() - 1) & STREET) == 0 || (fg(x, r.z() + r.d()) & STREET) == 0) return false;
			for (int z = r.z(); z < r.z() + r.d(); z++) if ((fg(r.x() - 1, z) & STREET) == 0 || (fg(r.x() + r.w(), z) & STREET) == 0) return false;
			return true;
		}

		// ---- the plan

		Detail assemble(List<Raw> finals) {
			List<Street> streets = new ArrayList<>();
			for (Raw r : finals) streets.add(r.street);
			DistrictInfo info = info();
			// provisional plan: streets mask and frontY come from the same helpers the brief filler uses
			List<List<Rect>> streetRects = new ArrayList<>();
			for (Street s : streets) streetRects.add(SettlementChecks.footprint(s));
			List<Rect> plazaRects = new ArrayList<>();
			for (Plaza p : plazas) plazaRects.add(new Rect(p.x(), p.z(), p.w(), p.d()));
			DistrictPlan bare = new DistrictPlan(GEN_VERSION, seed, info, streets, plazas, lots, null, List.of());
			List<LotBrief> out = new ArrayList<>();
			for (int i = 0; i < lots.size(); i++) {
				LotBrief l = lots.get(i);
				int mask = Briefs.streetMask(l, streetRects, plazaRects);
				int fy = Briefs.frontY(bare, l, streetRects);
				out.add(new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), l.type(), l.style(), l.minStoreys(), l.maxStoreys(), mask, lotAir.get(i), fy, l.density(), l.status(), l.walk()));
			}
			DistrictPlan plan = new DistrictPlan(GEN_VERSION, seed, info, streets, plazas, out, null, List.of());
			List<Violation> v = new ArrayList<>(SettlementChecks.detail(plan, landmarks));
			v.addAll(SettlementChecks.lotDry(plan, exact));
			plan = new DistrictPlan(GEN_VERSION, seed, info, streets, plazas, out, null, v);
			return new Detail(plan, landmarks, notes, plazaOk.length > 0 && plazaOk[0]);
		}

		static final int GEN_VERSION = dev.fractalbuildings.core.gen.v1.GeneratorV1.GEN_VERSION;

		/** Quarter (mode) and density (max) over the tile's urban lattice columns, as the skeleton's summary computes them. */
		DistrictInfo info() {
			int[] kinds = new int[QuarterKind.values().length];
			int max = 0, count = 0;
			for (int z = Math.floorDiv(tile.minZ() + 31, 32) * 32; z < tile.maxZ(); z += 32) {
				for (int x = Math.floorDiv(tile.minX() + 31, 32) * 32; x < tile.maxX(); x += 32) {
					if (!den.urban(x, z)) continue;
					int d = den.at(x, z);
					count++;
					max = Math.max(max, d);
					kinds[qf.at(x, z, d).ordinal()]++;
				}
			}
			int q = 0;
			for (int k = 1; k < kinds.length; k++) if (kinds[k] > kinds[q]) q = k;
			if (count == 0) q = QuarterKind.OUTSKIRTS.ordinal();
			return new DistrictInfo(id, ix, iz, tile.minX(), tile.minZ(), tile.widthX(), tile.widthZ(), q, max);
		}
	}
}
