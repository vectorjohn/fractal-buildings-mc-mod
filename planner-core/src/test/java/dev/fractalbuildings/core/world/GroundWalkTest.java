package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** B3: road surfaces are walkable on every gate window: neighbouring road columns differ by at most one block, the tile border included. */
class GroundWalkTest {
	static final Box3 ALL = new Box3(-100_000, -64, -100_000, 200_000, 400, 200_000);

	/** Steps (neighbouring surface columns more than 1 apart) among the road/paving/deck columns of a tile and its four neighbours, counted for columns of the tile. */
	static List<String> steps(WorldPlanner w, TileInfo info) {
		Map<Long, int[]> top = new HashMap<>();
		int[][] near = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : near) {
			int ix = info.ix() + d[0], iz = info.iz() + d[1];
			if (w.tile(ix, iz).isEmpty()) continue;
			for (GroundPiece g : w.ground(ix, iz)) {
				w.raster(g.ref(), ALL, (x, y, z, role, id, shape) -> {
					if (role != Role.ROAD && role != Role.PAVING && role != Role.DECK) return;
					top.merge(((long) x << 32) ^ (z & 0xffffffffL), new int[] {y, role.ordinal()}, (a, b) -> a[0] >= b[0] ? a : b);
				});
			}
		}
		List<String> out = new ArrayList<>();
		var t = info.tile();
		for (var e : top.entrySet()) {
			int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
			if (!t.contains(x, z)) continue;
			for (int[] d : new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
				int[] o = top.get(((long) (x + d[0]) << 32) ^ ((z + d[1]) & 0xffffffffL));
				if (o != null && Math.abs(o[0] - e.getValue()[0]) > 1) out.add(x + "," + z + " " + Role.values()[e.getValue()[1]] + e.getValue()[0] + " -> " + (x + d[0]) + "," + (z + d[1]) + " " + Role.values()[o[1]] + o[0]);
			}
		}
		return out;
	}

	/**
	 * Known issue, a ratchet and not a rule: streets that meet at a junction or end at another street are graded one by one, so at
	 * steep junctions neighbouring road columns of different streets differ by more than one block (a street can end 20 blocks under
	 * the street it meets). Along one street the grade is checked by {@code road-grade}. B3 measured 234 of 670 gate-window tiles with a step
	 * above 3 blocks (and 376 above 1) before and after the fill/headroom change (which does not move surfaces); the test keeps that
	 * from getting worse until street junction heights are reconciled (ROADMAP polish backlog).
	 */
	@Test
	void junctionStepsDoNotGetWorse() {
		int tiles = 0, over1 = 0, over3 = 0;
		List<String> examples = new ArrayList<>();
		for (GateWorld g : GateWorld.all()) {
			WorldPlanner w = g.planner(WorldPlanner.Caches.DEFAULT, null);
			for (TileInfo info : WorldPlannerParityTest.planned(w, g).values()) {
				List<String> s = steps(w, info);
				tiles++;
				if (s.isEmpty()) continue;
				over1++;
				int m = 0;
				for (String e : s) {
					String[] q = e.split(" ");
					m = Math.max(m, Math.abs(Integer.parseInt(q[1].replaceAll("[A-Z]", "")) - Integer.parseInt(q[4].replaceAll("[A-Z]", ""))));
				}
				if (m > 3) { over3++; if (examples.size() < 3) examples.add(g.name() + " " + info.ix() + "," + info.iz() + ": " + s.get(0)); }
			}
		}
		System.out.println("[B3] walkability: tiles " + tiles + ", with a step over 1: " + over1 + ", over 3: " + over3 + " " + examples);
		assertTrue(tiles > 600);
		assertTrue(over1 <= 400, "tiles with a step over 1 block: " + over1);
		assertTrue(over3 <= 250, "tiles with a step over 3 blocks: " + over3 + " " + examples);
	}

	/**
	 * B3: no road or paving cell stands over a hole the plan itself made. For every surface cell of a tile's ground (and of its four
	 * neighbours, whose streets cross the border), the cell under it is a plan cell (ground or building, not an air clear) or the probed
	 * ground is at or above it. This found one street's air cut removing the earth under a street that crosses it at another level.
	 */
	@Test
	void everyRoadSurfaceHasPlanEarthOrProbedGroundUnderIt() {
		int checked = 0, holes = 0;
		List<String> examples = new ArrayList<>();
		for (GateWorld g : GateWorld.all()) {
			WorldPlanner w = g.planner(WorldPlanner.Caches.DEFAULT, null);
			int k = 0;
			for (TileInfo info : WorldPlannerParityTest.planned(w, g).values()) {
				if (k++ % 3 != 0) continue;
				java.util.Set<Long> solid = new java.util.HashSet<>();
				List<int[]> surfaces = new ArrayList<>();
				for (var b : w.buildings(info.ix(), info.iz())) w.raster(b.ref(), ALL, (x, y, z, role, id, shape) -> { if (role != Role.AIR) solid.add(key(x, y, z)); });
				for (GroundPiece gp : w.ground(info.ix(), info.iz())) {
					w.raster(gp.ref(), ALL, (x, y, z, role, id, shape) -> {
						if (role != Role.AIR) solid.add(key(x, y, z));
						if (role == Role.ROAD || role == Role.PAVING) surfaces.add(new int[] {x, y, z});
					});
				}
				for (int[] s : surfaces) {
					checked++;
					if (solid.contains(key(s[0], s[1] - 1, s[2])) || g.probes().exact().floorY(s[0], s[2]) >= s[1]) continue;
					holes++;
					if (examples.size() < 5) examples.add(g.name() + " " + s[0] + "," + s[1] + "," + s[2] + " probe " + g.probes().exact().floorY(s[0], s[2]));
				}
			}
		}
		System.out.println("[B3] surfaces checked " + checked + ", over a hole " + holes + " " + examples);
		assertTrue(checked > 10_000);
		assertEquals(0, holes, "road or paving cells over a hole: " + examples);
	}

	private static long key(int x, int y, int z) { return ((long) x & 0x3ffffff) << 38 | ((long) z & 0x3ffffff) << 12 | ((long) (y + 64) & 0xfff); }
}
