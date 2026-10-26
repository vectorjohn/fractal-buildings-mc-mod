package dev.fractalbuildings.core.gen.v1.types.highrise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tower massing (plan highrises.md, B1): programme heights, the core block in every tier, the stair house. */
class HighriseMassingTest {
	static final int[] LOTS = {20, 28, 36, 44};
	static final int[] MAX_HEIGHTS = {120, 200, 256};
	static final int SEEDS = 200;

	record Built(BuildingSpec sp, BuildContext c, Massing m, int sd) {}

	interface Each { void at(Built b); }

	static Built build(int lot, long seed, int maxH, int sd) {
		Draws r = new Draws(seed * 7919 + lot * 31L + maxH);
		BuildingSpec sp = new BuildingSpec(GeneratorV1.GEN_VERSION, seed, "plain", HighriseType.ID, lot, lot, r.range(10, 60), "auto", maxH, 1);
		HighriseType t = new HighriseType();
		BuildContext c = new BuildContext(sp, t, StyleLibrary.get(sp.style()));
		return new Built(sp, c, HighriseMassing.build(c, sd), sd);
	}

	static void sweep(Each each) {
		for (int sd : new int[] {3, 4})
			for (int lot : LOTS)
				for (int maxH : MAX_HEIGHTS)
					for (long seed = 0; seed < SEEDS; seed++) each.at(build(lot, seed, maxH, sd));
	}

	static String why(Built b) { return "lot " + b.sp.width() + " seed " + b.sp.specSeed() + " maxH " + b.sp.maxHeight() + " sd " + b.sd + " storeys " + b.sp.storeys(); }

	@Test
	void massingRulesHoldAndFallbackIsRare() {
		int[] total = {0}, fallback = {0};
		sweep(b -> {
			total[0]++;
			assertEquals(List.of(), MassingCheck.check(b.m, b.sp.width(), b.sp.depth(), b.sp.maxHeight()), why(b));
			if (b.m.cores().isEmpty()) fallback[0]++;
		});
		System.out.println("tower massing fallbacks (no core): " + fallback[0] + "/" + total[0]);
		assertTrue(fallback[0] * 100 <= total[0] * 2, "fallback boxes " + fallback[0] + "/" + total[0]);
	}

	@Test
	void oneCoreAlongXFromStoreyZeroToTheStairHouse() {
		sweep(b -> {
			if (b.m.cores().isEmpty() || b.m.grid().count() < 3) return;
			assertEquals(1, b.m.cores().size(), why(b));
			Core k = b.m.cores().get(0);
			assertEquals(0, k.from());
			assertEquals(b.m.grid().count(), k.to(), why(b));
			assertEquals(2, k.sz());
			assertEquals(k.sx(), Core.length(b.m.grid(), k.from(), k.to()), why(b));
		});
	}

	@Test
	void everyTierHoldsTheBlockAndTheStairHouseCoversIt() {
		sweep(b -> {
			TowerBlock blk = TowerBlock.of(b.m, b.sd);
			if (blk == null) return;
			List<Mass> ms = b.m.masses();
			Mass stair = ms.get(ms.size() - 1), top = ms.get(ms.size() - 2);
			assertEquals("stairhouse", stair.kind(), why(b));
			for (Mass a : ms) if (a != stair) assertTrue(blk.fits(a), a + " " + blk + " " + why(b));
			for (Mass a : ms) {
				if (a == stair) continue;
				// restated independently of TowerBlock.fits
				int frontRows = (blk.hallZ0() - 1) - (a.z() + 1);
				assertTrue(frontRows >= 3, "band B " + frontRows + " rows in " + a + " " + why(b));
				int behind = a.maxZ() - 1 - (blk.z1() - 1) - 1;
				assertTrue(a.maxZ() == blk.z1() || behind >= 3, "rows behind the block " + behind + " " + why(b));
				int left = blk.x0() - (a.x() + 1), right = a.maxX() - 1 - blk.x1();
				assertTrue(blk.x0() == a.x() || left >= 3, "left gap " + left + " " + why(b));
				assertTrue(blk.x1() == a.maxX() || right >= 3, "right gap " + right + " " + why(b));
			}
			assertEquals(top.id(), stair.parent(), why(b));
			assertEquals(top.to(), stair.from());
			assertEquals(stair.from() + 1, stair.to());
			assertEquals(b.m.grid().count() - 1, stair.from());
			assertEquals(blk.x0(), stair.x(), why(b));
			assertEquals(blk.z0(), stair.z(), why(b));
			assertEquals(blk.width(), stair.sx(), why(b));
			assertEquals(blk.depth(), stair.sz(), why(b));
			// stairwell and shaft sit inside the block, side by side, flush with the landing row
			int[] sw = blk.stairwell(), sh = blk.shaft();
			assertTrue(sw[0] >= blk.x0() + 1 && sh[0] >= blk.x0() + 1 && sw[0] + sw[2] <= blk.x1() - 1 && sh[0] + sh[2] <= blk.x1() - 1, why(b));
			assertEquals(sw[1], sh[1], why(b));
			assertTrue(blk.hallZ0() > top.z() + 3 || blk.bandBDepth(top) >= 3);
			// the top of the stair house plus a roof layer stays under maxHeight
			assertTrue(b.m.grid().y(b.m.grid().count()) + HighriseMassing.ROOF_ROOM <= b.sp.maxHeight(), why(b));
		});
	}

