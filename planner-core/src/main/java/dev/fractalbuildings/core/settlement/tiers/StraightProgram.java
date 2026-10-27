package dev.fractalbuildings.core.settlement.tiers;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.settlement.CellGeometry;
import dev.fractalbuildings.core.settlement.DefaultProgram;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.SkeletonContext;
import java.util.ArrayList;
import java.util.List;

/**
 * What hamlets and villages share (D1): the main street is one straight axis-aligned line through the hub along the gentler of
 * the two axes, so district detail can line it with frontage lots (lots need straight runs), and an optional cross street meets
 * it at a junction on the street itself. The layout is a pure function of the cell context. Roads are connected to the hub (or
 * the main street) by construction: no pathfinding, so none can detour or drop out. Cells that do not hold the site centre, and
 * routes to cell gates, fall back to {@link DefaultProgram}.
 */
abstract class StraightProgram extends DefaultProgram {
	/** Sampling step along a line, blocks. */
	static final int SAMPLE = 8;
	/** Steepest accepted change in surface height over one 32-block coarse step (1 in 3). */
	static final int MAX_DY = 10;

	/** The main street ends {@code a} (negative side) and {@code b}, the cross street's junction and ends, null where absent. */
	record Layout(boolean alongX, Pt start, Pt a, Pt b, Pt junction, Pt c1, Pt c2) {}

	/** Shortest total main street that is accepted, blocks. */
	abstract int minLength();

	/** Half the longest main street, blocks (the street may sit lopsided about the hub when one side runs out of urban ground). */
	abstract int halfLength(SkeletonContext c);

	/** Does this site get a cross street? */
	abstract boolean crossStreet(SkeletonContext c);

	/** Longest cross street arm, blocks. */
	int crossReach(SkeletonContext c) { return 0; }

	/** Blocks walked from (x, z) along (dx, dz) while the columns stay owned, urban and gentle; also adds the height change walked to {@code rough[0]}. */
	static int walk(SkeletonContext c, int x, int z, int dx, int dz, int max, int[] rough) {
		int n = 0, px = x, pz = z;
		while (n + SAMPLE <= max) {
			int qx = px + dx * SAMPLE, qz = pz + dz * SAMPLE;
			if (!CellGeometry.owns(c.field, c.cell, qx, qz) || !c.urbanCandidate(qx, qz) || !c.tileOwned(qx, qz)) break; // ROADJOIN step 3: a street ends where the tiles this settlement plans end
			int dy = Math.abs(c.terrain.surfaceY(qx, qz) - c.terrain.surfaceY(px, pz));
			if (dy > MAX_DY) break;
			rough[0] += dy;
			px = qx;
			pz = qz;
			n += SAMPLE;
		}
		return n;
	}

	/** Distance kept between a street's centre line and the border of the tile (the hub square needs 14 + 14 blocks to its side). */
	static final int MAX_SHIFT = 16;
	/** Distance kept between the cross street's centre line and a tile border. */
	static final int CROSS_CLEAR = 12;

	/** The hub moved until the street and the hub square keep {@link #BORDER_CLEAR} from the tile border. */
	Pt anchor(SkeletonContext c, Pt hub) {
		int k = borderClear();
		Pt best = hub;
		long bestD = Long.MAX_VALUE;
		// a hub on a tile border belongs to the tile on its positive side; the tile on the other side may be the better one
		for (int[] o : new int[][] {{0, 0}, {-1, 0}, {0, -1}, {-1, -1}}) {
			var t = c.grid.tileAt(hub.x() + o[0], hub.z() + o[1]);
			Pt a = new Pt(Math.max(t.minX() + k, Math.min(t.maxX() - k, hub.x())), Math.max(t.minZ() + k, Math.min(t.maxZ() - k, hub.z())));
			if (!CellGeometry.owns(c.field, c.cell, a.x(), a.z()) || !c.urbanCandidate(a.x(), a.z())) continue; // never leave the cell or the footprint
			long d = (long) (a.x() - hub.x()) * (a.x() - hub.x()) + (long) (a.z() - hub.z()) * (a.z() - hub.z());
			if (d < bestD) { bestD = d; best = a; }
		}
		return best;
	}

