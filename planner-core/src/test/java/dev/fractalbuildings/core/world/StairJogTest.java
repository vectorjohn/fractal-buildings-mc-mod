package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CityPlanner;
import dev.fractalbuildings.core.settlement.DistrictScenes;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 6.31 STAIRJOG on the user's playtest world (seed 1573336981619055086, {@code pt1573-metro-s8} over {@code pt1573-world-s64}), metropolis s4_m2, lot d26_m7.l2: the
 * high-rise's triple door faces a lot that leaves two rows in front. The flight used to run two risers along the facade and jog out into the lot-edge row
 * (two turns one riser apart, a rail post beside the turn); it now runs straight along the row next to the wall, with rests, and steps off onto the street at the foot.
 */
class StairJogTest {
	@Test
	void theMetropolisTowerFlightRunsStraight() {
		WorldPlanner w = JoinWorld.pt1573();
		var site = w.siteOf(new Cell(4, -2)).orElseThrow();
		var p = w.probes();
		var city = CityPlanner.plan(w.source(site), p.coarse(), p.exact(), p.biome(), GeneratorV1.GEN_VERSION);
		DistrictScenes.Built b = CityPlanner.scene(city, "d26_m7", p.exact(), p.biome(), true);
		var pb = b.scene().buildings().stream().filter(x -> x.lots().contains("d26_m7.l2")).findFirst().orElseThrow();
		List<int[]> treads = pb.siteBoxes().stream().filter(x -> x.role() == Role.STAIR && x.box().x() >= 0 && x.box().z() < 0).map(x -> new int[] {x.box().x(), x.box().y(), x.box().z()}).toList();
		assertTrue(treads.size() >= 10, "an entrance flight of a rise of 6: " + treads.size());
		for (int[] t : treads) assertEquals(-1, t[2], "every tread (landing and flight) in the row beside the wall, no jog out into the lot-edge row: x=" + t[0] + " y=" + t[1]);
		assertEquals(List.of(), b.warnings().stream().filter(v -> v.invariant().startsWith("flight-") && v.building().equals(pb.id())).toList());
	}
}
