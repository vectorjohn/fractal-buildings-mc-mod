package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ROOMWALK (DS-48): {@code furnished-rooms-walkable}, lane-aware hooks, and beds with the head to the wall. */
class RoomWalkTest {
	private static final String ID = Invariant.FURNISHED_ROOMS_WALKABLE.id;
	private static final String[] TYPES = {"house", "shop", "workshop", "hall", "tower", "block", "highrise", "bastion", "temple"};

	private static GenRequest request(String type, long seed, String style) {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:70");
		boolean big = type.equals("highrise") || type.equals("block");
		return new GenRequest(seed, new Lot("lot0", 0, 0, big ? 28 : 22, big ? 28 : 20, Facing.NORTH), type, style, p.terrain(), p.biome());
	}

	private static SceneDoc gen(String type, long seed, String style) {
		try { return new GeneratorV1().generate(request(type, seed, style)); } catch (IllegalArgumentException e) { return null; } // lot too small for the type
	}

	private static List<Violation> mine(SceneDoc s, GenRequest rq) {
		List<Violation> v = new ArrayList<>();
		for (Violation x : Invariants.check(s, rq.terrain())) if (x.invariant().equals(ID)) v.add(x);
		return v;
	}

	@Test
	void everyFurnishedRoomOfEveryTypeIsWalkable() {
		int buildings = 0, hooks = 0;
		for (String type : TYPES)
			for (String style : new String[] {"medieval", "plain"})
				for (long seed = 1; seed <= 25; seed++) {
					SceneDoc s = gen(type, seed, style);
					if (s == null) continue;
					assertEquals(List.of(), mine(s, request(type, seed, style)), type + " " + style + " seed " + seed);
					buildings++;
					for (PlacedBuilding b : s.buildings()) hooks += b.shell().hooks().size();
				}
		assertTrue(buildings > 200 && hooks > 1000, buildings + " buildings, " + hooks + " hooks");
	}

