package dev.fractalbuildings.core.region;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.rand.Hash64;
import org.junit.jupiter.api.Test;

class RegionFieldTest {
	static int coord(long k, int i) {
		return Hash64.range(Hash64.hash(7, "t/coord", k, i), -40000, 40000);
	}

	@Test
	void sitesStayInsideTheirJitterBox() {
		RegionField f = new RegionField(42L, 1024);
		for (int cx = -30; cx < 30; cx++) {
			for (int cz = -30; cz < 30; cz++) {
				int x = f.siteX(cx, cz), z = f.siteZ(cx, cz);
				assertTrue(Math.abs(x - (cx * 1024 + 512)) <= 0.35 * 1024 + 1, "x " + x);
				assertTrue(Math.abs(z - (cz * 1024 + 512)) <= 0.35 * 1024 + 1, "z " + z);
			}
		}
	}

	@Test
	void threeByThreeSearchIsEnough() {
		RegionField f = new RegionField(42L, 1024);
		for (int i = 0; i < 20000; i++) {
			int x = coord(1, i), z = coord(2, i);
			var a = f.query(x, z, 1);
			var b = f.query(x, z, 3);
			assertEquals(b.cx(), a.cx());
			assertEquals(b.cz(), a.cz());
			assertEquals(b.d2sq(), a.d2sq());
		}
	}

	@Test
	void seedsDifferingAboveBit32GiveDifferentFields() {
		RegionField a = new RegionField(12345L, 1024), b = new RegionField(12345L + (1L << 32), 1024);
		int same = 0;
		for (int i = -20; i < 20; i++) if (a.siteX(i, 3) == b.siteX(i, 3)) same++;
		assertTrue(same <= 2, "fields too similar: " + same + " equal sites of 40");
	}

	@Test
	void edgeDistanceMatchesTheDistanceToTheNearestCellChange() {
		RegionField f = new RegionField(99L, 1024);
		for (int i = 0; i < 40; i++) {
			int x = coord(3, i) % 6000, z = coord(4, i) % 6000;
			var h = f.query(x, z);
			double edge = f.edgeDistance(x, z);
			assertTrue(edge >= 0);
			double min = Double.MAX_VALUE;
			for (int d = 0; d < 360; d++) {
				double a = d * StrictMath.PI / 180, dx = StrictMath.cos(a), dz = StrictMath.sin(a);
				for (double t = 0; t < 2000; t += 0.5) {
					var q = f.query((int) StrictMath.round(x + dx * t), (int) StrictMath.round(z + dz * t));
					if (q.cx() != h.cx() || q.cz() != h.cz()) {
						min = Math.min(min, t);
						break;
					}
				}
			}
			assertTrue(min >= edge - 1.5 && min <= edge + 6, "edge " + edge + " vs ray " + min);
		}
	}
}
