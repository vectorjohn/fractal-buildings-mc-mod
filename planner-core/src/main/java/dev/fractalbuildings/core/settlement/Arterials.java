package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.Tiers;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Arterial roads of a city or metropolis: tile-border segments (plan decision 2). A segment is arterial when
 * both its end samples are urban, it crosses no water run over 40 blocks and
 * {@code hash(seed, "arterial", segment key) < p(density at its midpoint)}. Every input is a pointwise function
 * of the segment, so the two tiles (and the two cells) sharing a border agree without talking.
 */
public final class Arterials {
	/** Longest water run an arterial may bridge, blocks. */
	public static final int MAX_BRIDGE = 40;
	/** Density bonus within {@link #REACH} blocks of an arterial. */
	public static final int BONUS = 8;
	public static final int REACH = 24;

	private final long seed;
	private final Tier tier;
	private final DensityField density;
	private final CoarseTerrain terrain;
	private final DistrictGrid grid;
	private final TreeMap<String, Boolean> memo = new TreeMap<>();

	Arterials(long seed, Tier tier, DensityField density, CoarseTerrain terrain, DistrictGrid grid) {
		this.seed = seed;
		this.tier = tier;
		this.density = density;
		this.terrain = terrain;
		this.grid = grid;
	}

	/** Carriageway width. */
	public int width() { return tier == Tier.METROPOLIS ? 11 : 9; }

	/** Probability that a segment of the given base density is arterial (1 at 80, 0.5 at 50, 0 at 30). */
	static double probability(int d) {
		if (d >= 80) return 1;
		if (d >= 50) return 0.5 + (d - 50) / 60.0;
		if (d > 30) return (d - 30) / 40.0;
		return 0;
	}

	static int ax(DistrictGrid.Segment s, int along) { return s.axis() == DistrictGrid.AXIS_X ? s.fixed() : along; }

	static int az(DistrictGrid.Segment s, int along) { return s.axis() == DistrictGrid.AXIS_X ? along : s.fixed(); }

	public boolean is(DistrictGrid.Segment s) {
		if (!tier.atLeast(Tier.CITY)) return false;
		String key = s.axis() + ":" + s.line() + ":" + s.index();
		Boolean m = memo.get(key);
		if (m != null) return m;
		boolean r = compute(s);
		memo.put(key, r);
		return r;
	}

	private boolean compute(DistrictGrid.Segment s) {
		if (!density.urban(ax(s, s.from()), az(s, s.from())) || !density.urban(ax(s, s.to()), az(s, s.to()))) return false;
		int mid = Math.floorDiv(s.from() + s.to(), 2);
		double p = probability(density.base(ax(s, mid), az(s, mid)));
		if (p <= 0 || Tiers.unit(s.hash(seed, "arterial")) >= p) return false;
		return waterOk(s);
	}

	/** The segment crosses no run of water wider than {@link #MAX_BRIDGE} (coarse lattice samples along it). Pointwise. */
	boolean waterOk(DistrictGrid.Segment s) {
		int run = 0;
		for (int q = terrain.snap(s.from()); q <= s.to(); q += terrain.step) {
			if (q < s.from()) continue;
			run = terrain.water(ax(s, q), az(s, q)) ? run + 1 : 0;
			if (run * terrain.step > MAX_BRIDGE) return false;
		}
		return true;
	}

	/** Is the column within {@link #REACH} blocks of an arterial segment of its own tile? */
	public boolean near(int x, int z) {
		if (!tier.atLeast(Tier.CITY)) return false;
		DistrictGrid.Tile t = grid.tileAt(x, z);
		for (DistrictGrid.Segment s : grid.segments(t.ix(), t.iz())) {
			int across = s.axis() == DistrictGrid.AXIS_X ? x : z, along = s.axis() == DistrictGrid.AXIS_X ? z : x;
			if (Math.abs(across - s.fixed()) <= REACH && along >= s.from() && along <= s.to() && is(s)) return true;
		}
		return false;
	}

	/** The segments of every tile overlapping the box, each once, in (axis, line, index) order. */
	public List<DistrictGrid.Segment> segmentsIn(int minX, int minZ, int maxX, int maxZ) {
		TreeMap<String, DistrictGrid.Segment> out = new TreeMap<>();
		DistrictGrid.TileId a = grid.tileOf(minX, minZ), b = grid.tileOf(maxX, maxZ);
		for (int iz = a.iz(); iz <= b.iz(); iz++) {
			for (int ix = a.ix(); ix <= b.ix(); ix++) {
				for (DistrictGrid.Segment s : grid.segments(ix, iz)) {
					out.putIfAbsent(String.format("%d:%09d:%09d", s.axis(), s.line() + 100_000_000, s.index() + 100_000_000), s);
				}
			}
		}
		return new ArrayList<>(out.values());
	}

	/** Centre-line points of the part of a segment between {@code from} and {@code to}: both ends and every lattice multiple between. */
	public List<Pt> points(DistrictGrid.Segment s, int from, int to) {
		List<Pt> pts = new ArrayList<>();
		pts.add(new Pt(ax(s, from), az(s, from)));
		for (int q = Math.floorDiv(from, terrain.step) * terrain.step + terrain.step; q < to; q += terrain.step) pts.add(new Pt(ax(s, q), az(s, q)));
		if (to > from) pts.add(new Pt(ax(s, to), az(s, to)));
		return pts;
	}
}
