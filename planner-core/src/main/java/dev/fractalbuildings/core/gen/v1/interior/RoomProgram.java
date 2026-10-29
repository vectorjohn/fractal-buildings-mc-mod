package dev.fractalbuildings.core.gen.v1.interior;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.rand.Draws;
import java.util.List;

/**
 * What the rooms of a building are for: the use tag of each room and the furnishing hooks in it. A
 * type supplies its own through {@link dev.fractalbuildings.core.gen.v1.BuildingType#rooms()} (a shop
 * puts a counter in its front room, a temple an altar in its hall). The geometry (where rooms and
 * partitions are) does not depend on the program, so a program never changes reachability.
 */
public interface RoomProgram {
	/** Where a room sits in its floor plan. */
	enum Role { ENTRY, CORRIDOR, STAIR, HALL, ROOM, /** the room that opens onto another mass of the building: a passage, not someone's room */ THROUGH }

	/** The room's {@code use} tag. */
	String use(Role role, int area, Draws d);

	/** Hook kinds to place in a room of this use and area, in order of importance (at most as many as fit). */
	List<String> hooks(String use, int area, Draws d);

	/** Where a floor is in the building: its storey and how many storeys the building has. */
	record Where(int storey, int storeys) {}

	/** As {@link #use(Role, int, Draws)}, for a program that cares which storey the room is on. */
	default String use(Role role, int area, Where where, Draws d) { return use(role, area, d); }

	/** As the {@code Where} overload, knowing the kind of the mass the room is in (a temple's nave versus its apse). */
	default String use(Role role, String massKind, int area, Where where, Draws d) { return use(role, area, where, d); }

	/** As {@link #hooks(String, int, Draws)}, with the room's place in the building. */
	default List<String> hooks(String use, int area, Where where, Draws d) { return hooks(use, area, d); }

	/**
	 * A chance to revise the uses of a whole floor of one mass once each room has its own (a house gives the
	 * spare rooms of a floor to bedrooms before it repeats a kitchen or living room). Arrays are indexed by
	 * room; change {@code uses} in place. Default: no change.
	 */
	default void refine(Role[] roles, int[] areas, String[] uses, Where where, Draws d) {}

	/** A room of the whole building as the building-wide program sees it; {@code use} may be changed. {@code storey} is the first storey of its band and {@code span} how many storeys share it. */
	final class Slot {
		public final int mass, storey, area, span;
		public final Role role;
		public String use;
		/** A back or side door (DS-35) opens into this room. */
		public boolean back;
		/** The room sleeps someone though it is not a bedroom (a one-room wing that is the kitchen-hall): it gets a bed hook. */
		public boolean needsBed;
		private final List<Slot> next = new java.util.ArrayList<>();

		public Slot(int mass, int storey, int span, Role role, int area, String use) {
			this.mass = mass; this.storey = storey; this.span = span; this.role = role; this.area = area; this.use = use;
		}

		/** Rooms joined to this one by a door of the same floor plan. */
		public List<Slot> next() { return next; }
	}

	/**
	 * A chance to revise the uses of every room of the building at once, after each floor plan has its own
	 * (a house gives itself one kitchen and one living room across its wings). Default: no change.
	 */
	/** World cells {x, z} on storey {@code s} of mass {@code a} that a door cut after the interior pass will use (a wall-walk doorway): the floor plan and the furniture keep clear of them. */
	default List<int[]> reserved(BuildContext c, Mass a, int s) { return List.of(); }

	default void refineBuilding(List<Slot> rooms, Where where, Draws d) {}

	/** The use of a room a back or side door opens into (DS-35: a hall, kitchen, lobby or shop floor, never a bedroom): the program's {@code use} unless it is a bedroom or a plain room, which become a hall. */
	default String backUse(String use, Role role) { return use.equals("bedroom") || use.equals("room") ? "hall" : use; }

	/** True if a floor plan must not be shared between storey {@code storey - 1} and {@code storey} of a mass (a type whose ground floor differs in use from the upper ones). */
	default boolean newBand(int storey) { return false; }

	/** True if rooms of this use may be joined by an archway or open plan instead of a door (public rooms, never a bedroom). */
	default boolean openPlan(String use) {
		return switch (use) {
			case "entry", "hall", "living", "kitchen", "landing", "dining", "porch" -> true;
			default -> false;
		};
	}

	/** Uses {@code entry, corridor, stair, hall, room}; a bed, table, chest and workstation per room. */
	RoomProgram DEFAULT = new RoomProgram() {
		@Override
		public String use(Role role, int area, Draws d) {
			return switch (role) {
				case ENTRY -> "entry";
				case CORRIDOR -> "corridor";
				case STAIR -> "stair";
				case HALL, THROUGH -> "hall";
				case ROOM -> "room";
			};
		}

		@Override
		public List<String> hooks(String use, int area, Draws d) {
			int n = Math.min(4, 1 + area / 10);
			return switch (use) {
				case "corridor" -> area >= 8 ? List.of("light") : List.of();
				case "stair" -> List.of("light");
				case "entry" -> List.of("chest", "table").subList(0, Math.min(n, 2));
				case "hall" -> List.of("table", "hearth", "chest", "light").subList(0, n);
				default -> List.of("bed", "table", "chest", "workstation").subList(0, n);
			};
		}
	};
}
