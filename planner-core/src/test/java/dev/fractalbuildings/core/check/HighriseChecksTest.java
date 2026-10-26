package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.ir.Volume;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * B0 high-rise design checks on a hand-built toy tower (12 x 11 plan, lobby 6 high, two typical floors, a stair house on the
 * roof) that passes all six, and one broken copy per failure mode.
 *
 * <p>Plan (local x right, z back): north band z 1..3 holds a flat room (x 1..4) and the shaft (x 6..8); a wall at z 4; the
 * corridor z 5..6 (the lobby on storey 0); a wall at z 7; the stairwell z 8..9 (x 1..9), two lanes of switchback flights.
 */
class HighriseChecksTest {
	static final int[] Y = {0, 6, 10, 14}; // storey slabs; storey 3 is the stair house on the roof slab
	static final int[] H = {6, 4, 4, 4};

	/** A tower as lists, so a test can edit what it needs. */
	static final class Tower {
		final List<Storey> storeys = new ArrayList<>();
		final List<Volume> volumes = new ArrayList<>();
		final List<Room> rooms = new ArrayList<>();
		final List<Opening> openings = new ArrayList<>();
		final List<Roof> roofs = new ArrayList<>();
		final List<Box> boxes = new ArrayList<>();
		final List<Hook> hooks = new ArrayList<>();
		int volW = 12, volD = 11;

		Tower() {
			for (int k = 0; k < 4; k++) storeys.add(new Storey(k, Y[k], H[k]));
			volumes.add(new Volume("main", Box3.of(0, 0, 0, 12, 14, 11), null));
			volumes.add(new Volume("house", Box3.of(0, 14, 7, 11, 5, 4), "main"));
			roofs.add(new Roof("flat", "main", null, 14, 0, 1));
			roofs.add(new Roof("flat", "house", null, 18, 0, 1));
			box(0, 14, 0, 12, 1, 11, Role.ROOF, -1); // before the storeys: the top flight carves its hole
			for (int k = 0; k < 3; k++) storey(k);
			for (int k = 0; k < 3; k++) flight(k); // after every slab, so the holes carve
			parapet();
			house();
		}

		void box(int x, int y, int z, int sx, int sy, int sz, Role r, int storey) { boxes.add(new Box(Box3.of(x, y, z, sx, sy, sz), r, Layer.SHELL, storey)); }

		void door(String id, int x, int y, int z, Facing f, String from, String to) { openings.add(new Opening(id, OpeningKind.DOOR, Box3.of(x, y, z, 1, 2, 1), f, from, to)); box(x, y, z, 1, 2, 1, Role.DOOR, -1); }

		void storey(int k) {
			int y = Y[k], h = H[k];
			box(0, y, 0, 12, 1, 11, Role.FLOOR, k);
			box(0, y + 1, 0, 12, h - 1, 1, Role.WALL, k);
			box(0, y + 1, 10, 12, h - 1, 1, Role.WALL, k);
			box(0, y + 1, 0, 1, h - 1, 11, Role.WALL, k);
			box(11, y + 1, 0, 1, h - 1, 11, Role.WALL, k);
			box(0, y + 1, 7, 12, h - 1, 1, Role.WALL, k); // stairwell wall
			box(10, y + 1, 8, 1, h - 1, 2, Role.WALL, k);
			box(5, y + 1, 1, 1, h - 1, 3, Role.WALL, k); // shaft west
			box(9, y + 1, 1, 2, h - 1, 3, Role.WALL, k); // shaft east
			if (k == 0) box(5, y + 1, 4, 7, h - 1, 1, Role.WALL, k); // lobby: open to the west of the shaft
			else box(0, y + 1, 4, 12, h - 1, 1, Role.WALL, k);
			String well = "w" + k, hall = k == 0 ? "lobby" : "hall" + k;
			if (k == 0) {
				rooms.add(new Room("lobby", 0, "main", List.of(Box3.of(1, 1, 1, 4, 5, 6), Box3.of(5, 1, 5, 6, 5, 2)), "lobby"));
				door("street", 3, 1, 0, Facing.SOUTH, Opening.OUTSIDE, "lobby");
			} else {
				rooms.add(new Room("flat" + k, k, "main", List.of(Box3.of(1, y + 1, 1, 4, h - 1, 3)), "flat"));
				rooms.add(new Room(hall, k, "main", List.of(Box3.of(1, y + 1, 5, 10, h - 1, 2)), "corridor"));
				door("unit" + k, 2, y + 1, 4, Facing.SOUTH, "flat" + k, hall);
			}
			rooms.add(new Room(well, k, "main", List.of(Box3.of(1, y + 1, 8, 9, h - 1, 2)), "stairwell"));
			door("sdoor" + k, 1, y + 1, 7, Facing.SOUTH, hall, well);
			// the shaft: carve the slab above storey 0..1 (the storey's own ceiling) and a window-role doorway
			box(7, y + 1, 4, 1, 2, 1, Role.WINDOW, k);
			if (k >= 1) box(6, y, 1, 3, 1, 3, Role.AIR, k);
		}

