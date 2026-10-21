package dev.fractalbuildings.core.terrain;

import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.Rect;
import dev.fractalbuildings.core.terrain.BoxMerge.Item;
import dev.fractalbuildings.core.terrain.GroundBox.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Road and plaza profiles over exact terrain. Heights follow the heightmap convention: a level
 * {@code y} is the first free block above the surface, so the surface block is at {@code y - 1}.
 * Cut = air from {@code y} up to the old ground; fill = earth from the old ground up to the
 * surface block.
 */
public final class Grading {
	private Grading() {}

	public static final int SAMPLE_EVERY = 8;
	public static final int MAX_CUT_FILL = 4;
	/** Road grade limit: at most 1 rise per this many blocks. */
	public static final int GRADE_RUN = 3;
	public static final int BRIDGE_CLEARANCE = 3;
	public static final int PIER_EVERY = 8;
	public static final int PLAZA_MAX_RELIEF = 6;

	/** Centre-line columns of a road, index-aligned arrays. */
	public record Profile(int[] x, int[] z, int[] y, boolean[] bridge) {
		public int size() { return x.length; }
	}

	/** Inclusive centre-line column range whose cut or fill exceeds {@link #MAX_CUT_FILL}. */
	public record Steep(int from, int to) {}

	public record RoadGrade(Profile profile, List<Steep> steep, List<GroundBox> boxes) {
		/** No column needs more than {@link #MAX_CUT_FILL} of cut or fill. When false the boxes still cover the steep columns: reroute or reject the road. */
		public boolean ok() { return steep.isEmpty(); }
	}

	public record PlazaGrade(int level, List<GroundBox> boxes) {}

	/** @param points centre line; consecutive points are joined by straight column runs */
	public static RoadGrade road(List<LatticePath.Pt> points, int width, TerrainProbe exact) {
		return grade(points, width, exact, true);
	}

	/** The height profile and steep runs of a road without building its boxes (the boxes list is empty); same profile as {@link #road}. */
	public static RoadGrade profile(List<LatticePath.Pt> points, TerrainProbe exact) {
		return grade(points, 1, exact, false);
	}

