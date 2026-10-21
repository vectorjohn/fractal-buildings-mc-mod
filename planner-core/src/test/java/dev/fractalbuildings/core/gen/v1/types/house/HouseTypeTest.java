package dev.fractalbuildings.core.gen.v1.types.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.ShellGeneratorV1;
import dev.fractalbuildings.core.gen.v1.Types;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** House rules (plan step T-house, 4.6): the room program and the variety the massing gives it. */
class HouseTypeTest {
	static final int SEEDS = 256;
	static final int[][] ENVELOPES = {{8, 8}, {16, 14}, {18, 18}, {29, 15}};
	static final List<String> STYLES = List.of("plain", "medieval");

	record Case(String style, long seed, int w, int d, int storeys, BuildingIr ir) {
		@Override
		public String toString() { return style + " seed " + seed + " " + w + "x" + d + " x" + storeys; }

		long count(String use, int storey) { return ir.rooms().stream().filter(r -> r.use().equals(use) && (storey < 0 || r.storey() == storey)).count(); }

		boolean hasHook(String kind, String use, int storeyFrom) {
			for (Room r : ir.rooms()) {
				if (!r.use().equals(use) || r.storey() < storeyFrom) continue;
				for (Hook h : ir.hooks()) if (h.room().equals(r.id()) && h.kind().equals(kind)) return true;
			}
			return false;
		}

		int storeysBuilt() { return ir.storeys().size(); }
	}

	static List<Case> cases() {
		List<Case> out = java.util.Collections.synchronizedList(new ArrayList<>());
		IntStream.range(0, SEEDS * ENVELOPES.length * STYLES.size()).parallel().forEach(i -> {
			int[] e = ENVELOPES[i % ENVELOPES.length];
			String style = STYLES.get(i / ENVELOPES.length % STYLES.size());
			long seed = i / (ENVELOPES.length * STYLES.size());
			int storeys = 1 + new Draws(seed * 31 + e[0]).below(3);
			BuildingSpec sp = new BuildingSpec(GeneratorV1.GEN_VERSION, seed, style, "house", e[0], e[1], storeys, "auto", 384, 1);
			out.add(new Case(style, seed, e[0], e[1], storeys, ShellGeneratorV1.generate(sp, Types.get("house"))));
		});
		out.sort(java.util.Comparator.comparing(Case::toString));
		return out;
	}

	@Test
	void everyUpperStoreyHouseHasABedroomWithABed() {
		int multi = 0;
		for (Case c : cases()) {
			if (c.storeysBuilt() < 2) continue;
			multi++;
			assertTrue(c.hasHook("bed", "bedroom", 1), c + ": no bedroom with a bed upstairs");
			// CIRC2: spare ground-floor rooms may be bedrooms, but never all of them (the entry or living room and the kitchen stay)
			long groundRooms = c.ir().rooms().stream().filter(r -> r.storey() == 0).count();
			assertTrue(c.count("bedroom", 0) <= Math.max(0, groundRooms - 2), c + ": ground floor of a house with an upper storey is mostly bedrooms");
		}
		assertTrue(multi > 400, "multi-storey houses " + multi);
	}

	@Test
	void groundFloorIsForLiving() {
		int houses = 0, hearth = 0, kitchen = 0, living = 0;
		for (Case c : cases()) {
			houses++;
			assertTrue(c.count("living", 0) + c.count("entry", 0) + c.count("hall", 0) + c.count("bedroom", 0) >= 1, c + ": no living room or entry downstairs");
			if (c.hasHook("hearth", "living", 0) || c.hasHook("hearth", "kitchen", 0)) hearth++;
			if (c.count("kitchen", 0) > 0) kitchen++;
			if (c.count("living", 0) > 0) living++;
		}
		System.out.println(String.format(Locale.ROOT, "house program: houses %d, with hearth %d, kitchen %d, living %d", houses, hearth, kitchen, living));
		assertTrue(hearth >= 0.60 * houses, "houses with a hearth " + hearth + "/" + houses);
		assertTrue(living >= 0.60 * houses, "houses with a living room " + living + "/" + houses);
	}

	@Test
	void housesVaryInTheirPrograms() {
		int multi = 0, twoBeds = 0, bedHooks = 0, plans = 0;
		java.util.Set<String> shapes = new java.util.TreeSet<>();
		for (Case c : cases()) {
			shapes.add(c.ir().volumes().size() + ":" + c.storeysBuilt() + ":" + c.count("bedroom", -1));
			if (c.storeysBuilt() < 2) continue;
			multi++;
			if (c.count("bedroom", -1) >= 2) twoBeds++;
			bedHooks += (int) c.ir().hooks().stream().filter(h -> h.kind().equals("bed")).count();
		}
		System.out.println(String.format(Locale.ROOT, "house variety: multi-storey %d, with 2+ bedrooms %d, beds %d, shapes %d", multi, twoBeds, bedHooks, shapes.size()));
		assertTrue(twoBeds >= 0.55 * multi, "2+ bedrooms " + twoBeds + "/" + multi);
		assertTrue(bedHooks >= multi, "beds " + bedHooks);
		assertTrue(shapes.size() >= 50, "distinct (volumes, storeys, bedrooms) shapes " + shapes.size());
	}

	@Test
	void frontMarginHoldsRoomForSteps() {
		assertTrue(Types.get("house").limits().front() >= 1);
	}
}
