package dev.fractalbuildings.core.settlement.tiers;

import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.Tiers;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.Rect;
import dev.fractalbuildings.core.settlement.Road;
import dev.fractalbuildings.core.settlement.Wall;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;

/**
 * The wall ring of a settlement (D2; D3 reuses it for old towns): a closed, axis-aligned rectangle round the hub, cut
 * into lots of the {@code wall} type (a gatehouse with two towers and a passage, curtains out to the lot's ends) and
 * {@code tower} lots at the four corners. Pure functions of the settlement's data; nothing here keeps state.
 *
 * <p>The ring is a polyline of four corners, clockwise from the north-west (z grows southward, so east, south, west,
 * north is clockwise on the plan). Side 0 runs west to east along {@code z = zt}, side 1 north to south along
 * {@code x = xr}, side 2 east to west, side 3 south to north. Outside is the left of the travel direction when z
 * points down: north for side 0, east for side 1, south for 2, west for 3, and each lot's front faces outside.
 *
 * <p>Every lot lies inside one district tile: the four ring coordinates keep {@link #CLEAR} blocks from the tile
 * lines, and the sides are cut at tile lines so no lot straddles one. A gate lot is exactly {@link #GATE_W} wide
 * (the narrowest {@code wall} lot, where the passage is centred, 5 wide on the gate column), and the road crosses it
 * on a straight perpendicular run of {@code 2 * reach} blocks; the other lots are {@link #MIN_LOT} to {@link #MAX_LOT}
 * long and {@link #MIN_DEPTH} to {@link #MAX_DEPTH} deep. A free stretch between two stations is never shorter than
 * {@link #MIN_LOT} (a gate or corner that would leave one is moved along the side).
 */
public final class WallRing {
	private WallRing() {}

	public static final int GATE_W = 23, TOWER = 12, MIN_LOT = 24, MAX_LOT = 64, MIN_DEPTH = 7, MAX_DEPTH = 11;
	/** Ring coordinates stay this far from tile lines (a tower is 12 wide, then a 24-long lot must fit before the line). */
	public static final int CLEAR = 30;
	/** Half-width of the passage columns on the gate column. */
	public static final int BAND = 2;
	/** Lot ids of the ring contain this mark: {@code <district>.w<n>}. */
	public static final String MARK = ".w";
	public static final String WALL = "wall", CURTAIN = "curtain", TOWER_TYPE = "bastion";

	/** Side scales tried, nearest the nominal ring first: indices into the scale list for the west, east, north and south sides. */
	private static final List<int[]> COMBOS = new ArrayList<>();

	static {
		for (int a = 0; a < 10; a++) for (int b = 0; b < 10; b++) for (int c = 0; c < 10; c++) for (int d = 0; d < 10; d++) COMBOS.add(new int[] {a, b, c, d});
		COMBOS.sort(java.util.Comparator.<int[]>comparingInt(q -> q[0] + q[1] + q[2] + q[3]).thenComparingInt(q -> q[0]).thenComparingInt(q -> q[1]).thenComparingInt(q -> q[2]).thenComparingInt(q -> q[3]));
	}

	/** The ring rectangle: its four lines. */
	public record Ring(int xl, int xr, int zt, int zb) {
		public List<Pt> corners() { return List.of(new Pt(xl, zt), new Pt(xr, zt), new Pt(xr, zb), new Pt(xl, zb)); }

		static Ring of(Wall w) {
			List<Pt> p = w.points();
			return new Ring(p.get(0).x(), p.get(1).x(), p.get(0).z(), p.get(2).z());
		}

		/** Coordinate of the side's line (z for sides 0 and 2, x for 1 and 3). */
		int line(int side) { return switch (side) { case 0 -> zt; case 1 -> xr; case 2 -> zb; default -> xl; }; }

		/** Along-axis extent of the side's centreline, from corner to corner, in increasing order. */
		int lo(int side) { return side % 2 == 0 ? xl : zt; }

		int hi(int side) { return side % 2 == 0 ? xr : zb; }

