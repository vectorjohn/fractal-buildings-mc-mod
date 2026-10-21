package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.region.RegionField;
import java.util.ArrayList;
import java.util.List;

/**
 * Voronoi geometry of region cells from the region field's sites: exact ownership of a column, the cell polygon
 * (Sutherland-Hodgman clip of a square by the bisectors with the neighbours) and the edge two cells share.
 * Doubles with {@code StrictMath}; results are rounded to ints at the end. Polygons and edges are clipped to a
 * square of {@link #REACH} cell sizes around the cell's site, so a very elongated cell is cut there; ownership
 * ({@link #owner}) is exact and unclipped, and is what the skeleton's rules use.
 */
public final class CellGeometry {
	private CellGeometry() {}

	/** Half-width of the clip square, in cell sizes (the plan's "3S square"). */
	public static final double REACH = 1.5;
	/** Neighbour rings (cells) whose sites bound a cell. */
	static final int RING = 3;

	/** A straight edge between two cells, canonical (computed from the lower cell). */
	public record Edge(double x0, double z0, double x1, double z1) {
		public double length() { return StrictMath.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0)); }

		public double x(double t) { return x0 + (x1 - x0) * t; }

		public double z(double t) { return z0 + (z1 - z0) * t; }
	}

	/** The cell that owns a column: nearest site, ties to the lower cz then cx (as {@link RegionField#query}). */
	public static Cell owner(RegionField f, int x, int z) {
		RegionField.Hit h = f.query(x, z);
		return new Cell(h.cx(), h.cz());
	}

	public static boolean owns(RegionField f, Cell c, int x, int z) {
		RegionField.Hit h = f.query(x, z);
		return h.cx() == c.cx() && h.cz() == c.cz();
	}

	/** (cz, cx) order, the order the region code uses. */
	public static int compare(Cell a, Cell b) {
		int c = Integer.compare(a.cz(), b.cz());
		return c != 0 ? c : Integer.compare(a.cx(), b.cx());
	}

	/** The cell polygon, counter-clockwise from the clip square's corner; vertices rounded to ints. */
	public static List<Pt> polygon(RegionField f, Cell c) {
		double ax = f.siteX(c.cx(), c.cz()), az = f.siteZ(c.cx(), c.cz());
		double r = REACH * f.cellSize;
		List<double[]> poly = new ArrayList<>(List.of(new double[] {-r, -r}, new double[] {r, -r}, new double[] {r, r}, new double[] {-r, r}));
		for (int dz = -RING; dz <= RING; dz++) {
			for (int dx = -RING; dx <= RING; dx++) {
				if (dx == 0 && dz == 0) continue;
				int cx = c.cx() + dx, cz = c.cz() + dz;
				double ox = f.siteX(cx, cz) - ax, oz = f.siteZ(cx, cz) - az;
				poly = clip(poly, ox, oz);
				if (poly.size() < 3) break;
			}
		}
		List<Pt> out = new ArrayList<>();
		for (double[] p : poly) {
			Pt q = new Pt((int) Math.round(ax + p[0]), (int) Math.round(az + p[1]));
			if (out.isEmpty() || !out.get(out.size() - 1).equals(q)) out.add(q);
		}
		if (out.size() > 1 && out.get(0).equals(out.get(out.size() - 1))) out.remove(out.size() - 1);
		return out;
	}

	/** Keep the side of the bisector (origin, o) nearer the origin: {@code 2 p.o <= |o|^2}. */
	private static List<double[]> clip(List<double[]> poly, double ox, double oz) {
		double rhs = ox * ox + oz * oz;
		List<double[]> out = new ArrayList<>();
		for (int i = 0; i < poly.size(); i++) {
			double[] a = poly.get(i), b = poly.get((i + 1) % poly.size());
			double fa = 2 * (a[0] * ox + a[1] * oz) - rhs, fb = 2 * (b[0] * ox + b[1] * oz) - rhs;
			if (fa <= 0) out.add(a);
			if ((fa < 0 && fb > 0) || (fa > 0 && fb < 0)) {
				double t = fa / (fa - fb);
				out.add(new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
			}
		}
		return out;
	}

	/**
	 * The edge cells {@code p} and {@code q} share, or null when they do not touch (or only at a corner, under
	 * 1 block). Always computed from the lower cell, so both cells get the same numbers.
	 */
	public static Edge sharedEdge(RegionField f, Cell p, Cell q) {
		Cell a = compare(p, q) <= 0 ? p : q, b = a == p ? q : p;
		if (a.equals(b)) return null;
		double ax = f.siteX(a.cx(), a.cz()), az = f.siteZ(a.cx(), a.cz());
		double bx = f.siteX(b.cx(), b.cz()) - ax, bz = f.siteZ(b.cx(), b.cz()) - az; // relative to a
		double mx = bx / 2, mz = bz / 2;
		double len = StrictMath.sqrt(bx * bx + bz * bz);
		double dx = -bz / len, dz = bx / len;
		double r = REACH * f.cellSize;
		double[] range = {-1e18, 1e18};
		// the clip square around a: |m + t d| <= r on both axes
		if (!bound(range, dx, r - mx) || !bound(range, -dx, r + mx) || !bound(range, dz, r - mz) || !bound(range, -dz, r + mz)) return null;
		// every other site o near a or b: 2 p.o <= |o|^2 with p = m + t d
		for (Cell centre : new Cell[] {a, b}) {
			for (int j = -RING; j <= RING; j++) {
				for (int i = -RING; i <= RING; i++) {
					int cx = centre.cx() + i, cz = centre.cz() + j;
					if ((cx == a.cx() && cz == a.cz()) || (cx == b.cx() && cz == b.cz())) continue;
					double ox = f.siteX(cx, cz) - ax, oz = f.siteZ(cx, cz) - az;
					double k = 2 * (dx * ox + dz * oz), rhs = ox * ox + oz * oz - 2 * (mx * ox + mz * oz);
					if (!bound(range, k, rhs)) return null;
				}
			}
		}
		if (range[1] - range[0] < 1) return null;
		return new Edge(ax + mx + range[0] * dx, az + mz + range[0] * dz, ax + mx + range[1] * dx, az + mz + range[1] * dz);
	}

	/** Intersect the parameter range with {@code k t <= rhs}; false when empty. */
	private static boolean bound(double[] range, double k, double rhs) {
		if (StrictMath.abs(k) < 1e-12) return rhs >= 0;
		double t = rhs / k;
		if (k > 0) range[1] = Math.min(range[1], t);
		else range[0] = Math.max(range[0], t);
		return range[0] <= range[1];
	}
}
