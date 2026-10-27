package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
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
 * LOTRAIL (DS-8): a flight or landing that needs a rail on the lot line makes the building move in from the lot line, and a door
 * wider than one cell opens onto a landing as wide as the door. Runs the whole type x test-lot x terrain matrix.
 */
class LotRailTest {
	private static final String[] TERRAINS = {"flat:64", "plane:64:1:0:4", "plane:64:0:1:2", "hill:64:20:24", "noise:7:64:6:32", "plane:64:0:1:9"};

	private record Run(GenRequest rq, SceneDoc scene, boolean setBack) {}

	private static List<Run> matrix() {
		List<Run> runs = new ArrayList<>();
		for (BuildingType t : Types.all()) {
			if (java.util.Set.of("wall", "curtain", "bastion").contains(t.id())) continue;
			boolean gentle = t.limits().maxGrade() > 3;
			for (int[] l0 : t.limits().testLots())
				for (String terrain : TERRAINS) {
					if (gentle && !terrain.startsWith("flat")) continue;
					for (long seed = 0; seed < 6; seed++) {
						int[] l = t.limits().oriented(l0, Facing.NORTH);
						ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
						if (!SpecPolicyV1.railRoomOk(t.id(), t.limits(), relief(p, l), l[0], l[1])) continue; // the lot planner never makes this lot (MINLOT)
						GenRequest rq = new GenRequest(seed, new Lot("lot0", 0, 0, l[0], l[1], Facing.NORTH), t.id(), "medieval", p.terrain(), p.biome());
						SceneDoc s;
						try { s = new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { continue; }
						BuildingSpecBounds b = new BuildingSpecBounds(s.buildings().get(0).shell().bounds().maxX(), s.buildings().get(0).shell().bounds().maxZ());
						boolean set = b.w() < l[0] - 2 * t.limits().side() || b.d() < l[1] - t.limits().front() - t.limits().back();
						runs.add(new Run(rq, s, set));
					}
				}
		}
		return runs;
	}

	static int relief(ProbeSpecs.Probes p, int[] l) {
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int x = 0; x < l[0]; x++) for (int z = 0; z < l[1]; z++) { int y = p.terrain().floorY(x, z); lo = Math.min(lo, y); hi = Math.max(hi, y); }
		return hi - lo;
	}

	private record BuildingSpecBounds(int w, int d) {}

	@Test
	void everyFlightIsRailedInsideTheLotAndInvariantsHold() {
		int total = 0, setBack = 0, unguarded = 0;
		List<String> bad = new ArrayList<>();
		for (Run r : matrix()) {
			total++;
			if (r.setBack()) setBack++;
			PlacedBuilding b = r.scene().buildings().get(0);
			List<String> u = RailingTest.unguardedEdges(b, r.rq());
			if (!u.isEmpty()) { unguarded++; if (bad.size() < 10) bad.add(r.rq().type() + " " + r.rq().lot().w() + "x" + r.rq().lot().d() + " seed " + r.rq().worldSeed() + " " + u.get(0)); }
			assertEquals(List.of(), Invariants.check(r.scene(), r.rq().terrain()), r.rq().type() + " seed " + r.rq().worldSeed());
		}
		System.out.println("LOTRAIL matrix: " + total + " buildings, " + setBack + " set back, " + unguarded + " with an unguarded edge, " + " " + bad);
		assertTrue(total > 500, "only " + total);
		assertEquals(List.of(), bad, "unguarded " + unguarded + " of " + total);
	}

	/** MINLOT: a lot at the type minimum needs more room on ground that needs a railed flight, and needs the room on a slope for a setback. */
	@Test
	void lotsAtTheMinimumNeedRoomOnSlopesAndAreRailedWithIt() {
		for (String type : List.of("house", "tower", "highrise")) {
			TypeLimits lim = Types.get(type).limits();
			for (long seed = 0; seed < 4; seed++) {
				ProbeSpecs.Probes slope = ProbeSpecs.parse("plane:64:0:1:2"), flat = ProbeSpecs.parse("flat:64");
				int w = lim.minLotWidth(), d = lim.minLotDepth();
				org.junit.jupiter.api.Assertions.assertFalse(SpecPolicyV1.railRoomOk(type, lim, relief(slope, new int[] {w, d}), w, d), type);
				org.junit.jupiter.api.Assertions.assertTrue(SpecPolicyV1.railRoomOk(type, lim, relief(slope, new int[] {w + 2, d + 1}), w + 2, d + 1), type);
				org.junit.jupiter.api.Assertions.assertTrue(SpecPolicyV1.railRoomOk(type, lim, 0, w, d), type);
				SceneDoc fs = new GeneratorV1().generate(new GenRequest(seed, new Lot("l", 0, 0, w, d, Facing.NORTH), type, "medieval", flat.terrain(), flat.biome()));
				assertEquals(List.of(), Invariants.check(fs, flat.terrain()), type + " flat seed " + seed);
				SceneDoc s = new GeneratorV1().generate(new GenRequest(seed, new Lot("l", 0, 0, w + 2, d + 1, Facing.NORTH), type, "medieval", slope.terrain(), slope.biome()));
				assertEquals(List.of(), RailingTest.unguardedEdges(s.buildings().get(0), new GenRequest(seed, new Lot("l", 0, 0, w + 2, d + 1, Facing.NORTH), type, "medieval", slope.terrain(), slope.biome())), type + " seed " + seed);
			}
		}
	}

	/** Every cell in front of a door wider than one cell is level with the door or a step down at most, or a landing tread. */
	@Test
	void wideDoorsOpenOntoALandingAsWideAsTheDoor() {
		int wide = 0, withLanding = 0;
		List<String> bad = new ArrayList<>();
		for (Run r : matrix()) {
			PlacedBuilding b = r.scene().buildings().get(0);
			if (InstancePassV1.entranceDoors(b.shell()).isEmpty()) continue;
			int floor = b.oy() + InstancePassV1.entranceStorey(b.shell()).y();
			for (Opening o : InstancePassV1.entranceDoors(b.shell())) {
				Facing f = InstancePassV1.outward(o);
				int w = o.box().sx() * o.box().sz();
				if (w <= 1) continue;
				wide++;
				boolean landing = false;
				for (int x = o.box().x(); x < o.box().maxX(); x++)
					for (int z = o.box().z(); z < o.box().maxZ(); z++) {
						int cx = x + f.dx, cz = z + f.dz;
						int g = dev.fractalbuildings.core.terrain.SiteGround.probe(b, r.rq().terrain()).floorY(b.placement().worldX(cx, cz), b.placement().worldZ(cx, cz));
						int surface = g;
						for (Box s : b.siteBoxes()) if (s.role() == Role.STAIR && s.box().x() == cx && s.box().z() == cz) { surface = Math.max(surface, b.oy() + s.box().y()); landing |= b.oy() + s.box().y() == floor; }
						if (floor - surface > 1 && bad.size() < 10) bad.add(r.rq().type() + " " + r.rq().lot().w() + "x" + r.rq().lot().d() + " seed " + r.rq().worldSeed() + " door " + o.box() + " drop " + (floor - surface));
					}
				if (landing) withLanding++;
			}
		}
		System.out.println("LOTRAIL wide doors: " + wide + ", " + withLanding + " with a landing, bad " + bad);
		assertEquals(List.of(), bad);
	}
}
