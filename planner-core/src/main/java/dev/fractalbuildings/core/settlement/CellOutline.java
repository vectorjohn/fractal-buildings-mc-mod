package dev.fractalbuildings.core.settlement;

import java.util.List;

/** A region cell of the settlement: its Voronoi site and polygon (world columns). */
public record CellOutline(int cx, int cz, Pt site, List<Pt> polygon) {
	public CellOutline {
		polygon = List.copyOf(polygon);
		if (polygon.size() < 3) throw new IllegalArgumentException("cell polygon needs 3 points, got " + polygon.size());
	}
}
