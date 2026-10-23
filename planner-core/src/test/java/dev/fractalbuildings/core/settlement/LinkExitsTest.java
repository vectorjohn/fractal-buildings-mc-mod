package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionMap;
import dev.fractalbuildings.core.region.RegionPlanner;
import dev.fractalbuildings.core.region.RegionProbe;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.road.RoadPort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * SK required exits and approaches (ADR 0004 N5, ROADMAP 6.10) on the recorded world 12345: each road port of a settlement is served by one
 * member cell with an exit toward it and an approach over dry ground; a settlement with no links is planned exactly as before.
 */
class LinkExitsTest {
	static final long SEED = 12345;
	private static final Path FIXTURES = Files.isDirectory(Path.of("../fixtures/terrain")) ? Path.of("../fixtures/terrain") : Path.of("fixtures/terrain");

	record Fx(RecordedProbe rec, RegionField field, FieldRegionView fv, RoadNet net, FieldSiteView withRoads, FieldSiteView plain, CoarseProbe coarse) {
		static Fx load() throws IOException {
			RecordedProbe r = RecordedProbe.read(FIXTURES.resolve("world-" + SEED + "-s64.json.gz"));
			RegionMap.Window w = new RegionMap.Window(r.x0(), r.z0(), r.nx(), r.nz());
			RegionField f = new RegionField(SEED, 1024);
			RegionPlanner p = new RegionPlanner(f, RegionProbe.of(r, r));
			FieldRegionView fv = new FieldRegionView(p, w);
			RoadNet net = new RoadNet(f, fv, w, RegionProbe.of(r, r), r);
			return new Fx(r, f, fv, net, new FieldSiteView(fv, f, net), new FieldSiteView(fv, f), CoarseProbe.of(r, r, 32));
		}

		SettlementSite site(int cx, int cz) { return withRoads.siteOf(new Cell(cx, cz)).orElseThrow(); }

		SettlementPlan plan(FieldSiteView v, SettlementSite s) { return Skeletons.plan(SEED, v, s, coarse, GeneratorV1.GEN_VERSION); }
	}

	/** town, village, hamlet and a two-cell city with links in the world's recorded cells (see the roads report). */
	static final int[][] CELLS = {{3, 7}, {-11, -3}, {10, 1}, {2, 1}, {4, 0}};

	@Test
	void everyPortGetsAnExitAndADryApproach() throws IOException {
		Fx fx = Fx.load();
		int served = 0, ports = 0;
		for (int[] c : CELLS) {
			SettlementSite site = fx.site(c[0], c[1]);
			List<RoadPort> want = fx.withRoads.ports(site);
			assertFalse(want.isEmpty(), site.id() + " has links");
			SettlementPlan plan = fx.plan(fx.withRoads, site);
			assertTrue(plan.violations().isEmpty(), site.id() + ": " + plan.violations());
			int unservedAllowed = 0; // a meeting point of a touching pair that lies on water has no approach (no bridges)
			for (RoadPort p : want) if (p.meeting() && fx.coarse.water(Math.floorDiv(p.x() + 16, 32) * 32, Math.floorDiv(p.z() + 16, 32) * 32)) unservedAllowed++;
			ports += want.size() - unservedAllowed;
			served += plan.approaches().size();
			assertEquals(want.size() - unservedAllowed, plan.approaches().size(), site.id() + ": one approach per port that is dry");
			List<Pt> portPts = new ArrayList<>();
			for (RoadPort p : want) portPts.add(new Pt(Math.floorDiv(p.x() + 16, 32) * 32, Math.floorDiv(p.z() + 16, 32) * 32));
			for (Road a : plan.approaches()) {
				Pt first = a.points().get(0), last = a.points().get(a.points().size() - 1);
				assertTrue(portPts.contains(last), site.id() + ": approach " + a.id() + " ends at " + last + ", not at a port " + portPts);
				boolean atExit = false;
				for (Gate g : plan.gates()) atExit |= g.kind() == GateKind.EXIT && g.x() == first.x() && g.z() == first.z();
				assertTrue(atExit, site.id() + ": approach " + a.id() + " starts at " + first + ", on no exit gate");
				// the water rule: no bridges, and every lattice column of the path dry on the coarse lattice
				assertTrue(a.bridges().isEmpty());
				for (int i = 1; i < a.points().size(); i++) {
					Pt p = a.points().get(i - 1), q = a.points().get(i);
					int n = Math.max(Math.abs(q.x() - p.x()), Math.abs(q.z() - p.z())) / 32;
					for (int k = 0; k <= n; k++) {
						int x = p.x() + (q.x() - p.x()) * k / Math.max(n, 1), z = p.z() + (q.z() - p.z()) * k / Math.max(n, 1);
						assertFalse(fx.coarse.water(x, z), site.id() + ": approach " + a.id() + " is wet at " + x + "," + z);
					}
				}
				// a port-facing exit: the approach leaves toward the port, not away from it
				Pt hub = plan.roads().get(0).points().get(0);
				double toPort = Math.atan2(last.z() - hub.z(), last.x() - hub.x()), toExit = Math.atan2(first.z() - hub.z(), first.x() - hub.x());
				double d = Math.abs(Math.atan2(Math.sin(toPort - toExit), Math.cos(toPort - toExit)));
				assertTrue(d < Math.toRadians(100), site.id() + ": exit " + Math.toDegrees(d) + " degrees off the port");
			}
		}
		assertTrue(ports >= 10 && served == ports, "ports " + ports + ", approaches " + served);
	}

