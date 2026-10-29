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
		for (Opening o : b.shell().openings()) if (o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to()) && o.facing() == dev.fractalbuildings.core.ir.Facing.NORTH) door = o; // the main entrance faces the front (DS35DOORS adds secondary doors on other faces)
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
		// S2 dig-in + S4 (the market hall is a level-ground square shop): shops are no longer lifted on slopes, so this is a regression guard, not a count floor
		assertTrue(total >= 300 && lifted * 20 <= total, "lifted " + lifted + " of " + total + " shops");
		assertTrue(wide >= lifted * 0.8, "wide landings " + wide + " of " + lifted);
	}

	/** SHOPLOW S2: a shop digs in to the street. On slopes up to the planner's limit every shop door rises at most 2, a stoop appears only at rise 2, and the street row reaches the door. */
	@Test
	void everyShopRisesAtMostTwoAndAStoopAppearsOnlyAtRiseTwo() {
		String[] slopes = {"plane:64:0:1:4", "plane:64:0:1:6", "plane:64:0:1:9", "plane:64:0:-1:4", "plane:64:1:0:6", "plane:64:-1:0:6", "plane:64:0:1:12", "flat:64"};
		int shops = 0, stoops = 0, rise2 = 0;
		for (String terrain : slopes)
			for (Facing f : Facing.values())
				for (int[] lt : new int[][] {{11, 17}, {9, 14}, {11, 20}})
					for (long seed = 0; seed < 12; seed++) {
						ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
						GenRequest rq = new GenRequest(seed, new Lot("lot0", 3, -5, f.ordinal() % 2 == 0 ? lt[0] : lt[1], f.ordinal() % 2 == 0 ? lt[1] : lt[0], f), "shop", "medieval", p.terrain(), p.biome());
						SceneDoc s;
						try { s = new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { continue; }
						String what = terrain + " " + f + " " + lt[0] + "x" + lt[1] + " seed " + seed;
						assertEquals(List.of(), Invariants.check(s, rq.terrain()), what);
						street(s, rq, what);
						PlacedBuilding b = s.buildings().get(0);
						if (InstancePassV1.entranceStorey(b.shell()).y() != 0) continue; // a market hall on posts: its trade floor is the open ground
						shops++;
						var eff = dev.fractalbuildings.core.terrain.SiteGround.probe(b, rq.terrain());
						Opening door = null;
						for (Opening o : b.shell().openings()) if (o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to()) && o.facing() == dev.fractalbuildings.core.ir.Facing.NORTH) door = o; // the main entrance faces the front (DS35DOORS adds secondary doors on other faces)
						int wx = b.placement().worldX(door.box().x(), door.box().z() - 1), wz = b.placement().worldZ(door.box().x(), door.box().z() - 1);
						int rise = b.oy() + door.box().y() - eff.floorY(wx, wz); // the door's rise above the ground in front of it (the lot's own, not the street's)
						int row = door.box().z() - 1, landing = 0;
						for (Box x : b.siteBoxes()) if (x.role() == Role.STAIR && x.box().y() == 0 && x.box().z() == row && b.siteBoxes().stream().noneMatch(q -> q.role() == Role.PAVING && q.box().y() == -1 && q.box().z() == row && q.box().x() <= x.box().x() && x.box().x() < q.box().maxX())) landing++; // (a stair block on the areaway paving is the step below the door, not a stoop; PORCHSTEP: nor is the one step in front of a door at rise 1, so a stoop is 2 or more cells)
						if (landing > 1) { stoops++; assertTrue(rise >= 2, what + ": a stoop at rise " + rise); }
						if (rise == 2) rise2++;
						for (dev.fractalbuildings.core.check.DoorRise.Door d : dev.fractalbuildings.core.check.DoorChecks.doors(s, b, rq.terrain()))
							if (d.front()) assertTrue(d.rise() <= 2, what + ": rise " + d.rise() + " from the street");
					}
		System.out.println("shop stoops after the dig-in: " + shops + " shops, " + rise2 + " at rise 2 over the ground in front, " + stoops + " stoops");
		assertTrue(shops >= 300, "shops " + shops);
	}

	@Test
	void theLandingCheckCanFail() {
		// control: the same scene with the landing's top blocks removed measures a landing of 0
		for (String terrain : SLOPES) for (Facing f : Facing.values()) for (long seed = 0; seed < 60; seed++) {
			ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
			GenRequest rq = new GenRequest(seed, new Lot("lot0", 3, -5, f.ordinal() % 2 == 0 ? 11 : 20, f.ordinal() % 2 == 0 ? 20 : 11, f), "shop", "medieval", p.terrain(), p.biome());
			SceneDoc s;
			try { s = new GeneratorV1().generate(rq); } catch (IllegalArgumentException e) { continue; }
			Stoop st = measure(s, rq);
			if (st.landing < 1) continue; // a stoop landing at rise 2 (shops dig in; no lifted market hall on a slope since S4)

			PlacedBuilding b = s.buildings().get(0);
			int floorY = InstancePassV1.entranceStorey(b.shell()).y();
			List<Box> site = new ArrayList<>(b.siteBoxes());
			site.removeIf(x -> x.role() == Role.STAIR && x.box().y() == floorY);
			PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), site);
			SceneDoc m = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), List.of());
			assertEquals(0, measure(m, rq).landing);
			return;
		}
		throw new AssertionError("no shop with a stoop landing found");
	}

	/** A walker from the lot's street-facing edge row reaches the door and, under a market hall, the open floor. */
	private static void street(SceneDoc s, GenRequest rq, String what) {
		StreetWalk w = new StreetWalk(s, dev.fractalbuildings.core.terrain.SiteGround.probe(s.buildings().get(0), rq.terrain())); // the ground a walker sees: the dig-in's cut and fill included
		assertTrue(w.reachesDoors(), what + ": the door is not reachable from the lot's street row");
		PlacedBuilding b = s.buildings().get(0);
		if (b.shell().volumes().stream().noneMatch(v -> v.id().startsWith("leg"))) return;
		Opening door = null;
		for (Opening o : b.shell().openings()) if (o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to()) && o.facing() == dev.fractalbuildings.core.ir.Facing.NORTH) door = o; // the main entrance faces the front (DS35DOORS adds secondary doors on other faces)
		assertTrue(w.reachesGround(door.box().x(), door.box().z() + 2), what + ": the open floor under the hall is not reachable from the street row");
	}
}