	private static RoadGrade grade(List<LatticePath.Pt> points, int width, TerrainProbe exact, boolean withBoxes) {
		if (points.isEmpty() || width <= 0) throw new IllegalArgumentException("road needs points and a width");
		// 1. centre-line columns
		ArrayList<int[]> cols = new ArrayList<>();
		cols.add(new int[] {points.get(0).x(), points.get(0).z()});
		for (int p = 1; p < points.size(); p++) {
			var a = points.get(p - 1);
			var b = points.get(p);
			int dx = b.x() - a.x(), dz = b.z() - a.z();
			int n = Math.max(Math.abs(dx), Math.abs(dz));
			for (int k = 1; k <= n; k++) {
				cols.add(new int[] {a.x() + (int) Math.floorDiv(2L * dx * k + n, 2L * n),
						a.z() + (int) Math.floorDiv(2L * dz * k + n, 2L * n)});
			}
		}
		int n = cols.size();
		int[] x = new int[n], z = new int[n], g = new int[n], y = new int[n];
		boolean[] wet = new boolean[n];
		for (int i = 0; i < n; i++) {
			x[i] = cols.get(i)[0];
			z[i] = cols.get(i)[1];
			g[i] = exact.floorY(x[i], z[i]);
			wet[i] = exact.waterDepth(x[i], z[i]) > 0;
		}
		// 2. water spans -> fixed deck levels
		int[] deck = new int[n];
		boolean[] spanFirst = new boolean[n], spanLast = new boolean[n];
		for (int i = 0; i < n; ) {
			if (!wet[i]) { i++; continue; }
			int j = i, top = Integer.MIN_VALUE;
			while (j < n && wet[j]) { top = Math.max(top, exact.surfaceY(x[j], z[j])); j++; }
			for (int k = i; k < j; k++) deck[k] = top + BRIDGE_CLEARANCE;
			spanFirst[i] = true;
			spanLast[j - 1] = true;
			i = j;
		}
		// 3. anchors every 8 columns, plus ends and span ends
		int[] anchors = java.util.stream.IntStream.range(0, n)
				.filter(i -> i % SAMPLE_EVERY == 0 || i == n - 1 || spanFirst[i] || spanLast[i]).toArray();
		int m = anchors.length;
		int[] r = new int[m];
		boolean[] fixed = new boolean[m];
		for (int a = 0; a < m; a++) {
			int i = anchors[a];
			if (wet[i]) { r[a] = deck[i]; fixed[a] = true; continue; }
			if (a == 0 || a == m - 1) { r[a] = g[i]; continue; }
			r[a] = median(g[anchors[a - 1]], g[i], g[anchors[a + 1]]);
		}
		// 4. clamp the grade between anchors, bridge anchors fixed
		for (int pass = 0; pass < 200; pass++) {
			boolean changed = false;
			for (int a = 1; a < m; a++) {
				if (fixed[a]) continue;
				int mx = (anchors[a] - anchors[a - 1]) / GRADE_RUN;
				int v = Math.max(r[a - 1] - mx, Math.min(r[a - 1] + mx, r[a]));
				if (v != r[a]) { r[a] = v; changed = true; }
			}
			for (int a = m - 2; a >= 0; a--) {
				if (fixed[a]) continue;
				int mx = (anchors[a + 1] - anchors[a]) / GRADE_RUN;
				int v = Math.max(r[a + 1] - mx, Math.min(r[a + 1] + mx, r[a]));
				if (v != r[a]) { r[a] = v; changed = true; }
			}
			if (!changed) break;
		}
		// 5. interpolate between anchors
		if (m == 1) y[0] = r[0];
		for (int a = 0; a + 1 < m; a++) {
			int d = anchors[a + 1] - anchors[a];
			for (int k = 0; k <= d; k++) y[anchors[a] + k] = r[a] + (int) Math.floorDiv((long) (r[a + 1] - r[a]) * k, d);
		}
		// 6. steep runs
		ArrayList<Steep> steep = new ArrayList<>();
		for (int i = 0; i < n; ) {
			if (wet[i] || Math.abs(y[i] - g[i]) <= MAX_CUT_FILL) { i++; continue; }
			int j = i;
			while (j + 1 < n && !wet[j + 1] && Math.abs(y[j + 1] - g[j + 1]) > MAX_CUT_FILL) j++;
			steep.add(new Steep(i, j));
			i = j + 1;
		}
		if (!withBoxes) return new RoadGrade(new Profile(x, z, y, Arrays.copyOf(wet, n)), steep, List.of());
		// 7. footprint cells, first column to claim a cell wins
		int half = width / 2;
		TreeMap<Long, int[]> cells = new TreeMap<>(); // key (z, x) -> {x, z, level, bridge}
		for (int i = 0; i < n; i++) {
			for (int dz = 0; dz < width; dz++) {
				for (int dx = 0; dx < width; dx++) {
					int cx = x[i] - half + dx, cz = z[i] - half + dz;
					cells.putIfAbsent(((long) cz << 32) + cx, new int[] {cx, cz, y[i], wet[i] ? 1 : 0});
				}
			}
		}
		ArrayList<Item> items = new ArrayList<>();
		for (int[] c : cells.values()) {
			int cx = c[0], cz = c[1], lvl = c[2];
			if (c[3] == 1) {
				items.add(new Item(Kind.DECK, cx, lvl - 1, cz, 1));
				continue;
			}
			int cg = exact.floorY(cx, cz);
			items.add(new Item(Kind.ROAD, cx, lvl - 1, cz, 1));
			if (lvl - 1 > cg) items.add(new Item(Kind.FILL, cx, cg, cz, lvl - 1 - cg));
			else if (lvl < cg) items.add(new Item(Kind.CUT, cx, lvl, cz, cg - lvl));
		}
		for (int i = 0, k = 0; i < n; i++) {
			if (!wet[i]) { k = 0; continue; }
			if (k % PIER_EVERY == PIER_EVERY / 2) {
				int bed = exact.floorY(x[i], z[i]);
				if (deck[i] - 1 > bed) items.add(new Item(Kind.PIER, x[i], bed, z[i], deck[i] - 1 - bed));
			}
			k++;
		}
		return new RoadGrade(new Profile(x, z, y, Arrays.copyOf(wet, n)), steep, BoxMerge.merge(items));
	}

	/** Levels a rectangle at the median of a 4-block lattice of exact heights; empty if it is too rough or wet. */
	public static Optional<PlazaGrade> plaza(Rect rect, TerrainProbe exact) {
		ArrayList<Integer> samples = new ArrayList<>();
		for (int zz = rect.z(); zz < rect.z() + rect.d(); zz += 4) {
			for (int xx = rect.x(); xx < rect.x() + rect.w(); xx += 4) samples.add(exact.floorY(xx, zz));
		}
		int minG = Integer.MAX_VALUE, maxG = Integer.MIN_VALUE;
		for (int zz = rect.z(); zz < rect.z() + rect.d(); zz++) {
			for (int xx = rect.x(); xx < rect.x() + rect.w(); xx++) {
				if (exact.waterDepth(xx, zz) > 0) return Optional.empty();
			}
		}
		for (int s : samples) { minG = Math.min(minG, s); maxG = Math.max(maxG, s); }
		if (maxG - minG > PLAZA_MAX_RELIEF) return Optional.empty();
		samples.sort(null);
		int level = samples.get(samples.size() / 2);
		level = Math.max(level, maxG - MAX_CUT_FILL); // fill first: no cut deeper than the limit
		ArrayList<Item> items = new ArrayList<>();
		for (int zz = rect.z(); zz < rect.z() + rect.d(); zz++) {
			for (int xx = rect.x(); xx < rect.x() + rect.w(); xx++) {
				int cg = exact.floorY(xx, zz);
				items.add(new Item(Kind.PLAZA, xx, level - 1, zz, 1));
				if (level - 1 > cg) items.add(new Item(Kind.FILL, xx, cg, zz, level - 1 - cg));
				else if (level < cg) {
					if (cg - level > MAX_CUT_FILL) return Optional.empty();
					items.add(new Item(Kind.CUT, xx, level, zz, cg - level));
				}
			}
		}
		return Optional.of(new PlazaGrade(level, BoxMerge.merge(items)));
	}

	private static int median(int a, int b, int c) {
		return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
	}
}
