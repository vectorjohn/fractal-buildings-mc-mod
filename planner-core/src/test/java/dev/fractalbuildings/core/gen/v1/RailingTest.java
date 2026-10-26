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

	/** A cell in front of a door leaf: the approach is kept free, no rail is expected there. */
	private static boolean nearDoor(PlacedBuilding b, int cx, int cz) {
		for (var o : b.shell().openings())
			if (o.kind() == dev.fractalbuildings.core.ir.OpeningKind.DOOR) {
				Facing out = o.to().equals(dev.fractalbuildings.core.ir.Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2);
				for (int x = o.box().x(); x < o.box().maxX(); x++) for (int z = o.box().z(); z < o.box().maxZ(); z++) if (cx == x + out.dx && cz == z + out.dz) return true;
			}
		return false;
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
							if (x.role() == Role.STAIR && c.y() + 1 <= q.y() && Math.abs(c.x() - q.x()) <= 1 && Math.abs(c.z() - q.z()) <= 1) beside = true;
							if (x.role() == Role.FOUNDATION && c.x() == q.x() && c.z() == q.z() && c.maxY() == q.y()) kerb = true;
						}
						assertTrue(beside, what + ": rail " + q + " has no step beside it at its level");
						assertTrue(kerb, what + ": rail " + q + " has no kerb under it");
						int wx = b.placement().worldX(q.x(), q.z()), wz = b.placement().worldZ(q.x(), q.z());
						assertTrue(b.oy() + q.y() - rq.terrain().floorY(wx, wz) >= InstancePassV1.RAIL_DROP_LANDING + 1, what + ": rail on ground level with the tread");
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


	/**
	 * Rails that hold: a player steps 0.6 and a fence collides to 1.5, so a rail block must stand one block above the walking
	 * surface of the tread it guards (top 1.5 above the feet), and every open edge of a deep flight or landing, inside the lot
	 * and outside the building, has one (no gap to step through).
	 */
	@Test
	void railsStandOneBlockAboveTheSurfaceAndLeaveNoGap() {
		int rails = 0, edges = 0, diag = 0;
		for (String type : TYPES)
			for (String slope : SLOPES)
				for (long seed = 1; seed <= 8; seed++) {
					GenRequest rq = request(type, seed, slope);
					SceneDoc s = generate(rq);
					if (s == null) continue;
					PlacedBuilding b = s.buildings().get(0);
					String what = type + " " + slope + " seed " + seed;
					java.util.Set<Long> columns = new java.util.HashSet<>(), railCols = new java.util.HashSet<>();
					for (Box x : b.siteBoxes()) {
						long k = ((long) x.box().x() << 32) ^ (x.box().z() & 0xffffffffL);
						if (x.role() == Role.RAIL) {
							railCols.add(k);
							rails++;
							// a player steps 0.6 and may cut a corner: the rail top is 1.5 above every tread within one cell, so rail y >= tread y + 1
							boolean near = false;
							for (Box t : b.siteBoxes())
								if (t.role() == Role.STAIR && Math.abs(t.box().x() - x.box().x()) <= 1 && Math.abs(t.box().z() - x.box().z()) <= 1) {
									near = true;
									assertTrue(x.box().y() >= t.box().y() + 1, what + ": rail " + x.box() + " top is under 1.5 above tread " + t.box());
								}
							assertTrue(near, what + ": rail " + x.box() + " guards no tread");
						} else if (x.role() == Role.STAIR) columns.add(k);
					}
					Box3 bd = b.shell().bounds();
					for (Box t : b.siteBoxes()) { // no diagonal gap: two rails diagonal to each other that share a tread cell need a rail (or the building) in the other shared cell
						if (t.role() != Role.STAIR) continue;
						for (int dx = -1; dx <= 1; dx += 2)
							for (int dz = -1; dz <= 1; dz += 2) {
								int ax = t.box().x() + dx, az = t.box().z(), bx = t.box().x(), bz = t.box().z() + dz, cx = t.box().x() + dx, cz = t.box().z() + dz;
								long ka = ((long) ax << 32) ^ (az & 0xffffffffL), kb = ((long) bx << 32) ^ (bz & 0xffffffffL), kc = ((long) cx << 32) ^ (cz & 0xffffffffL);
								if (railCols.contains(ka) && railCols.contains(kb)) { diag++; assertTrue(railCols.contains(kc) || columns.contains(kc) || cx >= 0 && cz >= 0 && cx < bd.maxX() && cz < bd.maxZ(), what + ": rails at (" + ax + "," + az + ") and (" + bx + "," + bz + ") meet diagonally with no corner post at (" + cx + "," + cz + ")"); }
							}
					}
					for (Box t : b.siteBoxes()) {
						if (t.role() != Role.STAIR) continue;
						for (Facing f : Facing.values()) {
							int cx = t.box().x() + f.dx, cz = t.box().z() + f.dz;
							long k = ((long) cx << 32) ^ (cz & 0xffffffffL);
							int wx = b.placement().worldX(cx, cz), wz = b.placement().worldZ(cx, cz);
							boolean inShell = cx >= 0 && cz >= 0 && cx < bd.maxX() && cz < bd.maxZ();
							if (inShell || nearDoor(b, cx, cz) || columns.contains(k)) continue;
							if (b.oy() + t.box().y() - rq.terrain().floorY(wx, wz) < InstancePassV1.RAIL_DROP_FLIGHT) continue;
							edges++;
							assertTrue(railCols.contains(k), what + (rq.lot().containsColumn(wx, wz) ? "" : " (lot line)") + ": open edge beside tread " + t.box() + " at (" + cx + "," + cz + ") has no rail");
						}
					}
				}
		assertTrue(rails >= 50 && edges >= 50 && diag >= 10, rails + " rails, " + edges + " edges, " + diag + " corners");
	}

	/** Open edges beside a flight or landing deeper than the rail threshold with no rail on them, as text. */
	static List<String> unguardedEdges(PlacedBuilding b, GenRequest rq) {
		List<String> bad = new ArrayList<>();
		java.util.Set<Long> columns = new java.util.HashSet<>(), railCols = new java.util.HashSet<>();
		for (Box x : b.siteBoxes()) {
			long k = ((long) x.box().x() << 32) ^ (x.box().z() & 0xffffffffL);
			if (x.role() == Role.RAIL) railCols.add(k);
			else if (x.role() == Role.STAIR) columns.add(k);
		}
		Box3 bd = b.shell().bounds();
		for (Box t : b.siteBoxes()) {
			if (t.role() != Role.STAIR) continue;
			for (Facing f : Facing.values()) {
				int cx = t.box().x() + f.dx, cz = t.box().z() + f.dz;
				long k = ((long) cx << 32) ^ (cz & 0xffffffffL);
				int wx = b.placement().worldX(cx, cz), wz = b.placement().worldZ(cx, cz);
				if (cx >= 0 && cz >= 0 && cx < bd.maxX() && cz < bd.maxZ() || nearDoor(b, cx, cz) || columns.contains(k)) continue;
				if (b.oy() + t.box().y() - rq.terrain().floorY(wx, wz) < InstancePassV1.RAIL_DROP_FLIGHT) continue;
				if (!railCols.contains(k)) bad.add("tread " + t.box() + " open to (" + cx + "," + cz + ")" + (rq.lot().containsColumn(wx, wz) ? "" : " on the lot line"));
			}
		}
		return bad;
	}

	/**
	 * LOTRAIL: the gallery presets whose buildings had only a one-cell margin (house-slope seeds 3, 4, 6, 7 had 8-11 block drops
	 * unguarded along the lot line): every flight and landing is railed inside the lot, because the building is set back.
	 */
	@Test
	void presetBuildingsWithAOneCellMarginAreSetBack() {
		Object[][] presets = {{"house", 14, 12, "plane:64:0:1:1", 8}, {"shop", 11, 25, "plane:64:0:1:9", 16}, {"tower", 13, 13, "noise:7:64:6:32", 16}};
		int checked = 0;
		for (Object[] pr : presets)
			for (long seed = 0; seed < (int) pr[4]; seed++) {
				ProbeSpecs.Probes p = ProbeSpecs.parse((String) pr[3]);
				GenRequest rq = new GenRequest(seed, new Lot("lot0", 0, 0, (int) pr[1], (int) pr[2], Facing.NORTH), (String) pr[0], "medieval", p.terrain(), p.biome());
				SceneDoc s = generate(rq);
				if (s == null) continue;
				checked++;
				String what = pr[0] + " " + pr[3] + " seed " + seed;
				assertEquals(List.of(), unguardedEdges(s.buildings().get(0), rq), what);
				assertEquals(List.of(), Invariants.check(s, rq.terrain()), what);
			}
		assertTrue(checked >= 35, "checked " + checked);
	}

	/** The walk model: the top of a rail or fence is not a floor. */
	@Test
	void aRailTopIsNotStandable() {
		assertFalse(dev.fractalbuildings.core.check.Invariants.walkFloor(Role.RAIL));
		assertFalse(dev.fractalbuildings.core.check.Invariants.walkFloor(Role.FENCE));
		assertTrue(dev.fractalbuildings.core.check.Invariants.walkFloor(Role.FOUNDATION));
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
