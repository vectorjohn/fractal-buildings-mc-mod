package dev.fractalbuildings.mod.ordersim;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.terrain.Grading;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.GroundPiece;
import dev.fractalbuildings.core.world.PieceRef;
import dev.fractalbuildings.core.world.TileInfo;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Finds where in a planned settlement the ORDERSIM scenarios SC1 to SC3 should put their features (ORDERSIM S2). Works on the plan
 * and its raster only, never on hard-coded coordinates, so the spots follow the generator as it changes. Pure and deterministic:
 * every scan is in sorted tile, piece, chunk and column order, and ties go to the lowest (z, x). Core API only (no Minecraft).
 *
 * <p>The spots are geometry: they say where a feature would reach a read-the-world branch of our passes (a street's edge row on a
 * chunk line for {@code StreetAirFix}, a dug-in wall for the lake veto). Whether the branch really fires is counted by the sim (S3).
 */
public final class ScenarioFinder {
	/** The deep foundation a building's raster has below its box ({@code Pieces.FOUNDATION_BELOW}, which is {@code Grading.FILL_BELOW}). */
	public static final int FOUNDATION_BELOW = Grading.FILL_BELOW;
	/** How far from the site's centre the finder looks, in district tiles. */
	static final int TILE_RADIUS = 2;

	private final WorldPlanner planner;
	private final SettlementSite site;
	private final TerrainProbe ground;
	private final List<TileInfo> tiles = new ArrayList<>();
	private final List<BuildingPiece> buildings = new ArrayList<>();
	private final List<GroundPiece> grounds = new ArrayList<>();

	public ScenarioFinder(WorldPlanner planner, SettlementSite site, TerrainProbe ground) {
		this.planner = planner;
		this.site = site;
		this.ground = ground;
		var centre = planner.grid().tileAt(site.centre().x(), site.centre().z());
		for (int dz = -TILE_RADIUS; dz <= TILE_RADIUS; dz++) {
			for (int dx = -TILE_RADIUS; dx <= TILE_RADIUS; dx++) {
				planner.tile(centre.ix() + dx, centre.iz() + dz).filter(t -> t.settlementId().equals(site.id())).ifPresent(tiles::add);
			}
		}
		tiles.sort(Comparator.comparingInt(TileInfo::iz).thenComparingInt(TileInfo::ix));
		for (TileInfo t : tiles) {
			buildings.addAll(planner.buildings(t.ix(), t.iz()));
			grounds.addAll(planner.ground(t.ix(), t.iz()));
		}
	}

	public List<TileInfo> tiles() { return tiles; }

	/** A square window of chunks {@code [cx, cx + n) x [cz, cz + n)} and what it holds. */
	public record Window(int cx, int cz, int n, int buildings, int groundPieces, int buildingAirColumns, int chunkLineStreetColumns) {
		public Box3 blocks(int y, int sy) { return new Box3(cx * 16, y, cz * 16, n * 16, sy, n * 16); }
	}

