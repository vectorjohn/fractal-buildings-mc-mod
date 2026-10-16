package dev.fractalbuildings.core.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class SpecHashTest {
	private static final BuildingSpec S = new BuildingSpec(1, 42L, "plain", "house", 7, 5, 1, "gable");

	@Test
	void canonicalIsCompact() {
		assertEquals("{\"genVersion\":1,\"specSeed\":\"42\",\"style\":\"plain\",\"type\":\"house\",\"width\":7,\"depth\":5,\"storeys\":1,\"roof\":\"gable\"}",
				SpecHash.canonical(S));
	}

	@Test
	void goldenValue() {
		assertEquals("8b7b09defc2546e2", SpecHash.of(S));
	}

	@Test
	void everyFieldMatters() {
		String h = SpecHash.of(S);
		assertNotEquals(h, SpecHash.of(new BuildingSpec(2, 42L, "plain", "house", 7, 5, 1, "gable")));
		assertNotEquals(h, SpecHash.of(new BuildingSpec(1, 43L, "plain", "house", 7, 5, 1, "gable")));
		assertNotEquals(h, SpecHash.of(new BuildingSpec(1, 42L, "medieval", "house", 7, 5, 1, "gable")));
		assertNotEquals(h, SpecHash.of(new BuildingSpec(1, 42L, "plain", "hut", 7, 5, 1, "gable")));
		assertNotEquals(h, SpecHash.of(new BuildingSpec(1, 42L, "plain", "house", 8, 5, 1, "gable")));
		assertNotEquals(h, SpecHash.of(new BuildingSpec(1, 42L, "plain", "house", 7, 6, 1, "gable")));
		assertNotEquals(h, SpecHash.of(new BuildingSpec(1, 42L, "plain", "house", 7, 5, 2, "gable")));
		assertNotEquals(h, SpecHash.of(new BuildingSpec(1, 42L, "plain", "house", 7, 5, 1, "flat")));
	}
}
