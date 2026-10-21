package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementJsonTest {
	private static final SampleSettlement.Sample S = SampleSettlement.sample();
	private static final String SETTLEMENT = SettlementJson.write(S.settlement());
	private static final String DISTRICT = DistrictJson.write(S.districts().get("d0_0"));

	private static String msg(Runnable r) {
		return assertThrows(IllegalArgumentException.class, r::run).getMessage();
	}

	@Test
	void settlementRoundTripsToEqualBytesAndEqualRecords() {
		SettlementPlan back = SettlementJson.read(SETTLEMENT);
		assertEquals(SETTLEMENT, SettlementJson.write(back));
		assertEquals(S.settlement().site(), back.site());
		assertEquals(S.settlement().coarse(), back.coarse());
		assertEquals(S.settlement().districts(), back.districts());
		assertEquals(S.settlement().gates(), back.gates());
		assertEquals(S.settlement().landmarks(), back.landmarks());
		assertEquals(S.settlement().roads().size(), back.roads().size());
		assertEquals(List.of(1, 2), List.of(back.roads().get(0).bridges().get(0)[0], back.roads().get(0).bridges().get(0)[1]));
	}

	@Test
	void everyDistrictRoundTrips() {
		for (DistrictPlan d : S.districts().values()) {
			String j = DistrictJson.write(d);
			DistrictPlan back = DistrictJson.read(j);
			assertEquals(j, DistrictJson.write(back));
			assertEquals(d.lots(), back.lots());
			assertEquals(d.plazas(), back.plazas());
			assertEquals(d.streets(), back.streets());
			assertEquals(d.district(), back.district());
		}
	}

	@Test
	void settlementReaderIsStrict() {
		assertTrue(msg(() -> SettlementJson.read(SETTLEMENT.replace("fb-settlement/1", "fb-settlement/2"))).startsWith("$.format"));
		assertTrue(msg(() -> SettlementJson.read(SETTLEMENT.replace("\"planVersion\": 1", "\"planVersion\": 9"))).startsWith("$.planVersion"));
		assertTrue(msg(() -> SettlementJson.read(SETTLEMENT.replace("\"site\":", "\"sitex\":"))).startsWith("$.site: missing"));
		assertTrue(msg(() -> SettlementJson.read(SETTLEMENT.replace("\"tier\": \"town\"", "\"tier\": \"megacity\""))).contains("$.site.tier"));
		assertTrue(msg(() -> SettlementJson.read(SETTLEMENT.replace("\"seed\": \"4242\"", "\"seed\": 4242"))).startsWith("$.seed"));
		// a road with a bent segment (neither axis-aligned nor 45 degrees)
		String bent = SETTLEMENT.replace("[[4, 210], [196, 210]]", "[[4, 210], [196, 215]]");
		assertTrue(msg(() -> SettlementJson.read(bent)).contains("$.roads[2]"));
		// a landmark in a district that does not exist
		assertTrue(msg(() -> SettlementJson.read(SETTLEMENT.replace("\"district\": \"d1_0\"", "\"district\": \"d9_9\""))).contains("unknown district"));
		// a district that is not chunk aligned
		assertTrue(msg(() -> SettlementJson.read(SETTLEMENT.replace("\"x\": 144, \"z\": 0, \"w\": 128", "\"x\": 145, \"z\": 0, \"w\": 128"))).contains("$.districts[1]"));
		// a coarse array of the wrong length
		assertTrue(msg(() -> SettlementJson.read(SETTLEMENT.replace("\"nx\": 10", "\"nx\": 11"))).contains("$.coarse"));
	}

	@Test
	void districtReaderRejectsALotOutsideItsTile() {
		String outside = DISTRICT.replace("\"id\": \"d0_0.l0\", \"x\": 8,", "\"id\": \"d0_0.l0\", \"x\": 140,");
		String m = msg(() -> DistrictJson.read(outside));
		assertTrue(m.startsWith("$.lots[0]") && m.contains("outside"), m);
		assertTrue(msg(() -> DistrictJson.read(DISTRICT.replace("fb-district/1", "fb-scene/1"))).startsWith("$.format"));
		assertTrue(msg(() -> DistrictJson.read(DISTRICT.replace("\"front\": \"south\"", "\"front\": \"up\""))).contains("$.lots[0].front"));
		assertTrue(msg(() -> DistrictJson.read(DISTRICT.replace("\"status\": \"build\"", "\"status\": \"maybe\""))).contains("status"));
		assertTrue(msg(() -> DistrictJson.read(DISTRICT.replace("\"storeys\": [1, 2]", "\"storeys\": [3, 2]"))).contains("storeys"));
		assertTrue(msg(() -> DistrictJson.read(DISTRICT.replace("\"streets\": [", "\"streetz\": ["))).contains("$.streets: missing"));
	}

	@Test
	void districtIdsUseMForNegatives() {
		assertEquals("d3_m2", Ids.district(3, -2));
		assertEquals("dm1_0", Ids.district(-1, 0));
	}
}