		/** Unit outward normal. */
		static int[] normal(int side) { return switch (side) { case 0 -> new int[] {0, -1}; case 1 -> new int[] {1, 0}; case 2 -> new int[] {0, 1}; default -> new int[] {-1, 0}; }; }

		static Facing facing(int side) { return switch (side) { case 0 -> Facing.NORTH; case 1 -> Facing.EAST; case 2 -> Facing.SOUTH; default -> Facing.WEST; }; }
	}

	/** A gate on a side: the along-axis coordinate of the gate column, and the column on the ring line. */
	/** Unit outward normal of a side (north, east, south, west). */
	public static int[] normal(int side) { return Ring.normal(side); }

	public record Gate(int side, int along, Pt at) {}

	/** A lot of the ring: {@code wall} or {@code tower}, its rectangle, front, and for a gate lot the gate column's along coordinate (else {@link #NONE}). */
	public record Piece(String type, Rect rect, Facing front, int gate) {}

	/** {@link Piece#gate} of a lot that is no gate lot. */
	public static final int NONE = Integer.MIN_VALUE;

	// ---- the rectangle

	/**
	 * Looks for the ring: starting at 0.5 to 0.7 of the radius in each axis, then pulling each side in on its own (down to 0.3 of that), the first rectangle whose centrelines
	 * (and the two lines 6 blocks to each side of them) are {@code ok} everywhere, mostly {@code urban}, at least 100 across, and round the hub.
	 *
	 * @param ok may a wall stand on this column (dry, buildable, owned by the caller's cell)
	 * @param urban does the column lie in the urban footprint
	 * @param step lattice step of the roads; ring coordinates are multiples of it
	 */
	public static Optional<Ring> ring(long seed, Pt centre, int radius, Pt hub, DistrictGrid grid, int step, BiPredicate<Integer, Integer> ok, BiPredicate<Integer, Integer> urban) {
		double fx = 0.5 + 0.2 * Tiers.unit(Hash64.hash(seed, "ring-fx", centre.x(), centre.z()));
		double fz = 0.5 + 0.2 * Tiers.unit(Hash64.hash(seed, "ring-fz", centre.x(), centre.z()));
		// each side takes its own scale (a river or a cliff on one side pulls only that side in); nearest to the nominal ring first
		double[] scales = {1.0, 0.93, 0.86, 0.8, 0.74, 0.68, 0.6, 0.5, 0.4, 0.3};
		for (int[] q : COMBOS) {
			int xl = clear(DistrictGrid.AXIS_X, snapTo(centre.x() - snap(fx * scales[q[0]] * radius, step), step), step, 1, grid), xr = clear(DistrictGrid.AXIS_X, snapTo(centre.x() + snap(fx * scales[q[1]] * radius, step), step), step, -1, grid);
			int zt = clear(DistrictGrid.AXIS_Z, snapTo(centre.z() - snap(fz * scales[q[2]] * radius, step), step), step, 1, grid), zb = clear(DistrictGrid.AXIS_Z, snapTo(centre.z() + snap(fz * scales[q[3]] * radius, step), step), step, -1, grid);
			if (xl == Integer.MIN_VALUE || xr == Integer.MIN_VALUE || zt == Integer.MIN_VALUE || zb == Integer.MIN_VALUE) continue;
			if (xr - xl < 100 || zb - zt < 100) continue;
			if (hub.x() < xl + 30 || hub.x() > xr - 30 || hub.z() < zt + 30 || hub.z() > zb - 30) continue;
			Ring r = new Ring(xl, xr, zt, zb);
			if (fits(r, step, ok, urban)) return Optional.of(r);
		}
		return Optional.empty();
	}

	private static int snap(double v, int step) { return (int) (Math.round(v / step) * step); }

	private static int snapTo(int v, int step) { return Math.floorDiv(v + step / 2, step) * step; }

	/** The nearest multiple of step to c, trying the preferred direction first, that keeps {@link #CLEAR} from tile lines on the axis; MIN_VALUE if none within 8 steps. */
	private static int clear(int axis, int c, int step, int prefer, DistrictGrid grid) {
		for (int k = 0; k <= 8; k++) {
			for (int sign : new int[] {prefer, -prefer}) {
				int v = c + sign * k * step;
				if (lineDist(grid, axis, v) >= CLEAR) return v;
			}
		}
		return Integer.MIN_VALUE;
	}

