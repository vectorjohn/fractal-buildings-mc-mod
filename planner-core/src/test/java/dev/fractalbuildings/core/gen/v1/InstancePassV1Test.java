package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import org.junit.jupiter.api.Test;

class InstancePassV1Test {
	@Test
	void envelopeSitsAtTheMarginsForEveryFront() {
		for (Facing f : Facing.values())
			for (int[] wd : new int[][] {{12, 10}, {31, 17}, {9, 22}}) {
				Lot lot = new Lot("lot0", -37, 15, wd[0], wd[1], f);
				GenRequest rq = new GenRequest(5, lot, "house", "plain", SyntheticTerrain.flat(64), (x, z) -> "minecraft:plains");
				BuildingSpec sp = SpecPolicyV1.spec(rq);
				PlacedBuilding b = InstancePassV1.place(ShellGeneratorV1.generate(sp), rq);
				Placement p = b.placement();
				int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
				for (int lx = 0; lx < sp.width(); lx++)
					for (int lz = 0; lz < sp.depth(); lz++) {
						minX = Math.min(minX, p.worldX(lx, lz));
						maxX = Math.max(maxX, p.worldX(lx, lz));
						minZ = Math.min(minZ, p.worldZ(lx, lz));
						maxZ = Math.max(maxZ, p.worldZ(lx, lz));
					}
				// house margins are 1 on every side: the envelope is the lot shrunk by one
				assertEquals(lot.x() + 1, minX, f + " " + wd[0]);
				assertEquals(lot.x() + lot.w() - 2, maxX, f + " " + wd[0]);
				assertEquals(lot.z() + 1, minZ, f + " " + wd[0]);
				assertEquals(lot.z() + lot.d() - 2, maxZ, f + " " + wd[0]);
				// local front (z = 0) is on the lot's front side
				int fx = p.worldX(sp.width() / 2, 0), fz = p.worldZ(sp.width() / 2, 0);
				int bx = p.worldX(sp.width() / 2, sp.depth() - 1), bz = p.worldZ(sp.width() / 2, sp.depth() - 1);
				assertTrue((bx - fx) * f.dx + (bz - fz) * f.dz < 0, "front faces " + f);
			}
	}

	@Test
	void foundationsOnlyUnderTheGroundFootprint() {
		Lot lot = new Lot("lot0", 0, 0, 30, 30, Facing.NORTH);
		GenRequest rq = new GenRequest(3, lot, "house", "plain", SyntheticTerrain.plane(64, 1, 0, 2), (x, z) -> "minecraft:plains");
		PlacedBuilding b = InstancePassV1.place(ShellGeneratorV1.generate(SpecPolicyV1.spec(rq)), rq);
		boolean[][] fp = InstancePassV1.footprint(b.shell());
		assertTrue(b.siteBoxes().size() > 0);
		for (Box s : b.siteBoxes())
			for (int x = s.box().x(); x < s.box().maxX(); x++) assertTrue(fp[x][s.box().z()], "foundation outside the footprint at " + x);
	}

	@Test
	void maxHeightKeepsTheBuildingUnderTheCeiling() {
		Lot lot = new Lot("lot0", 0, 0, 20, 20, Facing.NORTH);
		GenRequest rq = new GenRequest(3, lot, "house", "plain", SyntheticTerrain.flat(306), (x, z) -> "minecraft:plains");
		BuildingSpec sp = SpecPolicyV1.spec(rq);
		assertEquals(320 - 306, sp.maxHeight());
		PlacedBuilding b = InstancePassV1.place(ShellGeneratorV1.generate(sp), rq);
		assertTrue(b.oy() + b.shell().bounds().maxY() - 1 <= 319);
	}
}