		/** The switchback flight from storey k up: even k east along z 8, odd k west along z 9. */
		void flight(int k) {
			int y = Y[k], h = H[k], up = Y[k + 1];
			boolean east = k % 2 == 0;
			int z = east ? 8 : 9;
			if (east) box(2, up, z, h - 1, 1, 1, Role.AIR, k);
			else box(10 - h, up, z, h - 1, 1, 1, Role.AIR, k);
			for (int i = 0; i < h; i++) box(east ? 2 + i : 8 - i, y + 1 + i, z, 1, 1, 1, Role.STAIR, k);
			Box3 run = east ? Box3.of(2, y + 1, z, h, h, 1) : Box3.of(9 - h, y + 1, z, h, h, 1);
			openings.add(new Opening("f" + k, OpeningKind.STAIR, run, east ? Facing.EAST : Facing.WEST, "w" + k, "w" + (k + 1)));
		}

		void house() {
			box(0, 15, 7, 11, 3, 1, Role.WALL, 3);
			box(0, 15, 10, 11, 3, 1, Role.WALL, 3);
			box(0, 15, 7, 1, 3, 4, Role.WALL, 3);
			box(10, 15, 7, 1, 3, 4, Role.WALL, 3);
			box(0, 18, 7, 11, 1, 4, Role.ROOF, 3);
			rooms.add(new Room("w3", 3, "house", List.of(Box3.of(1, 15, 8, 9, 3, 2)), "stairwell"));
			door("roofdoor", 5, 15, 7, Facing.NORTH, "w3", Opening.OUTSIDE);
			plant();
			lights();
		}


		/** The roof edge: a parapet wall with a rail on it, all round. */
		void parapet() {
			for (int[] b : new int[][] {{0, 0, 12, 1}, {0, 10, 12, 1}, {0, 0, 1, 11}, {11, 0, 1, 11}}) {
				box(b[0], 15, b[1], b[2], 1, b[3], Role.WALL, -1);
				box(b[0], 16, b[1], b[2], 1, b[3], Role.RAIL, -1);
			}
		}

		/** The plant room on the roof over the shaft. */
		void plant() {
			volumes.add(new Volume("plant", Box3.of(5, 14, 0, 5, 5, 4), "main"));
			roofs.add(new Roof("flat", "plant", null, 18, 0, 1));
			box(5, 15, 0, 5, 3, 4, Role.WALL, 3);
			box(6, 15, 1, 3, 3, 3, Role.AIR, 3);
			box(5, 18, 0, 5, 1, 4, Role.ROOF, 3);
			rooms.add(new Room("plant3", 3, "plant", List.of(Box3.of(6, 15, 1, 3, 3, 3)), "plant"));
		}

		void hook(String room, String kind, int x, int y, int z) { hooks.add(new Hook("h" + hooks.size(), kind, room, x, y, z, Facing.SOUTH)); }

		/** A ceiling light in every room (two in the lobby), windows to the outside, a light on the pit floor. */
		void lights() {
			for (Room r : rooms) {
				if (r.id().equals("lobby")) { hook("lobby", "ceiling_light", 3, 1, 3); hook("lobby", "ceiling_light", 8, 1, 5); continue; }
				Box3 p = r.parts().get(0);
				hook(r.id(), "ceiling_light", p.x() + p.sx() / 2, p.y(), p.z() + p.sz() / 2);
			}
			hook("shaft", "light", 7, 1, 2);
			for (Room r : rooms) {
				if (r.use().equals("flat")) openings.add(new Opening("win-" + r.id(), OpeningKind.WINDOW, Box3.of(2, r.parts().get(0).y() + 1, 0, 1, 1, 1), Facing.NORTH, r.id(), Opening.OUTSIDE));
				if (r.use().equals("lobby")) openings.add(new Opening("win-lobby", OpeningKind.WINDOW, Box3.of(4, 2, 0, 1, 1, 1), Facing.NORTH, r.id(), Opening.OUTSIDE));
			}
		}

