package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.RegionField;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The hub's district on each real-terrain gate window (fixtures/terrain/gate-sites.tsv) gets its central square, a hall and a temple (facing the square or on a reachable street), within a bounded probe budget. */
class HubSquareGateTest {
	/**
	 * Halls and temples that get no lot in the hub tile at all (ROADS: one that finds no room facing the square takes a lot on a
	 * reachable street of the tile instead): the metropolis-mixed hub tile is mostly lake, with dry gentle ground for the temple only.
	 */
	static final java.util.Set<String> MISSING = java.util.Set.of("gate-metropolis-mixed:hall");

	@Test
	void everyGateWindowHasItsHubSquare() throws Exception {
		List<String> rows = Files.readAllLines(Path.of("../fixtures/terrain/gate-sites.tsv"));
		assertEquals(10, rows.size());
		for (String row : rows.subList(1, rows.size())) {
			String[] f = row.split("\t");
			String name = f[0];
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
			SettlementSite site = new SettlementSite("s" + claimant.cx() + "_" + claimant.cz(), Tier.of(f[4]), new Pt(x, z), radius, claimant, cells);
			RecordedProbe r = RecordedProbe.read(Path.of("../fixtures/terrain/" + name + ".json.gz"));
			var view = new FixedSiteView(site, field);
			var coarse = CoarseProbe.of(r, r, 32);
			SettlementPlan sk = Skeletons.plan(seed, view, site, coarse, SkeletonsTest.GEN);
			Landmark hub = sk.landmarks().stream().filter(l -> l.kind() == LandmarkKind.SQUARE).min(java.util.Comparator.comparingLong(l -> (long) (l.x() - x) * (l.x() - x) + (long) (l.z() - z) * (l.z() - z))).orElseThrow(); // the square nearest the site centre (cities also have squares at arterial crossings)
			DistrictSummary tile = sk.districts().stream().filter(d -> d.id().equals(hub.district())).findFirst().orElseThrow();
			AtomicInteger calls = new AtomicInteger();
			TerrainProbe counting = new TerrainProbe() {
				@Override public int floorY(int px, int pz) { calls.incrementAndGet(); return r.floorY(px, pz); }
				@Override public int waterDepth(int px, int pz) { calls.incrementAndGet(); return r.waterDepth(px, pz); }
			};
			var det = Districts.plan(new SkeletonSource.Direct(seed, view, site, coarse), tile.ix(), tile.iz(), counting).orElseThrow();
			DistrictPlan dp = det.plan();
			assertEquals(List.of(), dp.violations().stream().map(v -> v.invariant()).toList(), name);
			assertTrue(dp.plazas().stream().anyMatch(p -> p.kind().equals("square")), name + ": no hub square; " + det.notes());
			for (String kind : List.of("hall", "temple")) {
				boolean has = dp.lots().stream().anyMatch(l -> l.type().equals(kind));
				assertEquals(!MISSING.contains(name + ":" + kind), has, name + " " + kind + "; " + det.notes());
			}
			// the first tile of a settlement also builds its junction field (JUNCTION: every whole street of the settlement, read once,
			// ~130k calls in the 8-cell metropolis, ~15k in a one-cell city); 150k before it
			assertTrue(calls.get() < 220_000, name + ": " + calls.get() + " probe calls");
			System.out.println("HUBGATE " + name + " probe calls " + calls.get());
		}
	}
}