	/**
	 * SC1: the n x n chunk window nearest the settlement's centre with the most pieces (buildings, then ground pieces), scanned over
	 * the chunks of the planned tiles. Score: building pieces whose box meets the window, plus ground pieces in it, ties to the one
	 * closest to the centre, then lowest (z, x).
	 */
	public Window densestWindow(int n) {
		int cminX = Integer.MAX_VALUE, cminZ = Integer.MAX_VALUE, cmaxX = Integer.MIN_VALUE, cmaxZ = Integer.MIN_VALUE;
		for (TileInfo t : tiles) {
			cminX = Math.min(cminX, Math.floorDiv(t.tile().minX(), 16));
			cminZ = Math.min(cminZ, Math.floorDiv(t.tile().minZ(), 16));
			cmaxX = Math.max(cmaxX, Math.floorDiv(t.tile().maxX() - 1, 16));
			cmaxZ = Math.max(cmaxZ, Math.floorDiv(t.tile().maxZ() - 1, 16));
		}
		if (tiles.isEmpty()) throw new IllegalStateException("no planned tile near " + site.id());
		Window best = null;
		long bestScore = -1, bestDist = Long.MAX_VALUE;
		for (int cz = cminZ; cz + n - 1 <= cmaxZ; cz++) {
			for (int cx = cminX; cx + n - 1 <= cmaxX; cx++) {
				int b = 0, g = 0;
				for (BuildingPiece p : buildings) if (meets(p.box(), cx, cz, n)) b++;
				for (GroundPiece p : grounds) if (p.chunkX() >= cx && p.chunkX() < cx + n && p.chunkZ() >= cz && p.chunkZ() < cz + n) g++;
				long score = b * 1000L + g;
				long dx = cx * 16L + n * 8L - site.centre().x(), dz = cz * 16L + n * 8L - site.centre().z();
				long dist = dx * dx + dz * dz;
				if (score > bestScore || (score == bestScore && dist < bestDist)) {
					bestScore = score; bestDist = dist;
					best = new Window(cx, cz, n, b, g, 0, 0);
				}
			}
		}
		if (best == null) throw new IllegalStateException("no " + n + "x" + n + " window inside the planned tiles");
		Columns cols = columns(best.cx(), best.cz(), n);
		int air = 0, line = 0;
		for (Column c : cols.byCol.values()) {
			if (c.buildingAir) air++;
			if (c.street && (c.x % 16 == 0 || c.x % 16 == 15 || Math.floorMod(c.z, 16) == 0 || Math.floorMod(c.z, 16) == 15)) line++;
		}
		return new Window(best.cx(), best.cz(), n, best.buildings(), best.groundPieces(), air, line);
	}

	private static boolean meets(Box3 b, int cx, int cz, int n) {
		return b.x() < (cx + n) * 16 && b.maxX() > cx * 16 && b.z() < (cz + n) * 16 && b.maxZ() > cz * 16;
	}

	// ---------------------------------------------------------------- lakes (SC2)

	public enum LakeKind { DUG_IN_WALL, UNDER_BOX, BELOW }

	/**
	 * A lake origin and why it was chosen. {@code vetoes} is what {@code planner.claimsNear(LakeVeto.box(origin))} answers (the finder
	 * checks it): true for DUG_IN_WALL and UNDER_BOX, false for the BELOW control.
	 */
	public record LakeSpot(LakeKind kind, int x, int y, int z, PieceRef building, boolean vetoes) {
		public int chunkX() { return Math.floorDiv(x, 16); }
		public int chunkZ() { return Math.floorDiv(z, 16); }
	}

	/** The lake grid plus one block, as {@code LakeVeto.box(origin)} (copied until the S0 seam lands; a test pins it to the mod's). */
	public static Box3 lakeBox(int x, int y, int z) { return new Box3(x - 9, y - 5, z - 9, 18, 10, 18); }

	/**
	 * SC2 (a): an origin beside a dug-in building (its box starts below the ground round it) whose grid, and not its origin, meets the
	 * building's wall, on a chunk different from the building's centre chunk where one exists. Null when no building is dug in.
	 */
	public LakeSpot lakeAtDugInWall() {
		LakeSpot fallback = null;
		for (BuildingPiece b : buildings) {
			Box3 bx = b.box();
			int gy = ground.floorY(bx.x() + bx.sx() / 2, bx.z() + bx.sz() / 2);
			if (bx.y() >= gy - 1) continue; // not dug in
			// an origin 10 blocks outside the west wall: the grid (x - 8 .. x + 7) reaches 2 blocks into it
			int ox = bx.x() - 6, oz = bx.z() + bx.sz() / 2, oy = bx.y() + 3;
			if (Math.floorDiv(ox, 16) == Math.floorDiv(bx.x() + bx.sx() / 2, 16)) { if (fallback == null) fallback = spot(LakeKind.DUG_IN_WALL, ox, oy, oz, b); continue; }
			return spot(LakeKind.DUG_IN_WALL, ox, oy, oz, b);
		}
		return fallback;
	}

