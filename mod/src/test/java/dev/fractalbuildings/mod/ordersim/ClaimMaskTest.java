package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.ClaimMask;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ROADLEAFORDER: {@link ClaimMask} (the claims from the plan alone, which {@code FoliageClear.own} anticipates) against what the pieces and the
 * road draw really claim in a run of the order sim (the reference raster and the road ledger), over SC4: an intercity road beside districts
 * on the recorded river window of world 12345. Every piece cell has the same role in the mask; every road cell the draw claimed is in it
 * (the mask may claim more: a road column the draw drops under a foreign structure, the snow cell over the clear).
 */
class ClaimMaskTest {
	static Scenario sc4;
	static WorldPlanner planner;

	@BeforeAll
	static void build() {
		SimBoot.boot();
		SimSites site = RoadWorld.site();
		planner = RoadWorld.planner(site);
		RoadWorld.Window win = RoadWorld.densestWindow(planner, 4);
		sc4 = Scenario.city("sc4-claims", site, planner, win.cx(), win.cz(), win.n(), 0, false, List.of(), false);
	}

	@Test
	void theMaskHoldsEveryCellThePiecesAndTheRoadClaim() {
		SimRun r = OrderSim.run(sc4, SimOrders.forward(sc4.decX0(), sc4.decZ0(), sc4.decN(), sc4.decN()), false);
		Scenario.Plan p = sc4.plan();
		int x0 = sc4.cx * 16, z0 = sc4.cz * 16, n = sc4.n * 16;
		int y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
		for (long k : p.innerKeys) { y0 = Math.min(y0, PieceLedger.y(k)); y1 = Math.max(y1, PieceLedger.y(k)); }
		for (long k : r.roadStates.keySet()) if (sc4.inInner(PieceLedger.x(k), PieceLedger.z(k))) { y0 = Math.min(y0, PieceLedger.y(k)); y1 = Math.max(y1, PieceLedger.y(k)); }
		ClaimMask m = ClaimMask.of(planner, new Box3(x0, y0, z0, n, y1 - y0 + 1, n));
		List<String> bad = new ArrayList<>();
		int pieces = 0, roads = 0;
		for (long k : p.innerKeys) {
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			byte want = Block.BLOCK_STATE_REGISTRY.byId(p.ref.get(k)).isAir() ? ClaimMask.AIR : ClaimMask.SOLID;
			pieces++;
			if (m.role(x, y, z) != want && bad.size() < 20) bad.add("piece " + x + "," + y + "," + z + " want " + want + " mask " + m.role(x, y, z));
		}
		for (Map.Entry<Long, Integer> e : r.roadStates.entrySet()) {
			long k = e.getKey();
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			if (!sc4.inInner(x, z)) continue;
			byte want = Block.BLOCK_STATE_REGISTRY.byId(e.getValue()).isAir() ? ClaimMask.AIR : ClaimMask.SOLID;
			roads++;
			if (m.role(x, y, z) != want && bad.size() < 40) bad.add("road " + x + "," + y + "," + z + " want " + want + " mask " + m.role(x, y, z));
		}
		System.out.println("[claimmask] sc4: " + pieces + " piece cells, " + roads + " road cells, mismatches " + bad);
		assertTrue(pieces > 1000 && roads > 100, "the window has pieces and road: " + pieces + ", " + roads);
		assertEquals(List.of(), bad);
	}
}
