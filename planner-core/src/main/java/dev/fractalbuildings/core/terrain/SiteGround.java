package dev.fractalbuildings.core.terrain;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.List;

/**
 * The effective ground of a building's site (DS-45, plan shoplow section 3): what a walker, a door or an
 * invariant should treat as "the ground" in a column once the building has dug into, or built up, the
 * site. One rule shared by the generator, the invariants and (mirrored in TypeScript) the viewer:
 * <ul>
 *   <li>a footprint column (a volume starting at y 0): {@code min(raw, oy)}, the slab is the ground;
 *   <li>a column where a building {@code AIR} site box spans the surface ({@code worldY(box.y) <= raw - 1}
 *       and {@code worldMaxY >= raw}): the box's bottom;
 *   <li>then, where {@code PAVING} / {@code GROUND} site boxes stack from that ground up: one above their top.
 *       A {@code FOUNDATION} box may carry the stack up to a {@code PAVING} / {@code GROUND} top (a causeway of foundation stone paved over:
 *       SHOPLOW's forecourt on fill), but a column that ends in foundation (the kerb under a tread or a rail) stays "not ground", so a flight
 *       cannot stand on its own kerb.
 * </ul>
 * For a building that never cuts into the terrain the result equals the raw probe.
 */
public final class SiteGround {
	private SiteGround() {}

	/** The columns of the ground storey: every column under a volume that starts at local y 0, indexed {@code [lx][lz]}. */
	public static boolean[][] footprint(BuildingIr shell) {
		boolean[][] fp = new boolean[shell.bounds().maxX()][shell.bounds().maxZ()];
		for (Volume v : shell.volumes()) {
			if (v.box().y() != 0) continue;
			for (int x = Math.max(0, v.box().x()); x < Math.min(fp.length, v.box().maxX()); x++)
				for (int z = Math.max(0, v.box().z()); z < Math.min(fp[0].length, v.box().maxZ()); z++) fp[x][z] = true; // clipped to the bounds (hand-made test shells may disagree)
		}
		return fp;
	}

	/** The raw probe seen through the building's site: see the class comment. */
	public static TerrainProbe probe(PlacedBuilding b, TerrainProbe raw) {
		Placement p = b.placement();
		boolean[][] fp = footprint(b.shell());
		List<Box> air = new ArrayList<>(), stack = new ArrayList<>();
		for (Box s : b.siteBoxes()) {
			if (s.role() == Role.AIR) air.add(s);
			else if (s.role() == Role.PAVING || s.role() == Role.GROUND || s.role() == Role.FOUNDATION) stack.add(s);
		}
		int oy = b.oy();
		return new TerrainProbe() {
			@Override public int floorY(int x, int z) {
				int g = raw.floorY(x, z);
				int lx = p.localX(x, z), lz = p.localZ(x, z);
				if (lx >= 0 && lz >= 0 && lx < fp.length && lz < fp[0].length && fp[lx][lz]) g = Math.min(g, oy);
				int r = g;
				for (Box a : air) {
					var q = a.box();
					if (lx >= q.x() && lx < q.maxX() && lz >= q.z() && lz < q.maxZ() && oy + q.y() <= r - 1 && oy + q.maxY() >= r) g = Math.min(g, oy + q.y());
				}
				int top = g; // the ground as the paved stack leaves it; foundation under it only carries the stack
				boolean again = !stack.isEmpty();
				while (again) {
					again = false;
					for (Box s : stack) {
						var q = s.box();
						if (lx >= q.x() && lx < q.maxX() && lz >= q.z() && lz < q.maxZ() && oy + q.y() <= g && oy + q.maxY() > g) {
							g = oy + q.maxY();
							if (s.role() != Role.FOUNDATION) top = g;
							again = true;
						}
					}
				}
				return top;
			}

			@Override public int waterDepth(int x, int z) { return raw.waterDepth(x, z); }
		};
	}
}