		BuildingIr ir() {
			return new BuildingIr(new BuildingSpec(2, 1L, "plain", "highrise", volW, volD, 3, "flat"), Box3.of(0, 0, 0, 12, 19, 11), storeys, volumes, rooms, openings, roofs, boxes, hooks);
		}

		List<Violation> check() { return HighriseChecks.check(ir()); }

		List<Violation> check(String id) { return check().stream().filter(v -> v.invariant().equals(id)).toList(); }

		void rooms(UnaryOperator<Room> f) { rooms.replaceAll(f); }

		void dropOpenings(Predicate<Opening> p) { openings.removeIf(p); }
	}

	static void assertPasses(Tower t, String id) {
		assertEquals(List.of(), t.check(id).stream().map(Violation::message).toList(), id);
	}

	static void assertFails(Tower t, String id, String fragment) {
		List<Violation> v = t.check(id);
		assertTrue(!v.isEmpty(), id + " should fail: " + fragment);
		assertTrue(v.stream().anyMatch(x -> x.message().contains(fragment)), id + " messages " + v.stream().map(Violation::message).toList() + " should contain '" + fragment + "'");
		for (Violation x : v) assertEquals(id, x.invariant());
	}

	@Test
	void theToyTowerPassesEveryCheck() {
		assertEquals(List.of(), new Tower().check().stream().map(v -> v.invariant() + ": " + v.message()).toList());
	}

	// highrise-lobby

	@Test
	void lobbyNeedsALobbyRoomOnStoreyZero() {
		Tower t = new Tower();
		t.rooms(r -> r.use().equals("lobby") ? new Room(r.id(), r.storey(), r.volume(), r.parts(), "entry") : r);
		assertFails(t, HighriseChecks.LOBBY, "no room of use lobby");
	}

	@Test
	void lobbyMustBeWhereTheStreetDoorOpens() {
		Tower t = new Tower();
		t.dropOpenings(o -> o.id().equals("street"));
		assertFails(t, HighriseChecks.LOBBY, "no street door");
		Tower u = new Tower();
		u.rooms.add(new Room("annex", 0, "main", List.of(Box3.of(9, 1, 1, 1, 5, 1)), "room"));
		u.openings.replaceAll(o -> o.id().equals("street") ? new Opening("street", o.kind(), o.box(), o.facing(), o.from(), "annex") : o);
		assertFails(u, HighriseChecks.LOBBY, "no street door opens into a lobby");
	}

	@Test
	void lobbyMustBeFiveClear() {
		Tower t = new Tower();
		t.rooms(r -> r.use().equals("lobby") ? new Room(r.id(), r.storey(), r.volume(), r.parts().stream().map(p -> Box3.of(p.x(), p.y(), p.z(), p.sx(), 4, p.sz())).toList(), "lobby") : r);
		assertFails(t, HighriseChecks.LOBBY, "under 5 clear");
	}

	@Test
	void lobbyNeedsADoorToTheStairwell() {
		Tower t = new Tower();
		t.dropOpenings(o -> o.id().equals("sdoor0"));
		assertFails(t, HighriseChecks.LOBBY, "no door onto the storey 0 stairwell");
	}

	// floor-repeat

	@Test
	void typicalFloorsMustRepeatDoorsAndWindows() {
		Tower t = new Tower();
		t.box(3, 11, 4, 1, 2, 1, Role.DOOR, 2); // a second door in the storey 2 unit wall
		assertFails(t, HighriseChecks.FLOOR_REPEAT, "1 of 1 pairs");
		Tower u = new Tower();
		u.box(1, 11, 0, 1, 2, 1, Role.WINDOW, 2); // a window the storey below lacks
		assertFails(u, HighriseChecks.FLOOR_REPEAT, "differ");
		Tower w = new Tower();
		w.box(7, 11, 4, 1, 2, 1, Role.WALL, 2); // closes the shaft doorway on storey 2 only
		assertFails(w, HighriseChecks.FLOOR_REPEAT, "differ");
	}

