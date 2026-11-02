package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 6.38 DOORREACH: {@code door-reaches-street} on generated houses with a hand-laid street in front of the lot (front north, street at z -4..-2). */
class DoorReachTest {
	private static SceneDoc scene(long seed, String type, int streetTop) {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:70");
		Lot lot = new Lot("lot0", 0, 0, 18, 18, Facing.NORTH);
		SceneDoc g = new GeneratorV1().generate(new GenRequest(seed, lot, type, "medieval", p.terrain(), p.biome()));
		List<Box> ground = new ArrayList<>();
		ground.add(new Box(Box3.of(-12, 69, -4, 44, streetTop - 69, 3), Role.ROAD_TOWN, Layer.GROUND, Box.NO_STOREY));
		return new SceneDoc(g.genVersion(), g.seed(), TerrainPatch.sample(p.terrain(), -12, -12, 44, 44), g.lots(), g.buildings(), ground, List.of());
	}

	@Test
	void aStreetAtTheLotsLevelIsReached() {
		for (long seed = 1; seed <= 12; seed++)
			for (String type : List.of("house", "shop", "workshop")) {
				var r = DoorReach.check(scene(seed, type, 70), true);
				assertEquals(List.of(), r.violations(), type + " seed " + seed);
				assertEquals(1, r.street(), type + " seed " + seed);
			}
	}

	/** Control: a street 3 above the lot's ground with nothing grading it down is out of the walker's reach, and the message says what would do. */
	@Test
	void aBankBetweenLotAndStreetIsFound() {
		var r = DoorReach.check(scene(3, "house", 73), true);
		assertEquals(1, r.violations().size(), r.toString());
		assertEquals(DoorReach.RULE, r.violations().get(0).invariant());
		assertTrue(r.violations().get(0).message().contains("a walk exists with steps of 3"), r.violations().get(0).message());
	}

	@Test
	void aSceneWithoutTerrainIsNotChecked() {
		SceneDoc s = scene(3, "house", 70);
		var r = DoorReach.check(new SceneDoc(s.genVersion(), s.seed(), null, s.lots(), s.buildings(), s.ground(), List.of()));
		assertEquals(0, r.buildings());
	}
}
