package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.settlement.tiers.WallRing;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The wall walk (D2): on a flat town, a player who starts inside one corner tower can walk the whole ring on the wall
 * top and reach every corner tower and every curtain and gate lot, without falling or jumping more than one block.
 * The ring's buildings are voxelised in world space and walked block by block.
 */
class WallWalkTest {
	/** World voxels of the ring's buildings (boxes in order, later ones override earlier). */
	static final class World {
		final Map<Long, Role> roles = new HashMap<>();
		static long key(int x, int y, int z) { return ((long) (x + (1 << 21)) << 32) | ((long) (y + 512) << 22) | (z + (1 << 21)); }

		Role at(int x, int y, int z) { return roles.getOrDefault(key(x, y, z), Role.AIR); }

		void put(PlacedBuilding b, Box bx) {
			Placement p = b.placement();
			Box3 q = bx.box();
			for (int lx = q.x(); lx < q.maxX(); lx++)
				for (int lz = q.z(); lz < q.maxZ(); lz++)
					for (int ly = q.y(); ly < q.maxY(); ly++) {
						long k = key(p.worldX(lx, lz), p.worldY(ly), p.worldZ(lx, lz));
						if (bx.role() == Role.AIR) roles.remove(k); else roles.put(k, bx.role());
					}
		}

		boolean passable(int x, int y, int z) { return !at(x, y, z).solid; }

		boolean standable(int x, int y, int z) { return passable(x, y, z) && passable(x, y + 1, z) && at(x, y - 1, z).solid; }
	}

	record Ring(World world, List<PlacedBuilding> buildings, Map<String, Rect> rects, Map<String, String> types) {}

	static Ring ring(TownProgramTest.Town t) {
		World w = new World();
		List<PlacedBuilding> bs = new ArrayList<>();
		Map<String, Rect> rects = new TreeMap<>();
		Map<String, String> types = new TreeMap<>();
		for (DistrictPlan d : t.city().districts().values()) {
			if (d.lots().stream().noneMatch(l -> WallRing.isRingLotId(l.id()))) continue;
			DistrictScenes.Built b = CityPlanner.scene(t.city(), d.district().id(), t.setup().p().terrain(), t.setup().p().biome(), false);
			for (LotBrief l : b.plan().lots()) if (WallRing.isRingLotId(l.id())) { rects.put(l.id(), new Rect(l.x(), l.z(), l.w(), l.d())); types.put(l.id(), l.type()); }
			for (PlacedBuilding pb : b.scene().buildings()) {
				if (pb.lots().stream().noneMatch(WallRing::isRingLotId)) continue;
				bs.add(pb);
				for (Box bx : pb.shell().boxes()) w.put(pb, bx);
			}
		}
		return new Ring(w, bs, rects, types);
	}

