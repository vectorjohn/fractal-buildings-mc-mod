package dev.fractalbuildings.core.gen.v1.types.shop;

import dev.fractalbuildings.core.gen.v1.InstancePassV1;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.voxel.VoxelGrid;

/**
 * Test helper: walks (one block steps up or down, 2 high) from the standing cells on the terrain of the lot's
 * street-facing edge row, in the building's local frame, to a goal cell. The invariant starts anywhere in the lot;
 * this is the stricter reading (a street-side walker), used by SHOPLAND tests.
 */
final class StreetWalk {
	private final PlacedBuilding b;
	private final TerrainProbe t;
	private final VoxelGrid g;
	private final int x0, x1, z0, z1, nx, nz, ylo, ny;
	private final int[] top;
	/** Local street row z and x range of the lot's front edge. */
	final int rowZ, rowX0, rowX1;
	private final boolean[] seen;

	StreetWalk(SceneDoc scene, TerrainProbe terrain) {
		b = scene.buildings().get(0);
		t = terrain;
		g = VoxelGrid.of(b);
		Lot lot = scene.lots().get(0);
		var p = g.placement();
		int lx = Integer.MAX_VALUE, hx = Integer.MIN_VALUE, lz = Integer.MAX_VALUE, hz = Integer.MIN_VALUE;
		for (int cx : new int[] {lot.x(), lot.x() + lot.w() - 1})
			for (int cz : new int[] {lot.z(), lot.z() + lot.d() - 1}) {
				lx = Math.min(lx, p.localX(cx, cz)); hx = Math.max(hx, p.localX(cx, cz));
				lz = Math.min(lz, p.localZ(cx, cz)); hz = Math.max(hz, p.localZ(cx, cz));
			}
		x0 = Math.min(lx, g.minX) - 1; x1 = Math.max(hx, g.minX + g.sx - 1) + 1;
		z0 = Math.min(lz, g.minZ) - 1; z1 = Math.max(hz, g.minZ + g.sz - 1) + 1;
		rowZ = lz; rowX0 = lx; rowX1 = hx; // local front is z minimum (the shell frame faces north)
		nx = x1 - x0 + 1; nz = z1 - z0 + 1;
		top = new int[nx * nz];
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int z = z0; z <= z1; z++)
			for (int x = x0; x <= x1; x++) {
				int v = t.floorY(p.worldX(x, z), p.worldZ(x, z)) - b.oy();
				top[(z - z0) * nx + (x - x0)] = v;
				lo = Math.min(lo, v); hi = Math.max(hi, v);
			}
		ylo = Math.min(lo, g.minY) - 1;
		ny = Math.max(hi, g.minY + g.sy) + 4 - ylo;
		seen = new boolean[nx * ny * nz];
		walk();
	}

	private boolean passable(int x, int y, int z) {
		if (x < x0 || x > x1 || z < z0 || z > z1) return false;
		if (y <= top[(z - z0) * nx + (x - x0)]) return false;
		Role r = g.get(x, y, z);
		return r == Role.AIR || r == Role.DOOR;
	}

	private boolean standing(int x, int y, int z) {
		return passable(x, y, z) && passable(x, y + 1, z) && (y - 1 <= top[(z - z0) * nx + (x - x0)] || g.get(x, y - 1, z).solid);
	}

	private int idx(int x, int y, int z) { return ((y - ylo) * nz + (z - z0)) * nx + (x - x0); }

	private void walk() {
		int[] queue = new int[seen.length];
		int head = 0, tail = 0;
		for (int x = Math.max(rowX0, x0); x <= Math.min(rowX1, x1); x++) {
			int y = top[(rowZ - z0) * nx + (x - x0)] + 1;
			if (standing(x, y, rowZ) && !seen[idx(x, y, rowZ)]) { seen[idx(x, y, rowZ)] = true; queue[tail++] = idx(x, y, rowZ); }
		}
		int[] hxs = {1, -1, 0, 0}, hzs = {0, 0, 1, -1};
		while (head < tail) {
			int c = queue[head++];
			int cx = x0 + c % nx, cz = z0 + (c / nx) % nz, cy = ylo + c / (nx * nz);
			for (int d = 0; d < 4; d++)
				for (int dy = -1; dy <= 1; dy++) {
					int px = cx + hxs[d], py = cy + dy, pz = cz + hzs[d];
					if (px < x0 || px > x1 || pz < z0 || pz > z1 || py < ylo || py >= ylo + ny - 3) continue;
					if (g.get(px, py, pz) == Role.DOOR || g.get(px, py + 1, pz) == Role.DOOR) continue;
					if (!standing(px, py, pz)) continue;
					if (dy == 1 && !passable(cx, cy + 2, cz)) continue;
					if (dy == -1 && !passable(px, py + 2, pz)) continue;
					int ni = idx(px, py, pz);
					if (seen[ni]) continue;
					seen[ni] = true;
					queue[tail++] = ni;
				}
		}
	}

	/** True if a street-side walker can stand at the cell outside every exterior entrance door. */
	boolean reachesDoors() {
		int n = 0;
		for (Opening o : b.shell().openings()) {
			if (o.kind() != OpeningKind.DOOR || !Opening.OUTSIDE.equals(o.to())) continue;
			Facing out = o.facing();
			int sx = o.box().x() + out.dx, sz = o.box().z() + out.dz, sy = o.box().y();
			n++;
			if (!(seen(sx, sy, sz) || seen(sx, sy - 1, sz))) return false;
		}
		return n > 0;
	}

	boolean seen(int x, int y, int z) {
		return x >= x0 && x <= x1 && z >= z0 && z <= z1 && y >= ylo && y < ylo + ny && seen[idx(x, y, z)];
	}

	/** True if the walker can stand on the terrain at local column (x, z). */
	boolean reachesGround(int x, int z) {
		return x >= x0 && x <= x1 && z >= z0 && z <= z1 && seen(x, top[(z - z0) * nx + (x - x0)] + 1, z);
	}

	int entranceFloor() { return InstancePassV1.entranceStorey(b.shell()).y(); }
}
