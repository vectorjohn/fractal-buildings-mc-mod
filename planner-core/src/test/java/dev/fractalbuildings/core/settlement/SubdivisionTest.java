package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The old-town splitter (IRR1) as pure geometry: block sizes, T junctions only, determinism. */
class SubdivisionTest {
	static final Subdivision.Skel NONE = (x, z) -> false;

	/** A tile-like inner rectangle (tile 128 to 192 wide, less the 14 block inset). */
	static Rect inner(int i) {
		int w = 100 + Hash64.below(Hash64.hash(7, "w", i), 65), d = 100 + Hash64.below(Hash64.hash(7, "d", i), 65);
		return new Rect(Hash64.below(Hash64.hash(7, "x", i), 1000) - 500, Hash64.below(Hash64.hash(7, "z", i), 1000) - 500, w, d);
	}

	static boolean onLine(Subdivision.Line l, int x, int z) {
		int h = l.width() / 2;
		return l.constX() ? x >= l.c() - h && x < l.c() - h + l.width() && z >= l.from() && z <= l.to() : z >= l.c() - h && z < l.c() - h + l.width() && x >= l.from() && x <= l.to();
	}

	@Test
	void everyBlockIsThirtyToNinetyAcrossAndTheyAreDisjoint() {
		int blocks = 0, sizes = 0;
		TreeSet<String> distinct = new TreeSet<>();
		for (int i = 0; i < 2000; i++) {
			Rect in = inner(i);
			Subdivision s = Subdivision.of(in, Hash64.hash(1, "t", i), NONE);
			assertFalse(s.leaves.isEmpty());
			for (int a = 0; a < s.leaves.size(); a++) {
				Rect r = s.leaves.get(a);
				assertTrue(r.w() >= 30 && r.d() >= 30 && r.w() <= 90 && r.d() <= 90, "case " + i + " block " + r);
				assertTrue(r.x() >= in.x() && r.z() >= in.z() && r.x() + r.w() <= in.x() + in.w() && r.z() + r.d() <= in.z() + in.d(), "block inside the area");
				for (int b = a + 1; b < s.leaves.size(); b++) assertFalse(Rect.overlap(r.x(), r.z(), r.w(), r.d(), s.leaves.get(b).x(), s.leaves.get(b).z(), s.leaves.get(b).w(), s.leaves.get(b).d()), "blocks overlap");
				distinct.add(r.w() + "x" + r.d());
				blocks++;
			}
			sizes += s.leaves.size();
		}
		assertTrue(distinct.size() > 100, "block sizes vary: " + distinct.size());
		assertTrue(sizes / 2000.0 >= 3, "a tile gets several blocks: " + sizes / 2000.0);
		assertEquals(0, blocks - sizes);
	}

	@Test
	void streetsMeetInTJunctionsAndNeverCrossHeadOn() {
		int crossings = 0;
		for (int i = 0; i < 2000; i++) {
			Rect in = inner(i);
			Subdivision s = Subdivision.of(in, Hash64.hash(2, "t", i), NONE);
			crossings += s.crossings;
			for (Subdivision.Line l : s.lines) {
				// both ends of a street sit on another street or one block past the edge of the area
				for (int e = 0; e < 2; e++) {
					int at = e == 0 ? l.from() : l.to();
					int x = l.constX() ? l.c() : at, z = l.constX() ? at : l.c();
					boolean edge = l.constX() ? (z == in.z() - 1 || z == in.z() + in.d()) : (x == in.x() - 1 || x == in.x() + in.w());
					boolean onStreet = false;
					for (Subdivision.Line o : s.lines) if (o != l && o.constX() != l.constX() && onLine(o, x, z)) onStreet = true;
					assertTrue(edge || onStreet, "case " + i + " street " + l + " end " + e + " hangs in the air");
				}
				// no street of the other side continues it: the cell just past each side of a line is a street of the same orientation only where it is a different street
			}
			// a four-way crossing: two streets of one orientation ending on the same street from both sides within a few blocks
			for (Subdivision.Line a : s.lines) {
				for (Subdivision.Line b : s.lines) {
					if (a.id() >= b.id() || a.constX() != b.constX() || Math.abs(a.c() - b.c()) >= Subdivision.JOG) continue;
					// same orientation and nearly in line: they must not both end on one street from opposite sides
					for (Subdivision.Line p : s.lines) {
						if (p.constX() == a.constX()) continue;
						boolean aLow = onLine(p, a.c(), a.from()), aHigh = onLine(p, a.c(), a.to()), bLow = onLine(p, b.c(), b.from()), bHigh = onLine(p, b.c(), b.to());
						assertFalse((aLow && bHigh) || (aHigh && bLow), "case " + i + ": " + a + " and " + b + " cross " + p + " head on");
					}
				}
			}
		}
		assertEquals(0, crossings, "no split had to cross a junction head on");
	}

	@Test
	void sameHashSameSplitsAndOtherHashDiffers() {
		Rect in = new Rect(-20, 40, 150, 130);
		Subdivision a = Subdivision.of(in, 99, NONE), b = Subdivision.of(in, 99, NONE), c = Subdivision.of(in, 100, NONE);
		assertEquals(a.lines, b.lines);
		assertEquals(a.leaves, b.leaves);
		assertNotEquals(a.lines, c.lines);
	}

	@Test
	void aStraightSkeletonRoadAcrossTheBlockIsTheSplit() {
		Rect in = new Rect(0, 0, 150, 150);
		// a 7 wide road at x 70..76 spanning the whole area: with the first split free to land there it is used and not drawn again
		int usedRoad = 0;
		for (int i = 0; i < 200; i++) {
			Subdivision s = Subdivision.of(in, Hash64.hash(3, "t", i), (x, z) -> x >= 70 && x < 77 && z >= 0 && z < 150);
			for (Subdivision.Line l : s.lines) assertFalse(l.level() == 0 && l.constX() && Math.abs(l.c() - 73) < 8, "a street was drawn beside the road");
			boolean roadSplit = s.leaves.stream().anyMatch(r -> r.x() + r.w() == 70 || r.x() == 77);
			if (roadSplit) usedRoad++;
			for (Rect r : s.leaves) assertTrue(r.x() >= 77 || r.x() + r.w() <= 70 || r.w() <= 90, "no block over the road");
		}
		assertTrue(usedRoad > 0, "the road was used as a split at least once");
	}

	@Test
	void linesAreDrawnInPreorder() {
		for (int i = 0; i < 100; i++) {
			Subdivision s = Subdivision.of(inner(i), Hash64.hash(4, "t", i), NONE);
			List<Integer> ids = new ArrayList<>();
			for (Subdivision.Line l : s.lines) ids.add(l.id());
			for (int k = 0; k < ids.size(); k++) assertEquals(k, ids.get(k));
		}
	}
}