	/** Distance from a coordinate to the nearest tile line on the axis. */
	public static int lineDist(DistrictGrid grid, int axis, int c) {
		int k = grid.indexOf(axis, c);
		return Math.min(Math.abs(c - grid.lineBlock(axis, k)), Math.abs(grid.lineBlock(axis, k + 1) - c));
	}

	/** Do the ring's centrelines (and the lines 6 blocks to each side) pass {@code ok} everywhere, and are most of them {@code urban}? */
	public static boolean fits(Ring r, int step, BiPredicate<Integer, Integer> ok, BiPredicate<Integer, Integer> urban) {
		int total = 0, urb = 0;
		for (int side = 0; side < 4; side++) {
			int[] n = Ring.normal(side);
			boolean alongX = side % 2 == 0;
			for (int a = r.lo(side); a <= r.hi(side); a += Math.min(step, 8)) {
				for (int off = -6; off <= 6; off += 6) {
					int x = alongX ? a : r.line(side) + off, z = alongX ? r.line(side) + off : a;
					if (!ok.test(x, z)) return false;
				}
				total++;
				if (urban.test(alongX ? a : r.line(side), alongX ? r.line(side) : a)) urb++;
			}
		}
		return urb * 10 >= total * 7;
	}

	// ---- gates

	/**
	 * The gate each target needs, in target order (null for a target inside the ring): the place the straight line from the hub to
	 * the target leaves the ring, moved along the side to the nearest spot where the lots still fit. Empty if some target lies within
	 * 24 blocks of the ring (neither clearly in nor out), or a gate has no valid place.
	 *
	 * @param reach half-length of the straight perpendicular road run; the columns {@code reach} out on each side of the gate must be {@code ok}
	 */
	public static Optional<List<Gate>> gates(Ring r, Pt hub, List<Pt> targets, DistrictGrid grid, int step, int reach, BiPredicate<Integer, Integer> ok) {
		List<Gate> out = new ArrayList<>();
		List<Gate> placed = new ArrayList<>();
		for (Pt t : targets) {
			boolean outside = t.x() < r.xl() - 24 || t.x() > r.xr() + 24 || t.z() < r.zt() - 24 || t.z() > r.zb() + 24;
			boolean inside = t.x() > r.xl() + 24 && t.x() < r.xr() - 24 && t.z() > r.zt() + 24 && t.z() < r.zb() - 24;
			if (inside) { out.add(null); continue; }
			if (!outside) return Optional.empty();
			Gate g = place(r, hub, t, placed, grid, step, reach, ok);
			if (g == null) return Optional.empty();
			placed.add(g);
			out.add(g);
		}
		return Optional.of(out);
	}

	private static Gate place(Ring r, Pt hub, Pt t, List<Gate> placed, DistrictGrid grid, int step, int reach, BiPredicate<Integer, Integer> ok) {
		double dx = t.x() - hub.x(), dz = t.z() - hub.z();
		double tx = dx > 0 ? (r.xr() - hub.x()) / dx : dx < 0 ? (r.xl() - hub.x()) / dx : Double.MAX_VALUE;
		double tz = dz > 0 ? (r.zb() - hub.z()) / dz : dz < 0 ? (r.zt() - hub.z()) / dz : Double.MAX_VALUE;
		int side;
		double along;
		if (tx <= tz) { side = dx > 0 ? 1 : 3; along = hub.z() + tx * dz; }
		else { side = dz > 0 ? 2 : 0; along = hub.x() + tz * dx; }
		int a0 = snapTo((int) Math.round(along), step);
		for (int k = 0; k <= 12; k++) {
			for (int sign : k == 0 ? new int[] {0} : new int[] {1, -1}) {
				int a = a0 + sign * k * step;
				if (!valid(r, side, a, placed, grid)) continue;
				Gate g = new Gate(side, a, side % 2 == 0 ? new Pt(a, r.line(side)) : new Pt(r.line(side), a));
				if (reachOk(r, g, reach, ok)) return g;
			}
		}
		return null;
	}