	@Test
	void floorsWithDifferentRoomsOrHeightsAreNotCompared() {
		Tower t = new Tower();
		t.rooms.add(new Room("extra", 2, "main", List.of(Box3.of(10, 11, 1, 1, 3, 1)), "flat"));
		t.box(3, 11, 4, 1, 2, 1, Role.DOOR, 2);
		assertPasses(t, HighriseChecks.FLOOR_REPEAT);
	}

	// hall-access

	@Test
	void everyUnitNeedsADoorOntoAHall() {
		Tower t = new Tower();
		t.dropOpenings(o -> o.id().equals("unit2"));
		assertFails(t, HighriseChecks.HALL_ACCESS, "no door onto a corridor");
		Tower u = new Tower();
		u.rooms.add(new Room("back", 1, "main", List.of(Box3.of(10, 7, 5, 1, 3, 1)), "office"));
		u.openings.add(new Opening("thru", OpeningKind.DOOR, Box3.of(10, 7, 4, 1, 2, 1), Facing.SOUTH, "flat1", "back"));
		assertFails(u, HighriseChecks.HALL_ACCESS, "1 unit room(s)");
	}

	@Test
	void corridorsAreTwoWideAndThreeOnBigPlates() {
		Tower t = new Tower();
		t.rooms(r -> r.id().equals("hall1") ? new Room("hall1", 1, "main", List.of(Box3.of(1, 7, 5, 10, 3, 1)), "corridor") : r);
		assertFails(t, HighriseChecks.HALL_ACCESS, "too narrow");
		Tower u = new Tower();
		u.volumes.set(0, new Volume("main", Box3.of(0, 0, 0, 34, 14, 34), null));
		assertFails(u, HighriseChecks.HALL_ACCESS, "needs 3");
	}

	// stairwell-enclosed

	@Test
	void everyStoreyWithFlightsNeedsOneStairwellRoom() {
		Tower t = new Tower();
		t.rooms(r -> r.id().equals("w2") ? new Room("w2", 2, "main", r.parts(), "stair") : r);
		assertFails(t, HighriseChecks.STAIRWELL, "storey 2 has flights but 0 rooms of use stairwell");
	}

	@Test
	void theStairwellHasExactlyOneDoorOntoAHall() {
		Tower t = new Tower();
		t.door("second", 9, 7, 7, Facing.SOUTH, "hall1", "w1");
		assertFails(t, HighriseChecks.STAIRWELL, "2 doors");
		Tower u = new Tower();
		u.openings.replaceAll(o -> o.id().equals("sdoor1") ? new Opening("sdoor1", o.kind(), o.box(), o.facing(), "flat1", o.to()) : o);
		assertFails(u, HighriseChecks.STAIRWELL, "not a hall");
	}

	@Test
	void theStairwellDoorStacks() {
		Tower t = new Tower();
		t.openings.replaceAll(o -> o.id().equals("sdoor2") ? new Opening("sdoor2", o.kind(), Box3.of(3, 11, 7, 1, 2, 1), o.facing(), o.from(), o.to()) : o);
		assertFails(t, HighriseChecks.STAIRWELL, "not at the same x,z");
	}

	@Test
	void theStairwellIsWalledOnEverySide() {
		Tower t = new Tower();
		t.box(5, 7, 7, 1, 2, 1, Role.AIR, 1);
		assertFails(t, HighriseChecks.STAIRWELL, "open cell(s)");
	}

	@Test
	void flightsStayInsideTheStairwell() {
		Tower t = new Tower();
		t.rooms(r -> r.id().equals("w1") ? new Room("w1", 1, "main", List.of(Box3.of(1, 7, 8, 4, 3, 2)), "stairwell") : r);
		assertFails(t, HighriseChecks.STAIRWELL, "leave the stairwell");
	}

	// shaft-present

	@Test
	void aShaftNeedsAirDoorwaysAndWalls() {
		Tower t = new Tower();
		t.boxes.removeIf(b -> b.role() == Role.AIR && b.box().sx() == 3 && b.box().sz() == 3);
		assertFails(t, HighriseChecks.SHAFT, "air column stops");
		Tower u = new Tower();
		u.boxes.removeIf(b -> b.role() == Role.WINDOW && b.storey() == 2);
		assertFails(u, HighriseChecks.SHAFT, "no window-role doorway");
		Tower w = new Tower();
		w.box(5, 7, 2, 1, 2, 1, Role.AIR, 1);
		assertFails(w, HighriseChecks.SHAFT, "shaft wall open");
	}

