package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.types.house.HouseType;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.rand.Draws;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** House massing statistics (plan step M): wings, jetties and setbacks appear at the promised rates, and always pass the contract. */
class HouseMassingTest {
	static final int SEEDS = 1000;
	static final int[][] ENVELOPES = {{18, 18}, {29, 15}};

	record Stats(int houses, int multi, int differing, int jettied, int jettiable, int setback, int taller, int plans) {}

	static Massing mass(String style, long seed, int w, int d, int storeys) {
		BuildingSpec sp = new BuildingSpec(GeneratorV1.GEN_VERSION, seed, style, "house", w, d, storeys, "auto", 384, 1);
		BuildContext c = new BuildContext(sp, new HouseType(), StyleLibrary.get(style));
		Massing m = new HouseType().mass(c);
		List<String> p = MassingCheck.check(m, w, d, 384);
		assertTrue(p.isEmpty(), style + " seed " + seed + " " + w + "x" + d + ": " + p);
		return m;
	}

	static Stats stats(String style) {
		int houses = 0, multi = 0, differing = 0, jettied = 0, jettiable = 0, setback = 0, taller = 0, plans = 0;
		for (int[] e : ENVELOPES)
			for (long seed = 0; seed < SEEDS; seed++) {
				int storeys = 1 + new Draws(seed * 31 + e[0]).below(3);
				Massing m = mass(style, seed, e[0], e[1], storeys);
				houses++;
				if (storeys >= 2) jettiable++;
				boolean wings = false, diff = false;
				Mass main = m.main();
				int mainTop = 0;
				for (Mass a : m.masses()) if (List.of("main", "jetty", "setback").contains(a.kind())) mainTop = Math.max(mainTop, a.to());
				for (Mass a : m.masses()) {
					if (a.kind().equals("wing") || a.kind().equals("range")) {
						wings = true;
						if (Math.abs(a.sx() - main.sx()) >= 2 || Math.abs(a.sz() - main.sz()) >= 2 || a.to() != mainTop) diff = true;
						if (a.to() > mainTop) taller++;
					}
					if (a.kind().equals("jetty")) { jettied++; break; }
				}
				for (Mass a : m.masses()) if (a.kind().equals("setback")) { setback++; break; }
				if (wings) multi++;
				if (wings && diff) differing++;
				if (m.masses().stream().filter(a -> a.ground()).count() > 1) plans++;
			}
		return new Stats(houses, multi, differing, jettied, jettiable, setback, taller, plans);
	}

	@Test
	void medievalHousesHaveWingsOfOtherSizesAndJetties() {
		Stats s = stats("medieval");
		System.out.println(String.format(Locale.ROOT, "medieval house massing: %s", s));
		assertTrue(s.multi >= 0.40 * s.houses, "multi-volume houses " + s);
		assertTrue(s.differing >= 0.70 * s.multi, "wings differing from the main block " + s);
		assertTrue(s.jettied >= 0.20 * s.jettiable, "jetties among 2-3 storey houses " + s);
		assertTrue(s.setback > 0, "setbacks occur " + s);
	}

	@Test
	void plainHousesHaveNoJetties() {
		Stats s = stats("plain");
		System.out.println(String.format(Locale.ROOT, "plain house massing: %s", s));
		assertEquals(0, s.jettied, "plain has no jetties");
		assertTrue(s.multi >= 0.40 * s.houses, "multi-volume houses " + s);
		assertTrue(s.differing >= 0.70 * s.multi, "wings differing from the main block " + s);
	}

	@Test
	void sameSeedSameMassing() {
		for (long seed = 0; seed < 100; seed++) assertEquals(mass("medieval", seed, 29, 15, 3), mass("medieval", seed, 29, 15, 3));
	}
}