	/**
	 * SC2 (b): an origin under a building's footprint, below its box and inside the deep foundation, so that only the lake's grid meets
	 * the deep foundation and not the box itself: the veto must use the rastered box. Null when no building has such a spot.
	 */
	public LakeSpot lakeUnderBox() {
		for (BuildingPiece b : buildings) {
			Box3 bx = b.box();
			int ox = bx.x() + bx.sx() / 2, oz = bx.z() + bx.sz() / 2;
			// lakeBox spans y - 5 .. y + 4: its top (y + 4) is below bx.y() and its bottom reaches into the foundation
			int oy = bx.y() - 6;
			Box3 lb = lakeBox(ox, oy, oz);
			if (lb.intersects(bx)) continue;
			if (!planner.claimsNear(lb)) continue;
			if (othersMeet(lb, b)) continue; // only this building's deep foundation: no ground piece and no other building is near (ORDERSIM S4b: the veto then depends on the rastered box alone)
			return new LakeSpot(LakeKind.UNDER_BOX, ox, oy, oz, b.ref(), true);
		}
		return null;
	}

	/** SC2 (c): below the same footprint, deep enough that nothing of ours is near: the control, not vetoed. */
	public LakeSpot lakeFarBelow() {
		for (BuildingPiece b : buildings) {
			Box3 bx = b.box();
			int ox = bx.x() + bx.sx() / 2, oz = bx.z() + bx.sz() / 2;
			for (int oy = bx.y() - FOUNDATION_BELOW - 6; oy >= bx.y() - 60 && oy > -50; oy -= 2) {
				if (!planner.claimsNear(lakeBox(ox, oy, oz))) return new LakeSpot(LakeKind.BELOW, ox, oy, oz, b.ref(), false);
			}
		}
		return null;
	}

	private boolean othersMeet(Box3 lb, BuildingPiece b) {
		for (BuildingPiece o : buildings) {
			if (o == b) continue;
			Box3 ob = o.box();
			if (new Box3(ob.x(), ob.y() - FOUNDATION_BELOW, ob.z(), ob.sx(), ob.sy() + FOUNDATION_BELOW, ob.sz()).intersects(lb)) return true;
		}
		return false;
	}

	private LakeSpot spot(LakeKind k, int x, int y, int z, BuildingPiece b) {
		return new LakeSpot(k, x, y, z, b.ref(), planner.claimsNear(lakeBox(x, y, z)));
	}

	// ---------------------------------------------------------------- trunks (SC3)

	public enum TrunkKind {
		/** The street is raised above the natural ground: a 2 x 2 base at the natural level is under the road, the log is blocked. */
		BURIED,
		/** The surface is at the natural level and the base cell above it is planned air: a trunk on the street surface. */
		ON_SURFACE,
		/** The street is cut into a hillside; the base sits at the top of the headroom clear of the cut. */
		CUT_HEADROOM
	}

	/**
	 * A 2 x 2 dark oak base {@code (x, z)} .. {@code (x + 1, z + 1)} at {@code y} that straddles a street's edge row on a chunk line.
	 * {@code streetX/Z} is the street column of the pair, {@code outX/Z} its non-street neighbour across the chunk line.
	 */
	public record TrunkSpot(TrunkKind kind, int x, int y, int z, int streetX, int streetZ, int outX, int outZ, int surfaceY, String surfaceBlock, int airTopY, int naturalY) {
		public int streetChunkX() { return Math.floorDiv(streetX, 16); }
		public int streetChunkZ() { return Math.floorDiv(streetZ, 16); }
		public int outChunkX() { return Math.floorDiv(outX, 16); }
		public int outChunkZ() { return Math.floorDiv(outZ, 16); }
		/** The cut height: how far the natural ground is above the street surface (0 or negative: no cut). */
		public int cut() { return naturalY - surfaceY; }
	}

	/**
	 * The first spot of each kind, scanning 6 x 6 chunk windows of the planned tiles from the densest 5 x 5 window outwards (nearest first,
	 * then lowest (z, x)) until every kind is found or the tiles are exhausted; an absent kind has no entry.
	 */
	public List<TrunkSpot> trunkSpots() { return trunkSpots(false); }

