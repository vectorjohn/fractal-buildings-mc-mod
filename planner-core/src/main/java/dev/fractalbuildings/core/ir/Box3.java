package dev.fractalbuildings.core.ir;

/** Axis-aligned integer box: min corner and a positive size, in blocks. Max is exclusive. */
public record Box3(int x, int y, int z, int sx, int sy, int sz) {
	public Box3 {
		if (sx <= 0 || sy <= 0 || sz <= 0) {
			throw new IllegalArgumentException("box size must be positive: " + sx + "x" + sy + "x" + sz);
		}
	}

	public static Box3 of(int x, int y, int z, int sx, int sy, int sz) {
		return new Box3(x, y, z, sx, sy, sz);
	}

	public int maxX() { return x + sx; }
	public int maxY() { return y + sy; }
	public int maxZ() { return z + sz; }

	public long volume() { return (long) sx * sy * sz; }

	public boolean contains(int px, int py, int pz) {
		return px >= x && px < maxX() && py >= y && py < maxY() && pz >= z && pz < maxZ();
	}

	public boolean contains(Box3 o) {
		return o.x >= x && o.maxX() <= maxX() && o.y >= y && o.maxY() <= maxY() && o.z >= z && o.maxZ() <= maxZ();
	}

	public boolean intersects(Box3 o) {
		return o.x < maxX() && o.maxX() > x && o.y < maxY() && o.maxY() > y && o.z < maxZ() && o.maxZ() > z;
	}
}
