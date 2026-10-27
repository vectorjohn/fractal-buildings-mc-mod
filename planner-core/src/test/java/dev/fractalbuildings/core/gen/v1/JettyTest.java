package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariant;
import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.SyntheticBiomes;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Jetties over streets (Phase 5 JT): the air-rights strip in front of a street lot, and the {@code in-lot} invariant around it. */
class JettyTest {
	private static GenRequest req(long seed, String type, String style, int w, int d, Facing front, int air, int frontY) {
		return new GenRequest(seed, new Lot("lot0", 100, 200, w, d, front), type, style, SyntheticTerrain.flat(64), SyntheticBiomes.constant("minecraft:plains"),
				new Brief(1, 9, 1, air, frontY));
	}

	/** Columns outside the lot: [count, lowest y]. */
	private static int[] outside(SceneDoc s) {
		Lot lot = s.lots().get(0);
		VoxelGrid g = VoxelGrid.of(s.buildings().get(0));
		int[] r = {0, Integer.MAX_VALUE};
		g.forEach((x, y, z, role) -> {
			if (lot.containsColumn(g.worldX(x, z), g.worldZ(x, z))) return;
			r[0]++;
			r[1] = Math.min(r[1], g.worldY(y));
		});
		return r;
	}

	private static List<Violation> inLot(SceneDoc s) {
		List<Violation> out = new ArrayList<>();
		for (Violation v : Invariants.check(s)) if (v.invariant().equals(Invariant.IN_LOT.id)) out.add(v);
		return out;
	}

	@Test
	void jettiesReachOverTheStreetNeverLowerThanThreeAndNeverOnOtherSides() {
		int over = 0, cases = 0;
		for (String type : new String[] {"house", "shop"})
			for (Facing f : Facing.values())
				for (int air = 1; air <= 2; air++)
					for (long seed = 0; seed < 40; seed++) {
						int lw = type.equals("shop") ? 11 : 12, ld = type.equals("shop") ? 18 : 14;
						GenRequest r = f.dx == 0 ? req(seed, type, "medieval", lw, ld, f, air, 64) : req(seed, type, "medieval", ld, lw, f, air, 64);
						SceneDoc s = new GeneratorV1().generate(r);
						cases++;
						assertEquals(List.of(), Invariants.check(s), type + " " + f + " air " + air + " seed " + seed);
						int[] o = outside(s);
						if (o[0] > 0) {
							over++;
							assertTrue(o[1] >= 64 + Lot.AIR_CLEARANCE, "overhang at y " + o[1] + " is under 3 blocks of clearance");
							assertEquals(air, s.lots().get(0).airRights() >= 1 ? s.lots().get(0).airRights() : -1, "lot declares the air rights");
						}
					}
		assertTrue(over > cases / 10, "jetties over the street are drawn: " + over + " of " + cases);
	}

	@Test
	void overhangNeverExceedsTheAirRights() {
		for (int air = 1; air <= 2; air++)
			for (long seed = 0; seed < 60; seed++) {
				SceneDoc s = new GeneratorV1().generate(req(seed, "house", "medieval", 12, 14, Facing.NORTH, air, 64));
				VoxelGrid g = VoxelGrid.of(s.buildings().get(0));
				Lot lot = s.lots().get(0);
				int[] deepest = {0};
				g.forEach((x, y, z, role) -> deepest[0] = Math.max(deepest[0], lot.z() - g.worldZ(x, z)));
				assertTrue(deepest[0] <= air, "air " + air + " seed " + seed + " overhangs " + deepest[0]);
			}
	}

	@Test
	void noBriefOrNoStreetHeightOrOtherStylesMeanNoOverhang() {
		for (long seed = 0; seed < 30; seed++) {
			GenRequest plain = new GenRequest(seed, new Lot("lot0", 100, 200, 12, 14, Facing.NORTH), "house", "medieval", SyntheticTerrain.flat(64), SyntheticBiomes.constant("minecraft:plains"));
			assertEquals(0, new GeneratorV1().generate(plain).buildings().get(0).shell().spec().lead());
			assertEquals(0, outside(new GeneratorV1().generate(plain))[0]);
			// street surface above the lot's ground: no clearance guarantee, so no air rights
			assertEquals(0, SpecPolicyV1.spec(req(seed, "house", "medieval", 12, 14, Facing.NORTH, 2, 66)).lead());
			assertEquals(0, SpecPolicyV1.spec(req(seed, "house", "medieval", 12, 14, Facing.NORTH, 2, Brief.NO_FRONT_Y)).lead());
			// styles without jetties keep everything inside
			assertEquals(0, SpecPolicyV1.spec(req(seed, "house", "plain", 12, 14, Facing.NORTH, 2, 64)).lead());
			// types that do not use air rights
			assertEquals(0, SpecPolicyV1.spec(req(seed, "workshop", "medieval", 16, 14, Facing.NORTH, 2, 64)).lead());
		}
	}

