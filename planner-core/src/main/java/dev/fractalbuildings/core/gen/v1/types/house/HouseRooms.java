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
