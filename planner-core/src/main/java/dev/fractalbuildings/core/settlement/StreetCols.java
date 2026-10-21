package dev.fractalbuildings.core.settlement;

import java.util.ArrayList;
import java.util.List;

/**
 * Centre-line columns of a street polyline, one per block step (the enumeration {@code Grading.road} uses), with the
 * clipping and polyline reconstruction district detail needs. A column is {@code {x, z}}; consecutive columns differ by
 * at most one in each axis.
 */
final class StreetCols {
	private StreetCols() {}

	/** All columns of the polyline, the first point included once. */
	static List<int[]> columns(List<Pt> pts) {
		List<int[]> cols = new ArrayList<>();
		cols.add(new int[] {pts.get(0).x(), pts.get(0).z()});
		for (int p = 1; p < pts.size(); p++) {
			Pt a = pts.get(p - 1), b = pts.get(p);
			int dx = b.x() - a.x(), dz = b.z() - a.z();
			int n = Math.max(Math.abs(dx), Math.abs(dz));
			for (int k = 1; k <= n; k++) cols.add(new int[] {a.x() + (int) Math.floorDiv(2L * dx * k + n, 2L * n), a.z() + (int) Math.floorDiv(2L * dz * k + n, 2L * n)});
		}
		return cols;
	}

	/** Maximal runs of consecutive columns for which {@code keep} holds, each of at least 2 columns. */
	static List<List<int[]>> runs(List<int[]> cols, java.util.function.Predicate<int[]> keep) {
		List<List<int[]>> out = new ArrayList<>();
		List<int[]> cur = new ArrayList<>();
		for (int[] c : cols) {
			if (keep.test(c)) cur.add(c);
			else {
				if (cur.size() >= 2) out.add(cur);
				cur = new ArrayList<>();
			}
		}
		if (cur.size() >= 2) out.add(cur);
		return out;
	}

	/** Indices of the columns where the direction changes, plus both ends. */
	static List<Integer> vertexIndices(List<int[]> cols) {
		List<Integer> v = new ArrayList<>();
		v.add(0);
		for (int i = 1; i + 1 < cols.size(); i++) {
			int ax = cols.get(i)[0] - cols.get(i - 1)[0], az = cols.get(i)[1] - cols.get(i - 1)[1];
			int bx = cols.get(i + 1)[0] - cols.get(i)[0], bz = cols.get(i + 1)[1] - cols.get(i)[1];
			if (ax != bx || az != bz) v.add(i);
		}
		v.add(cols.size() - 1);
		return v;
	}

	/** The polyline through the direction changes. */
	static List<Pt> vertices(List<int[]> cols) {
		List<Pt> out = new ArrayList<>();
		for (int i : vertexIndices(cols)) out.add(new Pt(cols.get(i)[0], cols.get(i)[1]));
		return out;
	}

	/** Does the footprint square of a column of a street of the given width overlap the rectangle? */
	static boolean footprintOverlaps(int[] c, int width, Rect r) {
		int h = width / 2;
		return Rect.overlap(c[0] - h, c[1] - h, width, width, r.x(), r.z(), r.w(), r.d());
	}
}