	@Test
	void theShaftDoorwayMustFaceAHall() {
		Tower t = new Tower();
		t.rooms(r -> r.id().equals("hall2") ? new Room("hall2", 2, "main", r.parts(), "flat") : r);
		assertFails(t, HighriseChecks.SHAFT, "does not face a corridor");
	}

	// new in the second plan: entries, bedrooms, windows, lights, plant room, pit light, rails

	@Test
	void entriesOpenOntoACorridorAndBedroomsAreNotReachedThroughBedrooms() {
		Tower t = new Tower();
		t.rooms.add(new Room("entry1", 1, "main", List.of(Box3.of(10, 7, 1, 1, 3, 1)), "entry"));
		assertFails(t, HighriseChecks.HALL_ACCESS, "entry room(s) have no door onto a corridor");
		Tower u = new Tower();
		u.rooms.add(new Room("b1", 1, "main", List.of(Box3.of(10, 7, 1, 1, 3, 1)), "bedroom"));
		u.rooms.add(new Room("b2", 1, "main", List.of(Box3.of(10, 7, 2, 1, 3, 1)), "bedroom"));
		u.openings.add(new Opening("bb", OpeningKind.DOOR, Box3.of(10, 7, 1, 1, 2, 1), Facing.SOUTH, "b1", "b2"));
		assertFails(u, HighriseChecks.HALL_ACCESS, "only through another bedroom");
	}

	@Test
	void windowedUsesNeedAWindowToTheOutside() {
		Tower t = new Tower();
		assertPasses(t, HighriseChecks.ROOM_WINDOW);
		t.dropOpenings(o -> o.id().equals("win-flat2"));
		assertFails(t, HighriseChecks.ROOM_WINDOW, "flat2 (flat)");
	}

	@Test
	void everyRoomHasALightAndBigRoomsAreCoveredByCeilingLights() {
		Tower t = new Tower();
		assertPasses(t, HighriseChecks.ROOM_LIGHT);
		t.hooks.removeIf(h -> h.room().equals("hall1"));
		assertFails(t, HighriseChecks.ROOM_LIGHT, "hall1 has no light");
		Tower u = new Tower();
		u.hooks.removeIf(h -> h.room().equals("lobby") && h.x() == 8);
		assertFails(u, HighriseChecks.ROOM_LIGHT, "lobby has floor more than 6");
	}

	@Test
	void theShaftHasAPitLightAndAPlantRoomAboveIt() {
		Tower t = new Tower();
		t.hooks.removeIf(h -> h.room().equals("shaft"));
		assertFails(t, HighriseChecks.SHAFT, "pit floor");
		Tower u = new Tower();
		u.rooms.removeIf(r -> r.use().equals("plant"));
		assertFails(u, HighriseChecks.SHAFT, "no plant room");
	}

	@Test
	void theRoofEdgeNeedsAWallWithARailOnIt() {
		Tower t = new Tower();
		t.boxes.removeIf(b -> b.role() == Role.RAIL);
		assertFails(t, HighriseChecks.ROOF, "lack a wall with a rail above it");
		Tower u = new Tower();
		u.box(4, 16, 0, 1, 1, 1, Role.AIR, -1); // one gap
		assertFails(u, HighriseChecks.ROOF, "1 edge column(s)");
	}

	// roof-reachable

	@Test
	void theRoofMustBeReachableFromTheStreet() {
		Tower t = new Tower();
		t.dropOpenings(o -> o.id().equals("roofdoor"));
		t.box(5, 15, 7, 1, 2, 1, Role.WALL, 3);
		assertFails(t, HighriseChecks.ROOF, "reachable on foot");
	}

	@Test
	void aRoofDoorAloneIsNotAWayUp() {
		Tower t = new Tower();
		t.boxes.removeIf(b -> b.role() == Role.STAIR && b.storey() == 1); // the flight 1 -> 2 is gone, the roof door is still there
		assertFails(t, HighriseChecks.ROOF, "reachable on foot");
	}

	@Test
	void pitchedTopRoofsAreNotChecked() {
		Tower t = new Tower();
		t.roofs.replaceAll(r -> new Roof("gable", r.volume(), r.ridgeAxis(), r.baseY(), 1, 1));
		t.dropOpenings(o -> o.id().equals("roofdoor"));
		assertPasses(t, HighriseChecks.ROOF);
	}
}
