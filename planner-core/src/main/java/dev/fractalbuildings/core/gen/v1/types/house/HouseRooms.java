package dev.fractalbuildings.core.gen.v1.types.house;

import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram.Role;
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

	/** Ground-floor rooms (not counting the entry hall) from which a second kitchen is allowed. */
	static final int BIG_HOUSE_ROOMS = 5;

	private static boolean lives(String use) { return use.equals("bedroom") || use.equals("living") || use.equals("kitchen"); }

	/**
	 * The whole-house pass, after every mass has its own rooms: a house of two or more rooms has one
	 * kitchen (on the ground floor, by the living room or the entry, with the hearth) and one living room
	 * before further bedrooms. A second kitchen or living room survives {@link #REPEAT_PERCENT} of the time
	 * (a kitchen only in a house of {@link #BIG_HOUSE_ROOMS}+ ground rooms). A one-room house stays a single
	 * bedroom (a hut, "hall house"), and a ground floor of one room is a kitchen-hall that serves as the living room.
	 */
	@Override
	public void refineBuilding(List<Slot> rooms, Where w, Draws d) {
		List<Slot> ground = new ArrayList<>();
		int habitable = 0;
		for (Slot s : rooms) {
			if (lives(s.use)) habitable += s.span;
			if (s.storey == 0 && lives(s.use)) ground.add(s);
		}
		if (habitable < 2) return;
		List<Slot> pool = ground;
		if (ground.isEmpty()) { // a ground floor of only entries and halls: the kitchen goes in the entry room
			pool = new ArrayList<>();
			for (Slot s : rooms) if (s.storey == 0 && (s.role == Role.ENTRY || s.role == Role.THROUGH) && (s.use.equals("entry") || s.use.equals("hall"))) pool.add(s);
			if (pool.isEmpty()) return;
		}
		Slot kitchen = best(pool, "kitchen", null, w);
		if (kitchen == null) return;
		boolean spareKitchen = ground.size() >= BIG_HOUSE_ROOMS && d.chance(REPEAT_PERCENT, 100);
		for (Slot s : ground) {
			if (s == kitchen || !s.use.equals("kitchen") || s.role != Role.ROOM) continue;
			if (spareKitchen) { spareKitchen = false; continue; }
			s.use = "bedroom";
		}
		kitchen.use = "kitchen";
		Slot living = null;
		for (Slot s : ground) if (s.use.equals("living") && (living == null || rank(s) > rank(living))) living = s;
		if (living == null && ground.size() >= 2) {
			living = best(ground, "living", kitchen, w);
			if (living != null) living.use = "living";
		}
		for (Slot s : ground) {
			if (s == living || !s.use.equals("living") || s.role != Role.ROOM) continue;
			if (d.chance(REPEAT_PERCENT, 100)) continue;
			s.use = "bedroom";
		}
		if (w.storeys() == 1) { // a one-storey house sleeps on its ground floor
			boolean bed = false;
			for (Slot s : ground) bed |= s.use.equals("bedroom");
			if (!bed && ground.size() >= 3) {
				Slot spare = null;
				for (Slot s : ground) if (s.role == Role.ROOM && s != kitchen && s != living && (spare == null || s.area < spare.area)) spare = s;
				if (spare != null) spare.use = "bedroom";
			}
		}
	}

	private static int rank(Slot s) { return (s.role == Role.ENTRY ? 1000 : 0) + s.area; }

	/** The room of {@code pool} best suited to the use (a kitchen beside the living room or entry; a living room beside the kitchen); null if none. */
	private static Slot best(List<Slot> pool, String use, Slot kitchen, Where w) {
		Slot pick = null;
		int bestScore = Integer.MIN_VALUE;
		int bedrooms = 0;
		for (Slot s : pool) if (s.use.equals("bedroom")) bedrooms++;
		for (Slot s : pool) {
			if (kitchen != null && s == kitchen) continue;
			if (use.equals("living") && s.role != Role.ROOM && s.role != Role.ENTRY) continue;
			int score = Math.min(s.area, 20);
			if (s.use.equals(use)) score += 50; // already is one
			if (s.use.equals("kitchen") || s.use.equals("living")) score += 8; // a repeated type is the cheapest to change
			if (s.role == Role.ROOM) score += 6;
			if (s.area < 9) score -= 12;
			for (Slot n : s.next()) {
				if (use.equals("kitchen") && (n.use.equals("living") || n.role == Role.ENTRY)) score += 12;
				if (use.equals("living") && (n == kitchen || n.role == Role.ENTRY)) score += 12;
			}
			if (w.storeys() == 1 && bedrooms == 1 && s.use.equals("bedroom") && pool.size() >= 3) score -= 40; // keep the only bedroom
			if (score > bestScore) { bestScore = score; pick = s; }
		}
		return pick;
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
