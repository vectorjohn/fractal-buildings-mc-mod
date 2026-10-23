package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.probe.*;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import org.junit.jupiter.api.Test;

class BedPlacementTest {
	static boolean air(WorldPlanner w, PieceRef r, int x, int y, int z) {
		boolean[] a = {false};
		w.raster(r, new Box3(x, y, z, 1, 1, 1), (cx, cy, cz, role, id, s) -> a[0] = role == Role.AIR);
		return a[0];
	}
	@Test void everyBeddedBuildingKeepsABedWhenBedsMayCrossChunkEdges() {
		int bedded = 0, none = 0, noneOld = 0;
		for (long seed = 1; seed <= 30; seed++) {
			WorldProbes pr = WorldProbes.of(SyntheticTerrain.flat(70), SyntheticBiomes.constant("minecraft:plains"));
			WorldPlanner w = new WorldPlanner(seed, WorldConfig.defaults(), pr, null);
			var n = w.nearest(0, 0, Tier.HAMLET, 2);
			if (n.isEmpty()) continue;
			var c = n.get().site().centre();
			DistrictGrid.Tile t = w.grid().tileAt(c.x(), c.z());
			if (w.tile(t.ix(), t.iz()).isEmpty()) continue;
			for (BuildingPiece b : w.buildings(t.ix(), t.iz())) {
				int bd = 0, ok = 0, okOld = 0;
				for (var h : WorldHooks.of(b)) if (h.kind().equals("bed")) {
					bd++;
					boolean a = air(w, b.ref(), h.x(), h.y(), h.z()) && air(w, b.ref(), h.headX(), h.y(), h.headZ());
					if (a) ok++;
					if (a && h.bedInOneChunk()) okOld++;
				}
				if (bd == 0) continue;
				bedded++;
				if (ok == 0) none++;
				if (okOld == 0) noneOld++;
			}
		}
		org.junit.jupiter.api.Assertions.assertTrue(bedded > 300, "bedded " + bedded);
		org.junit.jupiter.api.Assertions.assertEquals(0, none, "buildings with bed hooks but no placeable bed (old one-chunk rule: " + noneOld + ")");
	}
}
