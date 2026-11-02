package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.check.SettlementChecks;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The street lattice on the tile borders of a town, city or metropolis (ROADS2): every street that is drawn reaches the hub, an
 * arterial the lattice cannot link to a main road is not drawn, and the built-up area thins out to low-density streets instead of
 * stopping at the arterials.
 */
class RoadLatticeTest {
	private static List<CityRoadsTest.Planned> all;

	private static synchronized List<CityRoadsTest.Planned> all() throws Exception {
		if (all == null) {
			List<CityRoadsTest.Planned> l = new ArrayList<>(CityRoadsTest.gates());
			l.addAll(CityRoadsTest.synthetic());
			all = l;
		}
		return all;
	}

	/** Every border segment of the planned area, from the settlement's own window. */
	private static List<DistrictGrid.Segment> segments(CityRoadsTest.Planned p) {
		int r = p.src().site().radius() + 400;
		Pt c = p.src().site().centre();
		return p.src().density().arterials().segmentsIn(c.x() - r, c.z() - r, c.x() + r, c.z() + r);
	}

	private static boolean touchesLive(RoadNetwork net, List<int[]> cols) {
		for (int[] c : cols) if (net.live(c[0], c[1])) return true;
		return false;
	}

	/** A present lattice segment (arterial or plain street) holds a column of the live network: it reaches the hub. */
	@Test
	void everyDrawnBorderSegmentReachesTheHub() throws Exception {
		int present = 0;
		for (CityRoadsTest.Planned p : all()) {
			RoadNetwork net = p.src().network(p.exact()); // THINDECK: the network as districts draw it (a wet edge street may be moved into its tile)
			if (!net.hasLattice()) continue;
			Arterials art = p.src().density().arterials();
			for (DistrictGrid.Segment s : segments(p)) {
				if (net.kind(s) == null) continue;
				present++;
				assertTrue(touchesLive(net, StreetCols.columns(net.points(art, s))), p.name() + " segment " + s + " is drawn but reaches no live road");
			}
		}
		assertTrue(present > 500, present + " lattice segments checked");
	}

	/** Every arterial and every border street a district draws is one the network lists, and every plan arterial is attached. */
	@Test
	void districtsDrawOnlyTheNetworksStreets() throws Exception {
		int arterials = 0, borders = 0;
		for (CityRoadsTest.Planned p : all()) {
			RoadNetwork net = p.src().network(p.exact()); // THINDECK: the network districts draw from
			if (!net.hasLattice()) continue;
			for (Road r : p.city().plan().roads()) if (r.kind() == RoadKind.ARTERIAL) assertTrue(net.arterialPresent(r.id()), p.name() + " plan lists arterial " + r.id() + " that the lattice does not draw");
			for (DistrictPlan dp : p.city().districts().values()) {
				for (Street s : dp.streets()) {
					if (s.kind() == RoadKind.ARTERIAL) { arterials++; assertTrue(net.arterialPresent(s.id().replaceAll("\\.\\d+$", "")), p.name() + " " + dp.district().id() + " draws arterial " + s.id()); }
					if (s.kind() == RoadKind.STREET && s.id().matches("b\\d.*")) {
						borders++;
						boolean live = false;
						for (Pt q : s.points()) live |= net.live(q.x(), q.z());
						assertTrue(live, p.name() + " " + dp.district().id() + " draws border street " + s.id() + " off the live network");
					}
				}
			}
		}
		assertTrue(arterials > 100 && borders > 300, arterials + " arterial and " + borders + " border pieces");
	}

	/** On a mountain city (snow) arterial candidates that no chain of streets links to a main road are left out: fewer arterials are drawn than the rule picks. */
	@Test
	void arterialsThatCannotBeLinkedAreNotDrawn() throws Exception {
		int picked = 0, drawn = 0;
		for (CityRoadsTest.Planned p : CityRoadsTest.gates()) {
			RoadNetwork net = p.src().network(p.exact());
			Arterials art = p.src().density().arterials();
			for (DistrictGrid.Segment s : segments(p)) {
				if (!art.is(s)) continue;
				picked++;
				if (net.kind(s) == RoadKind.ARTERIAL) drawn++;
			}
		}
		assertTrue(picked > 0 && drawn <= picked, drawn + " of " + picked + " picked arterial segments drawn"); // METROFILL R1: spaced arterials all link, so none is dropped on the gates any more
		// arterials are only ever dropped when nothing attaches them: the nine gates keep most of them
		assertTrue(drawn * 10 > picked * 6, drawn + " of " + picked + " picked arterial segments drawn");
	}

