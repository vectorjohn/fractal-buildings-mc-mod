package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.List;

/**
 * The old-town splitter (IRR1, plan "Irregular cities"): a rectangle is cut recursively into different-size blocks by straight
 * streets that meet in T junctions. Pure geometry keyed by a hash; {@link Districts} lays the streets on the ground, drops the runs
 * that cross water or squares, and packs the blocks' edges with lots.
 *
 * <p>The longer side is split at a hashed 35 to 65 per cent (snapped to a multiple of 4, or to a straight skeleton road that crosses
 * the block there: the road is the split, nothing is drawn), by a street {@value #FIRST} wide at the first level and lanes
 * {@value #LANE} wide deeper. A block whose longer side is under {@value #SPLIT_ABOVE} is never split, one up to {@value #MAX_SIDE} is
 * split with a hashed 45 per cent chance, a longer one always is. So every block is {@value #MIN_SIDE} to {@value #MAX_SIDE} blocks
 * across. A street ends on the street that bounds its block (a T junction); two streets never meet head on from the two sides of
 * a street (a four-way crossing): the second keeps {@value #JOG} blocks off the first one's junction or, when the block is too small
 * to dodge, is not split.
 */
final class Subdivision {
	static final int FIRST = 5, LANE = 3, MIN_SIDE = 30, MAX_SIDE = 90, SPLIT_ABOVE = 2 * MIN_SIDE + FIRST + 1, JOG = 6, STOP_PERCENT = 55;

	/** A street of the subdivision: the line of constant {@code c} (x when {@code constX}) from {@code from} to {@code to} (inclusive, one block past the block each side so it overlaps the bounding streets). */
	record Line(int id, boolean constX, int c, int from, int to, int width, int level) {}

	/** A straight skeleton road already there: true for a column on its footprint. */
	interface Skel { boolean at(int x, int z); }

	/** Lines in preorder (a parent before its children) and the leaf blocks. */
	final List<Line> lines = new ArrayList<>();
	final List<Rect> leaves = new ArrayList<>();
	/** Splits that had to cross a junction head on because no other position fitted (only for blocks too long to leave whole). */
	int crossings;

	private final Skel skel;
	private final List<int[]> junctions = new ArrayList<>(); // bounding street id, side of it the new street is on, position
	private int roadIds = -2;

	private Subdivision(Skel skel) { this.skel = skel; }

	static Subdivision of(Rect inner, long hash, Skel skel) {
		Subdivision s = new Subdivision(skel);
		s.split(inner, hash, 0, new int[] {-1, -1, -1, -1});
		return s;
	}

	/** {@code bnd}: ids of the streets bounding the block on its low x, high x, low z and high z side (-1: the edge of the whole area). */
	private void split(Rect r, long h, int level, int[] bnd) {
		boolean alongX = r.w() >= r.d(); // the long side runs along x: the new street is a line of constant x
		int start = alongX ? r.x() : r.z(), len = alongX ? r.w() : r.d(), crossStart = alongX ? r.z() : r.x(), crossLen = alongX ? r.d() : r.w();
		boolean stop = len < SPLIT_ABOVE || (len <= MAX_SIDE && Hash64.below(Hash64.hash(h, "stop"), 100) < STOP_PERCENT);
		int sw = level == 0 ? FIRST : LANE, hw = sw / 2;
		int cLo = Math.max(start + MIN_SIDE + hw, start + len * 35 / 100), cHi = Math.min(start + len - MIN_SIDE - (sw - hw), start + len * 65 / 100);
		if (stop || cLo > cHi) { leaves.add(r); return; }
		int c = cLo + Hash64.below(Hash64.hash(h, "pos"), cHi - cLo + 1);
		c = Math.floorDiv(c, 4) * 4;
		c = Math.max(cLo, Math.min(cHi, c));
		// an existing straight skeleton road across the whole block is the split: nothing to draw
		int roadLo = Integer.MIN_VALUE, roadHi = 0, bestDist = Integer.MAX_VALUE;
		for (int p = cLo; p <= cHi; p++) {
			if (Math.abs(p - c) >= bestDist) continue;
			boolean all = true;
			for (int k = crossStart; k < crossStart + crossLen && all; k++) all = skel.at(alongX ? p : k, alongX ? k : p);
			if (!all) continue;
			int a = p, b = p, mid = crossStart + crossLen / 2;
			while (a - 1 >= start && skel.at(alongX ? a - 1 : mid, alongX ? mid : a - 1)) a--;
			while (b + 1 < start + len && skel.at(alongX ? b + 1 : mid, alongX ? mid : b + 1)) b++;
			if (a - start < MIN_SIDE || start + len - (b + 1) < MIN_SIDE) continue;
			bestDist = Math.abs(p - c);
			roadLo = a;
			roadHi = b + 1;
		}
		Rect ra, rb;
		int id, lo, hi;
		if (roadLo != Integer.MIN_VALUE) {
			id = roadIds--;
			lo = roadLo;
			hi = roadHi;
		} else {
			// keep off the junctions the block across a bounding street already has (no four-way crossings)
			int endLo = alongX ? bnd[2] : bnd[0], endHi = alongX ? bnd[3] : bnd[1];
			int pick = c;
			boolean found = false;
			for (int o = 0; o <= cHi - cLo && !found; o++) {
				for (int sgn = -1; sgn <= 1 && !found; sgn += 2) {
					int p = c + sgn * o;
					if (p < cLo || p > cHi || (o == 0 && sgn > 0) || !clearOfJunction(endLo, 1, p) || !clearOfJunction(endHi, 0, p)) continue;
					pick = p;
					found = true;
				}
			}
			if (!found) {
				if (len <= MAX_SIDE) { leaves.add(r); return; }
				crossings++;
			}
			c = pick;
			id = lines.size();
			lo = c - hw;
			hi = c - hw + sw;
			lines.add(new Line(id, alongX, c, crossStart - 1, crossStart + crossLen, sw, level));
			if (endLo != -1) junctions.add(new int[] {endLo, 1, c});
			if (endHi != -1) junctions.add(new int[] {endHi, 0, c});
		}
		ra = alongX ? new Rect(r.x(), r.z(), lo - r.x(), r.d()) : new Rect(r.x(), r.z(), r.w(), lo - r.z());
		rb = alongX ? new Rect(hi, r.z(), r.x() + r.w() - hi, r.d()) : new Rect(r.x(), hi, r.w(), r.z() + r.d() - hi);
		int[] ba = alongX ? new int[] {bnd[0], id, bnd[2], bnd[3]} : new int[] {bnd[0], bnd[1], bnd[2], id}, bb = alongX ? new int[] {id, bnd[1], bnd[2], bnd[3]} : new int[] {bnd[0], bnd[1], id, bnd[3]};
		split(ra, Hash64.hash(h, "a"), level + 1, ba);
		split(rb, Hash64.hash(h, "b"), level + 1, bb);
	}

	/** No street already ends on the other side of the bounding street {@code bound} within {@value #JOG} blocks of {@code p}. */
	private boolean clearOfJunction(int bound, int side, int p) {
		if (bound == -1) return true;
		for (int[] j : junctions) if (j[0] == bound && j[1] != side && Math.abs(j[2] - p) < JOG) return false;
		return true;
	}
}
