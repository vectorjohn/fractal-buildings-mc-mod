package dev.fractalbuildings.core.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FloorPlanTextTest {
	private static void box(List<Box> l, Role r, int x, int y, int z, int sx, int sy, int sz) {
		l.add(new Box(Box3.of(x, y, z, sx, sy, sz), r, Layer.SHELL, 0));
	}

	/** Two storeys, 8 x 6: ground floor rooms r0 (left) and r1 (right) with a door, an entrance, a window and a stair up to r2. */
	private static BuildingIr sample(boolean isolatedRoom) {
		List<Box> b = new ArrayList<>();
		box(b, Role.FLOOR, 0, 0, 0, 8, 1, 6);
		box(b, Role.WALL, 0, 1, 0, 8, 3, 1);
		box(b, Role.WALL, 0, 1, 5, 8, 3, 1);
		box(b, Role.WALL, 0, 1, 1, 1, 3, 4);
		box(b, Role.WALL, 7, 1, 1, 1, 3, 4);
		box(b, Role.WALL, 4, 1, 1, 1, 3, 4);
		box(b, Role.FLOOR, 0, 4, 0, 8, 1, 6);
		box(b, Role.AIR, 1, 4, 4, 3, 1, 1);
		box(b, Role.STAIR, 3, 4, 4, 1, 1, 1);
		List<Room> rooms = new ArrayList<>(List.of(
				new Room("r0", 0, "main", List.of(Box3.of(1, 1, 1, 3, 3, 4)), "hall"),
				new Room("r1", 0, "main", List.of(Box3.of(5, 1, 1, 2, 3, 4)), "bedroom"),
				new Room("r2", 1, "main", List.of(Box3.of(1, 5, 1, 6, 3, 4)), "loft")));
		List<Opening> ops = new ArrayList<>(List.of(
				new Opening("o0", OpeningKind.DOOR, Box3.of(4, 1, 2, 1, 2, 1), Facing.EAST, "r0", "r1"),
				new Opening("o1", OpeningKind.DOOR, Box3.of(2, 1, 0, 1, 2, 1), Facing.NORTH, "r0", Opening.OUTSIDE),
				new Opening("o2", OpeningKind.WINDOW, Box3.of(5, 2, 0, 1, 1, 1), Facing.NORTH, "r1", Opening.OUTSIDE),
				new Opening("o3", OpeningKind.STAIR, Box3.of(1, 1, 4, 3, 4, 1), Facing.EAST, "r0", "r2")));
		if (isolatedRoom) rooms.add(new Room("r3", 1, "main", List.of(Box3.of(1, 5, 1, 1, 1, 1)), "closet"));
		if (isolatedRoom) ops.remove(3);
		List<Hook> hooks = List.of(new Hook("h0", "bed", "r1", 5, 1, 3, Facing.SOUTH), new Hook("h1", "light", "r1", 5, 3, 3, Facing.SOUTH));
		BuildingSpec spec = new BuildingSpec(2, 1L, "plain", "house", 8, 6, 2, "gable", 100, 1);
		return new BuildingIr(spec, Box3.of(0, 0, 0, 8, 9, 6), List.of(new Storey(0, 0, 4), new Storey(1, 4, 4)), List.of(new Volume("main", Box3.of(0, 0, 0, 8, 8, 6), null)),
				rooms, ops, List.of(), b, hooks);
	}

	/** The grid rows of storey {@code s} (the text after the '|' of each row). */
	private static List<String> rows(String text, int s) {
		String[] lines = text.split("\n");
		List<String> out = new ArrayList<>();
		boolean in = false;
		for (String l : lines) {
			if (l.startsWith("## ")) in = l.startsWith("## Storey " + s + ":");
			else if (in && l.matches("\\s*\\d+ \\|.*")) out.add(l.substring(l.indexOf('|') + 1));
		}
		return out;
	}

	@Test
	void groundFloorGlyphs() {
		List<String> r = rows(FloorPlanText.render(sample(false), false), 0);
		assertEquals(6, r.size());
		assertEquals('%', r.get(0).charAt(2), "entrance door");
		assertEquals('~', r.get(0).charAt(5), "window");
		assertEquals('/', r.get(2).charAt(4), "interior door in the partition");
		assertEquals('+', r.get(1).charAt(4), "partition");
		assertEquals('#', r.get(1).charAt(0), "exterior wall");
		assertEquals(">>>", r.get(4).substring(1, 4), "stair rising east");
		assertEquals('b', r.get(3).charAt(5), "bed");
		assertEquals('A', r.get(2).charAt(2), "room label of r0 at its centre");
	}

	@Test
	void upperFloorShowsStairwellAndLanding() {
		List<String> r = rows(FloorPlanText.render(sample(false), false), 1);
		assertEquals("OO@", r.get(4).substring(1, 4));
	}

	@Test
	void roomTableHasAreaUseAndConnections() {
		String t = FloorPlanText.render(sample(false), false);
		String r0 = line(t, "r0 ");
		assertTrue(r0.contains("hall") && r0.contains(" 12 "), r0);
		assertTrue(r0.contains("r1 (door o0, leave heading east)"), r0);
		assertTrue(r0.contains("outside (door o1"), r0);
		assertTrue(r0.contains("r2@storey1 (stair o3"), r0);
		String r2 = line(t, "r2 ");
		assertTrue(r2.contains("r0@storey0 (stair o3, enter heading east)"), r2);
	}

	@Test
	void windowSillAndHead() {
		String t = FloorPlanText.render(sample(false), false);
		assertTrue(line(t, "o2 ").contains("sill +1 head +2"), line(t, "o2 "));
		assertTrue(line(t, "o0 ").contains("0..2 above floor surface"));
	}

	@Test
	void circulationFlagsUnreachableRooms() {
		assertTrue(FloorPlanText.render(sample(false), false).contains("not reachable from outside through doors, arches and stairs: none"));
		String t = FloorPlanText.render(sample(true), false);
		assertTrue(t.contains("stairs: r2 (loft, storey 1), r3 (closet, storey 1)"), t);
	}

	@Test
	void furnitureLettersAreUniqueAndAvoidReserved() {
		List<Hook> hooks = new ArrayList<>();
		int i = 0;
		for (String k : List.of("bed", "bench", "bookshelf", "vault", "light", "table"))
			hooks.add(new Hook("h" + i++, k, "r", 0, 0, 0, Facing.NORTH));
		Map<String, Character> m = FloorPlanText.furnitureLetters(hooks);
		assertEquals('b', m.get("bed"));
		assertEquals('e', m.get("bench"));
		assertEquals('o', m.get("bookshelf"));
		assertEquals('a', m.get("vault"), "v is reserved for the stair arrow");
		assertEquals('l', m.get("light"));
		assertEquals(m.size(), new java.util.HashSet<>(m.values()).size());
	}

	@Test
	void wideDoublesWidthAndOutputIsAscii() {
		String narrow = FloorPlanText.render(sample(false), false), wide = FloorPlanText.render(sample(false), true);
		assertEquals(rows(narrow, 0).get(0).length() * 2, rows(wide, 0).get(0).length());
		for (char c : wide.toCharArray()) assertTrue(c == '\n' || c >= 32 && c < 127, "non-ascii or control char " + (int) c);
		assertFalse(wide.contains("\t"));
	}

	@Test
	void deterministic() {
		assertEquals(FloorPlanText.render(sample(false), false), FloorPlanText.render(sample(false), false));
	}

	private static String line(String text, String prefixAfterChar) {
		for (String l : text.split("\n")) {
			String t = l.length() > 5 ? l.substring(5) : l;
			if (l.startsWith(prefixAfterChar) || t.startsWith(prefixAfterChar)) return l;
		}
		throw new AssertionError("no line starting with '" + prefixAfterChar + "'");
	}
}
