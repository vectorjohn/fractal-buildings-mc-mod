package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.json.JsonReader;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The DS-47 lot-loss guard: a road removal must never lower the lot count of a district of density 50 and up. */
class FillLotLossTest {
	private static TreeMap<String, int[]> m(Object... kv) {
		TreeMap<String, int[]> t = new TreeMap<>();
		for (int i = 0; i < kv.length; i += 3) t.put((String) kv[i], new int[] {(Integer) kv[i + 1], (Integer) kv[i + 2]});
		return t;
	}

	@Test
	void aDropInADenseDistrictIsALoss() {
		assertEquals(java.util.List.of("a 34 -> 6 (density 83)"), FillStats.lotLosses(m("a", 34, 83, "b", 10, 90), m("a", 6, 83, "b", 12, 90)));
	}

	@Test
	void gainsSparseDistrictsAndUnchangedAreNotLosses() {
		assertTrue(FillStats.lotLosses(m("a", 10, 49, "b", 5, 80, "c", 3, 60), m("a", 2, 49, "b", 5, 80, "c", 9, 60)).isEmpty());
	}

	@Test
	void parsesWhatToJsonWrites() {
		Object site = JsonReader.parse("{\"districtLots\": {\"d1_2\": [34, 83], \"d1_3\": [0, 40]}}");
		TreeMap<String, int[]> p = new TreeMap<>(FillStats.parseDistrictLots(site));
		assertArrayEquals(new int[] {34, 83}, p.get("d1_2"));
		assertEquals(2, p.size());
		assertTrue(FillStats.parseDistrictLots(JsonReader.parse("{}")).isEmpty());
	}
}
