package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.probe.CoarseProbe;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** QUAYFAST 4b: the quay screen asks {@link CoarseTerrain} about the same lattice columns again and again; each must reach the probe once, with the same answers. */
class CoarseTerrainMemoTest {
	static long h(int x, int z) { long v = x * 73856093L ^ z * 19349663L; v ^= v >>> 29; v *= 0xbf58476d1ce4e5b9L; v ^= v >>> 32; return v; }

	static final class Counting implements CoarseProbe {
		final AtomicInteger reads = new AtomicInteger();
		public int stride() { return 32; }
		public int floorY(int x, int z) { return 60 + (int) (h(x, z) & 7); }
		public int waterDepth(int x, int z) { return 0; }
		public String biome(int x, int z) { reads.incrementAndGet(); return (h(x, z) >>> 8) % 7 == 0 ? "minecraft:river" : (h(x, z) >>> 8) % 11 == 0 ? "minecraft:jagged_peaks" : "minecraft:plains"; }
	}

	@Test
	void everyLatticeColumnReachesTheProbeOnceAndAnswersAreUnchanged() {
		Counting probe = new Counting();
		CoarseTerrain memo = new CoarseTerrain(probe);
		int[] answers = new int[1];
		for (int z = -400; z < 400; z += 3) {
			for (int x = -400; x < 400; x += 3) {
				boolean w = memo.waterWithin(x, z, 48), wp = memo.water(x, z), pk = memo.peak(x, z);
				answers[0] = answers[0] * 31 + (w ? 1 : 0) + (wp ? 2 : 0) + (pk ? 4 : 0);
			}
		}
		int lattice = (800 / 32 + 4) * (800 / 32 + 4) * 2; // water and peak, each column at most once (with the reach round the box)
		assertTrue(probe.reads.get() <= lattice, "biome reads " + probe.reads.get() + " over " + lattice);
		CoarseTerrain.memoise = false;
		try {
			CoarseTerrain plain = new CoarseTerrain(new Counting());
			int want = 0;
			for (int z = -400; z < 400; z += 3) {
				for (int x = -400; x < 400; x += 3) {
					boolean w = plain.waterWithin(x, z, 48), wp = plain.water(x, z), pk = plain.peak(x, z);
					want = want * 31 + (w ? 1 : 0) + (wp ? 2 : 0) + (pk ? 4 : 0);
				}
			}
			assertEquals(want, answers[0]);
		} finally {
			CoarseTerrain.memoise = true;
		}
	}
}