	private static boolean reachOk(Ring r, Gate g, int reach, BiPredicate<Integer, Integer> ok) {
		int[] n = Ring.normal(g.side());
		for (int k = -reach; k <= reach; k++) if (!ok.test(g.at().x() + n[0] * k, g.at().z() + n[1] * k)) return false;
		return true;
	}

	/** May a gate at along coordinate a join the gates already on its side? */
	private static boolean valid(Ring r, int side, int a, List<Gate> placed, DistrictGrid grid) {
		List<Integer> gs = new ArrayList<>();
		for (Gate g : placed) if (g.side() == side) gs.add(g.along());
		gs.add(a);
		return spans(r, side, gs, grid) != null;
	}

	/** Valid iff every gate column set is acceptable on the side (used on the crossings of final roads). */
	public static boolean valid(Ring r, List<Gate> gates, DistrictGrid grid) {
		for (int side = 0; side < 4; side++) {
			List<Integer> gs = new ArrayList<>();
			for (Gate g : gates) if (g.side() == side) gs.add(g.along());
			if (spans(r, side, gs, grid) == null) return false;
		}
		return true;
	}

	// ---- stations and spans

	/** A stretch of a side: {@code [from, to)} along the axis; {@code gate} is the gate column, or Integer.MIN_VALUE for a free stretch. */
	record Span(int from, int to, int gate) {}

	/**
	 * Cuts the side between its two corner towers into gate lots, and free stretches between tile lines, gates and the towers.
	 * Null if a gate lot would cross a tile line or leave the side, or a free stretch is shorter than {@link #MIN_LOT}.
	 */
	static List<Span> spans(Ring r, int side, List<Integer> gates, DistrictGrid grid) {
		int a0 = r.lo(side) + TOWER / 2, a1 = r.hi(side) - TOWER / 2;
		int axis = side % 2 == 0 ? DistrictGrid.AXIS_X : DistrictGrid.AXIS_Z;
		TreeSet<Integer> cuts = new TreeSet<>();
		for (int k = grid.indexOf(axis, a0) - 1; grid.lineBlock(axis, k) < a1 + 1; k++) {
			int l = grid.lineBlock(axis, k);
			if (l > a0 && l < a1) cuts.add(l);
		}
		TreeSet<Integer> sortedGates = new TreeSet<>(gates);
		if (sortedGates.size() != gates.size()) return null;
		List<int[]> gl = new ArrayList<>();
		int prevEnd = Integer.MIN_VALUE;
		for (int g : sortedGates) {
			int from = g - GATE_W / 2, to = from + GATE_W;
			if (from < a0 || to > a1 || from < prevEnd) return null;
			for (int c : cuts) if (c > from && c < to) return null;
			gl.add(new int[] {from, to, g});
			prevEnd = to;
		}
		TreeSet<Integer> b = new TreeSet<>(cuts);
		b.add(a0);
		b.add(a1);
		for (int[] g : gl) { b.add(g[0]); b.add(g[1]); }
		List<Span> out = new ArrayList<>();
		Integer prev = null;
		for (int p : b) {
			if (prev != null) {
				int gate = Integer.MIN_VALUE;
				for (int[] g : gl) if (g[0] == prev && g[1] == p) gate = g[2];
				if (gate == Integer.MIN_VALUE && p - prev < MIN_LOT) return null;
				out.add(new Span(prev, p, gate));
			}
			prev = p;
		}
		return out;
	}

	// ---- lots

