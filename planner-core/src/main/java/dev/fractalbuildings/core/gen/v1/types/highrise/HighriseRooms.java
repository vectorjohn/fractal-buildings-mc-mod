package dev.fractalbuildings.core.gen.v1.types.highrise;

import dev.fractalbuildings.core.gen.v1.interior.InteriorPass;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;

/**
 * Room uses and furnishing hooks of a high-rise (plan docs/plans/highrises.md, B3a). The uses of a planned floor come
 * from {@link TowerPlate}; {@link #use} only names the rooms of a floor the generic planner had to draw (a plate that
 * did not fit). No workstations anywhere; a flat's hearth is a stove (the mod places a furnace), never a fireplace
 * (DS-15). Every room gets a wall {@code light} first (so it is never dark when the wall candidates run out), and the
 * grand and big rooms a {@code ceiling_light} grid ({@link InteriorPass#CEILING_LIGHT}, DS-24).
 */
public final class HighriseRooms implements RoomProgram {
	/** Rooms of this area or more get ceiling lights (w + d >= 15 needs them; a room with w + d >= 15 and aspect <= 2 or thin has area 20 or more at least). */
	static final int BIG_AREA = 20;

	@Override
	public String use(Role role, int area, Draws d) {
		return switch (role) {
			case ENTRY -> "lobby";
			case CORRIDOR, THROUGH -> "corridor";
			case HALL -> "open";
			case STAIR -> "stairwell";
			case ROOM -> area >= 16 ? "office" : "storage";
		};
	}

	@Override
	public List<String> hooks(String use, int area, Draws d) {
		List<String> out = new ArrayList<>();
		switch (use) {
			case "stairwell" -> { /* TowerShaft hangs its ceiling light at the door cell */ }
			case "corridor" -> out.add(InteriorPass.CEILING_LIGHT);
			case "plant" -> { out.add("light"); out.add(InteriorPass.CEILING_LIGHT); }
			case "lobby" -> {
				out.add("light");
				add(out, "counter", 2);
				add(out, "bench", 4);
				out.add("table");
				out.add("light");
				out.add(InteriorPass.CEILING_LIGHT);
			}
			case "banquet" -> {
				out.add("light");
				add(out, "table", 6);
				add(out, "bench", 6);
				add(out, "counter", 2);
				out.add(InteriorPass.CEILING_LIGHT);
			}
			case "open" -> {
				out.add("light");
				add(out, "table", 4);
				add(out, "bench", 4);
				out.add("chest");
				out.add(InteriorPass.CEILING_LIGHT);
			}
			case "office" -> {
				int n = Math.max(1, Math.min(4, area / 12));
				out.add("light");
				add(out, "table", n);
				add(out, "bench", n);
				out.add("chest");
				if (area >= BIG_AREA) out.add(InteriorPass.CEILING_LIGHT);
			}
			case "flat" -> {
				out.add("light");
				out.addAll(List.of("hearth", "counter", "table", "bench", "bench", "chest"));
				if (area >= BIG_AREA) out.add(InteriorPass.CEILING_LIGHT);
			}
			case "bedroom" -> {
				out.add("light");
				out.addAll(List.of("bed", "chest"));
				if (area >= BIG_AREA) out.add(InteriorPass.CEILING_LIGHT);
			}
			case "kitchen" -> {
				out.add("light");
				out.addAll(List.of("hearth", "counter", "counter", "chest"));
				if (area >= BIG_AREA) out.add(InteriorPass.CEILING_LIGHT);
			}
			case "storage" -> {
				out.add("light");
				out.addAll(List.of("chest", "chest", "counter"));
				if (area >= BIG_AREA) out.add(InteriorPass.CEILING_LIGHT);
			}
			default -> { // entry and anything else
				out.add("light");
				out.add("chest");
				if (area >= BIG_AREA) out.add(InteriorPass.CEILING_LIGHT);
			}
		}
		// small rooms may have no wall cell clear of doors for a light; a hung one needs none
		if (!use.equals("stairwell") && !out.contains(InteriorPass.CEILING_LIGHT)) out.add(InteriorPass.CEILING_LIGHT);
		return out;
	}

	private static void add(List<String> out, String kind, int n) {
		for (int i = 0; i < n; i++) out.add(kind);
	}

	/** Public rooms of a tower are walled rooms joined by doors and the template's own archways; nothing is opened up later. */
	@Override
	public boolean openPlan(String use) { return false; }
}
