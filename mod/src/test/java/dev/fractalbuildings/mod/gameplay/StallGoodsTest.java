package dev.fractalbuildings.mod.gameplay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The stalls' wares are never a job block (interior designer's list) and every style has some. */
class StallGoodsTest {
	@Test
	void noStyleHoldsAJobBlock() {
		for (String style : new String[] {"medieval", "plains", "taiga", "snowy", "desert", "jungle", "plain", "mass-timber", "anything"}) {
			assertFalse(StallGoods.of(style).isEmpty(), style);
			for (String id : StallGoods.of(style)) assertFalse(StallGoods.NEVER.contains(id), style + " holds " + id);
		}
		assertTrue(StallGoods.NEVER.contains("lectern"));
	}

	@Test
	void pickIsAPureFunctionOfTheHash() {
		assertTrue(StallGoods.of("medieval").contains(StallGoods.pick("medieval", -12345L)));
		assertTrue(StallGoods.pick("desert", 7).equals(StallGoods.pick("desert", 7)));
	}
}
