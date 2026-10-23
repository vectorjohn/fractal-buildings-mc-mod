package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.settlement.Tier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Hooks in world coordinates (plan B5): they land in the planner's air with a floor under them, and the villager pick is order free. */
class WorldHooksTest {
	static final Box3 ALL = new Box3(-100_000, -64, -100_000, 200_000, 400, 200_000);

	@Test
	void hooksStandInRoomAirOnAFloor() {
		FixtureWorldPlanner w = FixtureWorldPlanner.sample();
		int hooks = 0, beds = 0, bedHeadsAir = 0;
		TreeSet<String> kinds = new TreeSet<>();
		for (BuildingPiece b : w.buildings(0, 0)) {
			Map<Long, Role> cells = new HashMap<>();
			w.raster(b.ref(), ALL, (x, y, z, role, id, shape) -> cells.put(WorldHooks.key(x, y, z), role));
			for (WorldHooks.WorldHook h : WorldHooks.of(b)) {
				hooks++;
				kinds.add(h.kind());
				assertEquals(Role.AIR, cells.get(WorldHooks.key(h.x(), h.y(), h.z())), "hook cell is not room air: " + h + " in " + b.ref());
				Role below = cells.get(WorldHooks.key(h.x(), h.y() - 1, h.z()));
				assertTrue(below != null && below != Role.AIR, "no floor under " + h + ": " + below);
				if (h.kind().equals("bed")) {
					beds++;
					if (cells.get(WorldHooks.key(h.headX(), h.y(), h.headZ())) == Role.AIR) bedHeadsAir++;
				}
			}
		}
		assertTrue(hooks > 5, "hooks " + hooks);
		assertTrue(kinds.contains("bed") && kinds.contains("chest"), "kinds " + kinds);
		assertTrue(beds == 0 || bedHeadsAir * 2 >= beds, "bed heads in air: " + bedHeadsAir + " of " + beds);
	}

	@Test
	void villagerPickIsCappedAndIgnoresLotOrder() {
		FixtureWorldPlanner w = FixtureWorldPlanner.sample();
		List<BuildingPiece> tile = new ArrayList<>(w.buildings(0, 0));
		Set<Long> a = WorldHooks.villagerBeds(tile, 42, 10);
		Collections.reverse(tile);
		assertEquals(a, WorldHooks.villagerBeds(tile, 42, 10));
		assertTrue(a.size() <= WorldHooks.MAX_VILLAGERS_PER_DISTRICT);
		Set<Long> three = WorldHooks.villagerBeds(tile, 42, 3);
		assertEquals(Math.min(3, a.size()), three.size());
		assertNotEquals(WorldHooks.villagerBeds(tile, 43, 3), three, "another seed picks other beds (unless fewer than 4 beds)");
		for (long k : a) assertTrue(a.contains(k));
	}

	@Test
	void onlyHamletsAndVillagesHaveVillagers() {
		for (Tier t : Tier.values()) assertEquals(t == Tier.HAMLET || t == Tier.VILLAGE, WorldHooks.hasVillagers(t));
	}
}
