package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.DistrictScenes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the planner hands out for a tile obeys the invariants the district scene is held to, and stays inside its tile: buildings
 * (air strip over the street included) and ground pieces never touch another tile's columns, so two tiles' pieces never overlap.
 */
class WorldPlannerInvariantTest {
	@Test
	void sampledTilesOfEveryGateWindowPassTheInvariants() {
		int tiles = 0, buildings = 0, grounds = 0;
		for (GateWorld g : GateWorld.all()) {
			WorldPlanner w = g.planner(WorldPlanner.Caches.DEFAULT, null);
			int n = 0;
			for (TileInfo info : WorldPlannerParityTest.planned(w, g).values()) {
				List<BuildingPiece> bs = w.buildings(info.ix(), info.iz());
				List<GroundPiece> gs = w.ground(info.ix(), info.iz());
				DistrictGrid.Tile t = info.tile();
				// containment holds for every tile
				for (BuildingPiece b : bs) {
					Box3 x = b.box();
					assertTrue(x.x() >= t.minX() && x.maxX() <= t.maxX() && x.z() >= t.minZ() && x.maxZ() <= t.maxZ(), g.name() + " " + info.ix() + "," + info.iz() + " building " + b.brief().id() + " box " + x + " leaves the tile " + t.rect());
				}
				for (GroundPiece p : gs) {
					for (Box b : p.boxes()) {
						Box3 x = b.box();
						assertTrue(x.x() >= t.minX() && x.maxX() <= t.maxX() && x.z() >= t.minZ() && x.maxZ() <= t.maxZ(), "ground outside the tile");
						assertTrue(Math.floorDiv(x.x(), 16) == p.chunkX() && Math.floorDiv(x.maxX() - 1, 16) == p.chunkX() && Math.floorDiv(x.z(), 16) == p.chunkZ() && Math.floorDiv(x.maxZ() - 1, 16) == p.chunkZ(), "ground outside its chunk");
					}
				}
				if (bs.isEmpty() || n++ % 4 != 0) continue; // the invariant run reads the tile plus a margin: every fourth tile
				List<Lot> lots = new ArrayList<>();
				List<PlacedBuilding> pbs = new ArrayList<>();
				for (BuildingPiece b : bs) { lots.add(b.lot()); pbs.add(b.building()); }
				List<Box> ground = new ArrayList<>();
				for (GroundPiece p : gs) ground.addAll(p.boxes());
				TerrainPatch patch = TerrainPatch.sampleWithWater(g.probes().exact(), t.minX() - DistrictScenes.MARGIN, t.minZ() - DistrictScenes.MARGIN, t.widthX() + 2 * DistrictScenes.MARGIN, t.widthZ() + 2 * DistrictScenes.MARGIN);
				List<Violation> v = Invariants.check(new SceneDoc(GeneratorV1.GEN_VERSION, g.seed(), patch, lots, pbs, ground, List.of()), g.probes().exact());
				assertTrue(v.isEmpty(), g.name() + " " + info.ix() + "," + info.iz() + ": " + v.stream().limit(3).toList());
				tiles++;
				buildings += bs.size();
				grounds += gs.size();
			}
		}
		assertTrue(tiles >= 30, "tiles checked " + tiles);
		assertTrue(buildings > 300, "buildings " + buildings);
		assertTrue(grounds > 100, "ground pieces " + grounds);
	}
}
