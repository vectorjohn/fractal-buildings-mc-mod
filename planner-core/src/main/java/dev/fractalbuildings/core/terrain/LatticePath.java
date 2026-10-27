package dev.fractalbuildings.core.terrain;

import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A* on a square lattice of world columns with 8 neighbours. Deterministic: the open set is
 * ordered by (f, g, z, x, waterRun), a total order, and a node's parent only changes on a strictly
 * lower g, so the path depends on neither neighbour generation order nor anything unordered.
 *
 * <p>Costs are in tenths of a block ("units"): a straight step of {@code s} blocks has length cost
 * {@code 10 s}, a diagonal {@code 14 s}. The heuristic is the octile distance in the same units,
 * so a cost function that charges at least the length keeps the search optimal.
 *
 * <p>Water: a run is the number of consecutive water nodes times the step; runs longer than
 * {@code maxWaterRun} blocks are forbidden (the state carries the run, so this is exact).
 * Search is bounded by {@code maxExpansions}; on exhaustion the result is empty.
 */
public final class LatticePath {
	private LatticePath() {}

	public record Pt(int x, int z) {}

	/** Inclusive world-column bounds. */
	public record Bounds(int minX, int minZ, int maxX, int maxZ) {
		public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
	}

	public interface Cost {
		long FORBIDDEN = -1;

		/** Cost in units of moving between adjacent lattice nodes, or {@link #FORBIDDEN}. */
		long step(int x, int z, int nx, int nz);

		/** Whether a node counts as water for the run limit. */
		default boolean water(int x, int z) { return false; }
	}

	public static final int DEFAULT_MAX_EXPANSIONS = 200_000;
	public static final int DEFAULT_MAX_WATER_RUN = 40;

	/**
	 * Default cost: 1 per block of length, 6 per block of height difference, 40 per water node
	 * entered (a bridge), all x10.
	 */
	public static Cost defaultCost(TerrainProbe exact, WaterSamples water) {
		return defaultCost(exact, water, water.stride());
	}

	/** As above for a search on a lattice of {@code step} blocks (which need not equal the water samples' stride). */
	public static Cost defaultCost(TerrainProbe exact, WaterSamples water, int step) {
		int stride = step;
		return new Cost() {
			@Override public long step(int x, int z, int nx, int nz) {
				long len10 = (x != nx && z != nz ? 14L : 10L) * stride;
				long dy = Math.abs(exact.floorY(nx, nz) - exact.floorY(x, z));
				long c = len10 + 60L * dy;
				if (water.water(nx, nz)) c += 400L;
				return c;
			}
			@Override public boolean water(int x, int z) { return water.water(x, z); }
		};
	}

	public static List<Pt> find(Pt start, Pt goal, Cost cost, Bounds bounds, int step) {
		return find(start, goal, cost, bounds, step, DEFAULT_MAX_WATER_RUN, DEFAULT_MAX_EXPANSIONS, false);
	}

	/**
	 * @param step lattice spacing in blocks; nodes are start + k * step, goal must be on the lattice
	 * @param maxWaterRun longest allowed run over water nodes, in blocks; negative = unlimited
	 * @param reverseNeighbours test hook: generate neighbours in the opposite order
	 * @return the points from start to goal inclusive, empty when unreachable or over budget
	 */
	public static List<Pt> find(Pt start, Pt goal, Cost cost, Bounds bounds, int step, int maxWaterRun,
			int maxExpansions, boolean reverseNeighbours) {
		return find(start, goal, cost, bounds, step, maxWaterRun, maxExpansions, reverseNeighbours, true);
	}

