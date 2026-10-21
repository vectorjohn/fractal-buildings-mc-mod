package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.settlement.DistrictGrid.Segment;
import dev.fractalbuildings.core.settlement.DistrictGrid.Tile;
import dev.fractalbuildings.core.settlement.DistrictGrid.TileId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DistrictGridTest {
	@Test
	void everyColumnOfWindowBelongsToExactlyOneTile() {
		for (long seed : new long[] {1, 42, -7}) {
			DistrictGrid g = new DistrictGrid(seed);
			for (int ox : new int[] {-2048, 100000}) {
				for (int oz : new int[] {-2048, -300000}) {
					// sample every 8th column plus every tile edge exactly
					for (int x = ox; x < ox + 4096; x += 8) {
						for (int z = oz; z < oz + 4096; z += 8) {
							Tile t = g.tileAt(x, z);
							assertTrue(t.contains(x, z), seed + " " + x + "," + z);
							// neighbours must not contain it
							assertFalse(g.tile(t.ix() + 1, t.iz()).contains(x, z));
							assertFalse(g.tile(t.ix() - 1, t.iz()).contains(x, z));
							assertFalse(g.tile(t.ix(), t.iz() + 1).contains(x, z));
							assertFalse(g.tile(t.ix(), t.iz() - 1).contains(x, z));
						}
					}
				}
			}
		}
	}

	@Test
	void tilePartitionIsExactAtEveryBorderColumn() {
		DistrictGrid g = new DistrictGrid(5);
		for (int ix = -50; ix < 50; ix++) {
			Tile t = g.tile(ix, 3);
			Tile e = g.tile(ix + 1, 3);
			assertEquals(t.maxX(), e.minX());
			assertEquals(ix, g.tileOf(t.minX(), t.minZ()).ix());
			assertEquals(ix, g.tileOf(t.maxX() - 1, t.minZ()).ix());
			assertEquals(ix + 1, g.tileOf(t.maxX(), t.minZ()).ix());
		}
	}

	@Test
	void tilesAre8To12ChunksAndChunkAligned() {
		for (long seed = 0; seed < 20; seed++) {
			DistrictGrid g = new DistrictGrid(seed * 0x1234567L);
			for (int ix = -200; ix < 200; ix++) {
				Tile t = g.tile(ix, ix * 7 % 100);
				assertEquals(0, t.minX() % 16);
				assertEquals(0, t.minZ() % 16);
				assertTrue(t.widthX() >= 128 && t.widthX() <= 192, "w " + t.widthX());
				assertTrue(t.widthZ() >= 128 && t.widthZ() <= 192, "d " + t.widthZ());
			}
		}
	}

	@Test
	void originChunkWithinSixOfEveryTileChunk() {
		for (long seed = 0; seed < 20; seed++) {
			DistrictGrid g = new DistrictGrid(seed);
			for (int ix = -60; ix < 60; ix++) {
				for (int iz = -5; iz < 5; iz++) {
					Tile t = g.tile(ix, iz);
					int cx0 = t.minX() / 16, cx1 = t.maxX() / 16 - 1, cz0 = t.minZ() / 16, cz1 = t.maxZ() / 16 - 1;
					assertTrue(t.originChunkX() >= cx0 && t.originChunkX() <= cx1);
					assertTrue(t.originChunkZ() >= cz0 && t.originChunkZ() <= cz1);
					for (int cx : new int[] {cx0, cx1})
						for (int cz : new int[] {cz0, cz1}) {
							assertTrue(Math.abs(cx - t.originChunkX()) <= 6);
							assertTrue(Math.abs(cz - t.originChunkZ()) <= 6);
						}
				}
			}
		}
	}

	@Test
	void seedsDifferingAboveBit32Differ() {
		DistrictGrid a = new DistrictGrid(7L), b = new DistrictGrid(7L + (1L << 40));
		int diff = 0;
		for (int k = -50; k < 50; k++) if (a.lineChunk(0, k) != b.lineChunk(0, k)) diff++;
		assertTrue(diff > 30, "diff " + diff);
	}

	@Test
	void linesDifferByAxisAndJitterIsUsed() {
		DistrictGrid g = new DistrictGrid(3);
		int[] seen = new int[3];
		int axisDiff = 0;
		for (int k = -100; k < 100; k++) {
			seen[g.lineChunk(0, k) - DistrictGrid.PERIOD * k + 1]++;
			if (g.lineChunk(0, k) != g.lineChunk(1, k)) axisDiff++;
		}
		for (int c : seen) assertTrue(c > 30, "offset spread " + java.util.Arrays.toString(seen));
		assertTrue(axisDiff > 50);
	}

	@Test
	void shuffledQueriesAgree() {
		DistrictGrid g = new DistrictGrid(99);
		Random r = new Random(5);
		List<int[]> pts = new ArrayList<>();
		List<TileId> expected = new ArrayList<>();
		for (int i = 0; i < 5000; i++) {
			int[] p = {r.nextInt(2_000_000) - 1_000_000, r.nextInt(2_000_000) - 1_000_000};
			pts.add(p);
			expected.add(g.tileOf(p[0], p[1]));
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < pts.size(); i++) order.add(i);
		Collections.shuffle(order, new Random(11));
		DistrictGrid fresh = new DistrictGrid(99);
		for (int i : order) assertEquals(expected.get(i), fresh.tileOf(pts.get(i)[0], pts.get(i)[1]));
	}

	@Test
	void sharedSegmentsHaveSameKeyAndHashFromBothSides() {
		DistrictGrid g = new DistrictGrid(8);
		for (int ix = -20; ix < 20; ix++) {
			for (int iz = -20; iz < 20; iz++) {
				Segment[] a = g.segments(ix, iz);
				Segment[] e = g.segments(ix + 1, iz);
				Segment[] s = g.segments(ix, iz + 1);
				assertEquals(a[1], e[0]); // east of this = west of next
				assertEquals(a[1].hash(8, "gate"), e[0].hash(8, "gate"));
				assertEquals(a[3], s[2]);
				assertNotEquals(a[0].hash(8, "gate"), a[1].hash(8, "gate"));
			}
		}
	}
}
