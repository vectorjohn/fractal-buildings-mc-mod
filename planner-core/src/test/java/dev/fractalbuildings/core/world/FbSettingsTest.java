package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FbSettingsTest {
	static FbSettings parse(String json, List<String> warns) { return FbSettings.parse(json, warns::add); }

	@Test
	void defaultsRoundTripWithoutWarnings() {
		List<String> w = new ArrayList<>();
		FbSettings d = FbSettings.defaults();
		assertEquals(d, parse(d.toJson(), w));
		assertEquals(List.of(), w);
		assertEquals(4096, d.world().maxRadius());
		assertEquals(d, parse("{}", w));
	}

	@Test
	void invalidValuesAreClampedAndLogged() {
		List<String> w = new ArrayList<>();
		FbSettings s = parse("{\"frequency\": -2, \"maxRadius\": 99999, \"cellSize\": 5, \"styles\": [\"medieval\", \"nope\"], \"villagers\": \"yes\"}", w);
		assertEquals(0, s.world().frequency());
		assertEquals(FbSettings.MAX_RADIUS, s.world().maxRadius());
		assertEquals(256, s.world().cellSize());
		assertEquals(Set.of("medieval"), s.world().styles());
		assertTrue(s.villagers());
		assertEquals(5, w.stream().filter(x -> x.contains("frequency") || x.contains("maxRadius 99999 out") || x.contains("cellSize") || x.contains("unknown style nope") || x.contains("villagers")).count(), w.toString());
		assertTrue(w.stream().anyMatch(x -> x.contains("hours of chunk generation")), w.toString());
	}

	@Test
	void intercityRoadsDefaultToOnAndCanBeSwitchedOff() {
		List<String> w = new ArrayList<>();
		assertTrue(FbSettings.defaults().world().intercityRoads());
		assertTrue(parse("{}", w).world().intercityRoads());
		FbSettings off = parse("{\"intercityRoads\": false}", w);
		assertFalse(off.world().intercityRoads());
		assertEquals(off, parse(off.toJson(), w));
		assertTrue(parse("{\"intercityRoads\": \"no\"}", w).world().intercityRoads(), "a bad value keeps the default");
		assertEquals(1, w.size(), w.toString());
	}

	@Test
	void garbageGivesDefaults() {
		for (String bad : new String[] {"", "{", "[1]", "42", "{\"frequency\": \"lots\"}"}) {
			List<String> w = new ArrayList<>();
			assertEquals(FbSettings.defaults(), parse(bad, w), bad);
			assertFalse(w.isEmpty(), bad);
		}
	}

	@Test
	void onlyUnknownStylesMeansEveryStyle() {
		List<String> w = new ArrayList<>();
		assertEquals(Set.of(), parse("{\"styles\": [\"zzz\"]}", w).world().styles());
		assertTrue(w.stream().anyMatch(x -> x.contains("enabling every style")));
	}

	@Test
	void sameFileSameConfig() {
		String j = "{\"frequency\": 0.5, \"maxRadius\": 2048, \"styles\": [\"plain\", \"desert\"]}";
		assertEquals(parse(j, new ArrayList<>()), parse(j, new ArrayList<>()));
		assertEquals(Set.of("desert", "plain"), parse(j, new ArrayList<>()).world().styles());
		assertEquals(parse(j, new ArrayList<>()).toJson(), parse(parse(j, new ArrayList<>()).toJson(), new ArrayList<>()).toJson());
	}
}
