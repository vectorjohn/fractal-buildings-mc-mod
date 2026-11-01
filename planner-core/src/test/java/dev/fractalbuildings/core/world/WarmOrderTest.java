package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Tier;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CASCADE A4: {@code warm} visits settlements nearest first (ties by id), and a destination-only warm changes no answer. */
class WarmOrderTest {
	private static SettlementSite site(String id, int x, int z, int r) {
		return new SettlementSite(id, Tier.TOWN, new Pt(x, z), r, new Cell(0, 0), List.of(new Cell(0, 0)));
	}

	@Test
	void visitsByRimDistanceThenId() {
		SettlementSite far = site("a-far", 1000, 0, 100), near = site("z-near", 300, 0, 50), big = site("m-big", -700, 0, 600), tieB = site("tie-b", 0, 500, 100), tieA = site("tie-a", 0, -500, 100);
		// rim distances from the origin: near 250, big 100, far 900, ties 400
		List<SettlementSite> order = WorldPlanner.warmOrder(List.of(far, near, tieB, big, tieA), 0, 0);
		assertEquals(List.of("m-big", "z-near", "tie-a", "tie-b", "a-far"), order.stream().map(SettlementSite::id).toList());
		// input order does not matter
		assertEquals(order, WorldPlanner.warmOrder(List.of(tieA, big, tieB, near, far), 0, 0));
	}

	@Test
	void destinationOnlyWarmChangesNoAnswer() {
		RegionField field = new RegionField(WorldPlannerRegionTest.SEED, 1024);
		int x = field.siteX(-3, -9), z = field.siteZ(-3, -9);
		WorldPlanner cold = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		WorldPlanner held = WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		held.warm(x, z, 600, true, true);
		held.warm(x, z, 600, true, false);
		assertEquals(cold.nearest(x, z, Tier.CITY, 1).orElseThrow(), held.nearest(x, z, Tier.CITY, 1).orElseThrow());
	}
}
