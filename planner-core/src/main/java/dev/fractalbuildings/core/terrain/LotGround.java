package dev.fractalbuildings.core.terrain;

import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.probe.TerrainProbe;

/** Sparse relief of a lot: 9 samples (corners, edge midpoints, centre) and the front-centre height. */
public final class LotGround {
	private LotGround() {}

	public record Relief(int relief, int min, int max, int frontY) {}

	public static Relief relief(Lot lot, TerrainProbe exact) {
		int x0 = lot.x(), x1 = lot.x() + lot.w() - 1, xm = lot.x() + (lot.w() - 1) / 2;
		int z0 = lot.z(), z1 = lot.z() + lot.d() - 1, zm = lot.z() + (lot.d() - 1) / 2;
		int[] xs = {x0, xm, x1}, zs = {z0, zm, z1};
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		for (int z : zs) {
			for (int x : xs) {
				int y = exact.floorY(x, z);
				min = Math.min(min, y);
				max = Math.max(max, y);
			}
		}
		int fx = switch (lot.front()) { case WEST -> x0; case EAST -> x1; default -> xm; };
		int fz = switch (lot.front()) { case NORTH -> z0; case SOUTH -> z1; default -> zm; };
		return new Relief(max - min, min, max, exact.floorY(fx, fz));
	}
}
