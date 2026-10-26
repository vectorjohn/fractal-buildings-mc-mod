package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DS-33 (7.12): windows are generous. The glazing measurement, and the window-size invariant it backs. */
class GlazingTest {
	private static SceneDoc scene(String type, long seed, String style, int w, int d) {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:70");
		return new GeneratorV1().generate(new GenRequest(seed, new Lot("lot0", 0, 0, w, d, Facing.NORTH), type, style, p.terrain(), p.biome()));
	}

	/** Every style's windows are 2 tall: no building has 1-tall windows, and 1x1 stays an accent (the invariant). */
	@Test
	void everyStyleAndTypeMeetsWindowSize() {
		int[][] lots = {{14, 12}, {20, 16}};
		for (String type : List.of("house", "shop", "workshop", "hall", "tower", "block", "highrise", "temple"))
			for (String style : StyleLibrary.ids())
				for (int[] lot : lots)
					for (long seed = 1; seed <= 6; seed++) {
						SceneDoc s;
						try { s = scene(type, seed, style, lot[0], lot[1]); } catch (RuntimeException e) { continue; } // lot too small for the type
						for (Violation v : Invariants.check(s))
							assertTrue(!v.invariant().equals(Invariant.WINDOW_SIZE.id), type + " " + style + " " + seed + ": " + v.message());
						for (PlacedBuilding b : s.buildings()) {
							Glazing.Stats g = Glazing.measure(b);
							if (g.windows() >= 4) assertTrue(g.windows2Tall() * 4 >= g.windows() * 3, type + " " + style + " " + seed + ": " + g);
						}
					}
	}

	/** Glass is a real share of the wall: every type above 12 % (before 7.12 they were 7-12 %), houses under 40 %. */
	@Test
	void townAndCommercialTypesAreGlassy() {
		for (String type : List.of("house", "shop", "hall", "highrise", "block")) {
			int[] lot = dev.fractalbuildings.core.gen.v1.Types.get(type).limits().testLots().get(0);
			Glazing.Stats sum = Glazing.Stats.ZERO;
			for (String style : List.of("plains", "medieval", "plain"))
				for (long seed = 1; seed <= 8; seed++)
					for (PlacedBuilding b : scene(type, seed, style, lot[0], lot[1]).buildings()) sum = sum.plus(Glazing.measure(b));
			assertTrue(sum.share() > 0.12, type + " glass share " + sum.share());
			if (type.equals("house")) assertTrue(sum.share() < 0.40, "house glass share " + sum.share());
		}
	}

	/** Lengths of runs of adjacent window columns, one entry per run, grouped by face (facing, fixed coordinate) and row (y); {@code upper} only above the ground storey; {@code facing} null for every face. */
	private static java.util.List<int[]> runs(PlacedBuilding b, Facing facing, boolean upper) {
		int y1 = b.shell().storeys().size() > 1 ? b.shell().storeys().get(1).y() : Integer.MAX_VALUE;
		java.util.TreeMap<String, java.util.TreeSet<Integer>> cols = new java.util.TreeMap<>();
		for (var o : b.shell().openings()) {
			if (o.kind() != dev.fractalbuildings.core.ir.OpeningKind.WINDOW || (facing != null && o.facing() != facing) || (upper && o.box().y() < y1)) continue;
			boolean ns = o.facing() == Facing.NORTH || o.facing() == Facing.SOUTH;
			cols.computeIfAbsent(o.facing() + "/" + (ns ? o.box().z() : o.box().x()) + "/" + o.box().y(), k -> new java.util.TreeSet<>()).add(ns ? o.box().x() : o.box().z());
		}
		java.util.List<int[]> out = new ArrayList<>();
		for (var e : cols.entrySet()) {
			int run = 0, prev = Integer.MIN_VALUE;
			for (int u : e.getValue()) {
				if (u == prev + 1) run++; else { if (run > 0) out.add(new int[] {run, e.getKey().hashCode()}); run = 1; }
				prev = u;
			}
			if (run > 0) out.add(new int[] {run, e.getKey().hashCode()});
		}
		return out;
	}

