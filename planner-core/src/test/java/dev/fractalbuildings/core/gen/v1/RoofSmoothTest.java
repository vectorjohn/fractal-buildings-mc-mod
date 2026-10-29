package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariant;
import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.raster.PieceRaster;
import dev.fractalbuildings.core.raster.Shape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** DS-36: pitched roofs are laid in stairs and slabs; the {@code roof-smooth} invariant; stair directions in the world. */
class RoofSmoothTest {
	static boolean smoothFires(SceneDoc s) {
		return Invariants.check(s).stream().anyMatch(v -> v.invariant().equals(Invariant.ROOF_SMOOTH.id));
	}

	/** The same building with every roof stair and slab turned back into a full roof block, as the generator emitted before DS-36. */
	static SceneDoc stepped(SceneDoc s) {
		PlacedBuilding b = s.buildings().get(0);
		List<Box> boxes = new ArrayList<>();
		for (Box x : b.shell().boxes()) boxes.add(x.role().isRoof() ? new Box(x.box(), Role.ROOF, x.layer(), x.storey()) : x);
		BuildingIr ir = b.shell();
		BuildingIr old = new BuildingIr(ir.spec(), ir.bounds(), ir.storeys(), ir.volumes(), ir.rooms(), ir.openings(), ir.roofs(), boxes);
		PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), old, b.siteBoxes());
		return new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), s.violations());
	}

	@Test
	void everyFixtureFormAndPitchIsSmoothAndTheSteppedVersionIsCaught() {
		int pitchedCases = 0, caught = 0;
		for (Map.Entry<String, Massing> f : TestMassings.all().entrySet())
			for (String form : List.of("gable", "hip", "pyramid", "shed", "mansard"))
				for (int[] p : RoofPassTest.PITCHES)
					for (long seed = 0; seed < 4; seed++) {
						StyleRules style = RoofPassTest.withRoof("medieval", RoofPassTest.roof(form, p[0], p[1], (int) (seed % 3), 100));
						SceneDoc s = RoofPassTest.scene(TestMassings.spec(seed, style.id()), f.getValue(), style);
						String what = f.getKey() + " " + form + " " + p[0] + "/" + p[1] + " seed " + seed;
						assertTrue(!smoothFires(s), what + ": " + Invariants.check(s));
						boolean pitched = s.buildings().get(0).shell().roofs().stream().anyMatch(r -> !r.form().equals("flat"));
						if (!pitched) continue;
						pitchedCases++;
						if (smoothFires(stepped(s))) caught++;
					}
		assertTrue(pitchedCases > 200, "pitched cases " + pitchedCases);
		assertEquals(pitchedCases, caught, "the check fires on the stepped-block version of every pitched roof");
	}

	/** A gable over a 7-wide mass (odd span): stairs rise to a slab-capped ridge; the same over a 6-wide mass (even): stairs back to back. */
	@Test
	void gableSectionIsStairsUpToACappedRidgeOrBackToBack() {
		for (int width : new int[] {7, 6}) {
			StyleRules style = RoofPassTest.withRoof("plain", RoofPassTest.roof("gable", 1, 1, 0, 0));
			Massing m = new Massing(TestMassings.grid(1), List.of(TestMassings.g("main0", "main", 4, 4, 12, width, 1)), List.of());
			SceneDoc s = RoofPassTest.scene(TestMassings.spec(1, "plain"), m, style);
			Map<String, Role> top = topCells(s.buildings().get(0));
			List<Role> row = new ArrayList<>();
			for (int z = 4; z < 4 + width; z++) row.add(top.get("10," + z));
			List<Role> want = width == 7
					? List.of(Role.ROOF_STAIR_S, Role.ROOF_STAIR_S, Role.ROOF_STAIR_S, Role.ROOF_SLAB, Role.ROOF_STAIR_N, Role.ROOF_STAIR_N, Role.ROOF_STAIR_N)
					: List.of(Role.ROOF_STAIR_S, Role.ROOF_STAIR_S, Role.ROOF_STAIR_S, Role.ROOF_STAIR_N, Role.ROOF_STAIR_N, Role.ROOF_STAIR_N);
			assertEquals(want, row, "span " + width);
		}
	}

	/** A 1:2 gable is slabs: bottom, top, bottom, top up the slope. */
	@Test
	void gentleGableIsAlternatingSlabs() {
		StyleRules style = RoofPassTest.withRoof("plain", RoofPassTest.roof("gable", 1, 2, 0, 0));
		Massing m = new Massing(TestMassings.grid(1), List.of(TestMassings.g("main0", "main", 4, 4, 12, 9, 1)), List.of());
		SceneDoc s = RoofPassTest.scene(TestMassings.spec(1, "plain"), m, style);
		Map<String, Role> top = topCells(s.buildings().get(0));
		List<Role> row = new ArrayList<>();
		for (int z = 4; z < 13; z++) row.add(top.get("10," + z));
		assertEquals(List.of(Role.ROOF_SLAB, Role.ROOF_SLAB_TOP, Role.ROOF_SLAB, Role.ROOF_SLAB_TOP, Role.ROOF_SLAB, Role.ROOF_SLAB_TOP, Role.ROOF_SLAB, Role.ROOF_SLAB_TOP, Role.ROOF_SLAB), row);
	}

	/** Top roof cell of every local column as one character: arrows for stairs (the high side), s / S for bottom / top slabs, # a full block, . none. */
	static String roofMap(PlacedBuilding b) {
		Map<String, Role> top = topCells(b);
		int maxX = 0, maxZ = 0;
		for (String k : top.keySet()) { String[] c = k.split(","); maxX = Math.max(maxX, Integer.parseInt(c[0])); maxZ = Math.max(maxZ, Integer.parseInt(c[1])); }
		StringBuilder sb = new StringBuilder();
		for (int z = 0; z <= maxZ; z++) {
			StringBuilder row = new StringBuilder();
			for (int x = 0; x <= maxX; x++) {
				Role r = top.get(x + "," + z);
				row.append(r == null ? '.' : switch (r) {
					case ROOF_STAIR_N -> '^'; case ROOF_STAIR_E -> '>'; case ROOF_STAIR_S -> 'v'; case ROOF_STAIR_W -> '<';
					case ROOF_SLAB -> 's'; case ROOF_SLAB_TOP -> 'S'; default -> '#';
				});
			}
			if (row.toString().chars().anyMatch(ch -> ch != '.')) sb.append(row).append('\n');
		}
		return sb.toString();
	}

	static String mapOf(String fixture, String form, int rise, int run, int overhang) {
		StyleRules style = RoofPassTest.withRoof("medieval", RoofPassTest.roof(form, rise, run, overhang, 0));
		return roofMap(RoofPassTest.scene(TestMassings.spec(1, style.id()), TestMassings.all().get(fixture), style).buildings().get(0));
	}

	/** The look of a hip roof from above (arrows: the high side of each stair): eave stairs straight, the corners of each ring outer corner stairs alternating along x and z, a slab cap on the one-wide ridge. */
	@Test
	void hipRoofMap() {
		assertEquals(String.join("\n", "....>vvvvvvv<", "....>vvvvvvv<", "....>>>vvv<<<", "....>>>sss<<<", "....>>>^^^<<<", "....>^^^^^^^<", "....>^^^^^^^<", ""), mapOf("box1", "hip", 1, 1, 0));
	}

	@Test
	void gableRoofMap() {
		assertEquals(String.join("\n", "....vvvvvvvvv", "....vvvvvvvvv", "....vvvvvvvvv", "....sssssssss", "....^^^^^^^^^", "....^^^^^^^^^", "....^^^^^^^^^", ""), mapOf("box1", "gable", 1, 1, 0));
	}

	/** DS-36 (architect r1): a hip's eave overhang ring turns its corners with a stair too (it faces along z, the eave corner above along x), so the hip line reaches the overhang. */
	@Test
	void hipOverhangRingTurnsItsCornersWithStairs() {
		StyleRules style = RoofPassTest.withRoof("medieval", RoofPassTest.roof("hip", 1, 1, 1, 0));
		PlacedBuilding b = RoofPassTest.scene(TestMassings.spec(1, style.id()), TestMassings.all().get("box1"), style).buildings().get(0);
		assertEquals(String.join("\n", "...vvvvvvvvvvv", "...>>vvvvvvv<<", "...>>vvvvvvv<<", "...>>>>vvv<<<<", "...>>>>sss<<<<", "...>>>>^^^<<<<", "...>>^^^^^^^<<", "...>>^^^^^^^<<", "...^^^^^^^^^^^", ""), roofMap(b));
		// in the raster the overhang ring has exactly four corner stairs, outer ones, at its four corners (before: a slab there, and the two stairs beside it turned outer, a notch)
		int[] minY = {Integer.MAX_VALUE};
		java.util.List<int[]> stairs = new java.util.ArrayList<>();
		java.util.Map<String, dev.fractalbuildings.core.raster.Shape> shapes = new java.util.HashMap<>();
		dev.fractalbuildings.core.raster.PieceRaster.building(b, dev.fractalbuildings.core.ir.Box3.of(-1000, -100, -1000, 3000, 600, 3000), (x, y, z, r, id, sh) -> {
			if (!r.isRoofStair()) return;
			minY[0] = Math.min(minY[0], y);
			stairs.add(new int[] {x, y, z});
			shapes.put(x + "," + y + "," + z, sh);
		});
		int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
		for (int[] s : stairs) if (s[1] == minY[0]) { x0 = Math.min(x0, s[0]); x1 = Math.max(x1, s[0]); z0 = Math.min(z0, s[2]); z1 = Math.max(z1, s[2]); }
		java.util.List<String> corners = new java.util.ArrayList<>();
		for (int[] s : stairs) {
			if (s[1] != minY[0]) continue;
			var sh = shapes.get(s[0] + "," + s[1] + "," + s[2]);
			if (sh.corner() == null) continue;
			corners.add(sh.corner().name().substring(0, 5) + ((s[0] == x0 || s[0] == x1) && (s[2] == z0 || s[2] == z1) ? "@corner" : "@" + s[0] + "," + s[2]));
		}
		assertEquals(java.util.List.of("OUTER@corner", "OUTER@corner", "OUTER@corner", "OUTER@corner"), corners);
	}

	/** A 1:2 hip is rings of bottom and top slabs. */
	@Test
	void gentleHipRoofMap() {
		assertEquals(String.join("\n", "....sssssssss", "....sSSSSSSSs", "....sSsssssSs", "....sSsSSSsSs", "....sSsssssSs", "....sSSSSSSSs", "....sssssssss", ""), mapOf("box1", "hip", 1, 2, 0));
	}
	static String code(Role r) { return r == null ? "." : r.name(); }

	/** Topmost roof-family role of every local column (x,z) of a building. */
	static Map<String, Role> topCells(PlacedBuilding b) {
		Map<String, Role> top = new HashMap<>();
		Map<String, Integer> topY = new HashMap<>();
		for (Box bx : b.shell().boxes()) {
			if (!bx.role().isRoof()) continue;
			var q = bx.box();
			for (int x = q.x(); x < q.maxX(); x++)
				for (int z = q.z(); z < q.maxZ(); z++) {
					String k = x + "," + z;
					int y = q.maxY() - 1;
					if (topY.getOrDefault(k, -1) <= y) { topY.put(k, y); top.put(k, bx.role()); }
				}
		}
		return top;
	}

	/** In the world, whatever the rotation, a roof stair's high side points at something higher (a solid above-neighbour) or at a twin back to back across a ridge; never down the slope. */
	@Test
	void roofStairsFaceUphillAtEveryRotation() {
		int stairs = 0;
		for (String form : List.of("gable", "hip")) {
			StyleRules style = RoofPassTest.withRoof("medieval", RoofPassTest.roof(form, 1, 1, 1, 100));
			for (int r = 0; r < 4; r++) {
				SceneDoc s = RoofPassTest.scene(TestMassings.spec(3, style.id()), TestMassings.all().get("L"), style);
				PlacedBuilding b0 = s.buildings().get(0);
				PlacedBuilding b = new PlacedBuilding(b0.id(), b0.lots(), 100, 64, 200, r, b0.shell(), List.of());
				Map<String, Role> roles = new HashMap<>();
				Map<String, Shape> shapes = new HashMap<>();
				PieceRaster.building(b, dev.fractalbuildings.core.ir.Box3.of(-1000, -100, -1000, 3000, 600, 3000), (x, y, z, role, id, sh) -> { roles.put(x + "," + y + "," + z, role); shapes.put(x + "," + y + "," + z, sh); });
				for (var e : roles.entrySet()) {
					if (!e.getValue().isRoofStair()) continue;
					stairs++;
					String[] c = e.getKey().split(",");
					int x = Integer.parseInt(c[0]), y = Integer.parseInt(c[1]), z = Integer.parseInt(c[2]);
					Facing f = shapes.get(e.getKey()).facing();
					Role ahead = roles.getOrDefault((x + f.dx) + "," + (y + 1) + "," + (z + f.dz), Role.AIR);
					Role level = roles.getOrDefault((x + f.dx) + "," + y + "," + (z + f.dz), Role.AIR);
					assertTrue(ahead.solid || level.isRoofStair() || level == Role.ROOF, form + " rotation " + r + ": stair at " + e.getKey() + " faces " + f + " into air (" + ahead + "/" + level + ")");
				}
			}
		}
		assertTrue(stairs > 100, "stairs " + stairs);
	}
}
