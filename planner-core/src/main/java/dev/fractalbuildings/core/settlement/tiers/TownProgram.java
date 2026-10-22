package dev.fractalbuildings.core.settlement.tiers;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.Tiers;
import dev.fractalbuildings.core.settlement.DefaultProgram;
import dev.fractalbuildings.core.settlement.Gate;
import dev.fractalbuildings.core.settlement.GateKind;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.Road;
import dev.fractalbuildings.core.settlement.SkeletonContext;
import dev.fractalbuildings.core.settlement.Wall;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * The town (D2): 3 to 5 radial main roads from the hub, the market square with its hall (the default hub landmarks for a town),
 * frontage streets, and on {@value #WALL_PCT} per cent of towns a {@link WallRing} with a gate where each main road crosses it.
 *
 * <p>The ring is built by the cell that holds the town's centre and only where that cell owns every column of it. A main road to a
 * target outside the ring is routed in three parts: hub to a point {@code reach} inside the gate, a straight run through the gate
 * lot, and from the point {@code reach} outside on to the target. {@link #walls} reads the roads back, so a ring whose roads
 * cross it anywhere else (or not straight) is dropped whole.
 */
public class TownProgram extends DefaultProgram {
	public static final int WALL_PCT = 40;
	/** A ring column keeps this far from coarse water (a stream between lattice columns can still slip through; its lots are then dropped). */
	static final int WATER_MARGIN = 28;

	/** Does this town get a wall? (hash of the seed and the centre; D3 uses its own percentage for old towns). */
	protected boolean walled(SkeletonContext c) {
		return c.holdsCentre() && Hash64.below(Hash64.hash(c.seed, "town-wall", c.site.centre().x(), c.site.centre().z()), 100) < WALL_PCT;
	}

	protected static int reach(SkeletonContext c) { return (14 + c.step - 1) / c.step * c.step; }

	protected static BiPredicate<Integer, Integer> ok(SkeletonContext c) { return (x, z) -> c.owned(x, z) && c.terrain.buildable(x, z) && !c.terrain.waterWithin(x, z, WATER_MARGIN); }

	protected Optional<WallRing.Ring> ring(SkeletonContext c, Pt hub) {
		if (!walled(c) || hub == null) return Optional.empty();
		return WallRing.ring(c.seed, c.site.centre(), c.site.radius(), hub, c.grid, c.step, ok(c), (x, z) -> c.density.urban(x, z));
	}

	@Override protected int exitCount(SkeletonContext c) {
		if (!c.holdsCentre()) return 1;
		return 3 + Hash64.below(Hash64.hash(c.seed, "exit-count", c.cell.cx(), c.cell.cz()), 3);
	}

	@Override public List<Route> mainRoads(SkeletonContext c, Pt hub, List<Pt> targets) {
		Optional<WallRing.Ring> ring = ring(c, hub);
		Optional<List<WallRing.Gate>> gates = ring.isEmpty() ? Optional.empty()
				: WallRing.gates(ring.get(), hub, targets, c.grid, c.step, reach(c), ok(c));
		if (gates.isEmpty()) return super.mainRoads(c, hub, targets);
		List<Route> out = new ArrayList<>();
		for (int i = 0; i < targets.size(); i++) {
			WallRing.Gate g = gates.get().get(i);
			out.add(g == null ? route(c, hub, targets.get(i)) : through(c, hub, targets.get(i), g, ring.get()));
		}
		return out;
	}

	/**
	 * Hub to the gate's inner point, straight through the gate lot, then on to the target. The inner part keeps strictly inside the
	 * ring and the outer part off the ring and its inside (ROADS3: an outer part that stepped back onto the gate column and left it on a
	 * diagonal cut through the gate lot's tower, so district detail split the road there and the town beyond had no way to the hub);
	 * where that finds no way the plain routes are used and {@link #walls} drops the ring if they cross it badly.
	 */
	protected Route through(SkeletonContext c, Pt hub, Pt target, WallRing.Gate g, WallRing.Ring ring) {
		int[] n = WallRing.normal(g.side());
		int r = reach(c);
		Pt in = new Pt(g.at().x() - n[0] * r, g.at().z() - n[1] * r), out = new Pt(g.at().x() + n[0] * r, g.at().z() + n[1] * r);
		Route a = route(c, hub, in, (x, z) -> !(x > ring.xl() && x < ring.xr() && z > ring.zt() && z < ring.zb()));
		Route b = route(c, out, target, (x, z) -> x >= ring.xl() && x <= ring.xr() && z >= ring.zt() && z <= ring.zb());
		if (a.points().size() < 2) a = route(c, hub, in);
		if (b.points().size() < 2) b = route(c, out, target);
		if (a.points().size() < 2 || b.points().size() < 2) return new Route(target, List.of(), List.of());
		List<Pt> pts = new ArrayList<>(a.points());
		pts.addAll(b.points());
		List<int[]> bridges = new ArrayList<>(a.bridges());
		for (int[] br : b.bridges()) bridges.add(new int[] {br[0] + a.points().size(), br[1] + a.points().size()});
		return new Route(target, pts, bridges);
	}

	@Override public WallPlan walls(SkeletonContext c, Pt hub, List<Road> roads) {
		Optional<WallRing.Ring> ring = ring(c, hub);
		if (ring.isEmpty()) return new WallPlan(List.of(), List.of());
		List<WallRing.Crossing> cross = WallRing.crossings(ring.get(), roads, reach(c));
		if (cross == null || cross.isEmpty()) return new WallPlan(List.of(), List.of());
		List<WallRing.Gate> gs = new ArrayList<>();
		for (WallRing.Crossing x : cross) gs.add(x.gate());
		if (!WallRing.valid(ring.get(), gs, c.grid)) return new WallPlan(List.of(), List.of());
		List<Gate> gates = new ArrayList<>();
		for (WallRing.Crossing x : cross) gates.add(new Gate("g?", GateKind.WALL, x.gate().at().x(), x.gate().at().z(), List.of(), x.road()));
		return new WallPlan(List.of(WallRing.wall("w" + c.site.id(), ring.get())), gates);
	}
}
