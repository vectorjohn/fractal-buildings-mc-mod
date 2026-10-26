package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Glazing measurement for DS-33 (ROADMAP 7.12): the share of a building's exterior wall area that is window,
 * and the sizes of its windows. A report, not an invariant. Exterior wall area is the count of wall-plane
 * cells (wall, trim, beam, window, door) from the ground-floor slab up to the top storey's ceiling that have
 * open air outside on a horizontal side (air connected to the outside of the grid, window holes included);
 * glass is the window cells among them. A window is a connected group of window cells on one face; 1x1 is
 * the accent size.
 */
public final class Glazing {
	private Glazing() {}

	/** Cell counts of one building (or the sum over many). {@code wall} includes {@code glass} and doors. */
	public record Stats(long glass, long wall, long glassGround, long wallGround, long glassUpper, long wallUpper, long windows, long windows1x1, long windows2Tall, long windows2Wide, long windows2x2, long north2x2, List<int[]> small) {
		public static final Stats ZERO = new Stats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());

		public Stats plus(Stats o) {
			return new Stats(glass + o.glass, wall + o.wall, glassGround + o.glassGround, wallGround + o.wallGround, glassUpper + o.glassUpper, wallUpper + o.wallUpper,
					windows + o.windows, windows1x1 + o.windows1x1, windows2Tall + o.windows2Tall, windows2Wide + o.windows2Wide, windows2x2 + o.windows2x2, north2x2 + o.north2x2, small);
		}

