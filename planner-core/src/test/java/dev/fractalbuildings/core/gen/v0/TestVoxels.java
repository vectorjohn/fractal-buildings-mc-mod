package dev.fractalbuildings.core.gen.v0;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Role;

/** Tiny local-frame voxel raster for generator tests (S4 owns the real grid and invariants). */
final class TestVoxels {
	final int sx, sy, sz;
	final Role[] cells;

	TestVoxels(BuildingIr b) {
		sx = b.bounds().sx();
		sy = b.bounds().sy();
		sz = b.bounds().sz();
		cells = new Role[sx * sy * sz];
		java.util.Arrays.fill(cells, Role.AIR);
		for (Box box : b.boxes()) {
			var c = box.box();
			for (int x = c.x(); x < c.maxX(); x++)
				for (int y = c.y(); y < c.maxY(); y++)
					for (int z = c.z(); z < c.maxZ(); z++) cells[idx(x, y, z)] = box.role();
		}
	}

	int idx(int x, int y, int z) { return (y * sz + z) * sx + x; }

	boolean in(int x, int y, int z) { return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz; }

	Role at(int x, int y, int z) { return in(x, y, z) ? cells[idx(x, y, z)] : Role.AIR; }

	boolean passable(int x, int y, int z) { return !at(x, y, z).solid; }

	long count(Role r) {
		long c = 0;
		for (Role x : cells) if (x == r) c++;
		return c;
	}

	/** Spec walk rule: standing needs passable p, p+up and solid below; steps change y by -1..+1, a step up also needs p+2up. */
	java.util.Set<Long> reachable(int sx0, int sy0, int sz0) {
		java.util.Set<Long> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		q.add(new int[] {sx0, sy0, sz0});
		seen.add(key(sx0, sy0, sz0));
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!q.isEmpty()) {
			int[] p = q.poll();
			for (int[] dd : dirs) {
				for (int dy = -1; dy <= 1; dy++) {
					int x = p[0] + dd[0], y = p[1] + dy, z = p[2] + dd[1];
					if (!stand(x, y, z) || (dy == 1 && !passable(p[0], p[1] + 2, p[2]))) continue;
					// a drop needs the intermediate column free at the current height
					if (seen.add(key(x, y, z))) q.add(new int[] {x, y, z});
				}
			}
		}
		return seen;
	}

	boolean stand(int x, int y, int z) {
		return in(x, y, z) && passable(x, y, z) && passable(x, y + 1, z) && at(x, y - 1, z).solid;
	}

	static long key(int x, int y, int z) { return ((long) x << 40) | ((long) y << 20) | z; }
}
