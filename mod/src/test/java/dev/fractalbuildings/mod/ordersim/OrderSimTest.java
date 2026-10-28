package dev.fractalbuildings.mod.ordersim;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.ordersim.SimOrders.Order;
import dev.fractalbuildings.mod.worldgen.BuildingAirClear;
import dev.fractalbuildings.mod.worldgen.SpillClear;
import dev.fractalbuildings.mod.worldgen.StreetAirFix;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ORDERSIM S4a: the sim's two broad scenarios in the fast order set, the liveness of the passes in them, and the three controls.
 *
 * <ul>
 * <li><b>SC1 city</b>: gate-temperate-city, the densest 6 x 6 chunk window, 3 hashed vanilla features per chunk.
 * <li><b>SC5 snow</b>: gate-snow-city, the densest 4 x 4 window, 3 hashed features per chunk with {@code freeze_top_layer} in the pool.
 * </ul>
 * Both leave {@code forest_rock} out of the pool: a rock of a neighbour chunk over our claimed solid street cell is a real order dependence
 * (vanilla's {@code BlockBlobFeature} replaces anything), pinned in {@link OrderSimRockTest} until ROCKVETO fixes it in production.
 *
 * <p>The controls do not fail C3. {@code SpillKinds} accepts leaves, logs and snow in claimed air as spill (the order test's classification),
 * so a pass switched off leaves only those, which C3 calls spill. What a missing pass shows is a bigger <em>residue</em> of them in the claimed
 * air than with the pass on (the histogram's {@code air->..._leaves/_log/snow} cells), so the controls assert that.
 */
class OrderSimTest {
	static SimSites temperate, snow;
	static Scenario sc1, sc5, sc1small;

	@BeforeAll
	static void build() {
		SimBoot.boot();
		temperate = SimSites.load("gate-temperate-city");
		WorldPlanner p1 = temperate.planner();
		ScenarioFinder f1 = new ScenarioFinder(p1, temperate.site(), temperate.probes().exact());
		ScenarioFinder.Window w6 = f1.densestWindow(6), w4 = f1.densestWindow(4);
		sc1 = Scenario.city("sc1", temperate, p1, w6.cx(), w6.cz(), w6.n(), 3, false, List.of(), false);
		sc1small = Scenario.city("sc1-small", temperate, p1, w4.cx(), w4.cz(), w4.n(), 3, false, List.of(), false);
		snow = SimSites.load("gate-snow-city");
		WorldPlanner p5 = snow.planner();
		ScenarioFinder.Window ws = new ScenarioFinder(p5, snow.site(), snow.probes().exact()).densestWindow(4);
		sc5 = Scenario.city("sc5", snow, p5, ws.cx(), ws.cz(), ws.n(), 3, true, List.of(), false);
		for (Scenario s : List.of(sc1, sc1small, sc5)) s.plan(); // the reference raster once per scenario: the tests time the orders
	}

	static List<Order> fast(Scenario s) { return SimOrders.fast(s.decX0(), s.decZ0(), s.decN(), s.decN(), s.seed); }

	/** Writes of our code over the audit runs (forward and reverse), by {@code Class.method/old-kind}. */
	static int fired(OrderSim.Result res, String key) {
		int n = 0;
		for (SimRun r : res.runs) n += r.branchWrites.getOrDefault(key, 0);
		return n;
	}

	static OrderSim.Result runPrint(Scenario s) {
		long t0 = System.nanoTime();
		OrderSim.Result res = OrderSim.runAll(s, fast(s), true);
		long ms = (System.nanoTime() - t0) / 1000;
		SimReport.write(res);
		int c4 = res.runs.stream().skip(1).mapToInt(r -> r.crossDiff).sum();
		System.out.println("[ordersim] " + s.id + ": " + res.runs.size() + " orders in " + ms / 1000 + " ms, loadavg " + SimReport.load() + ", C4 differing claimed cells (reported, not asserted) "
			+ res.runs.stream().skip(1).map(r -> r.order + "=" + r.crossDiff).toList() + ", passes fired " + res.runs.get(0).branchWrites);
		return res;
	}

	@Test
	void sc1CityPassesAndTheCleaningPassesFire() {
		OrderSim.Result res = runPrint(sc1);
		assertEquals(List.of(), res.failed());
		assertEquals(6, res.runs.size());
		assertTrue(res.runs.get(0).features.placed() > 50, "the hashed features really place: " + res.runs.get(0).features);
		assertTrue(res.runs.get(0).contractViolations == 0 && res.runs.get(1).contractViolations == 0, "C5 ran in forward and reverse");
		assertTrue(res.runs.get(0).idempotenceChanges == 0 && res.runs.get(1).idempotenceChanges == 0, "C6 ran in forward and reverse");
		// liveness: the passes really did something (forward and reverse together), so "passes" is not "never exercised"
		assertTrue(fired(res, "SpillClear.run/leaf") > 0, "SpillClear's leaf clear fired");
		assertTrue(fired(res, "StreetAirFix.run/log") > 0, "StreetAirFix removed a log");
		assertTrue(fired(res, "BuildingAirClear.clear/leaf") + fired(res, "BuildingAirClear.write/leaf") > 0, "BuildingAirClear removed a leaf");
		assertTrue(fired(res, "FoliageClear.erase/air") + fired(res, "FoliageClear.erase/leaf") + fired(res, "FoliageClear.erase/log") + fired(res, "FoliageClear.erase/other") > 0, "FoliageClear erased something");
	}

	@Test
	void sc5SnowCityPassesAndRemovesSnow() {
		OrderSim.Result res = runPrint(sc5);
		assertEquals(List.of(), res.failed());
		assertTrue(res.runs.get(0).features.byKey.containsKey("freeze_top_layer"), "snow is in the pool: " + res.runs.get(0).features);
		// the snow in street air is taken by SpillClear.run (snow is not a log, so it goes with the rest of the planned air); StreetAirFix removes only a layer hanging over air
		assertTrue(fired(res, "SpillClear.run/snow") + fired(res, "StreetAirFix.run/snow") > 0, "snow was removed from the claimed air: " + res.runs.get(0).branchWrites);
	}

	// ---------------------------------------------------------------- controls

	/** Claimed air cells that end up holding something the matcher takes, summed over the two audit orders (forward and reverse). */
	static java.util.List<java.util.Map<String, Integer>> histograms(Scenario s) {
		List<Order> o = fast(s);
		List<java.util.Map<String, Integer>> out = new java.util.ArrayList<>();
		for (int i = 0; i < 2; i++) out.add(OrderSim.run(s, o.get(i), false).histogram);
		return out;
	}

	static java.util.List<java.util.Map<String, Integer>> passOn;

	static int residue(java.util.List<java.util.Map<String, Integer>> hs, Predicate<String> key) {
		int n = 0;
		for (var h : hs) {
			n += h.entrySet().stream().filter(e -> e.getKey().startsWith("air->") && key.test(e.getKey())).mapToInt(java.util.Map.Entry::getValue).sum();
		}
		return n;
	}

	static void control(String what, Runnable off, Runnable on, Predicate<String> key) {
		if (passOn == null) passOn = histograms(sc1small); // every pass on: the same for the three controls
		int with = residue(passOn, key);
		int without;
		off.run();
		try {
			without = residue(histograms(sc1small), key);
		} finally {
			on.run();
		}
		System.out.println("[ordersim] control " + what + ": residue with the pass on " + with + ", off " + without);
		assertTrue(without > with, what + " off must leave more in the claimed air than on (on " + with + ", off " + without + ")");
	}

	@Test
	void controlSpillClearOffLeavesLeavesInTheStreet() {
		control("SpillClear", () -> SpillClear.ENABLED = false, () -> SpillClear.ENABLED = true, k -> k.contains("leaves") || k.contains("mossy_cobblestone"));
	}

	@Test
	void controlStreetAirFixOffLeavesLogsInTheStreet() {
		control("StreetAirFix", () -> StreetAirFix.ENABLED = false, () -> StreetAirFix.ENABLED = true, k -> k.contains("_log") || k.contains("_wood"));
	}

	@Test
	void controlBuildingAirClearOffLeavesLeavesInRooms() {
		control("BuildingAirClear", () -> BuildingAirClear.ENABLED = false, () -> BuildingAirClear.ENABLED = true, k -> k.contains("leaves") || k.contains("_log"));
	}
}
