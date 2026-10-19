package dev.fractalbuildings.core.gen.v1.types.highrise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.ShellGeneratorV1;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** High-rise properties (plan step 4.6, T-highrise): massing shape, height cap, core, shell time. */
class HighriseTypeTest {
	static final int SEEDS = 300;
	static final int[][] ENVELOPES = {{18, 18}, {26, 26}, {34, 34}, {42, 42}, {40, 20}};

	static BuildingSpec spec(long seed, int w, int d, int storeys, int maxHeight) {
		return new BuildingSpec(GeneratorV1.GEN_VERSION, seed, "plain", HighriseType.ID, w, d, storeys, "auto", maxHeight, 1);
	}

	static Massing mass(BuildingSpec sp) {
		HighriseType t = new HighriseType();
		Massing m = t.mass(new BuildContext(sp, t, StyleLibrary.get(sp.style())));
		assertEquals(List.of(), MassingCheck.check(m, sp.width(), sp.depth(), sp.maxHeight()), sp.toString());
		return m;
	}

	interface Each { void at(BuildingSpec sp, Massing m); }

	static void sweep(int minMaxHeight, int maxMaxHeight, Each each) {
		for (int[] e : ENVELOPES)
			for (long seed = 0; seed < SEEDS; seed++) {
				Draws r = new Draws(seed * 977 + e[0]);
				int storeys = r.range(20, 60), maxH = r.range(minMaxHeight, maxMaxHeight);
				BuildingSpec sp = spec(seed, e[0], e[1], storeys, maxH);
				each.at(sp, mass(sp));
			}
	}

	@Test
	void everyTierSitsInsideTheOneBelowAndTheTopStaysUnderMaxHeight() {
		sweep(40, 320, (sp, m) -> {
			Mass prev = null;
			for (Mass a : m.masses()) {
				if (prev != null) {
					assertEquals(prev.id(), a.parent());
					assertEquals(prev.to(), a.from());
					assertTrue(a.x() >= prev.x() && a.z() >= prev.z() && a.maxX() <= prev.maxX() && a.maxZ() <= prev.maxZ(), "tier inside the one below: " + a + " on " + prev);
					assertTrue(a.sx() < prev.sx() || a.sz() < prev.sz(), "a tier is a real setback");
				}
				prev = a;
			}
			int top = m.grid().y(prev.to());
			assertTrue(top + HighriseMassing.ROOF_ROOM <= sp.maxHeight() || m.masses().size() == 1, "top " + top + " vs maxHeight " + sp.maxHeight());
		});
	}

	@Test
	void tallWhenTheCapAllows() {
		int[] tall = {0};
		sweep(100, 320, (sp, m) -> {
			assertTrue(m.grid().count() >= 20, "maxHeight " + sp.maxHeight() + " gives " + m.grid().count() + " storeys, spec " + sp.storeys());
			tall[0]++;
		});
		assertTrue(tall[0] > 1000);
	}

	@Test
	void shapeIsPodiumShaftBandsAndCrown() {
		Set<Integer> shaftBands = new HashSet<>();
		int[] withCrown = {0}, withSetbacks = {0}, total = {0};
		sweep(150, 320, (sp, m) -> {
			total[0]++;
			Mass main = m.main();
			assertEquals("main", main.kind());
			assertEquals(0, main.from());
			assertTrue(main.to() >= 1 && main.to() <= 3, "podium storeys " + main.to());
			assertEquals(5, m.grid().height(0), "ground storey 5 high");
			List<Mass> shafts = m.masses().stream().filter(a -> a.kind().equals("shaft")).toList();
			assertFalse(shafts.isEmpty());
			for (int i = 0; i < shafts.size() - 1; i++) {
				int len = shafts.get(i).to() - shafts.get(i).from();
				assertTrue(len >= 8 && len <= 20, "interior band of " + len + " storeys");
				shaftBands.add(len);
			}
			if (shafts.size() > 1) withSetbacks[0]++;
			Mass last = m.masses().get(m.masses().size() - 1);
			if (last.kind().equals("crown")) {
				withCrown[0]++;
				assertTrue(last.to() - last.from() <= 3);
			}
			// insets 1..4 per side between neighbouring tiers
			for (int i = 1; i < m.masses().size(); i++) {
				Mass a = m.masses().get(i), p = m.masses().get(i - 1);
				for (int in : new int[] {a.x() - p.x(), a.z() - p.z(), p.maxX() - a.maxX(), p.maxZ() - a.maxZ()}) assertTrue(in >= 0 && in <= 4, "inset " + in);
			}
		});
		assertTrue(shaftBands.size() >= 8, "band lengths vary: " + shaftBands);
		assertTrue(withSetbacks[0] > total[0] / 3, "most tall towers step back more than once: " + withSetbacks[0] + "/" + total[0]);
		assertTrue(withCrown[0] > total[0] * 9 / 10, "crowns: " + withCrown[0] + "/" + total[0]);
	}

	@Test
	void oneCoreServesEveryStorey() {
		sweep(40, 320, (sp, m) -> {
			if (m.grid().count() < 2) return;
			assertEquals(1, m.cores().size());
			Core k = m.cores().get(0);
			assertEquals(0, k.from());
			assertEquals(m.grid().count(), k.to());
		});
	}

	@Test
	void lowCeilingStillBuildsSomethingValid() {
		for (int maxH : new int[] {6, 7, 9, 12, 20, 33}) {
			for (long seed = 0; seed < 20; seed++) {
				BuildingSpec sp = spec(seed, 18, 18, 40, maxH);
				Massing m = mass(sp);
				assertTrue(m.grid().y(m.grid().count()) + 2 <= maxH, "maxHeight " + maxH);
			}
		}
	}

	@Test
	void shellRespectsHeightCapAndIsFast() {
		List<Long> nanos = new ArrayList<>();
		for (int[] e : new int[][] {{18, 18}, {42, 42}}) {
			for (long seed = 0; seed < 12; seed++) {
				BuildingSpec sp = spec(seed, e[0], e[1], 60, 255);
				long t0 = System.nanoTime();
				BuildingIr ir = ShellGeneratorV1.generate(sp);
				nanos.add(System.nanoTime() - t0);
				assertTrue(ir.bounds().maxY() <= sp.maxHeight(), "bounds " + ir.bounds() + " vs " + sp.maxHeight());
				for (Volume v : ir.volumes()) assertTrue(v.box().maxY() <= sp.maxHeight());
				assertTrue(ir.storeys().size() >= 20);
			}
		}
		nanos.sort(null);
		long p95 = nanos.get(nanos.size() * 95 / 100);
		System.out.println("highrise shell p95 " + p95 / 1_000_000 + " ms, max " + nanos.get(nanos.size() - 1) / 1_000_000 + " ms");
		assertTrue(p95 < 2_000_000_000L, "shell p95 " + p95 / 1_000_000 + " ms");
	}
}
