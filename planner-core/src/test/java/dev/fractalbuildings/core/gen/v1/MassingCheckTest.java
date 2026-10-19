package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The fixtures keep the massing contract; one broken massing per rule shows each rule can fail. */
class MassingCheckTest {
	static final int E = TestMassings.ENVELOPE;

	static List<String> check(Massing m) { return MassingCheck.check(m, E, E, 384); }

	static void fails(String rule, Massing m) {
		List<String> p = check(m);
		assertTrue(p.stream().anyMatch(s -> s.startsWith(rule)), rule + " expected, got " + p);
	}

	static StoreyGrid g(int n) { return StoreyGrid.of(n, 4, 4); }

	static Mass ground(String id, int x, int z, int sx, int sz, int n) { return Mass.ground(id, "main", x, z, sx, sz, n); }

	@Test
	void fixturesPass() {
		for (Map.Entry<String, Massing> e : TestMassings.all().entrySet()) assertEquals(List.of(), check(e.getValue()), e.getKey());
	}

	@Test
	void eachRuleCanFail() {
		Mass main = ground("main0", 2, 2, 12, 8, 1);
		fails("M1", new Massing(g(2), List.of(new Mass("up", "x", 2, 2, 5, 5, 1, 2, "main0", false), main), List.of()));
		fails("M2", new Massing(g(1), List.of(ground("main0", 25, 2, 12, 8, 1)), List.of()));
		assertTrue(MassingCheck.check(new Massing(g(3), List.of(ground("main0", 2, 2, 12, 8, 3)), List.of(new Core(3, 6, 5, 2, 0, 3))), E, E, 13)
				.stream().anyMatch(s -> s.startsWith("M3")), "no roof room under maxHeight");
		fails("M3", new Massing(g(3), List.of(main), List.of()));
		fails("M5", new Massing(g(2), List.of(ground("main0", 4, 4, 12, 8, 1), new Mass("up", "jetty", 1, 4, 18, 8, 1, 2, "main0", false)), List.of())); // overhang 3
		fails("M5", new Massing(g(2), List.of(main, new Mass("up", "x", 10, 2, 8, 8, 1, 2, "main0", false)), List.of())); // sticks out on one side only
		fails("M6", new Massing(g(1), List.of(main, ground("w", 2, 8, 6, 6, 1)), List.of())); // overlaps two rows
		fails("M6", new Massing(g(1), List.of(main, ground("w", 2, 10, 6, 6, 1)), List.of())); // touches: double wall
		fails("M6", new Massing(g(1), List.of(main, ground("w", 12, 9, 6, 6, 1)), List.of())); // strip of 2
		fails("M7", new Massing(g(2), List.of(main, ground("w", 2, 9, 6, 6, 2)), List.of())); // wing storey 1 has no stair
		fails("M8", new Massing(g(2), List.of(ground("main0", 2, 2, 12, 8, 2)), List.of(new Core(2, 6, 5, 2, 0, 2)))); // in the wall
		fails("M8", new Massing(g(2), List.of(ground("main0", 2, 2, 12, 8, 2)), List.of(new Core(3, 6, 4, 2, 0, 2)))); // too short
		fails("M7", new Massing(g(1), List.of(main, ground("w", 2, 9, 6, 6, 1)), List.of(), List.of("main0"), List.of(new Massing.Party("main0", "w")))); // party wall, no own entrance
		fails("M1", new Massing(g(1), List.of(main, ground("w", 2, 9, 6, 6, 1)), List.of(), List.of("w", "main0"), List.of()));
		fails("M9", new Massing(g(2), List.of(ground("main0", 2, 2, 7, 5, 2)), List.of(new Core(3, 3, 5, 2, 0, 2)))); // core fills the front row
	}

	@Test
	void requireThrowsWithTheRules() {
		Massing bad = new Massing(g(1), List.of(ground("main0", 30, 2, 12, 8, 1)), List.of());
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> MassingCheck.require(bad, E, E, 384));
		assertTrue(e.getMessage().contains("M2"));
	}
}
