package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.check.DoorChecks;
import dev.fractalbuildings.core.probe.RecordedProbe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DOORBANK (6.20, playtest 2026-10-28 "do they take a ladder to get up to the road?"): no built shop, house or workshop has its front door 3 or more below the
 * street in front of it with no walkable way up to that street; the lot is left empty ({@code empty:bank}) instead. The control generates every lot without the
 * rule ({@link DistrictScenes#generateLot}) and counts the doors the check finds, so the check is known to bite.
 */
class DoorBankTest {
	record Site(String label, long seed, String file, int x, int z, Tier tier, int radius) {}

	static List<Site> sites() throws Exception {
		List<Site> out = new ArrayList<>();
		out.add(new Site("river-town", 12345, "w12345-river-s8.json.gz", -900, -3500, Tier.TOWN, 300));
		out.add(new Site("hill-town", 1, "fly-1-hill-town.json.gz", -1536, 4800, Tier.TOWN, 418));
		String only = System.getenv().getOrDefault("FB_BANK_SITES", "gate-snow-city,gate-temperate-city"); List<String> rows = Files.readAllLines(Path.of("../fixtures/terrain/gate-sites.tsv"));
		for (String r : rows.subList(1, rows.size())) {
			String[] f = r.split("\t");
			if (f.length < 6 || !java.util.List.of(only.split(",")).contains(f[0]) && !only.equals("all")) continue;
			out.add(new Site(f[0], Long.parseLong(f[1]), f[0] + ".json.gz", Integer.parseInt(f[2]), Integer.parseInt(f[3]), Tier.of(f[4]), Integer.parseInt(f[5])));
		}
		return out;
	}

	/** {lots built with a bank door without the rule, bank lots emptied by the rule, built lots with a bank door with the rule}. */
	static int[] count(Site s, StringBuilder by) throws Exception {
		RecordedProbe p = RecordedProbe.read(Path.of("../fixtures/terrain/" + s.file()));
		CityPlanner.City city = CityFillTest.city(p, p, s.seed(), s.x(), s.z(), s.tier(), s.radius());
		int control = 0, emptied = 0, left = 0, lostLandmarks = 0;
		java.util.TreeMap<String, Integer> kinds = new java.util.TreeMap<>();
		for (DistrictPlan plan : new ArrayList<>(city.districts().values())) {
			for (int i = 0; i < plan.lots().size(); i++) {
				LotBrief l = plan.lots().get(i);
				if (!l.builds()) continue;
				var ground = Briefs.withStreetFront(plan, l, p);
				DistrictScenes.Generated g = DistrictScenes.generateLot(plan.seed(), i, l, 0, ground, p);
				if (g != null && DoorChecks.bankDoor(g.lot(), g.building(), ground)) control++;
			}
			DistrictScenes.Lots gen = DistrictScenes.generateLots(plan, p, p);
			for (int i = 0; i < gen.lots().size(); i++) if (gen.lots().get(i).status().equals("empty:bank") && plan.lots().get(i).builds()) { emptied++; LotBrief pl = plan.lots().get(i); kinds.merge(pl.type() + "@" + (pl.density() / 10 * 10), 1, Integer::sum); } // lots the rule emptied (the planner's own empty:bank lots are not built to begin with)
			for (dev.fractalbuildings.core.settlement.Landmark lm : city.plan().landmarks()) { // a listed hall or temple stands on a built lot
				if (lm.kind() != LandmarkKind.HALL && lm.kind() != LandmarkKind.TEMPLE) continue;
				for (int i = 0; i < plan.lots().size(); i++) { LotBrief l = plan.lots().get(i); if (l.x() == lm.x() && l.z() == lm.z() && l.w() == lm.w() && l.d() == lm.d() && !gen.lots().get(i).builds()) { lostLandmarks++; by.append("  LANDMARK ").append(lm.id()).append(" lot ").append(l.id()).append(" is ").append(gen.lots().get(i).status()).append('\n'); } }
			}
			for (DistrictScenes.Generated g : gen.generated())
				if (DoorChecks.bankDoor(g.lot(), g.building(), Briefs.withStreetFront(plan, plan.lots().get(g.index()), p))) left++;
		}
		by.append("  emptied by type@density: ").append(kinds).append('\n');
		return new int[] {control, emptied, left, lostLandmarks};
	}

	@Test
	void noBuiltFrontDoorFacesABankItCannotClimb() throws Exception {
		int control = 0, emptied = 0;
		StringBuilder log = new StringBuilder();
		for (Site s : sites()) {
			StringBuilder by = new StringBuilder();
			int[] c = count(s, by);
			log.append(s.label()).append(": control ").append(c[0]).append(", emptied ").append(c[1]).append(", left ").append(c[2]).append(", landmarks lost ").append(c[3]).append('\n').append(by);
			assertEquals(0, c[3], s.label() + " lost a landmark lot\n" + by);
			assertEquals(0, c[2], s.label() + " has built lots with a bank door");
			control += c[0];
			emptied += c[1];
		}
		System.out.println("DOORBANK " + log);
		assertTrue(control > 0, "the check finds no door on the old rule: it cannot fail\n" + log);
		assertEquals(control, emptied, "every control case is emptied\n" + log);
	}
}
