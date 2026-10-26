package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.raster.PieceRaster;
import dev.fractalbuildings.core.raster.Shape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * STAIRS: an interior flight reaches the next floor with no rise over half a block per step ({@code stair-rise}), for every type
 * that has stairs; and the mod's block shapes (the raster) make every step of a flight a stair facing up the flight, the last one
 * included (it has nothing higher ahead and used to be a candidate for a full block).
 */
class StairRiseTest {
	private static final String ID = Invariant.STAIR_RISE.id;
	private static final String[] TYPES = {"house", "shop", "workshop", "hall", "tower", "block", "highrise", "bastion", "temple"};

	private static GenRequest request(String type, long seed, String style, int w, int d) {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:70");
		return new GenRequest(seed, new Lot("lot0", 0, 0, w, d, Facing.NORTH), type, style, p.terrain(), p.biome());
	}

	private static SceneDoc generate(String type, long seed, String style) {
		return new GeneratorV1().generate(request(type, seed, style, type.equals("highrise") || type.equals("block") ? 28 : 20, type.equals("highrise") || type.equals("block") ? 28 : 18));
	}

	private static List<Violation> mine(SceneDoc s, GenRequest rq) {
		List<Violation> v = new ArrayList<>();
		for (Violation x : Invariants.check(s, rq.terrain())) if (x.invariant().equals(ID)) v.add(x);
		return v;
	}

