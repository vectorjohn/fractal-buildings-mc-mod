package dev.fractalbuildings.core.gen.v1.types.temple;

import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.rand.Draws;
import java.util.List;

/** Room uses and hooks of a temple: nave with benches, sanctuary (the apse) with the altar, porch, side chapels, tower rooms. */
final class TempleRooms {
	private TempleRooms() {}

	static final RoomProgram PROGRAM = new RoomProgram() {
		@Override
		public String use(Role role, int area, Draws d) { return RoomProgram.DEFAULT.use(role, area, d); }

		@Override
		public String use(Role role, String massKind, int area, Where where, Draws d) {
			return switch (massKind) {
				case TempleMassing.NAVE -> "nave";
				case TempleMassing.APSE -> "sanctuary";
				case TempleMassing.PORCH -> "porch";
				case TempleMassing.TRANSEPT -> "chapel";
				case TempleMassing.TOWER -> role == Role.ENTRY ? "vestibule" : role == Role.ROOM ? "belfry" : use(role, area, d);
				default -> use(role, area, d);
			};
		}

		@Override
		public List<String> hooks(String use, int area, Draws d) {
			int n = Math.min(6, 1 + area / 12);
			return switch (use) {
				case "nave" -> List.of("light", "bench", "bench", "bench", "light", "bench").subList(0, n);
				case "sanctuary" -> List.of("altar", "light", "light", "chest").subList(0, Math.min(4, 1 + area / 10));
				case "chapel" -> List.of("bench", "light").subList(0, Math.min(2, 1 + area / 20));
				case "porch" -> area >= 12 ? List.of("light") : List.of();
				case "vestibule", "belfry" -> List.of("light");
				default -> RoomProgram.DEFAULT.hooks(use, area, d);
			};
		}
	};
}