	/** Distance the street and the hub square keep from the tile border. */
	abstract int borderClear();

	/** Does the program place a square at the street start that needs level, dry ground? */
	boolean flatSquare() { return false; }

	/** Half the side of the ground a square at the street start needs. */
	static final int SQUARE_REACH = 14;

	/**
	 * The start point along the street line, nearest to {@code s} first, whose square sits on the flattest coarse ground (no water, at
	 * most 2 blocks of relief over its corners and centre; else the least). District detail moves a square it cannot grade, and a square
	 * moved sideways off the street would leave the hall facing it cut off.
	 */
	Pt flattest(SkeletonContext c, Pt s, int dx, int dz) {
		Pt best = s;
		int bestRelief = Integer.MAX_VALUE;
		int[] offs = {0, 16, -16, 32, -32, 48, -48};
		var t = c.grid.tileAt(s.x(), s.z());
		int k = borderClear();
		for (int o : offs) {
			Pt p = new Pt(s.x() + dx * o, s.z() + dz * o);
			if (p.x() < t.minX() + k || p.x() > t.maxX() - k || p.z() < t.minZ() + k || p.z() > t.maxZ() - k) continue;
			if (!CellGeometry.owns(c.field, c.cell, p.x(), p.z()) || !c.urbanCandidate(p.x(), p.z())) continue;
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			boolean wet = false;
			for (int iz = -1; iz <= 1; iz++) {
				for (int ix = -1; ix <= 1; ix++) {
					int x = p.x() + ix * SQUARE_REACH, z = p.z() + iz * SQUARE_REACH;
					if (c.terrain.water(x, z)) wet = true;
					int y = c.terrain.floorY(x, z);
					lo = Math.min(lo, y);
					hi = Math.max(hi, y);
				}
			}
			int relief = wet ? 1000 : hi - lo;
			if (relief < bestRelief) { bestRelief = relief; best = p; }
			if (relief <= 2) break;
		}
		return best;
	}

	/** Is the coordinate {@code v} on axis {@code alongX ? x : z} at least {@link #CROSS_CLEAR} from the borders of the tile around (x, z)? */
	static boolean clearOfBorder(SkeletonContext c, int x, int z, boolean xCoord) {
		var t = c.grid.tileAt(x, z);
		int v = xCoord ? x : z, lo = xCoord ? t.minX() : t.minZ(), hi = xCoord ? t.maxX() : t.maxZ();
		return v - lo >= CROSS_CLEAR && hi - v >= CROSS_CLEAR;
	}

