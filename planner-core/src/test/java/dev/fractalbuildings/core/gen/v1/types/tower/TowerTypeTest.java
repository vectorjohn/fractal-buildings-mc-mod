package dev.fractalbuildings.core.gen.v1.types.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.rand.Draws;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tower properties (plan T-tower): tall and narrow, one core through every storey, under the height cap. */
class TowerTypeTest {
	static final int SEEDS = 600;
	static final int[][] ENVELOPES = {{7, 7}, {9, 9}, {11, 11}, {13, 13}};

	static Massing mass(String style, long seed, int w, int d, int storeys, int maxHeight) {
		BuildingSpec sp = new BuildingSpec(GeneratorV1.GEN_VERSION, seed, style, "tower", w, d, storeys, "auto", maxHeight, 1);
		return new TowerType().mass(new BuildContext(sp, new TowerType(), StyleLibrary.get(style)));
	}

	@Test
	void towersAreAtLeastTwiceAsTallAsWideAndStayUnderTheCap() {
		for (String style : StyleLibrary.ids())
			for (int[] e : ENVELOPES)
				for (long seed = 0; seed < SEEDS; seed++) {
					int storeys = 4 + new Draws(seed * 7 + e[0]).below(7);
					Massing m = mass(style, seed, e[0], e[1], storeys, 300);
					int wide = 0, top = 0;
					for (Mass a : m.masses()) {
						wide = Math.max(wide, Math.max(a.sx(), a.sz()));
						top = Math.max(top, a.topSlab(m.grid()));
					}
					String ctx = style + " " + e[0] + "x" + e[1] + " seed " + seed;
					assertTrue(top >= 2 * wide, ctx + ": height " + top + " < 2 x width " + wide);
					assertTrue(top + 2 <= 300, ctx + ": top " + top);
					assertEquals(storeys, m.storeys(), ctx + ": storeys kept when there is room");
					assertEquals(1, m.cores().size(), ctx);
					Core k = m.cores().get(0);
					assertTrue(k.from() == 0 && k.to() == m.storeys(), ctx + ": the core serves every storey");
				}
	}

	@Test
	void heightCapLowersTheStoreys() {
		Massing m = mass("plain", 3, 11, 11, 10, 34);
		assertTrue(m.storeys() < 10 && m.grid().y(m.storeys()) + 2 <= 34);
	}

	@Test
	void variantsAndFootprintsVary() {
		Set<String> shapes = new HashSet<>();
		Set<Integer> widths = new HashSet<>();
		for (long seed = 0; seed < SEEDS; seed++) {
			Massing m = mass("medieval", seed, 11, 11, 8, 300);
			shapes.add(m.masses().stream().map(Mass::kind).reduce("", (a, b) -> a + "/" + b));
			widths.add(m.main().sx() * 100 + m.main().sz());
		}
		assertTrue(shapes.size() >= 3, "shapes " + shapes);
		assertTrue(widths.size() >= 6, "footprints " + widths);
	}
}
