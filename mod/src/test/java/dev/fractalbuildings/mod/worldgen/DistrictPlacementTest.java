package dev.fractalbuildings.mod.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import org.junit.jupiter.api.Test;

/** The placement is pure geometry: start chunks are exactly the tile centres, and the locate walk visits each tile once. */
class DistrictPlacementTest {
	@org.junit.jupiter.api.BeforeAll
	static void bootstrap() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
	}

	static DistrictPlacement placement() {
		return new DistrictPlacement(Vec3i.ZERO, AbstractSpreadingStructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, 1301, Optional.empty());
	}

	@Test
	void sectorWalkVisitsEachTileCentreOnce() {
		DistrictPlacement p = placement();
		for (long seed : new long[] {0, 1, 987654321L, -42}) {
			DistrictGrid g = new DistrictGrid(seed);
			for (int sz = -25; sz <= 25; sz++) {
				for (int sx = -25; sx <= 25; sx++) {
					// every chunk the locate walk may pass for this sector (any chunk of the sector)
					for (int c : new int[] {0, 5, 9}) {
						ChunkPos want = p.getPotentialStructureChunk(seed, sx * 10 + c, sz * 10 + c);
						DistrictGrid.Tile t = g.tile(sx, sz);
						assertEquals(new ChunkPos(t.originChunkX(), t.originChunkZ()), want, "sector " + sx + "," + sz);
						assertTrue(DistrictPlacement.isStartChunk(seed, want.x(), want.z()));
					}
				}
			}
		}
	}

	@Test
	void startChunksAreExactlyTheTileCentres() {
		long seed = 987654321L;
		DistrictGrid g = new DistrictGrid(seed);
		int starts = 0;
		for (int cz = -120; cz < 120; cz++) {
			for (int cx = -120; cx < 120; cx++) {
				if (DistrictPlacement.isStartChunk(seed, cx, cz)) {
					starts++;
					DistrictGrid.Tile t = g.tileAt(cx * 16, cz * 16);
					assertEquals(cx, t.originChunkX());
					assertEquals(cz, t.originChunkZ());
				}
			}
		}
		// 240 chunks at about 10 per tile: 24 x 24 tiles, give or take the edge tiles
		assertTrue(starts >= 22 * 22 && starts <= 26 * 26, "starts " + starts);
	}

	@Test
	void spacingIsThePeriod() {
		DistrictPlacement p = placement();
		assertEquals(DistrictGrid.PERIOD, p.spacing());
	}
}
