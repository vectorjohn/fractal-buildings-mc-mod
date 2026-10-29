package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.raster.Shape;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * STEPTOP: walks the actual rastered cell shapes (the ones the mod places) from the ground to every exterior ground-storey door.
 * A stair-shaped cell tops out a half block above its floor, any other solid cell a full block; a walker climbs at most a half
 * block and drops at most one block. Also counts full-block cells of STAIR role sitting on a lower stair (the jump at the top of a flight).
 */
class RasterEntranceWalkTest {
	/** Largest rise per move, in half blocks: a player jumps one block; the cell shapes must never ask for more (a full block over a half step is 3). */
	static final int RISE = 2;
	/** PORCHSTEP, known issue (ROADMAP 6.19, shot 20.17.47): a flight of a metropolis block or highrise side door ends with a full-block drop to the street; a ratchet, never raise it. */
	static final int KNOWN_FOOT_JUMPS = 5;
	static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};

	static int top2(RasterOwnershipTest.Cell c, int y) {
		if (c == null) return 2 * y + 2; // terrain
		return c.role() == Role.STAIR && c.shape() != Shape.NONE ? 2 * y + 1 : 2 * y + 2;
	}

	@Test
	void everyEntranceDoorIsWalkableFromTheGround() throws Exception {
		List<SceneDoc> scenes = new ArrayList<>(RasterOwnershipTest.scenes(true));
		scenes.addAll(RasterOwnershipTest.scenes(false));
		int doors = 0, bad = 0, jumps = 0, raised = 0, mixed = 0;
		List<String> rm = new ArrayList<>();
		List<String> msgs = new ArrayList<>(), jm = new ArrayList<>();
		for (SceneDoc s : scenes) {
			Box3 h = RasterOwnershipTest.hull(s);
			Map<Long, RasterOwnershipTest.Cell> m = RasterOwnershipTest.rasterScene(s, h, null);
			// full-block steps standing directly on a lower stair-shaped cell: a one block jump
			for (int y = h.y(); y < h.maxY(); y++) for (int z = h.z(); z < h.maxZ(); z++) for (int x = h.x(); x < h.maxX(); x++) {
				var c = m.get(RasterOwnershipTest.key(x, y, z));
				if (c == null || c.role() != Role.STAIR || c.shape() != Shape.NONE) continue;
				var over = m.get(RasterOwnershipTest.key(x, y + 1, z));
				if (over != null && over.role() == Role.STAIR) continue; // support under a step
				for (int d = 0; d < 4; d++) {
					var lo = m.get(RasterOwnershipTest.key(x + DX[d], y - 1, z + DZ[d]));
					if (lo != null && lo.role() == Role.STAIR && lo.shape() != Shape.NONE && lo.owner().equals(c.owner())) { jumps++; jm.add("full-block step over stair at " + x + "," + y + "," + z); break; }
				}
			}
			// walk: multi-source from every terrain column and ground-owned carrier; terrain (not rastered) is a full block under floorY
			TerrainProbe tp = RasterOwnershipTest.probes.get(s);
			Set<Long> seen = new HashSet<>();
			ArrayDeque<int[]> q = new ArrayDeque<>();
			for (int z = h.z(); z < h.maxZ(); z++) for (int x = h.x(); x < h.maxX(); x++) {
				for (int y = Math.max(h.y(), tp.floorY(x, z) - 1); y < h.maxY(); y++) {
					var c = m.get(RasterOwnershipTest.key(x, y, z));
					if (y > tp.floorY(x, z) + 2) break;
					if (!solidAt(m, tp, x, y, z) || !bodyFree(m, tp, x, y, z)) continue;
					if (c != null && !c.owner().equals("ground")) continue;
					int t = top2(c, y);
					if (seen.add(node(x, z, t))) q.add(new int[] {x, z, t});
				}
			}
			Map<Long, List<Integer>> reached = new HashMap<>();
			while (!q.isEmpty()) {
				int[] n = q.poll();
				reached.computeIfAbsent(colKey(n[0], n[1]), k -> new ArrayList<>()).add(n[2]);
				for (int d = 0; d < 4; d++) {
					int nx = n[0] + DX[d], nz = n[1] + DZ[d];
					if (nx < h.x() || nx >= h.maxX() || nz < h.z() || nz >= h.maxZ()) continue;
					for (int y = n[2] / 2 - 3; y <= n[2] / 2 + 2; y++) {
						if (!solidAt(m, tp, nx, y, nz)) continue;
						int t = top2(m.get(RasterOwnershipTest.key(nx, y, nz)), y);
						var nc = m.get(RasterOwnershipTest.key(nx, y, nz));
						int rise = nc == null || nc.owner().equals("ground") ? RISE : (n[2] & 1) == 1 && (t & 1) == 1 ? 2 : 1; // PORCHSTEP: the terrain and the street may ask a jump, and a stair after a stair is a flight; anything else a building placed (a landing, a forecourt, a first step off the street) is a half step
						if (t - n[2] > rise || n[2] - t > 2 || !bodyFree(m, tp, nx, y, nz)) continue;
						if (seen.add(node(nx, nz, t))) q.add(new int[] {nx, nz, t});
					}
				}
			}
			for (PlacedBuilding b : s.buildings()) {
				for (Opening o : doors(b)) {
					doors++;
					Facing out = o.to().equals(Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2);
					Box3 cell = b.placement().toWorld(Box3.of(o.box().x() + out.dx, o.box().y(), o.box().z() + out.dz, 1, 1, 1));
					int floor2 = 2 * cell.y();
					boolean ok = false;
					for (int t : reached.getOrDefault(colKey(cell.x(), cell.z()), List.of())) if (t >= floor2 - 2 && t <= floor2 + 2) ok = true;
					// PORCHSTEP r2: a double door steps under every leaf or none: no leaf on a half step while another leaf has only a drop
					boolean stepped = false, dropped = false;
					for (int lx = 0; lx < o.box().sx(); lx++) for (int lz = 0; lz < o.box().sz(); lz++) {
						Box3 lc = b.placement().toWorld(Box3.of(o.box().x() + lx + out.dx, o.box().y(), o.box().z() + lz + out.dz, 1, 1, 1));
						boolean st = false, fl = false;
						for (int t : reached.getOrDefault(colKey(lc.x(), lc.z()), List.of())) { if (t == floor2 - 1) st = true; if (t >= floor2) fl = true; }
						stepped |= st; dropped |= !st && !fl;
					}
					if (stepped && dropped) { mixed++; { StringBuilder lv = new StringBuilder(); for (int lx = 0; lx < o.box().sx(); lx++) for (int lz = 0; lz < o.box().sz(); lz++) { Box3 lc = b.placement().toWorld(Box3.of(o.box().x() + lx + out.dx, o.box().y(), o.box().z() + lz + out.dz, 1, 1, 1)); lv.append(" ").append(lc.x()).append(",").append(lc.z()).append(reached.getOrDefault(colKey(lc.x(), lc.z()), List.of())); } rm.add("mixed front " + b.shell().spec().type() + " " + b.id() + " door " + o.id() + " floor2 " + floor2 + lv); } }
					boolean flush = false; // PORCHSTEP: a standing surface in the outside cell within half a block of the sill
					for (int t : reached.getOrDefault(colKey(cell.x(), cell.z()), List.of())) if (t >= floor2 - 1 && t <= floor2 + 2) flush = true;
					if (ok && !flush) { raised++;
						rm.add(b.shell().spec().type() + " " + b.id() + " door " + o.id() + " outside " + cell.x() + "," + cell.y() + "," + cell.z() + " surfaces " + reached.get(colKey(cell.x(), cell.z()))); }
					if (!ok) { bad++; msgs.add(b.shell().spec().type() + " door " + o.id() + " of " + b.id() + " outside cell " + cell.x() + "," + cell.y() + "," + cell.z() + " not reachable (surfaces " + reached.getOrDefault(colKey(cell.x(), cell.z()), List.of()) + ", need ~" + floor2 + ")"); }
				}
			}
		}
		System.out.println("entrance walk: " + doors + " doors, " + bad + " unreachable, " + jumps + " full-block steps over a stair, " + raised + " doors a full block above the surface outside");
		rm.stream().limit(25).forEach(System.out::println);
		msgs.stream().limit(15).forEach(System.out::println); jm.stream().limit(15).forEach(System.out::println);
		assertTrue(doors > 20, "doors " + doors);
		assertTrue(jumps == 0, jumps + " full-block steps over a lower stair");
		assertTrue(mixed == 0, mixed + " double doors with a step under some leaves only");
		assertTrue(raised == 0, raised + " entrance doors a full block above the standing surface outside, with no step");
		assertTrue(bad <= KNOWN_FOOT_JUMPS, bad + " entrance doors not reachable from the ground by rastered shapes (known: " + KNOWN_FOOT_JUMPS + ")");
	}


	/** A solid cell, or unrastered terrain under the probe surface. */
	static boolean solidAt(Map<Long, RasterOwnershipTest.Cell> m, TerrainProbe tp, int x, int y, int z) {
		var c = m.get(RasterOwnershipTest.key(x, y, z));
		return c != null ? c.role().solid : y < tp.floorY(x, z);
	}

	static boolean bodyFree(Map<Long, RasterOwnershipTest.Cell> m, TerrainProbe tp, int x, int y, int z) {
		for (int k = 1; k <= 2; k++) {
			var c = m.get(RasterOwnershipTest.key(x, y + k, z));
			if (solidAt(m, tp, x, y + k, z) || (c != null && c.role() == Role.DOOR)) return false;
		}
		return true;
	}

	static long node(int x, int z, int t) { return (((long) t + 4096) << 40) ^ (((long) x + 100000) << 20) ^ ((long) z + 100000); }
	static long colKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

	static List<Opening> doors(PlacedBuilding b) {
		List<Opening> doors = new ArrayList<>();
		if (b.shell().storeys().isEmpty()) return doors;
		int lowest = Integer.MAX_VALUE;
		for (Room r : b.shell().rooms()) lowest = Math.min(lowest, r.storey());
		Storey s0 = b.shell().storeys().get(lowest == Integer.MAX_VALUE ? 0 : lowest);
		for (Opening o : b.shell().openings()) {
			if (o.kind() != OpeningKind.DOOR || o.box().y() < s0.y() || o.box().y() >= s0.y() + s0.height()) continue;
			if (o.to().equals(Opening.OUTSIDE) == o.from().equals(Opening.OUTSIDE)) continue;
			doors.add(o);
		}
		return doors;
	}
}