	@Test
	void everyFlightOfEveryTypeAndStyleHasNoJump() {
		int flights = 0, buildings = 0;
		for (String type : TYPES)
			for (String style : new String[] {"medieval", "plain"})
				for (long seed = 1; seed <= 12; seed++) {
					GenRequest rq = request(type, seed, style, type.equals("highrise") || type.equals("block") ? 28 : 20, type.equals("highrise") || type.equals("block") ? 28 : 18);
					SceneDoc s;
					try { s = new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { continue; } // lot too small for the type
					assertEquals(List.of(), mine(s, rq), type + " " + style + " seed " + seed);
					buildings++;
					for (PlacedBuilding b : s.buildings()) for (Opening o : b.shell().openings()) if (o.kind() == OpeningKind.STAIR) flights++;
				}
		assertTrue(buildings > 100 && flights > 200, buildings + " buildings, " + flights + " flights");
	}

	/** Control: a flight whose last step (the one in the slab layer) is removed needs a jump, so the check must fail. */
	@Test
	void aFlightWithoutItsLastStepFails() {
		int tried = 0, failed = 0;
		for (String type : new String[] {"house", "highrise", "tower"})
			for (long seed = 1; seed <= 6; seed++) {
				GenRequest rq = request(type, seed, "medieval", type.equals("highrise") ? 28 : 18, type.equals("highrise") ? 28 : 18);
				SceneDoc s;
				try { s = new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { continue; }
				PlacedBuilding b = s.buildings().get(0);
				BuildingIr ir = b.shell();
				Opening run = null;
				for (Opening o : ir.openings()) if (o.kind() == OpeningKind.STAIR) { run = o; break; }
				if (run == null) continue;
				Box3 r = run.box();
				int topY = r.maxY() - 1;
				List<Box> boxes = new ArrayList<>();
				for (Box x : ir.boxes()) {
					boolean last = x.role() == Role.STAIR && x.box().maxY() - 1 == topY && r.x() <= x.box().x() && x.box().maxX() <= r.maxX() && r.z() <= x.box().z() && x.box().maxZ() <= r.maxZ();
					if (!last) boxes.add(x);
				}
				BuildingIr cut = new BuildingIr(ir.spec(), ir.bounds(), ir.storeys(), ir.volumes(), ir.rooms(), ir.openings(), ir.roofs(), boxes);
				PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), cut, b.siteBoxes());
				SceneDoc c = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), s.ground(), List.of());
				tried++;
				if (!mine(c, rq).isEmpty()) failed++;
			}
		assertTrue(tried >= 8, "tried " + tried);
		assertEquals(tried, failed, "every flight with its last step removed must fail stair-rise");
	}

	/** Mod side: the top cell of every step column in a flight's opening rasters as a stair facing up the flight (never a full block), including the last step. */
	@Test
	void everyStepOfEveryFlightIsAStairFacingUp() {
		int steps = 0, lasts = 0;
		for (String type : new String[] {"highrise", "house", "tower", "block", "shop"})
			for (long seed = 1; seed <= 6; seed++) {
				SceneDoc s;
				try { s = generate(type, seed, "medieval"); } catch (IllegalArgumentException e) { continue; }
				PlacedBuilding b = s.buildings().get(0);
				Map<String, Shape> shapes = new HashMap<>();
				Map<String, Role> roles = new HashMap<>();
				PieceRaster.building(b, Box3.of(-1000, -100, -1000, 3000, 600, 3000), (x, y, z, role, id, sh) -> { shapes.put(x + "," + y + "," + z, sh); roles.put(x + "," + y + "," + z, role); });
				var p = b.placement();
				for (Opening o : b.shell().openings()) {
					if (o.kind() != OpeningKind.STAIR) continue;
					Facing up = p.toWorld(o.facing());
					Box3 r = o.box();
					for (int y = r.y(); y < r.maxY(); y++) for (int z = r.z(); z < r.maxZ(); z++) for (int x = r.x(); x < r.maxX(); x++) {
						String k = p.worldX(x, z) + "," + p.worldY(y) + "," + p.worldZ(x, z);
						if (roles.get(k) != Role.STAIR || roles.get(p.worldX(x, z) + "," + (p.worldY(y) + 1) + "," + p.worldZ(x, z)) == Role.STAIR) continue; // buried cells are full blocks (noStairBlockUnderAStairOfTheSameFlight)
						steps++;
						assertNotEquals(Shape.NONE, shapes.get(k), type + " seed " + seed + " step " + k + " is a full block");
						assertEquals(Shape.stair(up), shapes.get(k), type + " seed " + seed + " step " + k);
						if (y == r.maxY() - 1) lasts++;
					}
				}
			}
		assertTrue(steps > 100 && lasts > 10, steps + " steps, " + lasts + " top-row cells");
	}

	/** STAIRFILL: no stair-shaped block sits directly under another cell of the same flight; the support under each step is a full block. */
	@Test
	void noStairBlockUnderAStairOfTheSameFlight() {
		int supports = 0;
		for (String type : new String[] {"highrise", "house", "tower", "block", "shop", "hall"})
			for (String style : new String[] {"medieval", "plain"})
				for (long seed = 1; seed <= 6; seed++) {
					SceneDoc s;
					try { s = generate(type, seed, style); } catch (IllegalArgumentException e) { continue; }
					PlacedBuilding b = s.buildings().get(0);
					Map<String, Shape> shapes = new HashMap<>();
					Map<String, Role> roles = new HashMap<>();
					PieceRaster.building(b, Box3.of(-1000, -100, -1000, 3000, 600, 3000), (x, y, z, role, id, sh) -> { shapes.put(x + "," + y + "," + z, sh); roles.put(x + "," + y + "," + z, role); });
					var p = b.placement();
					for (Opening o : b.shell().openings()) {
						if (o.kind() != OpeningKind.STAIR) continue;
						Box3 r = o.box();
						for (int y = r.y(); y < r.maxY(); y++) for (int z = r.z(); z < r.maxZ(); z++) for (int x = r.x(); x < r.maxX(); x++) {
							String k = p.worldX(x, z) + "," + p.worldY(y) + "," + p.worldZ(x, z);
							String above = p.worldX(x, z) + "," + (p.worldY(y) + 1) + "," + p.worldZ(x, z);
							if (roles.get(k) != Role.STAIR || roles.get(above) != Role.STAIR) continue;
							supports++;
							assertEquals(Shape.NONE, shapes.get(k), type + " " + style + " seed " + seed + " support " + k + " must be a full block");
						}
					}
				}
		assertTrue(supports > 50, supports + " support cells");
	}
}
