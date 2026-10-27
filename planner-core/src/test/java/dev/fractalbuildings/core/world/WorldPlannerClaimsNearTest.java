package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.SettlementSite;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link WorldPlanner#claimsNear} (ORDERFAIL): the question the mod's lake veto asks. It must be true wherever a piece of ours owns a
 * cell (else a neighbour's lake writes lava into a building in some chunk orders) and a pure function of the box.
 */
class WorldPlannerClaimsNearTest {
	/** The lake grid the mod tests ({@code LakeVeto.box}): 16 x 8 x 16 grown by one. */
	static Box3 lake(int x, int y, int z) { return new Box3(x - 9, y - 5, z - 9, 18, 10, 18); }

	static WorldPlanner planner() { return WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT); }

	/** One planner for the class: planning the settlements is the slow part. */
	static final WorldPlanner W = planner();

	/** The planned tile at the centre of each settlement of {@link WorldPlannerRoadsTest#CELLS}. */
	static List<DistrictGrid.TileId> tiles(WorldPlanner w) {
		List<DistrictGrid.TileId> out = new ArrayList<>();
		for (int[] c : WorldPlannerRoadsTest.CELLS) {
			SettlementSite s = w.siteOf(new Cell(c[0], c[1])).orElseThrow();
			DistrictGrid.TileId id = w.grid().tileOf(s.centre().x(), s.centre().z());
			if (w.tile(id.ix(), id.iz()).isPresent()) out.add(id);
		}
		return out;
	}

	@Test
	void everyCellOfEveryPieceIsClaimed() {
		WorldPlanner w = W;
		int pieces = 0;
		for (DistrictGrid.TileId id : tiles(w)) {
			for (BuildingPiece b : w.buildings(id.ix(), id.iz())) {
				Box3 bb = b.box();
				assertTrue(w.claimsNear(new Box3(bb.x(), bb.y(), bb.z(), 1, 1, 1)), "building " + b.ref() + " min corner");
				assertTrue(w.claimsNear(new Box3(bb.maxX() - 1, bb.maxY() - 1, bb.maxZ() - 1, 1, 1, 1)), "building " + b.ref() + " max corner");
				pieces++;
			}
			for (GroundPiece g : w.ground(id.ix(), id.iz())) {
				for (var b : g.boxes()) assertTrue(w.claimsNear(new Box3(b.box().x(), b.box().y(), b.box().z(), 1, 1, 1)), "ground " + g.ref());
				pieces++;
			}
		}
		assertTrue(pieces > 20, "pieces checked: " + pieces);
	}

	/** Where it answers false, no piece of the tiles round the box rasters a single cell: a vetoed-free lake can never meet our cells. */
	@Test
	void aBoxItCallsFreeHoldsNoCellOfAnyPiece() {
		WorldPlanner w = W;
		int free = 0, claimed = 0;
		for (DistrictGrid.TileId id : tiles(w)) {
			DistrictGrid.Tile t = w.grid().tile(id.ix(), id.iz());
			List<BuildingPiece> bs = w.buildings(id.ix(), id.iz());
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (BuildingPiece b : bs) { lo = Math.min(lo, b.box().y()); hi = Math.max(hi, b.box().maxY()); }
			for (GroundPiece g : w.ground(id.ix(), id.iz())) { lo = Math.min(lo, g.box().y()); hi = Math.max(hi, g.box().maxY()); }
			if (lo > hi) continue;
			for (int z = t.minZ() + 9; z < t.maxZ() - 9; z += 23) {
				for (int x = t.minX() + 9; x < t.maxX() - 9; x += 23) {
					for (int y = lo - 12; y <= hi + 12; y += 9) {
						Box3 box = lake(x, y, z);
						if (w.claimsNear(box)) { claimed++; continue; }
						free++;
						int[] n = {0};
						StringBuilder who = new StringBuilder();
						for (BuildingPiece b : bs) w.raster(b.ref(), box, (cx, cy, cz, role, block, shape) -> { if (n[0]++ < 3) who.append(" building ").append(b.ref()).append(" box ").append(b.box()).append(" cell ").append(cx).append(",").append(cy).append(",").append(cz).append(" ").append(block); });
						for (GroundPiece g : w.ground(id.ix(), id.iz())) w.raster(g.ref(), box, (cx, cy, cz, role, block, shape) -> { if (n[0]++ < 3) who.append(" ground ").append(g.ref()).append(" chunk ").append(g.chunkX()).append(",").append(g.chunkZ()).append(" box ").append(g.box()).append(" cell ").append(cx).append(",").append(cy).append(",").append(cz).append(" ").append(block); });
						assertEquals(0, n[0], "cells of our pieces inside a box claimsNear calls free: " + box + who);
					}
				}
			}
		}
		assertTrue(free > 50 && claimed > 50, "both answers seen: free " + free + ", claimed " + claimed);
	}

	@Test
	void deepUnderADistrictIsFree() {
		WorldPlanner w = W;
		for (DistrictGrid.TileId id : tiles(w)) {
			DistrictGrid.Tile t = w.grid().tile(id.ix(), id.iz());
			int x = (t.minX() + t.maxX()) / 2, z = (t.minZ() + t.maxZ()) / 2;
			int lo = Integer.MAX_VALUE;
			for (BuildingPiece b : w.buildings(id.ix(), id.iz())) lo = Math.min(lo, b.box().y());
			for (GroundPiece g : w.ground(id.ix(), id.iz())) lo = Math.min(lo, g.box().y());
			assertTrue(lo > -40, "a piece reaches down to " + lo);
			// an underground lava lake far below the city stays (only the lakes that reach our pieces or a road's ground go)
			boolean road = false;
			for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) road |= !w.roadColumns(Math.floorDiv(x, 16) + dx, Math.floorDiv(z, 16) + dz).isEmpty();
			if (!road) assertFalse(w.claimsNear(lake(x, -55, z)), "lake at " + x + ",-55," + z);
		}
	}

	@Test
	void roadsClaimTheirGroundButNotTheDeepUnderground() {
		WorldPlanner w = W;
		RoadNet net = w.roads();
		int checked = 0;
		for (int[] c : WorldPlannerRoadsTest.CELLS) {
			RoadNet.Settlement s = net.settlement(new CellKey(c[0], c[1])).orElseThrow();
			SettlementSite site = w.siteOf(new Cell(c[0], c[1])).orElseThrow();
			for (WorldPlanner.Approach a : w.approachRoads(site, s)) {
				RoadNet.Col col = a.columns().get(a.columns().size() - 1); // the port end, outside the settlement
				assertTrue(w.claimsNear(lake(col.x(), col.y(), col.z())), "lake on the road at " + col);
				// beside the road, at its reach: the shoulder and terraces
				assertTrue(w.claimsNear(lake(col.x() + 9 + 13, col.y(), col.z())), "lake at the road's reach " + col);
				assertTrue(w.claimsNear(lake(col.x(), col.y() - WorldPlanner.ROAD_DEPTH - 3, col.z())), "lake just under the road's depth " + col);
				Box3 deep = lake(col.x(), col.y() - WorldPlanner.ROAD_DEPTH - 5 - 10, col.z());
				if (!districtNear(w, deep)) assertFalse(w.claimsNear(deep), "lake deep under the road " + col);
				checked++;
			}
		}
		assertTrue(checked >= 3, "approaches checked: " + checked);
	}

	private static boolean districtNear(WorldPlanner w, Box3 b) {
		for (int cz = Math.floorDiv(b.z(), 16); cz <= Math.floorDiv(b.maxZ() - 1, 16); cz++)
			for (int cx = Math.floorDiv(b.x(), 16); cx <= Math.floorDiv(b.maxX() - 1, 16); cx++) if (w.districtColumns(cx, cz) != null) return true;
		return false;
	}

	/** The same answers after an eviction and in the other order (the veto must not depend on what the caches hold). */
	@Test
	void answersDoNotDependOnCachesOrOrder() {
		WorldPlanner w = W;
		List<Box3> boxes = new ArrayList<>();
		for (DistrictGrid.TileId id : tiles(w).subList(0, 1)) {
			DistrictGrid.Tile t = w.grid().tile(id.ix(), id.iz());
			for (int z = t.minZ(); z < t.maxZ(); z += 37) for (int x = t.minX(); x < t.maxX(); x += 37) for (int y = 40; y <= 100; y += 20) boxes.add(lake(x, y, z));
		}
		List<Boolean> a = new ArrayList<>();
		for (Box3 b : boxes) a.add(w.claimsNear(b));
		assertTrue(a.contains(true) && a.contains(false), "both answers seen: " + a);
		// a cold planner asked in the other order, then the same planner after an eviction
		WorldPlanner fresh = planner();
		List<Boolean> b = new ArrayList<>();
		for (int i = boxes.size() - 1; i >= 0; i--) b.add(0, fresh.claimsNear(boxes.get(i)));
		assertEquals(a, b);
		fresh.evictAll();
		List<Boolean> c = new ArrayList<>();
		for (Box3 x : boxes) c.add(fresh.claimsNear(x));
		assertEquals(a, c);
	}
}
