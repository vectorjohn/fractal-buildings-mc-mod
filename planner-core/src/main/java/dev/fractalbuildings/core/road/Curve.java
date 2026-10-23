package dev.fractalbuildings.core.road;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a road between its ports (ADR 0004 N4): a centripetal Catmull-Rom curve through the string-pulled lattice
 * nodes, resampled into <i>stations</i> exactly {@link #STATION} blocks apart along the curve. All arithmetic is
 * {@code StrictMath} or plain IEEE operations (D2), so the stations are identical on every JVM.
 */
public final class Curve {
	private Curve() {}

	/** Distance between two stations along the curve, blocks (the unit of the dry check and the deck profile). */
	public static final double STATION = 8.0;

	private static double dist(double[] a, double[] b) {
		double dx = b[0] - a[0], dz = b[1] - a[1];
		return StrictMath.sqrt(dx * dx + dz * dz);
	}

	private static double[] lerp(double[] a, double[] b, double ta, double tb, double t) {
		double u = (t - ta) / (tb - ta);
		return new double[] {a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u};
	}

	/** The curve as a dense polyline (a point about every 2 blocks), through every node, ends included. */
	static List<double[]> dense(List<int[]> nodes) {
		int m = nodes.size();
		List<double[]> out = new ArrayList<>();
		if (m == 0) return out;
		if (m == 1) {
			out.add(new double[] {nodes.get(0)[0], nodes.get(0)[1]});
			return out;
		}
		double[][] p = new double[m + 2][];
		for (int k = 0; k < m; k++) p[k + 1] = new double[] {nodes.get(k)[0], nodes.get(k)[1]};
		p[0] = new double[] {2 * p[1][0] - p[2][0], 2 * p[1][1] - p[2][1]};
		p[m + 1] = new double[] {2 * p[m][0] - p[m - 1][0], 2 * p[m][1] - p[m - 1][1]};
		for (int k = 1; k < m; k++) {
			double[] p0 = p[k - 1], p1 = p[k], p2 = p[k + 1], p3 = p[k + 2];
			double t0 = 0, t1 = t0 + StrictMath.sqrt(dist(p0, p1));
			double t2 = t1 + StrictMath.sqrt(dist(p1, p2));
			double t3 = t2 + StrictMath.sqrt(dist(p2, p3));
			if (t1 == t0) t1 = t0 + 1e-6;
			if (t3 == t2) t3 = t2 + 1e-6;
			int cnt = Math.max(1, (int) StrictMath.ceil(dist(p1, p2) / 2.0));
			for (int s = 0; s < cnt; s++) {
				double t = t1 + (t2 - t1) * s / cnt;
				double[] a1 = lerp(p0, p1, t0, t1, t), a2 = lerp(p1, p2, t1, t2, t), a3 = lerp(p2, p3, t2, t3, t);
				double[] b1 = lerp(a1, a2, t0, t2, t), b2 = lerp(a2, a3, t1, t3, t);
				out.add(lerp(b1, b2, t1, t2, t));
			}
		}
		out.add(p[m]);
		return out;
	}

	/** Stations every {@link #STATION} blocks of arc length from the first node; the last node is always the last station. */
	public static double[][] stations(List<int[]> nodes) {
		List<double[]> d = dense(nodes);
		List<double[]> st = new ArrayList<>();
		if (d.isEmpty()) return new double[0][];
		st.add(d.get(0));
		double carry = 0;
		for (int i = 1; i < d.size(); i++) {
			double[] a = d.get(i - 1), b = d.get(i);
			double seg = dist(a, b);
			if (seg == 0) continue;
			double at = STATION - carry;
			while (at <= seg) {
				double u = at / seg;
				st.add(new double[] {a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u});
				at += STATION;
			}
			carry = seg - (at - STATION);
		}
		double[] last = d.get(d.size() - 1);
		if (dist(st.get(st.size() - 1), last) > 1.0) st.add(last);
		else st.set(st.size() - 1, last);
		return st.toArray(new double[0][]);
	}
}