	/**
	 * As {@link #trunkSpots()}; with {@code fromNeighbour} only spots where the tree origin (the 2 x 2 base minimum corner, which decides the chunk
	 * that grows it) is in the non-street chunk, so a neighbour of the street chunk grows the tree (ORDERSIM S4b: the cases where the order matters).
	 */
	public List<TrunkSpot> trunkSpots(boolean fromNeighbour) {
		Window w = densestWindow(5);
		List<int[]> origins = new ArrayList<>();
		int minCx = Integer.MAX_VALUE, minCz = Integer.MAX_VALUE, maxCx = Integer.MIN_VALUE, maxCz = Integer.MIN_VALUE;
		for (TileInfo t : tiles) {
			minCx = Math.min(minCx, Math.floorDiv(t.tile().minX(), 16)); minCz = Math.min(minCz, Math.floorDiv(t.tile().minZ(), 16));
			maxCx = Math.max(maxCx, Math.floorDiv(t.tile().maxX() - 1, 16)); maxCz = Math.max(maxCz, Math.floorDiv(t.tile().maxZ() - 1, 16));
		}
		for (int cz = minCz; cz <= maxCz - 5; cz += 4) for (int cx = minCx; cx <= maxCx - 5; cx += 4) origins.add(new int[] {cx, cz});
		origins.add(0, new int[] {w.cx() - 1, w.cz() - 1});
		origins.subList(1, origins.size()).sort(Comparator.comparingLong((int[] o) -> (long) (o[0] - w.cx()) * (o[0] - w.cx()) + (long) (o[1] - w.cz()) * (o[1] - w.cz())).thenComparingInt(o -> o[1]).thenComparingInt(o -> o[0]));
		TreeMap<TrunkKind, TrunkSpot> out = new TreeMap<>();
		for (int[] o : origins) {
			if (out.size() == TrunkKind.values().length) break;
			scanTrunks(columns(o[0], o[1], 6), out, fromNeighbour);
		}
		return new ArrayList<>(out.values());
	}

