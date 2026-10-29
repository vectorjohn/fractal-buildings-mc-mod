package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 6.19 PORCHSTEP (playtest 2026-10-28: "nice and flush but missing any stairs at all"): the slab stands on the ground, so on flat
 * ground every entrance door's sill is a block above the ground in front of it, and a player cannot walk up a block. The cell
 * in front of every entrance door must hold a step (a stair block at the slab level, or part of a landing), unless the ground there
 * is already at the sill. The same test on a slope.
 */
class PorchStepTest {
	static final String[] TYPES = {"house", "shop", "workshop"};
	static final String[] STYLES = {"medieval", "plains", "taiga", "desert"};

	private static List<String> missing(String terrain, long seeds, int minDoors) {
		List<String> bad = new ArrayList<>();
		ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
		int doors = 0;
		for (String type : TYPES)
			for (String style : STYLES)
				for (Facing f : Facing.values())
					for (long seed = 0; seed < seeds; seed++) {
						GenRequest rq = new GenRequest(seed, new Lot("lot0", 0, 0, f.ordinal() % 2 == 0 ? 14 : 11, f.ordinal() % 2 == 0 ? 11 : 14, f), type, style, p.terrain(), p.biome());
						SceneDoc s;
						try { s = new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { continue; }
						PlacedBuilding b = s.buildings().get(0);
						int level = InstancePassV1.entranceStorey(b.shell()).y();
						var eff = dev.fractalbuildings.core.terrain.SiteGround.probe(b, rq.terrain());
						for (Opening o : InstancePassV1.entranceDoors(b.shell())) {
							if (o.box().y() != level + 1) continue; // a door above the entrance floor (a stilted hall, a balcony) has its own stair
							Facing out = InstancePassV1.outward(o);
							for (int lx = 0; lx < o.box().sx(); lx++) for (int lz = 0; lz < o.box().sz(); lz++) { // every leaf of a double door
							int[] c = {o.box().x() + lx + out.dx, o.box().z() + lz + out.dz};
							int g = eff.floorY(b.placement().worldX(c[0], c[1]), b.placement().worldZ(c[0], c[1]));
							int sill = b.oy() + o.box().y();
							if (g >= sill) continue; // the ground is at the sill: flush
							doors++;
							boolean step = false;
							for (Box x : b.siteBoxes()) if (x.role() == Role.STAIR && x.box().y() == level && x.box().x() <= c[0] && c[0] < x.box().maxX() && x.box().z() <= c[1] && c[1] < x.box().maxZ()) step = true;
							if (!step) { // the cell in front is not a step: only a flight starting beside the door that the plan could not place at the front is allowed, and then the ground must be within a block of a stair
								boolean beside = false;
								for (Box x : b.siteBoxes()) if (x.role() == Role.STAIR && Math.abs(x.box().x() - c[0]) + Math.abs(x.box().z() - c[1]) <= 1) beside = true;
								if (!beside) bad.add(type + " " + style + " " + f + " seed " + seed + " " + o.id() + " sill " + (sill - g) + " over the ground, no step");
							}
							}
						}
					}
		assertTrue(doors > minDoors, "doors " + doors);
		return bad;
	}

	@Test
	void everyDoorOverFlatGroundHasAStep() {
		List<String> bad = missing("flat:70", 4, 200);
		assertEquals(List.of(), bad.subList(0, Math.min(bad.size(), 8)), bad.size() + " doors without a step");
	}

	@Test
	void everyDoorOverASlopeHasAStep() {
		for (String t : new String[] {"plane:64:1:0:3", "plane:64:0:1:2"}) {
			List<String> bad = missing(t, 3, 40);
			assertEquals(List.of(), bad.subList(0, Math.min(bad.size(), 8)), t + ": " + bad.size() + " doors without a step");
		}
	}
}
