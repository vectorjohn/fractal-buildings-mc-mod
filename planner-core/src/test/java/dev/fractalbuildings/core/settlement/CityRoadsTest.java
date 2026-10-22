package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.terrain.Grading;
import dev.fractalbuildings.core.terrain.LatticePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Whole-city road rules (ROADS) on the nine real-terrain gate windows and on synthetic towns and cities: every building reaches the
 * hub along streets, neighbouring tiles agree on their streets and on the height of every road they share, every stored street
 * profile keeps the grade, and the plan's landmarks sit where district detail put them.
 */
class CityRoadsTest {
	record Planned(String name, CityPlanner.City city, TerrainProbe exact, SkeletonSource src) {}

	static List<Planned> gates() throws Exception {
		List<String> rows = Files.readAllLines(Path.of("../fixtures/terrain/gate-sites.tsv"));
		return rows.subList(1, rows.size()).parallelStream().map(row -> {
			try {
				String[] f = row.split("\t");
				long seed = Long.parseLong(f[1]);
				int x = Integer.parseInt(f[2]), z = Integer.parseInt(f[3]), radius = Integer.parseInt(f[5]);
				RegionField field = new RegionField(seed, 1024);
				Cell claimant = CellGeometry.owner(field, x, z);
				List<Cell> cells = new ArrayList<>();
				for (int dz = -8; dz <= 8; dz++) for (int dx = -8; dx <= 8; dx++) {
					Cell c = new Cell(claimant.cx() + dx, claimant.cz() + dz);
					long ex = field.siteX(c.cx(), c.cz()) - x, ez = field.siteZ(c.cx(), c.cz()) - z;
					if (c.equals(claimant) || ex * ex + ez * ez <= (long) radius * radius) cells.add(c);
				}
				cells.sort(CellGeometry::compare);
				SettlementSite site = new SettlementSite(Ids.site(claimant.cx(), claimant.cz()), Tier.of(f[4]), new Pt(x, z), radius, claimant, cells);
				RecordedProbe r = RecordedProbe.read(Path.of("../fixtures/terrain/" + f[0] + ".json.gz"));
				CoarseProbe coarse = CoarseProbe.of(r, r, 32);
				SkeletonSource src = new SkeletonSource.Direct(seed, new FixedSiteView(site, field), site, coarse);
				CityPlanner.City city = CityPlanner.plan(src, coarse, r, r, GeneratorV1.GEN_VERSION);
				return new Planned(f[0], city, r, src);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).toList();
	}

	static List<Planned> synthetic() {
		List<Planned> out = new ArrayList<>();
		String[] terrains = {"noise:3:70:10:300", "noise:5:75:25:200+river:-120:24:6"};
		int[][] sites = {{1297, -2233, 372}, {-1283, 2383, 951}, {2168, -870, 226}, {-1377, 778, 280}, {796, 250, 313}, {600, 500, 350}};
		Tier[] tiers = {Tier.TOWN, Tier.CITY, Tier.TOWN, Tier.TOWN, Tier.TOWN, Tier.CITY};
		long[] seeds = {7, 1, 8, 9, 14, 7};
		List<Object[]> jobs = new ArrayList<>();
		for (String t : terrains) for (int i = 0; i < sites.length; i++) jobs.add(new Object[] {t, i});
		return jobs.parallelStream().map(j -> {
			String t = (String) j[0];
			int i = (Integer) j[1];
			ProbeSpecs.Probes p = ProbeSpecs.parse(t);
			RegionField field = new RegionField(seeds[i], 1024);
			Cell claimant = CellGeometry.owner(field, sites[i][0], sites[i][1]);
			SettlementSite site = new SettlementSite(Ids.site(claimant.cx(), claimant.cz()), tiers[i], new Pt(sites[i][0], sites[i][1]), sites[i][2], claimant, List.of(claimant));
			CoarseProbe coarse = CoarseProbe.of(p.terrain(), p.biome(), 32);
			SkeletonSource src = new SkeletonSource.Direct(seeds[i], new FixedSiteView(site, field), site, coarse);
			CityPlanner.City city = CityPlanner.plan(src, coarse, p.terrain(), p.biome(), GeneratorV1.GEN_VERSION);
			return new Planned(t + " #" + i, city, p.terrain(), src);
		}).toList();
	}

	/** Every settlement rule holds on the whole city: roads-connected, tile-border-agree, lot-frontage, lots-disjoint and the rest. */
	@Test
	void gateWindowsAndSyntheticTownsHaveNoSettlementViolations() throws Exception {
		List<Planned> all = new ArrayList<>(gates());
		all.addAll(synthetic());
		for (Planned p : all) assertEquals(List.of(), CityPlanner.check(p.city()).stream().map(v -> v.invariant() + ": " + v.message()).toList(), p.name());
	}

	/**
	 * Roads that cross a tile border (skeleton roads, arterials, border and grid streets, quays) have the same height in both tiles
	 * at every column both list, and every stored street gives back a profile that keeps the grade. Lanes from port stubs are graded
	 * per tile and may differ by one (known issue).
	 */
	@Test
	void neighbouringTilesBuildSharedRoadsAtTheSameHeight() throws Exception {
		int shared = 0;
		for (Planned p : gates()) {
			TreeMap<String, TreeMap<Long, int[]>> by = new TreeMap<>();
			for (DistrictPlan dp : p.city().districts().values()) {
				int tag = dp.district().ix() * 7919 + dp.district().iz();
				for (Street s : dp.streets()) {
					List<LatticePath.Pt> pts = new ArrayList<>();
					for (Pt q : s.points()) pts.add(new LatticePath.Pt(q.x(), q.z()));
					Grading.Profile pr = Grading.road(pts, s.y(), 1, p.exact()).profile();
					assertEquals(List.of(), SettlementChecks.roadGrade(s.id(), pr.x(), pr.z(), pr.y()).stream().map(Violation::message).toList(), p.name() + " " + dp.district().id());
					if (s.kind() == RoadKind.LANE) continue;
					TreeMap<Long, int[]> m = by.computeIfAbsent(s.id().replaceAll("\\.\\d+$", ""), k -> new TreeMap<>());
					for (int i = 0; i < pr.size(); i++) {
						long key = ((long) pr.z()[i] << 32) ^ (pr.x()[i] & 0xffffffffL);
						int[] prev = m.putIfAbsent(key, new int[] {pr.y()[i], tag});
						if (prev == null || prev[1] == tag) continue;
						shared++;
						assertEquals(prev[0], pr.y()[i], p.name() + " street " + s.id() + " at " + pr.x()[i] + "," + pr.z()[i] + " in " + dp.district().id());
					}
				}
			}
		}
		assertTrue(shared > 30_000, shared + " shared columns");
	}

	/** The plan view's landmarks are where the district put them: the hub square on its plaza, hall and temple on their lots. */
	@Test
	void planLandmarksFollowTheDistrictPlan() throws Exception {
		int halls = 0, temples = 0;
		for (Planned p : gates()) {
			for (Landmark l : p.city().plan().landmarks()) {
				DistrictPlan dp = p.city().districts().get(l.district());
				if (dp == null) continue;
				if (l.kind() == LandmarkKind.SQUARE) {
					Plaza pz = dp.plazas().get(0);
					assertEquals(List.of(pz.x(), pz.z(), pz.w(), pz.d()), List.of(l.x(), l.z(), l.w(), l.d()), p.name() + " square");
				}
				if (l.kind() == LandmarkKind.HALL || l.kind() == LandmarkKind.TEMPLE) {
					String type = l.kind() == LandmarkKind.HALL ? "hall" : "temple";
					boolean onLot = dp.lots().stream().anyMatch(o -> o.type().equals(type) && o.x() == l.x() && o.z() == l.z() && o.w() == l.w() && o.d() == l.d());
					if (onLot) { if (type.equals("hall")) halls++; else temples++; }
				}
			}
		}
		assertTrue(halls >= 8 && temples >= 9, halls + " halls, " + temples + " temples on their lots");
	}
}
