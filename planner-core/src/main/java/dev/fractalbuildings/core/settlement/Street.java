package dev.fractalbuildings.core.settlement;

import java.util.List;
import java.util.Objects;

/** Part of a road or a tile's own street inside a tile; {@code y} is the road surface at each point (first free y). */
public record Street(String id, RoadKind kind, int width, List<Pt> points, int[] y) {
	public Street {
		Objects.requireNonNull(id);
		Objects.requireNonNull(kind);
		points = List.copyOf(points);
		y = y.clone();
		if (width <= 0) throw new IllegalArgumentException("street " + id + " width " + width);
		Geometry.checkPath(id, points);
		if (y.length != points.size()) throw new IllegalArgumentException("street " + id + " has " + y.length + " heights for " + points.size() + " points");
	}

	@Override public int[] y() { return y.clone(); }

	/** {chebyshev distance, interpolated y} of the centre-line column nearest to (px, pz). */
	public int[] nearest(int px, int pz) { return Briefs.nearest(this, px, pz); }

	@Override public boolean equals(Object o) {
		return o instanceof Street s && s.id.equals(id) && s.kind == kind && s.width == width && s.points.equals(points) && java.util.Arrays.equals(s.y, y);
	}

	@Override public int hashCode() { return java.util.Objects.hash(id, kind, width, points, java.util.Arrays.hashCode(y)); }
}
