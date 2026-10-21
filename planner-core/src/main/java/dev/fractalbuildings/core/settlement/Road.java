package dev.fractalbuildings.core.settlement;

import java.util.List;
import java.util.Objects;

/**
 * A road's centre line (every segment axis-aligned or 45 degrees) and the point-index ranges {@code [i, j]}
 * (inclusive) that bridge water.
 */
public record Road(String id, RoadKind kind, int width, List<Pt> points, List<int[]> bridges) {
	public Road {
		Objects.requireNonNull(id);
		Objects.requireNonNull(kind);
		points = List.copyOf(points);
		bridges = bridges.stream().map(int[]::clone).toList();
		if (width <= 0) throw new IllegalArgumentException("road " + id + " width " + width);
		Geometry.checkPath(id, points);
		for (int[] b : bridges) {
			if (b.length != 2 || b[0] < 0 || b[1] < b[0] || b[1] >= points.size()) throw new IllegalArgumentException("road " + id + " bridge range out of the points");
		}
	}
}
