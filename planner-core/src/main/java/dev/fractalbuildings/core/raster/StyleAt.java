package dev.fractalbuildings.core.raster;

import dev.fractalbuildings.core.ir.PlacedBuilding;
import java.util.List;

/** The style (palette) to use for a world column of ground, which has no building of its own. */
@FunctionalInterface
public interface StyleAt {
	String at(int x, int z);

	/** The viewer's {@code styleLocator}: the style of the building whose footprint centre is nearest (ties: first). */
	static StyleAt nearest(List<PlacedBuilding> buildings, String fallback) {
		if (buildings.isEmpty()) return (x, z) -> fallback;
		int n = buildings.size();
		double[] cx = new double[n], cz = new double[n];
		String[] st = new String[n];
		boolean same = true;
		for (int i = 0; i < n; i++) {
			PlacedBuilding b = buildings.get(i);
			boolean odd = b.rotation() % 2 == 1;
			int sx = b.shell().bounds().maxX(), sz = b.shell().bounds().maxZ();
			cx[i] = b.ox() + (odd ? sz : sx) / 2.0;
			cz[i] = b.oz() + (odd ? sx : sz) / 2.0;
			st[i] = b.shell().spec().style();
			same &= st[i].equals(st[0]);
		}
		if (same) return (x, z) -> st[0];
		return (x, z) -> {
			int best = 0;
			double bd = Double.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				double dx = cx[i] - x - 0.5, dz = cz[i] - z - 0.5, d = dx * dx + dz * dz;
				if (d < bd) { bd = d; best = i; }
			}
			return st[best];
		};
	}
}