	/** Control: a chest standing on the cell just inside a door cuts the door off, so the invariant must fail. */
	@Test
	void aChestInFrontOfADoorFails() {
		int tried = 0, failed = 0;
		for (String type : new String[] {"house", "workshop", "tower"})
			for (long seed = 1; seed <= 8; seed++) {
				SceneDoc s = gen(type, seed, "medieval");
				if (s == null) continue;
				PlacedBuilding b = s.buildings().get(0);
				BuildingIr ir = b.shell();
				Hook extra = null;
				for (Opening o : ir.openings()) {
					if (o.kind() != OpeningKind.DOOR || o.from().equals(Opening.OUTSIDE)) continue;
					Room r = ir.rooms().stream().filter(x -> x.id().equals(o.from())).findFirst().orElseThrow();
					for (Facing f : Facing.values()) {
						Box3 q = o.box();
						int x = q.x() + f.dx, z = q.z() + f.dz;
						if (r.parts().stream().anyMatch(p -> p.contains(x, q.y(), z)) && extra == null) extra = new Hook("hx", "chest", r.id(), x, q.y(), z, f);
					}
				}
				if (extra == null) continue;
				List<Hook> hooks = new ArrayList<>(ir.hooks());
				hooks.add(extra);
				BuildingIr bad = new BuildingIr(ir.spec(), ir.bounds(), ir.storeys(), ir.volumes(), ir.rooms(), ir.openings(), ir.roofs(), ir.boxes(), hooks);
				PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), bad, b.siteBoxes());
				SceneDoc c = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), s.ground(), List.of());
				tried++;
				if (!mine(c, request(type, seed, "medieval")).isEmpty()) failed++;
			}
		assertTrue(tried >= 8, "tried " + tried);
		assertEquals(tried, failed, "a chest on the cell inside a door must fail furnished-rooms-walkable");
	}


	/**
	 * d15_m36.l12 of the 987654321 world map (metropolis s2_m6, found by city-check --sites cells): in the narrow loose pass the shop counter
	 * went in a corner whose only free neighbour held a light, so its use cell was walled in. The lot, brief and terrain are the district's.
	 */
	@Test
	void aShopCounterIsNotWalledInByALight() {
		ProbeSpecs.Probes p = ProbeSpecs.parse("recorded:../fixtures/terrain/world-987654321-s64.json.gz");
		Lot lot = new Lot("d15_m36.l12", 2403, -5734, 15, 10, Facing.WEST);
		GenRequest rq = new GenRequest(987654321L, lot, "shop", "plains", p.terrain(), p.biome(), new dev.fractalbuildings.core.gen.Brief(4, 4, 1, 1, 68, null, false));
		SceneDoc s = new GeneratorV1().generate(rq);
		assertEquals(List.of(), mine(s, rq));
	}

	/** Beds: foot (the hook) and head (one cell along the facing) are both in the room's air, and the head is against a wall. */
	@Test
	void everyBedHasItsHeadAgainstAWall() {
		int beds = 0;
		for (String type : TYPES)
			for (String style : new String[] {"medieval", "plain"})
				for (long seed = 1; seed <= 25; seed++) {
					SceneDoc s = gen(type, seed, style);
					if (s == null) continue;
					for (PlacedBuilding b : s.buildings()) {
						VoxelGrid g = VoxelGrid.of(b);
						for (Hook h : b.shell().hooks()) {
							if (!h.kind().equals("bed")) continue;
							beds++;
							int hx = h.x() + h.facing().dx, hz = h.z() + h.facing().dz;
							String where = type + " " + style + " seed " + seed + " bed " + h.id();
							assertEquals(Role.AIR, g.get(h.x(), h.y(), h.z()), where + " foot");
							assertEquals(Role.AIR, g.get(hx, h.y(), hz), where + " head");
							Room r = b.shell().rooms().stream().filter(x -> x.id().equals(h.room())).findFirst().orElseThrow();
							assertTrue(r.parts().stream().anyMatch(p -> p.contains(hx, h.y(), hz)), where + " head in the room");
							int bx = hx + h.facing().dx, bz = hz + h.facing().dz;
							assertTrue(g.get(bx, h.y(), bz).solid || g.get(bx, h.y() + 1, bz).solid, where + " head against a wall");
						}
					}
				}
		assertTrue(beds > 100, "beds " + beds);
	}

	/** LAMPS: no room has both a standing light and a hung one (the pit light of a lift shaft is not in a room with a ceiling light). */
	@Test
	void noStandingLightWhereACeilingLightHangs() {
		int rooms = 0;
		for (String type : new String[] {"highrise", "block", "house", "tower"})
			for (long seed = 1; seed <= 15; seed++) {
				SceneDoc s = gen(type, seed, "plain");
				if (s == null) continue;
				for (PlacedBuilding b : s.buildings())
					for (Room r : b.shell().rooms()) {
						boolean standing = false, hung = false;
						for (Hook h : b.shell().hooks()) if (h.room().equals(r.id())) { standing |= h.kind().equals("light"); hung |= h.kind().equals("ceiling_light"); }
						if (hung) rooms++;
						if (r.use().equals("hall") || r.use().equals("lobby")) continue; // TowerShaft's pit light stands in the hall
						assertFalse(standing && hung, type + " seed " + seed + " room " + r.id() + " (" + r.use() + ") has a standing and a hung light");
					}
			}
		assertTrue(rooms > 50, "rooms with ceiling lights " + rooms);
	}

	/** The wall-walk door of a bastion keep is planned around, not furnished over: its room keeps its bed (seeds 0 and 1 used to lose it). */
	@Test
	void aWallWalkDoorDoesNotCostTheRoomItsBed() {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:70");
		int rooms = 0;
		for (long seed = 0; seed < 12; seed++) {
			GenRequest rq = new GenRequest(seed, new Lot("lot0", 0, 0, 12, 12, Facing.NORTH), "bastion", "medieval", p.terrain(), p.biome());
			BuildingIr ir = new GeneratorV1().generate(rq).buildings().get(0).shell();
			for (Opening o : ir.openings()) {
				if (o.kind() != OpeningKind.DOOR || !o.to().equals(Opening.OUTSIDE) || o.box().y() <= ir.storeys().get(0).y() + 2) continue; // not the street door
				Room r = ir.rooms().stream().filter(x -> x.id().equals(o.from())).findFirst().orElseThrow();
				int area = r.parts().stream().mapToInt(b -> b.sx() * b.sz()).sum();
				if (area < 16 || !r.use().equals("room")) continue;
				rooms++;
				assertTrue(ir.hooks().stream().anyMatch(h -> h.room().equals(r.id()) && h.kind().equals("bed")), "seed " + seed + " room " + r.id() + " (" + area + " cells) has a wall door and no bed");
			}
		}
		assertTrue(rooms >= 4, "rooms with a wall door " + rooms);
	}

	@Test
	void walkabilityFloodFill() {
		boolean[][] open = new boolean[5][5];
		List<int[]> two = List.of(new int[] {0, 0}, new int[] {4, 4});
		assertEquals(0, Walkability.cutOff(open, two, List.of(), false));
		boolean[][] wall = new boolean[5][5];
		for (int z = 0; z < 5; z++) wall[2][z] = true;
		assertEquals(1, Walkability.cutOff(wall, two, List.of(), false), "a wall across the room cuts the far target off");
		boolean[][] thin = new boolean[5][5]; // a 1-wide lane along z = 2: fine for lane 1, too thin for a 2-wide lane
		for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) thin[x][z] = z != 2;
		List<int[]> ends = List.of(new int[] {0, 2}, new int[] {4, 2});
		assertEquals(0, Walkability.cutOff(thin, ends, List.of(), false));
		assertEquals(1, Walkability.cutOff(thin, ends, List.of(), true), "a 1-wide lane is not a 2-wide lane");
		assertEquals(1, Walkability.cutOff(open, List.of(new int[] {0, 0}), List.of(List.of(new int[] {9, 9})), false), "a group with no cell in the room fails");
	}
}
