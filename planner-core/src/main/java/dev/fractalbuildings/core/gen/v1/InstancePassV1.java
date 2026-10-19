package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.List;

/**
 * Shell plus request to a placed building (generator v1). The envelope sits at the type's margins
 * inside the lot (rotation from the lot's front, as v0); the ground slab sits at the highest ground
 * under the ground footprint (the volumes that start at y 0); every footprint column whose ground is
 * lower gets a foundation down to it. Site work (front steps) is the site step's (plan step S).
 */
public final class InstancePassV1 {
	private InstancePassV1() {}

	public static PlacedBuilding place(BuildingIr shell, GenRequest req) { return place(shell, req, Types.get(shell.spec().type()).limits()); }

	/** With explicit margins (tests place shells of unregistered types). */
	public static PlacedBuilding place(BuildingIr shell, GenRequest req, TypeLimits lim) {
		Lot lot = req.lot();
		int r = lot.front().ordinal();
		boolean odd = (r & 1) == 1;
		int lw = odd ? lot.d() : lot.w(), ld = odd ? lot.w() : lot.d();
		int w = shell.bounds().maxX(), d = shell.bounds().maxZ();
		if (w + 2 * lim.side() > lw || d + lim.front() + lim.back() > ld) {
			throw new IllegalArgumentException("envelope " + w + "x" + d + " does not fit lot " + lot.w() + "x" + lot.d() + " with the type's margins");
		}
		Placement lotFrame = new Placement(lot.x(), 0, lot.z(), r, lw, ld);
		int ax = lotFrame.worldX(lim.side(), lim.front()), az = lotFrame.worldZ(lim.side(), lim.front());
		int bx = lotFrame.worldX(lim.side() + w - 1, lim.front() + d - 1), bz = lotFrame.worldZ(lim.side() + w - 1, lim.front() + d - 1);
		int ox = Math.min(ax, bx), oz = Math.min(az, bz);
		boolean[][] fp = footprint(shell);
		Placement p0 = new Placement(ox, 0, oz, r, w, d);
		TerrainProbe t = req.terrain();
		int oy = Integer.MIN_VALUE;
		for (int lx = 0; lx < w; lx++)
			for (int lz = 0; lz < d; lz++) if (fp[lx][lz]) oy = Math.max(oy, t.floorY(p0.worldX(lx, lz), p0.worldZ(lx, lz)));
		List<Box> site = new ArrayList<>();
		for (int lz = 0; lz < d; lz++) {
			int runStart = 0, runH = 0;
			for (int lx = 0; lx <= w; lx++) {
				int h = lx < w && fp[lx][lz] ? oy - t.floorY(p0.worldX(lx, lz), p0.worldZ(lx, lz)) : 0;
				if (lx == w || h != runH) {
					if (runH > 0) site.add(new Box(Box3.of(runStart, -runH, lz, lx - runStart, runH, 1), Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY));
					runStart = lx;
					runH = h;
				}
			}
		}
		return new PlacedBuilding("b0", List.of(lot.id()), ox, oy, oz, r, shell, site);
	}

	/** Local columns [x][z] under a volume that starts at y 0. */
	public static boolean[][] footprint(BuildingIr shell) {
		boolean[][] fp = new boolean[shell.bounds().maxX()][shell.bounds().maxZ()];
		for (Volume v : shell.volumes()) {
			if (v.box().y() != 0) continue;
			for (int x = v.box().x(); x < v.box().maxX(); x++) for (int z = v.box().z(); z < v.box().maxZ(); z++) fp[x][z] = true;
		}
		return fp;
	}
}
