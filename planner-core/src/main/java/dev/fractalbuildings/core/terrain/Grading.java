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
	/**
	 * Earth under every road and plaza surface reaches this far below the probed ground (and below the surface when the road is cut
	 * into the ground): the live ground near a building is the vanilla beardifier's, which lowers it up to its 12-block margin below
	 * what the probe saw, and a fill that starts at the probed ground would leave a gap under the road (B3).
	 */
	public static final int FILL_BELOW = 12;
	/** Air above every road and plaza surface, in blocks counted from the first free level: the beardifier can raise the live ground over a street the probe saw as open (B3). */
	public static final int HEADROOM = 4;

	/** Centre-line columns of a road, index-aligned arrays. */
	public record Profile(int[] x, int[] z, int[] y, boolean[] bridge) {
		public int size() { return x.length; }
	}

	/** Inclusive centre-line column range whose cut or fill exceeds {@link #MAX_CUT_FILL}. */
	public record Steep(int from, int to) {}

	/** @param anchors the column indices whose heights were fixed first; the profile is the floor interpolation between consecutive anchors, so the same anchors and heights give the same profile anywhere (they include every point of the centre line) */
	public record RoadGrade(Profile profile, List<Steep> steep, List<GroundBox> boxes, int[] anchors) {
		/** No column needs more than {@link #MAX_CUT_FILL} of cut or fill. When false the boxes still cover the steep columns: reroute or reject the road. */
		public boolean ok() { return steep.isEmpty(); }
	}

	public record PlazaGrade(int level, List<GroundBox> boxes) {}

	/** @param points centre line; consecutive points are joined by straight column runs */
	public static RoadGrade road(List<LatticePath.Pt> points, int width, TerrainProbe exact) {
		return grade(points, null, width, exact, true);
	}

	/**
	 * A road whose heights are already decided: {@code ys[k]} at {@code points.get(k)}, every point an anchor, the columns between
	 * two points interpolated as {@link #road} interpolates between its anchors (so a street stored as its anchors and their heights
	 * gives back exactly the profile it was graded with, in any tile). Steep runs and boxes as {@link #road}.
	 */
	public static RoadGrade road(List<LatticePath.Pt> points, int[] ys, int width, TerrainProbe exact) {
		return road(points, ys, width, exact, null);
	}

	/** Cells a road leaves to someone else (JUNCTION: an earlier piece of the same street builds them). */
	public interface CellFilter { boolean skip(int x, int z); }

	/** As {@link #road(List, int[], int, TerrainProbe)}, building no surface, earth or air on the cells {@code skip} names (null: none). */
	public static RoadGrade road(List<LatticePath.Pt> points, int[] ys, int width, TerrainProbe exact, CellFilter skip) {
		if (ys.length != points.size()) throw new IllegalArgumentException(ys.length + " heights for " + points.size() + " points");
		return grade(points, ys, null, width, exact, true, skip);
	}

	/** The height profile and steep runs of a road without building its boxes (the boxes list is empty); same profile as {@link #road}. */
	public static RoadGrade profile(List<LatticePath.Pt> points, TerrainProbe exact) {
		return grade(points, null, null, 1, exact, false);
	}

	/**
	 * As {@link #profile(List, TerrainProbe)}, with some columns pinned: {@code pins} maps a centre-line column index (in the order
	 * {@code StreetCols.columns} gives) to the level that column must have (a gate passage the road runs through, ROADS3). Pinned
	 * columns are anchors the grade clamp never moves; the other anchors follow them within the grade limit, so the road climbs or
	 * drops to the pin at 1 in {@link #GRADE_RUN}, and its cut or fill may then exceed {@link #MAX_CUT_FILL} (reported as steep).
	 * With no pins this is exactly {@link #profile(List, TerrainProbe)}.
	 */
	public static RoadGrade profile(List<LatticePath.Pt> points, TerrainProbe exact, java.util.SortedMap<Integer, Integer> pins) {
		return grade(points, null, pins == null || pins.isEmpty() ? null : pins, 1, exact, false);
	}

	private static RoadGrade grade(List<LatticePath.Pt> points, int[] given, int width, TerrainProbe exact, boolean withBoxes) {
		return grade(points, given, null, width, exact, withBoxes, null);
	}

	private static RoadGrade grade(List<LatticePath.Pt> points, int[] given, java.util.SortedMap<Integer, Integer> pins, int width, TerrainProbe exact, boolean withBoxes) {
		return grade(points, given, pins, width, exact, withBoxes, null);
	}

	private static RoadGrade grade(List<LatticePath.Pt> points, int[] given, java.util.SortedMap<Integer, Integer> pins, int width, TerrainProbe exact, boolean withBoxes, CellFilter skip) {
		if (points.isEmpty() || width <= 0) throw new IllegalArgumentException("road needs points and a width");
		// 1. centre-line columns
		List<Integer> pointAt = new ArrayList<>(List.of(0));
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
			pointAt.add(cols.size() - 1);
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
		// 3. anchors every 8 columns, plus ends and span ends (or, with given heights, the points)
		int[] anchors = given != null ? pointAt.stream().mapToInt(Integer::intValue).toArray() : java.util.stream.IntStream.range(0, n)
				.filter(i -> i % SAMPLE_EVERY == 0 || i == n - 1 || spanFirst[i] || spanLast[i] || (pins != null && pins.containsKey(i))).toArray();
		int m = anchors.length;
		int[] r = new int[m], floor = new int[m];
		boolean[] pinned = new boolean[m];
		if (pins != null) for (int a = 0; a < m; a++) pinned[a] = pins.containsKey(anchors[a]);
		if (given != null) System.arraycopy(given, 0, r, 0, m);
		else {
		for (int a = 0; a < m; a++) {
			int i = anchors[a];
			floor[a] = Integer.MIN_VALUE / 2;
			if (wet[i]) { r[a] = pinned[a] ? Math.max(deck[i], pins.get(i)) : deck[i]; floor[a] = deck[i]; continue; } // a pin over water may raise the deck (a junction on a bridge), never lower it
			if (pinned[a]) { r[a] = pins.get(i); continue; }
			if (a == 0 || a == m - 1) { r[a] = g[i]; continue; }
			r[a] = median(g[anchors[a - 1]], g[i], g[anchors[a + 1]]);
		}
		// 4. bridge decks are lower bounds (clearance over the water), not fixed levels: two spans close together over water at
		// different heights would otherwise force a grade steeper than the limit between them. The bounds first raise each other
		// by the grade limit (two sweeps give the envelope), so the clamp below always has a solution.
		for (int a = 1; a < m; a++) floor[a] = Math.max(floor[a], floor[a - 1] - (anchors[a] - anchors[a - 1]) / GRADE_RUN);
		for (int a = m - 2; a >= 0; a--) floor[a] = Math.max(floor[a], floor[a + 1] - (anchors[a + 1] - anchors[a]) / GRADE_RUN);
		for (int a = 0; a < m; a++) r[a] = Math.max(r[a], floor[a]);
		// clamp the grade between anchors, never below an anchor's bound
		for (int pass = 0; pass < 200; pass++) {
			boolean changed = false;
			for (int a = 1; a < m; a++) {
				if (pinned[a]) continue;
				int mx = (anchors[a] - anchors[a - 1]) / GRADE_RUN;
				int v = Math.max(floor[a], Math.max(r[a - 1] - mx, Math.min(r[a - 1] + mx, r[a])));
				if (v != r[a]) { r[a] = v; changed = true; }
			}
			for (int a = m - 2; a >= 0; a--) {
				if (pinned[a]) continue;
				int mx = (anchors[a + 1] - anchors[a]) / GRADE_RUN;
				int v = Math.max(floor[a], Math.max(r[a + 1] - mx, Math.min(r[a + 1] + mx, r[a])));
				if (v != r[a]) { r[a] = v; changed = true; }
			}
			if (!changed) break;
		}
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
		if (!withBoxes) return new RoadGrade(new Profile(x, z, y, Arrays.copyOf(wet, n)), steep, List.of(), anchors);
		// 7. footprint cells, first column to claim a cell wins
		int half = width / 2;
		TreeMap<Long, int[]> cells = new TreeMap<>(); // key (z, x) -> {x, z, level, bridge}
		for (int i = 0; i < n; i++) {
			for (int dz = 0; dz < width; dz++) {
				for (int dx = 0; dx < width; dx++) {
					int cx = x[i] - half + dx, cz = z[i] - half + dz;
					if (skip != null && skip.skip(cx, cz)) continue;
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
			surfaceEarthAndAir(items, cx, cz, lvl, cg);
		}
		for (int i = 0, k = 0; i < n; i++) {
			if (!wet[i]) { k = 0; continue; }
			if (k % PIER_EVERY == PIER_EVERY / 2) {
				int bed = exact.floorY(x[i], z[i]);
				if (deck[i] - 1 > bed) items.add(new Item(Kind.PIER, x[i], bed, z[i], deck[i] - 1 - bed));
			}
			k++;
		}
		return new RoadGrade(new Profile(x, z, y, Arrays.copyOf(wet, n)), steep, BoxMerge.merge(items), anchors);
	}

	/** Levels a rectangle at the median of a 4-block lattice of exact heights; empty if it is too rough or wet. */
	public static Optional<PlazaGrade> plaza(Rect rect, TerrainProbe exact) {
		return plaza(rect, exact, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	/**
	 * As {@link #plaza(Rect, TerrainProbe)}, with the level held within one block of every level in {@code [lo, hi]} (the streets the
	 * plaza touches; {@code hi - lo} at most 2), so it meets them without a step. Empty when that level needs a cut over the limit.
	 */
	public static Optional<PlazaGrade> plaza(Rect rect, TerrainProbe exact, int lo, int hi) {
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
		if (lo != Integer.MIN_VALUE) level = Math.max(hi - 1, Math.min(lo + 1, level)); // meet the streets it touches (the cut check below still holds)
		ArrayList<Item> items = new ArrayList<>();
		for (int zz = rect.z(); zz < rect.z() + rect.d(); zz++) {
			for (int xx = rect.x(); xx < rect.x() + rect.w(); xx++) {
				int cg = exact.floorY(xx, zz);
				items.add(new Item(Kind.PLAZA, xx, level - 1, zz, 1));
				if (level < cg && cg - level > MAX_CUT_FILL) return Optional.empty();
				surfaceEarthAndAir(items, xx, zz, level, cg);
			}
		}
		return Optional.of(new PlazaGrade(level, BoxMerge.merge(items)));
	}

	/**
	 * The earth and air of one surface column: the surface block is at {@code lvl - 1}, the probed ground has its first free block at {@code cg}.
	 * Earth from {@link #FILL_BELOW} under the lower of the two up to the surface block (so a live ground that the beardifier lowered or
	 * raised still has earth under the street), air from {@code lvl} to the probed ground or {@link #HEADROOM} above the surface, whichever is higher.
	 */
	static void surfaceEarthAndAir(List<Item> items, int x, int z, int lvl, int cg) {
		int from = Math.min(cg, lvl - 1) - FILL_BELOW;
		items.add(new Item(Kind.FILL, x, from, z, lvl - 1 - from));
		items.add(new Item(Kind.CUT, x, lvl, z, Math.max(cg, lvl + HEADROOM) - lvl));
	}

	private static int median(int a, int b, int c) {
		return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
	}
}
