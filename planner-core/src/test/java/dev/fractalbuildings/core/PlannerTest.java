package dev.fractalbuildings.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlannerTest {
	@Test
	void versionIsStable() {
		assertEquals("0.1.0", Planner.version());
	}
}
