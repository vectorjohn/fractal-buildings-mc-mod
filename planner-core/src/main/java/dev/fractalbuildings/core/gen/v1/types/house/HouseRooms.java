package dev.fractalbuildings.core.gen.v1.types.house;

import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * The house's room program: the ground floor is for living (the room behind the door is the living
 * room when it is big enough, otherwise an entry; the rest are kitchens and living rooms), the upper
 * floors are for sleeping. Hooks: bed and chest in bedrooms, table and hearth in living rooms,
 * hearth and table in kitchens. A one-storey house sleeps on its ground floor.
 */
final class HouseRooms implements RoomProgram {
	static final int LIVING_MIN_AREA = 16, STAIR_ROOM_MAX_AREA = 24;

	@Override
	public String use(Role role, int area, Draws d) { return RoomProgram.DEFAULT.use(role, area, d); }

	@Override
	public List<String> hooks(String use, int area, Draws d) { return hooks(use, area, new Where(0, 1), d); }

	@Override
	public String use(Role role, int area, Where w, Draws d) {
		boolean ground = w.storey() == 0;
		return switch (role) {
			case CORRIDOR -> "corridor";
			case STAIR -> area >= STAIR_ROOM_MAX_AREA ? (ground ? "living" : "bedroom") : "stair"; // a stair room with space beside the core is a room too
			case HALL -> "hall";
			case THROUGH -> !ground ? "landing" : area > STAIR_ROOM_MAX_AREA ? "living" : "hall"; // the way into another wing is no bedroom
			case ENTRY -> ground && area >= LIVING_MIN_AREA ? "living" : ground ? "entry" : "landing";
			case ROOM -> {
				if (!ground) yield "bedroom";
				if (w.storeys() == 1 && d.chance(1, 2)) yield "bedroom";
				yield d.chance(1, 2) ? "kitchen" : "living";
			}
		};
	}

	/** Chance in 100 that a spare room repeats a kitchen or living room instead of being another bedroom: the odd house. */
	static final int REPEAT_PERCENT = 15;

	/**
	 * Spare rooms of a floor: first the missing kitchen (and living room, if the entry did not become one),
	 * then bedrooms, so a house with room to spare gets a second and third bedroom before a second
	 * living room. A one-storey house sleeps on its ground floor, so it takes a bedroom first. A seeded
	 * few rooms repeat a type anyway.
	 */
	@Override
	public void refine(Role[] roles, int[] areas, String[] uses, Where w, Draws d) {
		if (w.storey() != 0) return;
		if (roles.length == 1 && roles[0] == Role.ENTRY && w.storeys() == 1 && areas[0] < LIVING_MIN_AREA) { uses[0] = "bedroom"; return; } // a hut is one bedroom with a door (CIRC2)
		List<Integer> spare = new ArrayList<>();
		boolean kitchen = false, living = false;
		for (int i = 0; i < roles.length; i++) {
			if (roles[i] == Role.ROOM) spare.add(i);
			else if (uses[i].equals("kitchen")) kitchen = true;
			else if (uses[i].equals("living")) living = true;
		}
		for (int i = spare.size() - 1; i > 0; i--) java.util.Collections.swap(spare, i, d.below(i + 1)); // which room gets which use is seeded
		List<String> order = new ArrayList<>();
		if (w.storeys() == 1 && (spare.size() > 1 || d.chance(80, 100))) order.add("bedroom"); // a lone wing room is a bedroom 80 % of the time, so a house of wings still gets a kitchen
		if (!kitchen) order.add("kitchen");
		if (!living) order.add("living");
		for (int k = 0; k < spare.size(); k++) {
			String u = k < order.size() ? order.get(k) : "bedroom";
			if (k >= order.size() && d.chance(REPEAT_PERCENT, 100)) u = d.chance(1, 2) ? "kitchen" : "living";
			uses[spare.get(k)] = u;
		}
	}

	@Override
	public List<String> hooks(String use, int area, Where w, Draws d) {
		List<String> h = new ArrayList<>();
		switch (use) {
			case "bedroom" -> {
				h.add("bed");
				h.add("chest");
				if (area >= 24) h.add("bed");
				if (area >= 16) h.add("table");
			}
			case "living" -> {
				h.add("table");
				h.add("hearth");
				h.add("chest");
				if (area >= 24) h.add("table");
			}
			case "kitchen" -> {
				h.add("hearth");
				h.add("table");
				h.add("chest");
			}
			case "entry", "landing" -> h.add("chest");
			case "stair" -> h.add("light");
			case "corridor" -> {
				if (area >= 8) h.add("light");
			}
			case "hall" -> {
				h.add("table");
				h.add("hearth");
			}
			default -> {}
		}
		return h;
	}

	/** Ground floor and upper floors are different programs, so they never share a floor plan. */
	@Override
	public boolean newBand(int storey) { return storey == 1; }
}