	/**
	 * DS-33 (FACADE2): a house's street front draws one window variant for all its floors: 2x2 when the inner length is 12 or more (the door and a 2x2 each side), else 1x2. In styles with 2-wide windows, 2x2 is 10-30 % of house windows and at least 80 % of long fronts carry one (medieval draws, so not asserted).
	 */
	@Test
	void housesCarryTwoByTwoWindowsOnTheirFront() {
		for (String style : List.of("plain", "plains", "medieval", "jungle")) {
			Glazing.Stats sum = Glazing.Stats.ZERO;
			int withFront = 0, longFronts = 0;
			for (int[] lot : new int[][] {{14, 12}, {18, 14}, {22, 16}, {26, 18}})
				for (long seed = 1; seed <= 25; seed++)
					for (PlacedBuilding b : scene("house", seed, style, lot[0], lot[1]).buildings()) {
						Glazing.Stats g = Glazing.measure(b);
						sum = sum.plus(g);
						if (b.shell().volumes().get(0).box().sx() - 2 >= 12) { longFronts++; withFront += (int) g.north2x2(); }
					}
			System.out.printf("house %s: 2x2 %.1f %% of windows, %d/%d long fronts with a 2x2%n", style, 100 * sum.share2x2(), withFront, longFronts);
			assertTrue(sum.share2x2() >= 0.10 && sum.share2x2() <= 0.30, style + " 2x2 share " + sum.share2x2());
			if (!style.equals("medieval")) assertTrue(withFront >= 0.8 * longFronts, style + " long fronts with 2x2: " + withFront + "/" + longFronts);
		}
	}

	/** DS-33 (FACADE2 round 2): shop and block upper floors in tallGlass styles are 2-wide windows between 1-cell piers, no glass run wider than 2, glass share of upper floors at least 20 % (blocks; party and stair walls cost some of the 24 % of the full rhythm) and 28 % (shops). */
	@Test
	void shopAndBlockUpperFloorsHavePieredTwoByTwoWindows() {
		for (String type : List.of("shop", "block")) {
			int[] lot = dev.fractalbuildings.core.gen.v1.Types.get(type).limits().testLots().get(0);
			for (String style : List.of("plain", "plains", "tall")) {
				Glazing.Stats sum = Glazing.Stats.ZERO;
				for (long seed = 1; seed <= 10; seed++)
					for (PlacedBuilding b : scene(type, seed, style, lot[0], lot[1]).buildings()) {
						sum = sum.plus(Glazing.measure(b));
						for (int[] r : runs(b, null, true)) assertTrue(r[0] <= 2, type + " " + style + " " + seed + ": glass run " + r[0]);
					}
				System.out.printf("%s %s upper %.1f %%%n", type, style, 100 * sum.upper());
				assertTrue(sum.upper() >= (type.equals("block") ? 0.20 : 0.28), type + " " + style + " upper share " + sum.upper());
			}
		}
	}

	/** DS-39 (FACADE2): upper floors of shops and blocks have sill +1 in every style; halls (and high-rises) alone may have sill 0, and only in tallGlass styles, so the layout must honour the flag. */
	@Test
	void floorToCeilingGlassOnlyOnHallsAndHighrisesInTallGlassStyles() {
		for (String type : List.of("shop", "block", "hall", "highrise"))
			for (String style : List.of("plain", "plains", "medieval", "tall")) {
				int[] lot = dev.fractalbuildings.core.gen.v1.Types.get(type).limits().testLots().get(0);
				int sill0 = 0, upper = 0;
				for (long seed = 1; seed <= 10; seed++)
					for (PlacedBuilding b : scene(type, seed, style, lot[0], lot[1]).buildings()) {
						var storeys = b.shell().storeys();
						for (var o : b.shell().openings()) {
							if (o.kind() != dev.fractalbuildings.core.ir.OpeningKind.WINDOW || Math.max(o.box().sx(), o.box().sz()) > 1) continue; // not the 3-wide arcade arches
							int s = 0;
							for (int i = 0; i < storeys.size(); i++) if (storeys.get(i).y() < o.box().y()) s = i;
							if (s < 1) continue;
							upper++;
							if (o.box().y() == storeys.get(s).y() + 1) sill0++;
						}
					}
				boolean may = (type.equals("hall") || type.equals("highrise")) && StyleLibrary.get(style).facade().tallGlass();
				String msg = type + " " + style + ": " + sill0 + " of " + upper + " upper windows at sill 0";
				if (type.equals("shop") || type.equals("block")) assertEquals(0, sill0, msg);
				else if (type.equals("hall")) { // the high-rise curtain wall is its own program (7.13); a hall's only sill-0 glass in a non-tallGlass style is its 3-wide arcade arches (19 %)
					if (may) assertTrue(sill0 >= 0.8 * upper, msg);
					else assertTrue(sill0 <= 0.3 * upper, msg);
				}
			}
	}