	private void scanTrunks(Columns cols, TreeMap<TrunkKind, TrunkSpot> out, boolean fromNeighbour) {
		for (Column c : cols.byCol.values()) { // sorted by (z, x)
			if (!c.street || c.airTop == Integer.MIN_VALUE) continue;
			for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
				int ox = c.x + d[0], oz = c.z + d[1];
				if (!cols.inside(ox, oz)) continue;
				if (Math.floorDiv(ox, 16) == Math.floorDiv(c.x, 16) && Math.floorDiv(oz, 16) == Math.floorDiv(c.z, 16)) continue; // not a chunk line
				Column o = cols.get(ox, oz);
				if (o != null && (o.street || o.buildingAir || o.buildingSolid)) continue; // the other side must be open ground
				if (fromNeighbour && Math.min(c.x, ox) == c.x && Math.min(c.z, oz) == c.z) continue; // the base corner is the street column: the street chunk grows the tree
				int nat = ground.floorY(ox, oz);
				int natStreet = ground.floorY(c.x, c.z);
				TrunkKind kind;
				if (natStreet - c.surfaceY >= 3) kind = TrunkKind.CUT_HEADROOM;
				else if (c.surfaceY > natStreet + 1) kind = TrunkKind.BURIED;
				else if (Math.abs(c.surfaceY - natStreet) <= 1) kind = TrunkKind.ON_SURFACE;
				else continue;
				if (out.containsKey(kind)) continue;
				// the 2 x 2 base: the pair (street, out) along the axis, and the next row along the other axis
				int bx = Math.min(c.x, ox), bz = Math.min(c.z, oz);
				int by = kind == TrunkKind.CUT_HEADROOM ? natStreet : (kind == TrunkKind.BURIED ? nat : c.surfaceY + 1);
				out.put(kind, new TrunkSpot(kind, bx, by, bz, c.x, c.z, ox, oz, c.surfaceY, c.surfaceBlock, c.airTop, natStreet));
			}
		}
	}

	public TrunkSpot trunkSpot(TrunkKind kind) {
		return trunkSpots().stream().filter(s -> s.kind() == kind).findFirst().orElse(null);
	}

	/** The first spot of {@code kind} whose tree is grown by the street chunk's neighbour; null when none. */
	public TrunkSpot trunkSpotFromNeighbour(TrunkKind kind) {
		return trunkSpots(true).stream().filter(s -> s.kind() == kind).findFirst().orElse(null);
	}

	/**
	 * SC3 (b'): a 2 x 2 base wholly on street columns, straddling a chunk line, grown from the lower chunk, with the street cut at least 3 below
	 * the (flat) natural ground under the base, so the dirt cell under the base is planned air and no log stays beside it once the trunk is
	 * removed: the SPILLDIRT third hit ({@code restoreBase} writing air). Null when none. Kind CUT_HEADROOM; {@code out} is the base cell
	 * across the chunk line, which is a street column too.
	 */
	public TrunkSpot trunkSpotInStreet() {
		Window w = densestWindow(5);
		for (int dz = -6; dz <= 6; dz += 3) {
			for (int dx = -6; dx <= 6; dx += 3) {
				Columns cols = columns(w.cx() + dx, w.cz() + dz, 4);
				for (Column c : cols.byCol.values()) {
					boolean lineX = Math.floorMod(c.x, 16) == 15, lineZ = Math.floorMod(c.z, 16) == 15;
					if (!(lineX || lineZ) || !c.street) continue;
					int y = ground.floorY(c.x, c.z);
					boolean ok = true;
					for (int ez = 0; ez < 2 && ok; ez++) for (int ex = 0; ex < 2 && ok; ex++) {
						Column q = cols.get(c.x + ex, c.z + ez);
						ok = q != null && q.street && !q.buildingAir && !q.buildingSolid && ground.floorY(c.x + ex, c.z + ez) == y && q.airTop >= y && q.surfaceY <= y - 2;
					}
					if (!ok) continue;
					int ox = lineX ? c.x + 1 : c.x, oz = lineZ ? c.z + 1 : c.z;
					return new TrunkSpot(TrunkKind.CUT_HEADROOM, c.x, y, c.z, ox, oz, c.x, c.z, c.surfaceY, c.surfaceBlock, c.airTop, y);
				}
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- columns from the raster

	/** One world column of the plan: what the pieces' raster says about it. */
	static final class Column {
		final int x, z;
		boolean street, buildingAir, buildingSolid;
		int surfaceY = Integer.MIN_VALUE, airTop = Integer.MIN_VALUE;
		String surfaceBlock;
		Column(int x, int z) { this.x = x; this.z = z; }
	}

	static final class Columns {
		final TreeMap<Long, Column> byCol = new TreeMap<>();
		final int x0, z0, x1, z1; // the window, blocks, max exclusive
		Columns(int cx, int cz, int n) { x0 = cx * 16; z0 = cz * 16; x1 = x0 + n * 16; z1 = z0 + n * 16; }
		boolean inside(int x, int z) { return x >= x0 && x < x1 && z >= z0 && z < z1; }
		static long key(int x, int z) { return ((long) (z + (1 << 30)) << 31) | (long) (x + (1 << 30)); } // sorts by z, then x
		Column get(int x, int z) { return byCol.get(key(x, z)); }
		Column at(int x, int z) { return byCol.computeIfAbsent(key(x, z), k -> new Column(x, z)); }
	}

	/**
	 * Rasters every piece that meets the n x n chunk window (a ground piece's cells give the street surface, its AIR the headroom; a
	 * building's cells give its air and solid columns) into a per-column summary. Cells come from {@code planner.raster}, the same
	 * reference the sim's checks use.
	 */
	Columns columns(int cx, int cz, int n) {
		Columns cols = new Columns(cx, cz, n);
		Box3 clip = new Box3(cx * 16, -64, cz * 16, n * 16, 400, n * 16);
		for (GroundPiece g : grounds) {
			if (g.chunkX() < cx || g.chunkX() >= cx + n || g.chunkZ() < cz || g.chunkZ() >= cz + n) continue;
			planner.raster(g.ref(), clip, (x, y, z, role, id, shape) -> {
				Column c = cols.at(x, z);
				if (role == Role.AIR) { c.airTop = Math.max(c.airTop, y); return; }
				if (role.isCarriageway() || role == Role.PAVING) {
					c.street = true;
					if (y >= c.surfaceY) { c.surfaceY = y; c.surfaceBlock = id; }
				}
			});
		}
		for (BuildingPiece b : buildings) {
			if (!meets(b.box(), cx, cz, n)) continue;
			planner.raster(b.ref(), clip, (x, y, z, role, id, shape) -> {
				Column c = cols.at(x, z);
				if (role == Role.AIR) c.buildingAir = true; else c.buildingSolid = true;
			});
		}
		// a column the raster gave air but no road surface is not a street column; an airTop without a surface is cleared headroom of a cut
		for (Column c : cols.byCol.values()) if (!c.street) c.airTop = Integer.MIN_VALUE;
		return cols;
	}
}
