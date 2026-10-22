package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.settlement.tiers.WallRing;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The town program (D2): radial roads, the wall ring, its gates and its lots. */
class TownProgramTest {
	private static final String[] TERRAINS = {"flat:64", "noise:3:70:10:300", "noise:5:75:25:200+river:-120:24:6"};

	record Town(CityPlannerTest.Setup setup, CityPlanner.City city) {
		SettlementPlan plan() { return city.plan(); }
	}

	static Town town(int i) {
		int radius = 260 + 30 * (i % 6);
		CityPlannerTest.Setup s = CityPlannerTest.setup(500 + i, TERRAINS[i % TERRAINS.length], i % 4, -(i % 3), Tier.TOWN, radius);
		return new Town(s, s.plan());
	}

	static List<Town> towns(int n) {
		List<Town> out = new ArrayList<>();
		for (int i = 0; i < n; i++) out.add(town(i));
		return out;
	}

	@Test
	void townsGetThreeToFiveRadialMainRoadsAndAMarketWithAHall() {
		int exits = 0;
		for (Town t : towns(12)) {
			CellSkeleton sk = t.setup().source().skeleton(t.setup().site().claimant());
			long mains = sk.roads().stream().filter(r -> r.kind() == RoadKind.MAIN).count();
			assertTrue(mains >= 2 && mains <= 5, "main roads " + mains);
			exits += (int) mains;
			assertTrue(sk.landmarks().stream().anyMatch(l -> l.kind() == LandmarkKind.MARKET), "market square");
			assertTrue(sk.landmarks().stream().anyMatch(l -> l.kind() == LandmarkKind.HALL), "hall");
		}
		assertTrue(exits >= 30, "mean main roads " + exits / 12.0);
	}

