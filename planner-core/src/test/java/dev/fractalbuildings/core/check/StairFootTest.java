package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** STAIRFOOT: {@code stair-foot}, the step in front of a door has floor behind it (the user's playtest 2026-10-27, a high-rise roof door over the stairwell opening). */
class StairFootTest {
	private static SceneDoc gen(String type, long seed, String style, int w, int d) {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:70");
		try { return new GeneratorV1().generate(new GenRequest(seed, new Lot("lot0", 0, 0, w, d, Facing.NORTH), type, style, p.terrain(), p.biome())); } catch (IllegalArgumentException e) { return null; }
	}

	@Test
	void everyRoofDoorStepOfAHighriseHasFloorBehindIt() {
		int built = 0, steps = 0;
		for (String style : new String[] {"medieval", "plain"})
			for (long seed = 1; seed <= 20; seed++) {
				SceneDoc s = gen("highrise", seed, style, 28, 28);
				if (s == null) continue;
				built++;
				for (PlacedBuilding b : s.buildings()) {
					assertEquals(List.of(), StairFoot.check(b, VoxelGrid.of(b)), "highrise " + style + " seed " + seed);
					steps++;
				}
			}
		assertTrue(built >= 30 && steps >= 30, built + " built");
	}

	@Test
	void everyTypeHasFloorBehindItsDoorSteps() {
		List<String> bad = new ArrayList<>();
		for (String type : new String[] {"house", "shop", "workshop", "hall", "tower", "bastion", "temple"})
			for (long seed = 1; seed <= 10; seed++) {
				SceneDoc s = gen(type, seed, "medieval", 22, 20);
				if (s == null) continue;
				for (PlacedBuilding b : s.buildings()) if (!StairFoot.check(b, VoxelGrid.of(b)).isEmpty()) bad.add(type + " " + seed);
			}
		assertEquals(List.of(), bad);
	}
}
