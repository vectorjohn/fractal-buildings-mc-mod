package dev.fractalbuildings.core.terrain;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.Rect;
import dev.fractalbuildings.core.terrain.GroundBox.Kind;
import dev.fractalbuildings.core.terrain.LatticePath.Pt;
import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

/**
 * B3: the earth and air a road or plaza leaves around its surface are enough for a live ground that differs from the probed one. In the game
 * the vanilla beardifier (beard_thin on the buildings) lowers the ground near a building up to its 12-block margin and raises it at the
 * shelf of a lower building, so the probe the grading read is wrong by a few blocks right where streets meet buildings. The model here
 * applies the boxes to a column of earth up to a "live" ground that is the probed one shifted by k, and checks what a walker sees.
 */
class GradingSurfaceTest {
	/** The blocks of a column after the boxes: the surface cell, whether earth fills the 12 cells under it, and how many cells above it are free. */
	record Column(boolean surface, int solidBelow, int freeAbove) {}

	/** Applies the boxes to a live ground {@code live} (first free level), reading one column. */
	static Column column(List<GroundBox> boxes, int x, int z, int surfaceY, int live) {
		int lo = surfaceY - 30, hi = surfaceY + 12;
		boolean[] solid = new boolean[hi - lo + 1];
		for (int y = lo; y <= hi; y++) solid[y - lo] = y < live;
		boolean road = false;
		for (GroundBox g : boxes) { // phases as DistrictScenes: earth, air, surface (kinds sort in that order inside one road)
			Box3 b = g.box();
			if (x < b.x() || x >= b.maxX() || z < b.z() || z >= b.maxZ()) continue;
			for (int y = Math.max(b.y(), lo); y < Math.min(b.maxY(), hi + 1); y++) {
				switch (g.kind()) {
					case FILL -> solid[y - lo] = true;
					case CUT -> solid[y - lo] = false;
					default -> { solid[y - lo] = true; if (y == surfaceY) road = true; }
				}
			}
		}
		int below = 0;
		for (int y = surfaceY - 1; y >= surfaceY - Grading.FILL_BELOW && solid[y - lo]; y--) below++;
		int free = 0;
		for (int y = surfaceY + 1; y <= hi && !solid[y - lo]; y++) free++;
		return new Column(road, below, free);
	}

	static void check(TerrainProbe probe, int shift, boolean plaza) {
		List<GroundBox> boxes = plaza
				? Grading.plaza(new Rect(10, 10, 9, 9), probe).orElseThrow().boxes()
				: Grading.road(List.of(new Pt(0, 0), new Pt(40, 0)), 3, probe).boxes();
		int level = plaza ? Grading.plaza(new Rect(10, 10, 9, 9), probe).orElseThrow().level() : -1;
		int cols = 0;
		for (int x = plaza ? 10 : 0; x < (plaza ? 19 : 41); x++) {
			for (int z = plaza ? 10 : -1; z < (plaza ? 19 : 2); z++) {
				int lvlSurface = -1;
				for (GroundBox g : boxes) {
					Box3 b = g.box();
					if ((g.kind() == Kind.ROAD || g.kind() == Kind.PLAZA) && x >= b.x() && x < b.maxX() && z >= b.z() && z < b.maxZ()) lvlSurface = b.y();
				}
				assertTrue(lvlSurface > -1, "surface at " + x + "," + z);
				Column c = column(boxes, x, z, lvlSurface, probe.floorY(x, z) + shift);
				String at = " at " + x + "," + z + " shift " + shift;
				assertTrue(c.surface(), "surface" + at);
				assertEquals(Grading.FILL_BELOW, c.solidBelow(), "earth under the surface" + at);
				assertTrue(c.freeAbove() >= Grading.HEADROOM, "air over the surface " + c.freeAbove() + at);
				cols++;
			}
		}
		assertTrue(cols >= 81);
	}

	@Test
	void roadOnASlopeHasEarthUnderAndAirOverWhateverTheLiveGroundDoes() {
		TerrainProbe slope = SyntheticTerrain.plane(64, 1, 0, 4);
		for (int shift = -11; shift <= 3; shift++) check(slope, shift, false);
	}

	@Test
	void roadInACutHasEarthUnderAndAirOverWhateverTheLiveGroundDoes() {
		TerrainProbe hump = (x, z) -> 70 + (x > 15 && x < 25 ? 3 : 0); // the road (level ~70) is cut into the hump
		for (int shift = -8; shift <= 4; shift++) check(hump, shift, false);
	}

	@Test
	void plazaHasEarthUnderAndAirOver() {
		TerrainProbe t = (x, z) -> 64 + (x + z) % 3;
		for (int shift = -11; shift <= 3; shift++) check(t, shift, true);
	}

	@Test
	void withoutTheRuleTheLiveGroundShowsAGap() {
		// control: boxes that start at the probed ground (the old rule) leave a gap under the road when the live ground is lower
		TerrainProbe flat = SyntheticTerrain.flat(64);
		int surface = 70; // a road 6 blocks over flat ground: fill from 64 to 69 only in the old rule
		List<GroundBox> old = List.of(new GroundBox(Box3.of(0, 64, 0, 1, 6, 1), Kind.FILL), new GroundBox(Box3.of(0, surface, 0, 1, 1, 1), Kind.ROAD));
		assertTrue(column(old, 0, 0, surface, 60).solidBelow() < Grading.FILL_BELOW, "the old rule leaves a gap when the ground is 4 lower");
		assertEquals(64, flat.floorY(0, 0));
	}
}
