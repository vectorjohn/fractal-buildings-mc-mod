package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.DoorChecks;
import dev.fractalbuildings.core.check.DoorRise;
import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.terrain.SiteGround;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * SHOPLOW S2 (DS-45): shops, workshops and houses dig in to the street instead of standing at the highest ground under their footprint. The cut
 * and approach boxes are the site boxes {@link SiteLevel} makes; here they are checked from the scene alone.
 */
class DigInTest {
	static GenRequest request(String type, TerrainProbe t, Facing f, long seed, int w, int d, Brief brief) {
		boolean alongX = f.ordinal() % 2 == 0;
		return new GenRequest(seed, new Lot("lot0", 3, -5, alongX ? w : d, alongX ? d : w, f), type, "medieval", t, ProbeSpecs.parse("flat:64").biome(), brief);
	}

	static SceneDoc gen(String type, String terrain, Facing f, long seed, int w, int d) { return new GeneratorV1().generate(request(type, ProbeSpecs.parse(terrain).terrain(), f, seed, w, d, null)); }

	/** Whether the building stands on its ground storey (a market hall on legs does not: its trade floor is the open ground). */
	static boolean groundStorey(PlacedBuilding b) { return InstancePassV1.entranceStorey(b.shell()).y() == 0; }

	record Case(String what, SceneDoc scene, TerrainProbe terrain) {}

	/** Every case the policy accepts: plane slopes rising towards the back, four fronts. */
	static List<Case> cases(String type, int[] ns, int[][] lots, int seeds) {
		List<Case> out = new ArrayList<>();
		for (int n : ns)
			for (Facing f : Facing.values())
				for (int[] lt : lots)
					for (long seed = 0; seed < seeds; seed++) {
						String terr = "plane:64:0:1:" + n;
						TerrainProbe t = ProbeSpecs.parse(terr).terrain();
						try {
							out.add(new Case(type + " " + terr + " " + f + " " + lt[0] + "x" + lt[1] + " seed " + seed, new GeneratorV1().generate(request(type, t, f, seed, lt[0], lt[1], null)), t));
						} catch (IllegalArgumentException e) { /* the policy rejects this lot */ }
					}
		return out;
	}

	static final int[][] SHOP_LOTS = {{9, 14}, {11, 17}, {11, 20}};

	@Test
	void shopsAndWorkshopsSitWithinTwoOfTheStreet() {
		for (String type : List.of("shop", "workshop")) {
			int dug = 0, flat = 0;
			for (Case c : cases(type, new int[] {4, 6, 9, 12}, SHOP_LOTS, 8)) {
				PlacedBuilding b = c.scene().buildings().get(0);
				assertEquals(List.of(), Invariants.check(c.scene(), c.terrain()), c.what());
				if (!groundStorey(b)) continue; // a market hall
				for (DoorRise.Door d : DoorChecks.doors(c.scene(), b, c.terrain())) {
					if (!d.front()) continue;
					assertTrue(d.rise() <= 2, c.what() + ": front door rise " + d.rise());
					if (d.rise() >= 1) dug++;
					else flat++;
				}
			}
			System.out.println(String.format(Locale.ROOT, "dig-in %s: %d front doors at rise 1-2, %d below", type, dug, flat));
			assertTrue(dug >= 100, type + ": " + dug);
		}
	}

	/** Houses on steep ground: door-rise (a house door at most 5 above the street on every lot within the planner's limit) holds, and a flight over 3 risers breaks at a landing or a corner. */
	@Test
	void housesOnSteepGroundSitWithinTheDefaultLimits() {
		int high = 0, broken = 0, runs = 0;
		for (Case c : cases("house", new int[] {1, 2, 3}, new int[][] {{11, 16}, {12, 14}, {11, 20}, {14, 18}}, 6)) {
			PlacedBuilding b = c.scene().buildings().get(0);
			assertEquals(List.of(), Invariants.check(c.scene(), c.terrain()), c.what());
			for (DoorRise.Door d : DoorChecks.doors(c.scene(), b, c.terrain())) {
				if (!d.front()) continue;
				if (d.rise() >= 4 && d.rise() <= 5) { high++; if (d.run() <= DoorChecks.MAX_RISERS) broken++; }
				if (d.run() > DoorChecks.MAX_RISERS) runs++;
			}
		}
		System.out.println("dig-in house: " + high + " doors at rise 4-5, " + broken + " with no straight run over 3; " + runs + " doors with a longer run (no route with landings fitted the lot)");
		assertTrue(high >= 10, "too few steep houses to mean anything: " + high);
		assertTrue(broken * 10 >= high * 8, "a flight over 3 risers breaks at a landing or a corner: " + broken + " of " + high);
	}

