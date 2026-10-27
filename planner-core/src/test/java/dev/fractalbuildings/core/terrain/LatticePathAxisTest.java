package dev.fractalbuildings.core.terrain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** METROFILL DX: the 4-neighbour mode of {@link LatticePath}. */
class LatticePathAxisTest {
	static final LatticePath.Cost FLAT = (x, z, nx, nz) -> (x != nx && z != nz ? 14L : 10L) * 8;
	static final LatticePath.Bounds B = new LatticePath.Bounds(0, 0, 400, 400);

	@Test
	void axisPathHasNoDiagonalStepAndManhattanLength() {
		LatticePath.Pt a = new LatticePath.Pt(0, 0), b = new LatticePath.Pt(240, 120);
		List<LatticePath.Pt> p = LatticePath.find(a, b, FLAT, B, 8, -1, 100_000, false, false);
		assertFalse(p.isEmpty());
		for (int i = 1; i < p.size(); i++) {
			LatticePath.Pt u = p.get(i - 1), v = p.get(i);
			assertTrue(u.x() == v.x() || u.z() == v.z(), "diagonal step " + u + " -> " + v);
			assertEquals(8, Math.abs(u.x() - v.x()) + Math.abs(u.z() - v.z()));
		}
		assertEquals((240 + 120) / 8 + 1, p.size());
	}

	@Test
	void diagonalModeStillCutsCorners() {
		List<LatticePath.Pt> p = LatticePath.find(new LatticePath.Pt(0, 0), new LatticePath.Pt(240, 120), FLAT, B, 8, -1, 100_000, false, true);
		assertEquals(240 / 8 + 1, p.size());
		assertEquals(p, LatticePath.find(new LatticePath.Pt(0, 0), new LatticePath.Pt(240, 120), FLAT, B, 8, -1, 100_000, false), "the old signature is the diagonal mode");
	}
}
