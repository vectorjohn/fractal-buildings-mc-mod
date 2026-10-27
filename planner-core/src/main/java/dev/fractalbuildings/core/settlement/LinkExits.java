package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.road.RoadPort;
import dev.fractalbuildings.core.terrain.LatticePath;
import java.util.ArrayList;
import java.util.List;

/**
 * The skeleton's side of the road links (ADR 0004 N5, ROADMAP 6.10): a required exit for each link port of the settlement, and the
 * approach from that exit to the port. The road graph reads decisions only, so the skeleton reads the graph and not the other way round.
 *
 * <p>Each port is served by exactly one member cell, the one whose region site is nearest the rim point toward the port (a rule every
 * member cell evaluates alike, so the neighbours agree without reading each other). That cell takes the urban column of its own mask
 * that lies furthest toward the port without straying sideways (the same score as the hashed exits) as a required exit, then runs an
 * approach over dry lattice columns from the exit to the port. Approaches keep to the water rule: no bridges.
 */
final class LinkExits {
	private LinkExits() {}

	/** A required exit and the port it faces. */
	record Link(RoadPort port, Pt exit) {}

	/** The ports this cell serves: of the settlement's ports, those whose rim point lies nearest this cell's site among the member cells. */
	static List<RoadPort> assigned(SettlementSite site, dev.fractalbuildings.core.region.RegionField field, Cell cell, List<RoadPort> ports) {
		List<RoadPort> out = new ArrayList<>();
		for (RoadPort p : ports) {
			double dx = p.x() - site.centre().x(), dz = p.z() - site.centre().z();
			double len = StrictMath.sqrt(dx * dx + dz * dz);
			if (len == 0) continue;
			long rx = (long) StrictMath.floor(site.centre().x() + dx / len * site.radius() + 0.5), rz = (long) StrictMath.floor(site.centre().z() + dz / len * site.radius() + 0.5);
			Cell best = null;
			long bestD = Long.MAX_VALUE;
			for (Cell k : site.cells()) {
				long ex = field.siteX(k.cx(), k.cz()) - rx, ez = field.siteZ(k.cx(), k.cz()) - rz, d = ex * ex + ez * ez;
				if (d < bestD || (d == bestD && best != null && CellGeometry.compare(k, best) < 0)) { bestD = d; best = k; }
			}
			if (cell.equals(best)) out.add(p);
		}
		return out;
	}

	/** The urban column of the cell furthest toward the port from the hub without straying sideways; null when the cell has none ahead. */
	static Pt exitFor(SkeletonContext c, Pt hub, RoadPort port) {
		double dx = port.x() - hub.x(), dz = port.z() - hub.z();
		double len = StrictMath.sqrt(dx * dx + dz * dz);
		if (len == 0) return null;
		double ux = dx / len, uz = dz / len;
		// ROADJOIN step 3: columns in tiles this settlement plans first; a column in a tile nobody plans only when no other qualifies
		for (int pass = 0; pass < 2; pass++) {
			double bestScore = -1e18;
			Pt best = null;
			for (int j = 0; j < c.nz; j++) {
				for (int i = 0; i < c.nx; i++) {
					if (c.mask[j * c.nx + i] != CoarseRaster.URBAN || !c.owned[j * c.nx + i]) continue;
					int x = c.x0 + i * c.step, z = c.z0 + j * c.step;
					double ex = x - hub.x(), ez = z - hub.z();
					double fwd = ex * ux + ez * uz, perp = StrictMath.abs(-ex * uz + ez * ux);
					double score = fwd - 0.5 * perp;
					if (fwd > 0 && score > bestScore && (pass == 1 || c.tileOwned(x, z))) { bestScore = score; best = new Pt(x, z); }
				}
			}
			if (best != null) return best;
		}
		return null;
	}

	/** Required exits of this cell, one per assigned port that has an urban column to leave from, in port order. */
	static List<Link> required(SkeletonContext c, Pt hub, List<RoadPort> assigned) {
		List<Link> out = new ArrayList<>();
		if (hub == null) return out;
		for (RoadPort p : assigned) {
			Pt e = exitFor(c, hub, p);
			if (e != null) out.add(new Link(p, e));
		}
		return out;
	}

	/** An approach: lattice columns from the exit to the port over dry ground, corners only; empty when no dry path exists inside the window. */
	/** Extra cost per lattice step through the urban footprint, in units of one step (a plain step costs 10). */
	static final long URBAN_PENALTY = 30;

	static List<Pt> approach(SkeletonContext c, Link l) {
		int step = c.step;
		Pt from = l.exit(), to = new Pt(Math.floorDiv(l.port().x() + step / 2, step) * step, Math.floorDiv(l.port().z() + step / 2, step) * step);
		if (from.equals(to)) return List.of();
		LatticePath.Bounds b = c.bounds();
		if (!b.contains(to.x(), to.z()) || !b.contains(from.x(), from.z())) return List.of();
		LatticePath.Cost cost = new LatticePath.Cost() {
			@Override public long step(int x, int z, int nx, int nz) {
				if (c.terrain.water(nx, nz) || c.terrain.peak(nx, nz)) return FORBIDDEN;
				boolean diag = x != nx && z != nz;
				int dy = Math.abs(c.terrain.surfaceY(nx, nz) - c.terrain.surfaceY(x, z));
				int len = diag ? step * 14 / 10 : step;
				if (dy * 3 > len) return FORBIDDEN;
				long cost = (diag ? 14L : 10L) * step + 60L * dy;
				if (c.terrain.roadSteep(nx, nz)) cost += 300;
				// ROADJOIN step 4: an approach leaves town (DS-52: the link is the main street at the gate); crossing the urban footprint is a last resort, not a short cut
				if (c.urbanCandidate(nx, nz) && !(nx == from.x() && nz == from.z())) cost += URBAN_PENALTY * step;
				return cost;
			}
		};
		List<LatticePath.Pt> path;
		try {
			path = LatticePath.find(new LatticePath.Pt(from.x(), from.z()), new LatticePath.Pt(to.x(), to.z()), cost, b, step, -1, LatticePath.DEFAULT_MAX_EXPANSIONS, false);
		} catch (IllegalArgumentException e) {
			return List.of();
		}
		if (path.size() < 2) return List.of();
		List<Pt> pts = new ArrayList<>();
		for (int i = 0; i < path.size(); i++) {
			boolean corner = i == 0 || i == path.size() - 1
				|| Integer.signum(path.get(i).x() - path.get(i - 1).x()) != Integer.signum(path.get(i + 1).x() - path.get(i).x())
				|| Integer.signum(path.get(i).z() - path.get(i - 1).z()) != Integer.signum(path.get(i + 1).z() - path.get(i).z());
			if (corner) pts.add(new Pt(path.get(i).x(), path.get(i).z()));
		}
		return pts;
	}
}