	Layout layout(SkeletonContext c, Pt hub) {
		if (hub == null || !c.holdsCentre()) return null;
		long h = Hash64.hash(c.seed, "straight-layout", c.site.centre().x(), c.site.centre().z());
		int half = halfLength(c), total = 2 * half;
		int[][] len = new int[2][2];
		double[] rough = new double[2];
		Pt[] start = new Pt[2];
		int[] shift = new int[2];
		for (int axis = 0; axis < 2; axis++) {
			int dx = axis == 0 ? 1 : 0, dz = axis == 0 ? 0 : 1;
			start[axis] = anchor(c, hub);
			shift[axis] = axis == 0 ? Math.abs(start[axis].z() - hub.z()) : Math.abs(start[axis].x() - hub.x());
			int[] r = {0};
			len[axis][0] = walk(c, start[axis].x(), start[axis].z(), -dx, -dz, total, r);
			len[axis][1] = walk(c, start[axis].x(), start[axis].z(), dx, dz, total, r);
			int sum = len[axis][0] + len[axis][1];
			rough[axis] = sum == 0 ? 1e9 : (double) r[0] / sum;
		}
		int tx = Math.min(total, len[0][0] + len[0][1]), tz = Math.min(total, len[1][0] + len[1][1]);
		boolean okX = tx >= minLength() && shift[0] <= MAX_SHIFT, okZ = tz >= minLength() && shift[1] <= MAX_SHIFT;
		if (!okX && !okZ) { okX = tx >= minLength(); okZ = tz >= minLength(); }
		boolean alongX;
		if (okX && okZ) alongX = rough[0] != rough[1] ? rough[0] < rough[1] : Hash64.below(Hash64.hash(h, "axis"), 2) == 0;
		else if (okX || okZ) alongX = okX;
		else alongX = tx != tz ? tx > tz : Hash64.below(Hash64.hash(h, "axis"), 2) == 0;
		int axis = alongX ? 0 : 1, dx = alongX ? 1 : 0, dz = alongX ? 0 : 1;
		Pt s = start[axis];
		int la = len[axis][0], lb = len[axis][1];
		if (flatSquare()) {
			Pt moved = flattest(c, s, dx, dz);
			if (!moved.equals(s)) {
				s = moved;
				int[] r = {0};
				la = walk(c, s.x(), s.z(), -dx, -dz, total, r);
				lb = walk(c, s.x(), s.z(), dx, dz, total, r);
			}
		}
		if (la + lb > total) { // keep the shorter side, the longer one gives the rest
			int lo = Math.min(Math.min(la, lb), half);
			if (la <= lb) { la = lo; lb = total - lo; } else { lb = lo; la = total - lo; }
		}
		if (la + lb < SAMPLE) return null;
		Pt a = la >= SAMPLE ? new Pt(s.x() - dx * la, s.z() - dz * la) : null;
		Pt b = lb >= SAMPLE ? new Pt(s.x() + dx * lb, s.z() + dz * lb) : null;
		Pt junction = null, c1 = null, c2 = null;
		if (crossStreet(c)) {
			int t0 = 40 + Hash64.below(Hash64.hash(h, "junction"), 51);
			int sgn0 = Hash64.below(Hash64.hash(h, "junction-side"), 2) == 0 ? 1 : -1;
			int[] nudge = {0, 12, -12, 24, -24};
			for (int side = 0; side < 2 && junction == null; side++) {
				int sgn = side == 0 ? sgn0 : -sgn0;
				for (int nd : nudge) {
					int t = t0 + nd;
					if (t < 36 || (sgn > 0 ? lb : la) < t + 16) continue;
					Pt j = new Pt(s.x() + dx * sgn * t, s.z() + dz * sgn * t);
					if (!clearOfBorder(c, j.x(), j.z(), alongX)) continue;
					int[] r = {0};
					int n1 = walk(c, j.x(), j.z(), -dz, -dx, crossReach(c), r), n2 = walk(c, j.x(), j.z(), dz, dx, crossReach(c), r);
					if (n1 < 16 && n2 < 16) continue;
					junction = j;
					c1 = n1 >= 16 ? new Pt(j.x() - dz * n1, j.z() - dx * n1) : null;
					c2 = n2 >= 16 ? new Pt(j.x() + dz * n2, j.z() + dx * n2) : null;
					break;
				}
			}
		}
		return new Layout(alongX, s, a, b, junction, c1, c2);
	}

	@Override public List<Pt> exits(SkeletonContext c, Pt hub) {
		Layout l = layout(c, hub);
		if (l == null) return super.exits(c, hub);
		List<Pt> out = new ArrayList<>();
		for (Pt p : new Pt[] {l.a(), l.b(), l.c1(), l.c2()}) if (p != null) out.add(p);
		return out;
	}

	@Override public List<Route> mainRoads(SkeletonContext c, Pt hub, List<Pt> targets) {
		Layout l = layout(c, hub);
		List<Route> out = new ArrayList<>();
		for (Pt t : targets) {
			if (l != null && (t.equals(l.a()) || t.equals(l.b()))) out.add(new Route(t, List.of(l.start(), t), List.of()));
			else if (l != null && l.junction() != null && (t.equals(l.c1()) || t.equals(l.c2()))) out.add(new Route(t, List.of(l.junction(), t), List.of()));
			else out.add(route(c, hub, t));
		}
		return out;
	}
}
