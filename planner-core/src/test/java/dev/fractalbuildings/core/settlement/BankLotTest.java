package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.style.StyleField;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SHOPLOW S3: {@code empty:bank} lots (the street on an embankment or in a cutting beside the lot) and the shop front-rise limit. */
class BankLotTest {
	/** One east-west street (surface y = {@code streetY}, rows z 0..4) (raw ground {@code frontGround} under it) and one 10 x 14 lot north-facing behind it, ground {@code frontGround} at its front row and rising {@code rise} over its depth. */
	static LotBrief fill(String type, int streetY, int frontGround, int rise) { return fill(type, streetY, frontGround, rise, 10, 14); }

	static LotBrief fill(String type, int streetY, int frontGround, int rise, int lw, int ld) {
		Street street = new Street("s0", RoadKind.STREET, 5, List.of(new Pt(0, 2), new Pt(40, 2)), new int[] {streetY, streetY});
		LotBrief lot = new LotBrief("l0", 10, 5, lw, ld, Facing.NORTH, type, "medieval", 1, 2, 1, 0, 0, 60, LotBrief.BUILD);
		DistrictInfo info = new DistrictInfo(Ids.district(0, 0), 0, 0, 0, 0, 40, 24, QuarterKind.RESIDENTIAL.ordinal(), 60);
		DistrictPlan plan = new DistrictPlan(1, 7, info, List.of(street), List.of(), List.of(lot), "", List.of());
		DistrictPlan out = Briefs.fill(plan, QuarterKind.RESIDENTIAL, new StyleField((x, z) -> "minecraft:plains", 7),
				(x, z) -> frontGround + Math.max(0, z - 5) * rise / (ld - 1));
		return out.lots().get(0);
	}

	@Test
	void aStreetOnAHighEmbankmentLeavesTheLotEmpty() {
		for (String t : List.of("shop", "house", "workshop")) {
			assertEquals("empty:bank", fill(t, 70, 64, 0).status(), t + " lot, street 6 above the front row");
			assertEquals("empty:bank", fill(t, 58, 64, 0).status(), t + " lot, street 6 below the front row (a cutting)");
			assertEquals("build", fill(t, 68, 64, 0).status(), t + " lot, street 4 above: within the limit");
		}
	}

	@Test
	void aShopLotRisingTooHighAboveItsStreetIsRepickedWithoutTheShop() {
		assertEquals("shop", fill("shop", 64, 64, 6).type(), "highest ground 6 above the street");
		LotBrief l = fill("shop", 64, 64, 7);
		assertFalse(l.builds() && l.type().equals("shop"), "highest ground 7 above the street: " + l.status());
		LotBrief lowStreet = fill("shop", 61, 64, 4); // front ground 3 above the street, rising 4 more: 7 above
		assertFalse(lowStreet.builds() && lowStreet.type().equals("shop"));
	}

	@Test
	void aWorkshopIsHeldToTheShopLimitButAHouseIsNot() {
		LotBrief w = fill("workshop", 64, 64, 7);
		assertFalse(w.builds() && w.type().equals("workshop"));
		assertEquals("house", fill("house", 64, 64, 7).type());
	}

	/** The workshop half of the front-rise limit on its own (S3 verifier): gentle relief (4), so only the low street can reject it. */
	@Test
	void aWorkshopOnGentleGroundButWellAboveItsStreetIsRepickedWithoutTheWorkshop() {
		assertEquals("workshop", fill("workshop", 64, 64, 4, 14, 18).type(), "relief 4, street level with the front row: built");
		LotBrief low = fill("workshop", 61, 64, 4, 14, 18); // the same lot, the street 3 below its front row: highest ground 7 above the street
		assertFalse(low.builds() && low.type().equals("workshop"), "workshop lot 7 above its street: " + low.type() + " " + low.status());
		assertEquals("house", fill("house", 61, 64, 4, 14, 18).type(), "a house lot 7 above its street stays (the limit is 9)");
		assertFalse(fill("house", 55, 64, 4, 14, 18).builds() && fill("house", 55, 64, 4, 14, 18).type().equals("house"), "a street 9 below the front row is also a bank");
		assertEquals("house", fill("house", 60, 64, 5, 14, 18).type(), "a house lot 9 above its street: built");
		assertFalse(fill("house", 60, 64, 6, 14, 18).builds() && fill("house", 60, 64, 6, 14, 18).type().equals("house"), "a house lot 10 above its street waits for the bank buildings");
	}

	@Test
	void shopsStandOnReliefUpToAboutSixOnAMidLot() {
		assertEquals("shop", fill("shop", 64, 64, 6).type(), "relief 6 on a 10 x 14 lot (SHOP_GRADE loosened)");
	}

	/** Seed 1's hill town (streets on 12-14-block embankments, fixture {@code fly-1-hill-town}): the lots beside them stay empty, and no built house, workshop or shop has its street more than 4 off the ground in front of it. */
	@Test
	void theHillTownLeavesEmbankmentLotsEmpty() throws Exception {
		var probe = dev.fractalbuildings.core.probe.RecordedProbe.read(java.nio.file.Path.of("../fixtures/terrain/fly-1-hill-town.json.gz"));
		CityPlanner.City city = CityFillTest.city(probe, probe, 1, -1536, 4800, Tier.TOWN, 418);
		java.util.Map<String, LotBrief> byId = new java.util.TreeMap<>();
		int bank = 0;
		for (DistrictPlan p : city.districts().values()) for (LotBrief l : p.lots()) {
			byId.put(l.id(), l);
			if (l.status().equals("empty:bank")) bank++;
			if (!l.builds() || !Briefs.frontType(l.type()) || l.frontY() == dev.fractalbuildings.core.gen.Brief.NO_FRONT_Y) continue;
			int k = ((l.front().dx != 0 ? l.d() : l.w()) - 1) / 2;
			int cx = switch (l.front()) { case NORTH, SOUTH -> l.x() + k; case WEST -> l.x() - 1; default -> l.x() + l.w(); };
			int cz = switch (l.front()) { case WEST, EAST -> l.z() + k; case NORTH -> l.z() - 1; default -> l.z() + l.d(); };
			assertTrue(Math.abs(l.frontY() - probe.floorY(cx, cz)) <= Briefs.BANK_LIMIT, l.id() + " is built with its street " + l.frontY() + " over ground " + probe.floorY(cx, cz));
		}
		assertEquals("empty:bank", byId.get("dm10_31.l0").status());
		assertEquals("empty:bank", byId.get("dm9_29.l10").status());
		assertTrue(bank >= 20, "embankment lots " + bank + " (25 when written)");
	}
}