	@Test
	void streetLowerThanTheLotLeavesMoreClearance() {
		for (long seed = 0; seed < 30; seed++) {
			// the street really is 2 below the lot: the ground east of the lot's front edge (x 114 on) is at 62, as the brief says (the house digs in to it, SHOPLOW S2)
			GenRequest rq = req(seed, "house", "medieval", 14, 12, Facing.EAST, 2, 62);
			rq = new GenRequest(rq.worldSeed(), rq.lot(), rq.type(), rq.style(), (px, pz) -> px >= 114 ? 62 : 64, rq.biome(), rq.brief());
			SceneDoc s = new GeneratorV1().generate(rq);
			assertEquals(List.of(), Invariants.check(s));
			int[] o = outside(s);
			if (o[0] > 0) assertTrue(o[1] >= 62 + 3);
		}
	}

	/** A scene with a real overhang, to mutate. */
	private static SceneDoc jettied() {
		for (long seed = 0; seed < 400; seed++) {
			SceneDoc s = new GeneratorV1().generate(req(seed, "house", "medieval", 12, 14, Facing.NORTH, 1, 64));
			if (outside(s)[0] > 0) return s;
		}
		throw new AssertionError("no jetty over the street in 400 seeds");
	}

	private static SceneDoc withLot(SceneDoc s, Lot l) {
		return new SceneDoc(s.genVersion(), s.seed(), s.terrain(), List.of(l), s.buildings(), s.violations());
	}

	@Test
	void inLotAcceptsTheStripAndFailsEverythingElse() {
		SceneDoc s = jettied();
		Lot lot = s.lots().get(0);
		assertEquals(List.of(), inLot(s));
		assertEquals(1, lot.airRights());
		// mutants: no air rights; the street too high (clearance short); the street on another side; the strip too shallow is checked by the next lines
		assertFalse(inLot(withLot(s, lot.withAir(0, 0))).isEmpty(), "no air rights declared");
		assertFalse(inLot(withLot(s, lot.withAir(1, 64 + 40))).isEmpty(), "building lower than frontY + 3");
		for (Facing other : new Facing[] {Facing.EAST, Facing.SOUTH, Facing.WEST})
			assertFalse(inLot(withLot(s, new Lot(lot.id(), lot.x(), lot.z(), lot.w(), lot.d(), other, 1, 64))).isEmpty(), "street declared on the " + other + " side");
			// the strip depth is exact too: an air-2 scene with a 2-deep overhang fails under 1 air right
		SceneDoc deep = null;
		for (long seed = 0; seed < 400 && deep == null; seed++) {
			SceneDoc d = new GeneratorV1().generate(req(seed, "house", "medieval", 12, 14, Facing.NORTH, 2, 64));
			Lot dl = d.lots().get(0);
			VoxelGrid g = VoxelGrid.of(d.buildings().get(0));
			int[] depth = {0};
			g.forEach((x, y, z, role) -> depth[0] = Math.max(depth[0], dl.z() - g.worldZ(x, z)));
			if (depth[0] == 2) deep = d;
		}
		assertTrue(deep != null, "a 2-deep overhang is drawn in 400 seeds");
		assertEquals(List.of(), inLot(deep));
		assertFalse(inLot(withLot(deep, deep.lots().get(0).withAir(1, 64))).isEmpty(), "2 deep is outside a 1-deep strip");
	// the clearance edge is exact: the lowest overhanging voxel sits at frontY + 3 or above, so frontY + (that - 64) still passes and one more fails
		int low = outside(s)[1];
		assertEquals(List.of(), inLot(withLot(s, lot.withAir(1, low - Lot.AIR_CLEARANCE))));
		assertFalse(inLot(withLot(s, lot.withAir(1, low - Lot.AIR_CLEARANCE + 1))).isEmpty());
	}

	@Test
	void sceneJsonRoundTripsTheAirRights() {
		SceneDoc s = jettied();
		String json = IrJson.write(s);
		assertTrue(json.contains("\"airRights\": 1"));
		assertTrue(json.contains("\"lead\": "));
		SceneDoc back = IrJson.read(json);
		assertEquals(s.lots(), back.lots());
		assertEquals(json, IrJson.write(back));
		// a lot without air rights writes neither key
		assertFalse(IrJson.write(new GeneratorV1().generate(new GenRequest(1, new Lot("lot0", 0, 0, 12, 14, Facing.NORTH), "house", "medieval",
				SyntheticTerrain.flat(64), SyntheticBiomes.constant("minecraft:plains")))).contains("airRights"));
	}

	@Test
	void sameRequestSameBytes() {
		GenRequest r = req(7, "shop", "medieval", 11, 18, Facing.SOUTH, 2, 64);
		assertEquals(IrJson.write(new GeneratorV1().generate(r)), IrJson.write(new GeneratorV1().generate(r)));
	}
}
