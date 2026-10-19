package dev.fractalbuildings.core.gen.v1.model;

import java.util.Arrays;

/**
 * The building's one storey grid (ADR 0001): storey i has its slab at local y {@link #y}(i) and is
 * {@link #height}(i) blocks from slab to next slab, so people stand at y(i) + 1 and the walls run
 * from y(i) + 1 to y(i + 1) - 1. Every mass uses this grid; a mass spans a range of grid storeys.
 * Heights are at least {@link #MIN_HEIGHT} (a straight flight of h - 1 steps needs h >= 4).
 */
public final class StoreyGrid {
	public static final int MIN_HEIGHT = 4;

	private final int[] heights;
	private final int[] ys;

	public StoreyGrid(int... heights) {
		if (heights.length == 0) throw new IllegalArgumentException("no storeys");
		this.heights = heights.clone();
		this.ys = new int[heights.length + 1];
		for (int i = 0; i < heights.length; i++) {
			if (heights[i] < MIN_HEIGHT) throw new IllegalArgumentException("storey " + i + " height " + heights[i] + " < " + MIN_HEIGHT);
			ys[i + 1] = ys[i] + heights[i];
		}
	}

	/** Ground storey {@code ground} high, then {@code n - 1} storeys {@code typical} high. */
	public static StoreyGrid of(int n, int ground, int typical) {
		int[] h = new int[n];
		Arrays.fill(h, typical);
		h[0] = ground;
		return new StoreyGrid(h);
	}

	public int count() { return heights.length; }

	public int height(int i) { return heights[i]; }

	/** Slab y of storey i; {@code y(count())} is the top slab of a mass reaching the top storey. */
	public int y(int i) { return ys[i]; }

	public int[] heights() { return heights.clone(); }

	@Override
	public boolean equals(Object o) { return o instanceof StoreyGrid g && Arrays.equals(heights, g.heights); }

	@Override
	public int hashCode() { return Arrays.hashCode(heights); }

	@Override
	public String toString() { return "StoreyGrid" + Arrays.toString(heights); }
}
