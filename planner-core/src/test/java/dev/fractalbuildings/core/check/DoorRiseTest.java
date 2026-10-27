package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DS-45 door rise (SHOPLOW S0): the measure behind the {@code ## Door rise} tables, on generated and hand-edited scenes. */
class DoorRiseTest {
	private static GenRequest request(String type, long seed, String terrain) {
		ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
		return new GenRequest(seed, new Lot("lot0", 0, 0, 18, 18, Facing.NORTH), type, "medieval", p.terrain(), p.biome());
	}

	private static List<DoorRise.Door> front(List<DoorRise.Door> all) {
		return all.stream().filter(DoorRise.Door::front).toList();
	}

	@Test
	void flatLotHasOneStepAtTheFrontDoor() {
		for (long seed = 1; seed <= 8; seed++) {
			GenRequest rq = request("house", seed, "flat:70");
			SceneDoc s = new GeneratorV1().generate(rq);
			List<DoorRise.Door> f = front(DoorRise.measure(s, rq.terrain()));
			assertEquals(1, f.size(), "seed " + seed);
			assertEquals(1, f.get(0).rise(), "seed " + seed);
			assertEquals("terrain", f.get(0).source());
			assertEquals("house", f.get(0).kind());
			assertTrue(f.get(0).reached());
			assertTrue(f.get(0).withinLimits());
		}
	}

	/** Lowering the ground in front of the door by 4 raises its rise by 4, and the walker then has no way down (no flight was built for it). */
	@Test
	void riseIsMeasuredFromTheTerrainInFront() {
		GenRequest rq = request("house", 3, "flat:70");
		SceneDoc s = new GeneratorV1().generate(rq);
		TerrainProbe low = new TerrainProbe() {
			@Override public int floorY(int x, int z) { return rq.terrain().floorY(x, z) - 4; }
			@Override public int surfaceY(int x, int z) { return rq.terrain().surfaceY(x, z) - 4; }
		};
		DoorRise.Door a = front(DoorRise.measure(s, rq.terrain())).get(0), b = front(DoorRise.measure(s, low)).get(0);
		assertEquals(a.rise() + 4, b.rise());
		assertEquals(5, b.rise());
		assertFalse(b.withinLimits()); // 5 without a landing
	}

	/** A road surface box 2 above the terrain in front of the door: the rise is measured from the road. Mutation: ignore ground boxes and this fails. */
	@Test
	void riseIsMeasuredFromTheRoadInFront() {
		GenRequest rq = request("house", 3, "flat:70");
		SceneDoc s = new GeneratorV1().generate(rq);
		DoorRise.Door d0 = front(DoorRise.measure(s, rq.terrain())).get(0);
		PlacedBuilding b = s.buildings().get(0);
		Opening o = StairGround.doors(b).get(0);
		Facing outW = b.placement().toWorld(o.to().equals(Opening.OUTSIDE) ? o.facing() : o.facing().rotate(2));
		// a road slab over the whole ring of columns around the lot, 2 above the terrain (first free block 72)
		Lot l = s.lots().get(0);
		List<Box> ground = new ArrayList<>(s.ground());
		ground.add(new Box(Box3.of(l.x() - 3, 69, l.z() - 3, l.w() + 6, 3, l.d() + 6), Role.ROAD, Layer.GROUND, Box.NO_STOREY));
		SceneDoc c = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), s.buildings(), ground, List.of());
		DoorRise.Door d = front(DoorRise.measure(c, rq.terrain())).get(0);
		assertEquals("ground", d.source());
		assertEquals(d0.rise() - 2, d.rise());
		assertEquals(outW, s.lots().get(0).front());
	}

	@Test
	void deterministicAndSaneOverTypesAndSlopes() {
		int doors = 0;
		for (String type : new String[] {"house", "shop", "workshop"})
			for (String slope : new String[] {"flat:70", "plane:70:1:0:6", "plane:70:0:1:6", "plane:70:-1:0:6"})
				for (long seed = 1; seed <= 5; seed++) {
					GenRequest rq = request(type, seed, slope);
					SceneDoc s;
					try { s = new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { continue; }
					List<DoorRise.Door> a = DoorRise.measure(s, rq.terrain());
					assertEquals(a, DoorRise.measure(s, rq.terrain()));
					for (DoorRise.Door d : a) {
						doors++;
						assertTrue(d.reached(), type + " " + slope + " " + seed);
						assertTrue(d.run() >= 0 && d.run() <= Math.max(0, d.rise()) + 1, type + " " + slope + " " + seed + " run " + d.run() + " rise " + d.rise());
					}
				}
		assertTrue(doors > 40, "only " + doors + " doors");
	}

	@Test
	void limitsFollowDs45() {
		DoorRise.Door shop2 = door("shop", 2, false, 1), shop3 = door("shop", 3, true, 1), shopLow = door("shop", -3, false, 0);
		DoorRise.Door house3 = door("house", 3, false, 3), house4 = door("house", 4, false, 3), house4L = door("house", 4, true, 3), house6L = door("house", 6, true, 3), run4 = door("house", 3, false, 4);
		assertTrue(shop2.withinLimits());
		assertFalse(shop3.withinLimits());
		assertFalse(shopLow.withinLimits());
		assertTrue(house3.withinLimits());
		assertFalse(house4.withinLimits());
		assertTrue(house4L.withinLimits());
		assertFalse(house6L.withinLimits());
		assertFalse(run4.withinLimits());
	}

	@Test
	void reportBucketsAndRenders() {
		DoorRise.Report r = new DoorRise.Report();
		r.add(List.of(door("shop", 1, false, 1), door("shop", 5, true, 5), door("house", -4, false, 0), door("house", 9, false, 9)));
		String md = r.toMarkdown();
		assertEquals(4, r.doors());
		assertTrue(md.contains("| shop | 2 | 0 | 0 | 1 | 0 | 0 | 0 | 1 | 0 | 0 | 1 (50 %) | 50 % | 1 | 5 | 1 (50 %) |"), md);
		assertTrue(md.contains("| house | 2 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 (50 %) | 0 % | 1 | 9 | 0 (0 %) |"), md);
	}

	private static DoorRise.Door door(String kind, int rise, boolean landing, int run) {
		return new DoorRise.Door("d", "b", "l", kind, "medieval", true, rise, "ground", run, landing, true);
	}
}
