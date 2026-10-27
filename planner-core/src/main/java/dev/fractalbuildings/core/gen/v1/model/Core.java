package dev.fractalbuildings.core.gen.v1.model;

/**
 * A vertical circulation core (ADR 0001, "shafts first"): a footprint rectangle reserved for stairs
 * that serves storeys [from, to), with a flight from each storey s to s + 1 for s in [from, to - 1).
 * The massing places it; the interior pass builds the stairs inside it and must not put anything
 * else in it. It must lie strictly inside the walls of the (non-open) mass occupying each storey it
 * serves. Its long side is at least {@link #length(StoreyGrid, int, int)}, its short side at least
 * 2 (the default stair is a two-row switchback, docs/plans/phase4.md "Core contract").
 */
public record Core(int x, int z, int sx, int sz, int from, int to) {
	/** A core's {@code from} of {@code LEG}: its first flight rises from the open floor under a raised building (a market hall on posts) to storey 0. */
	public static final int LEG = -1;

	public Core {
		if (sx < 2 || sz < 2) throw new IllegalArgumentException("core " + sx + "x" + sz);
		if (from < LEG || to < from + 2) throw new IllegalArgumentException("core storeys [" + from + ", " + to + ")");
	}

	public int maxX() { return x + sx; }

	public int maxZ() { return z + sz; }

	public boolean alongX() { return sx >= sz; }

	public boolean contains(int cx, int cz) { return cx >= x && cx < maxX() && cz >= z && cz < maxZ(); }

	/** Minimum long side for flights from storeys [from, to - 1): the tallest such storey plus 1. */
	public static int length(StoreyGrid g, int from, int to) {
		int h = 0;
		for (int s = Math.max(from, 0); s < to - 1; s++) h = Math.max(h, g.height(s));
		return h + 1;
	}
}