	/** The lots of the whole ring, clockwise from the north-west tower; empty if the gates do not fit (they do for gates that {@link #gates} placed). */
	public static List<Piece> pieces(Wall wall, List<Pt> gatePts, DistrictGrid grid, long seed) {
		Ring r = Ring.of(wall);
		List<Gate> gates = new ArrayList<>();
		for (Pt p : gatePts) gates.add(gateAt(r, p));
		List<Piece> out = new ArrayList<>();
		for (int side = 0; side < 4; side++) {
			Pt c = r.corners().get(side);
			out.add(new Piece(TOWER_TYPE, new Rect(c.x() - TOWER / 2, c.z() - TOWER / 2, TOWER, TOWER), Ring.facing(side), NONE));
			List<Integer> gs = new ArrayList<>();
			for (Gate g : gates) if (g != null && g.side() == side) gs.add(g.along());
			List<Span> spans = spans(r, side, gs, grid);
			if (spans == null) throw new IllegalArgumentException("ring side " + side + " cannot take gates " + gs);
			for (Span sp : spans) {
				int n = sp.gate() != Integer.MIN_VALUE ? 1 : (sp.to() - sp.from() + MAX_LOT - 1) / MAX_LOT;
				int len = sp.to() - sp.from(), at = sp.from();
				for (int i = 0; i < n; i++) {
					int l = len / n + (i < len % n ? 1 : 0);
					int depth = Hash64.range(Hash64.hash(seed, "wall-depth", side, at, r.line(side)), MIN_DEPTH, MAX_DEPTH);
					Rect rect = side % 2 == 0 ? new Rect(at, r.line(side) - depth / 2, l, depth) : new Rect(r.line(side) - depth / 2, at, depth, l);
					boolean gate = sp.gate() != Integer.MIN_VALUE;
					out.add(new Piece(gate ? WALL : CURTAIN, rect, Ring.facing(side), gate ? sp.gate() : NONE));
					at += l;
				}
			}
		}
		return out;
	}

	private static Gate gateAt(Ring r, Pt p) {
		if (p.z() == r.zt() && p.x() > r.xl() && p.x() < r.xr()) return new Gate(0, p.x(), p);
		if (p.x() == r.xr() && p.z() > r.zt() && p.z() < r.zb()) return new Gate(1, p.z(), p);
		if (p.z() == r.zb() && p.x() > r.xl() && p.x() < r.xr()) return new Gate(2, p.x(), p);
		if (p.x() == r.xl() && p.z() > r.zt() && p.z() < r.zb()) return new Gate(3, p.z(), p);
		throw new IllegalArgumentException("gate " + p + " is not on the ring");
	}

	/** The passage columns of a gate lot as a rectangle: {@link #BAND} each side of the gate column, through the lot's depth. */
	public static Rect band(Piece p) {
		Rect r = p.rect();
		boolean alongX = p.front() == Facing.NORTH || p.front() == Facing.SOUTH;
		return alongX ? new Rect(p.gate() - BAND, r.z(), 2 * BAND + 1, r.d()) : new Rect(r.x(), p.gate() - BAND, r.w(), 2 * BAND + 1);
	}

	/** The rectangles of a piece that streets must keep out of: the lot, less the passage band for a gate lot. */
	public static List<Rect> blocked(Piece p) {
		Rect r = p.rect();
		if (p.gate() == NONE) return List.of(r);
		boolean alongX = p.front() == Facing.NORTH || p.front() == Facing.SOUTH;
		List<Rect> out = new ArrayList<>();
		if (alongX) {
			out.add(new Rect(r.x(), r.z(), p.gate() - BAND - r.x(), r.d()));
			out.add(new Rect(p.gate() + BAND + 1, r.z(), r.x() + r.w() - (p.gate() + BAND + 1), r.d()));
		} else {
			out.add(new Rect(r.x(), r.z(), r.w(), p.gate() - BAND - r.z()));
			out.add(new Rect(r.x(), p.gate() + BAND + 1, r.w(), r.z() + r.d() - (p.gate() + BAND + 1)));
		}
		return out;
	}

	/** Is this lot a gate lot, by its shape alone: a {@code wall} lot exactly {@link #GATE_W} long. */
	public static boolean isGateShape(String type, Facing front, int w, int d) {
		return type.equals(WALL) && ((front == Facing.NORTH || front == Facing.SOUTH) ? w : d) == GATE_W;
	}

	/** Is this lot id a ring lot ({@code <district>.w<n>})? */
	public static boolean isRingLotId(String id) {
		int i = id.indexOf(MARK);
		return i >= 0 && i + MARK.length() < id.length() && Character.isDigit(id.charAt(i + MARK.length()));
	}


