package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * METROFILL R1 (DS-47): the pointwise rule puts arterials on every second tile line per axis (the metropolis outer ring is exempt);
 * the network pass then joins the pieces that cut off back to the main network by short links, or demotes them.
 */
class ArterialParityTest {
	static SkeletonSource source(int n) {
		SkeletonsTest.Case c = SkeletonsTest.syntheticCase(n);
		return new SkeletonSource.Direct(c.seed(), new FixedSiteView(c.site(), c.field()), c.site(), c.probe());
	}

	@Test
	void arterialsOffTheRingShareOneLineParityPerAxis() {
		for (int n = 1; n <= 3; n++) {
			SkeletonSource src = source(n);
			Arterials art = src.density().arterials();
			Pt m = src.site().centre();
			TreeSet<Integer>[] parity = new TreeSet[] {new TreeSet<Integer>(), new TreeSet<Integer>()};
			int found = 0;
			for (DistrictGrid.Segment s : art.segmentsIn(m.x() - 700, m.z() - 700, m.x() + 700, m.z() + 700)) {
				if (!art.pointwise(s) || art.outerRing(s)) continue;
				found++;
				parity[s.axis()].add(Math.floorMod(s.line(), 2));
				assertTrue(art.parityLine(s), "segment " + s);
			}
			for (TreeSet<Integer> p : parity) assertTrue(p.size() <= 1, "mixed parity " + p + " in case " + n);
			if (src.site().tier().atLeast(Tier.CITY)) assertTrue(found > 0, "no arterial in case " + n);
		}
	}

	/** The arterials of a synthetic city after the network pass: one connected piece. */
	@Test
	void theArterialsOfACityAreOneNetwork() {
		for (int n = 1; n <= 3; n++) {
			SkeletonSource src = source(n);
			if (!src.site().tier().atLeast(Tier.CITY)) continue;
			Arterials art = src.density().arterials();
			Pt m = src.site().centre();
			int r = src.density().maxRadius() + 64;
			TreeMap<Long, Long> parent = new TreeMap<>();
			int found = 0;
			for (DistrictGrid.Segment s : art.segmentsIn(m.x() - r, m.z() - r, m.x() + r, m.z() + r)) {
				if (!art.is(s)) continue;
				found++;
				long a = s.axis() == DistrictGrid.AXIS_X ? Arterials.node(s.line(), s.index()) : Arterials.node(s.index(), s.line());
				long b = s.axis() == DistrictGrid.AXIS_X ? Arterials.node(s.line(), s.index() + 1) : Arterials.node(s.index() + 1, s.line());
				parent.put(root(parent, a), root(parent, b));
			}
			TreeSet<Long> roots = new TreeSet<>();
			for (long v : parent.keySet()) roots.add(root(parent, v));
			assertTrue(found > 0, "case " + n);
			assertEquals(1, roots.size(), "arterial pieces in case " + n);
		}
	}

	static long root(TreeMap<Long, Long> parent, long v) {
		parent.putIfAbsent(v, v);
		while (parent.get(v) != v) v = parent.get(v);
		return v;
	}

	static long[] seg(int ax, int az, int bx, int bz) { return new long[] {Arterials.node(ax, az), Arterials.node(bx, bz)}; }

	@Test
	void piecesAreJoinedByTheShortestLinkOrDemoted() {
		TreeMap<String, long[]> art = new TreeMap<>(), cand = new TreeMap<>();
		art.put("m1", seg(0, 0, 1, 0)); art.put("m2", seg(1, 0, 2, 0)); art.put("m3", seg(2, 0, 3, 0)); // the main network
		art.put("i1", seg(0, 2, 1, 2)); cand.put("c1", seg(1, 2, 1, 1)); cand.put("c2", seg(1, 1, 1, 0)); // two links away
		art.put("k1", seg(3, 1, 4, 1)); cand.put("d1", seg(3, 1, 3, 0)); // one link away; the two-link route e1, e2 is not taken
		cand.put("e1", seg(4, 1, 4, 0)); cand.put("e2", seg(4, 0, 3, 0));
		art.put("j1", seg(0, 5, 1, 5)); // three links from i1: too far, demoted
		cand.put("f1", seg(0, 5, 0, 4)); cand.put("f2", seg(0, 4, 0, 3)); cand.put("f3", seg(0, 3, 0, 2));
		art.put("z1", seg(9, 9, 9, 10)); // no link at all: demoted
		cand.put("x1", seg(7, 7, 7, 8)); // a candidate nowhere near: not restored
		assertEquals(List.of("c1", "c2", "d1", "i1", "k1", "m1", "m2", "m3"), List.copyOf(Arterials.join(art, cand)));
	}
}
