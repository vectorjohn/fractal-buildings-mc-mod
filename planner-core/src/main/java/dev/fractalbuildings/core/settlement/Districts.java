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
	static final int STREET = 1, PLAZA = 2, LOT = 4, RESERVED = 8, WET = 16, SKEL = 32;
	/** Densest rows only get whole-block lots. */
	static final int BLOCK_DENSITY = 70;

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

	/** Plans one tile, or nothing if another settlement owns it. */
	public static Optional<Detail> plan(SkeletonSource src, int ix, int iz, TerrainProbe exact) {
		if (!owns(src, ix, iz)) return Optional.empty();
		return Optional.of(new Build(src, ix, iz, exact).run());
	}

	/** Spacing of the cut lanes along a 45-degree run, and the shortest run that gets any. */
	static final int AVE_GAP_MIN = 20, AVE_GAP_VAR = 17, AVE_MIN_RUN = 20;
	/** Longest cut lane, and the shortest that is kept (a lane needs a straight run of lot-width on each side). */
	static final int AVE_LANE_MAX = 56, AVE_LANE_MIN = 14;

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

		Build(SkeletonSource src, int ix, int iz, TerrainProbe exact) {
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
			this.qf = src.quarters();
			this.seed = src.seed();
			this.tier = src.site().tier();
			this.rx0 = tile.minX() - PAD;
			this.rz0 = tile.minZ() - PAD;
			this.rw = tile.widthX() + 2 * PAD;
			this.rd = tile.widthZ() + 2 * PAD;
			this.flags = new byte[rw * rd];
			this.owner = new short[rw * rd];
			markWater();
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

		boolean inR(int x, int z) { return x >= rx0 && z >= rz0 && x < rx0 + rw && z < rz0 + rd; }

		int fg(int x, int z) { return inR(x, z) ? flags[(z - rz0) * rw + (x - rx0)] : 0; }

		void or(int x, int z, int f) { if (inR(x, z)) flags[(z - rz0) * rw + (x - rx0)] |= (byte) f; }

		/** Free for a lot: inside the tile and nothing on it. */
		boolean free(int x, int z) { return tr.contains(x, z) && (fg(x, z) & (STREET | PLAZA | LOT | RESERVED | WET)) == 0; }

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
				if (!l.id().contains(WallRing.MARK) && !l.id().equals(nid)) lots.set(i, new LotBrief(nid, l.x(), l.z(), l.w(), l.d(), l.front(), l.type(), l.style(), l.minStoreys(), l.maxStoreys(), l.streets(), l.airRights(), l.frontY(), l.density(), l.status()));
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
				lots.set(pick, new LotBrief(o.id(), o.x(), o.z(), o.w(), o.d(), o.front(), type, o.style(), lim.minStoreys(), lim.maxStoreys(), o.streets(), o.airRights(), o.frontY(), o.density(), o.status()));
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
				Optional<Grading.PlazaGrade> g = dry(r) ? Grading.plaza(r, exact) : Optional.empty();
				if (g.isEmpty()) {
					// Exact terrain disagrees with the coarse probe the skeleton was placed on: search nearby for a spot that grades,
					// within NEAR_RINGS full size then shrunk (never below MIN_SQUARE), then the same out to SHIFT_RINGS; nearest shifts first. A first round wants dry ground HALL_ROOM blocks all round (room for the hall and temple facing it), a second round takes any grading spot.
					Rect from = r;
					search:
					for (int room = l.kind() == LandmarkKind.SQUARE ? HALL_ROOM : 0; room >= 0; room -= HALL_ROOM)
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

		void wallLots() {
			Cell centreCell = CellGeometry.owner(src.view().field(), src.site().centre().x(), src.site().centre().z());
			if (!src.site().cells().contains(centreCell)) return;
			CellSkeleton sk = src.skeleton(centreCell);
			for (Wall w : sk.walls()) {
				List<Pt> gates = new ArrayList<>();
				for (Gate g : sk.gates()) if (g.kind() == GateKind.WALL) gates.add(new Pt(g.x(), g.z()));
				List<WallRing.Piece> pieces = WallRing.pieces(w, gates, grid, seed);
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
					lots.add(new LotBrief(id + WallRing.MARK + k, r.x(), r.z(), r.w(), r.d(), p.front(), p.type(), StyleLibrary.AUTO, lim.minStoreys(), lim.maxStoreys(), 1, 0, Brief.NO_FRONT_Y, dens, LotBrief.BUILD));
					lotAir.add(0);
					paintRect(r, LOT | RESERVED);
					reserved.addAll(WallRing.blocked(p));
					wallBlocked.addAll(WallRing.blocked(p));
				}
			}
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
				int along = Math.max(alongX ? r.w() : r.d(), lim.minLotWidth()), deep = Math.max(alongX ? r.d() : r.w(), lim.minLotDepth());
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
			for (int z = r.z(); z < r.z() + r.d(); z++) for (int x = r.x(); x < r.x() + r.w(); x++) if ((fg(x, z) & (STREET | PLAZA | LOT)) != 0) return false;
			return true;
		}

		boolean rectFree(Rect r, boolean ignoreReserved) {
			int mask = STREET | PLAZA | LOT | WET | (ignoreReserved ? 0 : RESERVED);
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
		void addPieces(String base, RoadKind kind, int width, List<int[]> cols, boolean fixed) {
			int n = 0;
			for (Raw r : pieces(cols, width, kind, fixed)) {
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
		List<Raw> pieces(List<int[]> cols, int width, RoadKind kind, boolean fixed) {
			List<Raw> out = new ArrayList<>();
			Grading.RoadGrade wg = null;
			for (List<int[]> run0 : StreetCols.runs(cols, c -> keep(c) && !hitsReserved(c, width))) {
				if (wg == null) wg = Grading.profile(lattice(StreetCols.vertices(cols)), memo);
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
					r.wholeY[i] = wg.profile().y()[w];
					for (Grading.Steep st : wg.steep()) if (w >= st.from() && w <= st.to()) r.steep[i] = true;
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
			for (Road r : nearbyRoads()) addPieces(r.id(), r.kind(), r.width(), StreetCols.columns(r.points()), true);
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
					addPieces(Ids.arterial(s.axis(), s.line(), s.from()), k, art.width(), StreetCols.columns(art.points(s, s.from(), s.to())), true);
					continue;
				}
				if (rules.lattice()) {
					addPieces("b" + s.axis() + Ids.n(s.line()) + "_" + Ids.n(s.from()), RoadKind.STREET, StreetRules.BORDER_WIDTH, StreetCols.columns(art.points(s, s.from(), s.to())), true);
					continue;
				}
				for (List<Pt> run : nodeRuns(s.from(), s.to(), (a, b) -> StreetRules.borderEdgeOk(exact, Arterials.ax(s, a), Arterials.az(s, a), Arterials.ax(s, b), Arterials.az(s, b)), s)) {
					addPieces("b" + s.axis() + Ids.n(s.line()) + "_" + Ids.n(axisCoord(s, run.get(0))), RoadKind.STREET, StreetRules.BORDER_WIDTH, StreetCols.columns(run), true);
				}
			}
		}

		/**
		 * The column lies within {@code r} blocks of a border of its tile where the street lattice has no street. Grid lines and quays stop
		 * short of such a border (neither tile may draw a piece that crosses it, and the piece would reach no street there).
		 */
		boolean nearAbsentBorder(int x, int z, int r) {
			if (!rules.lattice()) return false;
			DistrictGrid.Tile t = grid.tileAt(x, z);
			for (DistrictGrid.Segment s : grid.segments(t.ix(), t.iz())) {
				int across = s.axis() == DistrictGrid.AXIS_X ? x : z, along = s.axis() == DistrictGrid.AXIS_X ? z : x;
				if (Math.abs(across - s.fixed()) <= r && along >= s.from() - r && along <= s.to() + r && rules.borderKind(s) == null) return true;
			}
			return false;
		}

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
		/** How far beyond the tile a grid line's runs are followed (a multiple of {@link StreetRules#NODE}). */
		static final int GRID_REACH = 256;

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
					for (List<Pt> run : nodeRuns(lo, hi, (a, b) -> rules.gridEdgeOk(memo, Arterials.ax(s, a), Arterials.az(s, a), Arterials.ax(s, b), Arterials.az(s, b)) && !nearAbsentBorder(Arterials.ax(s, a), Arterials.az(s, a), 0) && !nearAbsentBorder(Arterials.ax(s, b), Arterials.az(s, b), 0), s)) {
						addPieces("s" + (vertical ? "x" : "z") + Ids.n(pos) + "_" + Ids.n(axisCoord(s, run.get(0))), RoadKind.STREET, StreetRules.GRID_WIDTH, StreetCols.columns(run), false);
					}
				}
			}
		}

		// ---- grading and connectivity of the base streets

		boolean touchesBorder(Raw r) {
			for (int[] c : r.cols) if (c[0] <= tile.minX() || c[0] >= tile.maxX() - 1 || c[1] <= tile.minZ() || c[1] >= tile.maxZ() - 1) return true;
			return false;
		}

		/** Grades a street (the profile the scene will compute from the same columns); false when it needs more cut or fill than allowed. */
		boolean grade(Raw r) {
			int n = r.cols.size();
			int[] y;
			boolean ok = true;
			if (r.wholeY != null) {
				y = r.wholeY;
				for (int i = 0; i < n; i++) ok &= !r.steep[i];
			} else {
				Grading.RoadGrade g = Grading.profile(lattice(StreetCols.vertices(r.cols)), memo);
				if (g.profile().size() != n) throw new IllegalStateException("street " + r.id + ": profile has " + g.profile().size() + " columns for " + n);
				y = g.profile().y();
				ok = g.ok();
			}
			r.street = street(r, y);
			return ok;
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
					Raw link = quayLink("sdk" + subLink, run, base);
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
		boolean hasWaterfront() {
			for (int z = Math.floorDiv(tile.minZ(), 32) * 32; z <= tile.maxZ() + 32; z += 32) {
				for (int x = Math.floorDiv(tile.minX(), 32) * 32; x <= tile.maxX() + 32; x += 32) {
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
			return rise >= 0 && rise <= Quays.MAX_RISE;
		}

		/**
		 * Promenades along the water of the waterfront quarter: the marching-squares contour of "water within 8 blocks" on the street
		 * lattice, clipped to the dry columns on a low bank, each piece joined to the nearest road or street by a lane (so lots on it
		 * can be reached). Pointwise, so neighbouring tiles list the same promenade near their border.
		 */
		void quays() {
			if (!hasWaterfront()) return;
			int pad = Quays.MIN_LEN + MARGIN + 2 * Quays.STEP; // a run seen through the window is as long as the whole run, or already past the minimum
			List<Raw> base = new ArrayList<>(raws);
			int cn = 0;
			for (List<Pt> chain : Quays.contour(this::nearWater, tile.minX() - pad, tile.minZ() - pad, tile.maxX() + pad, tile.maxZ() + pad)) {
				List<int[]> cols = StreetCols.columns(chain);
				if (chain.get(0).equals(chain.get(chain.size() - 1))) cols = breakRing(cols);
				for (List<int[]> whole : StreetCols.runs(cols, this::quayOk)) {
					if (whole.size() < Quays.MIN_LEN) continue; // judged before clipping, so the neighbouring tile decides alike
					for (Raw piece : pieces(whole, Quays.WIDTH, RoadKind.QUAY, false)) {
						List<int[]> run = piece.cols;
						Raw q = new Raw("quay" + Ids.n(run.get(0)[0]) + "_" + Ids.n(run.get(0)[1]), RoadKind.QUAY, Quays.WIDTH, run, false).withWhole(piece);
						grade(q); // a steep stretch is the road's to bear (a promenade is never dropped: both tiles must list it)
						raws.add(q);
						paint(q);
						Raw link = quayLink("k" + cn, run, base);
						if (link != null) { cn++; raws.add(link); paint(link); }
					}
				}
			}
		}

		/** A closed ring of columns, rotated to start just after a column that is no quay, so a run through the ring's start is not split (the start depends on the window). */
		List<int[]> breakRing(List<int[]> ring) {
			List<int[]> cols = new ArrayList<>(ring.subList(0, ring.size() - 1));
			for (int k = 0; k < cols.size(); k++) {
				if (quayOk(cols.get(k))) continue;
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
			List<LatticePath.Pt> path = LatticePath.find(new LatticePath.Pt(qx, qz), new LatticePath.Pt(gx, gz), laneCost(qx, qz, 8), b, m, -1, 20_000, false);
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
							path = LatticePath.find(new LatticePath.Pt(qx, qz), new LatticePath.Pt(gx, gz), laneCost(qx, qz, 0), b, StreetRules.NODE, -1, 20_000, false);
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
					if (stub != null && !hitsWall(stub)) { grade(stub); raws.add(stub); paint(stub); }
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
								if ((fg(fx, fz) & (PLAZA | LOT | RESERVED | WET)) != 0 || !inR(fx, fz)) bad = true;
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
					List<LatticePath.Pt> path = LatticePath.find(new LatticePath.Pt(qx, qz), new LatticePath.Pt(gx, gz), laneCost(qx, qz, 0), b, m, -1, 40_000, false);
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
					List<LatticePath.Pt> path = LatticePath.find(new LatticePath.Pt(c[0], c[1]), new LatticePath.Pt(gx, gz), laneCost(c[0], c[1], 14), b, StreetRules.NODE, -1, 20_000, false);
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
					if ((fg(fx, fz) & (PLAZA | LOT | RESERVED | WET)) != 0 || !inR(fx, fz) || wt(fx, fz)) { k = AVE_LANE_MAX + 1; break; }
				}
				if (k > AVE_LANE_MAX) break;
			}
			if (end < 0) { // no street to run into: a dead-end lane of a hashed length that stopped at the limit or short of an obstacle
				int len = 0;
				for (int t = start + 1; t <= AVE_LANE_MAX; t++) {
					int x = c[0] + ux * t, z = c[1] + uz * t;
					boolean bad = !inner.contains(x, z) || !den.urban(x, z) || (fg(x, z) & (STREET | PLAZA | LOT | RESERVED | WET)) != 0 || wt(x, z);
					for (int o = -h; o <= h && !bad; o++) {
						int fx = x + (ux == 0 ? o : 0), fz = z + (uz == 0 ? o : 0);
						if ((fg(fx, fz) & (PLAZA | LOT | RESERVED | WET)) != 0 || !inR(fx, fz) || wt(fx, fz)) bad = true;
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
			int lotX = f.x(u, 0), lotZ = f.z(u, 0);
			int relief = 0;
			for (int attempt = 0; attempt < 3; attempt++) {
				Optional<ClassTable.Pick> p = ClassTable.pick(seed, lotX, lotZ, dens, q, relief, Math.min(wa, 100), dmaxFirst);
				if (p.isEmpty()) return null;
				String type = p.get().type();
				TypeLimits lim = Types.get(type).limits();
				if (lim.allStreets()) return null;
				int[] dims = dims(f, dep, u, wa, lim, Hash64.hash(seed, "lot-size", lotX, lotZ));
				if (dims == null) return null;
				Rect r = f.rect(u, dims[0], dims[1]);
				if (!dry(r)) return null;
				int rel = relief(r);
				if (!SpecPolicyV1.reliefOk(lim, rel, r.w(), r.d())) {
					if (rel == relief) return null;
					relief = rel;
					continue;
				}
				return place(f, street, r, u, dims, type, p.get(), dens);
			}
			return null;
		}

		/** Width along the street and depth for a type: a test lot that fits, widened to absorb a leftover too small for a lot. */
		int[] dims(Frame f, int[] dep, int u, int wa, TypeLimits lim, long h) {
			int minW = lim.minLotWidth(), minD = lim.minLotDepth();
			List<int[]> cands = new ArrayList<>();
			for (int[] t : lim.testLots()) {
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
			w += Hash64.below(Hash64.hash(h, "k", 1), Math.min(4, wa - w) + 1);
			if (wa - w < MIN_W) w = Math.min(wa, w + MIN_W);
			w = Math.min(w, wa);
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
				out.add(new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), l.type(), l.style(), l.minStoreys(), l.maxStoreys(), mask, lotAir.get(i), fy, l.density(), l.status()));
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