	@Test
	void aSettlementWithoutLinksIsPlannedExactlyAsBefore() throws IOException {
		Fx fx = Fx.load();
		RoadNet net = fx.net;
		int checked = 0;
		for (RoadNet.Settlement s : net.allSettlements()) {
			if (!net.edgesOf(s).isEmpty() || s.r() > 200) continue;
			SettlementSite site = fx.site(s.owner().cx(), s.owner().cz());
			assertTrue(fx.withRoads.ports(site).isEmpty());
			String a = SettlementJson.write(fx.plan(fx.withRoads, site)), b = SettlementJson.write(fx.plan(fx.plain, site));
			assertEquals(b, a, site.id());
			assertFalse(a.contains("approaches"));
			if (++checked >= 3) break;
		}
		assertEquals(3, checked, "unlinked small settlements found");
	}

	@Test
	void planIsIndependentOfCellOrderAndRoundTripsThroughJson() throws IOException {
		Fx fx = Fx.load();
		SettlementSite site = fx.site(2, 1); // a two-cell city: each port is served by one of the two cells
		assertEquals(2, site.cells().size());
		SettlementPlan plan = fx.plan(fx.withRoads, site);
		String ref = SettlementJson.write(plan);
		assertFalse(plan.approaches().isEmpty());
		for (int s = 0; s < 3; s++) {
			List<Cell> cells = new ArrayList<>(site.cells());
			Collections.shuffle(cells, new Random(s));
			DistrictGrid grid = new DistrictGrid(SEED);
			List<CellSkeleton> sk = new ArrayList<>();
			for (Cell c : cells) sk.add(Skeletons.build(SEED, fx.withRoads, c, fx.coarse, grid));
			assertEquals(ref, SettlementJson.write(Skeletons.assemble(SEED, fx.withRoads, site, fx.coarse, GeneratorV1.GEN_VERSION, sk)), "cells built in order " + s);
		}
		SettlementPlan back = SettlementJson.read(ref);
		assertEquals(plan.approaches(), back.approaches().stream().map(a -> a).toList());
		assertEquals(ref, SettlementJson.write(back));
		// exactly one cell serves each port
		int total = 0;
		for (Cell c : site.cells()) total += Skeletons.build(SEED, fx.withRoads, c, fx.coarse, new DistrictGrid(SEED)).approaches().size();
		assertEquals(fx.withRoads.ports(site).size(), total);
	}
}
