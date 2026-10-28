package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import org.junit.jupiter.api.Test;

/** B3: every cell a building owns lies inside the piece box the mod gives its structure piece (a chunk is only post-processed for pieces whose box reaches it). */
class BuildingCellExtentTest {
	static final Box3 ALL = new Box3(-100_000, -64, -100_000, 200_000, 400, 200_000);

	@Test
	void buildingCellsStayInsideTheirPieceBoxAndTheirTile() {
		FixtureWorldPlanner w = FixtureWorldPlanner.sample();
		int[] n = {0, 0, 0};
		for (int ix = 0; ix < 2; ix++) for (int iz = 0; iz < 2; iz++) {
			DistrictGrid.Tile t = w.tile(ix, iz).orElseThrow().tile();
			for (BuildingPiece b : w.buildings(ix, iz)) {
				Box3 box = b.box();
				w.raster(b.ref(), ALL, (x, y, z, role, id, shape) -> {
					n[0]++;
					if (x < box.x() || x >= box.maxX() || z < box.z() || z >= box.maxZ()) n[1]++;
					if (!t.contains(x, z)) n[2]++;
				});
			}
		}
		assertTrue(n[0] > 5000);
		assertEquals(0, n[1], "cells outside the piece box (XZ)");
		assertEquals(0, n[2], "cells outside the tile");
	}

	@Test
	void sameOnEveryGateWindow() {
		int[] n = {0, 0, 0};
		for (GateWorld g : GateWorld.all()) {
			WorldPlanner w = g.sharedPlanner();
			int k = 0;
			for (TileInfo info : WorldPlannerParityTest.planned(w, g).values()) {
				if (k++ % 3 != 0) continue;
				DistrictGrid.Tile t = info.tile();
				for (BuildingPiece b : w.buildings(info.ix(), info.iz())) {
					Box3 box = b.box();
					w.raster(b.ref(), ALL, (x, y, z, role, id, shape) -> {
						n[0]++;
						if (x < box.x() || x >= box.maxX() || z < box.z() || z >= box.maxZ()) n[1]++;
						if (!t.contains(x, z)) n[2]++;
					});
				}
			}
		}
		assertTrue(n[0] > 5000);
		assertEquals(0, n[1], "cells outside the piece box (XZ)");
		assertEquals(0, n[2], "cells outside the tile");
	}

	/** The street headroom air reaches frontY + 3, where a jetty may start (Lot.AIR_CLEARANCE): wherever building and ground meet (jetty cells over a street, foundations, doors) the building owns the cell and the ground yields. */
	@Test
	void groundYieldsEveryBuildingCellToTheBuilding() {
		int strip = 0;
		for (GateWorld g : GateWorld.all()) {
			WorldPlanner w = g.sharedPlanner();
			int k = 0;
			for (TileInfo info : WorldPlannerParityTest.planned(w, g).values()) {
				if (k++ % 2 != 0) continue;
				java.util.Set<String> building = new java.util.HashSet<>();
				int[] n = {0};
				for (BuildingPiece b : w.buildings(info.ix(), info.iz())) {
					var lot = b.lot();
					w.raster(b.ref(), ALL, (x, y, z, role, id, shape) -> {
						building.add(x + "," + y + "," + z);
						if (!lot.containsColumn(x, z)) n[0]++;
					});
				}
				strip += n[0];
				for (GroundPiece gp : w.ground(info.ix(), info.iz())) {
					w.raster(gp.ref(), ALL, (x, y, z, role, id, shape) -> {
						assertFalse(building.contains(x + "," + y + "," + z), "ground and building both own " + x + "," + y + "," + z);
					});
				}
			}
		}
		System.out.println("[B3] cells outside lot columns (air strips) seen: " + strip);
	}

	/** B3: under every ground-floor column a foundation runs from the floor down to 12 blocks below the probed ground, so a live ground that the beardifier lowered leaves no gap. */
	@Test
	void everyGroundFloorColumnHasFoundationTwelveBelowTheProbedGround() {
		int cols = 0, deep = 0;
		for (GateWorld g : GateWorld.all()) {
			WorldPlanner w = g.sharedPlanner();
			int k = 0;
			for (TileInfo info : WorldPlannerParityTest.planned(w, g).values()) {
				if (k++ % 4 != 0) continue;
				for (BuildingPiece b : w.buildings(info.ix(), info.iz())) {
					java.util.Map<Long, java.util.TreeMap<Integer, dev.fractalbuildings.core.ir.Role>> byCol = new java.util.HashMap<>();
					int[] floorY = {Integer.MAX_VALUE};
					w.raster(b.ref(), ALL, (x, y, z, role, id, shape) -> {
						byCol.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), q -> new java.util.TreeMap<>()).put(y, role);
						if (role == dev.fractalbuildings.core.ir.Role.FLOOR) floorY[0] = Math.min(floorY[0], y);
					});
					if (floorY[0] == Integer.MAX_VALUE) continue;
					boolean[][] fp = dev.fractalbuildings.core.gen.v1.InstancePassV1.footprint(b.building().shell());
					for (var e : byCol.entrySet()) {
						var cells = e.getValue();
						if (cells.get(floorY[0]) != dev.fractalbuildings.core.ir.Role.FLOOR) continue;
						int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
						if (!b.lot().containsColumn(x, z)) continue;
						var pl = b.building().placement();
						int lx = pl.localX(x, z), lz = pl.localZ(x, z);
						if (lx < 0 || lz < 0 || lx >= fp.length || lz >= fp[0].length || !fp[lx][lz]) continue; // a raised wing or a porch on posts has no ground floor here
						int bottom = Math.min(g.probes().exact().floorY(x, z), floorY[0]) - 12;
						cols++;
						for (int y = bottom; y < floorY[0]; y++) {
							assertNotNull(cells.get(y), "cells " + cells + " no foundation at " + x + "," + y + "," + z + " of " + b.brief().id() + " (floor " + floorY[0] + ", probe " + g.probes().exact().floorY(x, z) + ")");
						}
						deep++;
					}
				}
			}
		}
		assertTrue(cols > 500, "columns checked " + cols);
		assertEquals(cols, deep);
	}
}