	/** A hand-made wall of six 1x1 or six 1x2 windows: the measurement counts the cells. */
	@Test
	void measuresAHandBuiltFacade() {
		for (int h = 1; h <= 2; h++) {
			PlacedBuilding b = wall(h);
			Glazing.Stats g = Glazing.measure(b);
			assertEquals(6, g.windows());
			assertEquals(6 * h, g.glass());
			assertEquals(h == 1 ? 6 : 0, g.windows1x1());
			assertEquals(h == 1 ? 6.0 / 36 : 12.0 / 36, g.share(), 1e-9);
		}
	}

	/** Floor-to-ceiling glass is a per-style choice (DS-33): only the modern town styles (and the mass-timber towers, which extend tall) have it, never the timber-frame or small-window ones (styles extend plain, which has it). */
	@Test
	void tallGlassOnlyInModernStyles() {
		for (String id : StyleLibrary.ids())
			assertEquals(java.util.Set.of("plain", "plains", "tall").contains(id) || id.startsWith("mass-timber") || id.startsWith("tall-"), StyleLibrary.get(id).facade().tallGlass(), id);
	}

	/** The window-size invariant fires on a wall of 1x1 windows and not on one of 1x2 windows (the other invariants fire on the bare wall either way). */
	@Test
	void windowSizeInvariantFiresOnOneByOneWindowsOnly() {
		for (int h = 1; h <= 2; h++) {
			SceneDoc g = scene("house", 1, "plains", 14, 12);
			SceneDoc s = new SceneDoc(g.genVersion(), g.seed(), g.terrain(), g.lots(), List.of(wall(h)), g.violations());
			boolean fired = Invariants.check(s).stream().anyMatch(v -> v.invariant().equals(Invariant.WINDOW_SIZE.id));
			assertEquals(h == 1, fired, "window height " + h);
		}
	}

	/** A 12 x 3 (wide x high, one storey of height 4 plus slab row) south wall with six windows, a room behind it; open air on the other side. */
	private static PlacedBuilding wall(int winH) {
		List<Box> boxes = new ArrayList<>();
		boxes.add(new Box(Box3.of(0, 0, 0, 12, 1, 3), Role.FLOOR, Layer.SHELL, 0));
		boxes.add(new Box(Box3.of(0, 1, 0, 12, 3, 1), Role.WALL, Layer.SHELL, 0));
		for (int i = 0; i < 6; i++) boxes.add(new Box(Box3.of(1 + 2 * i, 2, 0, 1, winH, 1), Role.WINDOW, Layer.SHELL, 0));
		PlacedBuilding g = scene("house", 1, "plains", 14, 12).buildings().get(0);
		BuildingIr ir = new BuildingIr(g.shell().spec(), Box3.of(0, 0, 0, 12, 4, 3), List.of(new dev.fractalbuildings.core.ir.Storey(0, 0, 4)), List.of(), List.of(), List.of(), List.of(), boxes);
		return new PlacedBuilding("t", g.lots(), 0, 70, 0, 0, ir, List.of());
	}

	/** A7 round 2: bay lengths a style cannot tile (3..4 on an inner length of 5) fall back to narrower bays, so no side of a tall-style high-rise is left without windows. */
	@Test
	void everySideOfATallHighriseHasWindows() {
		int[][] lots = {{17, 17}, {19, 23}, {20, 20}, {24, 20}, {28, 28}, {36, 36}, {11, 13}};
		int towers = 0;
		for (String style : List.of("tall-brick", "tall-stone", "tall-terracotta", "tall-concrete", "mass-timber-oak"))
			for (int[] lot : lots)
				for (long seed = 1; seed <= 8; seed++) {
					SceneDoc s;
					try { s = scene("highrise", seed, style, lot[0], lot[1]); } catch (RuntimeException e) { continue; }
					for (PlacedBuilding b : s.buildings()) {
						if (b.shell().storeys().size() < 7) continue;
						towers++;
						for (Facing f : Facing.values()) {
							boolean any = b.shell().openings().stream().anyMatch(o -> o.facing() == f && o.kind() == dev.fractalbuildings.core.ir.OpeningKind.WINDOW);
							assertTrue(any, style + " " + lot[0] + "x" + lot[1] + " seed " + seed + ": no windows facing " + f);
						}
					}
				}
		assertTrue(towers > 20, "towers measured " + towers);
	}
}
