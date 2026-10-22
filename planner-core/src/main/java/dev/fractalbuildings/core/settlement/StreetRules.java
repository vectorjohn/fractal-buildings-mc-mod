package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The pointwise street rules of district detail (plan step DL, "Answers: city layout"): every answer depends only on the
 * seed, the settlement and a position, never on which tile asks, so neighbouring tiles agree on a shared border without
 * talking.
 *
 * <ul>
 * <li><b>Border streets</b>: a tile-border segment that is not arterial gets a plain street (width {@value #BORDER_WIDTH})
 * when both its ends are urban, it crosses no water run over 40 blocks and the layout at its midpoint is the grid.</li>
 * <li><b>Grid lines</b>: the inner grid streets on an axis are keyed by (seed, axis, tile interval) only, so they run
 * straight through the whole column (or row) of tiles; a line exists where the node test holds on both ends of an
 * {@value #NODE}-block edge.</li>
 * <li><b>Ports</b>: a border segment with no street gets 0 to 2 hashed port points where lanes cross into the neighbour,
 * used when both sides are urban frontage land.</li>
 * </ul>
 */
final class StreetRules {
	static final int BORDER_WIDTH = 5, GRID_WIDTH = 5, LANE_WIDTH = 3;
	/** Spacing of grid and port nodes along a line. */
	static final int NODE = 8;
	/** Smallest and largest distance between neighbouring grid lines (tile border to line to line). */
	static final int MIN_PITCH = 62, MAX_PITCH = 100;
	/** Edges whose ends differ by more than this are too steep for a grid street. */
	static final int EDGE_RISE = 2;

	private final long seed;
	private final Tier tier;
	private final DensityField density;
	private final QuarterField quarters;
	private final DistrictGrid grid;
	private final TierProgram program;
	private final Arterials arterials;
	private final TreeMap<String, RoadKind> borderMemo = new TreeMap<>();
	private final SkeletonSource src;
	private RoadNetwork network;

	StreetRules(SkeletonSource src) {
		this.src = src;
		this.seed = src.seed();
		this.tier = src.site().tier();
		this.density = src.density();
		this.quarters = src.quarters();
		this.grid = src.grid();
		this.program = src.program();
		this.arterials = src.density().arterials();
	}

	Arterials arterials() { return arterials; }

	/** Border streets come from the settlement's street lattice (city and metropolis): drawn whole, attached by construction. */
	boolean lattice() {
		if (!RoadNetwork.latticeTier(tier)) return false;
		if (network == null) network = src.network();
		return network.hasLattice();
	}

	/** Urban and laid out as the grid at the column. */
	boolean gridAt(int x, int z) {
		if (!density.urban(x, z)) return false;
		int d = density.at(x, z);
		return program.streetLayout(tier, quarters.at(x, z, d), d) == StreetLayout.GRID;
	}

	/** Urban, laid out as organic frontage and not a park. */
	boolean frontageAt(int x, int z) {
		if (!density.urban(x, z)) return false;
		int d = density.at(x, z);
		QuarterKind q = quarters.at(x, z, d);
		return d > 0 && q != QuarterKind.PARK && program.streetLayout(tier, q, d) == StreetLayout.FRONTAGE;
	}

	/** Urban and laid out as a recursively subdivided old-town tile (IRR1; never a park). */
	boolean subdivideAt(int x, int z) {
		if (!density.urban(x, z)) return false;
		int d = density.at(x, z);
		QuarterKind q = quarters.at(x, z, d);
		return d > 0 && q != QuarterKind.PARK && program.streetLayout(tier, q, d) == StreetLayout.SUBDIVIDE;
	}

	/** Organic land where lanes may cross a tile border: frontage or subdivided. */
	boolean organicAt(int x, int z) { return frontageAt(x, z) || subdivideAt(x, z); }

	/** {@link RoadKind#ARTERIAL}, {@link RoadKind#STREET} or null (no street) for a tile-border segment. */
	RoadKind borderKind(DistrictGrid.Segment s) {
		String key = s.axis() + ":" + s.line() + ":" + s.index();
		if (borderMemo.containsKey(key)) return borderMemo.get(key);
		RoadKind k = computeBorder(s);
		borderMemo.put(key, k);
		return k;
	}

	private RoadKind computeBorder(DistrictGrid.Segment s) {
		if (RoadNetwork.latticeTier(tier)) {
			if (network == null) network = src.network();
			if (network.hasLattice()) return network.kind(s);
		}
		if (arterials.is(s)) return RoadKind.ARTERIAL;
		if (!density.urban(Arterials.ax(s, s.from()), Arterials.az(s, s.from())) || !density.urban(Arterials.ax(s, s.to()), Arterials.az(s, s.to()))) return null;
		int mid = Math.floorDiv(s.from() + s.to(), 2);
		if (!gridAt(Arterials.ax(s, mid), Arterials.az(s, mid))) return null;
		return arterials.waterOk(s) ? RoadKind.STREET : null;
	}

	/** Carriageway width of the street on a border segment, 0 when it has none. */
	int borderWidth(DistrictGrid.Segment s) {
		RoadKind k = borderKind(s);
		return k == null ? 0 : k == RoadKind.ARTERIAL ? arterials.width() : BORDER_WIDTH;
	}

	/** Absolute positions of the inner grid lines of tile interval {@code k} on an axis (a line of constant x for AXIS_X). */
	List<Integer> gridLines(int axis, int k) {
		int a = grid.lineBlock(axis, k), b = grid.lineBlock(axis, k + 1), w = b - a;
		int nMin = Math.max(1, Math.floorDiv(w + MAX_PITCH - 1, MAX_PITCH) - 1), nMax = Math.max(nMin, w / MIN_PITCH - 1);
		long h = Hash64.hash(seed, "grid-lines", axis, k);
		int n = nMin + Hash64.below(Hash64.hash(h, "n"), nMax - nMin + 1);
		int pitch = w / (n + 1), jitter = Math.max(0, Math.min(8, (pitch - MIN_PITCH) / 2));
		List<Integer> out = new ArrayList<>();
		for (int i = 1; i <= n; i++) out.add(a + (int) ((long) w * i / (n + 1)) + Hash64.below(Hash64.hash(h, "j", i), 2 * jitter + 1) - jitter);
		return out;
	}

	static boolean wet(TerrainProbe exact, int x, int z) { return exact.waterDepth(x, z) > 0; }

	static boolean slopeOk(TerrainProbe exact, int x0, int z0, int x1, int z1) {
		return Math.abs(exact.floorY(x1, z1) - exact.floorY(x0, z0)) <= EDGE_RISE;
	}

	/** A grid street edge between two nodes: both ends grid land, dry, and not steeper than {@value #EDGE_RISE} per node step. */
	boolean gridEdgeOk(TerrainProbe exact, int x0, int z0, int x1, int z1) {
		return gridAt(x0, z0) && gridAt(x1, z1) && !wet(exact, x0, z0) && !wet(exact, x1, z1) && slopeOk(exact, x0, z0, x1, z1);
	}

	/** A border street edge: bridges are allowed, so only dry edges are checked for slope. */
	static boolean borderEdgeOk(TerrainProbe exact, int x0, int z0, int x1, int z1) {
		return wet(exact, x0, z0) || wet(exact, x1, z1) || slopeOk(exact, x0, z0, x1, z1);
	}

	/**
	 * The port positions (coordinate along the segment) of a segment without a street: 0 to 2 hashed multiples of
	 * {@value #NODE}, at least 32 from the ends and from each other, used when both sides are organic frontage land.
	 */
	List<Integer> ports(DistrictGrid.Segment s) {
		List<Integer> out = new ArrayList<>();
		if (borderKind(s) != null) return out;
		int len = s.to() - s.from();
		if (len < 96) return out;
		long h = s.hash(seed, "ports");
		int roll = Hash64.below(h, 4), n = roll == 0 ? 0 : roll == 3 ? 2 : 1;
		int slots = (len - 64) / NODE + 1;
		for (int i = 0; i < n; i++) {
			int p = s.from() + 32 + Hash64.below(Hash64.hash(h, "p", i), slots) * NODE;
			p = Math.floorDiv(p, NODE) * NODE;
			boolean clash = false;
			for (int q : out) if (Math.abs(p - q) < 32) clash = true;
			if (clash) continue;
			int ax = Arterials.ax(s, p), az = Arterials.az(s, p);
			boolean xAxis = s.axis() == DistrictGrid.AXIS_X; // a line of constant x: sides are x -/+ 12
			int lx = xAxis ? ax - 12 : ax, lz = xAxis ? az : az - 12, rx = xAxis ? ax + 12 : ax, rz = xAxis ? az : az + 12;
			if (organicAt(lx, lz) && organicAt(rx, rz)) out.add(p);
		}
		return out;
	}
}