	// ---- the walk profile (slope step)

	/** Most blocks a curtain walk may stand above the tower doorway it meets: it carves a stair flight down through its own end (down to just under the gallery floor: it cuts into its own foundation). */
	public static final int MAX_CARVE = 9;

	/**
	 * May two neighbouring ring lots stand at these levels? Curtains differ by at most {@link Brief#MAX_STEP} (the lower one climbs a ramp on top of its walk).
	 * A gate lot or corner bastion meets its neighbour by a doorway at its own walk level: against a curtain it may stand up to {@link Brief#MAX_STEP} below
	 * it (the curtain ramps up to the door) or up to {@link #MAX_CARVE} above it (the curtain's walk descends in a stair cut through its end);
	 * two towers side by side are level.
	 */
	public static boolean joined(String typeA, int a, String typeB, int b) {
		boolean ta = !typeA.equals(CURTAIN), tb = !typeB.equals(CURTAIN);
		if (ta && tb) return a == b;
		if (!ta && !tb) return Math.abs(a - b) <= Brief.MAX_STEP;
		int tower = ta ? a : b, curtain = ta ? b : a;
		return tower - curtain <= Brief.MAX_STEP && curtain - tower <= MAX_CARVE;
	}

	/**
	 * The level (world y of the ground slab) of every lot of the ring, in {@code pieces} order, so that the wall walk is continuous on sloping ground.
	 * Each level is at least the lot's own highest ground ({@code top}), so no lot is sunk into a hillside, and neighbours are {@link #joined}. Levels only
	 * rise from the ground, and only as much as the rules need, so a gate lot stays at its own ground (where the road passes) unless a neighbour forces it.
	 * The least such levels: a pure function of the ring and the ground, so every district tile that holds part of the ring gets the same profile.
	 */
	public static int[] walkLevels(List<Piece> pieces, java.util.function.ToIntFunction<Piece> top) {
		int n = pieces.size();
		int[] lv = new int[n];
		for (int i = 0; i < n; i++) lv[i] = top.applyAsInt(pieces.get(i));
		if (n < 2) return lv;
		int[] cw = clockwise(pieces.stream().map(Piece::type).toList());
		boolean changed = true;
		while (changed) {
			changed = false;
			for (int c = 0; c < n; c++) {
				int i = cw[c], j = cw[(c + 1) % n];
				String ti = pieces.get(i).type(), tj = pieces.get(j).type();
				boolean toweri = !ti.equals(CURTAIN), towerj = !tj.equals(CURTAIN);
				int x = lv[i], y = lv[j];
				if (toweri && towerj) { x = y = Math.max(x, y); }
				else if (toweri != towerj) {
					int tw = toweri ? x : y, cu = toweri ? y : x;
					tw = Math.max(tw, cu - MAX_CARVE);
					cu = Math.max(cu, tw - Brief.MAX_STEP);
					if (toweri) { x = tw; y = cu; } else { y = tw; x = cu; }
				} else {
					x = Math.max(x, y - Brief.MAX_STEP);
					y = Math.max(y, x - Brief.MAX_STEP);
				}
				if (x != lv[i] || y != lv[j]) { lv[i] = x; lv[j] = y; changed = true; }
			}
		}
		return lv;
	}

	/**
	 * The walk of piece {@code i}, given the {@link #walkLevels}: its base level and what its walk does at each end: a positive step is a ramp up of that many
	 * blocks to a higher neighbour, a negative one a stair cut down through a curtain's end to a lower tower; no work against a lower curtain (that one climbs).
	 * 'next' is the clockwise end, 'prev' the anticlockwise one.
	 */
	public static dev.fractalbuildings.core.gen.Brief.Walk walkOf(List<String> types, int[] lv, int i) {
		int n = lv.length;
		int[] cw = clockwise(types);
		int c = 0;
		while (cw[c] != i) c++;
		int p = cw[(c + n - 1) % n], q = cw[(c + 1) % n];
		return new dev.fractalbuildings.core.gen.Brief.Walk(lv[i], work(types.get(p), lv[p] - lv[i]), work(types.get(q), lv[q] - lv[i]));
	}

