package dev.fractalbuildings.core.gen.v1.types.highrise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.ShellGeneratorV1;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.Storey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * B3b: the high-rise facade by programme (windows stack within a tier, taller special-floor windows), the stair
 * house's roof doors and the roof guard, sill-0 windows above a setback deck, mass timber's bay-line columns.
 */
class HighriseFacadeTest {
	static final int[] LOTS = {20, 28, 36, 44};
	static final String[] STYLES = {"tall-brick", "tall-concrete", "tall-stone", "tall-terracotta"};

	static BuildingIr tower(long seed, String style, int lot) {
		return ShellGeneratorV1.generate(new BuildingSpec(GeneratorV1.GEN_VERSION, seed, style, HighriseType.ID, lot, lot, 10 + (int) (seed * 7 % 40), "auto", 320, 1));
	}

	interface Each { void at(String why, BuildingIr b); }

	static void sweep(Each each) {
		for (int lot : LOTS)
			for (long seed = 1; seed <= 12; seed++) {
				String style = STYLES[(int) (seed % STYLES.length)];
				BuildingIr b = tower(seed, style, lot);
				if (b.storeys().size() < 8 || b.rooms().isEmpty()) continue;
				each.at(style + " seed " + seed + " lot " + lot, b);
			}
	}

	static Room roomOf(BuildingIr b, String id) {
		for (Room r : b.rooms()) if (r.id().equals(id)) return r;
		return null;
	}

	static boolean isWindow(Opening o) { return o.kind() == OpeningKind.WINDOW && (o.from().equals(Opening.OUTSIDE) || o.to().equals(Opening.OUTSIDE)); }

	/** The room on the building side of an exterior opening. */
	static Room inside(BuildingIr b, Opening o) { return roomOf(b, o.from().equals(Opening.OUTSIDE) ? o.to() : o.from()); }

	/** DS-25: every storey of a tier (typical, special and its first above a setback) has windows in the same columns on each side. */
	@Test
	void windowsOfATierStackInTheSameColumns() {
		int[] checked = {0};
		sweep((why, b) -> {
			Map<String, Map<Integer, TreeSet<Integer>>> cols = new TreeMap<>(); // volume/facing -> storey -> columns along the face
			for (Opening o : b.openings()) {
				if (!isWindow(o)) continue;
				Room r = inside(b, o);
				if (r.storey() == 0 || r.use().equals("plant")) continue; // the lobby has its own bays
				int col = o.facing() == Facing.NORTH || o.facing() == Facing.SOUTH ? o.box().x() : o.box().z();
				cols.computeIfAbsent(r.volume() + "/" + o.facing(), k -> new TreeMap<>()).computeIfAbsent(r.storey(), k -> new TreeSet<>()).add(col);
			}
			for (var e : cols.entrySet()) {
				TreeSet<Integer> first = null;
				for (var s : e.getValue().entrySet()) {
					if (first == null) { first = s.getValue(); continue; }
					assertEquals(first, s.getValue(), why + " " + e.getKey() + " storey " + s.getKey());
					checked[0]++;
				}
			}
		});
		assertTrue(checked[0] > 1000, "storey pairs compared " + checked[0]);
	}

	/** Lobby and special floors keep the tier's columns but their windows are taller than a typical floor's. */
	@Test
	void specialFloorWindowsAreTallerThanTypicalOnes() {
		int[] special = {0}, lobby = {0};
		sweep((why, b) -> {
			Map<Integer, Storey> storeys = new TreeMap<>();
			for (Storey s : b.storeys()) storeys.put(s.index(), s);
			int typical = 0;
			List<Integer> others = new ArrayList<>();
			for (Opening o : b.openings()) {
				if (!isWindow(o)) continue;
				Room r = inside(b, o);
				Storey s = storeys.get(r.storey());
				if (r.use().equals("plant") || r.storey() >= b.storeys().size() - 1) continue;
				if (s.height() == TowerProgram.TYPICAL_H) typical = Math.max(typical, o.box().sy());
				else others.add(o.box().sy() * 1000 + r.storey() * 10 + (o.box().y() - s.y() - 1));
			}
			assertTrue(typical > 0, why);
			for (int h : others) assertTrue(h / 1000 > typical, why + ": a 6-high storey window (tall/storey/sill code " + h + "), typical " + typical);
			special[0] += others.size();
		});
		assertTrue(special[0] > 100, "taller windows seen " + special[0]);
	}

