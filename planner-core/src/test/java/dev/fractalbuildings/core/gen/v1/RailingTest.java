package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 6.9 railings: {@link Role#RAIL} on the open sides of exterior steps and landings. Rails stand on a kerb beside the flight,
 * never in a column of a step, only where the ground beside the tread is well below it, and they change no walk: the
 * invariants hold with and without them, and a rail put across a step is caught.
 */
class RailingTest {
	private static final String[] SLOPES = {"plane:70:1:0:6", "plane:70:0:1:6", "plane:70:1:1:12", "plane:70:-1:0:6", "plane:70:0:-1:8", "plane:70:0:1:12", "flat:70"};
	private static final String[] TYPES = {"house", "shop", "tower", "workshop", "temple"};

	private static GenRequest request(String type, long seed, String terrain) {
		ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
		return new GenRequest(seed, new Lot("lot0", 0, 0, 18, 18, Facing.NORTH), type, "medieval", p.terrain(), p.biome());
	}

	private static SceneDoc generate(GenRequest rq) {
		try { return new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { return null; } // lot too small or too steep for the type
	}

	private static List<Box> rails(PlacedBuilding b) {
		List<Box> r = new ArrayList<>();
		for (Box x : b.siteBoxes()) if (x.role() == Role.RAIL) r.add(x);
		return r;
	}

	private static SceneDoc without(SceneDoc s, boolean rails) {
		List<PlacedBuilding> bs = new ArrayList<>();
		for (PlacedBuilding b : s.buildings()) {
			List<Box> site = new ArrayList<>();
			for (Box x : b.siteBoxes()) if (!rails || x.role() != Role.RAIL) site.add(x);
			bs.add(new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), site));
		}
		return new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), bs, s.ground(), List.of());
	}

	@Test
	void railsStandBesideStepsOnAKerbAndNeverInTheirWay() {
		int scenes = 0, withRails = 0, total = 0;
		for (String type : TYPES)
			for (String slope : SLOPES)
				for (long seed = 1; seed <= 8; seed++) {
					GenRequest rq = request(type, seed, slope);
					SceneDoc s = generate(rq);
					if (s == null) continue;
					scenes++;
					PlacedBuilding b = s.buildings().get(0);
					List<Box> rails = rails(b);
					if (!rails.isEmpty()) withRails++;
					String what = type + " " + slope + " seed " + seed;
					for (Box r : rails) {
						total++;
						Box3 q = r.box();
						boolean beside = false, kerb = false;
						for (Box x : b.siteBoxes()) {
							Box3 c = x.box();
							if (x.role() == Role.STAIR && c.x() == q.x() && c.z() == q.z()) assertTrue(false, what + ": rail in the column of a step at " + q);
							if (x.role() == Role.STAIR && c.y() == q.y() && Math.abs(c.x() - q.x()) + Math.abs(c.z() - q.z()) == 1) beside = true;
							if (x.role() == Role.FOUNDATION && c.x() == q.x() && c.z() == q.z() && c.maxY() == q.y()) kerb = true;
						}
						assertTrue(beside, what + ": rail " + q + " has no step beside it at its level");
						assertTrue(kerb, what + ": rail " + q + " has no kerb under it");
						int wx = b.placement().worldX(q.x(), q.z()), wz = b.placement().worldZ(q.x(), q.z());
						assertTrue(b.oy() + q.y() - rq.terrain().floorY(wx, wz) >= InstancePassV1.RAIL_DROP_LANDING, what + ": rail on ground level with the tread");
					}
				}
		assertTrue(scenes > 60, "only " + scenes + " scenes generated");
		assertTrue(withRails >= 20 && total >= 50, "rails in only " + withRails + " scenes (" + total + " blocks)");
	}

	@Test
	void railsChangeNoWalk() {
		int checked = 0;
		for (String type : TYPES)
			for (String slope : SLOPES)
				for (long seed = 1; seed <= 8; seed++) {
					GenRequest rq = request(type, seed, slope);
					SceneDoc s = generate(rq);
					if (s == null) continue;
					String what = type + " " + slope + " seed " + seed;
					assertEquals(List.of(), Invariants.check(s, rq.terrain()), what + " with rails");
					assertEquals(List.of(), Invariants.check(without(s, true), rq.terrain()), what + " without rails");
					checked++;
				}
		assertTrue(checked > 60);
	}

	@Test
	void deterministic() {
		GenRequest rq = request("house", 3, "plane:70:1:1:12");
		assertEquals(generate(rq).buildings().get(0).siteBoxes(), generate(rq).buildings().get(0).siteBoxes());
	}

	/** Control: a rail put on the tread of a step (across the line of travel) fails the walk checks. */
	@Test
	void aRailAcrossAStepIsCaught() {
		int tried = 0, failed = 0;
		for (long seed = 1; seed <= 8; seed++) {
			GenRequest rq = request("house", seed, "plane:70:1:1:12");
			SceneDoc s = generate(rq);
			PlacedBuilding b = s.buildings().get(0);
			Box top = null;
			for (Box x : b.siteBoxes()) if (x.role() == Role.STAIR && (top == null || x.box().y() > top.box().y())) top = x;
			if (top == null) continue;
			tried++;
			List<Box> site = new ArrayList<>(b.siteBoxes());
			site.add(new Box(Box3.of(top.box().x(), top.box().y() + 1, top.box().z(), 1, 1, 1), Role.RAIL, Layer.FOUNDATION, Box.NO_STOREY));
			PlacedBuilding bad = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), site);
			SceneDoc c = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(bad), s.ground(), List.of());
			if (!Invariants.check(c, rq.terrain()).isEmpty()) failed++;
		}
		assertTrue(tried >= 3, "tried " + tried);
		assertEquals(tried, failed);
	}
}
