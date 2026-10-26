package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.probe.RecordedProbe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * DS-18, built edges taper too (CITYQ r4, architect review): a high-rise must not stand where the built-up area ends. The
 * architect counted 11 of 78 high-rises on the 12345 coast city with no built lot within 120 blocks outward. The denser
 * lattice had opened a west quarter that ended in a row of 15-storey towers. {@link BuiltEdge} caps a lot's class by how
 * far the built-up area goes on outward; {@link SettlementChecks#edgeLots} counts what is left.
 *
 * <p>A ratchet, not zero: the cap is per tile and reads the coarse mask, the street network and the coarse slope, but it
 * cannot see another tile's lots. Where exact relief empties a neighbouring tile that the coarse data calls buildable, a
 * tower can still end up at the edge. The bounds are today's counts. Lower them when they improve; raising them needs a reason.
 */
class EdgeTaperTest {
	static int edgeTowers(String file, long seed, int x, int z, Tier tier, int radius) throws Exception {
		RecordedProbe r = RecordedProbe.read(Path.of("../fixtures/terrain/" + file));
		CityPlanner.City city = CityFillTest.city(r, r, seed, x, z, tier, radius);
		int hr = 0;
		for (DistrictPlan p : city.districts().values()) hr += (int) p.lots().stream().filter(l -> l.builds() && l.type().equals("highrise")).count();
		int n = SettlementChecks.edgeLots(city.districts().values(), new Pt(x, z), Set.of("highrise")).size();
		System.out.println("[EDGE] " + file + ": " + n + " edge towers of " + hr + " high-rises");
		return n;
	}

	@Test
	void theCoastCityEndsInLowerBuildings() throws Exception {
		int n = edgeTowers("fly-12345-coast-city.json.gz", 12345, 1152, -256, Tier.CITY, 807);
		System.out.println("[EDGE] fly-12345-coast-city: " + n + " edge towers");
		assertTrue(n <= 2, "edge towers " + n + " (13 without BuiltEdge)");
	}

	@Test
	void theGateWindowsEndInLowerBuildings() throws Exception {
		List<String> rows = Files.readAllLines(Path.of("../fixtures/terrain/gate-sites.tsv"));
		int total = 0;
		StringBuilder sb = new StringBuilder();
		for (String row : rows.subList(1, rows.size())) {
			String[] c = row.split("\t");
			int n = edgeTowers(c[0] + ".json.gz", Long.parseLong(c[1]), Integer.parseInt(c[2]), Integer.parseInt(c[3]), Tier.valueOf(c[4].toUpperCase()), Integer.parseInt(c[5]));
			sb.append(c[0]).append(' ').append(n).append("; ");
			total += n;
		}
		System.out.println("[EDGE] gate windows: " + total + " edge towers: " + sb);
		assertTrue(total <= 15, "edge towers on the 9 gate windows " + total + " (38 without BuiltEdge): " + sb);
	}

	@Test
	void anEdgeLotHasNothingBuiltOutward() {
		// a tower at x 100 with the centre at 0: a lot 60 further out within 45 degrees keeps it off the edge; one behind it does not
		LotBrief tower = lot("t", 100, 0, "highrise"), out = lot("o", 160, 30, "house"), behind = lot("b", 40, 0, "house"), wide = lot("w", 130, 60, "house");
		Set<String> hr = Set.of("highrise");
		assertEquals(List.of(), SettlementChecks.edgeLots(List.of(plan(tower, out)), new Pt(0, 0), hr));
		assertEquals(List.of(tower), SettlementChecks.edgeLots(List.of(plan(tower, behind)), new Pt(0, 0), hr));
		assertEquals(List.of(tower), SettlementChecks.edgeLots(List.of(plan(tower, wide)), new Pt(0, 0), hr), "63 degrees off the outward line");
		assertEquals(List.of(tower), SettlementChecks.edgeLots(List.of(plan(tower, lot("f", 230, 0, "house"))), new Pt(0, 0), hr), "a lot 130 blocks out is too far");
		assertEquals(List.of(), SettlementChecks.edgeLots(List.of(plan(tower, behind)), new Pt(0, 0), Set.of("block")), "only the types asked for");
	}

	private static LotBrief lot(String id, int cx, int cz, String type) {
		return new LotBrief(id, cx - 5, cz - 5, 10, 10, dev.fractalbuildings.core.ir.Facing.NORTH, type, StyleLibrary.AUTO, 1, 1, 1, 0, Brief.NO_FRONT_Y, 50, LotBrief.BUILD);
	}

	private static DistrictPlan plan(LotBrief... lots) {
		return new DistrictPlan(2, 1, new DistrictInfo("d0_0", 0, 0, 0, 0, 128, 128, 0, 20), List.of(), List.of(), List.of(lots), null, List.of());
	}
}
