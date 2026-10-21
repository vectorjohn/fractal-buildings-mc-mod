package dev.fractalbuildings.core.settlement;

/** World columns {@code [x, x + w) x [z, z + d)}. */
public record Rect(int x, int z, int w, int d) {
	public Rect {
		if (w <= 0 || d <= 0) throw new IllegalArgumentException("rect " + w + "x" + d);
	}

	public boolean contains(int px, int pz) { return px >= x && px < x + w && pz >= z && pz < z + d; }

	public boolean containsRect(int ox, int oz, int ow, int od) { return ox >= x && oz >= z && ox + ow <= x + w && oz + od <= z + d; }

	public static boolean overlap(int ax, int az, int aw, int ad, int bx, int bz, int bw, int bd) {
		return ax < bx + bw && bx < ax + aw && az < bz + bd && bz < az + ad;
	}
}
