package dev.fractalbuildings.mod.ordersim;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.ordersim.ScenarioFinder.LakeSpot;
import dev.fractalbuildings.mod.ordersim.ScenarioFinder.TrunkKind;
import dev.fractalbuildings.mod.ordersim.ScenarioFinder.TrunkSpot;
import java.util.List;

/**
 * ORDERSIM S4b: the SC2 (lake) and SC3 (dark oak trunk base) scenarios on gate-temperate-city, built from {@link ScenarioFinder}'s spots. Each is
 * a 3 x 3 inner window round the chunk of the spot, no hashed features, one scripted feature (the first rng seed in 0..63 that places on the
 * bare terrain), so the run is a few hundred milliseconds. Shared by the regression and the mutant tests.
 */
final class RegressionScenarios {

	final SimSites gate;
	final WorldPlanner planner;
	final ScenarioFinder finder;

	RegressionScenarios() {
		SimBoot.boot();
		gate = SimSites.load("gate-temperate-city");
		planner = gate.planner();
		finder = new ScenarioFinder(planner, gate.site(), gate.probes().exact());
	}

	private static RegressionScenarios shared;

	static synchronized RegressionScenarios get() {
		if (shared == null) shared = new RegressionScenarios();
		return shared;
	}

	LakeSpot lake(LakeKind k) {
		return switch (k) {
			case DUG_IN_WALL -> finder.lakeAtDugInWall();
			case UNDER_BOX -> finder.lakeUnderBox();
			case BELOW -> finder.lakeFarBelow();
		};
	}

	enum LakeKind { DUG_IN_WALL, UNDER_BOX, BELOW }

	/** SC2: a lava lake at the spot, in a 3 x 3 window centred on the spot's chunk. */
	Scenario lakeScenario(LakeSpot s) { return lakeScenario(s, 0); }

	/** As above with the origin moved {@code dx} blocks east (into the wall of a dug-in building: the lake then really reaches it). */
	Scenario lakeScenario(LakeSpot s0, int dx) {
		LakeSpot s = new LakeSpot(s0.kind(), s0.x() + dx, s0.y(), s0.z(), s0.building(), s0.vetoes());
		SimFeatures.Call c = SimFeatures.scripted("lake_lava", s.x(), s.y(), s.z(), gate.seed(), gate.probes().exact(), gate.probes().biome());
		if (c == null) throw new AssertionError("no rng seed places lake_lava at " + s);
		return Scenario.city("sc2-" + s.kind(), gate, planner, s.chunkX() - 1, s.chunkZ() - 1, 3, 0, false, List.of(c), true);
	}

	/** SC3: a dark oak whose 2 x 2 base is on the spot, in a 3 x 3 window centred on the street column's chunk. */
	Scenario trunkScenario(TrunkSpot s) { return trunkScenario(s, "sc3-" + s.kind()); }

	Scenario trunkScenario(TrunkSpot s, String id) {
		SimFeatures.Call c = SimFeatures.scripted("dark_oak", s.x(), s.y(), s.z(), gate.seed(), gate.probes().exact(), gate.probes().biome());
		if (c == null) throw new AssertionError("no rng seed places dark_oak at " + s);
		return Scenario.city(id, gate, planner, s.streetChunkX() - 1, s.streetChunkZ() - 1, 3, 0, false, List.of(c), true);
	}

	/** The spot whose tree is grown by the chunk next to the street's, so its order against the street's matters. */
	TrunkSpot trunk(TrunkKind k) { return finder.trunkSpotFromNeighbour(k); }

	/** Origin shift east of the SC2a lake: the finder's spot only touches the wall with the rim of the lake, which rarely carves it. */
	static final int LAKE_INTO_WALL = 4;

	/** SC3 (b'): the whole 2 x 2 base on street columns across a chunk line (no log stays beside the dirt). */
	TrunkSpot trunkInStreet() { return finder.trunkSpotInStreet(); }
}
