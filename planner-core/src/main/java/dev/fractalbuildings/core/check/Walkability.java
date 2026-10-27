package dev.fractalbuildings.core.check;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;

/**
 * DS-48 "Rooms are walkable" (proposed; 6.18 ROOMWALK), the flood fill shared by the generator's hook placement and the
 * {@code furnished-rooms-walkable} invariant. Every number a reviewer might want to change is a constant here.
 */
public final class Walkability {
	private Walkability() {}

	/** Uses whose lane is 2 wide (DS-48, interior designer: shops, lobbies, banquet rooms, taprooms, the workshop). Everything else is 1 wide. */
	public static final Set<String> WIDE_USES = Set.of("shop", "lobby", "banquet", "taproom", "workshop", "open");
	/** A room whose short side is below this keeps a 1-wide lane even if its use is wide (a 3-wide shop cannot have two lanes). */
	public static final int MIN_SIDE_FOR_WIDE_LANE = 4;
	/** Hook kinds that stand on a floor cell and block it (the rest hang at y2 or higher, or lie flat). */
	public static boolean blocksFloor(String kind) { return !kind.equals("ceiling_light"); }
	/** Hook kinds with no use cell in front (a lantern is lit, not used). */
	public static boolean hasUseCell(String kind) { return !kind.equals("light") && !kind.equals("ceiling_light") && !kind.equals("bed"); }
	/** Kinds the placement may drop first when they break the lane (DS-48 step a); the rest are required for the room. */
	public static boolean optional(String kind) { return kind.equals("table") || kind.equals("bench") || kind.equals("light"); }

	public static boolean wide(String use, int w, int d) { return WIDE_USES.contains(use) && Math.min(w, d) >= MIN_SIDE_FOR_WIDE_LANE; }

	/**
	 * Are all {@code targets} ({x, z} cells) clear and in one connected region of the clear cells ({@code blocked[x][z]} false),
	 * and does each of the {@code groups} (alternative cells, e.g. the free sides of a bed) have a cell in it? With {@code wide},
	 * the region may run only through cells of a clear 2x2 square; a target or group cell outside every square joins it through
	 * a neighbour that is inside one.
	 * @return how many targets and groups are cut off (0 = walkable)
	 */
	public static int cutOff(boolean[][] blocked, List<int[]> targets, List<List<int[]>> groups, boolean wide) {
		int w = blocked.length, d = w == 0 ? 0 : blocked[0].length;
		boolean[][] ok = new boolean[w][d]; // cells the lane may use
		for (int x = 0; x < w; x++)
			for (int z = 0; z < d; z++) ok[x][z] = !blocked[x][z];
		int[] start = null;
		for (int[] t : targets) if (start == null && in(t[0], t[1], w, d) && ok[t[0]][t[1]]) start = t;
		if (wide) {
			boolean[][] lane = new boolean[w][d];
			for (int x = 0; x + 1 < w; x++)
				for (int z = 0; z + 1 < d; z++)
					if (ok[x][z] && ok[x + 1][z] && ok[x][z + 1] && ok[x + 1][z + 1]) lane[x][z] = lane[x + 1][z] = lane[x][z + 1] = lane[x + 1][z + 1] = true;
			boolean[][] stub = new boolean[w][d]; // one clear cell beside a 2x2 square also counts: the cell a door or a piece is used from may be a corner pocket
			for (int x = 0; x < w; x++)
				for (int z = 0; z < d; z++)
					if (ok[x][z] && !lane[x][z] && (x > 0 && lane[x - 1][z] || x + 1 < w && lane[x + 1][z] || z > 0 && lane[x][z - 1] || z + 1 < d && lane[x][z + 1])) stub[x][z] = true;
			for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) lane[x][z] |= stub[x][z];
			for (int[] t : targets) if (in(t[0], t[1], w, d) && ok[t[0]][t[1]]) lane[t[0]][t[1]] = true;
			for (List<int[]> g : groups) for (int[] t : g) if (in(t[0], t[1], w, d) && ok[t[0]][t[1]]) lane[t[0]][t[1]] = true;
			ok = lane;
		}
		if (targets.isEmpty())
			for (List<int[]> g : groups) for (int[] t : g) if (start == null && in(t[0], t[1], w, d) && ok[t[0]][t[1]]) start = t;
		if (start == null) return targets.size() + groups.size();
		boolean[][] seen = new boolean[w][d];
		ArrayDeque<int[]> q = new ArrayDeque<>();
		q.add(start);
		seen[start[0]][start[1]] = true;
		int[] dx = {1, -1, 0, 0}, dz = {0, 0, 1, -1};
		while (!q.isEmpty()) {
			int[] c = q.poll();
			for (int k = 0; k < 4; k++) {
				int nx = c[0] + dx[k], nz = c[1] + dz[k];
				if (in(nx, nz, w, d) && ok[nx][nz] && !seen[nx][nz]) { seen[nx][nz] = true; q.add(new int[] {nx, nz}); }
			}
		}
		int bad = 0;
		for (int[] t : targets) if (!in(t[0], t[1], w, d) || !seen[t[0]][t[1]]) bad++;
		for (List<int[]> g : groups) {
			boolean any = false;
			for (int[] t : g) any |= in(t[0], t[1], w, d) && seen[t[0]][t[1]];
			if (!any) bad++;
		}
		return bad;
	}

	/** The cells of {@code targets} that lie in the same region as the first reachable one, for a room with no furniture yet (a target the empty room cannot join is not asked of the furniture). */
	public static List<int[]> connected(boolean[][] blocked, List<int[]> targets, boolean wide) {
		List<int[]> out = new java.util.ArrayList<>();
		if (targets.isEmpty()) return out;
		for (int[] start : targets) {
			List<int[]> in = new java.util.ArrayList<>();
			for (int[] t : targets) {
				List<int[]> pair = new java.util.ArrayList<>(List.of(start, t));
				if (cutOff(blocked, pair, List.of(), wide) == 0) in.add(t);
			}
			if (in.size() > out.size()) out = in;
			if (out.size() * 2 > targets.size()) break;
		}
		return out;
	}

	private static boolean in(int x, int z, int w, int d) { return x >= 0 && z >= 0 && x < w && z < d; }
}