	/** As above; {@code diagonal == false} expands the 4 axis neighbours only, with the Manhattan heuristic (METROFILL DX). */
	public static List<Pt> find(Pt start, Pt goal, Cost cost, Bounds bounds, int step, int maxWaterRun,
			int maxExpansions, boolean reverseNeighbours, boolean diagonal) {
		if (step <= 0) throw new IllegalArgumentException("step " + step);
		if (Math.floorMod(goal.x() - start.x(), step) != 0 || Math.floorMod(goal.z() - start.z(), step) != 0) {
			throw new IllegalArgumentException("goal is not on the lattice of start");
		}
		if (!bounds.contains(start.x(), start.z()) || !bounds.contains(goal.x(), goal.z())) return List.of();
		// node (i, j) = start + (i - ox, j - oz) * step, covering the bounds
		int minI = Math.floorDiv(bounds.minX() - start.x() + step - 1, step);
		int maxI = Math.floorDiv(bounds.maxX() - start.x(), step);
		int minJ = Math.floorDiv(bounds.minZ() - start.z() + step - 1, step);
		int maxJ = Math.floorDiv(bounds.maxZ() - start.z(), step);
		int w = maxI - minI + 1, h = maxJ - minJ + 1;
		int runs = maxWaterRun < 0 ? 1 : maxWaterRun / step + 1;
		long total = (long) w * h * runs;
		if (total > 40_000_000L) throw new IllegalArgumentException("search window too large: " + total);
		int n = (int) total;
		long[] g = new long[n];
		Arrays.fill(g, Long.MAX_VALUE);
		int[] parent = new int[n];
		boolean[] closed = new boolean[n];

		int gi = Math.floorDiv(goal.x() - start.x(), step), gj = Math.floorDiv(goal.z() - start.z(), step);
		PriorityQueue<long[]> open = new PriorityQueue<>(LatticePath::compare);
		int s = ((0 - minJ) * w + (0 - minI)) * runs;
		g[s] = 0;
		parent[s] = -1;
		open.add(entry(heuristic(0, 0, gi, gj, step, diagonal), 0, 0, 0, 0, s));
		int[][] dirs = diagonal ? new int[][] {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}} : new int[][] {{0, -1}, {-1, 0}, {1, 0}, {0, 1}};
		int nd = dirs.length;
		int expansions = 0;
		int found = -1;
		while (!open.isEmpty()) {
			long[] e = open.poll();
			int id = (int) e[5];
			if (closed[id]) continue;
			closed[id] = true;
			int run = id % runs;
			int cell = id / runs;
			int i = cell % w + minI, j = cell / w + minJ;
			if (i == gi && j == gj) { found = id; break; }
			if (++expansions > maxExpansions) return List.of();
			int wx = start.x() + i * step, wz = start.z() + j * step;
			for (int q = 0; q < nd; q++) {
				int[] dd = dirs[reverseNeighbours ? nd - 1 - q : q];
				int ni = i + dd[0], nj = j + dd[1];
				if (ni < minI || ni > maxI || nj < minJ || nj > maxJ) continue;
				int nwx = start.x() + ni * step, nwz = start.z() + nj * step;
				long c = cost.step(wx, wz, nwx, nwz);
				if (c < 0) continue;
				int nrun = 0;
				if (maxWaterRun >= 0 && cost.water(nwx, nwz)) {
					nrun = run + 1;
					if ((long) nrun * step > maxWaterRun) continue;
				}
				int nid = ((nj - minJ) * w + (ni - minI)) * runs + nrun;
				if (closed[nid]) continue;
				long ng = g[id] + c;
				if (ng < g[nid]) {
					g[nid] = ng;
					parent[nid] = id;
					open.add(entry(ng + heuristic(ni, nj, gi, gj, step, diagonal), ng, nj, ni, nrun, nid));
				}
			}
		}
		if (found < 0) return List.of();
		ArrayList<Pt> out = new ArrayList<>();
		for (int id = found; id >= 0; id = parent[id]) {
			int cell = id / runs;
			out.add(new Pt(start.x() + (cell % w + minI) * step, start.z() + (cell / w + minJ) * step));
		}
		Collections.reverse(out);
		return out;
	}

	private static long[] entry(long f, long g, int z, int x, int run, int id) {
		return new long[] {f, g, z, x, run, id};
	}

	private static int compare(long[] a, long[] b) {
		for (int k = 0; k < 5; k++) {
			int c = Long.compare(a[k], b[k]);
			if (c != 0) return c;
		}
		return 0;
	}

	private static long heuristic(int i, int j, int gi, int gj, int step, boolean diagonal) {
		long dx = Math.abs(gi - i), dz = Math.abs(gj - j);
		if (!diagonal) return 10L * (dx + dz) * step;
		long diag = Math.min(dx, dz);
		return (14L * diag + 10L * (Math.max(dx, dz) - diag)) * step;
	}
}