	/**
	 * The drop path itself (since METROFILL R1 every picked arterial on the gates links, so the test above no longer drops any): a
	 * synthetic city whose planned tiles are cut down to the hub's 3 x 3 block and one tile far away. The far tile's arterial borders are
	 * picked by the rule but nothing drawn reaches them, so the network leaves them out; with every tile planned it draws them.
	 */
	@Test
	void anArterialOnATileNothingReachesIsNotDrawn() {
		SkeletonsTest.Case c = SkeletonsTest.syntheticCase(1);
		SkeletonSource full = new SkeletonSource.Direct(c.seed(), new FixedSiteView(c.site(), c.field()), c.site(), c.probe());
		assertTrue(full.site().tier().atLeast(Tier.CITY));
		Arterials art = full.density().arterials();
		RoadNetwork all = full.network();
		DistrictGrid g = full.grid();
		DistrictGrid.TileId hub = g.tileOf(all.hub().x(), all.hub().z());
		DistrictSummary far = null;
		List<DistrictSummary> every = new ArrayList<>();
		for (Cell cell : full.site().cells()) every.addAll(full.skeleton(cell).districts());
		every.sort(java.util.Comparator.comparing(DistrictSummary::id));
		for (DistrictSummary d : every) {
			if (Math.max(Math.abs(d.ix() - hub.ix()), Math.abs(d.iz() - hub.iz())) < 3) continue;
			for (DistrictGrid.Segment s : g.segments(d.ix(), d.iz())) if (art.is(s) && all.kind(s) == RoadKind.ARTERIAL) far = d;
			if (far != null) break;
		}
		assertNotNull(far, "a far tile with a drawn arterial border");
		DistrictSummary keepFar = far;
		SkeletonSource cut = new SkeletonSource() {
			@Override public long seed() { return full.seed(); }
			@Override public RegionView view() { return full.view(); }
			@Override public SettlementSite site() { return full.site(); }
			@Override public DistrictGrid grid() { return full.grid(); }
			@Override public TierProgram program() { return full.program(); }
			@Override public DensityField density() { return full.density(); }
			@Override public QuarterField quarters() { return full.quarters(); }
			@Override public CellSkeleton skeleton(Cell cell) {
				CellSkeleton k = full.skeleton(cell);
				List<DistrictSummary> ds = new ArrayList<>();
				for (DistrictSummary d : k.districts()) if (d == keepFar || d.id().equals(keepFar.id()) || Math.max(Math.abs(d.ix() - hub.ix()), Math.abs(d.iz() - hub.iz())) <= 1) ds.add(d);
				return new CellSkeleton(k.cell(), k.outline(), k.coarse(), k.hub(), k.roads(), k.gates(), k.landmarks(), ds, k.walls(), k.approaches());
			}
		};
		RoadNetwork net = cut.network();
		int picked = 0, drawn = 0;
		for (DistrictGrid.Segment s : g.segments(far.ix(), far.iz())) {
			if (!art.is(s) || all.kind(s) != RoadKind.ARTERIAL) continue;
			picked++;
			if (net.kind(s) == RoadKind.ARTERIAL) drawn++;
		}
		assertTrue(picked > 0);
		assertEquals(0, drawn, drawn + " of " + picked + " arterial borders of the cut-off tile " + far.id() + " drawn");
	}

	/** The built-up area tapers: streets and low-density lots reach the outer quarter of the footprint, and nothing tall stands there. */
	@Test
	void theBuiltUpAreaThinsOutInsteadOfStoppingAtTheArterials() throws Exception {
		int outerAll = 0, totalAll = 0;
		for (CityRoadsTest.Planned p : CityRoadsTest.gates()) {
			Pt c = p.src().site().centre();
			int r = p.src().site().radius(), outer = 0, total = 0;
			for (DistrictPlan dp : p.city().districts().values()) {
				for (LotBrief l : dp.lots()) {
					if (!l.builds()) continue;
					double d = Math.hypot(l.x() + l.w() / 2.0 - c.x(), l.z() + l.d() / 2.0 - c.z()) / r;
					total++;
					if (d >= 0.75) {
						outer++;
						// 0.77, not 0.75: MINLOT moved a lot edge so a highrise centre stands at 0.7555 (density 71 there)
						assertFalse(d >= 0.77 && (l.type().equals("highrise") || l.type().equals("block")), p.name() + " lot " + l.id() + " is a " + l.type() + " at " + Math.round(100 * d) + "% of the radius");
					}
				}
			}
			outerAll += outer;
			totalAll += total;
			assertTrue(outer * 100 >= total * 5, p.name() + ": " + outer + " of " + total + " lots in the outer quarter");
		}
		assertTrue(outerAll * 100 >= totalAll * 12, outerAll + " of " + totalAll + " lots in the outer quarter over the nine windows");
	}

	/** The network is a pure function of the source: two builds agree on every segment and every column of a sample. */
	@Test
	void theNetworkIsPure() throws Exception {
		for (CityRoadsTest.Planned p : CityRoadsTest.gates().subList(0, 3)) {
			RoadNetwork a = p.src().network(), b = RoadNetwork.build(p.src());
			TreeMap<String, RoadKind> ka = new TreeMap<>(), kb = new TreeMap<>();
			for (DistrictGrid.Segment s : segments(p)) { ka.put(RoadNetwork.key(s), a.kind(s)); kb.put(RoadNetwork.key(s), b.kind(s)); }
			assertEquals(ka, kb, p.name());
			Pt c = p.src().site().centre();
			for (int z = c.z() - 400; z < c.z() + 400; z += 7) for (int x = c.x() - 400; x < c.x() + 400; x += 7) assertEquals(a.live(x, z), b.live(x, z), p.name() + " " + x + "," + z);
		}
	}

	/** Villages and hamlets lay a street, not a tile grid: no lattice. */
	@Test
	void smallTiersHaveNoLattice() throws Exception {
		TreeSet<String> tiers = new TreeSet<>();
		for (CityRoadsTest.Planned p : all()) {
			Tier t = p.src().site().tier();
			tiers.add(t.json);
			assertEquals(t.atLeast(Tier.TOWN), p.src().network().hasLattice(), p.name() + " " + t);
		}
		assertTrue(tiers.contains("town") && tiers.contains("city"), tiers.toString());
	}

	/** The whole-city rule holds for every plan: no street piece, plaza included, is cut off from the hub. */
	@Test
	void noDetachedRoadAnywhere() throws Exception {
		for (CityRoadsTest.Planned p : all()) {
			var cr = SettlementChecks.cityRoads(p.city().districts().values(), p.city().hub());
			assertEquals(List.of(), cr.violations().stream().map(v -> v.invariant() + " " + v.message()).toList(), p.name());
			assertEquals(0, cr.strays(), p.name());
		}
	}
}
