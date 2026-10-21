package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.RegionField;
import org.junit.jupiter.api.Test;

class DensityFieldTest {
	static DensityField field(long seed, Tier tier, int radius, CoarseProbe probe) {
		Pt centre = new Pt(100, -200);
		SettlementSite s = new SettlementSite("s", tier, centre, radius, new Cell(0, 0), java.util.List.of(new Cell(0, 0)));
		return new DensityField(seed, s, new CoarseTerrain(probe), new DistrictGrid(seed));
	}

	@Test
	void densityIsPointwiseCappedAndZeroOutside() {
		CoarseProbe probe = SkeletonFixtures.probe("noise:3:70:10:300");
		for (Tier tier : Tier.values()) {
			DensityField a = field(5, tier, 700, probe), b = field(5, tier, 700, probe);
			int inside = 0;
			for (int i = 0; i < 600; i++) {
				int x = Hash64.below(Hash64.hash(5, "x", i), 2400) - 1100, z = Hash64.below(Hash64.hash(5, "z", i), 2400) - 1300;
				int d = a.at(x, z);
				assertEquals(d, b.at(x, z), "a fresh field answers the same");
				assertEquals(d, a.at(x, z), "asking twice answers the same");
				assertTrue(d >= 0 && d <= tier.densityCap, tier + " density " + d);
				if (!a.inside(x, z)) assertEquals(0, d);
				else inside++;
			}
			assertTrue(inside > 50);
		}
	}

	@Test
	void densityFallsFromTheCentreToTheEdge() {
		CoarseProbe probe = SkeletonFixtures.probe("flat:64");
		DensityField f = field(8, Tier.METROPOLIS, 1500, probe);
		long near = 0, far = 0;
		int n = 0;
		for (int a = 0; a < 360; a += 15) {
			double rad = Math.toRadians(a);
			near += f.base((int) (100 + 150 * Math.cos(rad)), (int) (-200 + 150 * Math.sin(rad)));
			far += f.base((int) (100 + 1300 * Math.cos(rad)), (int) (-200 + 1300 * Math.sin(rad)));
			n++;
		}
		assertTrue(near / n > 85, "metropolis centre density " + near / n);
		assertTrue(far / n < 40, "metropolis outskirts density " + far / n);
	}

	@Test
	void theFootprintIsDeformedButBounded() {
		DensityField f = field(9, Tier.CITY, 1000, SkeletonFixtures.probe("flat:64"));
		double lo = 1e9, hi = 0;
		for (int a = 0; a < 720; a++) {
			double r = f.radiusAt(Math.toRadians(a / 2.0));
			lo = Math.min(lo, r);
			hi = Math.max(hi, r);
		}
		assertTrue(lo >= 1000 * (1 - DensityField.WOBBLE) - 1e-6 && hi <= 1000 * (1 + DensityField.WOBBLE) + 1e-6, lo + ".." + hi);
		assertTrue(hi - lo > 100, "the edge is not a circle: " + lo + ".." + hi);
	}

	@Test
	void quartersFollowTheirRules() {
		// a river through the site: waterfront within 48 of it for towns and up, none for a village
		CoarseProbe probe = SkeletonFixtures.probe("flat:64+river:300:40:6");
		for (Tier tier : new Tier[] {Tier.VILLAGE, Tier.TOWN, Tier.CITY, Tier.METROPOLIS}) {
			Pt centre = new Pt(100, -200);
			SettlementSite s = new SettlementSite("s", tier, centre, 900, new Cell(0, 0), java.util.List.of(new Cell(0, 0)));
			QuarterField q = new QuarterField(3, s, new CoarseTerrain(probe));
			if (tier.atLeast(Tier.TOWN)) assertEquals(QuarterKind.WATERFRONT, q.at(290, 0, 40), tier + " beside the river");
			else assertNotEquals(QuarterKind.WATERFRONT, q.at(290, 0, 40), tier + " has no waterfront");
			assertNotEquals(QuarterKind.WATERFRONT, q.at(-500, 0, 40), "far from the river");
			assertEquals(QuarterKind.OUTSKIRTS, q.at(-500, 0, 10));
			if (tier == Tier.METROPOLIS) assertEquals(QuarterKind.DOWNTOWN, q.at(-500, 0, 90));
			else assertNotEquals(QuarterKind.DOWNTOWN, q.at(-500, 0, 90));
		}
	}

	@Test
	void quartersAreAPointwiseMixAcrossTheSite() {
		CoarseProbe probe = SkeletonFixtures.probe("flat:64");
		SettlementSite s = new SettlementSite("s", Tier.CITY, new Pt(0, 0), 1200, new Cell(0, 0), java.util.List.of(new Cell(0, 0)));
		QuarterField q = new QuarterField(11, s, new CoarseTerrain(probe));
		java.util.Set<QuarterKind> seen = new java.util.TreeSet<>();
		for (int z = -1100; z <= 1100; z += 64) for (int x = -1100; x <= 1100; x += 64) {
			QuarterKind k = q.at(x, z, 50);
			assertEquals(k, new QuarterField(11, s, new CoarseTerrain(probe)).at(x, z, 50));
			seen.add(k);
		}
		assertTrue(seen.size() >= 3, "kinds seen: " + seen);
		assertTrue(seen.contains(QuarterKind.RESIDENTIAL));
	}
}