	/** The stair house opens onto the roof by two doors, one into the stairwell and one into the machine room; the roof has its rail. */
	@Test
	void stairHouseHasTwoRoofDoorsAndTheRoofHasItsRail() {
		sweep((why, b) -> {
			int top = b.storeys().size() - 1;
			List<Opening> doors = new ArrayList<>();
			for (Opening o : b.openings()) {
				if (o.kind() != OpeningKind.DOOR || !(o.from().equals(Opening.OUTSIDE) || o.to().equals(Opening.OUTSIDE))) continue;
				if (inside(b, o).storey() == top) doors.add(o);
			}
			assertEquals(2, doors.size(), why);
			TreeSet<String> uses = new TreeSet<>();
			for (Opening d : doors) uses.add(inside(b, d).use());
			assertEquals(new TreeSet<>(List.of("plant", "stairwell")), uses, why);
			int rails = 0;
			for (Box x : b.boxes()) if (x.role() == Role.RAIL && x.layer() == dev.fractalbuildings.core.ir.Layer.ROOF) rails++;
			assertTrue(rails > 20, why + ": rail boxes " + rails);
			// no door between the stairwell and the machine room any more
			for (Opening o : b.openings())
				if (o.kind() == OpeningKind.DOOR && !isWindow(o) && inside(b, o) != null && roomOf(b, o.to()) != null && roomOf(b, o.from()) != null && roomOf(b, o.to()).storey() == top)
					assertFalse(roomOf(b, o.to()).use().equals("plant") || roomOf(b, o.from()).use().equals("plant"), why + ": door to the machine room from inside");
		});
	}

	/** SETBACKSILL: the first storey above a setback deck has sill-0 windows (the deck block outside no longer blocks them). */
	@Test
	void firstStoreyAboveASetbackHasFloorToCeilingWindows() {
		int[] towers = {0};
		sweep((why, b) -> {
			TreeSet<Integer> firstStoreys = new TreeSet<>();
			for (Room r : b.rooms()) if (r.storey() > 0) firstStoreys.add(r.storey());
			Map<String, Integer> firstOf = new TreeMap<>();
			for (Room r : b.rooms()) firstOf.merge(r.volume(), r.storey(), Math::min);
			Map<Integer, Storey> storeys = new TreeMap<>();
			for (Storey s : b.storeys()) storeys.put(s.index(), s);
			for (var e : firstOf.entrySet()) {
				int s = e.getValue();
				if (s == 0 || s >= b.storeys().size() - 1) continue;
				int standing = storeys.get(s).y() + 1;
				boolean low = false;
				for (Opening o : b.openings()) if (isWindow(o) && inside(b, o).storey() == s && inside(b, o).volume().equals(e.getKey()) && o.box().y() == standing) low = true;
				assertTrue(low, why + ": no sill-0 window on the first storey (" + s + ") of " + e.getKey());
				towers[0]++;
			}
		});
		assertTrue(towers[0] > 20, "setbacks seen " + towers[0]);
	}

	/** Mass timber: stripped-wood columns at the bay lines (key {@code bayPiers}); the masonry tall styles have none. */
	@Test
	void massTimberHasBayLineColumns() {
		assertTrue(StyleLibrary.get("mass-timber-oak").facade().bayPiers());
		assertFalse(StyleLibrary.get("tall-brick").facade().bayPiers());
		int timber = 0, masonry = 0;
		for (long seed = 1; seed <= 6; seed++) {
			timber += columns(tower(seed, "mass-timber-oak", 28));
			masonry += columns(tower(seed, "tall-brick", 28));
		}
		assertTrue(timber > 4 * masonry && timber > 300, "bay-line columns: mass timber " + timber + ", brick " + masonry);
	}

	/** Beam boxes one storey high or more standing in a wall plane (a column, not a course). */
	static int columns(BuildingIr b) {
		int n = 0;
		for (Box x : b.boxes()) if (x.role() == Role.BEAM && x.box().sy() >= 3 && (x.box().sx() == 1 || x.box().sz() == 1)) n++;
		return n;
	}
}
