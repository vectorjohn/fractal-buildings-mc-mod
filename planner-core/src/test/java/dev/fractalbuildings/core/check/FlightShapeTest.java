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
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** STAIRJOG: the DS-45 amendment's numbers (turns 2 or more risers apart, no rail post on a tread cell) on hand-made site boxes. */
class FlightShapeTest {
	private static PlacedBuilding base() {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:70");
		PlacedBuilding b = new GeneratorV1().generate(new GenRequest(3, new Lot("lot0", 0, 0, 16, 14, Facing.NORTH), "house", "medieval", p.terrain(), p.biome())).buildings().get(0);
		return new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), List.of());
	}

	private static PlacedBuilding with(PlacedBuilding b, int[][] treads, int[][] rails) {
		List<Box> boxes = new ArrayList<>();
		for (int[] t : treads) boxes.add(new Box(Box3.of(t[0], t[1], t[2], 1, 1, 1), Role.STAIR, Layer.FOUNDATION, Box.NO_STOREY));
		for (int[] r : rails) boxes.add(new Box(Box3.of(r[0], r[1], r[2], 1, 1, 1), Role.RAIL, Layer.FOUNDATION, Box.NO_STOREY));
		return new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), boxes);
	}

	private static List<String> ids(PlacedBuilding b) { return DoorChecks.flightShape(b).stream().map(v -> v.invariant()).toList(); }

	@Test
	void theJogOfTheMetropolisTowerIsFlagged() { // the flight of d26_m7.l2 as it was: along the facade two risers, then out into the lot-edge row and on after one step
		int[][] jog = {{12, 0, -1}, {13, 0, -1}, {14, 0, -1}, {11, -1, -1}, {10, -2, -1}, {9, -2, -1}, {9, -3, -2}, {8, -4, -2}, {7, -5, -2}, {6, -5, -2}, {5, -6, -2}};
		assertTrue(ids(with(base(), jog, new int[0][])).contains("flight-turns"));
	}

	@Test
	void aStraightFlightOfRunsOfTwoPasses() { // 2 / rest / 2 / rest / 2
		int[][] straight = {{12, 0, -1}, {13, 0, -1}, {14, 0, -1}, {11, -1, -1}, {10, -2, -1}, {9, -2, -1}, {8, -3, -1}, {7, -4, -1}, {6, -4, -1}, {5, -5, -1}, {4, -6, -1}};
		assertEquals(List.of(), ids(with(base(), straight, new int[0][])));
	}

	@Test
	void theFirstFixOf6_31WasStillFlaggedForARunOfOne() { // 2 / rest / 3 / rest / 1: the flight the architect rejected
		int[][] old = {{12, 0, -1}, {13, 0, -1}, {14, 0, -1}, {11, -1, -1}, {10, -2, -1}, {9, -2, -1}, {8, -3, -1}, {7, -4, -1}, {6, -5, -1}, {5, -5, -1}, {4, -6, -1}};
		assertEquals(List.of("flight-runs"), ids(with(base(), old, new int[0][])));
	}

	@Test
	void aSingleStepToTheDoorLandingIsExempt() {
		assertEquals(List.of(), ids(with(base(), new int[][] {{12, 0, -1}, {11, -1, -1}}, new int[0][])));
	}

	@Test
	void anLWithTurnsThreeRisersApartPasses() {
		int[][] l = {{5, 0, 5}, {4, -1, 5}, {3, -2, 5}, {2, -3, 5}, {2, -3, 4}, {2, -4, 3}, {2, -5, 2}};
		assertEquals(List.of(), ids(with(base(), l, new int[0][])));
	}

	@Test
	void aHalfLandingIsOneLandingNotTwoTurns() { // U-turn on a 2-cell landing: both turns on the same level
		int[][] u = {{5, 0, 5}, {4, -1, 5}, {3, -2, 5}, {2, -3, 5}, {2, -3, 4}, {3, -3, 4}, {3, -4, 4}};
		assertTrue(ids(with(base(), u, new int[0][])).isEmpty() || ids(with(base(), u, new int[0][])).stream().noneMatch(i -> i.equals("flight-turns")));
	}

	@Test
	void aRailPostOnATreadCellIsFlagged() {
		int[][] straight = {{12, 0, -1}, {11, -1, -1}, {10, -2, -1}};
		assertEquals(List.of("flight-rail"), ids(with(base(), straight, new int[][] {{11, 0, -1}})));
		assertEquals(List.of(), ids(with(base(), straight, new int[][] {{11, 0, -2}})));
	}
}
