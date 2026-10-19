package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import java.util.List;
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
		for (Box s : b.siteBoxes()) {
			boolean under = true; // a wall-slab foundation run, or a front step's single column
			for (int x = s.box().x(); x < s.box().maxX(); x++)
				under &= x >= 0 && s.box().z() >= 0 && x < fp.length && s.box().z() < fp[0].length && fp[x][s.box().z()];
			assertTrue(under || (s.box().sx() == 1 && s.box().sz() == 1), "foundation outside the footprint that is not a step column: " + s);
		}
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

	private static SceneDoc house(long seed, Facing front, String terrain) {
		var probes = dev.fractalbuildings.core.probe.ProbeSpecs.parse(terrain);
		Lot lot = new Lot("lot0", 0, 0, 12, 10, front);
		return new GeneratorV1().generate(new GenRequest(seed, lot, "house", "medieval", probes.terrain(), probes.biome()));
	}

	private static long steps(SceneDoc s) {
		return s.buildings().get(0).siteBoxes().stream().filter(b -> b.role() == Role.STAIR).count();
	}

	private static final String[] SLOPES = {"plane:64:1:0:1", "plane:64:-1:0:1", "plane:64:0:1:1", "plane:64:0:-1:1", "plane:64:1:1:1", "plane:64:-1:1:1", "plane:64:2:0:1"};

	@Test
	void everyDoorReachesTheGroundOnSlopesAndFlatGroundNeedsNoSteps() {
		int withSteps = 0, total = 0;
		for (String terrain : SLOPES)
			for (Facing f : Facing.values())
				for (long seed = 0; seed < 6; seed++) {
					SceneDoc s = house(seed, f, terrain);
					var rq = dev.fractalbuildings.core.probe.ProbeSpecs.parse(terrain).terrain();
					assertEquals(List.of(), Invariants.check(s, rq), terrain + " " + f + " seed " + seed);
					total++;
					if (steps(s) > 0) withSteps++;
				}
		assertTrue(withSteps > total / 3, "steps on only " + withSteps + " of " + total + " sloped houses");
		for (long seed = 0; seed < 6; seed++) assertEquals(0, steps(house(seed, Facing.SOUTH, "flat:70")));
	}

	@Test
	void entranceReachableFiresWhenTheStepsAreMissing() {
		int fired = 0, tried = 0;
		for (String terrain : SLOPES)
			for (Facing f : Facing.values()) {
				SceneDoc s = house(1, f, terrain);
				if (steps(s) == 0) continue;
				tried++;
				PlacedBuilding b = s.buildings().get(0);
				List<Box> site = new java.util.ArrayList<>(b.siteBoxes());
				boolean[][] fp = InstancePassV1.footprint(b.shell());
				site.removeIf(x -> x.box().x() < 0 || x.box().z() < 0 || x.box().x() >= fp.length || x.box().z() >= fp[0].length || !fp[x.box().x()][x.box().z()]); // the whole flight, steps and their foundations
				PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), site);
				SceneDoc m = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), List.of());
				var ids = Invariants.check(m, dev.fractalbuildings.core.probe.ProbeSpecs.parse(terrain).terrain()).stream().map(Violation::invariant).toList();
				if (ids.contains("entrance-reachable")) fired++;
			}
		assertTrue(tried >= 8, "only " + tried + " scenes had steps");
		assertEquals(tried, fired);
	}

	@Test
	void aDoorBuriedInAHillsideGetsALiftedSlab() {
		// the ground in front is higher than the footprint's: the slab rises to it, so the door is not buried
		int lifted = 0;
		for (String terrain : SLOPES)
			for (Facing f : Facing.values()) {
				SceneDoc s = house(2, f, terrain);
				PlacedBuilding b = s.buildings().get(0);
				var tp = dev.fractalbuildings.core.probe.ProbeSpecs.parse(terrain).terrain();
				int max = Integer.MIN_VALUE;
				boolean[][] fp = InstancePassV1.footprint(b.shell());
				for (int x = 0; x < fp.length; x++) for (int z = 0; z < fp[0].length; z++) if (fp[x][z]) max = Math.max(max, tp.floorY(b.placement().worldX(x, z), b.placement().worldZ(x, z)));
				if (b.oy() > max) lifted++;
			}
		assertTrue(lifted > 0);
	}

	@Test
	void everyTypeStaysUnderTheCeilingAtExtremeGround() {
		for (BuildingType t : Types.all()) {
			int[] wd = t.limits().testLots().get(t.limits().testLots().size() / 2);
			for (int y : new int[] {250, 280, 300, 310, 313, 314}) {
				for (int seed = 0; seed < 4; seed++) {
					Facing fr = Facing.values()[seed];
					int[] lt = t.limits().oriented(wd, fr);
					GenRequest rq = new GenRequest(seed, new Lot("lot0", 3, -5, lt[0], lt[1], fr), t.id(), "medieval",
							SyntheticTerrain.flat(y), (x, z) -> "minecraft:plains");
					SceneDoc s = new GeneratorV1().generate(rq);
					PlacedBuilding b = s.buildings().get(0);
					int topBox = Integer.MIN_VALUE;
					for (Box bx : b.shell().boxes()) topBox = Math.max(topBox, b.oy() + bx.box().maxY() - 1);
					assertTrue(topBox <= Invariants.WORLD_MAX_Y, t.id() + " at ground " + y + " reaches y " + topBox);
					assertEquals(List.of(), Invariants.check(s, rq.terrain()), t.id() + " at " + y);
				}
			}
			// no room left: fails cleanly (the CLI turns this into exit 2)
			for (int y : new int[] {315, 316, 319}) {
				GenRequest rq = new GenRequest(1, new Lot("lot0", 0, 0, wd[0], wd[1], Facing.NORTH), t.id(), "plain", SyntheticTerrain.flat(y), (x, z) -> "minecraft:plains");
				assertThrows(IllegalArgumentException.class, () -> new GeneratorV1().generate(rq), t.id() + " at " + y);
			}
		}
	}

	@Test
	void aSlopeUpToTheCeilingKeepsEveryVoxelInWorld() {
		var tp = SyntheticTerrain.plane(300, 1, 0, 2);
		Lot lot = new Lot("lot0", 0, 0, 20, 20, Facing.EAST);
		GenRequest rq = new GenRequest(5, lot, "house", "plain", tp, (x, z) -> "minecraft:plains");
		SceneDoc s = new GeneratorV1().generate(rq);
		assertEquals(List.of(), Invariants.check(s, tp));
		assertTrue(s.buildings().get(0).oy() + s.buildings().get(0).shell().bounds().maxY() - 1 <= 319);
	}
}
