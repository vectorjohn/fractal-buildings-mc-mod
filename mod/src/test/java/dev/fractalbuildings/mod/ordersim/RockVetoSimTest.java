package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.Mutations;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.RockVeto;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ROCKVETO: a real {@code forest_rock} (BlockBlobFeature) of one chunk reaches a street cell of the next across a chunk line. Without the
 * veto ({@link Mutations#ROCKS}) the sim finds the mossy cobblestone in a claimed street cell in some chunk orders (C3, and C4 between
 * orders); with it the same scenario passes. Also: the veto's box covers every cell the feature writes, over many rng seeds.
 */
class RockVetoSimTest {
	static SimSites gate;
	static WorldPlanner planner;
	static ScenarioFinder finder;
	static ScenarioFinder.Window window;

	@BeforeAll
	static void build() {
		SimBoot.boot();
		gate = SimSites.load("gate-temperate-city");
		planner = gate.planner();
		finder = new ScenarioFinder(planner, gate.site(), gate.probes().exact());
		window = finder.densestWindow(2);
	}

	@AfterEach
	void reset() { Mutations.mode = null; }

	/** A forest_rock call that writes a street-surface cell of the window from the next chunk over, found by trying origins on bare terrain. */
	static SimFeatures.Call rockOverStreet() {
		ScenarioFinder.Columns cols = finder.columns(window.cx(), window.cz(), window.n());
		var terrain = gate.probes().exact();
		for (ScenarioFinder.Column c : cols.byCol.values()) {
			if (!c.street || c.surfaceY == Integer.MIN_VALUE) continue;
			for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
				int ox = c.x + d[0], oz = c.z + d[1];
				if (Math.floorDiv(ox, 16) == Math.floorDiv(c.x, 16) && Math.floorDiv(oz, 16) == Math.floorDiv(c.z, 16)) continue; // same chunk
				for (int dy = 0; dy <= 1; dy++) {
					int oy = terrain.floorY(ox, oz) + dy;
					for (long s = 0; s < 64; s++) {
						SimWorld w = new SimWorld(gate.seed(), Math.floorDiv(ox, 16) - 1, Math.floorDiv(oz, 16) - 1, 3, 3, terrain, gate.probes().biome());
						SimFeatures.Call call = new SimFeatures.Call(SimFeatures.stepOf("forest_rock"), "forest_rock", ox, oy, oz, s);
						if (!SimFeatures.place(w, null, call, new SimFeatures.Stats())) continue;
						for (SimWorld.Write wr : w.writes()) {
							if (wr.x() == c.x && wr.z() == c.z && wr.y() == c.surfaceY) {
								return call;
							}
						}
					}
				}
			}
		}
		return null;
	}

	static Scenario scenario(SimFeatures.Call call) {
		return new Scenario("rock-over-street", planner, gate.seed(), gate.probes().exact(), gate.probes().biome(), window.cx(), window.cz(), window.n(), List.of(call), true);
	}

	@Test
	void aRockOverAStreetFailsWithoutTheVetoAndPassesWithIt() {
		SimFeatures.Call call = rockOverStreet();
		assertNotNull(call, "a forest_rock that reaches a street cell across a chunk line exists in SC1");
		Scenario s = scenario(call);
		List<SimOrders.Order> orders = SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed);
		// control: veto off
		Mutations.mode = Mutations.ROCKS;
		OrderSim.Result off = OrderSim.runAll(s, orders, false);
		Mutations.mode = null;
		SimReport.write(off);
		assertTrue(off.runs.stream().allMatch(r -> r.features.placed() == 1), "the rock places in every order: " + off.runs.get(0).features);
		assertFalse(off.passed(), "without the veto some order leaves a rock block in a street cell");
		assertTrue(off.failed().stream().anyMatch(f -> f.contains("C3") || f.contains("C4")), off.failed().toString());
		assertTrue(off.runs.stream().anyMatch(r -> r.histogram.keySet().stream().anyMatch(k -> k.contains("mossy_cobblestone"))), "the writer is the rock: " + off.runs.get(0).histogram);
		// the veto
		long before = RockVeto.VETOED.get();
		OrderSim.Result on = OrderSim.runAll(s, orders, false);
		SimReport.write(on);
		assertEquals(List.of(), on.failed());
		assertTrue(on.runs.stream().allMatch(r -> r.features.vetoed() == 1 && r.features.placed() == 0), "the rock is vetoed in every order: " + on.runs.get(0).features);
		assertEquals(before + orders.size(), RockVeto.VETOED.get());
	}

	@Test
	void everyCellTheFeatureWritesIsInsideTheVetoBox() {
		var terrain = gate.probes().exact();
		int ox = window.cx() * 16 + 8, oz = window.cz() * 16 + 8, placed = 0;
		for (int dy : new int[] {0, 1, 12, 30}) { // 12 and 30: over a canopy or deep water, the descent is longer than 8
			for (long s = 0; s < 150; s++) {
				SimWorld w = new SimWorld(1L, window.cx() - 1, window.cz() - 1, 3, 3, terrain, gate.probes().biome());
				int oy = terrain.floorY(ox, oz) + dy;
				var call = new SimFeatures.Call(SimFeatures.stepOf("forest_rock"), "forest_rock", ox, oy, oz, s);
				var landed = SimFeatures.landed(w, 0, 0, call); // before the rock writes: the descent reads the level as it was
				if (!SimFeatures.place(w, null, call, new SimFeatures.Stats())) continue;
				placed++;
				var box = RockVeto.box(landed);
				for (SimWorld.Write wr : w.writes()) {
					if (wr.now() == Blocks.AIR.defaultBlockState()) continue;
					assertTrue(wr.x() >= box.x() && wr.x() < box.maxX() && wr.y() >= box.y() && wr.y() < box.maxY() && wr.z() >= box.z() && wr.z() < box.maxZ(),
						"write " + wr.x() + "," + wr.y() + "," + wr.z() + " outside " + box + " for seed " + s);
				}
			}
		}
		assertTrue(placed > 100, "the feature places: " + placed);
	}
}