		public double share() { return wall == 0 ? 0 : (double) glass / wall; }
		public double ground() { return wallGround == 0 ? 0 : (double) glassGround / wallGround; }
		public double upper() { return wallUpper == 0 ? 0 : (double) glassUpper / wallUpper; }
		public double share1x1() { return windows == 0 ? 0 : (double) windows1x1 / windows; }
		public double share2x2() { return windows == 0 ? 0 : (double) windows2x2 / windows; }
	}

	private static final int[] DX = {1, -1, 0, 0}, DZ = {0, 0, 1, -1};

	public static Stats measure(PlacedBuilding b) { return measure(b, VoxelGrid.of(b)); }

	/** As above over a voxel grid the caller already has. {@code small} lists up to 8 local cells of 1x1 windows. */
	public static Stats measure(PlacedBuilding b, VoxelGrid g) {
		List<Storey> st = b.shell().storeys();
		if (st.isEmpty()) return Stats.ZERO;
		int y0 = st.get(0).y() + 1, y1 = st.get(st.size() - 1).y() + st.get(st.size() - 1).height();
		int groundTop = st.size() > 1 ? st.get(1).y() : y1;
		int ox = g.minX - 1, oy = g.minY - 1, oz = g.minZ - 1, ex = g.sx + 2, ey = g.sy + 2, ez = g.sz + 2;
		boolean[] hole = new boolean[ex * ey * ez];
		boolean[] win = new boolean[ex * ey * ez];
		for (Opening o : b.shell().openings()) {
			if (o.kind() != OpeningKind.WINDOW) continue;
			Box3 q = o.box();
			for (int y = q.y(); y < q.maxY(); y++)
				for (int z = q.z(); z < q.maxZ(); z++)
					for (int x = q.x(); x < q.maxX(); x++)
						if (g.inGrid(x, y, z)) {
							int i = ((y - oy) * ez + (z - oz)) * ex + (x - ox);
							win[i] = true;
							if (g.get(x, y, z) == Role.AIR) hole[i] = true;
						}
		}
		boolean[] out = new boolean[ex * ey * ez];
		int[] queue = new int[out.length];
		int head = 0, tail = 0;
		out[0] = true;
		queue[tail++] = 0;
		int[] dx = {1, -1, 0, 0, 0, 0}, dy = {0, 0, 1, -1, 0, 0}, dz = {0, 0, 0, 0, 1, -1};
		while (head < tail) {
			int c = queue[head++];
			int cx = c % ex, cz = (c / ex) % ez, cy = c / (ex * ez);
			for (int d = 0; d < 6; d++) {
				int nx = cx + dx[d], ny = cy + dy[d], nz = cz + dz[d];
				if (nx < 0 || ny < 0 || nz < 0 || nx >= ex || ny >= ey || nz >= ez) continue;
				int ni = (ny * ez + nz) * ex + nx;
				if (out[ni] || hole[ni] || g.get(ox + nx, oy + ny, oz + nz).closesShell) continue;
				out[ni] = true;
				queue[tail++] = ni;
			}
		}
		long glass = 0, wall = 0, gG = 0, wG = 0, gU = 0, wU = 0;
		boolean[] glassCell = new boolean[ex * ey * ez];
		// face id per glass cell: the horizontal direction of the open air (0..3)
		int[] face = new int[ex * ey * ez];
		for (int y = y0; y < y1; y++)
			for (int z = g.minZ; z < g.minZ + g.sz; z++)
				for (int x = g.minX; x < g.minX + g.sx; x++) {
					int i = ((y - oy) * ez + (z - oz)) * ex + (x - ox);
					Role r = g.get(x, y, z);
					boolean isGlass = r == Role.WINDOW || (r == Role.AIR && win[i]);
					if (!isGlass && r != Role.WALL && r != Role.TRIM && r != Role.BEAM && r != Role.DOOR) continue;
					int f = -1;
					for (int d = 0; d < 4 && f < 0; d++) {
						int j = ((y - oy) * ez + (z + DZ[d] - oz)) * ex + (x + DX[d] - ox);
						if (out[j]) f = d;
					}
					if (f < 0) continue;
					wall++;
					boolean ground = y < groundTop;
					if (ground) wG++; else wU++;
					if (isGlass) {
						glass++;
						if (ground) gG++; else gU++;
						glassCell[i] = true;
						face[i] = f;
					}
				}
		// windows: connected glass groups on one face (4-neighbourhood in the face plane)
		long n = 0, n11 = 0, n2t = 0, n2w = 0, n22 = 0, nN22 = 0;
		List<int[]> small = new ArrayList<>();
		boolean[] seen = new boolean[glassCell.length];
		for (int y = y0; y < y1; y++)
			for (int z = g.minZ; z < g.minZ + g.sz; z++)
				for (int x = g.minX; x < g.minX + g.sx; x++) {
					int i = ((y - oy) * ez + (z - oz)) * ex + (x - ox);
					if (!glassCell[i] || seen[i]) continue;
					int f = face[i];
					List<int[]> todo = new ArrayList<>();
					todo.add(new int[] {x, y, z});
					seen[i] = true;
					int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, cells = 0;
					boolean alongX = f >= 2; // faces looking +-z run along x
					while (!todo.isEmpty()) {
						int[] c = todo.remove(todo.size() - 1);
						cells++;
						int u = alongX ? c[0] : c[2];
						minU = Math.min(minU, u); maxU = Math.max(maxU, u);
						minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
						int[][] nb = alongX ? new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}} : new int[][] {{0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
						for (int[] d : nb) {
							int nx = c[0] + d[0], ny = c[1] + d[1], nz = c[2] + d[2];
							if (nx < ox || nz < oz || ny < oy || nx >= ox + ex || nz >= oz + ez || ny >= oy + ey) continue;
							int j = ((ny - oy) * ez + (nz - oz)) * ex + (nx - ox);
							if (glassCell[j] && !seen[j] && face[j] == f) {
								seen[j] = true;
								todo.add(new int[] {nx, ny, nz});
							}
						}
					}
					int w = maxU - minU + 1, h = maxY - minY + 1;
					n++;
					if (w == 1 && h == 1) { n11++; if (small.size() < 8) small.add(new int[] {x, y, z}); }
					if (h >= 2) n2t++;
					if (w >= 2) n2w++;
					if (w >= 2 && h >= 2) { n22++; if (f == 3) nN22++; } // f == 3: open air to the north (-z), the front in the local frame
				}
		return new Stats(glass, wall, gG, wG, gU, wU, n, n11, n2t, n2w, n22, nN22 > 0 ? 1 : 0, small);
	}

	/** Accumulates stats by key (building type, style) and renders the DS-33 report. */
	public static final class Report {
		private final Map<String, Stats> sums = new TreeMap<>();
		private final Map<String, double[]> range = new TreeMap<>();
		private final Map<String, Integer> counts = new TreeMap<>();

		public synchronized void add(String key, Stats s) {
			sums.merge(key, s, Stats::plus);
			counts.merge(key, 1, Integer::sum);
			double[] r = range.computeIfAbsent(key, k -> new double[] {1, 0});
			if (s.wall > 0) { r[0] = Math.min(r[0], s.share()); r[1] = Math.max(r[1], s.share()); }
		}

		public Stats total(String key) { return sums.getOrDefault(key, Stats.ZERO); }

		public String toMarkdown(String keyName) {
			StringBuilder sb = new StringBuilder();
			sb.append("| ").append(keyName).append(" | buildings | glass share | ground floor | upper floors | min..max per building | windows | 1x1 | 2+ tall | 2+ wide | 2x2 | DS-33 target |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n");
			for (var e : sums.entrySet()) {
				Stats s = e.getValue();
				double[] r = range.get(e.getKey());
				sb.append(String.format(Locale.ROOT, "| %s | %d | %.0f %% | %.0f %% | %.0f %% | %.0f..%.0f %% | %d | %.0f %% | %.0f %% | %.0f %% | %.0f %% | %s |\n", e.getKey(), counts.get(e.getKey()),
						100 * s.share(), 100 * s.ground(), 100 * s.upper(), 100 * r[0], 100 * r[1], s.windows, 100 * s.share1x1(),
						s.windows == 0 ? 0 : 100.0 * s.windows2Tall / s.windows, s.windows == 0 ? 0 : 100.0 * s.windows2Wide / s.windows, 100 * s.share2x2(), target(e.getKey())));
			}
			return sb.toString();
		}

		private static String target(String type) {
			return switch (type) {
				case "house", "workshop", "wall", "temple" -> "15-25 % (cottage), 2x2 15-30 % in 2-wide styles";
				case "shop" -> "upper 25-40 %, shopfront 50-70 %, upper sill +1";
				case "hall", "block" -> "25-40 %";
				case "tower" -> "-";
				case "highrise" -> "35-90 % (7.13)";
				default -> "";
			};
		}
	}
}
