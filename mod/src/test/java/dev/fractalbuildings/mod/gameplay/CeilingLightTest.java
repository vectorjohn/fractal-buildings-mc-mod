package dev.fractalbuildings.mod.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The chain-lantern geometry of {@link HookPlacer}'s ceiling_light: chain length, lantern height, everything inside the room's column. */
class CeilingLightTest {
	@Test
	void lanternHangsAtHookPlusTwoFromThreeClearUp() {
		for (int clear = 3; clear <= 8; clear++) {
			assertEquals(clear - 3, CeilingLight.chain(clear), "clear " + clear);
			assertEquals(2, CeilingLight.lantern(clear), "clear " + clear);
		}
	}

	@Test
	void twoClearHangsUnderTheCeilingWithoutAChain() {
		assertTrue(CeilingLight.fits(2));
		assertEquals(0, CeilingLight.chain(2));
		assertEquals(1, CeilingLight.lantern(2));
		assertFalse(CeilingLight.fits(1));
	}

	@Test
	void chainAndLanternStayBelowTheCeilingInTheColumn() {
		for (int clear = 2; clear <= 8; clear++) {
			// ceiling at hook + clear; chain occupies ceiling - 1 .. ceiling - chain, the lantern the cell below the chain
			int top = clear - 1, bottom = top - CeilingLight.chain(clear);
			assertEquals(bottom, CeilingLight.lantern(clear) + (0), "lantern directly under the chain, clear " + clear);
			assertTrue(bottom >= 1 && top <= clear - 1, "inside the room's air, clear " + clear);
		}
	}
}
