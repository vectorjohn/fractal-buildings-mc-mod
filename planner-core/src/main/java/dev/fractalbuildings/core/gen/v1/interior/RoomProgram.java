package dev.fractalbuildings.core.gen.v1.interior;

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
	enum Role { ENTRY, CORRIDOR, STAIR, HALL, ROOM }

	/** The room's {@code use} tag. */
	String use(Role role, int area, Draws d);

	/** Hook kinds to place in a room of this use and area, in order of importance (at most as many as fit). */
	List<String> hooks(String use, int area, Draws d);

	/** Uses {@code entry, corridor, stair, hall, room}; a bed, table, chest and workstation per room. */
	RoomProgram DEFAULT = new RoomProgram() {
		@Override
		public String use(Role role, int area, Draws d) {
			return switch (role) {
				case ENTRY -> "entry";
				case CORRIDOR -> "corridor";
				case STAIR -> "stair";
				case HALL -> "hall";
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
