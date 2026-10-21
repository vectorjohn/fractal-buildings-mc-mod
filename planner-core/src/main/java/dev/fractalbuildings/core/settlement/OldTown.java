package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.settlement.tiers.WallRing;
import java.util.Optional;

/**
 * The walled old town of a city (D3): {@value #WALL_PCT} per cent of cities have a {@link WallRing} round the centre, at about
 * 0.25 to 0.35 of the radius, and no arterials inside it (a wall cannot take a nine-block avenue through its gate; the roads
 * through its gates are the skeleton's main roads). The ring is found from the site and the coarse terrain alone, so the skeleton
 * (which builds the wall) and every district (which keeps arterials and grid streets out of the ring and builds the wall lots)
 * agree on it whichever cell or tile asks. The skeleton drops the wall (not the empty ring) when its own cell does not own the whole
 * ring or a road cannot cross it straight; the area inside then stays an old town without a wall.
 */
public final class OldTown {
	public static final int WALL_PCT = 60;
	/** A ring column keeps this far from coarse water. */
	public static final int WATER_MARGIN = 28;
	/** The ring is searched for at this fraction of the site radius (the ring search takes 0.5 to 0.7 of it per axis). */
	public static final double RADIUS_SCALE = 0.5;

	private final long seed;
	private final SettlementSite site;
	private final CoarseTerrain terrain;
	private final DistrictGrid grid;
	private final DensityField density;
	private volatile Optional<WallRing.Ring> ring; // computed once, lazily; pure, so a race only repeats the work

	OldTown(long seed, SettlementSite site, CoarseTerrain terrain, DistrictGrid grid, DensityField density) {
		this.seed = seed;
		this.site = site;
		this.terrain = terrain;
		this.grid = grid;
		this.density = density;
	}

	/** Does this city have an old-town wall (by hash; the ring may still not fit)? */
	public boolean walled() {
		return site.tier() == Tier.CITY && Hash64.below(Hash64.hash(seed, "old-town-wall", site.centre().x(), site.centre().z()), 100) < WALL_PCT;
	}

	/** The ring rectangle, if the city is walled and a ring fits the terrain. */
	public Optional<WallRing.Ring> ring() {
		Optional<WallRing.Ring> r = ring;
		if (r == null) {
			r = walled() ? WallRing.ring(seed, site.centre(), (int) (site.radius() * RADIUS_SCALE), nearestLattice(), grid, terrain.step,
					(x, z) -> terrain.buildable(x, z) && !terrain.waterWithin(x, z, WATER_MARGIN), density::urban) : Optional.empty();
			ring = r;
		}
		return r;
	}

	/** The lattice column nearest the site centre: stands in for the hub when the ring is searched. */
	private Pt nearestLattice() { return new Pt(terrain.snap(site.centre().x()), terrain.snap(site.centre().z())); }

	/** Is the column inside the ring (its centre lines included)? */
	public boolean contains(int x, int z) {
		Optional<WallRing.Ring> r = ring();
		return r.isPresent() && x >= r.get().xl() && x <= r.get().xr() && z >= r.get().zt() && z <= r.get().zb();
	}

	/** Does the tile-border segment pass through the ring's rectangle (so it is not an arterial)? */
	boolean cuts(DistrictGrid.Segment s) {
		Optional<WallRing.Ring> r = ring();
		if (r.isEmpty()) return false;
		WallRing.Ring q = r.get();
		boolean alongZ = s.axis() == DistrictGrid.AXIS_X;
		int lo = alongZ ? q.xl() : q.zt(), hi = alongZ ? q.xr() : q.zb(), alongLo = alongZ ? q.zt() : q.xl(), alongHi = alongZ ? q.zb() : q.xr();
		return s.fixed() >= lo && s.fixed() <= hi && s.from() <= alongHi && s.to() >= alongLo;
	}
}