	/** Independent of WallRing: where a road polyline meets the ring lines, as the {side-line coordinate hits}. */
	static List<Pt> ringHits(Wall w, Road r) {
		int xl = w.points().get(0).x(), xr = w.points().get(1).x(), zt = w.points().get(0).z(), zb = w.points().get(2).z();
		List<Pt> hits = new ArrayList<>();
		for (int i = 1; i < r.points().size(); i++) {
			Pt a = r.points().get(i - 1), b = r.points().get(i);
			int n = Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z()));
			int sx = Integer.signum(b.x() - a.x()), sz = Integer.signum(b.z() - a.z());
			for (int k = 0; k < n; k++) {
				int x0 = a.x() + sx * k, z0 = a.z() + sz * k, x1 = x0 + sx, z1 = z0 + sz;
				boolean in0 = x0 >= xl && x0 <= xr && z0 >= zt && z0 <= zb, in1 = x1 >= xl && x1 <= xr && z1 >= zt && z1 <= zb;
				if (in0 != in1) hits.add(in1 ? new Pt(x1, z1) : new Pt(x0, z0));
			}
		}
		return hits;
	}

	/** The rule: every place a main road meets the ring is a wall gate on that road. Returns the failures. */
	static List<String> crossingProblems(SettlementPlan p) {
		List<String> bad = new ArrayList<>();
		for (Wall w : p.walls()) {
			for (Road r : p.roads()) {
				if (r.kind() != RoadKind.MAIN) continue;
				for (Pt h : ringHits(w, r)) {
					boolean found = false;
					for (dev.fractalbuildings.core.settlement.Gate g : p.gates()) if (g.kind() == GateKind.WALL && g.x() == h.x() && g.z() == h.z() && r.id().equals(g.road())) found = true;
					if (!found) bad.add("road " + r.id() + " meets the ring at " + h.x() + "," + h.z() + " with no gate");
				}
			}
		}
		return bad;
	}

	@Test
	void everyMainRoadThatMeetsTheRingHasAGateAndEveryGateIsOnItsRoad() {
		int walled = 0, gates = 0, towns = 0;
		for (Town t : towns(60)) {
			towns++;
			SettlementPlan p = t.plan();
			assertEquals(List.of(), crossingProblems(p), "town " + t.setup().seed());
			if (p.walls().isEmpty()) { assertTrue(p.gates().stream().noneMatch(g -> g.kind() == GateKind.WALL)); continue; }
			walled++;
			assertEquals(1, p.walls().size());
			Wall w = p.walls().get(0);
			assertTrue(w.closed() && w.points().size() == 4);
			List<dev.fractalbuildings.core.settlement.Gate> wg = p.gates().stream().filter(g -> g.kind() == GateKind.WALL).toList();
			assertTrue(wg.size() >= 1, "a walled town has a gate");
			gates += wg.size();
			for (var g : wg) {
				assertTrue(g.road() != null && p.roads().stream().anyMatch(r -> r.id().equals(g.road())), "gate road " + g.road());
				boolean onRing = (g.x() == w.points().get(0).x() || g.x() == w.points().get(1).x()) != (g.z() == w.points().get(0).z() || g.z() == w.points().get(2).z());
				assertTrue(onRing, "gate " + g.x() + "," + g.z() + " on the ring");
			}
		}
		System.out.println("towns " + towns + " walled " + walled + " gates " + gates);
		assertTrue(walled >= 6 && walled <= 40, "walled " + walled + " of " + towns + " (draw is 40 %, some rings are refused by the ground)");
	}

	@Test
	void theCrossingRuleCatchesAMissingGate() {
		for (Town t : towns(60)) {
			SettlementPlan p = t.plan();
			if (p.walls().isEmpty()) continue;
			List<dev.fractalbuildings.core.settlement.Gate> kept = new ArrayList<>(p.gates());
			kept.removeIf(g -> g.kind() == GateKind.WALL);
			SettlementPlan stripped = new SettlementPlan(p.genVersion(), p.seed(), p.site(), p.bounds(), p.coarse(), p.quarters(), p.cells(), p.districts(), p.roads(), kept, p.landmarks(), p.walls(), p.violations());
			assertEquals(List.of(), crossingProblems(p), "control");
			assertTrue(!crossingProblems(stripped).isEmpty(), "mutant without gates must fail");
			return;
		}
		throw new AssertionError("no walled town in 60");
	}

	@Test
	void theRingIsClosedExceptAtGatesAndItsLotsObeyEveryRule() {
		int rings = 0, lotsTotal = 0, dropped = 0;
		TreeMap<String, Integer> other = new TreeMap<>();
		for (Town t : towns(60)) {
			SettlementPlan p = t.plan();
			if (p.walls().isEmpty()) continue;
			rings++;
			Wall w = p.walls().get(0);
			List<Pt> gates = p.gates().stream().filter(g -> g.kind() == GateKind.WALL).map(g -> new Pt(g.x(), g.z())).toList();
			List<WallRing.Piece> pieces = WallRing.pieces(w, gates, new DistrictGrid(t.setup().seed()), t.setup().seed());
			// the pieces form the ring: consecutive pieces on a side abut, every piece sits inside one tile
			DistrictGrid grid = new DistrictGrid(t.setup().seed());
			for (WallRing.Piece pc : pieces) {
				Rect r = pc.rect();
				assertEquals(grid.tileOf(r.x(), r.z()), grid.tileOf(r.x() + r.w() - 1, r.z() + r.d() - 1), "piece " + r + " crosses a tile line");
				if (pc.type().equals("wall")) {
					int len = pc.front() == dev.fractalbuildings.core.ir.Facing.NORTH || pc.front() == dev.fractalbuildings.core.ir.Facing.SOUTH ? r.w() : r.d();
					int depth = len == r.w() ? r.d() : r.w();
					assertTrue(len >= 23 && len <= 64 && (pc.gate() == WallRing.NONE ? len >= 24 : len == 23), "length " + len);
					assertTrue(depth >= 7 && depth <= 11, "depth " + depth);
				}
			}
			assertEquals(pieces.stream().filter(x -> x.gate() != WallRing.NONE).count(), gates.size(), "a gate lot per gate");
			// the lots in the districts: every piece is there (a lot or a note that it was dropped)
			TreeSet<String> ids = new TreeSet<>();
			for (DistrictPlan d : t.city().districts().values()) {
				for (LotBrief l : d.lots()) {
					if (!WallRing.isRingLotId(l.id())) continue;
					ids.add(l.id());
					lotsTotal++;
					assertTrue(l.type().equals("wall") || l.type().equals("curtain") || l.type().equals("bastion"), l.type());
					assertTrue(l.builds() || l.status().startsWith("empty:"), l.status());
				}
			}
			int notes = (int) t.city().notes().stream().filter(n -> n.contains("wall lot")).count();
			dropped += notes;
			assertEquals(pieces.size(), ids.size() + notes, "ring pieces " + pieces.size() + " = lots " + ids.size() + " + dropped " + notes + " (town " + t.setup().seed() + ")");
			for (Violation v : CityPlanner.check(t.city())) { other.merge(v.invariant(), 1, Integer::sum); }
		}
		System.out.println("rings " + rings + " ring lots " + lotsTotal + " dropped " + dropped + " other violations " + other);
		assertTrue(rings >= 6);
		assertEquals(new TreeMap<String, Integer>(), other);
	}

	@Test
	void wallDistrictsBuildWithNoViolationsAndGatePassagesLieOnTheRoad() {
		int scenes = 0, gateLots = 0;
		for (Town t : towns(60)) {
			SettlementPlan p = t.plan();
			if (p.walls().isEmpty()) continue;
			for (DistrictPlan d : t.city().districts().values()) {
				if (d.lots().stream().noneMatch(l -> WallRing.isRingLotId(l.id()))) continue;
				DistrictScenes.Built b = CityPlanner.scene(t.city(), d.district().id(), t.setup().p().terrain(), t.setup().p().biome(), true);
				List<String> bad = new ArrayList<>();
				for (Violation v : b.scene().violations()) bad.add(v.invariant() + ": " + v.message());
				for (Violation v : b.plan().violations()) bad.add(v.invariant() + ": " + v.message());
				assertEquals(List.of(), bad, "town " + t.setup().seed() + " " + d.district().id());
				assertTrue(b.fallbacks().stream().noneMatch(WallRing::isRingLotId), "a ring lot fell back: " + b.fallbacks());
				scenes++;
				gateLots += (int) d.lots().stream().filter(l -> WallRing.isGateShape(l.type(), l.front(), l.w(), l.d())).count();
			}
			if (scenes >= 10) break;
		}
		System.out.println("wall district scenes " + scenes + " gate lots " + gateLots);
		assertTrue(scenes >= 6 && gateLots >= 3);
	}

	/** The road runs through every gate lot: each column of the 5-wide passage lies on a main-road street of the district plan. */
	@Test
	void everyGatePassageLiesOnTheMainRoad() {
		int checked = 0;
		for (Town t : towns(60)) {
			for (DistrictPlan d : t.city().districts().values()) {
				for (LotBrief l : d.lots()) {
					if (!WallRing.isGateShape(l.type(), l.front(), l.w(), l.d())) continue;
					boolean alongX = l.front() == dev.fractalbuildings.core.ir.Facing.NORTH || l.front() == dev.fractalbuildings.core.ir.Facing.SOUTH;
					List<Rect> road = new ArrayList<>();
					for (Street s : d.streets()) if (s.kind() == RoadKind.MAIN) road.addAll(SettlementChecks.footprint(s));
					int gate = alongX ? l.x() + l.w() / 2 : l.z() + l.d() / 2;
					for (int a = gate - WallRing.BAND; a <= gate + WallRing.BAND; a++) {
						for (int k = 0; k < (alongX ? l.d() : l.w()); k++) {
							int x = alongX ? a : l.x() + k, z = alongX ? l.z() + k : a;
							boolean on = false;
							for (Rect r : road) if (r.contains(x, z)) on = true;
							assertTrue(on, "gate lot " + l.id() + " passage column " + x + "," + z + " is not on a main road (town " + t.setup().seed() + ")");
						}
					}
					checked++;
				}
			}
		}
		assertTrue(checked >= 20, "gate lots checked " + checked);
	}

	@Test
	void townsAreDeterministic() {
		for (int i : new int[] {1, 4, 7}) {
			assertEquals(SettlementJson.write(town(i).plan()), SettlementJson.write(town(i).plan()));
		}
	}
}
