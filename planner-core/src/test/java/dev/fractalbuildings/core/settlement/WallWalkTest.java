package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.Brief;
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
 * The wall walk (D2): a player who starts inside one corner tower can walk the whole ring on the wall top and reach every
 * corner tower and every curtain and gate lot, without falling or jumping more than one block, on flat ground and on
 * slopes (noise terrain, a river). The ring's buildings are voxelised in world space and walked block by block.
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

	/**
	 * @param floor per ring-lot column, the lowest y that counts as wall walk there (the lot's first upper storey)
	 * @param levels base level (oy) of each ring piece by its index in the ring
	 * @param complete every piece of the ring was built (a dropped piece leaves a real gap)
	 */
	record Ring(World world, List<PlacedBuilding> buildings, Map<String, Rect> rects, Map<String, String> types, Map<Long, Integer> floor, TreeMap<Integer, PlacedBuilding> byPiece,
			TreeMap<Integer, String> typeOfPiece, boolean complete) {}

	/** Piece indices in clockwise order round the ring. */
	static int[] clockwise(Ring r) { return WallRing.clockwise(new ArrayList<>(r.typeOfPiece().values())); }

	static int pieceIndex(String lotId) { return Integer.parseInt(lotId.substring(lotId.indexOf(WallRing.MARK) + WallRing.MARK.length())); }

	static Ring ring(TownProgramTest.Town t) {
		World w = new World();
		List<PlacedBuilding> bs = new ArrayList<>();
		Map<String, Rect> rects = new TreeMap<>();
		Map<String, String> types = new TreeMap<>();
		Map<Long, Integer> floor = new HashMap<>();
		TreeMap<Integer, PlacedBuilding> byPiece = new TreeMap<>();
		TreeMap<Integer, String> typeOfPiece = new TreeMap<>();
		boolean complete = true;
		for (DistrictPlan d : t.city().districts().values()) {
			if (d.lots().stream().noneMatch(l -> WallRing.isRingLotId(l.id()))) continue;
			DistrictScenes.Built b = CityPlanner.scene(t.city(), d.district().id(), t.setup().p().terrain(), t.setup().p().biome(), false);
			for (LotBrief l : b.plan().lots()) {
				if (!WallRing.isRingLotId(l.id())) continue;
				rects.put(l.id(), new Rect(l.x(), l.z(), l.w(), l.d()));
				types.put(l.id(), l.type());
				typeOfPiece.put(pieceIndex(l.id()), l.type());
				if (!l.builds()) complete = false;
			}
			for (PlacedBuilding pb : b.scene().buildings()) {
				if (pb.lots().stream().noneMatch(WallRing::isRingLotId)) continue;
				bs.add(pb);
				byPiece.put(pieceIndex(pb.lots().get(0)), pb);
				Rect rc = rects.get(pb.lots().get(0));
				for (int x = rc.x(); x < rc.x() + rc.w(); x++) for (int z = rc.z(); z < rc.z() + rc.d(); z++) floor.put(World.key(x, 0, z), pb.oy() + 6);
				for (Box bx : pb.shell().boxes()) w.put(pb, bx);
				for (Box bx : pb.siteBoxes()) w.put(pb, bx);
			}
		}
		// a piece dropped before the lots were planned (wet or taken ground) leaves a gap in the numbering
		int n = typeOfPiece.isEmpty() ? 0 : typeOfPiece.lastKey() + 1;
		if (typeOfPiece.size() != n) complete = false;
		return new Ring(w, bs, rects, types, floor, byPiece, typeOfPiece, complete && n > 0);
	}

	/** Cells reachable on foot from the start over the wall walk: level moves, one block up or down, only where {@code floor} allows. */
	static Set<Long> walk(Ring r, int sx, int sy, int sz) {
		World w = r.world();
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
					Integer fl = r.floor().get(World.key(x, 0, z));
					if (fl == null || (y < fl && w.at(x, y - 1, z) != Role.STAIR) || !w.standable(x, y, z) || seen.contains(World.key(x, y, z))) continue;
					if (dy == 1 && !w.passable(c[0], c[1] + 2, c[2])) continue; // headroom at the old spot to step up
					seen.add(World.key(x, y, z));
					q.add(new int[] {x, y, z});
				}
		}
		return seen;
	}

	/** Cells reachable from the start without leaving the building's own storeys at or above {@code minY} (the gate climb). */
	static Set<Long> climb(World w, int sx, int sy, int sz, int minY) {
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
					if (dy == 1 && !w.passable(c[0], c[1] + 2, c[2])) continue;
					seen.add(World.key(x, y, z));
					q.add(new int[] {x, y, z});
				}
		}
		return seen;
	}

	/** Lots (by id) with a reachable cell at or above their first upper storey inside their rectangle. */
	static List<String> unreached(Ring r, Set<Long> seen) {
		List<String> bad = new ArrayList<>();
		for (PlacedBuilding pb : r.buildings()) {
			String id = pb.lots().get(0);
			Rect rc = r.rects().get(id);
			boolean hit = false;
			for (int x = rc.x(); x < rc.x() + rc.w() && !hit; x++)
				for (int z = rc.z(); z < rc.z() + rc.d() && !hit; z++)
					for (int y = pb.oy() + 7; y < pb.oy() + 14; y++) if (seen.contains(World.key(x, y, z))) { hit = true; break; }
			if (!hit) bad.add(id + " (" + r.types().get(id) + ")");
		}
		return bad;
	}

	/** Towns of a terrain kind (index modulo 3: 0 flat, 1 noise, 2 noise with a river) that have a ring. */
	static List<TownProgramTest.Town> rings(int kind, int max) {
		List<TownProgramTest.Town> out = new ArrayList<>();
		for (int i = kind; i < 120 && out.size() < max; i += 3) {
			TownProgramTest.Town t = TownProgramTest.town(i);
			if (!t.plan().walls().isEmpty()) out.add(t);
		}
		return out;
	}

	record Tally(int rings, int complete, int lots, int steps, int maxStep) {}

	/** Walks every complete ring from each of its corner towers. Returns the tally; fails the test on the first unreachable lot. */
	static Tally walkAll(List<TownProgramTest.Town> towns, boolean sloped) {
		int complete = 0, lots = 0, steps = 0, maxStep = 0;
		for (TownProgramTest.Town t : towns) {
			Ring r = ring(t);
			if (!r.complete()) {
				System.out.println("INCOMPLETE town " + t.setup().seed() + " pieces " + r.typeOfPiece().size() + " of " + (r.typeOfPiece().isEmpty() ? 0 : r.typeOfPiece().lastKey() + 1) + " built " + r.byPiece().size());
				continue;
			}
			complete++;
			for (PlacedBuilding start : r.buildings()) {
				if (!r.types().get(start.lots().get(0)).equals("bastion")) continue;
				Placement p = start.placement();
				// the first upper storey of the tower, away from its stair: floor level 6 of the building
				int sx = Integer.MIN_VALUE, sz = 0, y = p.worldY(6);
				for (int lx = 2; lx < 11 && sx == Integer.MIN_VALUE; lx++)
					for (int lz = 2; lz < 10; lz++) if (r.world().standable(p.worldX(lx, lz), y, p.worldZ(lx, lz))) { sx = p.worldX(lx, lz); sz = p.worldZ(lx, lz); break; }
				assertTrue(sx != Integer.MIN_VALUE, "no standable start cell in " + start.lots());
				assertEquals(List.of(), unreached(r, walk(r, sx, y, sz)), "town " + t.setup().seed() + " from " + start.lots() + " cannot reach");
			}
			lots += r.buildings().size();
			int[] cw = clockwise(r);
			int n = cw.length;
			for (int k = 0; k < n; k++) {
				int d = Math.abs(r.byPiece().get(cw[k]).oy() - r.byPiece().get(cw[(k + 1) % n]).oy());
				maxStep = Math.max(maxStep, d);
				if (d > 0) steps++;
			}
		}
		return new Tally(towns.size(), complete, lots, steps, maxStep);
	}

	@Test
	void theWholeRingCanBeWalkedOnTheWallTopFromAnyCornerTowerOnFlatGround() {
		Tally t = walkAll(rings(0, 5), false);
		System.out.println("flat " + t);
		assertTrue(t.complete() >= 3, "flat walled towns " + t);
		assertEquals(0, t.steps(), "flat ground: every lot at one level");
	}

	@Test
	void theWholeRingCanBeWalkedOnSlopesWithAtMostOneBlockBetweenNeighbours() {
		for (int kind : new int[] {1, 2}) {
			Tally t = walkAll(rings(kind, 8), true);
			System.out.println((kind == 1 ? "noise " : "river ") + t);
			assertTrue(t.complete() >= 3, "sloped walled towns " + t);
			assertTrue(t.maxStep() <= Brief.MAX_STEP, "neighbouring lots differ by " + t.maxStep());
			assertTrue(t.steps() > 0, "the terrain kind must slope under the walls: " + t);
		}
	}

	/** Settlements with a ring on terrain other than the standard three: steep noise, and cities (walled old towns). */
	static List<TownProgramTest.Town> ringsOn(String terrain, dev.fractalbuildings.core.settlement.Tier tier, int radius, int max, int tries) {
		List<TownProgramTest.Town> out = new ArrayList<>();
		for (int i = 0; i < tries && out.size() < max; i++) {
			CityPlannerTest.Setup s = CityPlannerTest.setup(900 + i, terrain, i % 4, -(i % 3), tier, radius);
			CityPlanner.City c = s.plan();
			if (!c.plan().walls().isEmpty()) out.add(new TownProgramTest.Town(s, c));
		}
		return out;
	}

	@Test
	void theRingCanBeWalkedOnSteepGroundAndAroundWalledCities() {
		int rings = 0;
		for (String terrain : new String[] {"noise:9:90:40:150", "noise:7:80:30:100"}) {
			List<TownProgramTest.Town> towns = ringsOn(terrain, dev.fractalbuildings.core.settlement.Tier.TOWN, 280, 6, 60);
			towns.addAll(ringsOn(terrain, dev.fractalbuildings.core.settlement.Tier.CITY, 600, 3, 40));
			for (TownProgramTest.Town t : towns)
				for (DistrictPlan d : t.city().districts().values())
					for (LotBrief l : d.lots()) assertTrue(!WallRing.isRingLotId(l.id()) || l.builds(), "steep ground leaves no gap in the ring: " + l.id() + " " + l.status());
			Tally a = walkAll(ringsOn(terrain, dev.fractalbuildings.core.settlement.Tier.TOWN, 280, 6, 60), true);
			System.out.println("steep town " + terrain + " " + a);
			Tally c = walkAll(ringsOn(terrain, dev.fractalbuildings.core.settlement.Tier.CITY, 600, 3, 40), true);
			System.out.println("steep city " + terrain + " " + c);
			assertTrue(a.maxStep() <= Brief.MAX_STEP && c.maxStep() <= Brief.MAX_STEP);
			rings += a.complete() + c.complete();
		}
		assertTrue(rings >= 6, "complete rings walked " + rings);
	}

	/** Passage floor minus street surface, for every gate lot of the rings, on both faces; the street y is what the brief filler reports in front of the lot. */
	static List<Integer> raised = new ArrayList<>();

	static List<Integer> gateGaps(List<TownProgramTest.Town> towns) {
		raised.clear();
		List<Integer> gaps = new ArrayList<>();
		for (TownProgramTest.Town t : towns) {
			for (DistrictPlan d : t.city().districts().values()) {
				if (d.lots().stream().noneMatch(l -> WallRing.isRingLotId(l.id()))) continue;
				DistrictScenes.Built b = CityPlanner.scene(t.city(), d.district().id(), t.setup().p().terrain(), t.setup().p().biome(), true);
				List<List<Rect>> rects = new ArrayList<>();
				for (Street s : d.streets()) rects.add(dev.fractalbuildings.core.check.SettlementChecks.footprint(s));
				for (PlacedBuilding pb : b.scene().buildings()) {
					LotBrief l = null;
					for (LotBrief q : b.plan().lots()) if (q.id().equals(pb.lots().get(0))) l = q;
					if (!WallRing.isRingLotId(l.id()) || !WallRing.isGateShape(l.type(), l.front(), l.w(), l.d())) continue;
					LotBrief back = new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front().rotate(2), l.type(), l.style(), l.minStoreys(), l.maxStoreys(), l.streets(), l.airRights(), l.frontY(), l.density(), l.status());
					for (LotBrief face : new LotBrief[] {l, back}) {
						int fy = Briefs.frontY(b.plan(), face, rects);
						if (fy == dev.fractalbuildings.core.gen.Brief.NO_FRONT_Y) continue;
						gaps.add(pb.oy() + 1 - fy);
						int hi = Integer.MIN_VALUE; for (int x = l.x(); x < l.x() + l.w(); x++) for (int z = l.z(); z < l.z() + l.d(); z++) hi = Math.max(hi, t.setup().p().terrain().floorY(x, z));
						raised.add(pb.oy() - hi);
					}
				}
			}
		}
		return gaps;
	}

	/**
	 * A gate lot stands at its own ground (where the road passes), never lifted for its neighbours' sake unless a neighbouring curtain is more than
	 * {@link WallRing#MAX_CARVE} blocks above it, and the road through it is graded to its passage floor (ROADS3: the gate is a fixed height on the
	 * road's profile), so on every terrain, steep included, the passage floor is within 1 block of the street on both faces.
	 */
	@Test
	void gatePassagesStayAtTheirOwnGroundAndMeetTheStreet() {
		List<TownProgramTest.Town> towns = new ArrayList<>();
		for (int kind : new int[] {0, 1, 2}) towns.addAll(rings(kind, 8));
		List<Integer> gaps = gateGaps(towns);
		System.out.println("standard gate raise nonzero " + raised.stream().filter(v -> v != 0).toList() + " of " + raised.size() + ", gaps " + gaps);
		assertTrue(gaps.size() >= 40, "gate faces measured " + gaps.size());
		for (int r : raised) assertEquals(0, r, "gate lifted above its own ground: " + raised);
		for (int g : gaps) assertTrue(Math.abs(g) <= 1, "passage floor minus street on a standard terrain: " + gaps);
		List<TownProgramTest.Town> steep = new ArrayList<>();
		for (String terrain : new String[] {"noise:9:90:40:150", "noise:7:80:30:100"}) {
			steep.addAll(ringsOn(terrain, dev.fractalbuildings.core.settlement.Tier.TOWN, 280, 6, 60));
			steep.addAll(ringsOn(terrain, dev.fractalbuildings.core.settlement.Tier.CITY, 600, 3, 40));
		}
		List<Integer> steepGaps = gateGaps(steep);
		int lifted = 0;
		for (int r : raised) if (r > 0) lifted++;
		System.out.println("steep gate faces " + raised.size() + " lifted " + lifted + " " + raised + ", gaps " + steepGaps);
		assertTrue(steepGaps.size() >= 40, "steep gate faces measured " + steepGaps.size());
		assertTrue(lifted * 10 <= raised.size() * 3, "gates lifted above their own ground on steep terrain: " + lifted + " of " + raised.size());
		for (int g : steepGaps) assertTrue(Math.abs(g) <= 1, "passage floor minus street on steep terrain: " + steepGaps);
	}

	@Test
	void fromTheTownSideOfAGateYouCanClimbToTheWall() {
		int gates = 0;
		for (int kind : new int[] {0, 1, 2}) {
			for (TownProgramTest.Town t : rings(kind, 4)) {
				Ring r = ring(t);
				for (PlacedBuilding pb : r.buildings()) {
					String id = pb.lots().get(0);
					if (!r.types().get(id).equals("wall")) continue;
					Rect rc = r.rects().get(id);
					// the middle of the passage, on its floor
					int x = rc.x() + rc.w() / 2, z = rc.z() + rc.d() / 2, y0 = -1;
					for (int y = pb.oy(); y < pb.oy() + 4; y++) if (r.world().standable(x, y, z)) { y0 = y; break; }
					assertTrue(y0 >= 0, "no floor in the passage of " + id);
					Set<Long> seen = climb(r.world(), x, y0, z, pb.oy());
					boolean top = false;
					for (Long k : seen) top |= (int) (((k >> 22) & 0x3ff) - 512) >= pb.oy() + 7; // the standing level of the wall walk: 2 above the first upper storey floor line
					assertTrue(top, "town " + t.setup().seed() + " gate " + id + " oy " + pb.oy() + " rect " + rc + " front " + r.rects().get(id) + " has no way up to the wall top");
					gates++;
				}
			}
		}
		assertTrue(gates >= 6, "gates " + gates);
	}

	/**
	 * Where two lots meet at different levels a curtain does the work, so the walk is stepped, never a jump: the lower curtain climbs a stair ramp, or a
	 * curtain above a tower doorway has a stair cut down through its end.
	 */
	@Test
	void whereLotsMeetAtDifferentLevelsTheCurtainHasStairBlocks() {
		int ramps = 0, cuts = 0;
		for (int kind : new int[] {1, 2}) {
			for (TownProgramTest.Town t : rings(kind, 8)) {
				Ring r = ring(t);
				if (!r.complete()) continue;
				int[] cw = clockwise(r);
				int n = cw.length;
				for (int k = 0; k < n; k++) {
					PlacedBuilding a = r.byPiece().get(cw[k]), b = r.byPiece().get((cw[(k + 1) % n]));
					if (a.oy() == b.oy()) continue;
					String ta = r.types().get(a.lots().get(0)), tb = r.types().get(b.lots().get(0));
					assertTrue(WallRing.joined(ta, a.oy(), tb, b.oy()), "levels of " + a.lots() + " and " + b.lots());
					boolean aCurtain = ta.equals("curtain"), bCurtain = tb.equals("curtain");
					PlacedBuilding work, other;
					if (aCurtain && bCurtain) { work = a.oy() < b.oy() ? a : b; other = work == a ? b : a; }
					else if (aCurtain) { work = a; other = b; }
					else { work = b; other = a; } // a tower beside a curtain: the curtain climbs or cuts down
					if (!(aCurtain || bCurtain)) continue;
					Rect wr = r.rects().get(work.lots().get(0)), orc = r.rects().get(other.lots().get(0));
					int stairs = 0;
					for (int x = wr.x(); x < wr.x() + wr.w(); x++)
						for (int z = wr.z(); z < wr.z() + wr.d(); z++) {
							boolean beside = orc.contains(x + 1, z) || orc.contains(x - 1, z) || orc.contains(x, z + 1) || orc.contains(x, z - 1);
							if (!beside) continue;
							for (int y = Math.min(a.oy(), b.oy()) + 4; y < Math.max(a.oy(), b.oy()) + 12; y++) if (r.world().at(x, y, z) == Role.STAIR && r.world().at(x, y - 1, z).solid) stairs++;
						}
					assertTrue(stairs >= 3, "curtain " + work.lots() + " has " + stairs + " stair cells at its join with " + other.lots());
					if (work.oy() < other.oy()) ramps++; else cuts++;
				}
			}
		}
		System.out.println("ramps " + ramps + " cuts " + cuts);
		assertTrue(ramps >= 4 && cuts >= 1, "stepped joins: ramps " + ramps + " cuts " + cuts);
	}

	@Test
	void theRingLevelsAreTheLeastOnesThatRiseWithTheGroundAndJoinTheirNeighbours() {
		java.util.Random rnd = new java.util.Random(7);
		for (int round = 0; round < 300; round++) {
			int n = 8 + rnd.nextInt(30);
			List<WallRing.Piece> ps = new ArrayList<>();
			int[] top = new int[n];
			int h = 60;
			for (int i = 0; i < n; i++) {
				String type = i % 7 == 0 ? WallRing.TOWER_TYPE : (rnd.nextInt(5) == 0 ? WallRing.WALL : WallRing.CURTAIN);
				ps.add(new WallRing.Piece(type, new Rect(i * 30, 0, 24, 9), dev.fractalbuildings.core.ir.Facing.NORTH, WallRing.NONE));
				h += rnd.nextInt(25) - 12;
				top[i] = h;
			}
			Map<WallRing.Piece, Integer> at = new java.util.IdentityHashMap<>();
			for (int i = 0; i < n; i++) at.put(ps.get(i), top[i]);
			int[] lv = WallRing.walkLevels(ps, at::get);
			int[] cw = WallRing.clockwise(ps.stream().map(WallRing.Piece::type).toList());
			int[] pos = new int[n];
			for (int c = 0; c < n; c++) pos[cw[c]] = c;
			for (int i = 0; i < n; i++) {
				int j = cw[(pos[i] + 1) % n];
				assertTrue(lv[i] >= top[i], "never below the ground");
				assertTrue(WallRing.joined(ps.get(i).type(), lv[i], ps.get(j).type(), lv[j]), "neighbours " + i + "," + j);
			}
			// least: lowering any single level by one breaks a rule
			for (int i = 0; i < n; i++) {
				if (lv[i] == top[i]) continue;
				int[] w2 = lv.clone();
				w2[i]--;
				boolean broken = false;
				for (int q : new int[] {cw[(pos[i] + 1) % n], cw[(pos[i] + n - 1) % n]}) broken |= !WallRing.joined(ps.get(i).type(), w2[i], ps.get(q).type(), w2[q]);
				assertTrue(broken, "level " + i + " could be lower");
			}
		}
	}
}
