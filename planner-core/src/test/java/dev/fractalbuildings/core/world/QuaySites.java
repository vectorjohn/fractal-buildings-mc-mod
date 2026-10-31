package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Districts;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import java.util.List;
import java.util.function.Supplier;

/**
 * QUAYLAZY test fixtures: the waterfront settlements of the committed recorded worlds, each as a planner factory and a site. A fresh
 * planner gives a fresh {@link SkeletonSource} (nothing shared with another tile's planning), the worst case for "a tile's answer does not
 * depend on who asked first". Labels follow the CLI's cell names ({@code s4_m2} is cell (4, -2)).
 *
 * <p>Sites: the three pt1573 metropolises, the w12345 river-town window's, and the w987654321 gate windows (a gate window's planner
 * is cut to its site).
 */
record QuaySites(String label, boolean heavy, Supplier<WorldPlanner> planner, java.util.function.Function<WorldPlanner, SettlementSite> siteOf) {

	/** Every site; {@code heavy}: a metropolis whose tile-by-tile walks take minutes. */
	static List<QuaySites> all() {
		return List.of(
			cell("pt1573-s4_m2", true, JoinWorld::pt1573, new Cell(4, -2)),
			cell("pt1573-s4_m5", true, JoinWorld::pt1573, new Cell(4, -5)),
			cell("pt1573-s5_m4", false, JoinWorld::pt1573, new Cell(5, -4)),
			cell("w12345-s1_m5", false, QuaySites::w12345, new Cell(1, -5)),
			cell("w12345-sm1_m4", false, QuaySites::w12345, new Cell(-1, -4)),
			cell("w12345-sm3_m4", true, QuaySites::w12345, new Cell(-3, -4)),
			gate("w987-gate-metropolis-mixed", true, "gate-metropolis-mixed"),
			gate("w987-gate-coast-city", false, "gate-coast-city"));
	}

	static QuaySites of(String label) {
		for (QuaySites s : all()) if (s.label.equals(label)) return s;
		throw new IllegalArgumentException(label);
	}

	static WorldPlanner w12345() { return new WorldPlanner(ParallelReadsPlannerTest.SEED, WorldConfig.defaults(), ParallelReadsPlannerTest.base(), null); }

	private static QuaySites cell(String label, boolean heavy, Supplier<WorldPlanner> mk, Cell c) {
		return new QuaySites(label, heavy, mk, w -> w.siteOf(c).orElseThrow(() -> new IllegalStateException("no settlement at " + c + " for " + label)));
	}

	private static QuaySites gate(String label, boolean heavy, String window) {
		GateWorld g = GateWorld.load(window);
		return new QuaySites(label, heavy, () -> g.planner(WorldPlanner.Caches.DEFAULT, null), w -> g.site());
	}

	/** One planner, with its settlement's source. */
	record Open(WorldPlanner planner, SettlementSite site, SkeletonSource.Direct source) {
		TerrainProbe exact() { return planner.probes().exact(); }

		/** The owned waterfront tiles, in tile order. */
		List<DistrictGrid.TileId> waterfront() { return Districts.waterfrontTiles(source, exact()); }
	}

	/** A fresh planner and source. */
	Open open() {
		WorldPlanner w = planner.get();
		SettlementSite site = siteOf.apply(w);
		SkeletonSource.Direct d = (SkeletonSource.Direct) w.source(site);
		if (Boolean.getBoolean("fb.quaylazy.reference")) d.withQuayReference(true); // the verbatim pre-QUAYLAZY whole trace
		return new Open(w, site, d);
	}
}
