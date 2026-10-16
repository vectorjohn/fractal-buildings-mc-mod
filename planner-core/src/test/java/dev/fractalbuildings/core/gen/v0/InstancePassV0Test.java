package dev.fractalbuildings.core.gen.v0;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.*;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InstancePassV0Test {
	static GenRequest req(Lot lot, TerrainProbe t) { return new GenRequest(7, lot, "plain", t, (x, z) -> "plains"); }

	@Test
	void rotationFollowsFrontAndFootprintStaysInsideWithMargin() {
		int[][] lots = {{7, 7}, {9, 11}, {31, 17}, {17, 31}, {15, 8}, {8, 15}};
		for (Facing f : Facing.values()) {
			for (int[] l : lots) {
				for (long seed = 0; seed < 30; seed++) {
					Lot lot = new Lot("lot0", -13 + (int) seed, 40 - (int) seed, l[0], l[1], f);
					GenRequest rq = new GenRequest(seed, lot, "plain", (x, z) -> 64, (x, z) -> "plains");
					PlacedBuilding b = InstancePassV0.place(ShellGeneratorV0.generate(SpecPolicyV0.spec(rq)), rq);
					assertEquals(f.ordinal(), b.rotation());
					Placement p = b.placement();
					Box3 fp = p.toWorld(b.shell().bounds());
					assertTrue(fp.x() >= lot.x() + 1 && fp.maxX() <= lot.x() + lot.w() - 1, "x margin " + fp + " " + lot);
					assertTrue(fp.z() >= lot.z() + 1 && fp.maxZ() <= lot.z() + lot.d() - 1, "z margin " + fp + " " + lot);
					assertEquals(Facing.NORTH.rotate(f.ordinal()), p.toWorld(Facing.NORTH));
				}
			}
		}
	}

	@Test
	void foundationsFillDownToTheTerrainOnASlope() {
		TerrainProbe slope = (x, z) -> 60 + (x + 100) / 3 + (z + 100) / 5;
		for (Facing f : Facing.values()) {
			Lot lot = new Lot("lot0", 0, 0, 14, 12, f);
			GenRequest rq = req(lot, slope);
			PlacedBuilding b = InstancePassV0.place(ShellGeneratorV0.generate(SpecPolicyV0.spec(rq)), rq);
			Placement p = b.placement();
			int max = Integer.MIN_VALUE;
			int sx = b.shell().bounds().sx(), sz = b.shell().bounds().sz();
			for (int lx = 0; lx < sx; lx++) for (int lz = 0; lz < sz; lz++) max = Math.max(max, slope.floorY(p.worldX(lx, lz), p.worldZ(lx, lz)));
			assertEquals(max, b.oy());
			Set<String> filled = new HashSet<>();
			for (Box box : b.siteBoxes()) {
				assertEquals(Role.FOUNDATION, box.role());
				assertEquals(Layer.FOUNDATION, box.layer());
				assertEquals(-box.box().sy(), box.box().y());
				for (int x = box.box().x(); x < box.box().maxX(); x++) {
					int wx = p.worldX(x, box.box().z()), wz = p.worldZ(x, box.box().z());
					assertEquals(b.oy() + box.box().y(), slope.floorY(wx, wz), "foundation bottom is the terrain");
					assertTrue(filled.add(x + "," + box.box().z()), "no overlaps");
				}
			}
			int expect = 0;
			for (int lx = 0; lx < sx; lx++) for (int lz = 0; lz < sz; lz++) if (slope.floorY(p.worldX(lx, lz), p.worldZ(lx, lz)) < max) expect++;
			assertEquals(expect, filled.size());
			assertTrue(expect > 0);
		}
	}

	@Test
	void flatTerrainNeedsNoFoundation() {
		GenRequest rq = req(new Lot("lot0", 5, 5, 12, 12, Facing.EAST), (x, z) -> 70);
		PlacedBuilding b = InstancePassV0.place(ShellGeneratorV0.generate(SpecPolicyV0.spec(rq)), rq);
		assertEquals(70, b.oy());
		assertTrue(b.siteBoxes().isEmpty());
	}
}