	@Test
	void programmeHeightsAndSpecialFloors() {
		int[] withSpecials = {0}, towers = {0};
		sweep(b -> {
			TowerBlock blk = TowerBlock.of(b.m, b.sd);
			if (blk == null) return;
			int n = b.m.grid().count() - 1;
			TowerProgram p = TowerProgram.of(b.m, TowerProgram.drawsFor(b.c, n));
			assertNotNull(p);
			assertEquals(6, b.m.grid().height(0), why(b));
			assertEquals(4, b.m.grid().height(n), "stair house");
			int specials = 0;
			for (int s = 0; s <= n; s++) {
				assertEquals(p.height(s), b.m.grid().height(s), why(b) + " storey " + s);
				if (p.special(s)) {
					specials++;
					assertEquals(6, b.m.grid().height(s));
					assertFalse(p.special(s + 1) || s == 1 || p.special(s - 1), "adjacent specials / next to the lobby " + why(b));
					assertTrue(s < n);
				} else if (s > 0 && s < n) assertEquals(4, b.m.grid().height(s));
			}
			towers[0]++;
			if (specials > 0) withSpecials[0]++;
			if (n < 12) assertEquals(0, specials, why(b));
			else if (n < 20) assertTrue(specials <= 1, why(b));
			else if (n < 36) assertTrue(specials <= 2, why(b));
			else assertTrue(specials <= 3, why(b));
			// candidates: first above the podium, first of a later tier, last below the crown (or the top storey)
			List<Integer> cand = new ArrayList<>();
			cand.add(b.m.main().to());
			Mass last = null;
			for (Mass a : b.m.masses()) {
				if (a.kind().equals("shaft")) cand.add(a.from());
				if (a.kind().equals("crown")) cand.add(a.from() - 1);
				if (!a.kind().equals("stairhouse")) last = a;
			}
			if (!last.kind().equals("crown")) cand.add(n - 1);
			for (int s = 1; s < n; s++) if (p.special(s)) assertTrue(cand.contains(s), "special storey " + s + " not a candidate " + cand + " " + why(b));
		});
		assertTrue(withSpecials[0] * 100 > towers[0] * 40, "specials appear: " + withSpecials[0] + "/" + towers[0]);
	}

	@Test
	void facadeBandKeysCoverTierAndSpecialStoreysAlike() {
		sweep(b -> {
			if (b.m.cores().isEmpty()) return;
			int n = b.m.grid().count() - 1;
			TowerProgram p = TowerProgram.of(b.m, TowerProgram.drawsFor(b.c, n));
			assertEquals(0, p.band(0));
			for (int s = 1; s < n; s++) {
				int idx = -1;
				for (int i = 0; i < b.m.masses().size(); i++) if (b.m.masses().get(i).spans(s)) idx = i;
				Mass m = b.m.masses().get(idx);
				assertEquals(s == m.to() - 1 ? TowerProgram.TOP_KEY : 100 + idx, p.band(s), why(b) + " storey " + s);
			}
		});
	}

	@Test
	void deterministic() {
		for (int sd : new int[] {3, 4})
			for (long seed = 0; seed < 30; seed++) assertEquals(build(28, seed, 256, sd).m, build(28, seed, 256, sd).m);
	}

	@Test
	void wideBandBlockLeftOutIsRejected() {
		// control: a 2-deep street band must not fit (the mutation the plan asks for)
		TowerBlock b = new TowerBlock(14, 20, 7, true, 2, 3);
		Mass tall = new Mass("t", "main", 0, 0, 30, 23, 0, 1, null, false);
		assertTrue(b.fits(tall)); assertEquals(14, b.bandBDepth(tall));
		Mass shallowFront = new Mass("t", "main", 0, 12, 30, 12, 0, 1, null, false);
		assertFalse(new TowerBlock(14, 20, 7, true, 2, 3).fits(shallowFront));
	}
}
