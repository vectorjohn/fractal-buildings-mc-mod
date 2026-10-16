package dev.fractalbuildings.core.probe;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.rand.Hash64;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecordedProbeTest {
	static RecordedProbe load() throws Exception {
		return RecordedProbe.read(Path.of("../fixtures/terrain/s4-12345-crop32.json"));
	}

	@Test
	void latticePoints() throws Exception {
		RecordedProbe p = load();
		assertEquals(69, p.surfaceY(-832, -3840));
		assertEquals(0, p.waterDepth(-832, -3840));
		assertEquals("minecraft:sunflower_plains", p.biome(-832, -3840));
		assertEquals(86, p.surfaceY(-384, -3136));
		assertEquals("minecraft:windswept_hills", p.biome(-384, -3136));
		assertEquals(63, p.surfaceY(576, -4160));
		assertEquals(20, p.waterDepth(576, -4160));
		assertEquals(43, p.floorY(576, -4160));
		assertEquals("minecraft:river", p.biome(576, -4160));
	}

	@Test
	void interpolatesCellCentre() throws Exception {
		RecordedProbe p = load();
		assertEquals(80, p.floorY(-1376, -4128));
		assertEquals(86, p.surfaceY(-1376, -4128));
		assertEquals(6, p.waterDepth(-1376, -4128));
	}

	@Test
	void outsideGridThrows() throws Exception {
		RecordedProbe p = load();
		var e = assertThrows(IllegalArgumentException.class, () -> p.floorY(-1409, -4160));
		assertTrue(e.getMessage().contains("-1409"));
		assertThrows(IllegalArgumentException.class, () -> p.floorY(640, -4160));
		assertThrows(IllegalArgumentException.class, () -> p.surfaceY(0, -4161));
		assertDoesNotThrow(() -> p.floorY(-1408 + 31 * 64, -4160 + 31 * 64));
	}

	@Test
	void orderIndependent() throws Exception {
		RecordedProbe p = load();
		int n = 10_000;
		int[] xs = new int[n], zs = new int[n];
		for (int k = 0; k < n; k++) {
			long h = Hash64.hash(7, "cols", k);
			xs[k] = -1408 + Hash64.below(h, 31 * 64);
			zs[k] = -4160 + Hash64.below(Hash64.mix(h), 31 * 64);
		}
		int[] fwd = sample(p, xs, zs, false);
		int[] rev = sample(p, xs, zs, true);
		assertArrayEquals(fwd, rev);
		int[][] par = new int[4][];
		Thread[] ts = new Thread[4];
		for (int t = 0; t < 4; t++) {
			final int ti = t;
			ts[t] = new Thread(() -> par[ti] = sample(p, xs, zs, ti % 2 == 1));
			ts[t].start();
		}
		for (Thread t : ts) t.join();
		for (int[] r : par) assertArrayEquals(fwd, r);
	}

	private static int[] sample(RecordedProbe p, int[] xs, int[] zs, boolean reverse) {
		int n = xs.length;
		int[] out = new int[n * 3];
		for (int c = 0; c < n; c++) {
			int k = reverse ? n - 1 - c : c;
			out[k * 3] = p.floorY(xs[k], zs[k]);
			out[k * 3 + 1] = p.surfaceY(xs[k], zs[k]);
			out[k * 3 + 2] = p.biome(xs[k], zs[k]).hashCode();
		}
		return out;
	}
}
