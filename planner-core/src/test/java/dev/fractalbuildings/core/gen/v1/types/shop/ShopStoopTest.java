package dev.fractalbuildings.core.gen.v1.types.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.InstancePassV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * SHOPLAND (5.12 follow-up): a shop whose floor sits above the street gets a landing at door level
 * across (most of) the shopfront, and the front steps go down from its end, so the display window
 * does not sit at the top of a flight. Landing cells are STAIR blocks at floor level, on foundation,
 * inside the lot; the invariants (supported, in-lot, entrance reachable) come from {@link Invariants}.
 */
class ShopStoopTest {
	private static final String[] SLOPES = {"plane:64:0:1:12", "plane:64:0:-1:12", "plane:64:1:0:12", "plane:64:-1:0:12", "plane:64:0:1:14", "plane:64:1:1:24", "flat:64", "plane:64:0:1:9"};

	private record Stoop(boolean lifted, int landing, int width, int lift) {}

	private static Stoop measure(SceneDoc s, GenRequest rq) {
		PlacedBuilding b = s.buildings().get(0);
		Opening door = null;
		for (Opening o : b.shell().openings()) if (o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to())) door = o;
		int floorY = InstancePassV1.entranceStorey(b.shell()).y();
		int wx = b.placement().worldX(door.box().x(), door.box().z() - 1), wz = b.placement().worldZ(door.box().x(), door.box().z() - 1);
		int lift = b.oy() + floorY - rq.terrain().floorY(wx, wz);
		int row = door.box().z() - 1, landing = 0;
		for (Box x : b.siteBoxes()) if (x.role() == Role.STAIR && x.box().y() == floorY && x.box().z() == row) landing++;
		return new Stoop(lift >= 2, landing, b.shell().bounds().maxX(), lift);
	}

	@Test
	void liftedShopfrontsHaveALandingAcrossTheFront() {
		int lifted = 0, total = 0, wide = 0;
		List<String> bad = new ArrayList<>();
		for (String terrain : SLOPES)
			for (Facing f : Facing.values())
				for (int[] lt : new int[][] {{11, 20}, {11, 25}, {9, 14}})
				for (long seed = 0; seed < 40; seed++) {
					ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
					GenRequest rq = new GenRequest(seed, new Lot("lot0", 3, -5, f.ordinal() % 2 == 0 ? lt[0] : lt[1], f.ordinal() % 2 == 0 ? lt[1] : lt[0], f), "shop", "medieval", p.terrain(), p.biome());
					SceneDoc s;
					try {
						s = new GeneratorV1().generate(rq);
					} catch (IllegalArgumentException e) {
						continue; // the policy rejects lots steeper than 1 in 12
					}
					total++;
					assertEquals(List.of(), Invariants.check(s, rq.terrain()), terrain + " " + f + " seed " + seed);
					street(s, rq, terrain + " " + f + " seed " + seed);
					Stoop st = measure(s, rq);
					if (!st.lifted) continue;
					lifted++;
					// a landing of at least 3 cells (the door and a cell either side) at door level; a lift over 4 (the flight is one step longer since STAIRGROUND: it ends on the ground, not one above it; a market hall on ground that also falls away) may keep the plain flight
					if (st.landing < 3 && st.lift <= 4) bad.add(terrain + " " + f + " seed " + seed + " lift " + st.lift + " landing " + st.landing + " of " + st.width);
					if (st.landing * 2 >= st.width) wide++;
				}
		System.out.println(String.format(Locale.ROOT, "shop stoops: %d of %d shops lifted 2+ above the door's ground; %d landings span half the front or more", lifted, total, wide));
		assertTrue(bad.isEmpty(), bad.toString());
		assertTrue(lifted >= 30, "only " + lifted + " lifted shops");
		assertTrue(wide >= lifted * 0.8, "wide landings " + wide + " of " + lifted);
	}

	@Test
	void theLandingCheckCanFail() {
		// control: the same scene with the landing's top blocks removed measures a landing of 0
		ProbeSpecs.Probes p = ProbeSpecs.parse("plane:64:0:1:12");
		for (long seed = 0; seed < 200; seed++) {
			GenRequest rq = new GenRequest(seed, new Lot("lot0", 3, -5, 11, 20, Facing.NORTH), "shop", "medieval", p.terrain(), p.biome());
			SceneDoc s = new GeneratorV1().generate(rq);
			Stoop st = measure(s, rq);
			if (!st.lifted) continue;
			assertTrue(st.landing >= 3);
			PlacedBuilding b = s.buildings().get(0);
			int floorY = InstancePassV1.entranceStorey(b.shell()).y();
			List<Box> site = new ArrayList<>(b.siteBoxes());
			site.removeIf(x -> x.role() == Role.STAIR && x.box().y() == floorY);
			PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), site);
			SceneDoc m = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), List.of());
			assertEquals(0, measure(m, rq).landing);
			return;
		}
		throw new AssertionError("no lifted shop found");
	}

	/** A walker from the lot's street-facing edge row reaches the door and, under a market hall, the open floor. */
	private static void street(SceneDoc s, GenRequest rq, String what) {
		StreetWalk w = new StreetWalk(s, rq.terrain());
		assertTrue(w.reachesDoors(), what + ": the door is not reachable from the lot's street row");
		PlacedBuilding b = s.buildings().get(0);
		if (b.shell().volumes().stream().noneMatch(v -> v.id().startsWith("leg"))) return;
		Opening door = null;
		for (Opening o : b.shell().openings()) if (o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to())) door = o;
		assertTrue(w.reachesGround(door.box().x(), door.box().z() + 2), what + ": the open floor under the hall is not reachable from the street row");
	}
}
