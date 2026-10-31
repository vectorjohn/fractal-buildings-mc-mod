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

	/** Value noise on the lattice thresholded at {@code level}; edges forced true on the west and north when {@code edges}. */
	private static Quays.Near noise(long seed, double level, boolean edges, int i0, int j0) {
		return (x, z) -> {
			int i = Math.floorDiv(x, Quays.STEP), j = Math.floorDiv(z, Quays.STEP);
			if (edges && (i <= i0 + 1 || j <= j0 + 1) && ((i + j) & 3) != 0) return true;
			long h = (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ (seed * 0x165667B19E3779F9L);
			h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
			return ((h & 0xffff) / 65535.0) < level;
		};
	}

	private static final int W = 30;

	@Test
	void aWalkFromAnyPointGivesTheContoursChain() {
		int checked = 0;
		for (int s = 0; s < 200; s++) {
			double level = new double[] {0.3, 0.5, 0.6, 0.7}[s % 4];
			Quays.Near near = noise(s, level, s % 3 == 0, 0, 0);
			int i0 = 0, j0 = 0, i1 = W - 1, j1 = W - 1;
			List<List<Pt>> chains = Quays.contour(near, i0 * Quays.STEP, j0 * Quays.STEP, i1 * Quays.STEP, j1 * Quays.STEP);
			for (List<Pt> chain : chains) {
				for (int k : new int[] {0, chain.size() / 2, chain.size() - 1}) {
					assertEquals(chain, Quays.chainThrough(near, i0, j0, i1, j1, chain.get(k)), "seed " + s + " point " + k);
					checked++;
				}
			}
		}
		assertTrue(checked > 1000, "the fields must give chains: " + checked);
	}

	@Test
	void seedsCoverEveryChain() {
		for (int s = 0; s < 40; s++) {
			Quays.Near near = noise(s, 0.55, s % 2 == 0, 0, 0);
			List<List<Pt>> chains = Quays.contour(near, 0, 0, (W - 1) * Quays.STEP, (W - 1) * Quays.STEP);
			java.util.Set<Pt> got = new java.util.HashSet<>();
			for (Pt seed : Quays.seeds(near, 0, 0, W - 1, W - 1)) got.addAll(Quays.chainThrough(near, 0, 0, W - 1, W - 1, seed));
			java.util.Set<Pt> want = new java.util.HashSet<>();
			for (List<Pt> c : chains) want.addAll(c);
			assertEquals(want, got);
		}
	}

	@Test
	void aWalkReadsOnlyItsCells() {
		for (int s = 0; s < 30; s++) {
			Quays.Near base = noise(s, 0.4, false, 0, 0);
			for (List<Pt> chain : Quays.contour(base, 0, 0, (W - 1) * Quays.STEP, (W - 1) * Quays.STEP)) {
				java.util.Set<Long> nodes = new java.util.HashSet<>();
				Quays.Near counting = (x, z) -> { nodes.add(((long) z << 32) ^ (x & 0xffffffffL)); return base.at(x, z); };
				Quays.chainThrough(counting, 0, 0, W - 1, W - 1, chain.get(chain.size() / 2));
				assertTrue(nodes.size() < 4 * chain.size() + 8, "read " + nodes.size() + " nodes for a chain of " + chain.size() + " points");
			}
		}
	}

	@Test
	void aSaddleWalkKeepsItsOwnChain() {
		Quays.Near near = (x, z) -> (x == 0 && z == 0) || (x == 8 && z == 8);
		List<List<Pt>> chains = Quays.contour(near, 0, 0, 0, 0);
		assertEquals(2, chains.size());
		for (List<Pt> chain : chains) assertEquals(chain, Quays.chainThrough(near, 0, 0, 0, 0, chain.get(0)));
	}
}
