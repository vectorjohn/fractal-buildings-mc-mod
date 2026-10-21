package dev.fractalbuildings.core.settlement;

import java.util.List;
import java.util.Objects;

/** A wall line; closed walls join the last point to the first. */
public record Wall(String id, List<Pt> points, boolean closed) {
	public Wall {
		Objects.requireNonNull(id);
		points = List.copyOf(points);
		Geometry.checkPath(id, points);
		if (closed && points.size() < 3) throw new IllegalArgumentException("wall " + id + ": a closed wall needs 3 points");
	}
}
