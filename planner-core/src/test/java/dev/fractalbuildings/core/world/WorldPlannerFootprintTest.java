package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.settlement.SettlementSite;
import org.junit.jupiter.api.Test;

/** The veto's footprint question (plan B7): the disc of a settlement plus a margin, exactly. */
class WorldPlannerFootprintTest {
	static final GateWorld G = GateWorld.load("gate-joined-city");

	@Test
	void discPlusMarginIsTheBoundary() {
		WorldPlanner p = G.planner(WorldPlanner.Caches.DEFAULT, null);
		SettlementSite s = G.site();
		int cx = s.centre().x(), cz = s.centre().z(), r = s.radius();
		assertTrue(p.footprintNear(cx, cz, 0), "centre");
		assertTrue(p.footprintNear(cx + r, cz, 0), "on the rim");
		assertFalse(p.footprintNear(cx + r + 1, cz, 0), "one block outside, no margin");
		assertTrue(p.footprintNear(cx + r + 140, cz, 140), "margin reaches");
		assertFalse(p.footprintNear(cx + r + 141, cz, 140), "margin ends");
		assertTrue(p.footprintNear(cx, cz - r - 100, 100), "any direction");
		assertFalse(p.footprintNear(cx + 20000, cz, 160), "far away");
	}

	@Test
	void fixtureAgreesWithItsSite() {
		FixtureWorldPlanner f = FixtureWorldPlanner.sample();
		var c = f.nearest(0, 0, dev.fractalbuildings.core.settlement.Tier.HAMLET, 1).orElseThrow().site();
		assertTrue(f.footprintNear(c.centre().x(), c.centre().z(), 0));
		assertEquals(false, f.footprintNear(c.centre().x() + c.radius() + 500, c.centre().z(), 100));
	}
}
