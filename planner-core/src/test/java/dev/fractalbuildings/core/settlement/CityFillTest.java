package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.RegionField;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CITYQ: the street lattice reaches the built-up area of two settlements the B9 fly-through showed as sparse, on vanilla terrain
 * recorded around them (fixtures/terrain/fly-*.json.gz, step 8). Seed 12345 cell 1,-1 is a snowy coastal city (radius 807, frozen
 * rivers, a fifth water); seed 1 cell -2,4 is the dropped metropolis claimant that is now a town (radius 418, a quarter of its disk
 * steeper than 3 in 4). Before CITYQ the lattice's plain streets needed two of their three sample points buildable (dry, not a peak
 * and not steep by the 4-neighbour test) and a rise of at most 1 in 4, so whole urban tiles were cut off from the hub and dropped
 * every street and lot: 517 built lots and 67 % of the urban mask in tiles with lots on the city, 59 and 29 % on the town.
 */
class CityFillTest {
	record Fill(int built, double served) {}

	static CityPlanner.City city(dev.fractalbuildings.core.probe.TerrainProbe t, dev.fractalbuildings.core.probe.BiomeProbe b, long seed, int x, int z, Tier tier, int radius) {
		RegionField field = new RegionField(seed, 1024);
		Cell claimant = CellGeometry.owner(field, x, z);
		List<Cell> cells = new ArrayList<>();
		for (int dz = -8; dz <= 8; dz++) for (int dx = -8; dx <= 8; dx++) {
			Cell c = new Cell(claimant.cx() + dx, claimant.cz() + dz);
			long ex = field.siteX(c.cx(), c.cz()) - x, ez = field.siteZ(c.cx(), c.cz()) - z;
			if (c.equals(claimant) || ex * ex + ez * ez <= (long) radius * radius) cells.add(c);
		}
		cells.sort(CellGeometry::compare);
		SettlementSite site = new SettlementSite(Ids.site(claimant.cx(), claimant.cz()), tier, new Pt(x, z), radius, claimant, cells);
		CoarseProbe coarse = CoarseProbe.of(t, b, 32);
		SkeletonSource src = new SkeletonSource.Direct(seed, new FixedSiteView(site, field), site, coarse);
		return CityPlanner.plan(src, coarse, t, b, GeneratorV1.GEN_VERSION);
	}

	static Fill plan(String file, long seed, int x, int z, Tier tier, int radius) throws Exception {
		RecordedProbe r = RecordedProbe.read(Path.of("../fixtures/terrain/" + file));
		CityPlanner.City city = city(r, r, seed, x, z, tier, radius);
		assertTrue(CityPlanner.check(city).isEmpty(), file + ": " + CityPlanner.check(city));
		CoarseRaster c = city.plan().coarse();
		int[] mask = c.mask();
		int built = 0, urban = 0, served = 0;
		for (DistrictPlan p : city.districts().values()) {
			long b = p.lots().stream().filter(LotBrief::builds).count();
			built += (int) b;
			DistrictInfo d = p.district();
			for (int j = 0; j < c.nz(); j++) for (int i = 0; i < c.nx(); i++) {
				int px = c.x0() + i * c.step(), pz = c.z0() + j * c.step();
				if (px < d.x() || px >= d.x() + d.w() || pz < d.z() || pz >= d.z() + d.d() || mask[c.index(i, j)] != CoarseRaster.URBAN) continue;
				urban++;
				if (b > 0) served++;
			}
		}
		return new Fill(built, served / (double) Math.max(1, urban));
	}

	@Test
	void theSnowyCoastCityIsBuiltAcrossItsLand() throws Exception {
		Fill f = plan("fly-12345-coast-city.json.gz", 12345, 1152, -256, Tier.CITY, 807);
		assertTrue(f.built() >= 700, "built lots " + f.built() + " (517 before CITYQ)");
		assertTrue(f.served() >= 0.85, "urban mask in tiles with lots " + f.served() + " (0.67 before)");
	}

	@Test
	void theHillTownReachesMoreOfItsTiles() throws Exception {
		Fill f = plan("fly-1-hill-town.json.gz", 1, -1536, 4800, Tier.TOWN, 418);
		assertTrue(f.built() >= 110, "built lots " + f.built() + " (59 before CITYQ)");
		assertTrue(f.served() >= 0.45, "urban mask in tiles with lots " + f.served() + " (0.29 before)");
	}

	/**
	 * CITYQ: a plaza moved off rough ground that a street meets only diagonally, at a corner, reaches no road. District detail used to
	 * keep it (its floating-plaza test counted the corner cells) while the city flood is 4-connected, so roads-attached failed on this
	 * steep synthetic town once the lattice drew more streets.
	 */
	@Test
	void aPlazaMetOnlyAtACornerIsDropped() {
		var p = dev.fractalbuildings.core.probe.ProbeSpecs.parse("noise:9:90:40:150");
		CityPlanner.City city = city(p.terrain(), p.biome(), 9, 64, 1604, Tier.TOWN, 262);
		assertEquals(List.of(), CityPlanner.check(city));
		assertTrue(city.districts().get("d0_10").plazas().isEmpty(), "the moved market square touches the main road only at a corner");
	}
}