	/** Cells reachable on foot from the start: level moves, one block up or down, never below {@code minY}. */
	static Set<Long> walk(World w, int sx, int sy, int sz, int minY) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<int[]> q = new ArrayDeque<>();
		q.add(new int[] {sx, sy, sz});
		seen.add(World.key(sx, sy, sz));
		int[] dx = {1, -1, 0, 0}, dz = {0, 0, 1, -1};
		while (!q.isEmpty()) {
			int[] c = q.poll();
			for (int i = 0; i < 4; i++)
				for (int dy = -1; dy <= 1; dy++) {
					int x = c[0] + dx[i], y = c[1] + dy, z = c[2] + dz[i];
					if (y < minY || !w.standable(x, y, z) || seen.contains(World.key(x, y, z))) continue;
					if (dy == 1 && !w.passable(c[0], c[1] + 2, c[2])) continue; // headroom at the old spot to step up
					seen.add(World.key(x, y, z));
					q.add(new int[] {x, y, z});
				}
		}
		return seen;
	}

	/** Lots (by id) with a reachable cell at or above {@code minY} inside their rectangle. */
	static List<String> unreached(Ring r, Set<Long> seen, int minRel) {
		List<String> bad = new ArrayList<>();
		for (PlacedBuilding pb : r.buildings()) {
			String id = pb.lots().get(0);
			Rect rc = r.rects().get(id);
			boolean hit = false;
			for (int x = rc.x(); x < rc.x() + rc.w() && !hit; x++)
				for (int z = rc.z(); z < rc.z() + rc.d() && !hit; z++)
					for (int y = pb.oy() + minRel; y < pb.oy() + 14; y++) if (seen.contains(World.key(x, y, z))) { hit = true; break; }
			if (!hit) bad.add(id + " (" + r.types().get(id) + ")");
		}
		return bad;
	}

	/** Flat towns with a ring (every third town is on flat ground, so the lots share one base level). */
	static List<TownProgramTest.Town> flatRings(int max) {
		List<TownProgramTest.Town> out = new ArrayList<>();
		for (int i = 0; i < 90 && out.size() < max; i += 3) {
			TownProgramTest.Town t = TownProgramTest.town(i);
			if (!t.plan().walls().isEmpty()) out.add(t);
		}
		return out;
	}

	@Test
	void theWholeRingCanBeWalkedOnTheWallTopFromAnyCornerTower() {
		List<TownProgramTest.Town> towns = flatRings(5);
		assertTrue(towns.size() >= 3, "flat walled towns " + towns.size());
		int lots = 0;
		for (TownProgramTest.Town t : towns) {
			Ring r = ring(t);
			for (PlacedBuilding start : r.buildings()) {
				if (!r.types().get(start.lots().get(0)).equals("bastion")) continue;
				Placement p = start.placement();
				// the first upper storey of the tower, away from its stair: floor level 6 of the building
				int sx = Integer.MIN_VALUE, sz = 0, y = p.worldY(6);
				for (int lx = 2; lx < 11 && sx == Integer.MIN_VALUE; lx++)
					for (int lz = 2; lz < 10; lz++) if (r.world().standable(p.worldX(lx, lz), y, p.worldZ(lx, lz))) { sx = p.worldX(lx, lz); sz = p.worldZ(lx, lz); break; }
				assertTrue(sx != Integer.MIN_VALUE, "no standable start cell in " + start.lots());
				// walk-level only: nothing below the first upper storey, so the ground and the gate passages do not count
				Set<Long> seen = walk(r.world(), sx, y, sz, start.oy() + 6);
				assertEquals(List.of(), unreached(r, seen, 7), "town " + t.setup().seed() + " from " + start.lots() + " cannot reach");
			}
			lots += r.buildings().size();
		}
		System.out.println("walked rings " + towns.size() + " ring lots " + lots);
	}

	@Test
	void fromTheTownSideOfAGateYouCanClimbToTheWall() {
		int gates = 0;
		for (TownProgramTest.Town t : flatRings(5)) {
			Ring r = ring(t);
			for (PlacedBuilding pb : r.buildings()) {
				String id = pb.lots().get(0);
				if (!r.types().get(id).equals("wall")) continue;
				Rect rc = r.rects().get(id);
				// the middle of the passage, on its floor
				int x = rc.x() + rc.w() / 2, z = rc.z() + rc.d() / 2, y0 = -1;
				for (int y = pb.oy(); y < pb.oy() + 4; y++) if (r.world().standable(x, y, z)) { y0 = y; break; }
				assertTrue(y0 >= 0, "no floor in the passage of " + id);
				Set<Long> seen = walk(r.world(), x, y0, z, pb.oy());
				boolean top = false;
				for (Long k : seen) top |= (int) (((k >> 22) & 0x3ff) - 512) >= pb.oy() + 9;
				assertTrue(top, "gate " + id + " has no way up to the wall top");
				gates++;
			}
		}
		assertTrue(gates >= 3, "gates " + gates);
	}
}
