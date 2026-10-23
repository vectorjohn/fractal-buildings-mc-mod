package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.settlement.Tier;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixtureWorldPlannerTest {
	@Test
	void sampleServesItsFourTiles() {
		FixtureWorldPlanner w = FixtureWorldPlanner.sample();
		int buildings = 0, grounds = 0;
		for (int iz = 0; iz < 2; iz++) {
			for (int ix = 0; ix < 2; ix++) {
				TileInfo t = w.tile(ix, iz).orElseThrow();
				assertEquals(Tier.TOWN, t.tier());
				assertTrue(w.isStartChunk(t.tile().originChunkX(), t.tile().originChunkZ()));
				assertFalse(w.isStartChunk(t.tile().originChunkX() + 1, t.tile().originChunkZ()));
				assertEquals(t.tile().rect(), w.plan(ix, iz).plan().district().rect());
				List<BuildingPiece> bs = w.buildings(ix, iz);
				buildings += bs.size();
				for (BuildingPiece b : bs) {
					assertEquals(PieceRef.Kind.BUILDING, b.ref().kind());
					assertEquals(b.brief().id(), w.plan(ix, iz).plan().lots().get(b.ref().index()).id());
					assertTrue(b.box().contains(b.building().placement().toWorld(b.building().shell().bounds())), "box covers the shell");
				}
				for (GroundPiece g : w.ground(ix, iz)) {
					grounds++;
					assertFalse(g.boxes().isEmpty());
					for (Box b : g.boxes()) {
						Box3 k = b.box();
						assertTrue(Math.floorDiv(k.x(), 16) == g.chunkX() && Math.floorDiv(k.maxX() - 1, 16) == g.chunkX()
								&& Math.floorDiv(k.z(), 16) == g.chunkZ() && Math.floorDiv(k.maxZ() - 1, 16) == g.chunkZ(), "ground box inside its chunk");
						assertTrue(g.box().contains(k));
					}
				}
			}
		}
		assertTrue(buildings > 30, "buildings " + buildings);
		assertTrue(grounds > 10, "ground pieces " + grounds);
		assertTrue(w.tile(5, 5).isEmpty());
		assertTrue(w.buildings(5, 5).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> w.plan(5, 5));
		assertEquals(w.buildings(0, 0), w.buildings(0, 0), "same pieces on a second call");
		assertTrue(w.nearest(0, 0, Tier.CITY, 3).isEmpty());
		assertTrue(w.nearest(0, 0, Tier.HAMLET, 3).isPresent());
	}
}
