package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** {@code WorldPlanner.raster}: the pieces of a tile own disjoint cells, and chunk-sized clips add up to the whole piece. */
class WorldPlannerRasterTest {
	static final Box3 ALL = new Box3(-100_000, -64, -100_000, 200_000, 400, 200_000);

	private static TreeMap<String, String> cells(WorldPlanner w, PieceRef ref, Box3 clip, TreeMap<String, String> into, Set<Role> roles) {
		w.raster(ref, clip, (x, y, z, role, id, shape) -> {
			String k = x + "," + y + "," + z;
			assertNull(into.put(k, ref + ":" + role + ":" + id + ":" + shape), "cell written twice: " + k);
			roles.add(role);
		});
		return into;
	}

	@Test
	void piecesOfATileOwnDisjointCellsAndChunkClipsPartitionThem() {
		FixtureWorldPlanner w = FixtureWorldPlanner.sample();
		TileInfo info = w.tile(0, 0).orElseThrow();
		DistrictGrid.Tile t = info.tile();
		TreeMap<String, String> whole = new TreeMap<>();
		Set<Role> roles = EnumSet.noneOf(Role.class);
		for (BuildingPiece b : w.buildings(0, 0)) cells(w, b.ref(), ALL, whole, roles);
		for (GroundPiece g : w.ground(0, 0)) cells(w, g.ref(), ALL, whole, roles);
		assertTrue(whole.size() > 5000, "cells " + whole.size());
		assertTrue(roles.containsAll(Set.of(Role.WALL, Role.DOOR, Role.ROAD)) || roles.contains(Role.PAVING), "roles " + roles);

		TreeMap<String, String> byChunk = new TreeMap<>();
		for (int cz = t.minZ() / 16; cz < t.maxZ() / 16; cz++) {
			for (int cx = t.minX() / 16; cx < t.maxX() / 16; cx++) {
				Box3 clip = new Box3(cx * 16, -64, cz * 16, 16, 400, 16);
				for (BuildingPiece b : w.buildings(0, 0)) cells(w, b.ref(), clip, byChunk, roles);
				for (GroundPiece g : w.ground(0, 0)) cells(w, g.ref(), clip, byChunk, roles);
			}
		}
		// buildings may reach past the tile (foundations, air strip), so compare inside the tile only
		TreeMap<String, String> inside = new TreeMap<>();
		whole.forEach((k, v) -> {
			String[] p = k.split(",");
			if (t.contains(Integer.parseInt(p[0]), Integer.parseInt(p[2]))) inside.put(k, v);
		});
		assertEquals(inside.size(), byChunk.size());
		assertEquals(inside, byChunk);
	}

	@Test
	void bothHalvesOfADoorAreTheSameBlock() {
		FixtureWorldPlanner w = FixtureWorldPlanner.sample();
		TreeMap<String, String> id = new TreeMap<>();
		int[] uppers = {0};
		for (int ix = 0; ix < 2; ix++) {
			for (int iz = 0; iz < 2; iz++) {
				for (BuildingPiece b : w.buildings(ix, iz)) {
					w.raster(b.ref(), ALL, (x, y, z, role, blockId, shape) -> { if (role == Role.DOOR) id.put(x + "," + y + "," + z, blockId + "/" + shape.half()); });
				}
			}
		}
		id.forEach((k, v) -> {
			if (!v.endsWith("/UPPER")) return;
			String[] p = k.split(",");
			String lower = id.get(p[0] + "," + (Integer.parseInt(p[1]) - 1) + "," + p[2]);
			assertNotNull(lower, "upper door half without a lower one at " + k);
			assertEquals(v.substring(0, v.indexOf('/')), lower.substring(0, lower.indexOf('/')), "door halves at " + k);
			uppers[0]++;
		});
		assertTrue(uppers[0] > 5, "doors " + uppers[0]);
	}

	@Test
	void unknownPieceFailsAndEvictAllIsHarmlessOnTheFixture() {
		FixtureWorldPlanner w = FixtureWorldPlanner.sample();
		assertThrows(IllegalArgumentException.class, () -> w.raster(PieceRef.building(0, 0, 9999), ALL, (x, y, z, r, id, s) -> {}));
		w.evictAll();
		assertNotNull(w.config());
	}
}
