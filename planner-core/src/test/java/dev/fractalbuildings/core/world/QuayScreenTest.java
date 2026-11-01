package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Districts;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * QUAYFAST step 2: the bank's cheap screen ({@code Bank.maybeOk}: waterfront tile, urban, waterfront quarter; density and quarter fields
 * only) never rejects a column the full quay test accepts, and reads no exact terrain. Recorded terrain, counts only.
 */
class QuayScreenTest {

	/** The settlements of the plan's list that are quick to open. */
	static final List<String> QUICK = List.of("w12345-s1_m5", "pt1573-s5_m4", "w12345-sm1_m4", "w987-gate-coast-city");

	/** Runs the checks on one site; the result counts [reference quay columns, sampled columns, sampled ok, sampled screened out]. */
	static int[] check(String label) {
		QuaySites s = QuaySites.of(label);
		QuaySites.Open o = s.open();
		SkeletonCounting c = new SkeletonCounting(o);
		Districts.QuayColumns cols = Districts.quayColumns(c.src, c.probe);
		assertTrue(cols != null, label + ": has a waterfront tile");
		c.probe.chunks.clear();
		int quayCols = 0;
		for (String run : Districts.allQuayRuns(o.reference(), o.exact())) {
			for (String col : run.split(" ")) {
				String[] xz = col.substring(0, col.indexOf(':')).split(",");
				int x = Integer.parseInt(xz[0]), z = Integer.parseInt(xz[1]);
				assertTrue(cols.ok(x, z), label + ": reference quay column " + col + " passes the full test");
				assertTrue(cols.maybeOk(x, z), label + ": reference quay column " + col + " passes the screen");
				quayCols++;
			}
		}
		Random rnd = new Random(label.hashCode());
		int sampled = 0, ok = 0, out = 0;
		for (DistrictGrid.TileId t : o.waterfront()) {
			DistrictGrid.Tile tile = o.source().grid().tile(t.ix(), t.iz());
			for (int k = 0; k < 400; k++) {
				int x = tile.minX() + rnd.nextInt(tile.maxX() - tile.minX() + 1), z = tile.minZ() + rnd.nextInt(tile.maxZ() - tile.minZ() + 1);
				boolean full = cols.ok(x, z), maybe = cols.maybeOk(x, z);
				sampled++;
				if (full) {
					ok++;
					assertTrue(maybe, label + ": column " + x + "," + z + " is ok but the screen rejects it");
				}
				if (!maybe) out++;
			}
		}
		// the screen alone reads no terrain
		c.probe.chunks.clear();
		for (DistrictGrid.TileId t : o.waterfront()) {
			DistrictGrid.Tile tile = o.source().grid().tile(t.ix(), t.iz());
			for (int x = tile.minX(); x <= tile.maxX(); x += 7) for (int z = tile.minZ(); z <= tile.maxZ(); z += 7) cols.maybeOk(x, z);
		}
		assertEquals(0, c.probe.chunks.size(), label + ": maybeOk read exact terrain");
		System.out.println("QuayScreenTest " + label + ": " + quayCols + " reference quay columns, " + sampled + " sampled, " + ok + " ok, " + out + " screened out");
		return new int[] {quayCols, sampled, ok, out};
	}

	/** A source whose exact probe counts chunks. */
	record SkeletonCounting(dev.fractalbuildings.core.settlement.SkeletonSource.Direct src, QuayLazyTest.Counting probe) {
		SkeletonCounting(QuaySites.Open o) { this(o.fresh(), new QuayLazyTest.Counting(o.exact())); }
	}

	@Test
	void everyQuayColumnPassesTheScreen() {
		int totalQuay = 0, totalOk = 0, totalOut = 0;
		for (String l : QUICK) {
			int[] r = check(l);
			totalQuay += r[0];
			totalOk += r[2];
			totalOut += r[3];
		}
		assertTrue(totalQuay > 100, "the reference has quay columns to test: " + totalQuay);
		assertTrue(totalOk > 100, "the sample has ok columns: " + totalOk);
		assertTrue(totalOut > 0, "the screen rejects some columns (it is not a constant true)");
	}

	@Test
	@Tag("heavy")
	void everyQuayColumnPassesTheScreenOnTheMetropolises() {
		for (QuaySites s : QuaySites.all()) if (s.heavy()) check(s.label());
	}
}
