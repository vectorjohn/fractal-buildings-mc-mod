package dev.fractalbuildings.core.gen.v1.types.hall;

import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * Room program of the hall type: the open masses are single "hall" rooms furnished with rows of
 * stalls or benches ({@code table}), {@code chest}s along the walls and {@code light}s, in numbers that
 * grow with the floor area; the small tower and annex floors use the default rooms.
 */
final class HallRooms {
	private HallRooms() {}

	static final RoomProgram PROGRAM = new RoomProgram() {
		@Override
		public String use(Role role, int area, Draws d) { return RoomProgram.DEFAULT.use(role, area, d); }

		@Override
		public List<String> hooks(String use, int area, Draws d) {
			if (!use.equals("hall")) return RoomProgram.DEFAULT.hooks(use, area, d);
			int n = Math.min(12, 3 + area / 50);
			List<String> out = new ArrayList<>();
			String[] kinds = {"table", "light", "table", "chest"};
			for (int i = 0; i < n; i++) out.add(kinds[i % kinds.length]);
			return out;
		}
	};
}