	/** The cut: at most 4 above the standing level, every opening clear of the ground outside it, every earthbound ring column cut, no cut over the shell. */
	@Test
	void theCutIsBoundedAndNoOpeningIsBelowTheGroundOutsideIt() {
		int checked = 0;
		for (String type : List.of("shop", "house", "workshop")) {
			for (Case c : cases(type, new int[] {4, 6, 9}, SHOP_LOTS, 5)) {
				PlacedBuilding b = c.scene().buildings().get(0);
				if (!groundStorey(b)) continue;
				int oy = b.oy();
				TerrainProbe eff = SiteGround.probe(b, c.terrain());
				VoxelGrid shellOnly = VoxelGrid.of(new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), List.of()));
				for (Box s : b.siteBoxes()) {
					if (s.role() != Role.AIR) continue;
					Box3 q = s.box();
					assertTrue(q.maxY() <= 2 + SiteLevel.CUT_MAX, c.what() + ": a cut " + q + " reaches " + (q.maxY() - 1) + " above the standing level");
					for (int y = q.y(); y < q.maxY(); y++)
						for (int z = q.z(); z < q.maxZ(); z++)
							for (int x = q.x(); x < q.maxX(); x++) assertEquals(Role.AIR, shellOnly.get(x, y, z), c.what() + ": a site AIR box overlaps the shell at " + x + "," + y + "," + z);
				}
				Lot lot = c.scene().lots().get(0);
				boolean[][] fp = InstancePassV1.footprint(b.shell());
				var p = b.placement();
				for (int x = -1; x <= fp.length; x++)
					for (int z = -1; z <= fp[0].length; z++) { // the ring: every earthbound column in the lot beside the footprint is cut down to the well floor
						boolean in = x >= 0 && z >= 0 && x < fp.length && z < fp[0].length;
						if (in && fp[x][z]) continue;
						boolean near = false;
						for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) { int ax = x + dx, az = z + dz; if (ax >= 0 && az >= 0 && ax < fp.length && az < fp[0].length && fp[ax][az]) near = true; }
						int wx = p.worldX(x, z), wz = p.worldZ(x, z);
						if (!near || !lot.containsColumn(wx, wz) || shellOnly.get(x, -1, z) != Role.AIR) continue;
						int raw = c.terrain().floorY(wx, wz);
						boolean clear = true; // a cell the shell reaches into the earth (an overhang) splits the cut
						for (int y = 0; y < raw - oy; y++) clear &= shellOnly.get(x, y, z) == Role.AIR;
						if (raw >= oy + 2 && clear) assertEquals(oy, eff.floorY(wx, wz), c.what() + ": ring column " + x + "," + z + " (ground " + (raw - oy) + " above the slab) is not cut");
					}
				for (Opening o : b.shell().openings()) { // no window or door is below the ground outside it
					if (o.kind() != OpeningKind.DOOR && o.kind() != OpeningKind.WINDOW) continue;
					if (!o.to().equals(Opening.OUTSIDE) && !o.from().equals(Opening.OUTSIDE)) continue;
					Facing out = o.to().equals(Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2);
					Box3 q = o.box();
					for (int x = q.x(); x < q.maxX(); x++)
						for (int z = q.z(); z < q.maxZ(); z++) {
							int wx = p.worldX(x + out.dx, z + out.dz), wz = p.worldZ(x + out.dx, z + out.dz);
							if (!lot.containsColumn(wx, wz)) continue; // the street: the lot planner's business
							assertTrue(eff.floorY(wx, wz) <= oy + q.y(), c.what() + ": opening " + o.id() + " at y " + q.y() + " is below the ground outside it (" + (eff.floorY(wx, wz) - oy) + ")");
							checked++;
						}
				}
			}
		}
		assertTrue(checked > 500, "openings checked: " + checked);
	}

	/** Flat ground gives the scene the old slab rule gave, byte for byte; ground falling away from the street never puts the slab higher than the old rule did. */
	@Test
	void flatGroundIsUnchangedAndDownhillNeverGoesUp() {
		int same = 0, level = 0;
		for (String type : List.of("shop", "house", "workshop"))
			for (String terr : List.of("flat:64", "plane:64:0:-1:4", "plane:64:0:-1:9", "plane:64:0:-1:2"))
				for (long seed = 0; seed < 10; seed++) {
					TerrainProbe t = ProbeSpecs.parse(terr).terrain();
					SceneDoc now, legacy;
					try {
						now = new GeneratorV1().generate(request(type, t, Facing.NORTH, seed, 11, 17, null));
					} catch (IllegalArgumentException e) { continue; }
					SiteLevel.LEGACY.set(true);
					try { legacy = new GeneratorV1().generate(request(type, t, Facing.NORTH, seed, 11, 17, null)); } finally { SiteLevel.LEGACY.set(false); }
					if (!groundStorey(now.buildings().get(0))) continue; // a market hall's flight follows the stair-run rule now
					assertTrue(now.buildings().get(0).oy() <= legacy.buildings().get(0).oy(), type + " " + terr + " seed " + seed + ": the slab went up");
					level++;
					if (terr.startsWith("flat")) { assertEquals(IrJson.write(legacy), IrJson.write(now), type + " " + terr + " seed " + seed + " differs from the old slab rule"); same++; }
				}
		assertTrue(same >= 15 && level >= 50, "cases " + same + " flat, " + level + " in all");
	}

	/** Uphill the new rule is not the old one (control for the test above). */
	@Test
	void uphillGroundIsNotUnchanged() {
		TerrainProbe t = ProbeSpecs.parse("plane:64:0:1:4").terrain();
		int lower = 0;
		for (long seed = 0; seed < 12; seed++) {
			PlacedBuilding now = new GeneratorV1().generate(request("shop", t, Facing.NORTH, seed, 11, 17, null)).buildings().get(0), legacy;
			if (!groundStorey(now)) continue;
			SiteLevel.LEGACY.set(true);
			try { legacy = new GeneratorV1().generate(request("shop", t, Facing.NORTH, seed, 11, 17, null)).buildings().get(0); } finally { SiteLevel.LEGACY.set(false); }
			assertTrue(now.oy() <= legacy.oy(), "never higher than the old slab");
			if (now.oy() < legacy.oy()) lower++;
		}
		assertTrue(lower >= 5, "shops dug in below the old slab: " + lower);
	}

	/** Water in the cut keeps the old level: no digging into a pond. */
	@Test
	void waterInTheCutKeepsTheOldLevel() {
		TerrainProbe dry = ProbeSpecs.parse("plane:64:0:1:4").terrain();
		TerrainProbe wet = new TerrainProbe() {
			@Override public int floorY(int x, int z) { return dry.floorY(x, z); }
			@Override public int waterDepth(int x, int z) { return z > 0 ? 1 : 0; } // the uphill part of the site stands in water
		};
		for (long seed = 0; seed < 6; seed++) {
			PlacedBuilding d = new GeneratorV1().generate(request("house", dry, Facing.NORTH, seed, 11, 17, null)).buildings().get(0);
			PlacedBuilding w = new GeneratorV1().generate(request("house", wet, Facing.NORTH, seed, 11, 17, null)).buildings().get(0);
			SiteLevel.LEGACY.set(true);
			PlacedBuilding old;
			try { old = new GeneratorV1().generate(request("house", wet, Facing.NORTH, seed, 11, 17, null)).buildings().get(0); } finally { SiteLevel.LEGACY.set(false); }
			assertTrue(d.oy() < old.oy(), "the dry lot digs in (seed " + seed + ")");
			assertEquals(old.oy(), w.oy(), "the wet lot keeps the old slab (seed " + seed + ")");
		}
	}

	/** A street 4 below a plateau that starts at the lot's front edge: the door's outside cell is buried, so the approach is cut as a corridor, and the steps read the cut ground (SiteGround). */
	@Test
	void aBuriedDoorGetsACorridorAndTheStepsReadTheCutGround() {
		int tried = 0;
		for (String type : List.of("shop", "house")) {
			for (long seed = 0; seed < 6; seed++) {
				TerrainProbe t = (x, z) -> z < -5 ? 64 : 68; // the street (z before the lot's front edge) 4 below the plateau the lot stands on
				GenRequest rq = request(type, t, Facing.NORTH, seed, 11, 17, new Brief(1, 3, 1, 0, 64));
				SceneDoc s = new GeneratorV1().generate(rq);
				assertEquals(List.of(), Invariants.check(s, t), type + " seed " + seed);
				PlacedBuilding b = s.buildings().get(0);
				if (!groundStorey(b)) continue; // a market hall
				tried++;
				// the standing check for SiteGround in steps(): the door's way down is found on the cut ground (read raw, the buried ground in front of the door would leave it with none)
				TypeLimits lim = Types.get(type).limits();
				InstancePassV1.Placed placed = InstancePassV1.placeChecked(ShellGeneratorV1.generate(SpecPolicyV1.spec(rq, lim)), rq, lim);
				assertTrue(placed.guarded(), type + " seed " + seed + ": a door has no way down on the cut ground");
				assertTrue(b.siteBoxes().stream().anyMatch(x -> x.role() == Role.PAVING), type + " seed " + seed + ": no corridor paving");
				TerrainProbe eff = SiteGround.probe(b, t);
				int doors = 0;
				for (Opening o : b.shell().openings()) {
					if (o.kind() != OpeningKind.DOOR || !o.to().equals(Opening.OUTSIDE) || b.placement().toWorld(o.facing()) != Facing.NORTH) continue;
					int wx = b.placement().worldX(o.box().x(), o.box().z() - 1), wz = b.placement().worldZ(o.box().x(), o.box().z() - 1);
					assertTrue(eff.floorY(wx, wz) <= b.oy() + 1, type + " seed " + seed + ": the ground in front of the door is " + (eff.floorY(wx, wz) - b.oy()) + " above the slab");
					assertTrue(t.floorY(wx, wz) >= b.oy() + 2, "the plateau stands above the sill (a buried door without the corridor)");
					doors++;
				}
				assertTrue(doors > 0, type + " seed " + seed + ": no front door");
			}
		}
		assertTrue(tried >= 6, "buried-door cases: " + tried);
	}

	/** Interior review: no eave closes a cut ring cell (a covered 1-wide trench): the earth's top block over a cut ring column is never a shell block. */
	@Test
	void noEaveClosesTheWell() {
		int checked = 0, closed = 0;
		for (Case c : cases("house", new int[] {2, 3}, new int[][] {{20, 16}, {14, 12}}, 25)) { // (45-degree slopes are beyond the planner's limit: the rise cap keeps the slab low there and an eave can stay)
			PlacedBuilding b = c.scene().buildings().get(0);
			if (!groundStorey(b)) continue;
			int oy = b.oy();
			boolean[][] fp = InstancePassV1.footprint(b.shell());
			VoxelGrid shellOnly = VoxelGrid.of(new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), List.of()));
			Lot lot = c.scene().lots().get(0);
			var p = b.placement();
			for (int x = -1; x <= fp.length; x++)
				for (int z = -1; z <= fp[0].length; z++) {
					boolean in = x >= 0 && z >= 0 && x < fp.length && z < fp[0].length;
					if (in && fp[x][z]) continue;
					boolean near = false;
					for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) { int ax = x + dx, az = z + dz; if (ax >= 0 && az >= 0 && ax < fp.length && az < fp[0].length && fp[ax][az]) near = true; }
					int wx = p.worldX(x, z), wz = p.worldZ(x, z);
					if (!near || !lot.containsColumn(wx, wz)) continue;
					int top = c.terrain().floorY(wx, wz) - oy - 1; // local y of the earth's top block
					if (top < 1) continue;
					checked++;
					if (shellOnly.get(x, top, z) != Role.AIR) { closed++; if (System.getProperty("fb.dbgeave") != null) System.out.println("EAVE " + c.what() + " oy " + oy + " raw " + (top + oy + 1)); }
				}
		}
		System.out.println("eaves over cut ring columns at the surface: " + closed + " of " + checked + " columns above the slab");
		assertEquals(0, closed, "ring columns whose top earth block is a shell block");
		assertTrue(checked > 300, "columns " + checked);
	}

	/** The door-rise invariant can fail: the old rule puts a shop door high on a slope; a house lot far beyond the planner's limit gets a warning, not a violation. */
	@Test
	void doorRiseCanFailWithinTheContractAndOnlyThere() {
		TerrainProbe t = ProbeSpecs.parse("plane:64:0:1:9").terrain();
		int flagged = 0;
		SiteLevel.LEGACY.set(true);
		try {
			for (long seed = 0; seed < 20; seed++) {
				SceneDoc s = new GeneratorV1().generate(request("shop", t, Facing.NORTH, seed, 11, 25, null));
				if (!groundStorey(s.buildings().get(0))) continue;
				if (Invariants.check(s, t).stream().anyMatch(v -> v.invariant().equals("door-rise"))) flagged++;
			}
		} finally { SiteLevel.LEGACY.set(false); }
		assertTrue(flagged >= 3, "the old slab breaks door-rise on a shop lot within the limit: " + flagged);
		TerrainProbe steep = ProbeSpecs.parse("plane:64:0:1:1").terrain();
		int warned = 0;
		for (long seed = 0; seed < 8; seed++) {
			SceneDoc s = new GeneratorV1().generate(request("house", steep, Facing.NORTH, seed, 14, 18, null));
			assertTrue(Invariants.check(s, steep).stream().noneMatch(v -> v.invariant().equals("door-rise")), "a house on a 45-degree slope is beyond the planner's limit: a warning, not a violation");
			if (groundStorey(s.buildings().get(0)) && dev.fractalbuildings.core.check.DoorChecks.warnings(s, steep).stream().anyMatch(v -> v.invariant().equals("door-rise"))) warned++;
		}
		assertTrue(warned >= 3, "houses beyond the limit are listed as warnings: " + warned);
	}
}
