package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.raster.PieceRaster;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.terrain.CutFixture;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * SHOPLOW S5a: every cell of a dug-in building's volumes between the slab and the old ground is claimed by the building's own pieces
 * ({@link PieceRaster#building}: solid, or air from a room part or a site box). A cell nobody claims keeps the hill's earth, and a
 * room or an areaway would be filled with stone in the world.
 */
class BuriedRasterTest {
	static final Box3 ALL = Box3.of(-5000, -100, -5000, 10000, 600, 10000);

	/** The buried cells of the building that the raster does not emit, as "x,y,z" (world). */
	static List<String> unclaimed(PlacedBuilding b, TerrainProbe raw) {
		Set<String> emitted = new HashSet<>();
		PieceRaster.building(b, ALL, (x, y, z, r, id, s) -> emitted.add(x + "," + y + "," + z));
		return missing(b, raw, emitted);
	}

	static List<String> missing(PlacedBuilding b, TerrainProbe raw, Set<String> emitted) {
		Placement p = b.placement();
		BuildingIr shell = b.shell();
		List<String> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Volume v : shell.volumes()) {
			Box3 q = v.box();
			for (int x = q.x(); x < q.maxX(); x++)
				for (int z = q.z(); z < q.maxZ(); z++) {
					int wx = p.worldX(x, z), wz = p.worldZ(x, z);
					int top = raw.floorY(wx, wz); // the first free cell above the old ground
					for (int y = Math.max(1, q.y()); y < q.maxY() && b.oy() + y < top; y++) {
						String key = wx + "," + (b.oy() + y) + "," + wz;
						if (!emitted.contains(key) && seen.add(key)) out.add(key);
					}
				}
		}
		return out;
	}

	@Test
	void cutFixtureLeavesNothingBuried() {
		SceneDoc s = CutFixture.scene(true);
		PlacedBuilding b = s.buildings().get(0);
		int surface = b.oy() + CutFixture.DEPTH;
		List<String> bad = unclaimed(b, (x, z) -> surface);
		assertEquals(List.of(), bad);
		// control: the fixture has buried cells to check
		Set<String> none = new HashSet<>();
		assertTrue(missing(b, (x, z) -> surface, none).size() > 40, "the fixture should bury many cells");
	}

	@Test
	void dugInShopsAndHousesLeaveNothingBuried() {
		int checkedCells = 0, withBuried = 0, cases = 0;
		for (String type : List.of("shop", "house", "workshop")) {
			for (DigInTest.Case c : DigInTest.cases(type, new int[] {4, 6, 9}, DigInTest.SHOP_LOTS, 3)) {
				PlacedBuilding b = c.scene().buildings().get(0);
				cases++;
				List<String> bad = unclaimed(b, c.terrain());
				assertEquals(List.of(), bad.size() > 8 ? bad.subList(0, 8) : bad, c.what() + ": " + bad.size() + " buried cells nobody claims");
				Set<String> all = new HashSet<>();
				// control for the next test: how many cells the building buries at all
				int n = missing(b, c.terrain(), all).size();
				checkedCells += n;
				if (n > 0) withBuried++;
			}
		}
		System.out.println("buried raster: " + cases + " cases, " + withBuried + " bury something, " + checkedCells + " cells checked");
		assertTrue(withBuried >= 50 && checkedCells > 2000, "too little buried to mean anything: " + withBuried + " / " + checkedCells);
	}

	/** Mutation controls: without the room parts' own clear, the check fails. */
	@Test
	void theCheckCanFail() {
		int noCut = 0, noRooms = 0;
		for (DigInTest.Case c : DigInTest.cases("house", new int[] {4, 6}, DigInTest.SHOP_LOTS, 3)) {
			PlacedBuilding b = c.scene().buildings().get(0);
			if (!unclaimed(b, c.terrain()).isEmpty()) continue;
			List<Box> site = new ArrayList<>();
			for (Box s : b.siteBoxes()) if (s.role() != Role.AIR) site.add(s);
			PlacedBuilding cutless = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), site);
			if (!unclaimed(cutless, c.terrain()).isEmpty()) noCut++;
			BuildingIr s = b.shell();
			BuildingIr roomless = new BuildingIr(s.spec(), s.bounds(), s.storeys(), s.volumes(), List.of(), s.openings(), s.roofs(), s.boxes(), s.hooks());
			PlacedBuilding noRoom = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), roomless, b.siteBoxes());
			if (!unclaimed(noRoom, c.terrain()).isEmpty()) noRooms++;
		}
		System.out.println("buried raster controls: " + noCut + " cases fail without the cut boxes");
		assertTrue(noRooms >= 5, "dropping the room clear should leave cells unclaimed: " + noRooms);
	}
}
