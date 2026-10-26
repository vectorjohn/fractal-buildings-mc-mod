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

	/** Floor-to-ceiling glass is a per-style choice (DS-33): only the modern town styles have it, never the timber-frame or small-window ones (styles extend plain, which has it). */
	@Test
	void tallGlassOnlyInModernStyles() {
		for (String id : StyleLibrary.ids())
			assertEquals(java.util.Set.of("plain", "plains", "tall").contains(id), StyleLibrary.get(id).facade().tallGlass(), id);
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
}
