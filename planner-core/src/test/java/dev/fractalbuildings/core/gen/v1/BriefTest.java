package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.probe.SyntheticBiomes;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import org.junit.jupiter.api.Test;

class BriefTest {
	private static GenRequest req(String type, int w, int d, Brief b) {
		return new GenRequest(5, new Lot("lot0", 10, 20, w, d, Facing.NORTH), type, "plain", SyntheticTerrain.flat(64), SyntheticBiomes.constant("minecraft:plains"), b);
	}

	@Test
	void noBriefIsTheOldBehaviour() {
		for (String t : new String[] {"house", "shop", "tower"}) {
			assertEquals(SpecPolicyV1.spec(req(t, 20, 20, null)),
					SpecPolicyV1.spec(new GenRequest(5, new Lot("lot0", 10, 20, 20, 20, Facing.NORTH), t, "plain", SyntheticTerrain.flat(64), SyntheticBiomes.constant("minecraft:plains"))));
		}
	}

	@Test
	void storeyRangeIsIntersectedWithTheTypes() {
		assertEquals(3, SpecPolicyV1.spec(req("house", 14, 14, new Brief(3, 3, 1, 0, Brief.NO_FRONT_Y))).storeys());
		for (int s = 0; s < 40; s++) {
			GenRequest r = new GenRequest(s, new Lot("lot0", s * 40, 0, 14, 14, Facing.NORTH), "house", "plain", SyntheticTerrain.flat(64),
					SyntheticBiomes.constant("minecraft:plains"), new Brief(2, 9, 1, 0, 64));
			int st = SpecPolicyV1.spec(r).storeys();
			assertTrue(st >= 2 && st <= 3, "house limits 1..3 intersected with 2..9: " + st);
		}
	}

	@Test
	void disjointRangeUsesTheTypesNearestBound() {
		assertEquals(3, SpecPolicyV1.spec(req("house", 14, 14, new Brief(30, 40, 1, 0, 64))).storeys());
		assertEquals(10, SpecPolicyV1.spec(req("highrise", 30, 30, new Brief(1, 4, 1, 0, 64))).storeys()); // the mid-rise floor (D3: highrise limits start at 10)
	}

	@Test
	void streetsMaskHonouredFrontForcedAllStreetsKept() {
		assertEquals(1 | 2 | 8, SpecPolicyV1.spec(req("house", 14, 14, new Brief(1, 2, 2 | 8, 0, 64))).streets());
		assertEquals(BuildingSpec.STREET_FRONT, SpecPolicyV1.spec(req("house", 14, 14, new Brief(1, 2, 0, 0, 64))).streets());
		assertEquals(15, SpecPolicyV1.spec(req("block", 60, 60, new Brief(2, 3, 1, 0, 64))).streets());
	}

	@Test
	void airRightsAndFrontYAreCarriedButIgnored() {
		assertEquals(SpecPolicyV1.spec(req("house", 14, 14, new Brief(1, 3, 1, 0, Brief.NO_FRONT_Y))), SpecPolicyV1.spec(req("house", 14, 14, new Brief(1, 3, 1, 2, 70))));
	}

	@Test
	void briefValidatesItself() {
		assertThrows(IllegalArgumentException.class, () -> new Brief(0, 2, 1, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Brief(3, 2, 1, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Brief(1, 2, 16, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Brief(1, 2, 1, -1, 0));
	}
}