	private static int work(String neighbourType, int delta) { return delta < 0 && neighbourType.equals(CURTAIN) ? 0 : delta; }

	/**
	 * Indices of the pieces in clockwise order round the ring, from the north-west tower: {@link #pieces} lists each side's lots in increasing
	 * coordinate, which is clockwise on the north and east sides and against it on the south and west sides (a lot's local +x always points clockwise).
	 */
	public static int[] clockwise(List<String> types) {
		int n = types.size();
		int[] out = new int[n];
		int k = 0, side = -1;
		for (int i = 0; i < n;) {
			if (types.get(i).equals(TOWER_TYPE)) { side++; out[k++] = i++; }
			int j = i;
			while (j < n && !types.get(j).equals(TOWER_TYPE)) j++;
			if (side >= 2) for (int m = j - 1; m >= i; m--) out[k++] = m;
			else for (int m = i; m < j; m++) out[k++] = m;
			i = j;
		}
		return out;
	}

	// ---- roads

	/**
	 * The gates the roads make: every place a road crosses the ring, as long as the road runs straight and perpendicular for
	 * {@code reach} blocks each side of it. Null when some crossing is not like that (a road clips a corner, or wanders back across).
	 */
	/** A gate found on a road. */
	public record Crossing(Gate gate, String road) {}

	public static List<Crossing> crossings(Ring r, List<Road> roads, int reach) {
		List<Crossing> out = new ArrayList<>();
		Set<Long> seen = new TreeSet<>();
		for (Road road : roads) {
			List<Pt> cols = columns(road.points());
			Set<Long> set = new TreeSet<>();
			for (Pt p : cols) set.add(key(p.x(), p.z()));
			boolean prevIn = false;
			for (int i = 0; i < cols.size(); i++) {
				Pt p = cols.get(i);
				boolean in = p.x() >= r.xl() && p.x() <= r.xr() && p.z() >= r.zt() && p.z() <= r.zb();
				if (i > 0 && in != prevIn) {
					Pt edge = in ? p : cols.get(i - 1); // the inside column next to the outside one
					Gate g = gateOf(r, edge);
					if (g == null || !straight(set, g, reach)) return null;
					if (seen.add(key(g.at().x(), g.at().z()))) out.add(new Crossing(g, road.id()));
				}
				prevIn = in;
			}
		}
		return out;
	}

	private static Gate gateOf(Ring r, Pt p) {
		boolean onX = p.x() == r.xl() || p.x() == r.xr(), onZ = p.z() == r.zt() || p.z() == r.zb();
		if (onX == onZ) return null; // inside the ring, or on a corner
		if (onZ) return new Gate(p.z() == r.zt() ? 0 : 2, p.x(), p);
		return new Gate(p.x() == r.xr() ? 1 : 3, p.z(), p);
	}

	private static boolean straight(Set<Long> cols, Gate g, int reach) {
		int[] n = Ring.normal(g.side());
		for (int k = -reach; k <= reach; k++) if (!cols.contains(key(g.at().x() + n[0] * k, g.at().z() + n[1] * k))) return false;
		return true;
	}

	private static long key(int x, int z) { return ((long) z << 32) ^ (x & 0xffffffffL); }

	/** Every column along the polyline, point to point, no repeats between segments. */
	static List<Pt> columns(List<Pt> pts) {
		List<Pt> out = new ArrayList<>();
		for (int i = 1; i < pts.size(); i++) {
			Pt a = pts.get(i - 1), b = pts.get(i);
			int n = Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z())), sx = Integer.signum(b.x() - a.x()), sz = Integer.signum(b.z() - a.z());
			for (int k = i == 1 ? 0 : 1; k <= n; k++) out.add(new Pt(a.x() + sx * k, a.z() + sz * k));
		}
		return out;
	}

	public static Wall wall(String id, Ring r) { return new Wall(id, r.corners(), true); }

	public static Ring ring(Wall w) { return Ring.of(w); }
}
