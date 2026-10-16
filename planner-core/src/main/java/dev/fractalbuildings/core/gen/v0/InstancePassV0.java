package dev.fractalbuildings.core.gen.v0;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.List;

/** Shell plus request to a placed building: rotation, centring, ground height and foundations. */
public final class InstancePassV0 {
	private InstancePassV0() {}

	public static PlacedBuilding place(BuildingIr shell, GenRequest req) {
		Lot lot = req.lot();
		int r = lot.front().ordinal();
		int w = shell.bounds().maxX(), d = shell.bounds().maxZ();
		boolean odd = (r & 1) == 1;
		int rx = odd ? d : w, rz = odd ? w : d;
		if (rx > lot.w() - 2 || rz > lot.d() - 2) {
			throw new IllegalArgumentException("building " + rx + "x" + rz + " leaves no margin in lot " + lot.w() + "x" + lot.d());
		}
		int ox = lot.x() + (lot.w() - rx) / 2;
		int oz = lot.z() + (lot.d() - rz) / 2;
		TerrainProbe t = req.terrain();
		int oy = Integer.MIN_VALUE;
		for (int x = ox; x < ox + rx; x++) for (int z = oz; z < oz + rz; z++) oy = Math.max(oy, t.floorY(x, z));
		Placement p = new Placement(ox, oy, oz, r, w, d);
		List<Box> site = new ArrayList<>();
		for (int lz = 0; lz < d; lz++) {
			int runStart = 0, runH = 0;
			for (int lx = 0; lx <= w; lx++) {
				int h = lx < w ? oy - t.floorY(p.worldX(lx, lz), p.worldZ(lx, lz)) : 0;
				if (lx == w || h != runH) {
					if (runH > 0) {
						site.add(new Box(Box3.of(runStart, -runH, lz, lx - runStart, runH, 1), Role.FOUNDATION, Layer.FOUNDATION, Box.NO_STOREY));
					}
					runStart = lx;
					runH = h;
				}
			}
		}
		return new PlacedBuilding("b0", List.of(lot.id()), ox, oy, oz, r, shell, site);
	}
}
