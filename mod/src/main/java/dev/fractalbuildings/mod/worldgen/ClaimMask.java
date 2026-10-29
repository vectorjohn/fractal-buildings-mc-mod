package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.road.RoadSurface;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.GroundPiece;
import dev.fractalbuildings.core.world.WorldPlanner;
import java.util.List;
import java.util.TreeSet;

/**
 * ROADLEAFORDER: the cells of a box that our pieces and roads write, from the plan alone: {@link #AIR} (a clear: a room, a street's or a road's
 * headroom, the cut over a road) or {@link #SOLID} (a wall, a street surface, a road surface and its fill). Whether a piece of another chunk
 * has written them yet depends on chunk order; this answer does not, so {@link FoliageClear#own} decides with it what our cuts leave of a
 * tree before they happen.
 *
 * <p>Buildings and ground pieces: their raster. Roads: {@link RoadSurface#plan} of each chunk over the natural ground (the exact probe), with
 * the road feature's clear ({@code RoadFeature.draw}): fill and surface solid, air from the surface up to the higher of the headroom and one over
 * the old ground, and one cell more (the snow layer the draw removes there). The world's ground (what the draw reads) can differ from the
 * natural one where a feature changed it; there the mask is off by those cells. Village joins ({@code VillageRoads}) are not in it.
 */
public final class ClaimMask {
	public static final byte NONE = 0, AIR = 1, SOLID = 2;

	private final Box3 box;
	private final byte[] role;

	private ClaimMask(Box3 box) {
		this.box = box;
		this.role = new byte[box.sx() * box.sy() * box.sz()];
	}

	public Box3 box() { return box; }

	/** The role of a cell; {@link #NONE} outside the box. */
	public byte role(int x, int y, int z) {
		if (!box.contains(x, y, z)) return NONE;
		return role[idx(x, y, z)];
	}

	private int idx(int x, int y, int z) { return ((y - box.y()) * box.sz() + (z - box.z())) * box.sx() + (x - box.x()); }

	private void set(int x, int y, int z, byte r) {
		if (box.contains(x, y, z)) role[idx(x, y, z)] = r;
	}

	/** Natural ground for {@link RoadSurface#plan}: the exact probe's floor and water. */
	static RoadSurface.Ground natural(WorldPlanner planner) {
		return new RoadSurface.Ground() {
			@Override public int topY(int x, int z) { return planner.naturalFloorY(x, z) - 1; }
			@Override public boolean wet(int x, int z) { return planner.naturalWaterDepth(x, z) > 0; }
		};
	}

	/** The claims inside {@code box}. Pure in the planner. */
	public static ClaimMask of(WorldPlanner planner, Box3 box) {
		ClaimMask m = new ClaimMask(box);
		int cx0 = Math.floorDiv(box.x(), 16), cx1 = Math.floorDiv(box.maxX() - 1, 16), cz0 = Math.floorDiv(box.z(), 16), cz1 = Math.floorDiv(box.maxZ() - 1, 16);
		if (planner.roadsEnabled()) {
			RoadSurface.Ground ground = natural(planner);
			for (int cz = cz0; cz <= cz1; cz++) {
				for (int cx = cx0; cx <= cx1; cx++) {
					List<List<RoadNet.Col>> around = planner.roadColumnsAround(cx, cz);
					boolean any = false;
					for (List<RoadNet.Col> l : around) any |= !l.isEmpty();
					if (!any) continue;
					RoadSurface.Plan p = RoadSurface.plan(cx, cz, around, planner.districtColumns(cx, cz), ground);
					for (int i = 0; i < 256; i++) {
						if (p.kind[i] == RoadSurface.NONE) continue;
						int x = cx * 16 + (i & 15), z = cz * 16 + (i >> 4);
						if (x < box.x() || x >= box.maxX() || z < box.z() || z >= box.maxZ()) continue;
						int surf = p.surf[i], g = p.ground[i];
						boolean wet = ground.wet(x, z);
						int lo = surf > g && !wet ? g + 1 : surf;
						for (int y = Math.max(lo, box.y()); y <= Math.min(surf, box.maxY() - 1); y++) m.set(x, y, z, SOLID);
						int clearTo = Math.max(g + 1, surf + RoadSurface.HEADROOM) + 1;
						for (int y = Math.max(surf + 1, box.y()); y <= Math.min(clearTo, box.maxY() - 1); y++) m.set(x, y, z, AIR);
					}
				}
			}
		}
		// pieces: ground first, buildings over it (a building owns the cells its boxes touch)
		DistrictGrid g = planner.grid();
		TreeSet<Long> tiles = new TreeSet<>();
		boolean near = false;
		for (int cz = cz0; cz <= cz1 && !near; cz++) for (int cx = cx0; cx <= cx1 && !near; cx++) near = planner.districtsNear(cx, cz);
		if (near) { // the tiles the box meets (as SpillClear reads them)
			DistrictGrid.TileId ta = g.tileOf(box.x(), box.z()), tb = g.tileOf(box.maxX() - 1, box.maxZ() - 1);
			for (int iz = ta.iz(); iz <= tb.iz(); iz++) for (int ix = ta.ix(); ix <= tb.ix(); ix++) tiles.add(OwnWrites.key(ix, iz));
		}
		for (long t : tiles) {
			int ix = (int) (t >> 32), iz = (int) t;
			if (planner.tile(ix, iz).isEmpty()) continue;
			for (GroundPiece gp : planner.ground(ix, iz)) if (gp.box().intersects(box)) raster(planner, m, gp.ref(), gp.box());
		}
		for (long t : tiles) {
			int ix = (int) (t >> 32), iz = (int) t;
			if (planner.tile(ix, iz).isEmpty()) continue;
			for (BuildingPiece b : planner.buildings(ix, iz)) {
				Box3 pb = b.box();
				if (pb.x() >= box.maxX() || pb.maxX() <= box.x() || pb.z() >= box.maxZ() || pb.maxZ() <= box.z()) continue;
				raster(planner, m, b.ref(), pb);
			}
		}
		return m;
	}

	private static void raster(WorldPlanner planner, ClaimMask m, dev.fractalbuildings.core.world.PieceRef ref, Box3 pieceBox) {
		Box3 box = m.box;
		int x0 = Math.max(box.x(), pieceBox.x()), x1 = Math.min(box.maxX(), pieceBox.maxX()), z0 = Math.max(box.z(), pieceBox.z()), z1 = Math.min(box.maxZ(), pieceBox.maxZ());
		if (x0 >= x1 || z0 >= z1) return;
		try {
			planner.raster(ref, new Box3(x0, box.y(), z0, x1 - x0, box.sy(), z1 - z0), (x, y, z, role, blockId, shape) -> m.set(x, y, z, role == Role.AIR ? AIR : SOLID));
		} catch (IllegalArgumentException e) {
			// the piece no longer exists in the tile (old save): no claim
		}
	}
}
