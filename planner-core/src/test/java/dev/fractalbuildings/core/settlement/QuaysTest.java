package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The promenade contour (TR 5.3): marching squares on the street lattice. */
class QuaysTest {
	/** Water within OFFSET of a disk of radius r. */
	static Quays.Near disk(int cx, int cz, int r) {
		return (x, z) -> {
			long dx = x - cx, dz = z - cz;
			return dx * dx + dz * dz <= (long) (r + Quays.OFFSET) * (r + Quays.OFFSET);
		};
	}

	@Test
	void aLakeGetsOneClosedRingAroundIt() {
		List<List<Pt>> chains = Quays.contour(disk(0, 0, 60), -120, -120, 120, 120);
		assertEquals(1, chains.size());
		List<Pt> ring = chains.get(0);
		assertEquals(ring.get(0), ring.get(ring.size() - 1), "the ring closes");
		for (Pt p : ring) {
			double d = Math.hypot(p.x(), p.z());
			assertTrue(Math.abs(d - (60 + Quays.OFFSET)) <= Quays.STEP, "contour point " + p + " lies " + d + " from the lake centre");
		}
	}

	@Test
	void everySegmentRunsAlongAnAxisOrAt45Degrees() {
		for (int r : new int[] {20, 45, 61, 133}) {
			for (List<Pt> chain : Quays.contour(disk(7, -3, r), -200, -200, 200, 200)) {
				for (int i = 1; i < chain.size(); i++) {
					int dx = Math.abs(chain.get(i).x() - chain.get(i - 1).x()), dz = Math.abs(chain.get(i).z() - chain.get(i - 1).z());
					assertTrue(dx == 0 || dz == 0 || dx == dz, "segment " + chain.get(i - 1) + " to " + chain.get(i));
				}
			}
		}
	}

	/** Two windows that overlap see the same contour in the overlap: tiles can plan alone and still agree. */
	@Test
	void overlappingWindowsAgreeInTheOverlap() {
		Quays.Near near = (x, z) -> {
			// a wavy shore: water south of z = 40 sin(x / 70)
			return z > 40 * Math.sin(x / 70.0) + 10;
		};
		List<Pt> a = points(Quays.contour(near, -400, -150, 100, 150)), b = points(Quays.contour(near, -150, -150, 400, 150));
		for (Pt p : a) if (p.x() >= -120 && p.x() <= 70) assertTrue(b.contains(p), "point " + p + " missing from the second window");
		for (Pt p : b) if (p.x() >= -120 && p.x() <= 70) assertTrue(a.contains(p), "point " + p + " missing from the first window");
		assertFalse(a.isEmpty());
	}

	@Test
	void noWaterNoContour() {
		assertTrue(Quays.contour((x, z) -> false, -100, -100, 100, 100).isEmpty());
		assertTrue(Quays.contour((x, z) -> true, -100, -100, 100, 100).isEmpty());
	}

	@Test
	void saddleCellsDoNotJoinTheTwoNearCorners() {
		// near corners at (0,0) and (8,8) only
		Quays.Near near = (x, z) -> (x == 0 && z == 0) || (x == 8 && z == 8);
		List<List<Pt>> chains = Quays.contour(near, 0, 0, 0, 0); // the one cell (0..8)
		assertEquals(2, chains.size(), "two separate arcs, one round each near corner");
	}

	private static List<Pt> points(List<List<Pt>> chains) {
		List<Pt> out = new ArrayList<>();
		for (List<Pt> c : chains) out.addAll(c);
		return out;
	}
}
