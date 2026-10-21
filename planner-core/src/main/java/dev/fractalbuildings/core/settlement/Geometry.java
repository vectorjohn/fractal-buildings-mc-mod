package dev.fractalbuildings.core.settlement;

import java.util.List;

/** Shared shape checks. */
final class Geometry {
	private Geometry() {}

	/** At least 2 points; every segment axis-aligned or at 45 degrees, and not zero length. */
	static void checkPath(String id, List<Pt> points) {
		if (points.size() < 2) throw new IllegalArgumentException(id + ": a path needs 2 points");
		for (int i = 1; i < points.size(); i++) {
			long dx = (long) points.get(i).x() - points.get(i - 1).x(), dz = (long) points.get(i).z() - points.get(i - 1).z();
			if ((dx == 0 && dz == 0) || !(dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz))) {
				throw new IllegalArgumentException(id + ": segment " + (i - 1) + " is neither axis-aligned nor 45 degrees");
			}
		}
	}
}
